package app.freshtuber.ui.music

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.freshtuber.R
import app.freshtuber.core.connectivity.NetworkMonitor
import app.freshtuber.data.account.AccountRepository
import app.freshtuber.data.account.AccountState
import app.freshtuber.data.downloads.Download
import app.freshtuber.data.downloads.DownloadKind
import app.freshtuber.data.downloads.DownloadStatus
import app.freshtuber.data.downloads.Downloads
import app.freshtuber.data.music.DailyMusicMix
import app.freshtuber.data.music.Lyrics
import app.freshtuber.data.music.MusicAccount
import app.freshtuber.data.music.MusicDiscovery
import app.freshtuber.data.music.MusicLibrary
import app.freshtuber.data.music.MusicLyrics
import app.freshtuber.data.music.MusicSource
import app.freshtuber.data.music.MusicTrack
import app.freshtuber.data.music.PlaylistCount
import app.freshtuber.data.settings.AppFeature
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.data.video.VideoRepository
import app.freshtuber.domain.model.PlaylistSummary
import app.freshtuber.innertube.InnerTubeCommand
import app.freshtuber.player.music.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A short note for the snackbar: a string, optionally a plural count or an argument. */
data class MusicNote(val res: Int, val arg: String? = null, val count: Int? = null)

sealed interface LyricsState {
    data object Off : LyricsState
    data object Loading : LyricsState
    data object Missing : LyricsState
    data object Failed : LyricsState
    data class Found(val lyrics: Lyrics) : LyricsState
}

data class HomeMusic(
    val daily: DailyMusicMix? = null,
    val dailyLoading: Boolean = true,
    val jams: app.freshtuber.data.music.MusicJamShelves = app.freshtuber.data.music.MusicJamShelves(),
    val signedIn: Boolean = false,
)

/** Everything the music screens share: the library, downloads and the actions on a song. */
@HiltViewModel
class MusicViewModel @Inject constructor(
    val music: MusicPlayer,
    private val library: MusicLibrary,
    private val discovery: MusicDiscovery,
    private val downloads: Downloads,
    private val lyrics: MusicLyrics,
    private val videos: VideoRepository,
    private val accounts: AccountRepository,
    private val folder: app.freshtuber.data.music.MusicFolder,
    private val settings: AppSettings,
    network: NetworkMonitor,
) : ViewModel() {
    val state = music.state
    val favorites = music.favoriteIds
    val online: StateFlow<Boolean> = network.online
    val tracks: StateFlow<List<MusicTrack>> = library.tracks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recent: StateFlow<List<MusicTrack>> = library.recentlyPlayed(20).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlists: StateFlow<List<PlaylistCount>> = library.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Audio downloads by video id. */
    val downloaded: StateFlow<Map<String, Download>> = downloads.audioItems.map { list -> list.filter { it.state != DownloadStatus.Deleting }.associateBy { it.videoId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val lyricsEnabled = settings.preferences.map { it.enabled(AppFeature.MusicLyrics) }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _notes = MutableSharedFlow<MusicNote>(extraBufferCapacity = 4)
    val notes: SharedFlow<MusicNote> = _notes.asSharedFlow()

    private val _home = MutableStateFlow(HomeMusic())
    val home: StateFlow<HomeMusic> = _home.asStateFlow()

    fun playlistTracks(id: String) = library.playlistTracks(id)
    suspend fun playlistName(id: String) = library.playlist(id)?.name

    fun loadHome() {
        viewModelScope.launch {
            val account = MusicAccount.from(accounts.state.value)
            _home.value = _home.value.copy(signedIn = account != null)
            launch {
                val daily = try { discovery.daily() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                _home.value = _home.value.copy(daily = daily ?: _home.value.daily, dailyLoading = false)
            }
            launch {
                val jams = try { discovery.discover(account) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                if (jams != null && !jams.isEmpty) _home.value = _home.value.copy(jams = jams)
            }
        }
    }

    private var jamJob: Job? = null
    fun playJam(jam: PlaylistSummary) {
        jamJob?.cancel()
        jamJob = viewModelScope.launch {
            try {
                val page = discovery.load(discovery.currentCursor(jam))
                if (page.tracks.isEmpty()) { _notes.tryEmit(MusicNote(R.string.music_failed)); return@launch }
                music.playCollection(page.tracks.map { library.known(it) }, 0, jam.title)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { _notes.tryEmit(MusicNote(R.string.music_failed)) }
        }
    }

    /** [title] is «Микс дня» in the listener's language, shown in the player and the notification. */
    fun playDaily(title: String, shuffle: Boolean = false) {
        val mix = _home.value.daily ?: return
        viewModelScope.launch { music.playCollection(mix.tracks.map { library.known(it) }, 0, title, shuffle = shuffle) }
    }

    /** The ids of the one library («Любимое»); the heart is on for these. */
    val libraryIds: StateFlow<Set<String>> = library.tracks.map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** The heart: adds the song to «Любимое», or takes it out of the one library again. */
    fun toggleHeart(track: MusicTrack) {
        viewModelScope.launch {
            if (MusicLibraryLogic.hearted(track, libraryIds.value)) library.remove(track) else music.toggleFavorite(track.copy(favorite = false))
        }
    }

    fun toggleFavorite(track: MusicTrack) { viewModelScope.launch { music.toggleFavorite(track) } }
    fun save(track: MusicTrack) { viewModelScope.launch { library.save(track) } }
    fun remove(track: MusicTrack) { viewModelScope.launch { library.remove(track) } }

    fun download(track: MusicTrack) {
        if (!track.isYouTube) return
        viewModelScope.launch {
            library.save(track)
            if (downloads.enqueue(track.videoSummary(), DownloadKind.Audio)) _notes.tryEmit(MusicNote(R.string.music_download_started))
        }
    }

    fun removeDownload(track: MusicTrack) { if (track.isYouTube) downloads.delete(track.videoId, DownloadKind.Audio) }

    /** True when the song has a file on this phone: a download, a file from the phone, or a found ErgoTube file. */
    fun onDevice(track: MusicTrack): Boolean =
        !track.isYouTube || track.uri != null || downloaded.value[track.videoId]?.state == DownloadStatus.Complete

    /** «Удалить с устройства?» waiting for the listener's answer in the app's own dialog. */
    private val _deleteAsk = MutableStateFlow<MusicTrack?>(null)
    val deleteAsk: StateFlow<MusicTrack?> = _deleteAsk.asStateFlow()
    /** Android's own «Удалить файл?» or the storage permission, to be asked by the screen. */
    private val _deleteSteps = MutableSharedFlow<app.freshtuber.data.music.FileDeletion>(extraBufferCapacity = 1)
    val deleteSteps: SharedFlow<app.freshtuber.data.music.FileDeletion> = _deleteSteps.asSharedFlow()
    private var pendingDelete: MusicTrack? = null

    /**
     * «Удалить с устройства». A download (with its copy in Music) only needs the app's own
     * question; a file from the phone, on Android 11 and newer, gets Android's own dialog instead.
     */
    fun askDelete(track: MusicTrack) {
        if (track.isYouTube || android.os.Build.VERSION.SDK_INT < 30) _deleteAsk.value = track else deleteFromDevice(track)
    }

    fun dismissDelete() { _deleteAsk.value = null }

    fun deleteFromDevice(track: MusicTrack) {
        _deleteAsk.value = null
        viewModelScope.launch {
            val download = if (track.isYouTube) downloads.audioItems.first().firstOrNull { it.videoId == track.videoId && it.state != DownloadStatus.Deleting } else null
            if (download != null) {
                // The download goes with its copy in the Music folder; the song still plays online.
                downloads.delete(track.videoId, DownloadKind.Audio)
                _notes.tryEmit(MusicNote(R.string.music_deleted_from_device))
                return@launch
            }
            val uri = track.uri ?: run { _notes.tryEmit(MusicNote(R.string.music_delete_failed)); return@launch }
            when (val step = folder.deleteFile(uri)) {
                app.freshtuber.data.music.FileDeletion.Done -> finishDelete(track)
                app.freshtuber.data.music.FileDeletion.Failed -> _notes.tryEmit(MusicNote(R.string.music_delete_failed))
                else -> { pendingDelete = track; _deleteSteps.tryEmit(step) }
            }
        }
    }

    /** Android's answer to its own dialog; on Android 10 the file goes only when asked again. */
    fun deleteAnswered(confirmed: Boolean, retry: Boolean) {
        val track = pendingDelete ?: return
        pendingDelete = null
        if (!confirmed) return
        if (retry) deleteFromDevice(track) else viewModelScope.launch { finishDelete(track) }
    }

    /** Android 9 and older: with the storage permission the delete runs again. */
    fun deletePermission(granted: Boolean) {
        val track = pendingDelete ?: return
        pendingDelete = null
        if (granted) deleteFromDevice(track) else _notes.tryEmit(MusicNote(R.string.music_delete_failed))
    }

    private suspend fun finishDelete(track: MusicTrack) {
        if (track.isYouTube) library.dropFile(track) else {
            library.remove(track)
            // The deleted file cannot play on: the queue moves on to the next song.
            val queue = music.state.value.queue
            if (queue.track?.id == track.id) { if (queue.upcoming.isNotEmpty()) music.next() else music.stop() }
        }
        _notes.tryEmit(MusicNote(R.string.music_deleted_from_device))
    }

    fun createPlaylist(name: String, add: MusicTrack? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val playlist = library.createPlaylist(name)
            if (add != null) { library.addToPlaylist(playlist.id, add); _notes.tryEmit(MusicNote(R.string.music_added_to_playlist, playlist.name)) }
        }
    }

    fun addToPlaylist(playlist: PlaylistCount, track: MusicTrack) {
        viewModelScope.launch { library.addToPlaylist(playlist.id, track); _notes.tryEmit(MusicNote(R.string.music_added_to_playlist, playlist.name)) }
    }

    fun removeFromPlaylist(playlist: String, track: MusicTrack) { viewModelScope.launch { library.removePlaylistTrack(playlist, track.id) } }
    fun renamePlaylist(id: String, name: String) { viewModelScope.launch { library.renamePlaylist(id, name) } }
    fun deletePlaylist(id: String) { viewModelScope.launch { library.removePlaylist(id) } }

    /** «Показывать треки из Music»: turned on only once audio access is given. */
    fun showFolder(on: Boolean) {
        viewModelScope.launch {
            settings.setFeature(AppFeature.MusicShowFolder, on)
            // Turning it on again after a new file arrived looks through the folder at once.
            if (on) { val added = folder.scan(); if (added > 0) _notes.tryEmit(MusicNote(R.plurals.music_imported, count = added)) }
        }
    }
    val folderReadable get() = folder.canRead()
    /** The stored switch is on but audio access was never given: Music should ask for it. */
    suspend fun folderNeedsAccess(): Boolean = settings.preferences.first().enabled(AppFeature.MusicShowFolder) && !folder.canRead()
    fun rescanFolder() { viewModelScope.launch { folder.scan() } }

    fun removeDuplicates() {
        viewModelScope.launch {
            val removed = folder.removeDuplicates()
            _notes.tryEmit(if (removed > 0) MusicNote(R.plurals.music_duplicates_removed, count = removed) else MusicNote(R.string.music_duplicates_none))
        }
    }

    fun importFiles(uris: List<Uri>) { viewModelScope.launch { report(library.importFiles(uris)) } }
    fun importFolder(uri: Uri) { viewModelScope.launch { report(library.importFolder(uri)) } }

    private fun report(result: app.freshtuber.data.music.MusicImportResult) {
        if (result.added > 0) _notes.tryEmit(MusicNote(R.plurals.music_imported, count = result.added))
        if (result.unavailable > 0) _notes.tryEmit(MusicNote(R.string.music_import_failed))
    }

    /** A YouTube like is a separate, explicit action; ♥ never does it. Goes through ActionGuard. */
    fun likeOnYouTube(track: MusicTrack) {
        if (!track.isYouTube) return
        if (accounts.state.value !is AccountState.SignedIn) { _notes.tryEmit(MusicNote(R.string.feed_later_sign_in)); return }
        viewModelScope.launch {
            try {
                videos.perform(InnerTubeCommand.like(track.videoId, "LIKE") ?: return@launch)
                _notes.tryEmit(MusicNote(R.string.music_liked_youtube))
            } catch (e: CancellationException) { throw e } catch (_: Exception) { _notes.tryEmit(MusicNote(R.string.music_failed)) }
        }
    }

    // Lyrics of the song on screen.
    private val _lyrics = MutableStateFlow<Pair<String, LyricsState>?>(null)
    val lyricsState: StateFlow<Pair<String, LyricsState>?> = _lyrics.asStateFlow()
    private var lyricsJob: Job? = null

    fun loadLyrics(track: MusicTrack) {
        if (_lyrics.value?.first == track.id && _lyrics.value?.second !is LyricsState.Failed) return
        lyricsJob?.cancel()
        if (!lyricsEnabled.value) { _lyrics.value = track.id to LyricsState.Off; return }
        _lyrics.value = track.id to LyricsState.Loading
        lyricsJob = viewModelScope.launch {
            val result = try { lyrics.find(track)?.let { LyricsState.Found(it) } ?: LyricsState.Missing }
            catch (e: CancellationException) { throw e } catch (_: Exception) { LyricsState.Failed }
            _lyrics.value = track.id to result
        }
    }

    // Search.
    data class SearchResults(val query: String = "", val phone: List<MusicTrack> = emptyList(), val youtube: List<MusicTrack> = emptyList(),
        val loading: Boolean = false, val failed: Boolean = false)
    private val _search = MutableStateFlow(SearchResults())
    val search: StateFlow<SearchResults> = _search.asStateFlow()
    private var searchJob: Job? = null

    fun search(query: String) {
        val q = query.trim()
        searchJob?.cancel()
        if (q.isEmpty()) { _search.value = SearchResults(); return }
        _search.value = SearchResults(q, loading = true)
        searchJob = viewModelScope.launch {
            val downloadedIds = downloaded.value.values.filter { it.state == DownloadStatus.Complete }.map { it.videoId }.toSet()
            // Without a network only what plays from the phone is offered.
            val phone = library.search(q).let { found -> if (online.value) found else found.filter { MusicLibraryLogic.isLocal(it, downloadedIds) } }
                .sortedBy { if (it.source == MusicSource.Phone) 0 else 1 }
            _search.value = _search.value.copy(phone = phone)
            val youtube = if (!online.value) emptyList() else try { discovery.searchYouTube(q).map { library.known(it) } }
                catch (e: CancellationException) { throw e } catch (_: Exception) { _search.value = _search.value.copy(failed = true); emptyList() }
            val known = phone.map { it.id }.toSet()
            _search.value = _search.value.copy(youtube = youtube.filterNot { it.id in known }, loading = false)
        }
    }

    suspend fun unplayableOffline(track: MusicTrack) = music.unplayableOffline(track)

    fun downloadedTrack(item: Download): MusicTrack = MusicTrack("yt:${item.videoId}", MusicSource.YouTube, item.title,
        item.channelName?.let(app.freshtuber.data.music.MusicNames::artist), artwork = downloads.thumbnail(item),
        durationMs = item.durationSeconds * 1000, saved = true, addedAt = item.createdAt)

    fun bytesOnPhone(): Long = downloaded.value.values.filter { it.state == DownloadStatus.Complete }.sumOf { it.bytesTotal }
}
