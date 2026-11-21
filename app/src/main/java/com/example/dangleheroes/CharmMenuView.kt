package com.example.dangleheroes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** Compact control panel opened by holding the charm for 480ms. */
class CharmMenuView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var paused = false
    private var sizeLabel = "92dp"
    private var motionEnabled = true
    private var charmName = "Evil Eye"

    var onNextRequested: (() -> Unit)? = null
    var onPauseRequested: (() -> Unit)? = null
    var onSmallerRequested: (() -> Unit)? = null
    var onBiggerRequested: (() -> Unit)? = null
    var onMotionRequested: (() -> Unit)? = null
    var onCloseRequested: (() -> Unit)? = null
    var onDismissRequested: (() -> Unit)? = null

    fun updateState(isPaused: Boolean, currentSize: String, isMotionEnabled: Boolean, currentCharm: String) {
        paused = isPaused
        sizeLabel = currentSize
        motionEnabled = isMotionEnabled
        charmName = currentCharm
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val panel = RectF(dp(3f), dp(3f), width - dp(3f), height - dp(3f))
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(244, 23, 27, 39)
        canvas.drawRoundRect(panel, dp(18f), dp(18f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = Color.argb(175, 210, 177, 113)
        canvas.drawRoundRect(panel, dp(18f), dp(18f), paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(245, 218, 170)
        paint.textSize = dp(10f)
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("${charmName.uppercase()}  ·  $sizeLabel", dp(15f), dp(21f), paint)

        val labels = arrayOf(
            "NEXT", if (paused) "RESUME" else "PAUSE", "CLOSE",
            "SMALLER", "BIGGER", if (motionEnabled) "TILT ON" else "TILT OFF"
        )
        val left = dp(10f)
        val top = dp(30f)
        val gapX = dp(6f)
        val gapY = dp(5f)
        val buttonWidth = (width - left * 2f - gapX * 2f) / 3f
        val buttonHeight = (height - top - dp(10f) - gapY) / 2f

        for (i in labels.indices) {
            val row = i / 3
            val col = i % 3
            val x = left + col * (buttonWidth + gapX)
            val y = top + row * (buttonHeight + gapY)
            val rect = RectF(x, y, x + buttonWidth, y + buttonHeight)
            paint.style = Paint.Style.FILL
            paint.color = when (i) {
                2 -> Color.rgb(105, 43, 43)
                5 -> if (motionEnabled) Color.rgb(47, 72, 63) else Color.rgb(58, 53, 44)
                else -> Color.rgb(43, 49, 65)
            }
            canvas.drawRoundRect(rect, dp(9f), dp(9f), paint)
            paint.color = if (i == 2) Color.rgb(255, 220, 210) else Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp(9.5f)
            paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            val textY = rect.centerY() - (paint.descent() + paint.ascent()) * 0.5f
            canvas.drawText(labels[i], rect.centerX(), textY, paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val left = dp(10f)
                val top = dp(30f)
                val gapX = dp(6f)
                val gapY = dp(5f)
                val buttonWidth = (width - left * 2f - gapX * 2f) / 3f
                val buttonHeight = (height - top - dp(10f) - gapY) / 2f
                if (event.y >= top && event.y <= height - dp(10f)) {
                    val col = ((event.x - left) / (buttonWidth + gapX)).toInt()
                    val row = ((event.y - top) / (buttonHeight + gapY)).toInt()
                    val index = row * 3 + col
                    val xInCell = event.x - (left + col * (buttonWidth + gapX))
                    val yInCell = event.y - (top + row * (buttonHeight + gapY))
                    val inside = row in 0..1 && col in 0..2 &&
                        xInCell in 0f..buttonWidth && yInCell in 0f..buttonHeight
                    if (inside) {
                        when (index) {
                            0 -> onNextRequested?.invoke()
                            1 -> onPauseRequested?.invoke()
                            2 -> onCloseRequested?.invoke()
                            3 -> onSmallerRequested?.invoke()
                            4 -> onBiggerRequested?.invoke()
                            5 -> onMotionRequested?.invoke()
                        }
                        return true
                    }
                }
                onDismissRequested?.invoke()
                return true
            }
            MotionEvent.ACTION_OUTSIDE, MotionEvent.ACTION_CANCEL -> {
                onDismissRequested?.invoke()
                return true
            }
        }
        return true
    }

    private fun dp(value: Float): Float = value * density
}
