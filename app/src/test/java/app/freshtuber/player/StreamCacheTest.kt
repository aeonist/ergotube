package app.freshtuber.player

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class StreamCacheTest {
    @Test fun `foreground shares extraction already started by preload`() = runTest {
        var calls = 0
        val cache = StreamCache(backgroundScope) { id: String -> calls++; delay(100); "stream:$id" }
        val preload = async { cache.get("next") }
        val foreground = async { cache.get("next") }
        assertEquals(listOf("stream:next", "stream:next"), awaitAll(preload, foreground))
        assertEquals(1, calls)
    }

    @Test fun `cancelled screen does not discard shared extraction`() = runTest {
        var calls = 0
        val started = CompletableDeferred<Unit>()
        val cache = StreamCache(backgroundScope) { _: String -> calls++; started.complete(Unit); delay(100); "ready" }
        val screen = launch { cache.get("next") }
        started.await()
        screen.cancelAndJoin()
        assertEquals("ready", cache.get("next"))
        assertEquals(1, calls)
    }

    @Test fun `expired URLs and evicted items are extracted again`() = runTest {
        var time = 0L
        var calls = 0
        val cache = StreamCache(backgroundScope, ttlMs = 100, capacity = 2, clock = { time }) { id: String -> calls++; id }
        cache.get("a"); cache.get("b"); cache.get("c"); cache.get("a")
        assertEquals(4, calls)
        time = 101
        cache.get("a")
        assertEquals(5, calls)
    }

    @Test fun `failed anonymous preload cannot poison a later explicit attempt`() = runTest {
        var calls = 0
        val cache = StreamCache(backgroundScope) { _: String -> if (++calls == 1) error("offline") else "ready" }
        assertTrue(runCatching { cache.get("next") }.isFailure)
        assertEquals("ready", cache.get("next"))
        assertEquals(2, calls)
    }
}
