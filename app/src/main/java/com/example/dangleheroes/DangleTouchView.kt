package com.example.dangleheroes

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

class DangleTouchView(
    context: Context,
    private val renderer: DangleView
) : View(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var longPressTriggered = false
    private var isDown = false

    /** The service freezes the hit-window position during a drag or long press. */
    var isGestureActive: Boolean = false
        private set

    var onDismissRequested: (() -> Unit)? = null
    var onMenuRequested: (() -> Unit)? = null

    private val longPressRunnable = Runnable {
        if (isDown && !moved && !longPressTriggered) {
            longPressTriggered = true
            renderer.cancelPull()
            onMenuRequested?.invoke()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacks(longPressRunnable)
                downX = event.rawX
                downY = event.rawY
                moved = false
                longPressTriggered = false
                isDown = true
                isGestureActive = true
                renderer.beginPull(event.rawX, event.rawY)
                handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.rawX - downX) > touchSlop || abs(event.rawY - downY) > touchSlop) {
                    moved = true
                    handler.removeCallbacks(longPressRunnable)
                }
                if (!longPressTriggered) renderer.movePull(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                val wasLongPress = longPressTriggered
                val closeRequested = moved && !wasLongPress && renderer.isInBottomDropZone(event.rawY)
                if (!wasLongPress) renderer.releasePull()
                isDown = false
                isGestureActive = false
                if (closeRequested) {
                    onDismissRequested?.invoke()
                } else if (!moved && !wasLongPress) {
                    renderer.nextCharm()
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (!longPressTriggered) renderer.releasePull()
                isDown = false
                isGestureActive = false
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable)
        isDown = false
        isGestureActive = false
        super.onDetachedFromWindow()
    }

    companion object {
        private const val LONG_PRESS_MS = 480L
    }
}
