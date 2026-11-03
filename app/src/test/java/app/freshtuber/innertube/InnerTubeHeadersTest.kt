package app.freshtuber.innertube

import app.freshtuber.data.account.AccountProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeHeadersTest {

    private val profile = AccountProfile(
        id = "p1",
        cookies = linkedMapOf("SID" to "sid", "SAPISID" to "SAPISID_VALUE"),
        visitorData = "visitor",
        dataSyncId = "107654321||",
        sessionIndex = 1,
    )

    @Test
    fun `anonymous requests carry no account data`() {
        val headers = InnerTubeHeaders.build(null, "visitor", "ru-RU", 1_700_000_000)
        assertFalse("Cookie" in headers)
        assertFalse("Authorization" in headers)
        assertFalse("X-Goog-AuthUser" in headers)
        assertEquals("visitor", headers["X-Goog-Visitor-Id"])
        assertEquals("1", headers["X-YouTube-Client-Name"])
        assertEquals(WebClient.VERSION, headers["X-YouTube-Client-Version"])
        assertEquals("ru-RU", headers["Accept-Language"])
    }

    @Test
    fun `signed-in requests carry cookies, hash with _u and the account index`() {
        val headers = InnerTubeHeaders.build(profile, profile.visitorData, "ru-RU", 1_700_000_000)
        assertEquals("SID=sid; SAPISID=SAPISID_VALUE", headers["Cookie"])
        assertEquals(
            "SAPISIDHASH 1700000000_97829958abf651e688ea855ea78e47ba82a08bbd_u",
            headers["Authorization"],
        )
        assertEquals("https://www.youtube.com", headers["X-Origin"])
        assertEquals("1", headers["X-Goog-AuthUser"])
        assertEquals("true", headers["X-Youtube-Bootstrap-Logged-In"])
        assertFalse("X-Goog-PageId" in headers)
    }

    @Test
    fun `brand channel adds the page id`() {
        val headers = InnerTubeHeaders.build(profile.copy(activePageId = "990011"), null, "en-US", 1)
        assertEquals("990011", headers["X-Goog-PageId"])
    }
}

class CookieRotationTest {

    private val host = "www.youtube.com"
    private val now = 1_700_000_000_000L // 2023-11-14

    @Test
    fun `rotated cookies replace old values and new ones are added`() {
        val result = CookieRotation.apply(
            mapOf("SID" to "s", "SIDCC" to "old"),
            host,
            listOf(
                "SIDCC=new; Domain=.youtube.com; Path=/; Secure",
                "YSC=fresh; Domain=.youtube.com; Path=/; Secure; HttpOnly",
            ),
            now,
        )
        assertEquals(mapOf("SID" to "s", "SIDCC" to "new", "YSC" to "fresh"), result)
    }

    @Test
    fun `max-age zero and past expires remove cookies`() {
        val result = CookieRotation.apply(
            mapOf("LOGIN_INFO" to "x", "PREF" to "p", "SID" to "s"),
            host,
            listOf(
                "LOGIN_INFO=; Domain=.youtube.com; Path=/; Max-Age=0",
                "PREF=; Domain=.youtube.com; Expires=Mon, 01-Jan-1990 00:00:00 GMT; Path=/",
            ),
            now,
        )
        assertEquals(mapOf("SID" to "s"), result)
    }

    @Test
    fun `future expires keeps the cookie and max-age wins over expires`() {
        val result = CookieRotation.apply(
            emptyMap(),
            host,
            listOf(
                "A=1; Expires=Wed, 21 Oct 2026 07:28:00 GMT",
                "B=2; Max-Age=3600; Expires=Mon, 01-Jan-1990 00:00:00 GMT",
            ),
            now,
        )
        assertEquals(mapOf("A" to "1", "B" to "2"), result)
    }

    @Test
    fun `unchanged jar is returned as the same instance`() {
        val current = mapOf("SID" to "s")
        assertSame(current, CookieRotation.apply(current, host, listOf("SID=s; Domain=.youtube.com"), now))
    }

    @Test
    fun `cookies for other domains are ignored`() {
        val current = mapOf("SID" to "s")
        val result = CookieRotation.apply(
            current,
            host,
            listOf("NID=g; Domain=.google.com; Path=/", "X=1; Domain=youtube.com.evil.example"),
            now,
        )
        assertTrue(result === current)
    }
}
