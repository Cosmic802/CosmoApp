package com.cosmo.butler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusWatchdogService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var targetPackages: Set<String> = emptySet()

    companion object {
        const val CHANNEL_ID = "cosmo_focus_watchdog"
        const val NOTIFICATION_ID = 2026
        const val ACTION_START = "com.cosmo.butler.ACTION_START_WATCHDOG"
        const val ACTION_STOP = "com.cosmo.butler.ACTION_STOP_WATCHDOG"
        const val EXTRA_TARGETS = "extra_targets"

        fun start(context: Context, targets: Set<String>) {
            val intent = Intent(context, FocusWatchdogService::class.java).apply {
                action = ACTION_START
                putStringArrayListExtra(EXTRA_TARGETS, ArrayList(targets))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusWatchdogService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val list = intent.getStringArrayListExtra(EXTRA_TARGETS) ?: arrayListOf()
                targetPackages = list.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                startForeground(NOTIFICATION_ID, buildNotification(targetPackages.size))
                startMonitoring()
            }
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                if (targetPackages.isNotEmpty()) {
                    val foregroundPkg = detectForegroundPackage()
                    if (foregroundPkg != null && foregroundPkg in targetPackages) {
                        RootShell.exec("am force-stop $foregroundPkg")
                    }
                }
                delay(1500L)
            }
        }
    }

    private fun detectForegroundPackage(): String? {
        val res = RootShell.exec(
            "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | head -n 1"
        )
        if (!res.isSuccess || res.output.isBlank()) return null
        val regex = Regex("""([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)/""")
        return regex.find(res.output)?.groupValues?.getOrNull(1)
    }

    private fun buildNotification(count: Int) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_cosmo_notification)
        .setContentTitle("Cosmo Focus Lock Active")
        .setContentText("Monitoring $count distracting target(s)")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .addAction(
            R.drawable.ic_cosmo_notification,
            "Stop",
            PendingIntent.getService(
                this,
                0,
                Intent(this, FocusWatchdogService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Lock Watchdog",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
