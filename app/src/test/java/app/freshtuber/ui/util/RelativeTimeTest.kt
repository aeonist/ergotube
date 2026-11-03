package app.freshtuber.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    private fun ago(seconds: Long) = ageOf(seconds * 1000)

    @Test
    fun `picks the largest whole unit`() {
        assertEquals(Age(42, AgeUnit.Seconds), ago(42))
        assertEquals(Age(5, AgeUnit.Minutes), ago(5 * 60 + 59))
        assertEquals(Age(5, AgeUnit.Hours), ago(5 * 3600))
        assertEquals(Age(2, AgeUnit.Days), ago(2 * 86_400 + 100))
        assertEquals(Age(3, AgeUnit.Weeks), ago(21 * 86_400))
        assertEquals(Age(4, AgeUnit.Months), ago(125 * 86_400))
        assertEquals(Age(8, AgeUnit.Years), ago(8 * 365 * 86_400 + 1000))
    }

    @Test
    fun `future dates count as zero seconds`() {
        assertEquals(Age(0, AgeUnit.Seconds), ageOf(-5_000))
    }
}
