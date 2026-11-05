package app.freshtuber.player

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import app.freshtuber.BuildConfig
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import java.io.File
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.freshtuber.data.account.AccountRepository
import app.freshtuber.data.account.AccountState
import app.freshtuber.data.extras.CommunityExtras
import app.freshtuber.data.extras.SkipSegment
import app.freshtuber.data.extras.SponsorBlockRules
import app.freshtuber.data.local.PlaybackPosition
import app.freshtuber.data.local.PlaybackPositionDao
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.data.video.VideoRepository
import app.freshtuber.domain.model.VideoSummary
import app.freshtuber.ui.feed.LoadError
import app.freshtuber.ui.feed.toLoadError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

data class NowPlaying(
    val videoId: String,
    val title: String,
    val channelName: String?,
    val thumbnailUrl: String?,
    val isLive: Boolean,
    val isShort: Boolean,
    val qualities: List<VideoTrack>,
    /** -1 when playing a combined stream or live. */
    val qualityIndex: Int,
    val subtitles: List<SubtitleTrack>,
    /** -1 when subtitles are off. */
    val subtitleIndex: Int,
    val channel: app.freshtuber.domain.model.ChannelSummary? = null,
    val isOffline: Boolean = false,
    val durationSeconds: Long = 0,
    val chapters: List<Chapter> = emptyList(),
    val storyboard: Storyboard? = null,
)

sealed interface SleepTimer {
    data object Off : SleepTimer
    data object EndOfVideo : SleepTimer
    data class Minutes(val value: Int) : SleepTimer {
        init { require(value in 1..180) }
    }
}

data class PlayerState(
    val nowPlaying: NowPlaying? = null,
    /** Video id being resolved, before [nowPlaying] switches to it. */
    val loadingId: String? = null,
    val error: LoadError? = null,
    val speed: Float = 1f,
    val repeat: Boolean = false,
    val sleepTimer: SleepTimer = SleepTimer.Off,
    val queue: List<VideoSummary> = emptyList(),
    /** Videos played before this one in this session, oldest first; Previous goes back here. */
    val history: List<String> = emptyList(),
)

/** Something the user should briefly see, e.g. a skipped sponsor segment. */
sealed interface PlayerEvent {
    data class SegmentSkipped(val segment: SkipSegment) : PlayerEvent
    data class Advanced(val videoId: String) : PlayerEvent
}

/**
 * The one player of the app. It lives as long as the process so playback continues on other
 * screens, in the background and in picture-in-picture; [PlaybackService] exposes it to the
 * system through a MediaSession.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlayerController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    okHttp: OkHttpClient,
    private val resolver: StreamResolver,
    private val positions: PlaybackPositionDao,
    private val extras: CommunityExtras,
    private val settings: AppSettings,
    private val videos: VideoRepository,
    private val accounts: AccountRepository,
    private val downloads: app.freshtuber.data.downloads.Downloads,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Playback uses its own client: no cookies, no compression, googlevideo request shaping. */
    private val playbackClient = okHttp.newBuilder()
        .apply { interceptors().clear() }
        .addInterceptor(GoogleVideoInterceptor())
        .build()
    private val streamCache by lazy {
        SimpleCache(File(context.cacheDir, "playback"), LeastRecentlyUsedCacheEvictor(64L * 1024 * 1024), StandaloneDatabaseProvider(context))
    }
    private val dataSourceFactory by lazy {
        CacheDataSource.Factory().setCache(streamCache)
            .setUpstreamDataSourceFactory(ChunkedDataSource.Factory(OkHttpDataSource.Factory(playbackClient)))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    val player: ExoPlayer by lazy {
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector { mimeType, secure, tunneling ->
                MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
                    .filterNot { it.name in failedDecoders }
            }
        ExoPlayer.Builder(context, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()
            .also { it.addListener(listener) }
    }

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    /**
     * Screens showing the video right now, so leaving the app can open a floating window. A
     * count, not a flag: the next page appears before the previous one is gone.
     */
    private val screensShowingVideo = java.util.concurrent.atomic.AtomicInteger(0)
    val videoOnScreen: Boolean get() = screensShowingVideo.get() > 0
    fun setVideoScreen(shown: Boolean) {
        if (shown) screensShowingVideo.incrementAndGet() else screensShowingVideo.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    /** Next or previous Short asked for from outside the pager (the floating window's buttons). */
    private val _shortSteps = MutableSharedFlow<Int>(extraBufferCapacity = 2)
    val shortSteps: SharedFlow<Int> = _shortSteps.asSharedFlow()

    /** Next in whatever is playing: the next Short, or the next suggested video. */
    fun skipForward() {
        if (_state.value.nowPlaying?.isShort == true) _shortSteps.tryEmit(1) else playNext()
    }

    /** Back in whatever is playing: the previous Short or video, else the start of this one. */
    fun skipBack() {
        when {
            _state.value.nowPlaying?.isShort == true -> _shortSteps.tryEmit(-1)
            _state.value.history.isNotEmpty() -> playPrevious()
            else -> player.seekTo(0)
        }
    }

    private var resolved: ResolvedVideo? = null
    private var segments: List<SkipSegment> = emptyList()
    private var sponsorEnabled = true
    init {
        scope.launch { settings.sponsorBlockEnabled.collectLatest { enabled ->
            sponsorEnabled = enabled
            if (!enabled) segments = emptyList()
            else resolved?.takeUnless { it.isLive || _state.value.nowPlaying?.isOffline == true }?.let { segments = extras.sponsorSegments(it.videoId) }
        } }
    }
    private var prefetchId: String? = null
    private var preloadJob: Job? = null
    private var loadJob: Job? = null
    private var requestedId: String? = null
    private var requestedShort = false
    /** True while [playPrevious] reopens a video, so it is not added to the history again. */
    private var goingBack = false
    private var startedAt = 0L
    private var frameReported = false
    private var tickJob: Job? = null
    private var sleepJob: Job? = null
    private var reportedWatch = false
    /** Decoders (by name) that failed on this device during this run. */
    private val failedDecoders: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Decoder families that failed on this device during this run. */
    private val blockedCodecs = mutableSetOf<String>()

    private val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            if (frameReported) return
            frameReported = true
            if (BuildConfig.DEBUG && requestedShort) Log.d("ShortsTiming", "first_frame_ms=${SystemClock.elapsedRealtime() - startedAt}")
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startTicking()
                reportWatchOnce()
            } else {
                savePosition()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) onEnded()
        }

        override fun onPlayerError(error: PlaybackException) {
            savePosition()
            if (error.errorCode in DECODER_ERRORS && (skipBrokenDecoder(error) || stepDownAfterDecoderError())) return
            _state.update { it.copy(error = (error.cause ?: error).toLoadError()) }
        }
    }

    /** Opens [videoId]; does nothing if it is already the current video. */
    fun play(videoId: String, isShort: Boolean = false) {
        if (requestedId == videoId && (_state.value.nowPlaying?.videoId == videoId || _state.value.loadingId == videoId) && _state.value.error == null) return
        val leaving = requestedId
        if (!goingBack && leaving != null && leaving != videoId && !requestedShort && !isShort) {
            _state.update { it.copy(history = (it.history - leaving + leaving).takeLast(MAX_HISTORY)) }
        }
        requestedId = videoId
        requestedShort = isShort
        startedAt = SystemClock.elapsedRealtime()
        frameReported = false
        if (isShort) { player.pause(); segments = emptyList() }
        savePosition()
        loadJob?.cancel()
        _state.update { it.copy(loadingId = videoId, error = null) }
        loadJob = scope.launch {
            try {
                val local = downloads.localVideo(videoId)
                val video = local ?: resolver.resolve(videoId)
                if (BuildConfig.DEBUG && isShort) Log.d("ShortsTiming", "resolve_ms=${SystemClock.elapsedRealtime() - startedAt}")
                resolved = video
                reportedWatch = false
                val tracks = StreamChoice.videoTracks(video.videoCandidates, blockedCodecs)
                val qualityIndex = StreamChoice.pick(tracks, settings.preferredQuality.first())
                val nowPlaying = NowPlaying(
                    videoId = videoId,
                    isOffline = local != null,
                    channel = video.channel,
                    title = video.title,
                    channelName = video.channelName,
                    thumbnailUrl = video.thumbnailUrl,
                    isLive = video.isLive,
                    isShort = isShort,
                    qualities = tracks,
                    qualityIndex = if (video.isLive) -1 else qualityIndex,
                    subtitles = video.subtitles,
                    subtitleIndex = -1,
                    durationSeconds = video.durationSeconds,
                    chapters = video.chapters,
                    storyboard = if (local == null) video.storyboard else null,
                )
                val resumeAt = if (video.isLive || isShort) null else resumePosition(videoId)
                _state.update { it.copy(nowPlaying = nowPlaying, loadingId = null) }
                player.repeatMode = if ((isShort || _state.value.repeat) && _state.value.sleepTimer != SleepTimer.EndOfVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                player.setMediaSource(buildSource(video, nowPlaying), resumeAt ?: 0L)
                player.prepare()
                player.playWhenReady = true
                segments = emptyList()
                if (local == null && !video.isLive && settings.sponsorBlockEnabled.first()) {
                    segments = extras.sponsorSegments(videoId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingId = null, error = e.toLoadError()) }
            }
        }
    }

    fun warmShort(videoId: String?) {
        if (videoId == null) return
        scope.launch {
            try { resolver.resolve(videoId, allowAccount = false) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Optional anonymous lookahead. */ }
        }
    }

    /** Only anonymous extraction/bytes: never plays, uses cookies, or reports history. */
    fun prefetchShort(videoId: String?) {
        if (prefetchId == videoId && preloadJob?.isActive == true) return
        prefetchId = videoId
        preloadJob?.cancel()
        if (videoId == null) return
        preloadJob = scope.launch {
            try {
                val video = resolver.resolve(videoId, allowAccount = false)
                // Resolve while the current clip starts, but reserve bandwidth for its first frame.
                while (player.playbackState != Player.STATE_READY) delay(100)
                val tracks = StreamChoice.videoTracks(video.videoCandidates, blockedCodecs)
                val track = tracks.getOrNull(StreamChoice.pick(tracks, settings.preferredQuality.first()))
                val urls = if (track != null && video.audioUrl != null)
                    listOf(track.url to 512L * 1024, video.audioUrl to 128L * 1024)
                else listOfNotNull(video.muxedUrl?.let { it to 640L * 1024 })
                coroutineScope {
                    urls.map { (url, bytes) -> async(Dispatchers.IO) {
                        val job = currentCoroutineContext()
                        val spec = DataSpec.Builder().setUri(url).setLength(minOf(bytes, ChunkPlan.streamLength(url) ?: bytes)).build()
                        CacheWriter(dataSourceFactory.createDataSource(), spec, null) { _, _, _ -> job.ensureActive() }.cache()
                    } }.awaitAll()
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Preloading is optional; foreground playback retries normally. */ }
        }
    }

    /** Retries after an error, keeping the position (stream URLs expire after some hours). */
    fun retry() {
        val id = requestedId ?: return
        resolver.invalidate(id)
        val position = player.currentPosition
        val wasShort = requestedShort
        _state.update { it.copy(nowPlaying = null, loadingId = null) }
        play(id, wasShort)
        scope.launch {
            loadJob?.join()
            if (position > 0) player.seekTo(position)
        }
    }

    fun selectQuality(index: Int) {
        val video = resolved ?: return
        val now = _state.value.nowPlaying ?: return
        if (index == now.qualityIndex || index !in now.qualities.indices) return
        val updated = now.copy(qualityIndex = index)
        _state.update { it.copy(nowPlaying = updated, error = null) }
        scope.launch { settings.setPreferredQuality(now.qualities[index].height) }
        rebuildKeepingPosition(video, updated)
    }

    fun selectSubtitles(index: Int) {
        val video = resolved ?: return
        val now = _state.value.nowPlaying ?: return
        if (index == now.subtitleIndex || index < -1 || index >= now.subtitles.size) return
        val updated = now.copy(subtitleIndex = index)
        _state.update { it.copy(nowPlaying = updated, error = null) }
        rebuildKeepingPosition(video, updated)
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun toggleRepeat() {
        val repeat = !_state.value.repeat
        player.repeatMode = if (repeat && _state.value.sleepTimer != SleepTimer.EndOfVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        _state.update { it.copy(repeat = repeat) }
    }

    fun setSleepTimer(timer: SleepTimer) {
        sleepJob?.cancel()
        _state.update { it.copy(sleepTimer = timer) }
        player.repeatMode = if (timer != SleepTimer.EndOfVideo && (_state.value.repeat || _state.value.nowPlaying?.isShort == true))
            Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        val minutes = (timer as? SleepTimer.Minutes)?.value ?: return
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            player.pause()
            _state.update { it.copy(sleepTimer = SleepTimer.Off) }
        }
    }

    /** Videos to continue with when this one ends (the page's suggestions). */
    fun setQueue(videos: List<VideoSummary>) {
        _state.update { it.copy(queue = videos.filterNot { v -> v.isLive || v.isShort }) }
    }

    fun playNext() {
        val next = _state.value.queue.firstOrNull() ?: return
        _state.update { it.copy(queue = it.queue.drop(1)) }
        play(next.id)
        _events.tryEmit(PlayerEvent.Advanced(next.id))
    }

    /** Goes back to the video played before this one; the current one is not queued again. */
    fun playPrevious() {
        val previous = _state.value.history.lastOrNull() ?: return
        _state.update { it.copy(history = it.history.dropLast(1)) }
        goingBack = true
        try { play(previous) } finally { goingBack = false }
        _events.tryEmit(PlayerEvent.Advanced(previous))
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun stop() {
        savePosition()
        loadJob?.cancel()
        preloadJob?.cancel()
        requestedId = null
        prefetchId = null
        sleepJob?.cancel()
        player.stop()
        player.clearMediaItems()
        resolved = null
        _state.update { PlayerState(speed = it.speed) }
    }

    /**
     * A decoder that fails on real streams (broken hardware decoders exist; the emulator's
     * always fail) is left out from then on, so the next one, usually software, takes over.
     */
    private fun skipBrokenDecoder(error: PlaybackException): Boolean {
        val name = when (val cause = error.cause) {
            is MediaCodecDecoderException -> cause.codecInfo?.name
            is MediaCodecRenderer.DecoderInitializationException -> cause.codecInfo?.name
            else -> null
        } ?: return false
        if (!failedDecoders.add(name)) return false
        val video = resolved ?: return false
        val now = _state.value.nowPlaying ?: return false
        _state.update { it.copy(error = null) }
        rebuildKeepingPosition(video, now)
        player.playWhenReady = true
        return true
    }

    /**
     * Some decoders fail on real streams (the emulator's H.264 one always does). Stop using
     * that codec family and pick the same height in another codec, else step down a quality.
     */
    private fun stepDownAfterDecoderError(): Boolean {
        val video = resolved ?: return false
        val now = _state.value.nowPlaying ?: return false
        val failed = now.qualities.getOrNull(now.qualityIndex) ?: return false
        val tracks = if (failed.codecMime != null && blockedCodecs.add(failed.codecMime)) {
            StreamChoice.videoTracks(video.videoCandidates, blockedCodecs)
        } else {
            now.qualities.filterIndexed { index, _ -> index > now.qualityIndex }
        }
        val index = StreamChoice.pick(tracks, failed.height)
        if (index < 0) return false
        val updated = now.copy(qualities = tracks, qualityIndex = index)
        _state.update { it.copy(nowPlaying = updated, error = null) }
        rebuildKeepingPosition(video, updated)
        player.playWhenReady = true
        return true
    }

    private fun rebuildKeepingPosition(video: ResolvedVideo, nowPlaying: NowPlaying) {
        val position = player.currentPosition
        val playing = player.playWhenReady
        player.setMediaSource(buildSource(video, nowPlaying), position)
        player.prepare()
        player.playWhenReady = playing
    }

    private fun buildSource(video: ResolvedVideo, nowPlaying: NowPlaying): MediaSource {
        val metadata = MediaMetadata.Builder()
            .setTitle(video.title)
            .setArtist(video.channelName)
            .setArtworkUri(video.thumbnailUrl?.let(Uri::parse))
            .build()
        val subtitleConfigurations = video.subtitles.getOrNull(nowPlaying.subtitleIndex)?.let { subtitle ->
            listOf(MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                .setMimeType(subtitle.mimeType)
                .setLanguage(subtitle.language)
                .setLabel(subtitle.label)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build())
        }.orEmpty()
        fun item(url: String, withSubtitles: Boolean = false) = MediaItem.Builder()
            .setUri(url)
            .setMediaId(video.videoId)
            .setMediaMetadata(metadata)
            .setSubtitleConfigurations(if (withSubtitles) subtitleConfigurations else emptyList())
            .build()

        // Media3 parses sidecar captions during extraction into cues. SingleSampleMediaSource
        // sends legacy TTML samples, which the current TextRenderer rejects.
        val factory = DefaultMediaSourceFactory(if (nowPlaying.isOffline) androidx.media3.datasource.DefaultDataSource.Factory(context) else dataSourceFactory)
        if (video.isLive && video.hlsUrl != null) {
            return factory.createMediaSource(item(video.hlsUrl, withSubtitles = true).buildUpon()
                .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8).build())
        }
        val sources = mutableListOf<MediaSource>()
        val track = nowPlaying.qualities.getOrNull(nowPlaying.qualityIndex)
        if (track != null && video.audioUrl != null) {
            sources += factory.createMediaSource(item(track.url, withSubtitles = true))
            sources += factory.createMediaSource(item(video.audioUrl))
        } else {
            sources += factory.createMediaSource(item(checkNotNull(video.muxedUrl), withSubtitles = true))
        }
        return if (sources.size == 1) sources.first() else MergingMediaSource(*sources.toTypedArray())
    }

    /** While playing: skip SponsorBlock segments and save the position now and then. */
    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            var sinceSave = 0
            while (isActive && player.isPlaying) {
                val seconds = player.currentPosition / 1000.0
                SponsorBlockRules.segmentAt(if (sponsorEnabled) segments else emptyList(), seconds)?.let { segment ->
                    player.seekTo((segment.end * 1000).toLong())
                    _events.tryEmit(PlayerEvent.SegmentSkipped(segment))
                }
                if (++sinceSave >= SAVE_EVERY_TICKS) {
                    sinceSave = 0
                    savePosition()
                }
                delay(TICK_MS)
            }
        }
    }

    private fun onEnded() {
        val id = _state.value.nowPlaying?.videoId
        if (id != null) scope.launch { positions.delete(id) }
        if (_state.value.sleepTimer == SleepTimer.EndOfVideo) {
            _state.update { it.copy(sleepTimer = SleepTimer.Off) }
            return
        }
        scope.launch {
            if (!_state.value.repeat && settings.autoplayNext.first()) playNext()
        }
    }

    private fun savePosition() {
        val now = _state.value.nowPlaying ?: return
        if (now.isLive || now.isShort) return
        val position = player.currentPosition
        val duration = player.duration.takeIf { it > 0 } ?: return
        scope.launch {
            if (position < MIN_RESUME_MS || duration - position < END_MARGIN_MS) {
                positions.delete(now.videoId)
            } else {
                positions.save(PlaybackPosition(now.videoId, position, duration, System.currentTimeMillis()))
            }
        }
    }

    private suspend fun resumePosition(videoId: String): Long? =
        positions.get(videoId)?.positionMs?.takeIf { it >= MIN_RESUME_MS }

    private fun reportWatchOnce() {
        if (reportedWatch || _state.value.nowPlaying?.isOffline == true) return
        val now = _state.value.nowPlaying ?: return
        if (accounts.state.value !is AccountState.SignedIn) return
        reportedWatch = true
        scope.launch { videos.reportWatched(now.videoId) }
    }

    private companion object {
        const val MAX_HISTORY = 50
        val DECODER_ERRORS = setOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        )
        const val SEEK_STEP_MS = 10_000L
        const val TICK_MS = 500L
        const val SAVE_EVERY_TICKS = 10
        const val MIN_RESUME_MS = 10_000L
        const val END_MARGIN_MS = 15_000L
    }
}
