package app.freshtuber.data.support

/**
 * Where people can support the author. Fill in the values to switch the options on; a blank
 * value hides its row, and with all three blank the settings item is hidden too.
 */
object DonateLinks {
    /** CloudTips page: payments from Russia (Mir cards, SBP). */
    val CLOUDTIPS_URL: String = ""

    /** lava.top page: foreign cards and PayPal. */
    val LAVA_URL: String = ""

    /** Wallet address for USDT, on the network in [USDT_NETWORK]. */
    val USDT_ADDRESS: String = "TUzoKgYS74SmxA32a9hqkiPrTyfFKtRaxC"

    /** The network [USDT_ADDRESS] belongs to; USDT sent on another network is lost. */
    val USDT_NETWORK: UsdtNetwork = UsdtNetwork.TRC20

    val options: List<DonateOption> get() = availableOptions(CLOUDTIPS_URL, LAVA_URL, USDT_ADDRESS)
}

enum class DonateOption { Russia, Abroad, Crypto }

/** Networks USDT can be received on. */
enum class UsdtNetwork { TON, TRC20 }

/** The options whose value is set, in the order they are shown. */
fun availableOptions(cloudTipsUrl: String, lavaUrl: String, cryptoAddress: String): List<DonateOption> = buildList {
    if (cloudTipsUrl.isNotBlank()) add(DonateOption.Russia)
    if (lavaUrl.isNotBlank()) add(DonateOption.Abroad)
    if (cryptoAddress.isNotBlank()) add(DonateOption.Crypto)
}

/** A long wallet address cut to its start and end, e.g. `UQAb12…xY9z`. */
fun shortAddress(address: String, head: Int = 6, tail: Int = 4): String {
    val clean = address.trim()
    return if (clean.length <= head + tail + 1) clean else clean.take(head) + "…" + clean.takeLast(tail)
}

/** USDT jetton master contract on TON. */
const val USDT_TON_JETTON = "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"

/**
 * A link that opens a wallet app on a USDT transfer to [address], the sum left to the person.
 * Only TON has such a link that wallets share; for TRC20 there is none and the address is copied.
 */
fun usdtWalletLink(network: UsdtNetwork, address: String): String? = when (network) {
    UsdtNetwork.TON -> "ton://transfer/${java.net.URLEncoder.encode(address.trim(), "UTF-8")}?jetton=$USDT_TON_JETTON"
    UsdtNetwork.TRC20 -> null
}
