# 📱 Task & Reminder — Android

Kotlin · Jetpack Compose (Material 3) · Room · AlarmManager

## ✨ Features

- **Today screen** — Next Reminder card (live countdown, counts up when due) + *No action taken* list
- **5 card styles** — Classic, Minimal, Focus, Progress, Large & Clear; swipe to choose, remembered automatically
- **Tasks screen** — Upcoming / Done / All tabs with fixed tab bar
- **Lead time** — Days / Hours / Minutes before due (default 5 min), live ring-time preview, or pick an exact time
- **Alarm sound per task** — phone sounds or audio files, preview button, falls back to default if unavailable
- **Alarm popup** — over lock screen; Done / Snooze 5m / Dismiss (Dismiss silences, task stays open)
- **Reliability** — exact alarms, persisted snooze, reboot rescheduling, permission status cards
- **About** — ⓘ in the top bar: version, contact email, check for updates

## 🏗️ Tech

| Area | Used |
|---|---|
| UI | Jetpack Compose, Material 3, HorizontalPager |
| Data | Room (schema v3, migrations 1→2→3) |
| Alarms | `AlarmManager.setExactAndAllowWhileIdle`, `BroadcastReceiver`, full-screen intent |
| Audio | `MediaPlayer` on alarm stream, `VibratorManager` |

## 🚀 Build

Requires Android Studio (Ladybug+), **JDK 17–21** for Gradle 8.10, Android SDK 35 (min 26).

```bash
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

Release builds are signed from a local, gitignored `keystore.properties`; without it `assembleRelease` outputs an unsigned APK.

## ✉️ Contact

[sharathhc529@gmail.com](mailto:sharathhc529@gmail.com)
