# Proguard configuration for TaskReminder
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
