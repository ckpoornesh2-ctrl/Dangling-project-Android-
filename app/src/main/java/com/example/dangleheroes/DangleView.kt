package com.example.dangleheroes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.*

/**
 * Full-screen transparent renderer. Touch is handled by the small hit target
 * managed by DangleOverlayService, so the rest of the phone remains usable.
 */
class DangleView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    var onHeroPosition: ((Float, Float) -> Unit)? = null
    private var heroIndex = 0
    private var angle = 0.02f
    private var angularVelocity = 0f
    private var ropeRest = 188f * density
    private var ropeLength = ropeRest
    private var stretch = 0f
    private var dragging = false
    private var dragStartY = 0f
    private var dragStartX = 0f
    private var lastY = 0f
    private var lastTime = 0L
    private var releaseBoost = 0f
    private var lastFrame = SystemClock.uptimeMillis()
    private var screenW = 0f
    private var screenH = 0f
    private var heroX = 0f
    private var heroY = 0f
    private var transition = 1f
    private var oldHero = 0
    private var transitionStart = 0L

    init {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    fun setHero(index: Int) {
        oldHero = heroIndex
        heroIndex = ((index % 4) + 4) % 4
        transition = 0f
        transitionStart = SystemClock.uptimeMillis()
        invalidate()
    }

    fun nextHero() = setHero(heroIndex + 1)

    fun beginDrag(pointerX: Float, pointerY: Float) {
        dragging = true
        dragStartX = pointerX
        dragStartY = pointerY
        lastY = pointerY
        lastTime = SystemClock.uptimeMillis()
        angularVelocity = 0f
        releaseBoost = 0f
    }

    fun dragTo(pointerX: Float, pointerY: Float) {
        if (!dragging) return
        val dx = pointerX - dragStartX
        val dy = (pointerY - dragStartY).coerceAtLeast(0f)
        val maxStretch = 235f * density
        stretch = dy.coerceIn(0f, maxStretch)
        ropeLength = ropeRest + stretch
        val anchorX = screenW * 0.5f
        val maxAngle = 1.22f
        angle = (dx / (screenW * 0.46f)).coerceIn(-maxAngle, maxAngle)
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastTime).coerceAtLeast(1L)) / 1000f
        releaseBoost = ((pointerY - lastY) / dt).coerceIn(-2600f, 2600f)
        lastY = pointerY
        lastTime = now
        invalidate()
    }

    fun release() {
        if (!dragging) return
        dragging = false
        // Elastic sling: stretch energy and upward hand velocity launch the hero.
        val elasticKick = (stretch / density) * 0.030f
        angularVelocity += (releaseBoost / 2600f) * 2.0f
        ropeLength = ropeRest
        stretch = 0f
        if (elasticKick > 0.05f) {
            angle += (if (angle == 0f) 0.08f else sign(angle) * elasticKick).coerceIn(-0.28f, 0.28f)
            angularVelocity += sign(angle) * elasticKick * 2.4f
        }
        invalidate()
    }

    fun onTap() = nextHero()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        screenW = width.toFloat()
        screenH = height.toFloat()
        if (screenW <= 0f || screenH <= 0f) return

        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastFrame).coerceIn(1L, 24L)) / 1000f
        lastFrame = now

        if (!dragging) {
            // Pendulum + damped spring. The spring length returns smoothly after release.
            val gravity = 8.8f
            val acceleration = -(gravity / (ropeRest / density)) * sin(angle) -
                0.82f * angularVelocity
            angularVelocity += acceleration * dt
            angle += angularVelocity * dt
            angle = angle.coerceIn(-1.30f, 1.30f)
            ropeLength += (ropeRest - ropeLength) * min(1f, dt * 8.5f)
            if (abs(angularVelocity) < 0.0008f && abs(angle) < 0.012f) {
                angle *= 0.985f
                angularVelocity *= 0.96f
            }
        }

        val anchorX = screenW * 0.5f
        val anchorY = 1.5f * density
        val sway = sin(now / 470.0).toFloat() * 2.0f * density
        val heroCenterX = anchorX + sin(angle) * ropeLength
        val heroCenterY = anchorY + cos(angle) * ropeLength + 46f * density
        heroX = heroCenterX
        heroY = heroCenterY
        drawRope(canvas, anchorX, anchorY, heroCenterX, heroCenterY - 34f * density, sway)
        drawHero(canvas, heroCenterX, heroCenterY, now)

        val elapsed = (now - transitionStart).coerceAtLeast(0L)
        transition = (elapsed / 260f).coerceIn(0f, 1f)
        onHeroPosition?.invoke(heroCenterX, heroCenterY)
        postInvalidateOnAnimation()
    }

    private fun drawRope(c: Canvas, ax: Float, ay: Float, hx: Float, hy: Float, sway: Float) {
        val dx = hx - ax
        val dy = hy - ay
        val tension = (stretch / (235f * density)).coerceIn(0f, 1f)
        val midX = ax + dx * 0.48f + sway * (1f - tension)
        val midY = ay + dy * 0.48f
        val rope = Path().apply {
            moveTo(ax, ay)
            quadTo(midX, midY, hx, hy)
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f * density
        p.color = Color.argb(95, 0, 0, 0)
        c.drawPath(rope, p)
        p.strokeWidth = 3.8f * density
        p.color = Color.rgb(25, 29, 39)
        c.drawPath(rope, p)
        p.strokeWidth = 1.25f * density
        p.color = Color.rgb(198, 207, 220)
        c.drawPath(rope, p)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        c.drawCircle(ax, ay, 3.0f * density, p)
        if (tension > 0.05f) {
            p.color = Color.argb((80 + tension * 150).toInt(), 110, 205, 255)
            p.style = Paint.Style.STROKE
            p.strokeWidth = (1.5f + tension * 2f) * density
            c.drawPath(rope, p)
            p.style = Paint.Style.FILL
        }
    }

    private fun drawHero(c: Canvas, x: Float, y: Float, now: Long) {
        val bob = sin(now / 190.0).toFloat() * 2.2f * density
        c.save()
        c.translate(x, y + bob)
        c.rotate(Math.toDegrees(angle.toDouble()).toFloat() * 0.72f)
        val scale = density
        c.scale(scale, scale)
        if (transition < 1f) {
            val t = transition
            c.save()
            c.scale(0.88f + 0.12f * t, 0.88f + 0.12f * t)
            drawCharacter(c, heroIndex, now)
            c.restore()
        } else {
            drawCharacter(c, heroIndex, now)
        }
        c.restore()
    }

    private fun drawCharacter(c: Canvas, index: Int, now: Long) {
        when (index) {
            0 -> spider(c, now)
            1 -> sentinel(c, now)
            2 -> storm(c, now)
            else -> trickster(c, now)
        }
    }

    private fun stroke(c: Canvas, color: Int, width: Float, vararg xy: Float) {
        p.color = color; p.style = Paint.Style.STROKE; p.strokeWidth = width
        val path = Path()
        path.moveTo(xy[0], xy[1])
        var i = 2
        while (i + 1 < xy.size) { path.lineTo(xy[i], xy[i + 1]); i += 2 }
        c.drawPath(path, p); p.style = Paint.Style.FILL
    }

    private fun fill(c: Canvas, color: Int, vararg xy: Float) {
        val path = Path()
        path.moveTo(xy[0], xy[1])
        var i = 2
        while (i + 1 < xy.size) { path.lineTo(xy[i], xy[i + 1]); i += 2 }
        path.close(); p.color = color; p.style = Paint.Style.FILL; c.drawPath(path, p)
    }

    private fun limb(c: Canvas, color: Int, width: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
        stroke(c, Color.argb(100, 0, 0, 0), width + 2f, x1, y1, x2, y2)
        stroke(c, color, width, x1, y1, x2, y2)
    }

    private fun head(c: Canvas, color: Int, eyeColor: Int = Color.WHITE) {
        p.color = Color.rgb(12, 15, 23); c.drawOval(RectF(-11f, -31f, 11f, -7f), p)
        p.color = color; c.drawOval(RectF(-9f, -30f, 9f, -9f), p)
        p.color = eyeColor
        c.drawOval(RectF(-6.8f, -22f, -1.2f, -19f), p)
        c.drawOval(RectF(1.2f, -22f, 6.8f, -19f), p)
    }

    private fun spider(c: Canvas, now: Long) {
        val red = Color.rgb(194, 27, 43); val blue = Color.rgb(24, 54, 120)
        // athletic split-pose body
        fill(c, blue, -10f,-7f, 10f,-7f, 13f,17f, 7f,31f, -8f,29f, -14f,14f)
        fill(c, red, -10f,-7f, 10f,-7f, 8f,12f, -8f,12f)
        limb(c, red, 6f, -7f,0f,-23f,10f); limb(c, blue, 5f, -23f,10f,-30f,25f)
        limb(c, red, 6f, 7f,0f,22f,9f); limb(c, blue, 5f, 22f,9f,29f,23f)
        limb(c, blue, 7f, -6f,26f,-16f,43f); limb(c, red, 6f, -16f,43f,-25f,55f)
        limb(c, blue, 7f, 6f,26f,17f,42f); limb(c, red, 6f, 17f,42f,22f,56f)
        head(c, red)
        // web motif on chest
        stroke(c, Color.rgb(240, 205, 200), 0.8f, -5f,2f, 0f,10f, 5f,2f)
        stroke(c, Color.rgb(240, 205, 200), 0.7f, -7f,7f, 0f,5f, 7f,7f)
        stroke(c, Color.rgb(240, 205, 200), 0.7f, -6f,12f, 0f,15f, 6f,12f)
    }

    private fun sentinel(c: Canvas, now: Long) {
        val black = Color.rgb(23,27,39); val grey = Color.rgb(78,88,108)
        val cape = Path().apply { moveTo(-9f,-5f); lineTo(-26f,18f); lineTo(-32f,64f); lineTo(-5f,52f); lineTo(25f,65f); lineTo(12f,13f); close() }
        p.color = Color.rgb(8,10,17); c.drawPath(cape,p)
        stroke(c, Color.rgb(55,65,84), 1.2f, -24f,23f,-27f,57f,-6f,49f)
        fill(c, black, -9f,-7f, 9f,-7f, 12f,27f, 6f,37f, -8f,36f, -13f,24f)
        limb(c, grey, 7f,-7f,0f,-22f,12f); limb(c,black,6f,-22f,12f,-29f,28f)
        limb(c, grey, 7f,7f,0f,20f,13f); limb(c,black,6f,20f,13f,26f,28f)
        limb(c,black,8f,-7f,32f,-16f,48f); limb(c,grey,6f,-16f,48f,-20f,60f)
        limb(c,black,8f,7f,32f,16f,48f); limb(c,grey,6f,16f,48f,18f,60f)
        fill(c, black, -10f,-28f,-14f,-42f,-4f,-33f, 4f,-33f,14f,-42f,10f,-27f)
        head(c, black, Color.rgb(244,211,140))
        stroke(c, Color.rgb(178,151,96), 2f, -7f,5f,7f,5f)
    }

    private fun storm(c: Canvas, now: Long) {
        val blue = Color.rgb(38,72,145); val red = Color.rgb(145,25,43); val gold = Color.rgb(238,188,67)
        val wave = sin(now / 150.0).toFloat() * 4f
        fill(c, red, -8f,-6f, 9f,-6f, 27f,17f, 19f,63f, 1f,51f, -23f,64f, -27f,18f)
        stroke(c, gold, 2f, -20f,25f,-12f,36f,-22f,50f)
        fill(c, blue, -10f,-7f,10f,-7f,13f,25f,6f,36f,-8f,35f,-13f,22f)
        limb(c, gold,6f,-7f,0f,-20f,12f); limb(c,blue,6f,-20f,12f,-28f,26f)
        limb(c, gold,6f,7f,0f,21f,12f); limb(c,blue,6f,21f,12f,28f,27f)
        limb(c,blue,8f,-7f,32f,-15f,48f); limb(c,gold,6f,-15f,48f,-21f,60f)
        limb(c,blue,8f,7f,32f,16f,48f); limb(c,gold,6f,16f,48f,22f,60f)
        head(c, Color.rgb(184,145,105))
        stroke(c, gold, 2f, -7f,4f,7f,4f)
        stroke(c, Color.argb(210,255,231,126), 2.2f, 24f, 2f+wave, 15f,17f+wave, 25f,15f+wave, 15f,31f+wave)
    }

    private fun trickster(c: Canvas, now: Long) {
        val green = Color.rgb(25,111,75); val gold = Color.rgb(210,164,58)
        val coat = Path().apply { moveTo(-8f,-6f); lineTo(-20f,16f); lineTo(-30f,60f); lineTo(-3f,52f); lineTo(25f,62f); lineTo(12f,15f); close() }
        p.color = Color.rgb(11,64,47); c.drawPath(coat,p)
        stroke(c, gold, 2f,-22f,25f,-13f,36f,-24f,50f)
        fill(c, green,-10f,-7f,10f,-7f,12f,26f,7f,36f,-8f,36f,-13f,23f)
        limb(c,gold,6f,-7f,0f,-21f,12f); limb(c,green,6f,-21f,12f,-27f,27f)
        limb(c,gold,6f,7f,0f,20f,12f); limb(c,green,6f,20f,12f,26f,27f)
        limb(c,green,7f,-7f,32f,-16f,48f); limb(c,gold,5f,-16f,48f,-22f,59f)
        limb(c,green,7f,7f,32f,16f,48f); limb(c,gold,5f,16f,48f,21f,59f)
        fill(c, gold,-10f,-26f,-18f,-42f,-3f,-33f, 4f,-33f,18f,-42f,10f,-26f)
        head(c, Color.rgb(207,169,132))
        val pulse = (sin(now / 180.0).toFloat()*0.5f+0.5f)
        p.color = Color.argb((100+150*pulse).toInt(),75,255,170)
        c.drawCircle(25f,27f,3f+2f*pulse,p)
    }
}
