package app.freshtuber.player

import app.freshtuber.ui.player.SleepDialMath
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepDialTest {
    private fun radians(degrees: Int) = (degrees * PI / 180).toFloat()

    @Test fun `crossing the seam clockwise advances one minute without a jump`() {
        assertEquals(31f, SleepDialMath.turn(30f, radians(177), radians(-177)), 0.001f)
    }

    @Test fun `crossing the seam counterclockwise subtracts one minute`() {
        assertEquals(29f, SleepDialMath.turn(30f, radians(-177), radians(177)), 0.001f)
    }

    @Test fun `rotation is normalized to the calibrated ellipse`() {
        assertEquals(SleepDialMath.angle(100f, 150f, 200f, 300f),
            SleepDialMath.angle(150f, 80f, 300f, 160f), 0.001f)
    }

    @Test fun `rotation stops at the timer bounds`() {
        assertEquals(180f, SleepDialMath.turn(179f, 0f, radians(90)), 0.001f)
        assertEquals(1f, SleepDialMath.turn(2f, 0f, radians(-90)), 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a zero minute timer cannot be started`() { SleepTimer.Minutes(0) }

    @Test(expected = IllegalArgumentException::class)
    fun `an out of range timer cannot be started`() { SleepTimer.Minutes(181) }
}
