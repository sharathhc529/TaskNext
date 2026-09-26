package com.example.taskreminder.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.taskreminder.data.TaskDatabase
import com.example.taskreminder.receiver.cancelAlarmNotification
import com.example.taskreminder.receiver.showAlarmNotification
import com.example.taskreminder.ui.theme.DangerRed
import com.example.taskreminder.ui.theme.PrimaryBlue
import com.example.taskreminder.ui.theme.SuccessGreen
import com.example.taskreminder.ui.theme.TaskReminderTheme
import com.example.taskreminder.util.AlarmScheduler
import com.example.taskreminder.util.ReminderSoundPlayer
import com.example.taskreminder.util.formatReminderOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderPopupActivity : ComponentActivity() {

    // Compose state so a newer alarm delivered via onNewIntent (singleTask) replaces what's shown
    private var taskId by mutableLongStateOf(-1L)
    private var taskTitle by mutableStateOf("Scheduled Task")
    private var taskDesc by mutableStateOf("")
    private var scheduledTime by mutableLongStateOf(0L)
    private var offsetMins by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        readTaskExtras(intent)

        // Wake screen and show over lockscreen
        turnScreenOnAndDismissKeyguard()

        setContent {
            TaskReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B132B)
                ) {
                    ReminderPopupScreen(
                        title = taskTitle,
                        description = taskDesc,
                        scheduledTime = scheduledTime,
                        offsetMins = offsetMins,
                        onMarkComplete = {
                            handleComplete()
                        },
                        onSnooze = { minutes ->
                            handleSnooze(minutes)
                        },
                        onDismiss = {
                            handleDismiss()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readTaskExtras(intent)
    }

    private fun readTaskExtras(intent: Intent) {
        taskId = intent.getLongExtra(AlarmScheduler.EXTRA_TASK_ID, -1L)
        taskTitle = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_TITLE) ?: "Upcoming Task"
        taskDesc = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_DESC) ?: ""
        scheduledTime = intent.getLongExtra(AlarmScheduler.EXTRA_TASK_SCHEDULED_TIME, System.currentTimeMillis())
        offsetMins = intent.getIntExtra(AlarmScheduler.EXTRA_TASK_OFFSET_MINS, 0)
    }

    override fun onResume() {
        super.onResume()
        // The popup is on top of everything now; the heads-up notification would only cover it
        if (taskId != -1L) cancelAlarmNotification(applicationContext, taskId)
    }

    override fun onStop() {
        super.onStop()
        // Left while still ringing (Home, app switch, screen off): restore the notification so
        // Dismiss/Snooze stay reachable. No full-screen intent, or it would relaunch this popup.
        if (!isFinishing && !isChangingConfigurations && taskId != -1L) {
            showAlarmNotification(
                applicationContext, taskId, taskTitle, taskDesc, scheduledTime, offsetMins,
                fullScreen = false
            )
        }
    }

    private fun turnScreenOnAndDismissKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun handleComplete() {
        ReminderSoundPlayer.stop()
        if (taskId != -1L) {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = TaskDatabase.getDatabase(applicationContext)
                db.taskDao().setTaskCompleted(taskId, true)
            }
        }
        finish()
    }

    private fun handleSnooze(minutes: Int) {
        ReminderSoundPlayer.stop()
        if (taskId != -1L) {
            val snoozedUntil = AlarmScheduler.snoozeTaskAlarm(
                applicationContext,
                taskId,
                taskTitle,
                taskDesc,
                scheduledTime,
                snoozeMinutes = minutes
            )
            lifecycleScope.launch(Dispatchers.IO) {
                val db = TaskDatabase.getDatabase(applicationContext)
                db.taskDao().setTaskSnoozed(taskId, snoozedUntil)
            }
        }
        finish()
    }

    /** Silences the alarm only; the task stays pending and shows as due until marked done. */
    private fun handleDismiss() {
        ReminderSoundPlayer.stop()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        ReminderSoundPlayer.stop()
    }
}

@Composable
fun ReminderPopupScreen(
    title: String,
    description: String,
    scheduledTime: Long,
    offsetMins: Int,
    onMarkComplete: () -> Unit,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val timeFormatter = SimpleDateFormat("hh:mm a, EEE MMM dd", Locale.getDefault())
    val formattedTime = timeFormatter.format(Date(scheduledTime))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .scale(pulseScale)
                    .background(
                        Brush.radialGradient(
                            listOf(PrimaryBlue.copy(alpha = 0.8f), Color.Transparent)
                        ),
                        shape = CircleShape
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(PrimaryBlue, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = "Alarm Icon",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "TASK REMINDER",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = Color(0xFF60A5FA)
            )

            if (offsetMins > 0) {
                Text(
                    text = "Alerted ${formatReminderOffset(offsetMins)} in advance",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Center Task Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1C2541)
            ),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .background(Color(0xFF3A506B), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Due: $formattedTime",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFE0E1DD)
                    )
                }
            }
        }

        // Bottom Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mark Done Button
            Button(
                onClick = onMarkComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mark as Done",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Snooze & Dismiss Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onSnooze(5) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                ) {
                    Icon(Icons.Default.Snooze, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Snooze 5m", color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = DangerRed)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Dismiss", color = DangerRed, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
