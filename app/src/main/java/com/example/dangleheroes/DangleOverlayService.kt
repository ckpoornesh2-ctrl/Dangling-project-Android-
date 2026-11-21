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
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import kotlin.math.abs

class DangleOverlayService : Service() {
    private lateinit var wm: WindowManager
    private var renderer: DangleView? = null
    private var touchView: DangleTouchView? = null
    private var touchParams: WindowManager.LayoutParams? = null
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        try {
            installOverlay()
        } catch (e: Exception) {
            Log.e(TAG, "Could not create Lucky Dangle overlay", e)
            stopSelf()
        }
    }

    private fun installOverlay() {
        val draw = DangleView(this)
        renderer = draw
        val full = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(draw, full)

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        // Keep the interactive window close to the compact charm instead of
        // creating a large invisible touch surface over other apps.
        val hitWidth = minOf(dp(176), screenWidth - dp(16)).coerceAtLeast(dp(132))
        val hitHeight = minOf(dp(360), screenHeight - dp(16)).coerceAtLeast(dp(200))
        val hit = DangleTouchView(this, draw)
        touchView = hit
        hit.onDismissRequested = { stopSelf() }

        val hp = WindowManager.LayoutParams(
            hitWidth,
            hitHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - hitWidth) / 2
            y = 0
        }
        touchParams = hp
        wm.addView(hit, hp)

        draw.onCharmPosition = { x, y ->
            // Keep the touch surface over the moving charm while idle. Freeze it
            // for the duration of a drag so Android keeps the same touch target.
            if (!hit.isGestureActive) {
                val p = touchParams
                if (p != null) {
                    val nx = (x - hitWidth * 0.5f).toInt()
                        .coerceIn(0, (screenWidth - hitWidth).coerceAtLeast(0))
                    val ny = (y - hitHeight * 0.5f).toInt()
                        .coerceIn(0, (screenHeight - hitHeight).coerceAtLeast(0))
                    if (abs(nx - lastX) > dp(3) || abs(ny - lastY) > dp(3)) {
                        lastX = nx
                        lastY = ny
                        p.x = nx
                        p.y = ny
                        try {
                            wm.updateViewLayout(hit, p)
                        } catch (e: Exception) {
                            Log.w(TAG, "Charm touch target update skipped", e)
                        }
                    }
                }
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Lucky Dangle", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DangleOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Lucky Dangle is running")
            .setContentText("Tap to switch charms · Drag to swing · Drop near bottom to close")
            .setSmallIcon(android.R.drawable.star_on)
            .addAction(Notification.Action.Builder(null, "Close", stopIntent).build())
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (::wm.isInitialized) {
            touchView?.let { try { wm.removeView(it) } catch (_: Exception) { } }
            renderer?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        }
        touchView = null
        touchParams = null
        renderer = null
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LuckyDangle"
        private const val CHANNEL_ID = "lucky_dangle"
        private const val NOTIFICATION_ID = 77
        const val ACTION_STOP = "com.example.dangleheroes.STOP"
    }
}
