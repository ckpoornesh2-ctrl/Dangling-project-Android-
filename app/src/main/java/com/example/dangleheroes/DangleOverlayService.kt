package com.example.dangleheroes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

class DangleOverlayService : Service() {
    private lateinit var wm: WindowManager
    private var renderer: DangleView? = null
    private var touchView: DangleTouchView? = null
    private var renderParams: WindowManager.LayoutParams? = null
    private var touchParams: WindowManager.LayoutParams? = null
    private var lastTouchX = -1
    private var lastTouchY = -1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
    }

    private fun showOverlay() {
        if (renderer != null) return
        val draw = DangleView(this)
        renderer = draw
        val full = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 0 }
        renderParams = full
        wm.addView(draw, full)

        val hit = DangleTouchView(this, draw) { Pair(lastTouchX.toFloat(), lastTouchY.toFloat()) }
        touchView = hit
        val hitParams = WindowManager.LayoutParams(
            dp(112), dp(132),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = dp(170)
        }
        touchParams = hitParams
        wm.addView(hit, hitParams)

        draw.onHeroPosition = { cx, cy ->
            val p = touchParams
            if (p != null) {
                val x = (cx - dp(56)).toInt()
                val y = (cy - dp(66)).toInt()
                if (kotlin.math.abs(x-lastTouchX) > dp(2) || kotlin.math.abs(y-lastTouchY) > dp(2)) {
                    lastTouchX = x; lastTouchY = y
                    p.x = x; p.y = y
                    try { wm.updateViewLayout(hit, p) } catch (_: Exception) {}
                }
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Dangle Heroes", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, DangleOverlayService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("Dangle Heroes is running")
            .setContentText("Pull the hero down and release to sling · Tap to switch")
            .setSmallIcon(android.R.drawable.star_on)
            .addAction(Notification.Action.Builder(null, "Stop", pending).build())
            .setOngoing(true).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        touchView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        renderer?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        touchView = null; renderer = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "dangle_heroes_channel"
        private const val NOTIFICATION_ID = 77
        const val ACTION_STOP = "com.example.dangleheroes.STOP"
    }
}
