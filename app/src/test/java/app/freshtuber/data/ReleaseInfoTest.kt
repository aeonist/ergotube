package app.freshtuber.data

import app.freshtuber.data.update.ReleaseInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ReleaseInfoTest {
    private fun releases(text: String) = Json.parseToJsonElement(text) as JsonArray

    @Test fun theBuildNumberComesFromTheTagTitleOrApkName() {
        assertEquals(28, ReleaseInfo.buildOf("v1.3-build28", null))
        assertEquals(31, ReleaseInfo.buildOf("v1.3", "ErgoTube build 31"))
        assertEquals(32, ReleaseInfo.buildOf("v1.3", "Новая сборка 32"))
        assertEquals(33, ReleaseInfo.buildOf(null, "ErgoTube-build33-release.apk"))
        assertNull(ReleaseInfo.buildOf("v1.2-liquid10", "ErgoTube 1.2 liquid 10"))
    }

    @Test fun onlyANewerPublishedBuildIsOffered() {
        val list = releases("""[
          {"tag_name":"build29","name":"Build 29","draft":true,"html_url":"https://github.com/x/y/releases/29","assets":[]},
          {"tag_name":"build28","name":"Build 28","draft":false,"prerelease":true,"html_url":"https://github.com/x/y/releases/28",
           "assets":[{"name":"ErgoTube-build28-debug.apk","browser_download_url":"https://github.com/d.apk"},
                     {"name":"ErgoTube-build28-release.apk","browser_download_url":"https://github.com/r.apk"}]},
          {"tag_name":"build27","name":"Build 27","draft":false,"html_url":"https://github.com/x/y/releases/27","assets":[]}
        ]""")
        val update = ReleaseInfo.newer(list, current = 27)!!
        assertEquals(28, update.build)
        assertEquals("https://github.com/r.apk", update.url)
        // A debug build is offered the debug APK, so it updates itself instead of installing a second app.
        assertEquals("https://github.com/d.apk", ReleaseInfo.newer(list, current = 27, debug = true)!!.url)
        assertNull(ReleaseInfo.newer(list, current = 28))
        assertNull(ReleaseInfo.newer(list, current = 27, includePre = false))
    }

    @Test fun aReleaseNamedOnlyInItsApkCounts() {
        val list = releases("""[
          {"tag_name":"v1.3","name":"Свежая версия","draft":false,"prerelease":false,"html_url":"https://github.com/x/y/releases/v1.3",
           "assets":[{"name":"ErgoTube-build33-release.apk","browser_download_url":"https://github.com/33.apk"}]}
        ]""")
        assertEquals(33, ReleaseInfo.newer(list, current = 32)!!.build)
        assertNull(ReleaseInfo.newer(list, current = 33))
    }

    @Test fun aReleaseWithoutANumberIsNewerWhenPublishedAfterThisInstall() {
        val installed = Instant.parse("2026-11-13T12:00:00Z").toEpochMilli()
        val list = releases("""[
          {"tag_name":"fresh","name":"Свежая версия","draft":false,"published_at":"2026-11-14T09:00:00Z","html_url":"https://github.com/x/y/releases/fresh",
           "assets":[{"name":"ErgoTube.apk","browser_download_url":"https://github.com/fresh.apk"}]},
          {"tag_name":"beta-liquid16","name":"ErgoTube 1.2 beta: liquid 16","draft":false,"published_at":"2026-11-06T00:03:34Z","html_url":"https://github.com/x/y/releases/16","assets":[]}
        ]""")
        val update = ReleaseInfo.newer(list, current = 32, installedAt = installed)!!
        assertEquals(0, update.build)
        assertEquals("fresh", update.key)
        assertEquals("https://github.com/fresh.apk", update.url)
        // Installed after it was published: nothing to offer.
        assertNull(ReleaseInfo.newer(list, current = 32, installedAt = Instant.parse("2026-11-15T00:00:00Z").toEpochMilli()))
        // Unknown install time never turns an old unnumbered release into an update.
        assertNull(ReleaseInfo.newer(list, current = 32))
    }
}
