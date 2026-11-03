package app.freshtuber.innertube.parse

import app.freshtuber.innertube.ResponseContext
import app.freshtuber.innertube.bestThumbnailUrl
import app.freshtuber.innertube.text
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun fixture(name: String): JsonObject {
    val stream = checkNotNull(AccountParsersTest::class.java.getResourceAsStream("/innertube/$name")) { name }
    return Json.parseToJsonElement(stream.bufferedReader().readText()) as JsonObject
}

class AccountParsersTest {

    @Test
    fun `account menu gives name, email, handle and the largest photo`() {
        val info = AccountParsers.parseAccountMenu(fixture("account_menu.json"))
        assertEquals(
            AccountMenuInfo(
                name = "Тестовый Аккаунт",
                email = "fixture@example.test",
                handle = "@fixture",
                avatarUrl = "https://yt3.example.test/photo=s88",
            ),
            info,
        )
    }

    @Test
    fun `account menu without header is null`() {
        assertNull(AccountParsers.parseAccountMenu(fixture("logged_out.json")))
    }

    @Test
    fun `accounts list gives own and brand channels with their tokens`() {
        val channels = AccountParsers.parseAccountsList(fixture("accounts_list.json"))
        assertEquals(2, channels.size)

        val own = channels[0]
        assertEquals("Тестовый Аккаунт", own.name)
        assertTrue(own.isSelected)
        assertNull(own.pageId)
        assertEquals("107654321||", own.dataSyncId)

        val brand = channels[1]
        assertEquals("Бренд Канал", brand.name)
        assertEquals("@brand-fixture", brand.handle)
        assertFalse(brand.isSelected)
        assertEquals("990011", brand.pageId)
        assertEquals("990011||107654321", brand.dataSyncId)
        assertEquals("https://yt3.example.test/brand=s48", brand.avatarUrl)
    }

    @Test
    fun `channel switcher page variant is parsed too`() {
        val page = Json.parseToJsonElement(
            """
            {"actions":[{"updateChannelSwitcherPageAction":{"page":{"channelSwitcherPageRenderer":{"contents":[
              {"unrelatedRenderer":{}},
              {"accountItemRenderer":{"accountName":{"simpleText":"Канал"},"isSelected":true,
                "serviceEndpoint":{"selectActiveIdentityEndpoint":{"supportedTokens":[{"pageIdToken":{"pageId":"42"}}]}}}}
            ]}}}}]}
            """.trimIndent(),
        )
        val channels = AccountParsers.parseAccountsList(page)
        assertEquals(listOf("42"), channels.map { it.pageId })
    }

    @Test
    fun `empty and unrelated responses give no channels`() {
        for (raw in listOf("{}", """{"actions":[{},{"getMultiPageMenuAction":{}}]}""")) {
            assertTrue(AccountParsers.parseAccountsList(Json.parseToJsonElement(raw)).isEmpty())
        }
    }
}

class ResponseContextTest {

    @Test
    fun `reads visitor data and logged out flag`() {
        val loggedOut = fixture("logged_out.json")
        assertEquals("Cgtsb2dnZWRPdXQ%3D", ResponseContext.visitorData(loggedOut))
        assertTrue(ResponseContext.isLoggedOut(loggedOut))
        assertFalse(ResponseContext.isLoggedOut(fixture("account_menu.json")))
    }
}

class JsonPathsTest {

    private fun json(raw: String): JsonElement = Json.parseToJsonElement(raw)

    @Test
    fun `text reads simpleText and joins runs`() {
        assertEquals("a", json("""{"simpleText":"a"}""").text())
        assertEquals("ab", json("""{"runs":[{"text":"a"},{"text":"b"}]}""").text())
        assertNull(json("""{"runs":[]}""").text())
        assertNull(json("""[1]""").text())
    }

    @Test
    fun `best thumbnail is the widest and gets https`() {
        val thumbs = json("""{"thumbnails":[{"url":"//x/1","width":10},{"url":"//x/2","width":20}]}""")
        assertEquals("https://x/2", thumbs.bestThumbnailUrl())
    }
}
