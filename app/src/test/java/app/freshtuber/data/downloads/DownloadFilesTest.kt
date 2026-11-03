package app.freshtuber.data.downloads

import org.junit.Assert.*
import org.junit.Test

class DownloadFilesTest {
    @Test fun rejectsFileTraversalAndNonVideoIds() {
        assertTrue(DownloadFiles.validId("BaW_jenozKc"))
        listOf("../downloads", "../../x", "", "BaW_jenozKc?", "https://youtu.be/BaW_jenozKc").forEach { assertFalse(DownloadFiles.validId(it)) }
    }
    @Test fun renewedSignedUrlResumesOnlyTheSameRepresentation() {
        val first = DownloadFiles.fingerprint("https://rr.googlevideo.com/videoplayback?itag=137&clen=999&lmt=100&expire=1&sig=secret1")
        val renewed = DownloadFiles.fingerprint("https://rr.googlevideo.com/videoplayback?expire=2&lmt=100&clen=999&itag=137&sig=secret2")
        assertTrue(DownloadFiles.canResume(first, renewed))
        assertFalse(first.contains("secret"))
        assertFalse(DownloadFiles.canResume(first, DownloadFiles.fingerprint("https://rr.googlevideo.com/videoplayback?itag=248&clen=999&lmt=100")))
        assertFalse(DownloadFiles.canResume(first, DownloadFiles.fingerprint("https://rr.googlevideo.com/videoplayback?itag=137&clen=1000&lmt=100")))
        assertFalse(DownloadFiles.canResume(first, DownloadFiles.fingerprint("https://rr.googlevideo.com/videoplayback?itag=137&clen=999&lmt=200")))
        assertFalse(DownloadFiles.canResume(null, renewed))
        assertFalse(DownloadFiles.canResume("::", "::"))
    }
}
