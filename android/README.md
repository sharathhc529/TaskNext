# 📱 Task & Reminder Android App

A modern Android application built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Room Database**. It runs reliably in the background and brings up full-screen alarms with custom audio and vibration to remind you of your upcoming tasks.

---

## ✨ Features

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

## 🏗️ Architecture & Tech Stack

- **Language**: Kotlin 2.0+
- **UI Toolkit**: Jetpack Compose + Material 3
- **Database**: Room Database (SQLite with Coroutines `Flow` for reactive UI updates)
- **Background Engine**: `AlarmManager` + `BroadcastReceiver` + `WakeLock`
- **Alarm Audio & Haptics**: `MediaPlayer` (Alarm stream) + `VibratorManager`
- **Build System**: Gradle Version Catalogs (`libs.versions.toml`)

---

## 🚀 Getting Started

### Prerequisites
- [Android Studio Ladybug (or newer)](https://developer.android.com/studio)
- JDK 17+
- Android SDK 35 (Min SDK: 26 / Android 8.0+)

### Opening in Android Studio
1. Open Android Studio.
2. Select **Open** and choose the `android/` directory.
3. Allow Gradle to sync dependencies.
4. Run on an Android Device or Emulator (API 26+).

### Building with Command Line
```bash
# Debug APK
./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📦 GitHub Setup & CI/CD
This repository includes a pre-configured GitHub Actions workflow located at `.github/workflows/android-build.yml`.

Whenever you push to `main` or create a PR:
1. GitHub Actions automatically installs JDK 17.
2. Compiles the Kotlin & Jetpack Compose codebase.
3. Builds and uploads the debug APK as a downloadable artifact.
