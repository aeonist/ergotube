package app.freshtuber.data.settings

/** Recent search queries: newest first, no duplicates, at most [LIMIT]. Pure for tests. */
object SearchHistory {
    const val LIMIT = 20

    fun add(history: List<String>, query: String): List<String> {
        val clean = query.trim().replace('\n', ' ')
        if (clean.isEmpty()) return history
        return (listOf(clean) + history.filterNot { it.equals(clean, ignoreCase = true) }).take(LIMIT)
    }

    /** History entries that start with (or contain a word starting with) what is typed. */
    fun suggestions(history: List<String>, typed: String): List<String> {
        val q = typed.trim()
        if (q.isEmpty()) return history
        return history.filter { entry ->
            !entry.equals(q, ignoreCase = true) &&
                (entry.startsWith(q, ignoreCase = true) || entry.split(' ').any { it.startsWith(q, ignoreCase = true) })
        }
    }

    fun encode(history: List<String>): String = history.joinToString("\n")
    fun decode(raw: String?): List<String> = raw?.split('\n')?.filter(String::isNotBlank).orEmpty()
}
