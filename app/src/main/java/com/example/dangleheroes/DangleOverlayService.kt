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

/**
 * Persistent floating-handle service. The tiny top hook remains on screen while
 * the pendant is collapsed, so the launcher activity is only needed to start it.
 */
class DangleOverlayService : Service() {
    private lateinit var wm: WindowManager
    private var renderer: DangleView? = null
    private var handleView: DangleHandleView? = null
    private var handleParams: WindowManager.LayoutParams? = null
    private var touchView: DangleTouchView? = null
    private var touchParams: WindowManager.LayoutParams? = null
    private var menuView: CharmMenuView? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var handleWidth = 0
    private var handleHeight = 0
    private var rendererOriginX = 0f
    private var rendererOriginY = 0f
    private var hookTopScreenY = 0
    private var hookTopLocalY = 0f
    private var anchorX = 0f              // Screen coordinates, used by the touch window.
    private var anchorYLocal = 0f         // Renderer coordinates, at the hook's lower eyelet.
    private var anchorYScreen = 0f        // Screen coordinates for the pendant touch window.
    private var charmX = 0f
    private var charmY = 0f
    private var dangleOpen = false

    private val preferences by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        try {
            installOverlay()
        } catch (e: Exception) {
            Log.e(TAG, "Could not create the floating handle", e)
            stopSelf()
        }
    }

    private fun installOverlay() {
        screenWidth = resources.displayMetrics.widthPixels
        screenHeight = resources.displayMetrics.heightPixels

        // The visible hook is drawn once by DangleView. This larger transparent
        // hit target sits over it and remains in place even while the pendant is hidden.
        handleWidth = dp(108)
        handleHeight = dp(112)

        val draw = DangleView(this)
        renderer = draw
        draw.setDangleVisible(false)

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

        // Resolve the renderer's actual screen origin. Some Android builds include
        // system bars in this window and some offset the content below them.
        val rendererLocation = IntArray(2)
        draw.getLocationOnScreen(rendererLocation)
        rendererOriginX = rendererLocation[0].toFloat()
        rendererOriginY = rendererLocation[1].toFloat()

        // Re-read the origin after the first layout pass. This avoids a one-time
        // double status-bar offset on devices that lay out application overlays
        // below the system bar.
        draw.post {
            if (renderer !== draw) return@post
            val laidOutLocation = IntArray(2)
            draw.getLocationOnScreen(laidOutLocation)
            rendererOriginX = laidOutLocation[0].toFloat()
            rendererOriginY = laidOutLocation[1].toFloat()
            hookTopLocalY = (hookTopScreenY - rendererOriginY).coerceAtLeast(dp(1).toFloat())
            anchorYLocal = hookTopLocalY +
                DangleView.TOP_HOOK_HEIGHT_DP * DangleView.TOP_HOOK_EYELET_CENTER_RATIO *
                resources.displayMetrics.density
            anchorYScreen = rendererOriginY + anchorYLocal
            draw.setAnchorPosition(anchorX - rendererOriginX, anchorYLocal)
            updateTouchWindowPosition()
        }

        // Keep the ornament just under the status-bar safe area, at the very top
        // of the usable screen. Do not add the status-bar inset twice.
        hookTopScreenY = statusBarHeight() + dp(1)
        hookTopLocalY = (hookTopScreenY - rendererOriginY).coerceAtLeast(dp(1).toFloat())
        val density = resources.displayMetrics.density
        anchorYLocal = hookTopLocalY +
            DangleView.TOP_HOOK_HEIGHT_DP * DangleView.TOP_HOOK_EYELET_CENTER_RATIO * density
        anchorYScreen = rendererOriginY + anchorYLocal

        val savedFraction = preferences.getFloat(KEY_ANCHOR_FRACTION, 0.5f)
            .coerceIn(0.08f, 0.92f)
        anchorX = screenWidth * savedFraction
        draw.setAnchorPosition(anchorX - rendererOriginX, anchorYLocal)

        charmX = anchorX
        charmY = anchorYScreen + dp(205)
        draw.onCharmPosition = { x, y ->
            // DangleView reports local coordinates; overlay windows use screen coordinates.
            charmX = x + rendererOriginX
            charmY = y + rendererOriginY
            updateTouchWindowPosition()
        }
        draw.onVisibilityAnimationFinished = { visible ->
            if (!visible && !dangleOpen) {
                removeCharmTouchWindow()
            } else if (visible && dangleOpen) {
                updateTouchWindowPosition()
            }
        }

        val handle = DangleHandleView(this)
        handleView = handle
        handle.setCenterX(anchorX)
        val hitTop = (hookTopScreenY - dp(16)).coerceAtLeast(0)
        val hp = WindowManager.LayoutParams(
            handleWidth,
            handleHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (anchorX - handleWidth * 0.5f).toInt()
                .coerceIn(0, (screenWidth - handleWidth).coerceAtLeast(0))
            y = hitTop
        }
        handleParams = hp
        handle.onAnchorMoved = { requestedCenter -> moveAnchor(requestedCenter) }
        handle.onOpenRequested = { setDangleOpen(true) }
        handle.onCloseRequested = { setDangleOpen(false) }
        wm.addView(handle, hp)

        // Restore the last state. The transparent hit target is present in both states.
        val restoreOpen = preferences.getBoolean(KEY_DANGLE_OPEN, true)
        setDangleOpen(restoreOpen, persist = false)
    }

    private fun moveAnchor(requestedCenter: Float) {
        val minCenter = handleWidth * 0.5f
        val maxCenter = (screenWidth - handleWidth * 0.5f).coerceAtLeast(minCenter)
        anchorX = requestedCenter.coerceIn(minCenter, maxCenter)
        handleView?.setCenterX(anchorX)
        handleParams?.let { params ->
            params.x = (anchorX - handleWidth * 0.5f).toInt()
                .coerceIn(0, (screenWidth - handleWidth).coerceAtLeast(0))
            try {
                wm.updateViewLayout(handleView, params)
            } catch (e: Exception) {
                Log.w(TAG, "Could not reposition transparent hook hit target", e)
            }
        }

        renderer?.setAnchorPosition(anchorX - rendererOriginX, anchorYLocal)
        preferences.edit()
            .putFloat(KEY_ANCHOR_FRACTION, (anchorX / screenWidth.toFloat()).coerceIn(0.08f, 0.92f))
            .apply()
        updateTouchWindowPosition()
    }

    private fun setDangleOpen(open: Boolean, persist: Boolean = true) {
        dangleOpen = open
        handleView?.setOpen(open)
        renderer?.setDangleVisible(open)
        if (persist) preferences.edit().putBoolean(KEY_DANGLE_OPEN, open).apply()

        if (open) {
            // Re-create the pendant touch area after every successful close/re-open cycle.
            addCharmTouchWindow()
        } else {
            // Keep the pendant touch area only during the retract animation.
            // The top hook hit target is never removed.
            dismissCharmMenu()
        }
    }

    private fun addCharmTouchWindow() {
        if (touchView != null || !dangleOpen) return
        val hit = DangleTouchView(this, renderer ?: return)
        touchView = hit
        hit.onDismissRequested = { setDangleOpen(false) }
        hit.onMenuRequested = { showCharmMenu() }

        val hitWidth = minOf(dp(156), (screenWidth - dp(12)).coerceAtLeast(dp(120)))
        // Leave the hook mount and its eyelet exclusively to DangleHandleView.
        // This prevents the pendant touch window from stealing hook open/close swipes.
        val top = (anchorYScreen + dp(28)).toInt().coerceAtMost(screenHeight - dp(180))
        val height = calculateTouchHeight(top)
        val params = WindowManager.LayoutParams(
            hitWidth,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (charmX - hitWidth * 0.5f).toInt()
                .coerceIn(0, (screenWidth - hitWidth).coerceAtLeast(0))
            y = top
        }
        touchParams = params
        try {
            wm.addView(hit, params)
        } catch (e: Exception) {
            touchView = null
            touchParams = null
            Log.e(TAG, "Could not add charm touch area", e)
        }
    }

    private fun calculateTouchHeight(top: Int): Int {
        val available = (screenHeight - top - dp(8)).coerceAtLeast(dp(180))
        val charmCenter = if (charmY > top) charmY else anchorYScreen + dp(205)
        val needed = (charmCenter - top + dp(125)).toInt()
        return needed.coerceIn(dp(230), minOf(dp(520), available).coerceAtLeast(dp(230)))
    }

    private fun updateTouchWindowPosition() {
        val view = touchView ?: return
        val params = touchParams ?: return
        if (view.isGestureActive || menuView != null || !dangleOpen) return
        val width = params.width
        val top = (anchorYScreen + dp(28)).toInt().coerceAtMost(screenHeight - dp(180))
        val nx = (charmX - width * 0.5f).toInt()
            .coerceIn(0, (screenWidth - width).coerceAtLeast(0))
        val newHeight = calculateTouchHeight(top)
        if (abs(nx - params.x) > dp(2) || abs(top - params.y) > dp(2) || newHeight != params.height) {
            params.x = nx
            params.y = top
            params.height = newHeight
            try {
                wm.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.w(TAG, "Could not update charm touch area", e)
            }
        }
    }

    private fun removeCharmTouchWindow() {
        touchView?.let { view ->
            try {
                wm.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "Charm touch window removal skipped", e)
            }
        }
        touchView = null
        touchParams = null
    }

    private fun showCharmMenu() {
        if (menuView != null || !dangleOpen) return
        val draw = renderer ?: return
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
        menu.onCloseRequested = { setDangleOpen(false) }
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
            try {
                wm.removeView(menu)
            } catch (e: Exception) {
                Log.w(TAG, "Charm menu removal skipped", e)
            }
        }
        menuView = null
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
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
            .setContentTitle("Lucky Dangle is ready")
            .setContentText("Pull the gold hook down to drop the pendant; push up to retract it")
            .setSmallIcon(android.R.drawable.star_on)
            .addAction(Notification.Action.Builder(null, "Stop service", stopIntent).build())
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        dismissCharmMenu()
        removeCharmTouchWindow()
        handleView?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        renderer?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        handleView = null
        handleParams = null
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
        private const val PREFS = "lucky_dangle_settings"
        private const val KEY_ANCHOR_FRACTION = "anchor_fraction"
        private const val KEY_DANGLE_OPEN = "dangle_open"
        private const val CHANNEL_ID = "lucky_dangle"
        private const val NOTIFICATION_ID = 77
        const val ACTION_STOP = "com.example.dangleheroes.STOP"
    }
}
