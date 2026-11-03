package app.freshtuber.innertube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Expected hashes were computed independently with Python's hashlib. */
class SapisidHashTest {

    private val origin = "https://www.youtube.com"
    private val ts = 1_700_000_000L

    @Test
    fun `basic form is SHA-1 of timestamp sapisid origin`() {
        assertEquals(
            "SAPISIDHASH 1700000000_a9c98f59449b5946d5a6773e5448ba7015de77d3",
            SapisidHash.part("SAPISIDHASH", "SAPISID_VALUE", origin, ts),
        )
    }

    @Test
    fun `user session id is hashed first and marked with _u`() {
        assertEquals(
            "SAPISIDHASH 1700000000_97829958abf651e688ea855ea78e47ba82a08bbd_u",
            SapisidHash.part("SAPISIDHASH", "SAPISID_VALUE", origin, ts, userSessionId = "107654321"),
        )
    }

    @Test
    fun `header carries all three schemes when all cookies exist`() {
        val cookies = mapOf(
            "SAPISID" to "SAPISID_VALUE",
            "__Secure-1PAPISID" to "ONE_P_VALUE",
            "__Secure-3PAPISID" to "THREE_P_VALUE",
        )
        assertEquals(
            "SAPISIDHASH 1700000000_a9c98f59449b5946d5a6773e5448ba7015de77d3 " +
                "SAPISID1PHASH 1700000000_486b8933d229ed61f375881401bc5418aad642ef " +
                "SAPISID3PHASH 1700000000_8fc6af31560ac4ef301cf4bc3b8f64c290259e66",
            SapisidHash.header(cookies, origin, ts),
        )
    }

    @Test
    fun `missing SAPISID falls back to 3PAPISID`() {
        val header = SapisidHash.header(mapOf("__Secure-3PAPISID" to "THREE_P_VALUE"), origin, ts)
        assertEquals(
            "SAPISIDHASH 1700000000_8fc6af31560ac4ef301cf4bc3b8f64c290259e66 " +
                "SAPISID3PHASH 1700000000_8fc6af31560ac4ef301cf4bc3b8f64c290259e66",
            header,
        )
    }

    @Test
    fun `no sid cookies means no header`() {
        assertNull(SapisidHash.header(mapOf("PREF" to "f6=40000000"), origin, ts))
        assertNull(SapisidHash.header(mapOf("SAPISID" to ""), origin, ts))
    }
}

class DataSyncIdTest {

    @Test
    fun `main channel has only a user session id`() {
        assertEquals(DataSyncId(null, "107654321"), DataSyncId.parse("107654321||"))
    }

    @Test
    fun `brand channel has page id then user session id`() {
        assertEquals(DataSyncId("990011", "107654321"), DataSyncId.parse("990011||107654321"))
    }

    @Test
    fun `blank and missing are empty`() {
        assertEquals(DataSyncId(null, null), DataSyncId.parse(null))
        assertEquals(DataSyncId(null, null), DataSyncId.parse(""))
        assertEquals(DataSyncId(null, "lonely"), DataSyncId.parse("lonely"))
    }
}
