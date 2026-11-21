package com.example.dangleheroes

import android.content.Context
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class DangleTouchView(
    context: Context,
    private val renderer: DangleView
) : View(context) {
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    /** The service freezes the hit-window position during an active drag. */
    var isGestureActive: Boolean = false
        private set

    var onDismissRequested: (() -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                moved = false
                isGestureActive = true
                renderer.beginPull(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.rawX - downX) > 8f || abs(event.rawY - downY) > 8f) moved = true
                renderer.movePull(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val closeRequested = moved && renderer.isInBottomDropZone(event.rawY)
                renderer.releasePull()
                isGestureActive = false
                if (closeRequested) {
                    onDismissRequested?.invoke()
                } else if (!moved) {
                    renderer.nextCharm()
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                renderer.releasePull()
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
}
