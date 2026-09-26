package com.example.taskreminder.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.example.taskreminder.R
import com.example.taskreminder.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch

const val CONTACT_EMAIL = "sharathhc529@gmail.com"

@Composable
fun AboutDialog(onDismiss: () -> Unit, onOpenFeedback: () -> Unit) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    // Read from the installed package so it always matches the build (versionName + versionCode)
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val version = packageInfo
        ?.let { "Version ${it.versionName} (${PackageInfoCompat.getLongVersionCode(it)})" }
        ?: "Version unknown"


    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppIconBadge(size = 64.dp) },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(appName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    text = version,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                AboutRow(
                    icon = Icons.Default.Email,
                    label = "Contact",
                    value = CONTACT_EMAIL,
                    onClick = { sendEmail(context, appName, version) }
                )
                AboutRow(
                    icon = Icons.Default.RateReview,
                    label = "Suggestions & feedback",
                    value = "Tell us what to improve",
                    onClick = onOpenFeedback
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

}

/** The launcher icon's own artwork, for use inside the app (header, About). */
@Composable
fun AppIconBadge(size: Dp) {
    Box(modifier = Modifier.size(size).clip(CircleShape)) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.matchParentSize().scale(1.35f)
        )
    }
}

@Composable
private fun AboutRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit, highlight: Boolean = false) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun sendEmail(context: Context, appName: String, version: String) {
    composeEmail(context, subject = "$appName feedback ($version)")
}

/** Opens the user's email app addressed to the developer. Returns false (and copies the address) if there is none. */
internal fun composeEmail(context: Context, subject: String, body: String? = null): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(CONTACT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        body?.let { putExtra(Intent.EXTRA_TEXT, it) }
    }
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        // No email app installed: copy the address instead
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Email", CONTACT_EMAIL))
        Toast.makeText(context, "No email app found. Address copied.", Toast.LENGTH_SHORT).show()
        false
    }
}
