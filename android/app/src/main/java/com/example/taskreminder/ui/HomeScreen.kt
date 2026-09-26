package com.example.taskreminder.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskreminder.R
import com.example.taskreminder.data.TaskEntity
import com.example.taskreminder.ui.theme.AccentAmber
import com.example.taskreminder.ui.theme.DangerRed
import com.example.taskreminder.ui.theme.PrimaryBlue
import com.example.taskreminder.ui.theme.PrimaryBlueDark
import com.example.taskreminder.ui.theme.SuccessGreen
import com.example.taskreminder.util.formatReminderOffset
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allTasks by viewModel.allTasks.collectAsState()
    val permissionState by viewModel.permissionState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<TaskEntity?>(null) }
    var selectedScreen by rememberSaveable { mutableIntStateOf(0) } // 0 = Today, 1 = Tasks
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Upcoming, 1 = Completed, 2 = All

    // Periodic ticker for live countdown
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000)
        }
    }

    // Next task = earliest alarm still ahead of now (snooze and pre-reminder offsets included).
    // If every pending alarm has already passed, surface the most recent overdue one instead.
    val pendingTasks = remember(allTasks) { allTasks.filter { it.isPending } }
    val nextTask = pendingTasks
        .filter { it.triggerTimestamp > currentTimeMillis }
        .minByOrNull { it.triggerTimestamp }
        ?: pendingTasks.maxByOrNull { it.triggerTimestamp }
    // Alarms that already rang with no action taken (other than the one on the hero card)
    val dueTasks = pendingTasks
        .filter { it.id != nextTask?.id && it.triggerTimestamp <= currentTimeMillis }
        .sortedByDescending { it.triggerTimestamp }
    val dueCount = pendingTasks.count { it.triggerTimestamp <= currentTimeMillis }

    val filteredTasks = remember(allTasks, selectedTab) {
        when (selectedTab) {
            0 -> allTasks.filter { it.isPending }
            1 -> allTasks.filter { it.isCompleted }
            else -> allTasks
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIconBadge(size = 36.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "About")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Task")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Add Task", fontWeight = FontWeight.Bold)
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedScreen == 0,
                    onClick = { selectedScreen = 0 },
                    icon = {
                        BadgedBox(badge = { if (dueCount > 0) Badge { Text("$dueCount") } }) {
                            Icon(Icons.Default.Today, contentDescription = null)
                        }
                    },
                    label = { Text("Today") }
                )
                NavigationBarItem(
                    selected = selectedScreen == 1,
                    onClick = { selectedScreen = 1 },
                    icon = { Icon(Icons.Default.Checklist, contentDescription = null) },
                    label = { Text("Tasks") }
                )
            }
        }
    ) { paddingValues ->
        when (selectedScreen) {
            0 -> LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Permission Warnings Card
                item {
                    PermissionBanners(permissionState, context)
                }

                // Next Upcoming Task Hero Banner
                item {
                    NextUpcomingHeroCard(
                        nextTask = nextTask,
                        currentTime = currentTimeMillis,
                        onComplete = { task -> viewModel.toggleTaskComplete(task) }
                    )
                }

                // Due tasks with no action taken
                if (dueTasks.isNotEmpty()) {
                    item {
                        DueTasksStrip(
                            tasks = dueTasks,
                            currentTime = currentTimeMillis,
                            onComplete = { task -> viewModel.toggleTaskComplete(task) },
                            onOpen = { task -> taskToEdit = task }
                        )
                    }
                }

                // Bottom spacing for FAB
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            else -> Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tabs stay fixed at the top; only the list below scrolls
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Upcoming (${pendingTasks.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Done (${allTasks.count { it.isCompleted }})") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("All (${allTasks.size})") }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(2.dp)) }

                    if (filteredTasks.isEmpty()) {
                        item {
                            EmptyStateView(selectedTab)
                        }
                    } else {
                        items(filteredTasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                onToggleComplete = { viewModel.toggleTaskComplete(task) },
                                onDelete = { viewModel.deleteTask(task) },
                                onClick = { taskToEdit = task }
                            )
                        }
                    }

                    // Bottom spacing for FAB
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false }, onOpenFeedback = { showFeedback = true })
    }

    if (showFeedback) {
        // Once sent, close About behind the thank-you so closing it lands straight back on the app
        FeedbackDialog(onDismiss = { showFeedback = false }, onSent = { showAbout = false })
    }

    if (showAddDialog) {
        AddEditTaskDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, desc, time, offset, sound ->
                viewModel.addTask(title, desc, time, offset, sound)
            }
        )
    }

    taskToEdit?.let { task ->
        AddEditTaskDialog(
            initialTask = task,
            onDismiss = { taskToEdit = null },
            onSave = { title, desc, time, offset, sound ->
                viewModel.updateTask(
                    task.copy(
                        title = title,
                        description = desc,
                        scheduledTimestamp = time,
                        reminderOffsetMinutes = offset,
                        alarmSoundUri = sound,
                        snoozedUntil = null
                    )
                )
                taskToEdit = null
            }
        )
    }
}

private enum class HeroStatus(val label: String, val color: Color) {
    SCHEDULED("Scheduled", Color.White),
    SNOOZED("Snoozed", AccentAmber),
    DUE("Due", Color(0xFFFCA5A5))
}

/** Visual styles for the Next Reminder card; the one swiped to is remembered as the default. */
private enum class HeroCardStyle(val label: String) {
    CLASSIC("Classic"),
    MINIMAL("Minimal"),
    FOCUS("Focus"),
    PROGRESS("Progress"),
    LARGE("Large & Clear")
}

private const val UI_PREFS = "ui_prefs"
private const val KEY_HERO_CARD_STYLE = "hero_card_style"

/** Everything the card styles display, derived once per tick. */
private data class HeroInfo(
    val task: TaskEntity,
    val now: Long,
    val diffMillis: Long,
    val status: HeroStatus
) {
    val isDue get() = diffMillis <= 0

    /** Countdown caption naming the actual clock time: "until 9:55 AM" / "since 9:30 AM". */
    val clockCaption: String
        get() {
            val time = formatFriendlyTime(task.triggerTimestamp, now).removeSuffix(" · Today")
            return if (isDue) "since $time" else "until $time"
        }
}

private fun heroInfo(task: TaskEntity, now: Long): HeroInfo {
    val diffMillis = task.triggerTimestamp - now
    val status = when {
        diffMillis <= 0 -> HeroStatus.DUE
        task.snoozedUntil != null -> HeroStatus.SNOOZED
        else -> HeroStatus.SCHEDULED
    }
    return HeroInfo(task, now, diffMillis, status)
}

/** "02:14:09" under a day, "3d 04h 12m" beyond that. */
private fun formatCountdown(millis: Long): String {
    val totalSecs = millis / 1000
    val days = totalSecs / 86_400
    val hours = (totalSecs % 86_400) / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (days > 0) {
        String.format(Locale.getDefault(), "%dd %02dh %02dm", days, hours, minutes)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }
}

/** Short form for tight spaces: "45s", "12m", "2h 5m", "3d 4h". */
private fun formatShortCountdown(millis: Long): String {
    val totalMins = millis / 60_000
    return when {
        totalMins < 1 -> "${millis / 1000}s"
        totalMins < 60 -> "${totalMins}m"
        totalMins < 1440 -> "${totalMins / 60}h ${totalMins % 60}m"
        else -> "${totalMins / 1440}d ${totalMins % 1440 / 60}h"
    }
}

/** Plain words, easiest to read at a glance: "in 12 minutes", "2 hours 5 minutes ago". */
private fun formatRelativeWords(diffMillis: Long): String {
    fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit" else "$n ${unit}s"
    val mins = kotlin.math.abs(diffMillis) / 60_000
    if (mins < 1) return if (diffMillis > 0) "in less than a minute" else "just now"
    val text = when {
        mins < 60 -> plural(mins, "minute")
        mins < 1440 -> plural(mins / 60, "hour") + if (mins % 60 > 0) " " + plural(mins % 60, "minute") else ""
        else -> plural(mins / 1440, "day") + if (mins % 1440 / 60 > 0) " " + plural(mins % 1440 / 60, "hour") else ""
    }
    return if (diffMillis > 0) "in $text" else "$text ago"
}

/** "9:35 AM · Today", "9:35 AM · Tomorrow", or "9:35 AM · Mon, Sep 28". */
private fun formatFriendlyTime(timestamp: Long, now: Long): String {
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val sameYear = target.get(Calendar.YEAR) == today.get(Calendar.YEAR)
    val dayDiff = target.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR)
    val day = when {
        sameYear && dayDiff == 0 -> "Today"
        sameYear && dayDiff == 1 -> "Tomorrow"
        sameYear && dayDiff == -1 -> "Yesterday"
        else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(timestamp))
    }
    return "$time · $day"
}

@Composable
fun NextUpcomingHeroCard(
    nextTask: TaskEntity?,
    currentTime: Long,
    onComplete: (TaskEntity) -> Unit
) {
    if (nextTask == null) {
        AllCaughtUpCard()
        return
    }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE) }
    val styles = HeroCardStyle.entries
    val pagerState = rememberPagerState(
        initialPage = prefs.getInt(KEY_HERO_CARD_STYLE, 0).coerceIn(0, styles.lastIndex)
    ) { styles.size }

    // Whatever style the user settles on becomes the default — no save step
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            prefs.edit().putInt(KEY_HERO_CARD_STYLE, page).apply()
        }
    }

    val info = heroInfo(nextTask, currentTime)
    val onDone = { onComplete(nextTask) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top
        ) { page ->
            when (styles[page]) {
                HeroCardStyle.CLASSIC -> ClassicHeroCard(info, onDone)
                HeroCardStyle.MINIMAL -> MinimalHeroCard(info, onDone)
                HeroCardStyle.FOCUS -> FocusHeroCard(info, onDone)
                HeroCardStyle.PROGRESS -> ProgressHeroCard(info, onDone)
                HeroCardStyle.LARGE -> LargeClearHeroCard(info, onDone)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Page dots + style name, so it's obvious the card can be swiped
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            styles.indices.forEach { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(width = if (selected) 18.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) PrimaryBlue
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${styles[pagerState.currentPage].label} · swipe to change style",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AllCaughtUpCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "All caught up!",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "No pending tasks scheduled right now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: HeroStatus, color: Color = status.color) {
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = color
            )
        }
    }
}

// ---------- 1. Classic: branded blue gradient ----------

@Composable
private fun ClassicHeroCard(info: HeroInfo, onDone: () -> Unit) {
    val task = info.task
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF172554), Color(0xFF1E40AF), PrimaryBlue)))
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NEXT REMINDER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.4.sp
                        ),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    StatusPill(info.status)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Countdown and Mark done share one row to keep the card short
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Tabular digits so the countdown doesn't jitter every second
                        Text(
                            text = formatCountdown(kotlin.math.abs(info.diffMillis)),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = if (info.isDue) HeroStatus.DUE.color else Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = info.clockCaption,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onDone,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = PrimaryBlueDark),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Done", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ---------- 2. Minimal: quiet outlined card that follows the app theme ----------

@Composable
private fun MinimalHeroCard(info: HeroInfo, onDone: () -> Unit) {
    val task = info.task
    val accent = if (info.isDue) DangerRed else MaterialTheme.colorScheme.primary
    val statusColor = when (info.status) {
        HeroStatus.SCHEDULED -> MaterialTheme.colorScheme.onSurfaceVariant
        HeroStatus.DUE -> DangerRed
        else -> AccentAmber
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Next reminder · ${info.status.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatCountdown(kotlin.math.abs(info.diffMillis)),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontFeatureSettings = "tnum"
                        ),
                        color = accent,
                        maxLines = 1
                    )
                    Text(
                        text = info.clockCaption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButton(onClick = onDone, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Done")
                }
            }
        }
    }
}

// ---------- 3. Focus: dark, one giant countdown ----------

@Composable
private fun FocusHeroCard(info: HeroInfo, onDone: () -> Unit) {
    val task = info.task
    val countdownColor = if (info.isDue) HeroStatus.DUE.color else Color.White
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (info.isDue) "DUE FOR" else "RINGS IN",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = Color.White.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatCountdown(kotlin.math.abs(info.diffMillis)),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Light,
                    fontFeatureSettings = "tnum"
                ),
                color = countdownColor
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatFriendlyTime(task.triggerTimestamp, info.now),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f)
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Mark done", fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ---------- 4. Progress: ring that fills up as the alarm approaches ----------

@Composable
private fun ProgressHeroCard(info: HeroInfo, onDone: () -> Unit) {
    val task = info.task
    val ringColor = if (info.isDue) DangerRed else MaterialTheme.colorScheme.primary
    // Fraction of the wait (from when the task was created) that has passed
    val totalWait = (task.triggerTimestamp - task.createdAt).coerceAtLeast(1L)
    val progress = ((info.now - task.createdAt).toFloat() / totalWait).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = ringColor,
                    strokeWidth = 9.dp,
                    trackColor = ringColor.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatShortCountdown(kotlin.math.abs(info.diffMillis)),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (info.isDue) "since due" else "left",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(18.dp))

            Column(modifier = Modifier.weight(1f)) {
                StatusPill(
                    info.status,
                    color = when (info.status) {
                        HeroStatus.SCHEDULED -> MaterialTheme.colorScheme.primary
                        HeroStatus.DUE -> DangerRed
                        else -> AccentAmber
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatFriendlyTime(task.triggerTimestamp, info.now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(onClick = onDone, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Done")
                }
            }
        }
    }
}

// ---------- 5. Large & Clear: high contrast, big text, times in words ----------

@Composable
private fun LargeClearHeroCard(info: HeroInfo, onDone: () -> Unit) {
    val task = info.task
    val highlight = Color(0xFFFFD600)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(2.dp, highlight)
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Alarm at ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(task.triggerTimestamp))}",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = highlight
            )
            Text(
                text = formatRelativeWords(info.diffMillis),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = highlight, contentColor = Color.Black),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mark done", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun DueTasksStrip(
    tasks: List<TaskEntity>,
    currentTime: Long,
    onComplete: (TaskEntity) -> Unit,
    onOpen: (TaskEntity) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = DangerRed,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "No action taken (${tasks.size})",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tasks.forEach { task ->
                Card(
                    onClick = { onOpen(task) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Due since ${formatFriendlyTime(task.triggerTimestamp, currentTime)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onComplete(task) }) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Mark ${task.title} done",
                                tint = SuccessGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskCard(
    task: TaskEntity,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("EEE, MMM dd • hh:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(if (task.isCompleted) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggleComplete() },
                colors = CheckboxDefaults.colors(checkedColor = SuccessGreen)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                )

                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = timeFormatter.format(Date(task.snoozedUntil ?: task.scheduledTimestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (task.reminderOffsetMinutes > 0) {
                        Surface(
                            color = PrimaryBlue.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "⏰ ${formatReminderOffset(task.reminderOffsetMinutes)} before",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = PrimaryBlue,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete Task",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun EmptyStateView(selectedTab: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Alarm,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = when (selectedTab) {
                0 -> "No upcoming reminders"
                1 -> "No completed tasks yet"
                else -> "No tasks created yet"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Tap the + button to schedule your next task reminder.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun PermissionBanners(state: PermissionState, context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!state.notificationsGranted) {
            PermissionCard(
                title = "Notification Permission Required",
                desc = "Required to alert you when tasks are due.",
                icon = Icons.Default.Notifications,
                buttonLabel = "Enable",
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            )
        }

        if (!state.exactAlarmGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionCard(
                title = "Exact Alarm Permission",
                desc = "Required to trigger alarms and bring the app to foreground exactly on time.",
                icon = Icons.Default.Alarm,
                buttonLabel = "Grant",
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )
        }

        if (!state.batteryOptimizationIgnored && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PermissionCard(
                title = "Battery Optimization Exemption",
                desc = "Prevents Android from putting alarms to sleep in the background.",
                icon = Icons.Default.BatteryAlert,
                buttonLabel = "Allow",
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )
        }

        if (!state.overlayGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PermissionCard(
                title = "Display Over Other Apps",
                desc = "Allows reminder popups to appear over whatever app you're currently using.",
                icon = Icons.Default.Layers,
                buttonLabel = "Turn On",
                onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = DangerRed)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Text(text = desc, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
            ) {
                Text(text = buttonLabel, fontSize = 12.sp)
            }
        }
    }
}
