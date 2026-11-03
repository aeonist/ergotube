package app.freshtuber.data.account

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * In-memory copy of the encrypted sessions with serialized, persisted updates.
 *
 * [sessions] is null until [load] finishes.
 */
class SessionStore(
    private val fileStore: SessionFileStore,
    private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val _sessions = MutableStateFlow<StoredSessions?>(null)
    val sessions: StateFlow<StoredSessions?> = _sessions.asStateFlow()

    /** Current value for request building; empty until loaded. */
    val current: StoredSessions get() = _sessions.value ?: StoredSessions()

    suspend fun load() {
        mutex.withLock {
            if (_sessions.value == null) {
                _sessions.value = withContext(io) { fileStore.read() }
            }
        }
    }

    suspend fun update(transform: (StoredSessions) -> StoredSessions): StoredSessions =
        mutex.withLock {
            val before = _sessions.value ?: withContext(io) { fileStore.read() }
            val after = transform(before)
            if (after != before) {
                withContext(io) { fileStore.write(after) }
            }
            _sessions.value = after
            after
        }

    /** Applies [transform] to one profile; does nothing if it was removed meanwhile. */
    suspend fun updateProfile(id: String, transform: (AccountProfile) -> AccountProfile) {
        update { sessions ->
            sessions.copy(profiles = sessions.profiles.map { if (it.id == id) transform(it) else it })
        }
    }
}
