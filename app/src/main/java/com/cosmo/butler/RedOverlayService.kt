package com.cosmo.butler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RedOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                removeOverlay()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_ALPHA -> {
                val alpha = intent.getFloatExtra(EXTRA_ALPHA, DEFAULT_ALPHA).coerceIn(MIN_ALPHA, MAX_ALPHA)
                overlayView?.setBackgroundColor(buildRedTintColor(alpha))
                return START_STICKY
            }
            else -> {
                if (!Settings.canDrawOverlays(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val alpha = intent?.getFloatExtra(EXTRA_ALPHA, DEFAULT_ALPHA)
                    ?.coerceIn(MIN_ALPHA, MAX_ALPHA) ?: DEFAULT_ALPHA

                startForegroundWithNotification()
                showOrUpdateOverlay(alpha)
                _isRunning.value = true
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        removeOverlay()
        _isRunning.value = false
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        createNotificationChannel()
        val notification = buildNotification()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                description = getString(R.string.overlay_notification_text)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RedOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cosmo_notification)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, getString(R.string.overlay_notification_stop), stopPendingIntent)
            .build()
    }

    private fun showOrUpdateOverlay(alpha: Float) {
        val tintColor = buildRedTintColor(alpha)
        val existingView = overlayView
        if (existingView != null) {
            existingView.setBackgroundColor(tintColor)
            return
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val view = View(this).apply {
            setBackgroundColor(tintColor)
            isClickable = false
            isFocusable = false
        }

        runCatching {
            wm.addView(view, params)
            overlayView = view
        }.onFailure {
            stopSelf()
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager?.removeViewImmediate(view) }
        }
        overlayView = null
        _isRunning.value = false
    }

    private fun buildRedTintColor(alpha: Float): Int {
        val a = (alpha.coerceIn(MIN_ALPHA, MAX_ALPHA) * 255f).toInt()
        return Color.argb(a, 185, 0, 12)
    }

    companion object {
        private const val CHANNEL_ID = "cosmo_red_overlay_channel"
        private const val NOTIFICATION_ID = 4041
        private const val ACTION_START = "com.example.cosmo.ACTION_START_OVERLAY"
        private const val ACTION_UPDATE_ALPHA = "com.example.cosmo.ACTION_UPDATE_ALPHA"
        private const val ACTION_STOP = "com.example.cosmo.ACTION_STOP_OVERLAY"
        private const val EXTRA_ALPHA = "extra_alpha"

        const val DEFAULT_ALPHA = 0.55f
        const val MIN_ALPHA = 0.20f
        const val MAX_ALPHA = 0.85f

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context, alpha: Float = DEFAULT_ALPHA) {
            val intent = Intent(context, RedOverlayService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ALPHA, alpha)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun updateAlpha(context: Context, alpha: Float) {
            if (!_isRunning.value) return
            val intent = Intent(context, RedOverlayService::class.java).apply {
                action = ACTION_UPDATE_ALPHA
                putExtra(EXTRA_ALPHA, alpha)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RedOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}