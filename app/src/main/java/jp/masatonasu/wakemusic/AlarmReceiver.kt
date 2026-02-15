package jp.masatonasu.wakemusic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L

        val service = Intent(context, AlarmPlayerService::class.java).apply {
            action = AlarmPlayerService.ACTION_START
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmPlayerService.EXTRA_SOURCE, "alarm")
        }
        ContextCompat.startForegroundService(context, service)

        // Schedule next occurrence for this alarm
        if (alarmId > 0) {
            val alarms = AlarmStore.loadAlarms(context)
            alarms.firstOrNull { it.id == alarmId }?.let { AlarmScheduler.schedule(context, it) }
        }
    }

    companion object {
        const val ACTION_ALARM_FIRED = "jp.masatonasu.wakemusic.action.ALARM_FIRED"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
    }
}
