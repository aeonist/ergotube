package app.freshtuber.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieHostPolicyTest {

    @Test
    fun `allows youtube and googlevideo hosts`() {
        assertTrue(CookieHostPolicy.isAllowed("youtube.com"))
        assertTrue(CookieHostPolicy.isAllowed("www.youtube.com"))
        assertTrue(CookieHostPolicy.isAllowed("m.youtube.com"))
        assertTrue(CookieHostPolicy.isAllowed("rr3---sn-abc.googlevideo.com"))
        assertTrue(CookieHostPolicy.isAllowed("WWW.YOUTUBE.COM."))
    }

    @Test
    fun `rejects look-alike and unrelated hosts`() {
        assertFalse(CookieHostPolicy.isAllowed("youtube.com.evil.example"))
        assertFalse(CookieHostPolicy.isAllowed("notyoutube.com"))
        assertFalse(CookieHostPolicy.isAllowed("i.ytimg.com"))
        assertFalse(CookieHostPolicy.isAllowed("sponsor.ajay.app"))
        assertFalse(CookieHostPolicy.isAllowed("accounts.google.com"))
    }
}

class CookieHeaderTest {

    @Test
    fun `merges headers and later values win`() {
        assertEquals(
            "SID=b; HSID=h; SOCS=x",
            CookieHeader.merge("SID=a; HSID=h", "SID=b; SOCS=x"),
        )
    }

    @Test
    fun `parses a cookie header into a map`() {
        assertEquals(
            mapOf("SID" to "a=b", "HSID" to "h"),
            CookieHeader.parse("SID=a=b; HSID=h; broken; =nameless"),
        )
        assertEquals(emptyMap<String, String>(), CookieHeader.parse(null))
    }

    @Test
    fun `ignores nulls and blanks`() {
        assertEquals("A=1", CookieHeader.merge(null, " A=1 ; ;", null))
        assertNull(CookieHeader.merge(null, "", " ; "))
    }
}
