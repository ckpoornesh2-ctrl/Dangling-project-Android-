package com.example.dangleheroes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/** Small always-visible top handle: drag left/right to reposition, down to open, up to hide. */
class DangleHandleView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val verticalThreshold = dp(54f)

    private var open = true
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

    fun setOpen(isOpen: Boolean) {
        open = isOpen
        invalidate()
    }

    fun setCenterX(value: Float) {
        centerX = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width * 0.5f
        val cy = height * 0.5f
        val body = RectF(dp(5f), dp(4f), width - dp(5f), height - dp(4f))

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(65, 4, 7, 13)
        canvas.drawRoundRect(body, dp(19f), dp(19f), paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.2f)
        paint.color = Color.argb(190, 221, 171, 86)
        canvas.drawRoundRect(body, dp(19f), dp(19f), paint)

        // A simple gold suspension hook and eyelet; intentionally not a character or charm.
        val hook = Path().apply {
            moveTo(cx, cy - dp(15f))
            cubicTo(cx - dp(8f), cy - dp(15f), cx - dp(10f), cy + dp(1f), cx, cy + dp(3f))
            cubicTo(cx + dp(10f), cy + dp(5f), cx + dp(10f), cy - dp(7f), cx + dp(5f), cy - dp(8f))
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = dp(3.1f)
        paint.color = Color.rgb(255, 205, 112)
        canvas.drawPath(hook, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(255, 225, 162)
        canvas.drawCircle(cx, cy + dp(5f), dp(2.5f), paint)

        // Direction cue: down while collapsed, up while expanded.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.7f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.argb(220, 255, 224, 174)
        val arrowY = cy + dp(16f)
        val arrow = Path().apply {
            if (open) {
                moveTo(cx - dp(4f), arrowY + dp(2f))
                lineTo(cx, arrowY - dp(2f))
                lineTo(cx + dp(4f), arrowY + dp(2f))
            } else {
                moveTo(cx - dp(4f), arrowY - dp(2f))
                lineTo(cx, arrowY + dp(2f))
                lineTo(cx + dp(4f), arrowY - dp(2f))
            }
        }
        canvas.drawPath(arrow, paint)
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
                    val nextCenter = (startCenterX + dx).coerceIn(width * 0.5f, resources.displayMetrics.widthPixels - width * 0.5f)
                    centerX = nextCenter
                    onAnchorMoved?.invoke(nextCenter)
                } else if (!horizontalDrag && !verticalActionTriggered && abs(dy) >= verticalThreshold && abs(dy) > abs(dx) * 1.12f) {
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
                    invalidate()
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
                    invalidate()
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    private fun confirmHaptic() {
        val constant = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
        performHapticFeedback(constant)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float) = value * density
}
