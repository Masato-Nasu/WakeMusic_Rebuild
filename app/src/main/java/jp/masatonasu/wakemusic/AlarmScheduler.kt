package jp.masatonasu.wakemusic

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

object AlarmScheduler {

    fun schedule(context: Context, alarm: AlarmItem) {
        if (!alarm.enabled) return

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!am.canScheduleExactAlarms()) {
                // Ask user for permission
                val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(i) }
                return
            }
        }

        val triggerAtMillis = nextOccurrenceMillis(alarm)
        val pi = pendingIntent(context, alarm.id)

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
            else -> {
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        }
    }

    fun cancel(context: Context, alarmId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, alarmId))
    }

    fun rescheduleAllEnabled(context: Context) {
        AlarmStore.loadAlarms(context)
            .filter { it.enabled }
            .forEach { schedule(context, it) }
    }

    private fun pendingIntent(context: Context, alarmId: Long): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_FIRED
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, alarmId.toInt(), i, flags)
    }

    fun nextOccurrenceMillis(alarm: AlarmItem): Long {
        val now = java.util.Calendar.getInstance()
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            set(java.util.Calendar.HOUR_OF_DAY, alarm.hour)
            set(java.util.Calendar.MINUTE, alarm.minute)
        }
        if (cal.timeInMillis <= now.timeInMillis) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
