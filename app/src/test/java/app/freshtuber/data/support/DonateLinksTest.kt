package app.freshtuber.data.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DonateLinksTest {
    @Test fun `blank values hide their options`() {
        assertTrue(availableOptions("", " ", "").isEmpty())
        assertEquals(listOf(DonateOption.Abroad), availableOptions("", "https://lava.top/x", ""))
        assertEquals(listOf(DonateOption.Russia, DonateOption.Crypto), availableOptions("https://pay.cloudtips.ru/p/1", "", "UQabc"))
        assertEquals(DonateOption.entries, availableOptions("a", "b", "c"))
    }

    @Test fun `shipped config offers the crypto wallet`() {
        assertEquals(listOf(DonateOption.Crypto), DonateLinks.options)
        assertEquals(UsdtNetwork.TRC20, DonateLinks.USDT_NETWORK)
        // A TRON (TRC20) address: 34 base58 characters starting with T.
        assertTrue(DonateLinks.USDT_ADDRESS.matches(Regex("T[1-9A-HJ-NP-Za-km-z]{33}")))
    }

    @Test fun `long addresses are shortened, short ones kept`() {
        assertEquals("UQAb12…xY9z", shortAddress("UQAb12cdefghijklmnopqrstuvwxY9z"))
        assertEquals("short", shortAddress(" short "))
    }

    @Test fun `only TON has a wallet link`() {
        assertEquals("ton://transfer/UQabc_-1?jetton=$USDT_TON_JETTON", usdtWalletLink(UsdtNetwork.TON, " UQabc_-1 "))
        assertNull(usdtWalletLink(UsdtNetwork.TRC20, "TUzoKgYS74SmxA32a9hqkiPrTyfFKtRaxC"))
    }
}
