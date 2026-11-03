package app.freshtuber.innertube.parse

import app.freshtuber.innertube.arr
import app.freshtuber.innertube.bestThumbnailUrl
import app.freshtuber.innertube.bool
import app.freshtuber.innertube.findAll
import app.freshtuber.innertube.obj
import app.freshtuber.innertube.path
import app.freshtuber.innertube.str
import app.freshtuber.innertube.text
import kotlinx.serialization.json.JsonElement

/** Signed-in account as shown in YouTube's account menu. */
data class AccountMenuInfo(
    val name: String,
    val email: String?,
    val handle: String?,
    val avatarUrl: String?,
)

/** One entry of the channel switcher (the account's own channel or a brand channel). */
data class ChannelEntry(
    val name: String,
    val handle: String?,
    val byline: String?,
    val avatarUrl: String?,
    val isSelected: Boolean,
    val pageId: String?,
    val dataSyncId: String?,
    val signinUrl: String?,
)

/**
 * Parsers for `account/account_menu` and `account/accounts_list`.
 *
 * They search for the renderers by name instead of fixed paths, because the nesting differs
 * between the popup and page variants and changes over time.
 */
object AccountParsers {

    fun parseAccountMenu(root: JsonElement): AccountMenuInfo? {
        val header = root.findAll("activeAccountHeaderRenderer").firstOrNull() ?: return null
        val name = header.obj("accountName").text() ?: return null
        return AccountMenuInfo(
            name = name,
            email = header.obj("email").text(),
            handle = header.obj("channelHandle").text(),
            avatarUrl = header.obj("accountPhoto").bestThumbnailUrl(),
        )
    }

    fun parseAccountsList(root: JsonElement): List<ChannelEntry> =
        (root.findAll("accountItem") + root.findAll("accountItemRenderer"))
            .mapNotNull(::parseAccountItem)
            .distinctBy { it.pageId to it.name }

    private fun parseAccountItem(item: JsonElement): ChannelEntry? {
        val name = item.obj("accountName").text() ?: return null
        val tokens = item.path("serviceEndpoint", "selectActiveIdentityEndpoint").arr("supportedTokens").orEmpty()
        return ChannelEntry(
            name = name,
            handle = item.obj("channelHandle").text(),
            byline = item.obj("accountByline").text(),
            avatarUrl = item.obj("accountPhoto").bestThumbnailUrl(),
            isSelected = item.bool("isSelected") == true,
            pageId = tokens.firstNotNullOfOrNull { it.obj("pageIdToken").str("pageId") },
            dataSyncId = tokens.firstNotNullOfOrNull { it.obj("datasyncIdToken").str("datasyncIdToken") },
            signinUrl = tokens.firstNotNullOfOrNull { it.obj("accountSigninToken").str("signinUrl") },
        )
    }
}
