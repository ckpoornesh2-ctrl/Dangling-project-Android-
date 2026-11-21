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
    private var menuView: CharmMenuView? = null
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE
    private var charmX = 0f
    private var charmY = 0f

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
        // A compact touch target around the charm, not a screen-sized invisible blocker.
        val hitWidth = minOf(dp(148), screenWidth - dp(16)).coerceAtLeast(dp(112))
        val hitHeight = minOf(dp(292), screenHeight - dp(16)).coerceAtLeast(dp(190))
        val hit = DangleTouchView(this, draw)
        touchView = hit
        hit.onDismissRequested = { stopSelf() }
        hit.onMenuRequested = { showCharmMenu() }

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
            charmX = x
            charmY = y
            if (!hit.isGestureActive && menuView == null) {
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

    private fun showCharmMenu() {
        if (menuView != null) return
        val draw = renderer ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val menuWidth = minOf(dp(332), screenWidth - dp(16)).coerceAtLeast(dp(250))
        val menuHeight = dp(118)
        val menu = CharmMenuView(this)
        menuView = menu
        menu.updateState(draw.isPaused(), draw.currentSizeLabel(), draw.isPhoneMotionEnabled(), draw.currentCharmName())
        menu.onNextRequested = {
            draw.nextCharm()
            dismissCharmMenu()
        }
        menu.onPauseRequested = {
            val nowPaused = draw.togglePaused()
            menu.updateState(nowPaused, draw.currentSizeLabel(), draw.isPhoneMotionEnabled(), draw.currentCharmName())
        }
        menu.onSmallerRequested = {
            draw.decreaseSize()
            menu.updateState(draw.isPaused(), draw.currentSizeLabel(), draw.isPhoneMotionEnabled(), draw.currentCharmName())
        }
        menu.onBiggerRequested = {
            draw.increaseSize()
            menu.updateState(draw.isPaused(), draw.currentSizeLabel(), draw.isPhoneMotionEnabled(), draw.currentCharmName())
        }
        menu.onMotionRequested = {
            draw.togglePhoneMotion()
            menu.updateState(draw.isPaused(), draw.currentSizeLabel(), draw.isPhoneMotionEnabled(), draw.currentCharmName())
        }
        menu.onCloseRequested = { stopSelf() }
        menu.onDismissRequested = { dismissCharmMenu() }

        val x = ((screenWidth - menuWidth) / 2).coerceAtLeast(0)
        val preferredY = charmY.toInt() + dp(18)
        val maxY = (screenHeight - menuHeight - dp(16)).coerceAtLeast(dp(8))
        val y = preferredY.coerceIn(dp(8), maxY)
        val params = WindowManager.LayoutParams(
            menuWidth,
            menuHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
        try {
            wm.addView(menu, params)
        } catch (e: Exception) {
            menuView = null
            Log.e(TAG, "Could not show charm menu", e)
        }
    }

    private fun dismissCharmMenu() {
        menuView?.let { menu ->
            try { wm.removeView(menu) } catch (e: Exception) {
                Log.w(TAG, "Charm menu removal skipped", e)
            }
        }
        menuView = null
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
            .setContentText("Tap to switch · Hold for menu · Tilt to sway · Drag to swing or close")
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
        dismissCharmMenu()
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
