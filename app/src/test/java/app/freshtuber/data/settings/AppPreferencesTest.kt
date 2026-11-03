package app.freshtuber.data.settings

import org.junit.Assert.*
import org.junit.Test

class AppPreferencesTest {
    @Test fun `defaults avoid mobile downloads and mixed-in shorts`() {
        val settings = AppPreferences()
        assertTrue(settings.enabled(AppFeature.DownloadsWifi))
        assertFalse(settings.enabled(AppFeature.ShortsInFeeds))
        assertTrue(settings.enabled(AppFeature.ShortsTab))
        assertEquals(720, settings.downloadQuality)
        assertEquals(ThemeMode.System, settings.theme)
    }
    @Test fun `feature settings have independent persisted keys`() {
        assertEquals(AppFeature.entries.size, AppFeature.entries.map { it.key }.toSet().size)
        val changed = AppPreferences().copy(features = AppPreferences().features + (AppFeature.SponsorBlock to false))
        assertFalse(changed.enabled(AppFeature.SponsorBlock))
        assertTrue(changed.enabled(AppFeature.BackgroundPlayback))
    }
}
