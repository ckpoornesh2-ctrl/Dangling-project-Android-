package com.example.dangleheroes

import android.content.Context
import android.view.MotionEvent
import android.view.View

/** Small movable touch target; the full-screen drawing layer stays non-touchable. */
class DangleTouchView(
    context: Context,
    private val renderer: DangleView,
    private val screenPosition: () -> Pair<Float, Float>
) : View(context) {
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                moved = false
                renderer.beginDrag(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.rawX-downX) > 5f || kotlin.math.abs(event.rawY-downY) > 5f) moved = true
                renderer.dragTo(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                renderer.release()
                if (!moved) renderer.onTap()
                performClick()
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
