package app.freshtuber.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryTest {
    @Test fun aDroppedConnectionFirstTriesTheSameAddressThenAsksForNewOnes() {
        val code = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        assertEquals(false, PlaybackRecovery.next(0, code)!!.refresh)
        assertEquals(true, PlaybackRecovery.next(1, code)!!.refresh)
        assertEquals(true, PlaybackRecovery.next(2, code)!!.refresh)
        assertTrue(PlaybackRecovery.next(2, code)!!.delayMs > PlaybackRecovery.next(1, code)!!.delayMs)
    }

    @Test fun anExpiredAddressIsRefreshedAtOnce() {
        assertEquals(true, PlaybackRecovery.next(0, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)!!.refresh)
    }

    @Test fun theErrorIsShownAfterThreeQuietTries() {
        assertNotNull(PlaybackRecovery.next(PlaybackRecovery.MAX_ATTEMPTS - 1, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertNull(PlaybackRecovery.next(PlaybackRecovery.MAX_ATTEMPTS, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
    }

    @Test fun errorsThatATryCannotFixAreShownAtOnce() {
        assertNull(PlaybackRecovery.next(0, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertNull(PlaybackRecovery.next(0, PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED))
    }
}
