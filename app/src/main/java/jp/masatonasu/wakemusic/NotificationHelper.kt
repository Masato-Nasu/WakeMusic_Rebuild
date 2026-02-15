package jp.masatonasu.wakemusic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = AlarmPlayerService.NOTIF_CHANNEL_ID
        if (nm.getNotificationChannel(id) != null) return
        val ch = NotificationChannel(
            id,
            "WakeMusic Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "WakeMusic alarm playback"
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }
}
