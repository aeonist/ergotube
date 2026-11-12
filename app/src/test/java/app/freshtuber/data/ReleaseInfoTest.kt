package app.freshtuber.data

import app.freshtuber.data.update.ReleaseInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseInfoTest {
    private fun releases(text: String) = Json.parseToJsonElement(text) as JsonArray

    @Test fun theBuildNumberComesFromTheTagOrTitle() {
        assertEquals(28, ReleaseInfo.buildOf("v1.3-build28", null))
        assertEquals(31, ReleaseInfo.buildOf("v1.3", "ErgoTube build 31"))
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
        assertNull(ReleaseInfo.newer(list, current = 28))
        assertNull(ReleaseInfo.newer(list, current = 27, includePre = false))
    }
}
