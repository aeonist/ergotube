package app.freshtuber.data.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Expiry never deletes cookies or signs a user out automatically. */
@Singleton
class SessionHealth @Inject constructor() {
    private val expired = MutableStateFlow<Set<String>>(emptySet())
    val expiredProfiles = expired.asStateFlow()
    fun expired(id: String) { expired.update { it + id } }
}
