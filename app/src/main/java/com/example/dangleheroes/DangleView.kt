package com.example.dangleheroes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.cos
import kotlin.math.sin

class DangleView(context: Context, private val stopOverlay: () -> Unit) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var heroIndex = 0
    private var angle = 0f
    private var angularVelocity = 0f
    private var lastFrame = SystemClock.uptimeMillis()
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var startWindowY = 0
    private var moved = false
    private var pressedAt = 0L

    private val heroes = listOf(
        Hero("SPIDER", Color.rgb(220, 25, 35), Color.rgb(20, 90, 165), Color.WHITE),
        Hero("BAT", Color.rgb(25, 27, 38), Color.rgb(245, 190, 45), Color.WHITE),
        Hero("THUNDER", Color.rgb(35, 95, 190), Color.rgb(205, 35, 45), Color.WHITE),
        Hero("TRICKSTER", Color.rgb(35, 145, 85), Color.rgb(220, 175, 45), Color.WHITE)
    )

    data class Hero(val name: String, val main: Int, val secondary: Int, val detail: Int)

    fun attach(manager: WindowManager, layout: WindowManager.LayoutParams) {
        wm = manager
        params = layout
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastFrame).coerceAtMost(32L) / 1000f)
        lastFrame = now
        if (!moved) {
            angularVelocity += (-angle * 5.0f - angularVelocity * 1.8f) * dt
            angle += angularVelocity * dt
            angle = angle.coerceIn(-0.48f, 0.48f)
        }
        drawScene(canvas)
        postInvalidateDelayed(16)
    }

    private fun drawScene(canvas: Canvas) {
        val w = width.toFloat()
        val cx = w / 2f
        val anchorY = 3f * density
        val ropeLength = 116f * density
        val pivotX = cx + sin(angle) * ropeLength
        val pivotY = anchorY + cos(angle) * ropeLength

        paint.color = Color.argb(55, 0, 0, 0)
        paint.strokeWidth = 2f * density
        canvas.drawLine(cx + 1f, anchorY, pivotX + 1f, pivotY + 1f, paint)
        paint.color = Color.rgb(225, 225, 230)
        paint.strokeWidth = 2.4f * density
        canvas.drawLine(cx, anchorY, pivotX, pivotY, paint)
        paint.color = Color.LTGRAY
        canvas.drawCircle(cx, anchorY, 3f * density, paint)

        canvas.save()
        canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat(), cx, anchorY)
        drawHero(canvas, cx - 30f * density, 108f * density)
        canvas.restore()

        paint.color = Color.argb(175, 30, 30, 38)
        paint.textSize = 9f * density
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(heroes[heroIndex].name, cx, height - 7f * density, paint)
    }

    private fun drawHero(canvas: Canvas, left: Float, top: Float) {
        val hero = heroes[heroIndex]
        val cell = 5f * density
        // Pixel-art body: head, shoulders, torso, arms and boots.
        val rows = listOf(
            "....XXXX....",
            "...XXXXXX...",
            "..XXXXXXXX..",
            "..XX..XX....",
            ".XXXXXXXXXX.",
            "XXXXXXXXXXXX",
            "XX..XXXX..XX",
            "....XXXX....",
            "...XXXXXX...",
            "...XX..XX....",
            "...XX..XX....",
            "..XXX..XXX..."
        )
        val width = rows.maxOf { it.length }
        val startX = left + (12 - width) * cell / 2f
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                if (ch == 'X') {
                    val color = when {
                        y <= 3 -> hero.main
                        y in 4..7 && (x < 2 || x > 9) -> hero.secondary
                        y in 8..11 -> hero.main
                        else -> if ((x + y) % 4 == 0) hero.detail else hero.secondary
                    }
                    paint.color = color
                    canvas.drawRect(startX + x * cell, top + y * cell,
                        startX + (x + 1) * cell - 0.5f, top + (y + 1) * cell - 0.5f, paint)
                }
            }
        }
        // Tiny contrasting chest emblem.
        paint.color = hero.detail
        canvas.drawRect(left + 24f * density, top + 27f * density,
            left + 36f * density, top + 33f * density, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val p = params ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                startWindowY = p.y
                moved = false
                pressedAt = SystemClock.uptimeMillis()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 7f * density) moved = true
                if (moved) {
                    p.x += (event.rawX - lastRawX).toInt()
                    p.y += (event.rawY - lastRawY).toInt()
                    wm?.updateViewLayout(this, p)
                    angularVelocity = (event.rawX - lastRawX) / (18f * density)
                }
                lastRawX = event.rawX
                lastRawY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dy = event.rawY - downRawY
                if (!moved || SystemClock.uptimeMillis() - pressedAt < 180L && kotlin.math.abs(dy) < 18f * density) {
                    nextHero()
                } else if (dy > 65f * density) {
                    nextHero()
                    p.y = startWindowY
                    wm?.updateViewLayout(this, p)
                } else {
                    angularVelocity = (event.rawX - downRawX) / (260f * density)
                }
                moved = false
                performClick()
                return true
            }
        }
        return true
    }

    private fun nextHero() {
        heroIndex = (heroIndex + 1) % heroes.size
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
