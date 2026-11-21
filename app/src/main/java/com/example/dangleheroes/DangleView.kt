package com.example.dangleheroes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/**
 * Japanese Chibi Edition renderer.
 *
 * Each character has a seven-pose sprite strip. The long tether is drawn here,
 * while the short hand-grip segment in each pose keeps the character attached.
 * Pull/release uses a damped spring and a pendulum-style swing with substeps.
 */
class DangleView(context: Context) : View(context) {
    companion object {
        private const val FRAME_COUNT = 7
        private val HERO_NAMES = listOf("Sora", "Hana", "Kaito", "Miyu", "Ren")
    }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sprites = ArrayList<Bitmap>(5)

    private var hero = 0
    private var frame = 0
    private var animClock = 0f

    private var angle = 0.08f
    private var angularVelocity = 0f
    private var ropeRest = 180f * density
    private var ropeLength = ropeRest
    private var radialVelocity = 0f
    private var pullDistance = 0f
    private var pulling = false
    private var pullStartX = 0f
    private var pullStartY = 0f
    private var lastPointerX = 0f
    private var lastPointerY = 0f
    private var lastPointerTime = 0L
    private var pointerVX = 0f
    private var pointerVY = 0f
    private var lastFrameTime = SystemClock.uptimeMillis()

    private var screenW = 0f
    private var screenH = 0f
    private var anchorX = 0f
    private var anchorY = 0f
    private var heroX = 0f
    private var heroY = 0f

    var onHeroPosition: ((Float, Float) -> Unit)? = null

    init {
        for (i in 0 until HERO_NAMES.size) {
            val bitmap = context.assets.open("heroes/hero_$i.png").use {
                BitmapFactory.decodeStream(it)
            } ?: throw IllegalStateException("Missing or invalid hero sprite: heroes/hero_$i.png")
            require(bitmap.width % FRAME_COUNT == 0) {
                "Sprite hero_$i.png must contain exactly $FRAME_COUNT equal-width frames"
            }
            sprites.add(bitmap)
        }
        paint.isFilterBitmap = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun chooseHero(index: Int) {
        hero = ((index % sprites.size) + sprites.size) % sprites.size
        animClock = 0f
        frame = 0
        invalidate()
    }

    fun nextHero() = chooseHero(hero + 1)

    fun beginPull(x: Float, y: Float) {
        pulling = true
        pullStartX = x
        pullStartY = y
        lastPointerX = x
        lastPointerY = y
        lastPointerTime = SystemClock.uptimeMillis()
        pointerVX = 0f
        pointerVY = 0f
        // A new gesture should not fight the previous swing's radial motion.
        angularVelocity *= 0.18f
        radialVelocity = 0f
        pullDistance = 0f
    }

    fun movePull(x: Float, y: Float) {
        if (!pulling) return

        val maxPull = min(screenH * 0.25f, 320f * density).coerceAtLeast(120f * density)
        val dx = x - pullStartX
        pullDistance = (y - pullStartY).coerceIn(0f, maxPull)
        val maxPullAngle = 1.12f
        angle = (dx / max(ropeRest, 1f)).coerceIn(-maxPullAngle, maxPullAngle)
        ropeLength = ropeRest + pullDistance

        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastPointerTime).coerceAtLeast(1L)) / 1000f
        pointerVX = ((x - lastPointerX) / dt).coerceIn(-2600f, 2600f)
        pointerVY = ((y - lastPointerY) / dt).coerceIn(-2600f, 2600f)
        lastPointerX = x
        lastPointerY = y
        lastPointerTime = now
        invalidate()
    }

    fun releasePull() {
        if (!pulling) return
        pulling = false

        val extension = (ropeLength - ropeRest).coerceAtLeast(0f)
        val maxPull = min(screenH * 0.25f, 320f * density).coerceAtLeast(120f * density)
        val extensionRatio = (extension / maxPull).coerceIn(0f, 1f)

        // The old kick was only a few pixels/second. This scales impulse with
        // actual pull distance and adds a little extra energy for a quick flick.
        var launchSpeed = extension * 2.35f
        if (pointerVY < -120f) launchSpeed += abs(pointerVY) * 0.24f
        radialVelocity = -launchSpeed.coerceAtMost(820f * density)

        val direction = when {
            abs(pointerVX) > 90f -> sign(pointerVX)
            abs(angle) > 0.05f -> sign(angle)
            else -> 1f
        }
        val tangentialKick = (pointerVX / max(1f, ropeRest)) * 0.72f
        angularVelocity += tangentialKick.coerceIn(-4.5f, 4.5f)
        angularVelocity += direction * (0.72f + extensionRatio * 2.15f)
        angularVelocity = angularVelocity.coerceIn(-5.8f, 5.8f)
        pullDistance = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        screenW = width.toFloat()
        screenH = height.toFloat()
        if (screenW <= 0f || screenH <= 0f) return

        val now = SystemClock.uptimeMillis()
        val elapsed = ((now - lastFrameTime).coerceIn(1L, 32L)) / 1000f
        lastFrameTime = now

        anchorX = screenW * 0.5f
        anchorY = 2f * density
        ropeRest = min(screenW * 0.44f, screenH * 0.27f).coerceAtLeast(140f * density)

        if (!pulling) stepPhysics(elapsed)

        val safeHalfWidth = max(0f, screenW * 0.5f - 54f * density)
        val maxAngle = kotlin.math.asin((safeHalfWidth / max(ropeLength, 1f)).coerceIn(0f, 0.96f))
        val visibleAngle = angle.coerceIn(-maxAngle, maxAngle)
        heroX = anchorX + sin(visibleAngle) * ropeLength
        heroY = anchorY + cos(visibleAngle) * ropeLength

        animClock += elapsed
        val baseFps = when (hero) {
            0 -> 5.6f // Sora: flowing scarf and wind
            1 -> 6.2f // Hana: petals and twirls
            2 -> 7.2f // Kaito: quick electric dashes
            3 -> 6.4f // Miyu: playful bounces
            else -> 5.2f // Ren: slow, drifting glide
        }
        val speedBoost = min(5.0f, abs(angularVelocity) * 1.15f)
        frame = (animClock * (baseFps + speedBoost)).toInt() % FRAME_COUNT

        drawCharacterEffects(canvas, heroX, heroY, now, visibleAngle)
        drawTether(canvas, anchorX, anchorY, heroX, heroY - 35f * density, now)
        drawSprite(canvas, heroX, heroY, visibleAngle)

        // Keep the touch target over the character's body, not the rope hand.
        onHeroPosition?.invoke(heroX, heroY + 30f * density)
        postInvalidateOnAnimation()
    }

    /** Stable spring/pendulum integration using short substeps. */
    private fun stepPhysics(elapsed: Float) {
        val steps = max(1, kotlin.math.ceil(elapsed / 0.008f).toInt())
        val dt = elapsed / steps
        repeat(steps) {
            val extension = ropeLength - ropeRest
            val springStiffness = 76f
            val springDamping = 10.8f
            val radialAcceleration = -springStiffness * extension - springDamping * radialVelocity
            radialVelocity += radialAcceleration * dt
            ropeLength += radialVelocity * dt

            // A soft rebound at the natural rope length gives the release a
            // visible up-then-down follow-through instead of stopping abruptly.
            if (ropeLength < ropeRest) {
                ropeLength = ropeRest
                if (radialVelocity < 0f) radialVelocity *= -0.30f
            }
            ropeLength = ropeLength.coerceAtMost(ropeRest + 320f * density)

            val lengthDp = max(70f, ropeLength / density)
            val gravity = 1280f
            val angularAcceleration = -(gravity / lengthDp) * sin(angle) -
                (0.62f + abs(radialVelocity) / max(1f, ropeRest) * 0.10f) * angularVelocity
            angularVelocity += angularAcceleration * dt
            angle += angularVelocity * dt

            val safeHalfWidth = max(0f, screenW * 0.5f - 54f * density)
            val limit = kotlin.math.asin((safeHalfWidth / max(ropeLength, 1f)).coerceIn(0f, 0.96f))
            if (angle > limit) {
                angle = limit
                if (angularVelocity > 0f) angularVelocity *= -0.28f
            } else if (angle < -limit) {
                angle = -limit
                if (angularVelocity < 0f) angularVelocity *= -0.28f
            }

            if (abs(angularVelocity) < 0.008f && abs(angle) < 0.012f &&
                abs(ropeLength - ropeRest) < 0.8f * density) {
                angle *= 0.985f
                angularVelocity *= 0.94f
                if (abs(radialVelocity) < 1.5f) radialVelocity = 0f
            }
        }
    }

    private fun drawTether(c: Canvas, ax: Float, ay: Float, hx: Float, hy: Float, now: Long) {
        val tension = ((ropeLength - ropeRest) / (320f * density)).coerceIn(0f, 1f)
        val swingSpeed = abs(angularVelocity)
        val sag = if (pulling) 0f else (2.0f + 5.0f * (1f - min(1f, swingSpeed * 0.25f))) * density
        val sway = if (pulling) 0f else sin(now / 320.0).toFloat() * 2.0f * density
        val midX = (ax + hx) * 0.5f + sway
        val midY = (ay + hy) * 0.5f + sag * (1f - tension)
        val rope = Path().apply {
            moveTo(ax, ay)
            quadTo(midX, midY, hx, hy)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 7f * density
        paint.color = Color.argb(95, 0, 0, 0)
        c.drawPath(rope, paint)
        paint.strokeWidth = 3.6f * density
        paint.color = Color.rgb(45, 48, 60)
        c.drawPath(rope, paint)
        paint.strokeWidth = 1.15f * density
        paint.color = Color.rgb(224, 224, 230)
        c.drawPath(rope, paint)

        if (pulling && pullDistance > 3f) {
            paint.strokeWidth = (1.3f + tension * 2.5f) * density
            paint.color = Color.argb((70 + 150 * tension).toInt(), 255, 202, 128)
            c.drawPath(rope, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        c.drawCircle(ax, ay, 3.2f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = Color.rgb(193, 198, 213)
        c.drawCircle(ax, ay + 4f * density, 4.5f * density, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawSprite(c: Canvas, x: Float, y: Float, swingAngle: Float) {
        val bitmap = sprites[hero]
        val cellWidth = bitmap.width / FRAME_COUNT
        val src = Rect(frame * cellWidth, 0, (frame + 1) * cellWidth, bitmap.height)
        val scale = density
        val dst = RectF(x - 50f * scale, y - 35f * scale, x + 50f * scale, y + 90f * scale)
        c.save()
        val bodyRotation = Math.toDegrees(swingAngle.toDouble()).toFloat() * 0.34f
        c.rotate(bodyRotation, x, y + 25f * scale)
        paint.isFilterBitmap = true
        c.drawBitmap(bitmap, src, dst, paint)
        c.restore()
    }

    private fun drawCharacterEffects(c: Canvas, x: Float, y: Float, now: Long, swingAngle: Float) {
        val centerY = y + 34f * density
        val speed = min(1f, abs(angularVelocity) / 4f)
        when (hero) {
            0 -> drawSoraWind(c, x, centerY, now, speed)
            1 -> drawHanaPetals(c, x, centerY, now, speed)
            2 -> drawKaitoLightning(c, x, centerY, now, speed)
            3 -> drawMiyuPaws(c, x, centerY, now, speed)
            else -> drawRenLeaves(c, x, centerY, now, speed)
        }
    }

    private fun drawSoraWind(c: Canvas, x: Float, y: Float, now: Long, speed: Float) {
        val phase = now / 360.0
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 2.0f * density
        paint.color = Color.argb((90 + speed * 95).toInt(), 236, 194, 126)
        val path = Path().apply {
            moveTo(x - 48f * density, y + 23f * density)
            quadTo(x - 12f * density, y + (8f + sin(phase).toFloat() * 8f) * density,
                x + 39f * density, y - 5f * density)
        }
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        for (i in 0..2) {
            val px = x + (sin(phase + i * 2.1).toFloat() * (42f + i * 5f)) * density
            val py = y + (cos(phase * 0.8 + i).toFloat() * 29f) * density
            paint.color = Color.argb(190, 255, 210, 145)
            c.drawCircle(px, py, (1.8f + speed) * density, paint)
        }
    }

    private fun drawHanaPetals(c: Canvas, x: Float, y: Float, now: Long, speed: Float) {
        val phase = now / 430.0
        paint.style = Paint.Style.FILL
        for (i in 0..5) {
            val a = phase + i * 1.05
            val px = x + sin(a).toFloat() * (38f + (i % 2) * 10f) * density
            val py = y + cos(a * 0.83).toFloat() * (28f + i % 3 * 7f) * density
            c.save()
            c.rotate((a * 50.0).toFloat(), px, py)
            paint.color = if (i % 2 == 0) Color.argb(205, 255, 153, 190) else Color.argb(190, 255, 210, 224)
            c.drawOval(RectF(px - 2.2f * density, py - 4.2f * density,
                px + 2.2f * density, py + 4.2f * density), paint)
            c.restore()
        }
    }

    private fun drawKaitoLightning(c: Canvas, x: Float, y: Float, now: Long, speed: Float) {
        val pulse = (sin(now / 95.0).toFloat() * 0.5f + 0.5f)
        for (side in -1..1 step 2) {
            val sx = x + side * 42f * density
            val sy = y - 12f * density
            val ex = x + side * (26f + pulse * 9f) * density
            val ey = y + 38f * density
            val path = Path().apply {
                moveTo(sx, sy)
                lineTo(x + side * 26f * density, y - 2f * density)
                lineTo(x + side * 12f * density, y + 12f * density)
                lineTo(x + side * 30f * density, y + 20f * density)
                lineTo(ex, ey)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = (1.8f + speed * 1.4f) * density
            paint.color = Color.argb((125 + pulse * 100).toInt(), 72, 164, 255)
            c.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((110 + pulse * 120).toInt(), 126, 202, 255)
        c.drawCircle(x + sin(now / 120.0).toFloat() * 42f * density,
            y - 20f * density, 2f * density, paint)
    }

    private fun drawMiyuPaws(c: Canvas, x: Float, y: Float, now: Long, speed: Float) {
        val phase = now / 500.0
        for (i in 0..2) {
            val a = phase + i * 2.1
            val px = x + sin(a).toFloat() * 48f * density
            val py = y + cos(a).toFloat() * 30f * density
            drawPaw(c, px, py, (5.0f + speed * 1.5f) * density,
                if (i % 2 == 0) Color.argb(190, 255, 180, 127) else Color.argb(185, 255, 135, 177))
        }
    }

    private fun drawPaw(c: Canvas, x: Float, y: Float, size: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawCircle(x, y + size * 0.22f, size * 0.34f, paint)
        c.drawCircle(x - size * 0.40f, y - size * 0.20f, size * 0.19f, paint)
        c.drawCircle(x - size * 0.12f, y - size * 0.48f, size * 0.19f, paint)
        c.drawCircle(x + size * 0.19f, y - size * 0.48f, size * 0.19f, paint)
        c.drawCircle(x + size * 0.45f, y - size * 0.18f, size * 0.18f, paint)
    }

    private fun drawRenLeaves(c: Canvas, x: Float, y: Float, now: Long, speed: Float) {
        val phase = now / 520.0
        for (i in 0..4) {
            val a = phase + i * 1.28
            val px = x + sin(a).toFloat() * (42f + (i % 2) * 9f) * density
            val py = y + cos(a * 0.78).toFloat() * 31f * density
            c.save()
            c.translate(px, py)
            c.rotate((a * 60.0).toFloat())
            val leaf = Path().apply {
                moveTo(0f, -6f * density)
                cubicTo(7f * density, -5f * density, 8f * density, 3f * density, 0f, 6f * density)
                cubicTo(-8f * density, 3f * density, -7f * density, -5f * density, 0f, -6f * density)
                close()
            }
            paint.style = Paint.Style.FILL
            paint.color = if (i % 2 == 0) Color.argb(210, 237, 111, 35) else Color.argb(205, 255, 177, 63)
            c.drawPath(leaf, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f * density
            paint.color = Color.argb(160, 128, 70, 35)
            c.drawLine(0f, -4f * density, 0f, 5f * density, paint)
            c.restore()
        }
        paint.style = Paint.Style.FILL
    }
}
