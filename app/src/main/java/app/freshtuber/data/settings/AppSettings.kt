package app.freshtuber.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import app.freshtuber.ui.radial.ThumbArc
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Non-sensitive app settings. Account data never goes here. */
@Singleton
class AppSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** True once the user signed in or chose to continue without an account. */
    val welcomeDone: Flow<Boolean> = context.settingsStore.data.map { it[WELCOME_DONE] == true }

    suspend fun markWelcomeDone() {
        context.settingsStore.edit { it[WELCOME_DONE] = true }
    }

    /** DeArrow community titles and thumbnails. Settings screen arrives in stage 7. */
    val deArrowEnabled: Flow<Boolean> = context.settingsStore.data.map { it[DEARROW] ?: true }

    /** Return YouTube Dislike counts on the video page. */
    val dislikesEnabled: Flow<Boolean> = context.settingsStore.data.map { it[DISLIKES] ?: true }

    /** Shorts inside Home and Subscriptions; off by default, they have their own tab. */
    val shortsInFeeds: Flow<Boolean> = context.settingsStore.data.map { it[SHORTS_IN_FEEDS] ?: false }

    /** SponsorBlock: skip sponsor segments, self-promotion and "subscribe" reminders. */
    val sponsorBlockEnabled: Flow<Boolean> = context.settingsStore.data.map { it[SPONSOR_BLOCK] ?: true }

    /** Brightness (left) and volume (right) by swiping up and down on the video. */
    val gestureControls: Flow<Boolean> = context.settingsStore.data.map { it[GESTURES] ?: true }

    /** Play the next suggested video when one ends. */
    val autoplayNext: Flow<Boolean> = context.settingsStore.data.map { it[AUTOPLAY] ?: true }

    /** Shrink the video into a floating window when leaving the app. */
    val pipOnLeave: Flow<Boolean> = context.settingsStore.data.map { it[PIP] ?: true }

    /** Highest quality picked automatically, in lines (1080 = 1080p). */
    val preferredQuality: Flow<Int> = context.settingsStore.data.map { it[QUALITY] ?: 1080 }

    suspend fun setPreferredQuality(height: Int) {
        context.settingsStore.edit { it[QUALITY] = height }
    }

    val leftHanded: Flow<Boolean> = context.settingsStore.data.map { it[LEFT_HAND] ?: false }
    val thumbArc: Flow<ThumbArc> = context.settingsStore.data.map {
        ThumbArc(it[ARC_RADIUS] ?: 220f, it[ARC_SWEEP] ?: 90f, it[ARC_VERTICAL] ?: ((it[ARC_RADIUS] ?: 220f) * 1.36f)).normalized()
    }
    suspend fun setLeftHanded(enabled: Boolean) {
        context.settingsStore.edit { it[LEFT_HAND] = enabled }
    }
    suspend fun setThumbArc(arc: ThumbArc) {
        val normalized = arc.normalized()
        context.settingsStore.edit { it[ARC_RADIUS] = normalized.radius; it[ARC_SWEEP] = normalized.sweep; it[ARC_VERTICAL] = normalized.verticalRadius }
    }

    val videoPinned: Flow<Boolean> = context.settingsStore.data.map { it[VIDEO_PINNED] ?: true }
    suspend fun setVideoPinned(pinned: Boolean) {
        context.settingsStore.edit { it[VIDEO_PINNED] = pinned }
    }

    private companion object {
        val VIDEO_PINNED = booleanPreferencesKey("video_pinned")
        val ARC_VERTICAL = floatPreferencesKey("thumb_arc_vertical")
        val LEFT_HAND = booleanPreferencesKey("left_hand")
        val ARC_RADIUS = floatPreferencesKey("thumb_arc_radius")
        val ARC_SWEEP = floatPreferencesKey("thumb_arc_sweep")
        val SPONSOR_BLOCK = booleanPreferencesKey("sponsor_block")
        val GESTURES = booleanPreferencesKey("gesture_controls")
        val AUTOPLAY = booleanPreferencesKey("autoplay_next")
        val PIP = booleanPreferencesKey("pip_on_leave")
        val QUALITY = intPreferencesKey("preferred_quality")
        val WELCOME_DONE = booleanPreferencesKey("welcome_done")
        val DEARROW = booleanPreferencesKey("dearrow")
        val DISLIKES = booleanPreferencesKey("return_dislikes")
        val SHORTS_IN_FEEDS = booleanPreferencesKey("shorts_in_feeds")
    }
}
