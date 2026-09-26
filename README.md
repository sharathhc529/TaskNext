# ⏰ TaskNext — Task Alarms

**Task alarms that make sure you never miss a thing.** Loud, full-screen reminders that stay until the task is done.

<sub>Kotlin · Jetpack Compose (Material 3) · Room</sub>

**Latest:** [v1.2.1](https://github.com/sharathhc529/TaskReminderApp/releases/latest) · Android 8.0+ · [Contact](mailto:sharathhc529@gmail.com)

---

## ✨ Highlights

- **Today & Tasks screens** — bottom navigation; Today shows what needs attention, Tasks holds the Upcoming / Done / All lists.
- **Next Reminder card** — live countdown to the next alarm, counts up once due. Swipe between 5 styles (Classic, Minimal, Focus, Progress, Large & Clear); your pick is remembered.
- **No action taken** — alarms that rang without being completed stay listed until marked done.
- **Flexible lead time** — remind Days / Hours / Minutes before (default 5 min), with a live "Alarm will ring at" preview or pick the exact time.
- **Per-task alarm sound** — phone ringtones or any audio file, with in-app preview.
- **Full-screen alarm popup** — wakes the screen over the lock screen; Done, Snooze 5m, Dismiss. No notification banner covering it when the app is open.
- **Reliable** — exact alarms, snooze survives reboots, alarms restored after restart.
- **About** — version info, contact email, and a suggestions & feedback form (ⓘ button).

## 🆕 What's new

**1.2.1** — New name **TaskNext** and a new progress-ring icon (fixed boxed launcher icon and blank notification icon) · suggestions & feedback form

**1.2.0** — About screen (ⓘ in the top bar): app version, contact email, check for updates

**1.1.1** — Security hardening; signed, optimized release build

**1.1.0**

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

## ✉️ Contact

Questions, bugs or ideas: **[sharathhc529@gmail.com](mailto:sharathhc529@gmail.com)**

## 🔒 Privacy

TaskNext keeps your tasks on your phone. It only goes online when you send **Suggestions & feedback**: the form emails the developer what you type (name, email, location, message) plus the app and Android version. Location detection is optional, approximate (city level), and only runs when you tap it.
