package com.example.dangleheroes

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.*

/**
 * Full-screen pixel-art renderer.
 *
 * Physics model:
 * - angular pendulum motion for the swing
 * - radial spring/damper for rope extension and recoil
 * - tangential velocity from the user's drag gesture
 * - soft angular limits derived from the actual screen-safe area
 */
class DangleView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sprites = ArrayList<Bitmap>()

    private var hero = 0
    private var frame = 0
    private var animClock = 0f

    private var angle = 0.02f
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
    private val spriteWidth = 64f
    private val spriteHeight = 80f

    var onHeroPosition: ((Float, Float) -> Unit)? = null

    init {
        for (i in 0..3) {
            context.assets.open("heroes/hero_$i.png").use {
                sprites.add(BitmapFactory.decodeStream(it))
            }
        }
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun chooseHero(index: Int) {
        hero = ((index % sprites.size) + sprites.size) % sprites.size
        animClock = 0f
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
        angularVelocity = 0f
        radialVelocity = 0f
    }

    fun movePull(x: Float, y: Float) {
        if (!pulling) return

        val maxPull = min(screenH * 0.22f, 280f * density)
        val dx = x - pullStartX
        pullDistance = (y - pullStartY).coerceIn(0f, maxPull)

        // Sideways movement sets the swing angle; downward movement stretches the rope.
        val maxPullAngle = 1.05f
        angle = (dx / max(ropeRest, 1f)).coerceIn(-maxPullAngle, maxPullAngle)
        ropeLength = ropeRest + pullDistance

        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastPointerTime).coerceAtLeast(1L)) / 1000f
        pointerVX = ((x - lastPointerX) / dt).coerceIn(-2400f, 2400f)
        pointerVY = ((y - lastPointerY) / dt).coerceIn(-2400f, 2400f)
        lastPointerX = x
        lastPointerY = y
        lastPointerTime = now
        invalidate()
    }

    fun releasePull() {
        if (!pulling) return
        pulling = false

        val extension = pullDistance
        val extensionRatio = (extension / max(1f, 280f * density)).coerceIn(0f, 1f)

        // Convert drag velocity into angular/tangential momentum.
        val tangentialKick = (pointerVX / max(1f, ropeRest)) * 0.62f
        angularVelocity += tangentialKick.coerceIn(-4.2f, 4.2f)

        // A stretched elastic tether pulls radially back toward rest length.
        // A deliberate downward pull stores energy; release produces an upward recoil.
        val springKick = sqrt(max(0f, extension * 0.055f)) * 5.0f
        radialVelocity = -springKick.coerceIn(0f, 520f * density)

        // A fast upward finger flick adds a little extra launch energy.
        if (pointerVY < -180f) {
            radialVelocity += (pointerVY * 0.16f).coerceAtLeast(-300f * density)
        }

        // Pulling far to one side gives the swing a smooth initial push.
        angularVelocity += (if (abs(angle) < 0.06f) sign(pointerVX) else sign(angle)) *
            extensionRatio * 0.75f

        pullDistance = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        screenW = width.toFloat()
        screenH = height.toFloat()
        if (screenW <= 0f || screenH <= 0f) return

        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastFrameTime).coerceIn(1L, 24L)) / 1000f
        lastFrameTime = now

        anchorX = screenW * 0.5f
        anchorY = 2f * density
        ropeRest = min(screenW * 0.44f, screenH * 0.27f).coerceAtLeast(132f * density)

        if (!pulling) stepPhysics(dt)

        val safeHalfWidth = screenW * 0.5f - 38f * density
        val maxAngle = asin((safeHalfWidth / max(ropeLength, 1f)).coerceIn(0f, 0.96f))
        val visibleAngle = angle.coerceIn(-maxAngle, maxAngle)

        // Derive both rope and hero position from the same constrained angle.
        // This keeps the tether physically connected and prevents edge clipping.
        heroX = anchorX + sin(visibleAngle) * ropeLength
        heroY = anchorY + cos(visibleAngle) * ropeLength

        drawTether(canvas, anchorX, anchorY, heroX, heroY, now)
        animClock += dt
        val fps = if (pulling) 10f else (5.5f + min(4f, abs(angularVelocity) * 0.8f))
        frame = (animClock * fps).toInt() % 4
        drawSprite(canvas, heroX, heroY, visibleAngle)

        onHeroPosition?.invoke(heroX, heroY)
        postInvalidateOnAnimation()
    }

    private fun stepPhysics(dt: Float) {
        // Radial spring: rope length returns to its rest length with damped recoil.
        val extension = ropeLength - ropeRest
        val radialAcceleration = -30f * extension - 8.5f * radialVelocity
        radialVelocity += radialAcceleration * dt
        ropeLength += radialVelocity * dt
        if (ropeLength < ropeRest) {
            ropeLength = ropeRest
            if (radialVelocity < 0f) radialVelocity = 0f
        }
        ropeLength = ropeLength.coerceAtMost(ropeRest + 280f * density)

        // Pendulum equation with damping. Gravity increases the swing's return force.
        val lengthDp = max(70f, ropeLength / density)
        val angularAcceleration = -(9.81f / lengthDp) * sin(angle) -
            (0.34f + abs(radialVelocity) / max(1f, ropeRest) * 0.12f) * angularVelocity
        angularVelocity += angularAcceleration * dt
        angle += angularVelocity * dt

        // Soft screen-edge barrier. Limit is calculated from screen width and sprite bounds.
        val safeHalfWidth = screenW * 0.5f - 38f * density
        val maxAngle = asin((safeHalfWidth / max(ropeLength, 1f)).coerceIn(0f, 0.96f))
        if (angle > maxAngle) {
            angle = maxAngle
            if (angularVelocity > 0f) angularVelocity *= -0.24f
        } else if (angle < -maxAngle) {
            angle = -maxAngle
            if (angularVelocity < 0f) angularVelocity *= -0.24f
        }

        if (abs(angularVelocity) < 0.001f && abs(angle) < 0.012f &&
            abs(ropeLength - ropeRest) < 0.8f * density) {
            angle *= 0.985f
            angularVelocity *= 0.94f
        }
    }

    private fun drawTether(c: Canvas, ax: Float, ay: Float, hx: Float, hy: Float, now: Long) {
        val tension = ((ropeLength - ropeRest) / (280f * density)).coerceIn(0f, 1f)
        val swingSpeed = abs(angularVelocity)
        val sag = if (pulling) 0f else (2.5f + 3f * (1f - min(1f, swingSpeed))) * density
        val sway = if (pulling) 0f else sin(now / 370.0).toFloat() * 2.2f * density
        val midX = (ax + hx) * 0.5f + sway
        val midY = (ay + hy) * 0.5f + sag * (1f - tension)

        val rope = Path().apply {
            moveTo(ax, ay)
            quadTo(midX, midY, hx, hy - 23f * density)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 6f * density
        paint.color = Color.argb(110, 0, 0, 0)
        c.drawPath(rope, paint)
        paint.strokeWidth = 3.2f * density
        paint.color = Color.rgb(37, 43, 54)
        c.drawPath(rope, paint)
        paint.strokeWidth = 1.1f * density
        paint.color = Color.rgb(215, 222, 232)
        c.drawPath(rope, paint)

        if (pulling && pullDistance > 2f) {
            paint.strokeWidth = (1.4f + 2.2f * tension) * density
            paint.color = Color.argb((75 + 150 * tension).toInt(), 110, 205, 255)
            c.drawPath(rope, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        c.drawCircle(ax, ay, 3f * density, paint)
        // Small anchor ring gives the rope a visible attachment point.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * density
        paint.color = Color.rgb(190, 199, 215)
        c.drawCircle(ax, ay + 4f * density, 4.5f * density, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawSprite(c: Canvas, x: Float, y: Float, swingAngle: Float) {
        val bitmap = sprites[hero]
        val src = Rect(frame * 64, 0, (frame + 1) * 64, 80)
        val scale = density
        val dst = RectF(
            x - 32f * scale,
            y - 17f * scale,
            x + 32f * scale,
            y + 63f * scale
        )
        c.save()
        // Body follows the swing but counter-rotates slightly like a hanging body.
        val bodyRotation = Math.toDegrees(swingAngle.toDouble()).toFloat() * 0.58f
        c.rotate(bodyRotation, x, y)
        paint.isFilterBitmap = false
        c.drawBitmap(bitmap, src, dst, paint)
        paint.isFilterBitmap = true
        c.restore()
    }
}
