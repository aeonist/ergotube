package app.freshtuber.data.support

/**
 * Where people can support the author. Fill in the values to switch the options on; a blank
 * value hides its row, and with nothing set the settings item is hidden too.
 */
object DonateLinks {
    /** CloudTips page: payments from Russia (Mir cards, SBP). */
    val CLOUDTIPS_URL: String = ""

    /** lava.top page: foreign cards and PayPal. */
    val LAVA_URL: String = "https://app.lava.top/2097792502?tabId=donate"

    /** USDT wallets, shown in this order; a blank address hides its row. */
    val USDT_WALLETS: List<UsdtWallet> = listOf(
        UsdtWallet(UsdtNetwork.TON, "UQCE_gE_8LGNM9_8WztoWQcTSqRh3E2uY9CrTstU5IOIv_R2"),
        UsdtWallet(UsdtNetwork.TRC20, "TUzoKgYS74SmxA32a9hqkiPrTyfFKtRaxC"),
    )

    val options: List<DonateOption> get() = availableOptions(CLOUDTIPS_URL, LAVA_URL, USDT_WALLETS)
}

/** Networks USDT can be received on. USDT sent on another network than the address's is lost. */
enum class UsdtNetwork { TON, TRC20 }

data class UsdtWallet(val network: UsdtNetwork, val address: String)

sealed interface DonateOption {
    data object Russia : DonateOption
    data object Abroad : DonateOption
    data class Usdt(val wallet: UsdtWallet) : DonateOption
}

/** The options whose value is set, in the order they are shown. */
fun availableOptions(cloudTipsUrl: String, lavaUrl: String, wallets: List<UsdtWallet>): List<DonateOption> = buildList {
    if (cloudTipsUrl.isNotBlank()) add(DonateOption.Russia)
    if (lavaUrl.isNotBlank()) add(DonateOption.Abroad)
    wallets.filter { it.address.isNotBlank() }.forEach { add(DonateOption.Usdt(it.copy(address = it.address.trim()))) }
}

/** A long wallet address cut to its start and end, e.g. `UQAb12…xY9z`. */
fun shortAddress(address: String, head: Int = 6, tail: Int = 4): String {
    val clean = address.trim()
    return if (clean.length <= head + tail + 1) clean else clean.take(head) + "…" + clean.takeLast(tail)
}

/** USDT jetton master contract on TON. */
const val USDT_TON_JETTON = "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"

/**
 * A link that opens a wallet app on a USDT transfer to the wallet, the sum left to the person.
 * Only TON has such a link that wallets share; for TRC20 there is none and the address is copied.
 */
fun usdtWalletLink(wallet: UsdtWallet): String? = when (wallet.network) {
    UsdtNetwork.TON -> "ton://transfer/${java.net.URLEncoder.encode(wallet.address.trim(), "UTF-8")}?jetton=$USDT_TON_JETTON"
    UsdtNetwork.TRC20 -> null
}
