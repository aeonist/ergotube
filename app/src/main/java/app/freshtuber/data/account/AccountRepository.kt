package app.freshtuber.data.account

import app.freshtuber.core.di.ApplicationScope
import app.freshtuber.innertube.CallAs
import app.freshtuber.innertube.DataSyncId
import app.freshtuber.innertube.InnerTubeClient
import app.freshtuber.innertube.InnerTubeException
import app.freshtuber.innertube.parse.AccountParsers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AccountState {
    data object Loading : AccountState
    data object SignedOut : AccountState
    data class SignedIn(val active: AccountProfile, val all: List<AccountProfile>) : AccountState
}

/** What the login WebView hands over once youtube.com shows the user signed in. */
data class LoginCapture(
    val cookies: Map<String, String>,
    val visitorData: String?,
    val dataSyncId: String?,
    val sessionIndex: Int,
)

@Singleton
class AccountRepository @Inject constructor(
    private val sessions: SessionStore,
    private val innerTube: InnerTubeClient,
    private val webData: WebDataCleaner,
    @ApplicationScope scope: CoroutineScope,
) {
    val state: StateFlow<AccountState> = sessions.sessions
        .map { stored ->
            val active = stored?.active
            when {
                stored == null -> AccountState.Loading
                active == null -> AccountState.SignedOut
                else -> AccountState.SignedIn(active, stored.profiles)
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, AccountState.Loading)

    init {
        scope.launch { sessions.load() }
    }

    /**
     * Verifies a fresh login against YouTube and makes it the active account. A login for an
     * account that is already stored replaces the old entry.
     */
    suspend fun completeLogin(capture: LoginCapture): Result<AccountProfile> {
        val draft = AccountProfile(
            id = UUID.randomUUID().toString(),
            cookies = capture.cookies,
            visitorData = capture.visitorData,
            dataSyncId = capture.dataSyncId,
            sessionIndex = capture.sessionIndex,
            activePageId = DataSyncId.parse(capture.dataSyncId).delegatedSessionId,
        )
        // Stored but not active yet, so rotated cookies during verification are kept.
        sessions.update { it.copy(profiles = it.profiles + draft) }
        return try {
            val menu = innerTube.post("account/account_menu", ACCOUNT_MENU_BODY, CallAs.Profile(draft.id))
            val info = AccountParsers.parseAccountMenu(menu) ?: throw InnerTubeException.SessionExpired()
            val channels = fetchChannels(draft.id)
            var verified: AccountProfile? = null
            sessions.update { stored ->
                val current = stored.profiles.first { it.id == draft.id }.copy(
                    name = info.name,
                    email = info.email,
                    handle = info.handle,
                    avatarUrl = info.avatarUrl,
                    channels = channels,
                ).also { verified = it }
                val others = stored.profiles.filter {
                    it.id != draft.id && !(info.email != null && it.email == info.email)
                }
                stored.copy(profiles = others + current, activeProfileId = current.id)
            }
            Result.success(checkNotNull(verified))
        } catch (e: CancellationException) {
            removeProfile(draft.id)
            throw e
        } catch (e: Exception) {
            removeProfile(draft.id)
            Result.failure(e)
        }
    }

    suspend fun switchProfile(id: String) {
        sessions.update { stored ->
            if (stored.profiles.any { it.id == id }) stored.copy(activeProfileId = id) else stored
        }
    }

    /** Acts as [channel] from now on; the account's own channel has no page id. */
    suspend fun switchChannel(channel: ChannelIdentity) {
        val activeId = sessions.current.activeProfileId ?: return
        sessions.updateProfile(activeId) { profile ->
            profile.copy(
                activePageId = channel.pageId,
                dataSyncId = channel.dataSyncId ?: profile.dataSyncId,
            )
        }
    }

    /**
     * Forgets one account. When it was the last one, every trace of web sessions goes too:
     * WebView cookies and storage, caches and the encrypted file.
     */
    suspend fun signOut(id: String) {
        val remaining = sessions.update { stored ->
            val profiles = stored.profiles.filterNot { it.id == id }
            val active = stored.activeProfileId.takeIf { it != id } ?: profiles.firstOrNull()?.id
            stored.copy(profiles = profiles, activeProfileId = active)
        }
        webData.clearWebViewData()
        if (remaining.profiles.isEmpty()) webData.clearCaches()
    }

    suspend fun signOutEverywhere() {
        sessions.update { StoredSessions() }
        webData.clearWebViewData()
        webData.clearCaches()
    }

    private suspend fun fetchChannels(profileId: String): List<ChannelIdentity> =
        try {
            val list = innerTube.post(
                "account/accounts_list",
                ACCOUNTS_LIST_BODY,
                CallAs.Profile(profileId),
                asChannel = false,
            )
            AccountParsers.parseAccountsList(list).map {
                ChannelIdentity(
                    name = it.name,
                    handle = it.handle,
                    avatarUrl = it.avatarUrl,
                    pageId = it.pageId,
                    dataSyncId = it.dataSyncId,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The switcher is optional: an account without brand channels works fine without it.
            emptyList()
        }

    private suspend fun removeProfile(id: String) {
        sessions.update { stored -> stored.copy(profiles = stored.profiles.filterNot { it.id == id }) }
    }

    private companion object {
        val ACCOUNT_MENU_BODY = JsonObject(emptyMap())
        val ACCOUNTS_LIST_BODY = buildJsonObject {
            put("requestType", "ACCOUNTS_LIST_REQUEST_TYPE_CHANNEL_SWITCHER")
            put("callCircumstance", "SWITCHING_USERS_FULL")
        }
    }
}
