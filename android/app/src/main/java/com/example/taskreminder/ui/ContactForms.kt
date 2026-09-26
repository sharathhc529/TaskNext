package com.example.taskreminder.ui

import android.Manifest
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.taskreminder.R
import com.example.taskreminder.util.ApproxLocation
import com.example.taskreminder.util.FeedbackSender
import com.example.taskreminder.util.UserProfile
import kotlinx.coroutines.launch

private const val PRIVACY_NOTE =
    "What you enter here, plus the app and Android version, is sent to the developer by email. Nothing else is shared."

/** Location text field with a Detect button (approximate location, asked for only when tapped). */
@Composable
private fun LocationField(value: String, onValueChange: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detecting by remember { mutableStateOf(false) }

    fun detect() {
        detecting = true
        scope.launch {
            val place = ApproxLocation.detect(context)
            detecting = false
            if (place != null) onValueChange(place)
            else Toast.makeText(context, "Couldn't find your location. Please type your city.", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) detect()
        else Toast.makeText(context, "No problem, just type your city.", Toast.LENGTH_SHORT).show()
    }

    // Fill in quietly only if the user already allowed location earlier
    LaunchedEffect(Unit) {
        if (value.isBlank() && ApproxLocation.hasPermission(context)) detect()
    }

    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(120)) },
        label = { Text("Location") },
        placeholder = { Text("City, Country") },
        singleLine = true,
        trailingIcon = {
            if (detecting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = {
                    if (ApproxLocation.hasPermission(context)) detect()
                    else permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Detect my location")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(80)) },
        label = { Text("Your name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun PrivacyNote() {
    Text(PRIVACY_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Suggestions & feedback form: name, email, location, message. */
@Composable
/** [onSent] fires once the feedback is on its way, so the caller can close whatever opened the form. */
fun FeedbackDialog(onDismiss: () -> Unit, onSent: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = remember { UserProfile.load(context) }
    var name by remember { mutableStateOf(saved.name) }
    var email by remember { mutableStateOf(saved.email) }
    var location by remember { mutableStateOf(saved.location) }
    var message by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var handedToMailApp by remember { mutableStateOf(false) }
    val appName = stringResource(R.string.app_name)
    var error by remember { mutableStateOf<String?>(null) }

    val emailValid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val canSend = name.isNotBlank() && emailValid && location.isNotBlank() && message.isNotBlank() && !sending

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
        title = { Text(if (sent) "Thank you!" else "Suggestions & feedback") },
        text = {
            if (sent) {
                Text(
                    if (handedToMailApp) "Your email app is open with your feedback. Tap Send there to finish. Thanks for your valuable time."
                    else "Thanks for your valuable time.",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NameField(name) { name = it }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.take(120) },
                        label = { Text("Email") },
                        singleLine = true,
                        isError = email.isNotBlank() && !emailValid,
                        supportingText = if (email.isNotBlank() && !emailValid) {
                            { Text("Enter a valid email so the developer can reply") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    LocationField(location) { location = it }
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it.take(2000) },
                        label = { Text("Feedback or suggestion") },
                        minLines = 4,
                        maxLines = 8,
                        supportingText = { Text("${message.length} / 2000") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    PrivacyNote()
                }
            }
        },
        confirmButton = {
            if (sent) {
                Button(onClick = onDismiss) { Text("Close") }
            } else {
                Button(
                    enabled = canSend,
                    onClick = {
                        UserProfile.save(context, name = name, email = email, location = location)
                        if (!FeedbackSender.isConfigured) {
                            // No relay in this build yet: hand the same details to the user's email app
                            val body = "$message\n\n- $name ($email)\n$location"
                            if (composeEmail(context, "$appName feedback from $name", body)) {
                                handedToMailApp = true
                                sent = true
                                onSent()
                            }
                            return@Button
                        }
                        sending = true
                        error = null
                        scope.launch {
                            val ok = FeedbackSender.send(
                                context, FeedbackSender.Kind.FEEDBACK,
                                mapOf("name" to name, "email" to email, "location" to location, "message" to message)
                            )
                            sending = false
                            if (ok) {
                                sent = true
                                onSent()
                            } else error = "Couldn't send. Check your internet connection and try again."
                        }
                    }
                ) {
                    if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Save")
                }
            }
        },
        dismissButton = {
            if (!sent && !sending) TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
