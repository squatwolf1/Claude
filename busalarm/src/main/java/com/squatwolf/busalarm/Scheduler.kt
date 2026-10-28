package com.squatwolf.busalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 다음 알림 시간대가 시작할 때 [WatchService] 를 깨운다.
 * 정확한 알람에서 시작하면 백그라운드에서도 포그라운드 서비스를 띄울 수 있다.
 */
object Scheduler {
    fun reschedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            c, 0, Intent(c, StartReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.cancel(pi)

        val now = LocalDateTime.now()
        val alarms = Store.alarms(c).filter { it.enabled }
        val at = if (alarms.any { it.isActive(now) }) now else alarms.mapNotNull { it.nextStart(now) }.minOrNull()
        if (at == null) return

        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }
}

class StartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WatchService.start(context)
    }
}

/** 재부팅·앱 업데이트·시간 변경 뒤에 알람을 다시 건다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.reschedule(context)
    }
}
