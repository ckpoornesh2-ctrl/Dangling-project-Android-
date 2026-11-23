package com.example.dangleheroes

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/** Touch surface around the single charm. It never covers the whole display. */
class DangleTouchView(
    context: Context,
    private val renderer: DangleView
) : View(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var longPressTriggered = false
    private var isDown = false
    private var dropZoneActive = false

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
                dropZoneActive = false
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
                if (!longPressTriggered) {
                    renderer.movePull(event.rawX, event.rawY)
                    val inDropZone = renderer.isInBottomDropZone(event.rawY)
                    if (inDropZone && !dropZoneActive) performCloseZoneHaptic()
                    dropZoneActive = inDropZone
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                val wasLongPress = longPressTriggered
                val dy = event.rawY - downY
                val closeRequested = moved && !wasLongPress && dy > dp(65f) &&
                    renderer.isInBottomDropZone(event.rawY)

                if (closeRequested) {
                    if (!dropZoneActive) performCloseZoneHaptic()
                    renderer.cancelPull()
                    onDismissRequested?.invoke()
                } else if (!wasLongPress) {
                    // All ordinary drags remain physical sling gestures in this developer build.
                    renderer.releasePull()
                }

                isDown = false
                isGestureActive = false
                dropZoneActive = false
                if (!closeRequested && !moved && !wasLongPress) {
                    renderer.nextCharm()
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (!longPressTriggered) renderer.cancelPull()
                isDown = false
                isGestureActive = false
                dropZoneActive = false
                return true
            }
        }
        return true
    }

    private fun performCloseZoneHaptic() {
        val feedback = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        performHapticFeedback(feedback)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable)
        isDown = false
        isGestureActive = false
        dropZoneActive = false
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float) = value * density

    companion object {
        private const val LONG_PRESS_MS = 480L
    }
}
