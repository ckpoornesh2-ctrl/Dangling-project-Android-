package com.example.dangleheroes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** Small custom overlay menu opened by holding the charm for 480ms. */
class CharmMenuView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var paused = false
    private var small = false

    var onNextRequested: (() -> Unit)? = null
    var onPauseRequested: (() -> Unit)? = null
    var onSizeRequested: (() -> Unit)? = null
    var onCloseRequested: (() -> Unit)? = null
    var onDismissRequested: (() -> Unit)? = null

    fun updateState(isPaused: Boolean, isSmall: Boolean) {
        paused = isPaused
        small = isSmall
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val panel = RectF(dp(3f), dp(3f), width - dp(3f), height - dp(3f))
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(242, 23, 27, 39)
        canvas.drawRoundRect(panel, dp(18f), dp(18f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = Color.argb(170, 210, 177, 113)
        canvas.drawRoundRect(panel, dp(18f), dp(18f), paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(245, 218, 170)
        paint.textSize = dp(10f)
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("LUCKY DANGLE  ·  CHARM MENU", dp(15f), dp(21f), paint)

        val labels = arrayOf("NEXT", if (paused) "RESUME" else "PAUSE", if (small) "BIGGER" else "SMALLER", "CLOSE")
        val left = dp(10f)
        val top = dp(31f)
        val gap = dp(5f)
        val buttonWidth = (width - left * 2f - gap * 3f) / 4f
        val buttonHeight = height - top - dp(9f)
        for (i in labels.indices) {
            val x = left + i * (buttonWidth + gap)
            val rect = RectF(x, top, x + buttonWidth, top + buttonHeight)
            paint.style = Paint.Style.FILL
            paint.color = when (i) {
                3 -> Color.rgb(105, 43, 43)
                else -> Color.rgb(43, 49, 65)
            }
            canvas.drawRoundRect(rect, dp(10f), dp(10f), paint)
            paint.color = if (i == 3) Color.rgb(255, 220, 210) else Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp(10f)
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
                val top = dp(31f)
                val gap = dp(5f)
                val buttonWidth = (width - left * 2f - gap * 3f) / 4f
                val buttonHeight = height - top - dp(9f)
                if (event.y in top..(top + buttonHeight)) {
                    val index = ((event.x - left) / (buttonWidth + gap)).toInt()
                    val inside = index in 0..3 && event.x >= left + index * (buttonWidth + gap) &&
                        event.x <= left + index * (buttonWidth + gap) + buttonWidth
                    if (inside) {
                        when (index) {
                            0 -> onNextRequested?.invoke()
                            1 -> onPauseRequested?.invoke()
                            2 -> onSizeRequested?.invoke()
                            3 -> onCloseRequested?.invoke()
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
