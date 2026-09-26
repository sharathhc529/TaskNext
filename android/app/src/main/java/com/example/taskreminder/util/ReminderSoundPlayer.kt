package com.example.taskreminder.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

object ReminderSoundPlayer {
    private const val TAG = "ReminderSoundPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    @Synchronized
    fun startAlarmSoundAndVibration(context: Context, customSoundUri: String? = null) {
        try {
            stop() // ensure previous is cleared

            // 1. Play the task's chosen sound, falling back to system sounds if it can't be played
            //    (file deleted/moved, access revoked, unsupported format)
            val candidates = listOfNotNull(
                customSoundUri?.let { Uri.parse(it) },
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            )
            mediaPlayer = candidates.firstNotNullOfOrNull { uri -> createLoopingPlayer(context, uri) }
            mediaPlayer?.start()

            // 2. Start Repeating Vibration
            val vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibrator = vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let { vib ->
                if (vib.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(
                            VibrationEffect.createWaveform(vibrationPattern, 0), // 0 means repeat at index 0
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .build()
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(vibrationPattern, 0)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting alarm sound/vibration: ${e.message}", e)
        }
    }

    private fun createLoopingPlayer(context: Context, uri: Uri): MediaPlayer? {
        val player = MediaPlayer()
        return try {
            player.setDataSource(context, uri)
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.isLooping = true
            player.prepare()
            player
        } catch (e: Exception) {
            Log.w(TAG, "Cannot play $uri, trying next sound: ${e.message}")
            player.release()
            null
        }
    }

    @Synchronized
    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media player: ${e.message}")
        }

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping vibrator: ${e.message}")
        }
    }
}
