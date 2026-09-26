package com.example.taskreminder.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskreminder.data.TaskEntity
import com.example.taskreminder.util.formatReminderOffset
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val TAG = "AddEditTaskDialog"
private const val PREVIEW_MAX_MS = 15_000L

/** Human-readable name for a sound URI: file name, ringtone title, or "Default alarm". */
private fun soundDisplayName(context: Context, uriString: String?): String {
    if (uriString == null) return "Default alarm"
    val uri = Uri.parse(uriString)
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getString(0).substringBeforeLast('.')
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read sound name for $uri: ${e.message}")
    }
    return try {
        RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Custom sound"
    } catch (e: Exception) {
        "Custom sound"
    }
}

private const val DEFAULT_OFFSET_MINUTES = 5

/** Keeps only digits and caps the number at [max] (e.g. 23 for hours). */
private fun sanitizeDuration(input: String, max: Int): String {
    val digits = input.filter { it.isDigit() }.take(max.toString().length)
    return if ((digits.toIntOrNull() ?: 0) > max) max.toString() else digits
}

@Composable
private fun DurationField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskDialog(
    initialTask: TaskEntity? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, scheduledTime: Long, reminderOffset: Int, alarmSoundUri: String?) -> Unit
) {
    val context = LocalContext.current

    var alarmSoundUri by remember { mutableStateOf(initialTask?.alarmSoundUri) }
    val alarmSoundName = remember(alarmSoundUri) { soundDisplayName(context, alarmSoundUri) }

    // Sound preview (plays at alarm volume so it sounds like the real thing)
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    fun stopPreview() {
        previewPlayer?.release()
        previewPlayer = null
    }
    fun startPreview() {
        stopPreview()
        val uri = alarmSoundUri?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val player = MediaPlayer()
        try {
            player.setDataSource(context, uri)
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setOnCompletionListener { stopPreview() }
            player.prepare()
            player.start()
            previewPlayer = player
        } catch (e: Exception) {
            Log.w(TAG, "Preview failed for $uri: ${e.message}")
            player.release()
            Toast.makeText(context, "Can't play this sound", Toast.LENGTH_SHORT).show()
        }
    }
    // Stop when the sound changes, after a short clip, and when the dialog closes
    LaunchedEffect(alarmSoundUri) { stopPreview() }
    LaunchedEffect(previewPlayer) {
        if (previewPlayer != null) {
            delay(PREVIEW_MAX_MS)
            stopPreview()
        }
    }
    DisposableEffect(Unit) { onDispose { stopPreview() } }

    val audioFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Keep read access across reboots so the alarm can still play this file later
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.w(TAG, "Persistable permission not granted for $uri: ${e.message}")
            }
            alarmSoundUri = uri.toString()
        }
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            // Picking the "Default" entry is stored as null so it follows the phone's alarm setting
            alarmSoundUri = picked
                ?.takeIf { it != RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) }
                ?.toString()
        }
    }

    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var description by remember { mutableStateOf(initialTask?.description ?: "") }
    // How long before the due time the alarm rings, as days / hours / minutes (default 0 / 0 / 5).
    // Empty fields count as 0.
    val initialOffset = initialTask?.reminderOffsetMinutes ?: DEFAULT_OFFSET_MINUTES
    var offsetDays by remember { mutableStateOf((initialOffset / 1440).toString()) }
    var offsetHours by remember { mutableStateOf((initialOffset % 1440 / 60).toString()) }
    var offsetMinutes by remember { mutableStateOf((initialOffset % 60).toString()) }
    val selectedOffsetMinutes = (offsetDays.toIntOrNull() ?: 0) * 1440 +
        (offsetHours.toIntOrNull() ?: 0) * 60 +
        (offsetMinutes.toIntOrNull() ?: 0)

    val calendar = remember {
        Calendar.getInstance().apply {
            if (initialTask != null) {
                timeInMillis = initialTask.scheduledTimestamp
            } else {
                // Default to 15 minutes from now
                add(Calendar.MINUTE, 15)
            }
        }
    }

    var selectedTimestamp by remember { mutableStateOf(calendar.timeInMillis) }

    val dateFormatter = remember { SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                selectedTimestamp = calendar.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    val timePickerDialog = remember {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                selectedTimestamp = calendar.timeInMillis
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        )
    }

    val calculatedTriggerTime = selectedTimestamp - selectedOffsetMinutes * 60_000L
    val ringTimeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val ringDateFormatter = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }

    // Alternative to typing: pick the exact ring date & time; the fields are filled in from it
    fun pickExactReminderTime() {
        val ringAt = Calendar.getInstance().apply { timeInMillis = calculatedTriggerTime }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                ringAt.set(year, month, dayOfMonth)
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        ringAt.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        ringAt.set(Calendar.MINUTE, minute)
                        ringAt.set(Calendar.SECOND, 0)
                        ringAt.set(Calendar.MILLISECOND, 0)
                        val leadMinutes = ((selectedTimestamp - ringAt.timeInMillis + 30_000L) / 60_000L).toInt()
                        if (leadMinutes < 0) {
                            Toast.makeText(context, "The alarm can't ring after the due time", Toast.LENGTH_SHORT).show()
                        } else {
                            offsetDays = (leadMinutes / 1440).toString()
                            offsetHours = (leadMinutes % 1440 / 60).toString()
                            offsetMinutes = (leadMinutes % 60).toString()
                        }
                    },
                    ringAt.get(Calendar.HOUR_OF_DAY),
                    ringAt.get(Calendar.MINUTE),
                    false
                ).show()
            },
            ringAt.get(Calendar.YEAR),
            ringAt.get(Calendar.MONTH),
            ringAt.get(Calendar.DAY_OF_MONTH)
        ).apply { datePicker.maxDate = selectedTimestamp }.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialTask == null) "Schedule New Task" else "Edit Task",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Task Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title *") },
                    placeholder = { Text("e.g., Client meeting, Take medicine...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Task Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes / Details (Optional)") },
                    placeholder = { Text("Additional information...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Date & Time Picker Buttons
                Text(
                    text = "When is this task scheduled?",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { datePickerDialog.show() },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = dateFormatter.format(Date(selectedTimestamp)),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = { timePickerDialog.show() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = timeFormatter.format(Date(selectedTimestamp)),
                            fontSize = 12.sp
                        )
                    }
                }

                // Reminder lead time
                Text(
                    text = "Remind me before the due time",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DurationField("Days", offsetDays, { offsetDays = sanitizeDuration(it, 365) }, Modifier.weight(1f))
                    DurationField("Hours", offsetHours, { offsetHours = sanitizeDuration(it, 23) }, Modifier.weight(1f))
                    DurationField("Minutes", offsetMinutes, { offsetMinutes = sanitizeDuration(it, 59) }, Modifier.weight(1f))
                }

                // Live result: when the alarm will actually ring. Tap to pick an exact time instead.
                Surface(
                    onClick = { pickExactReminderTime() },
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Alarm will ring at",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = ringTimeFormatter.format(Date(calculatedTriggerTime)),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = ringDateFormatter.format(Date(calculatedTriggerTime)) + " · " +
                                    if (selectedOffsetMinutes == 0) "at due time"
                                    else "${formatReminderOffset(selectedOffsetMinutes)} before due",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Pick exact alarm time",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Alarm sound picker
                Text(
                    text = "Alarm sound",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = alarmSoundName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 10.dp)
                        )
                        IconButton(onClick = { if (previewPlayer != null) stopPreview() else startPreview() }) {
                            Icon(
                                imageVector = if (previewPlayer != null) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (previewPlayer != null) "Stop preview" else "Preview sound",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (alarmSoundUri != null) {
                            TextButton(onClick = { alarmSoundUri = null }) {
                                Text("Reset")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                )
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    alarmSoundUri?.let { Uri.parse(it) }
                                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                )
                            }
                            stopPreview()
                            ringtoneLauncher.launch(intent)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.LibraryMusic, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Phone sounds", fontSize = 12.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            stopPreview()
                            audioFileLauncher.launch(arrayOf("audio/*"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("From files", fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        Toast.makeText(context, "Please enter a task title", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (calculatedTriggerTime <= System.currentTimeMillis()) {
                        Toast.makeText(
                            context,
                            "The reminder trigger time is in the past! Please select a future time.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }

                    onSave(title, description, selectedTimestamp, selectedOffsetMinutes, alarmSoundUri)
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (initialTask == null) "Schedule Task" else "Update Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
