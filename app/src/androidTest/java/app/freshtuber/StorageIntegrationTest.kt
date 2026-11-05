package app.freshtuber

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.freshtuber.data.local.*
import app.freshtuber.data.subscriptions.*
import app.freshtuber.data.downloads.*
import app.freshtuber.data.settings.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @get:Rule val migrations = MigrationTestHelper(instrumentation, AppDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun upgradesAllReleasedSchemasWithoutLosingPositionsOrSubscriptions() {
        for (version in 1..3) {
            val name = "migration-$version"
            context.deleteDatabase(name)
            migrations.createDatabase(name, version).apply {
                execSQL("INSERT INTO playback_positions VALUES ('BaW_jenozKc', 5000, 10000, 123)")
                if (version >= 2) execSQL("INSERT INTO local_channels VALUES ('UCsXVk37bltHxD1rDPwtNM8Q', 'Kurzgesagt', NULL, 1, 123, 1, 123, 0)")
                close()
            }
            migrations.runMigrationsAndValidate(name, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).apply {
                query("SELECT positionMs FROM playback_positions").use { assertTrue(it.moveToFirst()); assertEquals(5000L, it.getLong(0)) }
                if (version >= 2) query("SELECT notifications FROM local_channels").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
                close()
            }
            context.deleteDatabase(name)
        }
    }

    @Test fun deletingLocalChannelCascadesItsFeedButNotDownloads() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val channel = LocalChannel("UCsXVk37bltHxD1rDPwtNM8Q", "Kurzgesagt", null, addedAt = 123)
            db.localSubscriptions().save(channel)
            db.localSubscriptions().insertVideos(listOf(LocalVideo("BaW_jenozKc", channel.id, channel.name, "Test", null, 123)))
            db.downloads().save(Download("BaW_jenozKc", "Offline test", channelId = channel.id, workId = "test"))
            db.localSubscriptions().remove(channel.id)
            assertTrue(db.localSubscriptions().videos().first().isEmpty())
            assertNotNull(db.downloads().get("BaW_jenozKc"))
        } finally { db.close() }
    }

    @Test fun oldWorkerCannotOverwriteProgressFromNewGeneration() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.downloads()
            dao.save(Download("BaW_jenozKc", "Test", workId = "new", status = DownloadStatus.Downloading.name))
            dao.progress("BaW_jenozKc", "old", 1000, 2000)
            assertEquals(0L, dao.get("BaW_jenozKc")!!.bytesDone)
            dao.progress("BaW_jenozKc", "new", 1000, 2000)
            assertEquals(1000L, dao.get("BaW_jenozKc")!!.bytesDone)
            dao.state("BaW_jenozKc", DownloadStatus.Paused.name)
            dao.progress("BaW_jenozKc", "new", 2000, 2000)
            assertEquals(1000L, dao.get("BaW_jenozKc")!!.bytesDone)
        } finally { db.close() }
    }

    @Test fun preferencesPersistIndependentlyAndRestore() = runBlocking {
        val settings = AppSettings(context)
        val original = settings.preferences.first()
        try {
            settings.setTheme(ThemeMode.Amoled)
            settings.setFeature(AppFeature.DownloadsWifi, false)
            settings.setDownloadQuality(480)
            settings.setPlaybackQuality(true, 0)
            settings.setPlaybackQuality(false, 480)
            settings.setDockWidth(25)
            settings.setFeature(AppFeature.MiniPlayerTapOpens, false)
            settings.setFeature(AppFeature.ShortsTab, false)
            val reopened = AppSettings(context).preferences.first()
            assertEquals(ThemeMode.Amoled, reopened.theme)
            assertFalse(reopened.enabled(AppFeature.DownloadsWifi))
            assertEquals(480, reopened.downloadQuality)
            assertEquals(0, reopened.wifiQuality)
            assertEquals(480, reopened.mobileQuality)
            assertEquals(25, reopened.dockWidth)
            assertFalse(reopened.enabled(AppFeature.MiniPlayerTapOpens))
            assertEquals(original.quality, reopened.quality)
            assertEquals(original.enabled(AppFeature.SponsorBlock), reopened.enabled(AppFeature.SponsorBlock))
        } finally {
            settings.setTheme(original.theme)
            settings.setPlaybackQuality(true, original.wifiQuality)
            settings.setPlaybackQuality(false, original.mobileQuality)
            settings.setDockWidth(original.dockWidth)
            settings.setFeature(AppFeature.MiniPlayerTapOpens, original.enabled(AppFeature.MiniPlayerTapOpens))
            settings.setFeature(AppFeature.DownloadsWifi, original.enabled(AppFeature.DownloadsWifi))
            settings.setFeature(AppFeature.ShortsTab, original.enabled(AppFeature.ShortsTab))
            settings.setDownloadQuality(original.downloadQuality)
        }
    }
}
