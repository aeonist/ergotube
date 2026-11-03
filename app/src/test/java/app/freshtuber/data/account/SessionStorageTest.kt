package app.freshtuber.data.account

import app.freshtuber.core.security.BlobCipher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Reversible stand-in for the Keystore cipher: the file must not contain the plain text. */
private class XorCipher : BlobCipher {
    override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun decrypt(blob: ByteArray) = encrypt(blob)
}

class SessionStorageTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val profile = AccountProfile(
        id = "p1",
        cookies = mapOf("SAPISID" to "secret-value"),
        visitorData = "v",
        name = "Тест",
    )

    @Test
    fun `file round-trips and never holds cookies in plain text`() {
        val file = temp.root.resolve("session.bin")
        val store = SessionFileStore(file, XorCipher())
        val sessions = StoredSessions(listOf(profile), activeProfileId = "p1")

        store.write(sessions)

        assertFalse(file.readText(Charsets.ISO_8859_1).contains("secret-value"))
        assertEquals(sessions, store.read())
        assertEquals(profile, store.read().active)
    }

    @Test
    fun `unreadable file reads as signed out`() {
        val file = temp.root.resolve("session.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals(StoredSessions(), SessionFileStore(file, XorCipher()).read())
    }

    @Test
    fun `writing no profiles deletes the file`() {
        val file = temp.root.resolve("session.bin")
        val store = SessionFileStore(file, XorCipher())
        store.write(StoredSessions(listOf(profile), "p1"))
        store.write(StoredSessions())
        assertFalse(file.exists())
    }

    @Test
    fun `store loads, updates and persists`() = runTest {
        val file = temp.root.resolve("session.bin")
        val io = StandardTestDispatcher(testScheduler)
        val store = SessionStore(SessionFileStore(file, XorCipher()), io)

        assertNull(store.sessions.value)
        store.load()
        assertEquals(StoredSessions(), store.sessions.value)

        store.update { it.copy(profiles = listOf(profile), activeProfileId = "p1") }
        store.updateProfile("p1") { it.copy(visitorData = "fresh") }

        val reread = SessionFileStore(file, XorCipher()).read()
        assertEquals("fresh", reread.active?.visitorData)
        assertTrue(file.exists())
    }
}

class LoginCaptureRulesTest {

    @Test
    fun `signed in needs a SAPISID-type cookie`() {
        assertTrue(LoginCaptureRules.isSignedIn(mapOf("SAPISID" to "x")))
        assertTrue(LoginCaptureRules.isSignedIn(mapOf("__Secure-3PAPISID" to "x")))
        assertFalse(LoginCaptureRules.isSignedIn(mapOf("YSC" to "x", "VISITOR_INFO1_LIVE" to "y")))
        assertFalse(LoginCaptureRules.isSignedIn(mapOf("SAPISID" to "")))
    }

    @Test
    fun `youtube pages and the rejected page are recognised`() {
        assertTrue(LoginCaptureRules.isYouTubePage("m.youtube.com"))
        assertTrue(LoginCaptureRules.isYouTubePage("youtube.com"))
        assertFalse(LoginCaptureRules.isYouTubePage("youtube.com.evil.example"))
        assertTrue(LoginCaptureRules.isRejectedPage("accounts.google.com", "/v3/signin/rejected"))
        assertFalse(LoginCaptureRules.isRejectedPage("accounts.google.com", "/v3/signin/identifier"))
    }

    @Test
    fun `page config arrives double encoded`() {
        val raw = "\"{\\\"v\\\":\\\"CgtWaXNpdG9y\\\",\\\"d\\\":\\\"990011||107654321\\\",\\\"s\\\":\\\"2\\\",\\\"l\\\":true}\""
        assertEquals(
            LoginCaptureRules.PageConfig("CgtWaXNpdG9y", "990011||107654321", 2, true),
            LoginCaptureRules.parseConfigResult(raw),
        )
        assertNull(LoginCaptureRules.parseConfigResult("null"))
        assertNull(LoginCaptureRules.parseConfigResult("garbage"))
    }

    @Test
    fun `login url goes through YouTube's sign-in handler`() {
        val url = LoginCaptureRules.loginUrl("ru-RU")
        assertTrue(url.startsWith("https://accounts.google.com/ServiceLogin?service=youtube"))
        assertTrue(url.contains("&hl=ru-RU"))
        assertTrue(url.contains("continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue"))
    }
}
