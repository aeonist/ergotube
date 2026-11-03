package app.freshtuber.player

import kotlinx.coroutines.*

/** Bounded, short-lived anonymous extraction results; foreground and preload share one request. */
class StreamCache<T>(
    private val scope: CoroutineScope,
    private val ttlMs: Long = 5 * 60_000L,
    private val capacity: Int = 4,
    private val clock: () -> Long = System::currentTimeMillis,
    private val load: suspend (String) -> T,
) {
    private val workerScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private data class Entry<T>(val createdAt: Long, val value: Deferred<T>)
    private val entries = LinkedHashMap<String, Entry<T>>(capacity, 0.75f, true)

    suspend fun get(id: String): T {
        val deferred = synchronized(entries) {
            entries[id]?.takeIf { clock() - it.createdAt < ttlMs && !it.value.isCancelled }?.value ?: run {
                entries.remove(id)?.value?.cancel()
                val task = workerScope.async(start = CoroutineStart.LAZY) { load(id) }
                entries[id] = Entry(clock(), task)
                while (entries.size > capacity) {
                    val oldest = entries.entries.first()
                    entries.remove(oldest.key)?.value?.cancel()
                }
                task
            }
        }
        return try { deferred.await() } catch (e: Exception) {
            // A cancelled screen must not cancel an extraction another screen is awaiting.
            if (deferred.isCompleted) synchronized(entries) {
                if (entries[id]?.value === deferred) entries.remove(id)
            }
            throw e
        }
    }

    fun invalidate(id: String) { synchronized(entries) { entries.remove(id)?.value?.cancel() } }
}
