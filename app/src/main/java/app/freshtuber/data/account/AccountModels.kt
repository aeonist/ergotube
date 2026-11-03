package app.freshtuber.data.account

import kotlinx.serialization.Serializable

/**
 * One signed-in Google account with its own YouTube cookie jar.
 *
 * Every field here is sensitive and lives only in the encrypted session file.
 */
@Serializable
data class AccountProfile(
    val id: String,
    /** youtube.com cookies, name to value. */
    val cookies: Map<String, String>,
    val visitorData: String? = null,
    /** "userSessionId||" for the main channel, "pageId||userSessionId" for a brand channel. */
    val dataSyncId: String? = null,
    /** Index of this account in Google's multi-login list, sent as X-Goog-AuthUser. */
    val sessionIndex: Int = 0,
    val name: String? = null,
    val email: String? = null,
    val handle: String? = null,
    val avatarUrl: String? = null,
    val channels: List<ChannelIdentity> = emptyList(),
    /** Brand channel the user acts as; null means the account's own channel. */
    val activePageId: String? = null,
)

/** A channel the account can act as: its own or a brand channel. */
@Serializable
data class ChannelIdentity(
    val name: String,
    val handle: String? = null,
    val avatarUrl: String? = null,
    /** Null for the account's own channel. */
    val pageId: String? = null,
    val dataSyncId: String? = null,
)

@Serializable
data class StoredSessions(
    val profiles: List<AccountProfile> = emptyList(),
    val activeProfileId: String? = null,
) {
    val active: AccountProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId }
}
