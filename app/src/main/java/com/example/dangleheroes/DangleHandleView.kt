package com.example.dangleheroes

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Transparent, generously-sized gesture target over the single rendered 3D hook.
 * No graphics are drawn here, so a duplicate hook or arrow can never appear.
 *
 * Gestures are classified only after a clear direction emerges:
 * - pull down while collapsed: deploy the pendant
 * - swipe up while expanded: retract the pendant
 * - drag left/right: move the hook along the top edge
 * - tap: toggle the pendant
 */
class DangleHandleView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val axisLockDistance = dp(18f)
    private val verticalThreshold = dp(30f)
    private val horizontalThreshold = dp(24f)

    private enum class GestureAxis { UNDECIDED, VERTICAL, HORIZONTAL }

    private var open = false
    private var downX = 0f
    private var downY = 0f
    private var startCenterX = 0f
    private var centerX = 0f
    private var moved = false
    private var axis = GestureAxis.UNDECIDED

    var onAnchorMoved: ((Float) -> Unit)? = null
    var onOpenRequested: (() -> Unit)? = null
    var onCloseRequested: (() -> Unit)? = null

    init {
        isClickable = true
        isFocusable = false
    }

    fun setOpen(isOpen: Boolean) {
        open = isOpen
    }

    fun setCenterX(value: Float) {
        centerX = value
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        // Intentionally transparent. DangleView draws the only visible gold hook.
        super.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startCenterX = centerX
                moved = false
                axis = GestureAxis.UNDECIDED
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                val absX = abs(dx)
                val absY = abs(dy)

                if (absX > touchSlop || absY > touchSlop) moved = true

                // Avoid locking to horizontal because of a few pixels of finger jitter.
                if (axis == GestureAxis.UNDECIDED &&
                    maxOf(absX, absY) >= maxOf(axisLockDistance, touchSlop * 1.5f)
                ) {
                    axis = when {
                        absY >= absX * 1.22f -> GestureAxis.VERTICAL
                        absX >= absY * 1.35f -> GestureAxis.HORIZONTAL
                        else -> GestureAxis.UNDECIDED
                    }
                }

                if (axis == GestureAxis.HORIZONTAL && absX >= horizontalThreshold) {
                    val nextCenter = (startCenterX + dx).coerceIn(
                        width * 0.5f,
                        resources.displayMetrics.widthPixels - width * 0.5f
                    )
                    centerX = nextCenter
                    onAnchorMoved?.invoke(nextCenter)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                val absX = abs(dx)
                val absY = abs(dy)

                // ACTION_UP is also checked, so quick swipes work even if Android
                // coalesces the intermediate MOVE events.
                if (axis == GestureAxis.UNDECIDED && absY >= verticalThreshold &&
                    absY >= absX * 1.2f
                ) {
                    axis = GestureAxis.VERTICAL
                    moved = true
                } else if (axis == GestureAxis.UNDECIDED && absX >= horizontalThreshold &&
                    absX >= absY * 1.35f
                ) {
                    axis = GestureAxis.HORIZONTAL
                    moved = true
                    val nextCenter = (startCenterX + dx).coerceIn(
                        width * 0.5f,
                        resources.displayMetrics.widthPixels - width * 0.5f
                    )
                    centerX = nextCenter
                    onAnchorMoved?.invoke(nextCenter)
                }

                when {
                    axis == GestureAxis.VERTICAL && dy >= verticalThreshold && !open -> {
                        open = true
                        onOpenRequested?.invoke()
                        confirmHaptic()
                    }
                    axis == GestureAxis.VERTICAL && dy <= -verticalThreshold && open -> {
                        open = false
                        onCloseRequested?.invoke()
                        confirmHaptic()
                    }
                    !moved -> {
                        open = !open
                        if (open) onOpenRequested?.invoke() else onCloseRequested?.invoke()
                        confirmHaptic()
                    }
                }

                performClick()
                resetGesture()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                resetGesture()
                return true
            }
        }
        return true
    }

    private fun resetGesture() {
        moved = false
        axis = GestureAxis.UNDECIDED
    }

    private fun confirmHaptic() {
        val constant = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        performHapticFeedback(constant)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float): Float = value * density
}
