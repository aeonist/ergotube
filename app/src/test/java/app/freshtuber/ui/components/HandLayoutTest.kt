package app.freshtuber.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class HandLayoutTest {
    @Test fun firstItemSitsOnTheWorkingHandSide() {
        assertEquals(listOf("c", "b", "a"), handOrder(listOf("a", "b", "c"), left = false))
        assertEquals(listOf("a", "b", "c"), handOrder(listOf("a", "b", "c"), left = true))
    }

    @Test fun gridFillsBottomUpFromTheHandCorner() {
        val items = listOf(1, 2, 3, 4, 5)
        // Right hand: item 1 in the bottom-right cell, the short row on top.
        assertEquals(listOf(null, 5, 4, 3, 2, 1), handGrid(items, 3, left = false))
        // Left hand: item 1 in the bottom-left cell.
        assertEquals(listOf(4, 5, null, 1, 2, 3), handGrid(items, 3, left = true))
    }
}
