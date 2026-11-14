package app.freshtuber.data.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DonateLinksTest {
    private val ton = UsdtWallet(UsdtNetwork.TON, "UQabc")
    private val tron = UsdtWallet(UsdtNetwork.TRC20, "Tabc")

    @Test fun `blank values hide their options`() {
        assertTrue(availableOptions("", " ", listOf(UsdtWallet(UsdtNetwork.TON, " "))).isEmpty())
        assertEquals(listOf(DonateOption.Abroad), availableOptions("", "https://lava.top/x", emptyList()))
        assertEquals(listOf(DonateOption.Russia, DonateOption.Usdt(tron)),
            availableOptions("https://pay.cloudtips.ru/p/1", "", listOf(UsdtWallet(UsdtNetwork.TON, ""), tron)))
        assertEquals(listOf(DonateOption.Russia, DonateOption.Abroad, DonateOption.Usdt(ton), DonateOption.Usdt(tron)),
            availableOptions("a", "b", listOf(ton, tron)))
    }

    @Test fun `shipped config offers TON first, then TRC20`() {
        val wallets = DonateLinks.options.filterIsInstance<DonateOption.Usdt>().map { it.wallet }
        assertEquals(listOf(UsdtNetwork.TON, UsdtNetwork.TRC20), wallets.map { it.network })
        // TON user-friendly address: 48 base64url characters; TRON: 34 base58 characters starting with T.
        assertTrue(wallets[0].address.matches(Regex("[UE]Q[A-Za-z0-9_-]{46}")))
        assertTrue(wallets[1].address.matches(Regex("T[1-9A-HJ-NP-Za-km-z]{33}")))
    }

    @Test fun `long addresses are shortened, short ones kept`() {
        assertEquals("UQAb12…xY9z", shortAddress("UQAb12cdefghijklmnopqrstuvwxY9z"))
        assertEquals("short", shortAddress(" short "))
    }

    @Test fun `only TON has a wallet link`() {
        assertEquals("ton://transfer/UQabc_-1?jetton=$USDT_TON_JETTON", usdtWalletLink(UsdtWallet(UsdtNetwork.TON, " UQabc_-1 ")))
        assertNull(usdtWalletLink(tron))
    }
}
