package app.freshtuber.data.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DonateLinksTest {
    @Test fun `blank values hide their options`() {
        assertTrue(availableOptions("", " ", "").isEmpty())
        assertEquals(listOf(DonateOption.Abroad), availableOptions("", "https://lava.top/x", ""))
        assertEquals(listOf(DonateOption.Russia, DonateOption.Crypto), availableOptions("https://pay.cloudtips.ru/p/1", "", "UQabc"))
        assertEquals(DonateOption.entries, availableOptions("a", "b", "c"))
    }

    @Test fun `shipped config has no options yet`() {
        // The real values are added later; until then the settings item stays hidden.
        assertTrue(DonateLinks.options.isEmpty())
    }

    @Test fun `long addresses are shortened, short ones kept`() {
        assertEquals("UQAb12…xY9z", shortAddress("UQAb12cdefghijklmnopqrstuvwxY9z"))
        assertEquals("short", shortAddress(" short "))
    }

    @Test fun `ton link asks a wallet for a USDT transfer`() {
        assertEquals("ton://transfer/UQabc_-1?jetton=$USDT_TON_JETTON", tonTransferLink(" UQabc_-1 "))
    }
}
