# 📱 Task & Reminder Android App

Kotlin · Jetpack Compose (Material 3) · Room — full-screen alarms that reliably remind you of upcoming tasks.

**Latest:** [v1.1.0](https://github.com/sharathhc529/TaskReminderApp/releases/latest) · Android 8.0+

---

## ✨ Highlights

- **Today & Tasks screens** — bottom navigation; Today shows what needs attention, Tasks holds the Upcoming / Done / All lists.
- **Next Reminder card** — live countdown to the next alarm, counts up once due. Swipe between 5 styles (Classic, Minimal, Focus, Progress, Large & Clear); your pick is remembered.
- **No action taken** — alarms that rang without being completed stay listed until marked done.
- **Flexible lead time** — remind Days / Hours / Minutes before (default 5 min), with a live "Alarm will ring at" preview or pick the exact time.
- **Per-task alarm sound** — phone ringtones or any audio file, with in-app preview.
- **Full-screen alarm popup** — wakes the screen over the lock screen; Done, Snooze 5m, Dismiss. No notification banner covering it when the app is open.
- **Reliable** — exact alarms, snooze survives reboots, alarms restored after restart.

## 🆕 What's new in 1.1.0

- Snooze now updates the task (countdown, time and after-reboot rescheduling)
- Next Reminder picks the alarm that rings first (reminder offsets and snoozes included)
- Dismiss only silences the alarm — the task stays open until done
- Card styles carousel, Today/Tasks navigation, per-task sounds, Days/Hours/Minutes lead time

## 🚀 Run

1. Open the `android/` folder in Android Studio.
2. Set **Gradle JDK** to 17–21 (Settings → Build Tools → Gradle).
3. Run ▶ on a device or emulator (API 26+).

Or grab the APK from [Releases](https://github.com/sharathhc529/TaskReminderApp/releases).

## 📁 Structure

```
android/app/src/main/java/com/example/taskreminder/
├── data/      Room entity, DAO, database, repository
├── receiver/  AlarmReceiver (ring, notification), BootReceiver
├── util/      AlarmScheduler, ReminderSoundPlayer, formatting
└── ui/        HomeScreen, AddEditTaskDialog, ReminderPopupActivity
```
