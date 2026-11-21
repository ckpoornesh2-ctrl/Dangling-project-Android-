package com.example.dangleheroes

import android.content.Context
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class DangleTouchView(context: Context, private val renderer: DangleView) : View(context) {
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                moved = false
                renderer.beginPull(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.rawX - downX) > 6f || abs(event.rawY - downY) > 6f) moved = true
                renderer.movePull(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                renderer.releasePull()
                if (!moved) renderer.nextHero()
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
