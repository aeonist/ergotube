package app.freshtuber.data.account

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class TooManyActionsException : IOException("Too many account actions in a short time")

/**
 * Careful mode for the user's main account: account actions (likes, subscriptions, comments,
 * playlist edits) are spaced out and capped, so the app never looks like a bot.
 */
@Singleton
class ActionGuard @Inject constructor() {
    private val mutex = Mutex()
    private val recent = ArrayDeque<Long>()

    suspend fun <T> run(block: suspend () -> T): T {
        mutex.withLock {
            val now = System.currentTimeMillis()
            while (recent.isNotEmpty() && now - recent.first() > WINDOW_MS) recent.removeFirst()
            if (recent.size >= MAX_ACTIONS_PER_WINDOW) throw TooManyActionsException()
            recent.lastOrNull()?.let { last ->
                val wait = MIN_GAP_MS - (now - last)
                if (wait > 0) delay(wait)
            }
            recent.addLast(System.currentTimeMillis())
        }
        return block()
    }

    private companion object {
        const val MIN_GAP_MS = 1_200L
        const val WINDOW_MS = 10 * 60 * 1000L
        const val MAX_ACTIONS_PER_WINDOW = 40
    }
}
