package jp.masatonasu.wakemusic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {
    // NOTE:
    // Android O+ notification channels are immutable once created.
    // To avoid being stuck with a user/OS-modified channel configuration (e.g. forced "silent"),
    // we version channel IDs when behavior needs to change.
    const val CHANNEL_PLAYBACK_ID = "wakemusic_playback_v2"
    const val CHANNEL_ALARM_ID = "wakemusic_alarm_v2"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1) Ongoing foreground-service notification (silent, low importance)
        if (nm.getNotificationChannel(CHANNEL_PLAYBACK_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_PLAYBACK_ID,
                "WakeMusic（再生中）",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "WakeMusic playback (foreground service)"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }

        // 2) Alarm-start control notification (alerting, but with no sound)
        if (nm.getNotificationChannel(CHANNEL_ALARM_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ALARM_ID,
                "WakeMusic（アラーム開始）",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "WakeMusic alarm controls (Stop button / Fullscreen)"
                setShowBadge(false)
                // Keep silent to avoid volume "ducking" / notification sound.
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }
    }
}
