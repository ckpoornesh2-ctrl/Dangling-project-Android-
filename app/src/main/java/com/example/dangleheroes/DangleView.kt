package com.example.dangleheroes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class DangleView(context: Context, private val stopOverlay: () -> Unit) : View(context) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var heroIndex = 0
    private var angle = 0.08f
    private var angularVelocity = 0f
    private var lastFrame = SystemClock.uptimeMillis()
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var touchStartedAt = 0L

    private val ropeLength = 164f * d

    fun attach(manager: WindowManager, layout: WindowManager.LayoutParams) {
        wm = manager
        params = layout
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastFrame).coerceIn(1L, 32L)) / 1000f
        lastFrame = now

        if (!dragging) {
            // Damped pendulum: gravity pulls the hero back through the centre.
            val acceleration =
                -(8.8f / (ropeLength / d)) * sin(angle) - 1.65f * angularVelocity
            angularVelocity += acceleration * dt
            angle += angularVelocity * dt

            if (angle > 0.72f) {
                angle = 0.72f
                angularVelocity *= -0.35f
            } else if (angle < -0.72f) {
                angle = -0.72f
                angularVelocity *= -0.35f
            }
        }

        drawRopeAndHero(canvas, now)
        postInvalidateOnAnimation()
    }

    private fun drawRopeAndHero(canvas: Canvas, now: Long) {
        val centreX = width / 2f
        val handX = centreX + sin(angle) * ropeLength
        val handY = cos(angle) * ropeLength
        val gentleSway = sin(now / 410.0).toFloat() * 1.4f * d

        // A single continuous rope, beginning at the top edge of the overlay.
        val rope = Path().apply {
            moveTo(centreX, 0f)
            quadTo(
                centreX + sin(angle) * ropeLength * 0.48f + gentleSway,
                ropeLength * 0.48f,
                handX,
                handY
            )
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5.2f * d
        paint.color = Color.argb(85, 0, 0, 0)
        canvas.drawPath(rope, paint)

        paint.strokeWidth = 2.7f * d
        paint.color = Color.rgb(40, 43, 51)
        canvas.drawPath(rope, paint)

        paint.strokeWidth = 1.05f * d
        paint.color = Color.rgb(205, 211, 222)
        canvas.drawPath(rope, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(235, 238, 245)
        canvas.drawCircle(centreX, 1.8f * d, 2.2f * d, paint)

        canvas.save()
        canvas.translate(handX, handY)
        canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat())
        drawHero(canvas, now)
        canvas.restore()
    }

    private fun line(
        canvas: Canvas,
        x1: Float, y1: Float, x2: Float, y2: Float,
        color: Int, width: Float
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width * d
        paint.color = color
        canvas.drawLine(x1 * d, y1 * d, x2 * d, y2 * d, paint)
        paint.style = Paint.Style.FILL
    }

    private fun oval(
        canvas: Canvas,
        left: Float, top: Float, right: Float, bottom: Float,
        color: Int
    ) {
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawOval(
            RectF(left * d, top * d, right * d, bottom * d),
            paint
        )
    }

    private fun round(
        canvas: Canvas,
        left: Float, top: Float, right: Float, bottom: Float,
        radius: Float, color: Int
    ) {
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawRoundRect(
            RectF(left * d, top * d, right * d, bottom * d),
            radius * d,
            radius * d,
            paint
        )
    }

    private fun drawHero(canvas: Canvas, now: Long) {
        val pulse = (sin(now / 240.0).toFloat() * 0.5f + 0.5f)
        when (heroIndex) {
            0 -> drawSpiderHero(canvas)
            1 -> drawDarkKnight(canvas)
            2 -> drawThunderHero(canvas, pulse)
            else -> drawTrickster(canvas, pulse)
        }
    }

    // The hand is at (0,0). Each character has a distinct hanging silhouette.
    private fun drawSpiderHero(canvas: Canvas) {
        val red = Color.rgb(190, 20, 37)
        val blue = Color.rgb(15, 45, 105)

        line(canvas, 0f, 0f, -12f, 24f, red, 8f)
        line(canvas, -12f, 24f, -19f, 49f, blue, 10f)
        line(canvas, -12f, 24f, 8f, 40f, red, 8f)
        line(canvas, 8f, 40f, 15f, 62f, blue, 8f)
        line(canvas, -2f, 11f, -22f, 20f, red, 7f)
        line(canvas, -22f, 20f, -30f, 37f, blue, 6f)
        line(canvas, -1f, 10f, 15f, 19f, red, 7f)
        line(canvas, 15f, 19f, 25f, 31f, blue, 6f)

        oval(canvas, -10f, 3f, 8f, 22f, red)
        oval(canvas, -6f, 7f, 4f, 17f, Color.rgb(16, 25, 43))
        line(canvas, -4f, 9f, 2f, 15f, Color.WHITE, 1.1f)
        line(canvas, 2f, 9f, -4f, 15f, Color.WHITE, 1.1f)
        line(canvas, -12f, 29f, 7f, 34f, Color.rgb(230, 42, 55), 2f)
        oval(canvas, -3f, 40f, 3f, 46f, Color.rgb(235, 235, 245))
        line(canvas, -17f, 47f, -20f, 54f, Color.rgb(70, 150, 255), 1.8f)
        line(canvas, 12f, 56f, 17f, 63f, Color.rgb(70, 150, 255), 1.8f)
    }

    private fun drawDarkKnight(canvas: Canvas) {
        val black = Color.rgb(19, 22, 32)
        val grey = Color.rgb(66, 74, 91)

        val cape = Path().apply {
            moveTo(-5f * d, 15f * d)
            lineTo(-24f * d, 40f * d)
            lineTo(-30f * d, 83f * d)
            lineTo(-5f * d, 69f * d)
            lineTo(19f * d, 84f * d)
            lineTo(14f * d, 35f * d)
            close()
        }
        paint.color = Color.rgb(8, 10, 17)
        canvas.drawPath(cape, paint)

        line(canvas, 0f, 0f, -9f, 22f, grey, 7f)
        line(canvas, -9f, 22f, -14f, 49f, black, 10f)
        line(canvas, -8f, 25f, 9f, 39f, black, 8f)
        line(canvas, 9f, 39f, 15f, 59f, grey, 7f)
        oval(canvas, -14f, 5f, 5f, 24f, black)

        val ears = Path().apply {
            moveTo(-12f * d, 9f * d)
            lineTo(-15f * d, -2f * d)
            lineTo(-5f * d, 6f * d)
            lineTo(4f * d, -2f * d)
            lineTo(3f * d, 10f * d)
            close()
        }
        paint.color = black
        canvas.drawPath(ears, paint)
        oval(canvas, -9f, 12f, -4f, 15f, Color.rgb(235, 210, 130))
        oval(canvas, -1f, 12f, 4f, 15f, Color.rgb(235, 210, 130))
        line(canvas, -12f, 31f, 7f, 34f, grey, 2f)
        line(canvas, -24f, 44f, -28f, 73f, Color.rgb(54, 68, 94), 1.6f)
    }

    private fun drawThunderHero(canvas: Canvas, pulse: Float) {
        val blue = Color.rgb(27, 65, 145)
        val red = Color.rgb(145, 22, 35)
        val gold = Color.rgb(244, 190, 65)

        val cape = Path().apply {
            moveTo(-7f * d, 18f * d)
            lineTo(-27f * d, 48f * d)
            lineTo(-22f * d, 82f * d)
            lineTo(15f * d, 68f * d)
            lineTo(12f * d, 32f * d)
            close()
        }
        paint.color = red
        canvas.drawPath(cape, paint)

        line(canvas, 0f, 0f, -9f, 23f, gold, 7f)
        line(canvas, -9f, 23f, -15f, 48f, blue, 10f)
        line(canvas, -9f, 23f, 9f, 40f, blue, 9f)
        line(canvas, 9f, 40f, 14f, 59f, gold, 7f)
        oval(canvas, -13f, 4f, 6f, 24f, Color.rgb(185, 145, 100))
        round(canvas, -13f, 3f, 6f, 12f, 5f, Color.rgb(75, 85, 105))
        line(canvas, -8f, 30f, 4f, 36f, gold, 2.2f)
        line(canvas, -4f, 44f, 5f, 48f, gold, 2f)

        val bolt = Path().apply {
            moveTo(20f * d, 20f * d)
            lineTo(12f * d, 37f * d)
            lineTo(22f * d, 34f * d)
            lineTo(11f * d, 54f * d)
        }
        paint.color = Color.argb((150 + pulse * 100).toInt(), 255, 220, 95)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f * d
        canvas.drawPath(bolt, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawTrickster(canvas: Canvas, pulse: Float) {
        val green = Color.rgb(23, 112, 73)
        val gold = Color.rgb(205, 157, 48)

        val coat = Path().apply {
            moveTo(-8f * d, 18f * d)
            lineTo(-18f * d, 42f * d)
            lineTo(-27f * d, 76f * d)
            lineTo(0f, 66f * d)
            lineTo(23f * d, 78f * d)
            lineTo(12f * d, 36f * d)
            close()
        }
        paint.color = Color.rgb(15, 66, 48)
        canvas.drawPath(coat, paint)

        line(canvas, 0f, 0f, -8f, 22f, gold, 7f)
        line(canvas, -8f, 22f, -14f, 48f, green, 8f)
        line(canvas, -8f, 22f, 10f, 39f, green, 7f)
        line(canvas, 10f, 39f, 17f, 59f, gold, 6f)
        oval(canvas, -13f, 5f, 6f, 24f, Color.rgb(205, 170, 130))

        val horns = Path().apply {
            moveTo(-12f * d, 9f * d)
            lineTo(-18f * d, -3f * d)
            lineTo(-4f * d, 5f * d)
            lineTo(5f * d, -3f * d)
            lineTo(3f * d, 10f * d)
            close()
        }
        paint.color = gold
        canvas.drawPath(horns, paint)

        line(canvas, -9f, 30f, 5f, 35f, gold, 2f)
        oval(
            canvas, 19f, 25f, 25f, 31f,
            Color.argb((140 + pulse * 100).toInt(), 90, 255, 170)
        )
        oval(canvas, -29f, 51f, -24f, 56f, Color.argb(180, 90, 255, 170))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                touchStartedAt = SystemClock.uptimeMillis()
                dragging = true
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                if (abs(event.y - downY) > 5f * d || abs(dx) > 5f * d) {
                    angle = (dx / (ropeLength * 0.72f)).coerceIn(-0.72f, 0.72f)
                    angularVelocity = 0f
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = event.x - downX
                val elapsed = SystemClock.uptimeMillis() - touchStartedAt

                if (abs(dx) < 8f * d && elapsed < 450L) {
                    heroIndex = (heroIndex + 1) % 4
                } else {
                    angularVelocity = (dx / (ropeLength * 0.72f)) * 2.2f
                }

                dragging = false
                invalidate()
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
