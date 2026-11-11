package app.freshtuber.data.music

import app.freshtuber.core.connectivity.NetworkMonitor
import app.freshtuber.core.di.ApplicationScope
import app.freshtuber.data.downloads.Download
import app.freshtuber.data.downloads.DownloadKind
import app.freshtuber.data.downloads.DownloadStatus
import app.freshtuber.data.downloads.Downloads
import app.freshtuber.data.settings.AppFeature
import app.freshtuber.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Which songs the daily-mix download fetched, and on which day; manual downloads are not here. */
@Serializable
data class AutoDownloads(val daily: Map<String, String> = emptyMap())

/**
 * Optional automatic downloads on Wi-Fi: «Любимое» and today's «Микс дня», within a size limit
 * (2 GB unless changed). To make room, earlier daily mixes go first; favourites and the
 * listener's own downloads are never removed.
 */
@Singleton
class MusicOffline @Inject constructor(
    private val downloads: Downloads,
    private val library: MusicLibrary,
    private val discovery: MusicDiscovery,
    private val settings: AppSettings,
    private val network: NetworkMonitor,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            combine(settings.preferences, network.wifi, library.favorites) { preferences, wifi, favorites -> Triple(preferences, wifi, favorites) }
                .debounce(4_000)
                .collect { (preferences, wifi, favorites) ->
                    val daily = preferences.enabled(AppFeature.MusicAutoDaily)
                    val favorite = preferences.enabled(AppFeature.MusicAutoFavorites)
                    if (!wifi || (!daily && !favorite)) return@collect
                    try { sync(preferences.musicLimitGb * GB, if (favorite) favorites else emptyList(), daily) }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { /* Next change tries again. */ }
                }
        }
    }

    private suspend fun sync(limit: Long, favorites: List<MusicTrack>, daily: Boolean) {
        val items = downloads.audioItems.first().filter { it.state != DownloadStatus.Deleting }.associateBy { it.videoId }.toMutableMap()
        var used = items.values.sumOf(::size)
        val auto = library.readState<AutoDownloads>(STATE_KEY) ?: AutoDownloads()
        val dailyIds = auto.daily.toMutableMap()
        val favoriteIds = favorites.map { it.videoId }.toSet()
        val today = LocalDate.now().toString()

        suspend fun fetch(track: MusicTrack, fromDaily: Boolean): Boolean {
            if (!track.isYouTube || items.containsKey(track.videoId)) return true
            val need = estimate(track)
            // Make room from earlier daily mixes, oldest first; never favourites.
            while (used + need > limit) {
                val oldest = dailyIds.entries.filter { it.value != today && it.key !in favoriteIds && items.containsKey(it.key) }
                    .minByOrNull { it.value } ?: return false
                downloads.delete(oldest.key, DownloadKind.Audio)
                used -= items.remove(oldest.key)?.let(::size) ?: 0
                dailyIds.remove(oldest.key)
            }
            if (downloads.enqueue(track.videoSummary(), DownloadKind.Audio)) {
                used += need
                items[track.videoId] = Download(track.videoId, track.title, kind = DownloadKind.Audio, workId = "")
                if (fromDaily) dailyIds[track.videoId] = today
            }
            return true
        }

        favorites.forEach { if (!fetch(it, fromDaily = false)) return@forEach }
        if (daily) {
            val mix = discovery.daily()
            for (track in mix.tracks) if (!fetch(track, fromDaily = true)) break
        }
        library.writeState(STATE_KEY, AutoDownloads(dailyIds))
    }

    private fun size(item: Download): Long = maxOf(item.bytesTotal, item.bytesDone)

    /** About 160 kbps when the size is not known yet. */
    private fun estimate(track: MusicTrack): Long = (track.durationMs.takeIf { it > 0 } ?: 240_000L) / 1000 * 20_000

    private companion object {
        const val STATE_KEY = "auto_downloads"
        const val GB = 1024L * 1024 * 1024
    }
}
