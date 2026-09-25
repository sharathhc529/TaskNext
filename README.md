# 📱 Task & Reminder Android App

A modern Android application built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Room Database**. It runs reliably in the background and brings up full-screen alarms with custom audio and vibration to remind you of your upcoming tasks.

---

## 🌟 Features

1. **⚡ Next Task Up Hero Banner**:
   - Displays your imminent upcoming task with a **real-time live countdown** (hours, minutes, seconds).
   - Instant "Mark as Done" one-tap completion.

2. **🔔 Pre-Reminder Trigger Choices**:
   - Choose when the alarm should bring the app to the foreground:
     - ⏱ **Exact Time** (0 minutes)
     - ⏱ **5 minutes before**
     - ⏱ **30 minutes before**
     - ⏱ **1 hour before**
     - ⏱ **3 hours before**
     - ⏱ **1 day before**
   - Automatically computes and displays the exact popup trigger timestamp.

3. **🚨 Full-Screen Foreground Alarm Popup**:
   - Wakes up the screen even when locked (`USE_FULL_SCREEN_INTENT`, `setShowWhenLocked(true)`, `setTurnScreenOn(true)`).
   - Loops alarm audio and repeating vibration until interacted with.
   - Quick actions:
     - 🟢 **Mark as Done**
     - 🟡 **Snooze (5 min)**
     - 🔴 **Dismiss**

4. **🔋 Background Reliability & Reboot Recovery**:
   - Uses `AlarmManager.setExactAndAllowWhileIdle()` for battery-optimized exact timing.
   - Listens to `BOOT_COMPLETED` to automatically reschedule all pending alarms after device reboot.

5. **🛡️ Comprehensive Permission Management**:
   - In-app status cards and direct links to Android Settings for:
     - **Post Notifications** (`POST_NOTIFICATIONS` - Android 13+)
     - **Schedule Exact Alarms** (`SCHEDULE_EXACT_ALARM` - Android 12+)
     - **Battery Optimization Exemption** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
     - **Display Over Other Apps** (`SYSTEM_ALERT_WINDOW`)

---

## 📁 Repository Structure

```
├── android/                        # 📱 Native Android Project (Kotlin + Jetpack Compose)
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml # Permissions, fullScreenIntent, receivers
│   │   │   ├── java/com/example/taskreminder/
│   │   │   │   ├── data/           # Room Database, DAO, Entity, Repository
│   │   │   │   ├── receiver/       # AlarmReceiver, BootReceiver
│   │   │   │   ├── util/           # AlarmScheduler, ReminderSoundPlayer
│   │   │   │   └── ui/             # HomeScreen, AddEditTaskDialog, ReminderPopupActivity
│   │   │   └── res/                # Themes, strings, drawables, launcher icons
│   │   └── build.gradle.kts
│   ├── gradle/libs.versions.toml   # Dependency Version Catalog
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── backend/                        # Optional Backend Services
├── frontend/                       # Optional Web Dashboard
└── README.md
```

---

## 🚀 How to Run in Android Studio

1. Open **Android Studio**.
2. Click **Open** and select the **`android/`** folder.
3. Allow Gradle to sync.
4. Select your connected device or emulator and click **Run (▶)**.
