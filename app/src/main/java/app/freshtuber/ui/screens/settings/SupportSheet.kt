package app.freshtuber.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CurrencyBitcoin
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.freshtuber.R
import app.freshtuber.data.support.DonateLinks
import app.freshtuber.data.support.DonateOption
import app.freshtuber.data.support.UsdtNetwork
import app.freshtuber.data.support.UsdtWallet
import app.freshtuber.data.support.usdtWalletLink
import app.freshtuber.data.support.shortAddress
import app.freshtuber.ui.components.BlurSheet
import app.freshtuber.ui.components.LocalAppMessages

/** The settings row that opens the support sheet, in the same card style as the account row. */
@Composable
fun SupportAuthorRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocalCafe, null)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(stringResource(R.string.support_author), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.support_author_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Ways to support the author; a row shows only when its value is set in [DonateLinks]. */
@Composable
fun SupportSheet(onDismiss: () -> Unit, options: List<DonateOption>) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val messages = LocalAppMessages.current
    val openFailed = stringResource(R.string.support_open_failed)
    val copied = stringResource(R.string.support_copied)
    val clear = ListItemDefaults.colors(containerColor = Color.Transparent)
    fun open(url: String) { runCatching { uriHandler.openUri(url) }.onFailure { messages?.show(openFailed) } }
    fun copy(wallet: UsdtWallet) {
        copyToClipboard(context, wallet.address)
        // Android 13 and newer confirm a copy themselves.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) messages?.show(copied)
    }
    BlurSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.support_author), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            Text(stringResource(R.string.support_sheet_text), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp))
            options.forEach { option ->
                when (option) {
                    DonateOption.Russia -> ListItem(colors = clear,
                        leadingContent = { Icon(Icons.Rounded.CreditCard, null) },
                        headlineContent = { Text(stringResource(R.string.support_russia)) },
                        modifier = Modifier.heightIn(min = 56.dp).clickable { open(DonateLinks.CLOUDTIPS_URL.trim()) })
                    DonateOption.Abroad -> ListItem(colors = clear,
                        leadingContent = { Icon(Icons.Rounded.Public, null) },
                        headlineContent = { Text(stringResource(R.string.support_abroad)) },
                        modifier = Modifier.heightIn(min = 56.dp).clickable { open(DonateLinks.LAVA_URL.trim()) })
                    is DonateOption.Usdt -> {
                        val wallet = option.wallet
                        val (title, hint) = when (wallet.network) {
                            UsdtNetwork.TON -> R.string.support_usdt_ton to R.string.support_network_ton
                            UsdtNetwork.TRC20 -> R.string.support_usdt_trc20 to R.string.support_network_trc20
                        }
                        ListItem(colors = clear,
                            leadingContent = { Icon(Icons.Rounded.CurrencyBitcoin, null) },
                            headlineContent = { Text(stringResource(title)) },
                            supportingContent = {
                                Column {
                                    Text(shortAddress(wallet.address))
                                    // Coins sent on another network are lost, so say which one plainly.
                                    Text(stringResource(hint), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { copy(wallet) }) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.support_copy)) }
                            },
                            // On TON a wallet app opens on the transfer; otherwise (or with no wallet) the address is copied.
                            modifier = Modifier.heightIn(min = 64.dp).clickable {
                                val link = usdtWalletLink(wallet)
                                if (link == null || !openWallet(context, link)) copy(wallet)
                            })
                    }
                }
            }
        }
    }
}

private fun openWallet(context: Context, link: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("USDT", text))
}
