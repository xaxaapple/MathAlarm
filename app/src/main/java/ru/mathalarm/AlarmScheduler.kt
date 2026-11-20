package ru.mathalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {

    private fun firePendingIntent(c: Context, id: Int): PendingIntent {
        val intent = Intent(c, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            c, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancel(c: Context, id: Int) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(firePendingIntent(c, id))
    }

    /** Ставит (или снимает, если выключен) ближайшее срабатывание. Возвращает время срабатывания или null. */
    fun schedule(c: Context, alarm: Alarm): Long? {
        cancel(c, alarm.id)
        if (!alarm.enabled) return null

        val am = c.getSystemService(AlarmManager::class.java)
        val trigger = alarm.nextTriggerMillis()
        val pi = firePendingIntent(c, alarm.id)
        val showIntent = PendingIntent.getActivity(
            c, 100_000 + alarm.id,
            Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                // setAlarmClock — самый надёжный способ: показывается в статус-баре как «будильник»,
                // не откладывается Doze и разрешает запуск службы переднего плана.
                am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, showIntent), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
        return trigger
    }

    fun rescheduleAll(c: Context) {
        AlarmStore.load(c).forEach { schedule(c, it) }
    }
}

/** Срабатывание будильника: перепланирует следующий раз и запускает звонок. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val list = AlarmStore.load(context)
        val alarm = list.find { it.id == id } ?: return

        if (alarm.days == 0) {
            // Одноразовый — выключаем после срабатывания.
            AlarmStore.save(context, list.map { if (it.id == id) it.copy(enabled = false) else it })
        } else {
            AlarmScheduler.schedule(context, alarm)
        }
        AlarmService.start(context, id, alarm.soundUri)
    }

    companion object {
        const val ACTION_FIRE = "ru.mathalarm.FIRE"
        const val EXTRA_ID = "alarm_id"
    }
}

/** Восстанавливает будильники после перезагрузки, смены времени/часового пояса и обновления приложения. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context)
    }
}
