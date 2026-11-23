package com.example.dangleheroes

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Transparent gesture target aligned exactly over the rendered gold hook asset.
 * It deliberately draws nothing: the hook in DangleView is the one and only
 * visible hook, with no duplicate miniature hook, rounded panel or arrow.
 */
class DangleHandleView(context: Context) : View(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val verticalThreshold = dp(42f)

    private var open = false
    private var downX = 0f
    private var downY = 0f
    private var startCenterX = 0f
    private var centerX = 0f
    private var horizontalDrag = false
    private var moved = false
    private var verticalActionTriggered = false

    var onAnchorMoved: ((Float) -> Unit)? = null
    var onOpenRequested: (() -> Unit)? = null
    var onCloseRequested: (() -> Unit)? = null

    fun setOpen(isOpen: Boolean) { open = isOpen }
    fun setCenterX(value: Float) { centerX = value }

    override fun onDraw(canvas: android.graphics.Canvas) {
        // Intentionally transparent. The real 3D hook is drawn by DangleView.
        super.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startCenterX = centerX
                horizontalDrag = false
                moved = false
                verticalActionTriggered = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true

                if (!verticalActionTriggered && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.12f) {
                    horizontalDrag = true
                    val nextCenter = (startCenterX + dx).coerceIn(
                        width * 0.5f,
                        resources.displayMetrics.widthPixels - width * 0.5f
                    )
                    centerX = nextCenter
                    onAnchorMoved?.invoke(nextCenter)
                } else if (!horizontalDrag && !verticalActionTriggered &&
                    abs(dy) >= verticalThreshold && abs(dy) > abs(dx) * 1.12f) {
                    verticalActionTriggered = true
                    if (!open && dy > 0f) {
                        open = true
                        onOpenRequested?.invoke()
                        confirmHaptic()
                    } else if (open && dy < 0f) {
                        open = false
                        onCloseRequested?.invoke()
                        confirmHaptic()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    if (open) {
                        open = false
                        onCloseRequested?.invoke()
                    } else {
                        open = true
                        onOpenRequested?.invoke()
                    }
                    confirmHaptic()
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    private fun confirmHaptic() {
        val constant = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.CONTEXT_CLICK
        performHapticFeedback(constant)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
