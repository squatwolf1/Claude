package com.squatwolf.busalarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 알림 시간대 동안 떠 있으면서 도착 정보를 주기적으로 확인하고,
 * 설정한 분 안으로 들어온 차량마다 한 번씩 알린다.
 */
class WatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /** "알림ID/차량" → 알린 시각. 같은 차량에 두 번 알리지 않는다. */
    private val notified = mutableMapOf<String, LocalDateTime>()
    private var nextId = 100

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "busalarm:watch")
            .apply { acquire(6 * 60 * 60 * 1000L) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ServiceCompat.startForeground(
                this, ONGOING_ID, ongoing(listOf("도착 정보 확인 중…")),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive != true) loop = scope.launch { watch() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private suspend fun watch() {
        while (true) {
            val now = LocalDateTime.now()
            val active = Store.alarms(this).filter { it.enabled && it.isActive(now) }
            if (active.isEmpty()) break

            notified.entries.removeAll { it.value.isBefore(now.minusMinutes(30)) }
            val lines = mutableListOf<String>()
            // 가장 가까운 차가 알림 시점까지 5분 넘게 남았으면 천천히 확인해 API 호출량을 아낀다.
            var slack = Int.MAX_VALUE
            for (alarm in active) {
                try {
                    val list = Api.arrivals(this, alarm)
                    val first = list.firstOrNull()
                    lines += "${alarm.title}: " +
                        (first?.let { "${it.name} ${it.etaText}" } ?: "도착 정보 없음")
                    for (a in list) {
                        val left = a.etaSec - alarm.leadMin * 60
                        if (left > 0) slack = minOf(slack, left)
                        if (a.etaSec in 1..alarm.leadMin * 60 && notified.put("${alarm.id}/${a.vehicle}", now) == null) {
                            notifyArrival(alarm, a)
                        }
                    }
                } catch (e: Exception) {
                    lines += "${alarm.title}: ${e.message ?: "불러오기 실패"}"
                }
            }
            notifyIfAllowed(ONGOING_ID, ongoing(lines))
            delay(if (slack > 300) 60_000 else 25_000)
        }
        Scheduler.reschedule(this)
        stopSelf()
    }

    private fun notifyArrival(alarm: Alarm, a: Arrival) {
        val title = when (alarm.kind) {
            Kind.BUS -> "${a.name}번 ${a.etaText} 후 도착"
            Kind.SUBWAY -> "${subwayLines[alarm.lineId]} ${a.name} ${a.etaText} 후 도착"
        }
        val text = listOf(alarm.stationName, a.detail).filter { it.isNotEmpty() }.joinToString(" · ")
        val n = NotificationCompat.Builder(this, ARRIVAL_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(openApp(this))
            .setAutoCancel(true)
            .build()
        notifyIfAllowed(nextId++, n)
    }

    private fun ongoing(lines: List<String>): Notification =
        NotificationCompat.Builder(this, WATCH_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("도착 알림 대기 중")
            .setContentText(lines.firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(this))
            .build()

    companion object {
        const val ARRIVAL_CHANNEL = "arrival"
        private const val WATCH_CHANNEL = "watch"
        private const val ONGOING_ID = 1

        fun start(c: Context) {
            try {
                ContextCompat.startForegroundService(c, Intent(c, WatchService::class.java))
            } catch (e: Exception) {
                // 백그라운드 시작이 막힌 경우. 다음 시간대에 다시 시도한다.
            }
        }

        fun createChannels(c: Context) {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(ARRIVAL_CHANNEL, "도착 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "버스·지하철이 곧 도착할 때"
                    enableVibration(true)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(WATCH_CHANNEL, "대기 중 표시", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "알림 시간대 동안 떠 있는 상태 표시"
                },
            )
        }

        fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** 안드로이드 13 이상에서 알림 권한이 없으면 조용히 건너뛴다. */
@SuppressLint("MissingPermission")
fun Context.notifyIfAllowed(id: Int, n: Notification) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    NotificationManagerCompat.from(this).notify(id, n)
}
