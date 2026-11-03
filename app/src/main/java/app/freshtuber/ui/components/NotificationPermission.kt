package app.freshtuber.ui.components

import android.Manifest
import android.os.Build
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import app.freshtuber.R

@Composable
fun rememberNotificationPermission(onAllowed: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val allowed by rememberUpdatedState(onAllowed)
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) allowed() else denied = true
    }
    if (denied) AlertDialog(onDismissRequest = { denied = false }, title = { Text(stringResource(R.string.notification_permission_title)) },
        text = { Text(stringResource(R.string.notification_permission_hint)) },
        confirmButton = { TextButton(onClick = { denied = false; context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text(stringResource(R.string.nav_settings)) } },
        dismissButton = { TextButton(onClick = { denied = false }) { Text(stringResource(R.string.action_cancel)) } })
    return {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) denied = true
        else allowed()
    }
}
