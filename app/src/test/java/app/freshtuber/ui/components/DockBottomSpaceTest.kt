package app.freshtuber.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class DockBottomSpaceTest {
    @Test fun gestureNavigationLetsTheDropOverlapTheUpperHalfOfTheBar() {
        // A 24 dp gesture bar takes no taps: 12 dp of it + 4 dp of air (was 14 + 24 = 38 dp).
        assertEquals(16f, Reach.dockBottomSpace(24.dp, 0.dp).value, 0.001f)
    }

    @Test fun threeButtonNavigationKeepsItsWholeBar() {
        assertEquals(52f, Reach.dockBottomSpace(48.dp, 48.dp).value, 0.001f)
    }

    @Test fun noBarStillKeepsAirUnderTheDrop() {
        assertEquals(4f, Reach.dockBottomSpace(0.dp, 0.dp).value, 0.001f)
    }
}
