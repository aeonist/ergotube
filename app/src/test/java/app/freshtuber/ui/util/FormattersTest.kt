package app.freshtuber.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `formats minutes and hours`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:07", formatDuration(7))
        assertEquals("4:05", formatDuration(245))
        assertEquals("59:59", formatDuration(3599))
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("1:12:08", formatDuration(4328))
    }

    @Test
    fun `negative is zero`() {
        assertEquals("0:00", formatDuration(-5))
    }
}
