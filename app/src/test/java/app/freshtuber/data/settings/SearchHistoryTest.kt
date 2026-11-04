package app.freshtuber.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryTest {
    @Test fun newestFirstWithoutDuplicates() {
        val history = SearchHistory.add(SearchHistory.add(SearchHistory.add(emptyList(), "плов"), "дождь"), " Плов ")
        assertEquals(listOf("Плов", "дождь"), history)
        assertEquals(history, SearchHistory.add(history, "   "))
    }

    @Test fun keepsAtMostTheLimit() {
        val history = (1..30).fold(emptyList<String>()) { acc, i -> SearchHistory.add(acc, "q$i") }
        assertEquals(SearchHistory.LIMIT, history.size)
        assertEquals("q30", history.first())
    }

    @Test fun suggestsByPrefixOfAnyWord() {
        val history = listOf("рецепт плова", "звуки дождя для сна", "плов в казане")
        assertEquals(listOf("рецепт плова", "плов в казане"), SearchHistory.suggestions(history, "пло"))
        assertEquals(history, SearchHistory.suggestions(history, ""))
        assertEquals(emptyList<String>(), SearchHistory.suggestions(listOf("плов"), "ПЛОВ"))
    }

    @Test fun encodingRoundTrips() {
        val history = listOf("a b", "c")
        assertEquals(history, SearchHistory.decode(SearchHistory.encode(history)))
        assertEquals(emptyList<String>(), SearchHistory.decode(null))
    }
}
