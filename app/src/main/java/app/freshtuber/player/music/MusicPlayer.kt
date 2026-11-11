package app.freshtuber.player.music

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.freshtuber.BuildConfig
import app.freshtuber.core.connectivity.NetworkMonitor
import app.freshtuber.data.downloads.Downloads
import app.freshtuber.data.music.MusicDiscovery
import app.freshtuber.data.music.MusicLibrary
import app.freshtuber.data.music.MusicSource
import app.freshtuber.data.music.MusicTrack
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.data.settings.MusicQuality
import app.freshtuber.player.ChunkedDataSource
import app.freshtuber.player.Chapter
import app.freshtuber.player.GoogleVideoInterceptor
import app.freshtuber.player.PlayerController
import app.freshtuber.player.StreamResolver
import com.google.common.util.concurrent.ListenableFuture
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MusicSleep {
    data object Off : MusicSleep
    /** Stops when the current song ends. */
    data object EndOfSong : MusicSleep
    data class Minutes(val value: Int) : MusicSleep
}

data class MusicState(
    val queue: MusicQueue = MusicQueue(),
    /** Playing, or about to (buffering while it should play). */
    val playing: Boolean = false,
    val buffering: Boolean = false,
    /** Songs that failed to load in this run; the queue skips them. */
    val unavailable: Set<String> = emptySet(),
    val sleep: MusicSleep = MusicSleep.Off,
    /** When the minutes timer ends, wall clock ms. */
    val sleepEndsAt: Long = 0,
    /** Chapters of a long mix, from the timestamps in its description. */
    val chapters: List<Chapter> = emptyList(),
    val radioLoading: Boolean = false,
) {
    val active get() = queue.current != null
}

sealed interface MusicEvent {
    /** [count] songs could not be played and were skipped. */
    data class Skipped(val count: Int) : MusicEvent
    /** Music was stopped from the dock; it can come back with «Вернуть». */
    data object Stopped : MusicEvent
    data object Failed : MusicEvent
}

/**
 * The music player: its own ExoPlayer for audio only, apart from the video player, so a video
 * can start while a song waits paused in the dock. Only one of them sounds at a time. The
 * player holds the current song and, from 30 s before its end, the next one, so songs follow
 * each other without a gap; the rest of the queue lives in [MusicQueue].
 */
@OptIn(UnstableApi::class)
@Singleton
class MusicPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    okHttp: OkHttpClient,
    private val resolver: StreamResolver,
    private val downloads: Downloads,
    private val library: MusicLibrary,
    private val discovery: MusicDiscovery,
    private val settings: AppSettings,
    private val network: NetworkMonitor,
    private val video: PlayerController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val playbackClient = okHttp.newBuilder().apply { interceptors().clear() }.addInterceptor(GoogleVideoInterceptor()).build()

    val player: ExoPlayer by lazy {
        // Whole songs: the buffer takes up to 15 minutes, so a song arrives in one or two
        // requests and the radio sleeps until the next one.
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(60_000, 15 * 60_000, 1_000, 2_000)
            .setTargetBufferBytes(48 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val sources = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, ChunkedDataSource.Factory(OkHttpDataSource.Factory(playbackClient))))
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(sources)
            .setLoadControl(load)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .also { it.addListener(listener) }
    }

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<MusicEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<MusicEvent> = _events.asSharedFlow()

    val favoriteIds: StateFlow<Set<String>> = library.favorites.map { list -> list.map { it.id }.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    private val queue get() = _state.value.queue
    /** The player holds [MusicQueue.current] (false after a restart until Play). */
    private var loaded = false
    private var loadJob: Job? = null
    private var nextJob: Job? = null
    private var tickJob: Job? = null
    private var sleepJob: Job? = null
    private var radioJob: Job? = null
    private var saveJob: Job? = null
    private var scanJob: Job? = null
    private var retried: String? = null
    private var skippedInRow = 0
    private var stoppedSnapshot: MusicQueue? = null
    private var controller: ListenableFuture<MediaController>? = null
    /** Headphones went away and paused the song; with the setting on, their return resumes it. */
    private var pausedByUnplug = false

    init {
        scope.launch {
            library.readState<MusicQueue>(STATE_KEY)?.takeIf { it.current != null }?.let { saved ->
                if (_state.value.queue.current == null) _state.update { it.copy(queue = saved) }
            }
        }
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        audio.registerAudioDeviceCallback(object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>) {
                if (!pausedByUnplug || added.none { it.isSink && it.type in HEADPHONES }) return
                scope.launch { if (settings.preferences.first().enabled(app.freshtuber.data.settings.AppFeature.MusicResumeOnConnect)) play() }
            }
        }, android.os.Handler(android.os.Looper.getMainLooper()))
        // Only one thing sounds: a video starting pauses the song, which waits in the dock.
        video.player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && player.playWhenReady) player.pause()
            }
        })
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refreshPlaying()
            if (isPlaying) {
                startTicking()
                if (video.player.isPlaying) video.player.pause()
            } else saveSoon(immediate = true)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            refreshPlaying()
            if (playbackState == Player.STATE_ENDED) onEnded()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            pausedByUnplug = !playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
            refreshPlaying()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT || mediaItem == null) return
            val id = mediaItem.mediaId
            when (id) {
                queue.track?.id -> Unit
                queue.next?.id -> queue.advance(explicit = false)?.let { setQueue(it) }
                else -> return
            }
            // The previous song leaves the player; the new one is item 0.
            val index = player.currentMediaItemIndex
            if (index > 0) player.removeMediaItems(0, index)
            nextJob?.cancel()
            onSongStarted()
            if (_state.value.sleep == MusicSleep.EndOfSong) finishSleep()
        }

        override fun onPlayerError(error: PlaybackException) {
            val track = queue.track ?: return
            val position = player.currentPosition
            if (BuildConfig.DEBUG) Log.w("MusicPlayer", "error ${error.errorCodeName} on ${track.id}")
            // Stream addresses expire after some hours: look the song up once more.
            if (track.isYouTube && retried != track.id && error.errorCode in RETRYABLE) {
                retried = track.id
                resolver.invalidate(track.videoId)
                loadCurrent(position, play = true)
                return
            }
            markUnavailable(track)
            skipUnavailable()
        }
    }

    private fun refreshPlaying() {
        val playing = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
        _state.update { it.copy(playing = playing, buffering = player.playbackState == Player.STATE_BUFFERING) }
    }

    // Starting music.

    /** Plays [tracks] from [start] as the collection («Перемешать» reorders only these). */
    fun playCollection(tracks: List<MusicTrack>, start: Int = 0, title: String? = null, shuffle: Boolean? = null) {
        if (tracks.isEmpty()) return
        val next = MusicQueue.start(tracks, start, title, queue.takeIf { it.current != null }, shuffle ?: queue.shuffle)
        setQueue(next)
        loadCurrent(0, play = true)
    }

    /** One song and radio after it. */
    fun playRadio(track: MusicTrack, title: String? = null, startMs: Long = 0) {
        setQueue(MusicQueue.radio(track, title, queue.takeIf { it.current != null }))
        loadCurrent(startMs, play = true)
    }

    /**
     * «Как музыку» on the watch page: the song goes on as audio from the same second, with
     * radio after it. The video keeps playing until the audio is ready, then it stops.
     */
    fun takeOverFromVideo(track: MusicTrack, title: String?) {
        clearPlayer()
        // The song shows at once (dock line, player); the sound follows when it is found.
        setQueue(MusicQueue.radio(track, title, queue.takeIf { it.current != null }))
        loadJob = scope.launch {
            val item = try { mediaItem(track) } catch (e: CancellationException) { throw e } catch (_: Exception) {
                _events.tryEmit(MusicEvent.Failed); return@launch
            }
            val position = video.player.currentPosition.takeIf { video.player.currentMediaItem?.mediaId == track.videoId } ?: 0L
            video.stop()
            ensureSession()
            player.setMediaItem(item, position)
            player.prepare()
            player.playWhenReady = true
            loaded = true
            onSongStarted()
        }
    }

    fun playNext(track: MusicTrack) {
        if (queue.current == null) { playRadio(track); return }
        setQueue(queue.playNext(track))
    }

    fun playLast(track: MusicTrack) {
        if (queue.current == null) { playCollection(listOf(track)); return }
        setQueue(queue.playLast(track))
    }

    // Transport.

    fun play() {
        if (queue.current == null) return
        if (video.player.isPlaying) video.player.pause()
        ensureSession()
        if (!loaded) { loadCurrent(queue.positionMs, play = true); return }
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    fun pause() { player.pause() }

    fun toggle() { if (_state.value.playing) pause() else play() }

    fun seekTo(ms: Long) {
        if (!loaded) { setQueue(queue.copy(positionMs = ms)); return }
        player.seekTo(ms)
    }

    fun seekBy(ms: Long) {
        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        seekTo((player.currentPosition + ms).coerceIn(0, duration))
    }

    /** Next song, or in a long mix with chapters the next chapter. */
    fun next() {
        val chapters = _state.value.chapters
        if (chapters.isNotEmpty() && loaded) {
            val seconds = player.currentPosition / 1000
            chapters.firstOrNull { it.startSeconds > seconds }?.let { player.seekTo(it.startSeconds * 1000); return }
        }
        val advanced = queue.advance(explicit = true) ?: return
        val target = advanced.track ?: return
        setQueue(advanced)
        if (loaded && player.mediaItemCount > 1 && player.getMediaItemAt(1).mediaId == target.id) {
            player.seekTo(1, 0)
            if (!player.playWhenReady) player.play()
        } else loadCurrent(0, play = true)
    }

    /** Back to the start of the song (or chapter), or to the previous song near the start. */
    fun previous() {
        val position = if (loaded) player.currentPosition else queue.positionMs
        val chapters = _state.value.chapters
        if (chapters.isNotEmpty() && loaded) {
            val seconds = position / 1000
            val current = Chapter.at(chapters, seconds)
            val index = chapters.indexOf(current)
            if (current != null && seconds - current.startSeconds > 3) { player.seekTo(current.startSeconds * 1000); return }
            if (index > 0) { player.seekTo(chapters[index - 1].startSeconds * 1000); return }
        }
        if (position > 3_000 || queue.history.isEmpty()) { seekTo(0); return }
        val back = queue.back() ?: return
        setQueue(back)
        loadCurrent(0, play = true)
    }

    fun jump(index: Int) {
        val next = queue.jump(index) ?: return
        setQueue(next)
        loadCurrent(0, play = true)
    }

    /** Hold Next: scans forward while the finger stays down. */
    fun scan(on: Boolean) {
        scanJob?.cancel()
        if (!on || !loaded) return
        scanJob = scope.launch {
            while (isActive) {
                val duration = player.duration.takeIf { it > 0 } ?: break
                player.seekTo((player.currentPosition + 2_500).coerceAtMost(duration - 500))
                delay(160)
            }
        }
    }

    fun setShuffle(on: Boolean) = setQueue(queue.withShuffle(on))
    fun toggleShuffle() = setShuffle(!queue.shuffle)
    fun cycleRepeat() = setQueue(queue.copy(repeat = queue.repeat.next()))
    fun setEndless(on: Boolean) = setQueue(queue.withEndless(on))
    fun remove(layer: QueueLayer, index: Int) = setQueue(queue.remove(layer, index))
    fun restore(layer: QueueLayer, index: Int, track: MusicTrack) = setQueue(queue.restore(layer, index, track))
    fun move(layer: QueueLayer, from: Int, to: Int) = setQueue(queue.move(layer, from, to))

    /** Stops the music; «Вернуть» brings it back with [undoStop]. */
    fun stop() {
        if (queue.current == null) return
        stoppedSnapshot = queue.copy(positionMs = if (loaded) player.currentPosition else queue.positionMs)
        clearPlayer()
        setQueue(MusicQueue(shuffle = queue.shuffle, repeat = queue.repeat, endless = queue.endless))
        _events.tryEmit(MusicEvent.Stopped)
        releaseSession()
    }

    fun undoStop() {
        val snapshot = stoppedSnapshot ?: return
        stoppedSnapshot = null
        setQueue(snapshot)
        loadCurrent(snapshot.positionMs, play = true)
    }

    fun setSleep(timer: MusicSleep) {
        sleepJob?.cancel()
        player.volume = 1f
        _state.update { it.copy(sleep = timer, sleepEndsAt = (timer as? MusicSleep.Minutes)?.let { m -> System.currentTimeMillis() + m.value * 60_000L } ?: 0) }
        val minutes = (timer as? MusicSleep.Minutes)?.value ?: return
        sleepJob = scope.launch {
            delay(minutes * 60_000L - FADE_MS)
            // The last 30 seconds fade out.
            val steps = 30
            repeat(steps) { step ->
                player.volume = 1f - (step + 1f) / steps
                delay(FADE_MS / steps)
            }
            finishSleep()
        }
    }

    private fun finishSleep() {
        sleepJob?.cancel()
        player.pause()
        player.volume = 1f
        _state.update { it.copy(sleep = MusicSleep.Off, sleepEndsAt = 0) }
    }

    suspend fun toggleFavorite(track: MusicTrack): Boolean = library.toggleFavorite(track)

    // Loading.

    private fun setQueue(next: MusicQueue) {
        val before = queue
        _state.update { it.copy(queue = next) }
        if (before.next?.id != next.next?.id || before.repeat != next.repeat) syncNext()
        player.repeatMode = if (next.repeat == RepeatMode.One) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        fillRadio()
        saveSoon()
    }

    /** Loads the current song into the player and plays it from [startMs]. */
    private fun loadCurrent(startMs: Long, play: Boolean) {
        val track = queue.track ?: return
        loadJob?.cancel()
        nextJob?.cancel()
        // The old song falls silent at once while the new one is looked up.
        if (loaded) { player.stop(); player.clearMediaItems(); loaded = false }
        _state.update { it.copy(buffering = true, playing = play, chapters = emptyList()) }
        loadJob = scope.launch {
            ensureSession()
            val item = try { mediaItem(track) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w("MusicPlayer", "unavailable ${track.id}: ${e.javaClass.simpleName}")
                markUnavailable(track)
                skipUnavailable()
                return@launch
            }
            player.setMediaItem(item, startMs)
            player.prepare()
            player.playWhenReady = play
            loaded = true
            onSongStarted()
        }
    }

    private fun onSongStarted() {
        val track = queue.track ?: return
        skippedInRow.takeIf { it > 0 }?.let { _events.tryEmit(MusicEvent.Skipped(it)) }
        skippedInRow = 0
        _state.update { it.copy(chapters = emptyList()) }
        scope.launch { library.recordPlay(track) }
        if (track.isYouTube && track.durationMs > LONG_TRACK_MS) loadChapters(track)
        startTicking()
        saveSoon()
    }

    /** Long mixes: chapters from the description, once the song plays. */
    private fun loadChapters(track: MusicTrack) {
        scope.launch {
            delay(3_000)
            try {
                val chapters = resolver.completed(resolver.resolveAudio(track.videoId)).chapters
                if (queue.track?.id == track.id) _state.update { it.copy(chapters = chapters) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
    }

    private fun markUnavailable(track: MusicTrack) {
        _state.update { it.copy(unavailable = it.unavailable + track.id) }
    }

    /** The current song cannot play: go on with the next playable one, and say so once. */
    private fun skipUnavailable() {
        skippedInRow++
        if (skippedInRow > MAX_SKIPS) {
            _events.tryEmit(MusicEvent.Skipped(skippedInRow)); skippedInRow = 0
            clearPlayer(); return
        }
        val next = queue.advance(explicit = true)
        if (next?.current == null) {
            _events.tryEmit(MusicEvent.Skipped(skippedInRow)); skippedInRow = 0
            clearPlayer(); return
        }
        setQueue(next)
        loadCurrent(0, play = true)
    }

    private fun clearPlayer() {
        loadJob?.cancel(); nextJob?.cancel(); tickJob?.cancel(); scanJob?.cancel()
        player.stop()
        player.clearMediaItems()
        loaded = false
        _state.update { it.copy(chapters = emptyList()) }
    }

    @Throws(Exception::class)
    private suspend fun mediaItem(track: MusicTrack): MediaItem {
        val uri: String = when (track.source) {
            MusicSource.Phone -> track.uri ?: throw IllegalStateException("no uri")
            MusicSource.YouTube -> downloads.localAudio(track.videoId)?.second ?: track.uri?.takeIf(library::readable) ?: run {
                if (!network.online.value) throw IllegalStateException("offline")
                val resolved = resolver.resolveAudio(track.videoId)
                val economy = settings.preferences.first().musicQuality(network.wifi.value) == MusicQuality.Economy
                (if (economy) resolved.economyAudioUrl else null) ?: resolved.audioUrl ?: throw IllegalStateException("no audio")
            }
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album ?: queue.title)
            .setArtworkUri(track.artwork?.let(Uri::parse))
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
        return MediaItem.Builder().setUri(uri).setMediaId(track.id).setMediaMetadata(metadata).build()
    }

    /** True when the song cannot play right now (offline and not on the phone). */
    suspend fun unplayableOffline(track: MusicTrack): Boolean =
        track.isYouTube && !network.online.value && downloads.localAudio(track.videoId) == null && !library.readable(track.uri)

    /** The player's second item must be the queue's next song. */
    private fun syncNext() {
        if (!loaded) return
        nextJob?.cancel()
        if (player.mediaItemCount > 1 && player.getMediaItemAt(1).mediaId != queue.next?.id) {
            player.removeMediaItems(1, player.mediaItemCount)
        }
    }

    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            var ticks = 0
            while (isActive && (player.isPlaying || player.playbackState == Player.STATE_BUFFERING)) {
                val duration = player.duration
                val position = player.currentPosition
                if (duration > 0 && duration - position <= PREFETCH_MS) prefetchNext()
                if (_state.value.sleep == MusicSleep.EndOfSong && duration > 0) {
                    player.volume = ((duration - position).toFloat() / FADE_MS).coerceIn(0f, 1f)
                }
                if (++ticks % 10 == 0) saveSoon(immediate = true)
                delay(1_000)
            }
        }
    }

    /** About 30 s before the end the next song is looked up and queued in the player. */
    private fun prefetchNext() {
        if (nextJob?.isActive == true || queue.repeat == RepeatMode.One) return
        if (player.mediaItemCount > 1) return
        val next = queue.next ?: return
        nextJob = scope.launch {
            var candidate = next
            var lookahead = queue
            repeat(4) {
                if (candidate.id !in _state.value.unavailable && !unplayableOffline(candidate)) {
                    val item = try { mediaItem(candidate) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    if (item != null) {
                        if (queue.next?.id == candidate.id && player.mediaItemCount == 1) player.addMediaItem(item)
                        return@launch
                    }
                    markUnavailable(candidate)
                }
                // Skip it in the queue so the player can go on to a playable one.
                lookahead = lookahead.withoutUpcoming(setOf(candidate.id))
                skippedInRow++
                setQueueQuietly(lookahead)
                candidate = lookahead.next ?: return@launch
            }
        }
    }

    private fun setQueueQuietly(next: MusicQueue) {
        _state.update { it.copy(queue = next) }
        saveSoon()
    }

    private fun onEnded() {
        if (_state.value.sleep == MusicSleep.EndOfSong) { finishSleep(); return }
        val next = queue.advance(explicit = false)
        if (next?.current == null) {
            // End of everything: stay on the last song, ready to play again.
            setQueue(queue.copy(positionMs = 0))
            player.seekTo(0)
            player.pause()
            return
        }
        setQueue(next)
        loadCurrent(0, play = true)
    }

    /** «Бесконечно»: radio from the last song of the collection before the listener's songs run out. */
    private fun fillRadio() {
        val q = queue
        if (!q.needsRadio || radioJob?.isActive == true || !network.online.value) return
        val seed = (q.collection.lastOrNull() ?: q.radio.lastOrNull() ?: q.track)?.takeIf { it.isYouTube }
            ?: q.history.lastOrNull { it.track.isYouTube }?.track ?: return
        radioJob = scope.launch {
            _state.update { it.copy(radioLoading = true) }
            try {
                val tracks = discovery.radio(seed).take(25)
                if (tracks.isNotEmpty()) setQueue(queue.withRadio(tracks))
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
            } finally { _state.update { it.copy(radioLoading = false) } }
        }
    }

    private fun saveSoon(immediate: Boolean = false) {
        saveJob?.cancel()
        saveJob = scope.launch {
            if (!immediate) delay(800)
            val position = if (loaded) player.currentPosition else queue.positionMs
            val snapshot = queue.copy(positionMs = position)
            _state.update { it.copy(queue = it.queue.copy(positionMs = position)) }
            try { library.writeState(STATE_KEY, snapshot) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
    }

    /** The music service runs (and shows its notification) while a controller is connected. */
    private fun ensureSession() {
        if (controller != null) return
        controller = MediaController.Builder(context, SessionToken(context, ComponentName(context, MusicService::class.java))).buildAsync()
    }

    private fun releaseSession() {
        controller?.let(MediaController::releaseFuture)
        controller = null
    }

    private companion object {
        const val STATE_KEY = "queue_v2"
        const val PREFETCH_MS = 30_000L
        const val FADE_MS = 30_000L
        const val MAX_SKIPS = 6
        /** Longer than this, a song is a mix: chapters and ±10 s. */
        const val LONG_TRACK_MS = 20 * 60_000L
        val HEADPHONES = setOf(android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_USB_HEADSET, 26 /* TYPE_BLE_HEADSET */)
        val RETRYABLE = setOf(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )
    }
}
