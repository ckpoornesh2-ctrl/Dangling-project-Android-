package com.example.dangleheroes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/**
 * Lucky Dangle renderer.
 * Shows exactly one glossy 3D charm at a time. Tapping the charm advances
 * through the five charms; dragging stretches the live cord and releasing
 * launches a damped swing. Releasing inside the bottom drop zone closes it.
 */
class DangleView(context: Context) : View(context) {
    companion object {
        private val CHARM_NAMES = listOf(
            "Evil Eye", "Chili & Lemon", "Daruma", "Lucky Cat", "Oni Mask"
        )
    }

    private data class MotionState(
        var ropeRest: Float,
        var ropeLength: Float,
        var angle: Float,
        var angularVelocity: Float = 0f,
        var radialVelocity: Float = 0f,
        var pullDistance: Float = 0f,
        var pulling: Boolean = false,
        var pullStartX: Float = 0f,
        var pullStartY: Float = 0f,
        var pointerY: Float = 0f,
        var lastPointerX: Float = 0f,
        var lastPointerY: Float = 0f,
        var lastPointerTime: Long = 0L,
        var pointerVX: Float = 0f,
        var pointerVY: Float = 0f
    )

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }
    private val charms = ArrayList<Bitmap>(CHARM_NAMES.size)
    private val motions = ArrayList<MotionState>(CHARM_NAMES.size)

    private var selectedCharm = 0
    private var lastFrameTime = SystemClock.uptimeMillis()
    private var screenW = 0f
    private var screenH = 0f
    private var anchorY = dp(4f)

    /** Center of the active charm; used to keep one movable touch window aligned. */
    var onCharmPosition: ((Float, Float) -> Unit)? = null

    init {
        for (i in CHARM_NAMES.indices) {
            val bitmap = context.assets.open("charms/charm_$i.png").use {
                BitmapFactory.decodeStream(it)
            } ?: throw IllegalStateException("Missing or invalid charm image: charms/charm_$i.png")
            require(bitmap.width > 0 && bitmap.height > 0) {
                "Charm image charm_$i.png has invalid dimensions"
            }
            charms.add(bitmap)
            val rest = dp(68f)
            motions.add(MotionState(rest, rest, if (i % 2 == 0) 0.018f else -0.018f))
        }
    }

    fun beginPull(x: Float, y: Float) {
        val state = activeMotion()
        state.pulling = true
        state.pullStartX = x
        state.pullStartY = y
        state.pointerY = y
        state.lastPointerX = x
        state.lastPointerY = y
        state.lastPointerTime = SystemClock.uptimeMillis()
        state.pointerVX = 0f
        state.pointerVY = 0f
        state.pullDistance = 0f
        state.angularVelocity *= 0.20f
        state.radialVelocity = 0f
    }

    fun movePull(x: Float, y: Float) {
        val state = activeMotion()
        if (!state.pulling) return

        val maxPull = min(screenH * 0.72f, dp(620f)).coerceAtLeast(dp(220f))
        val dx = x - state.pullStartX
        state.pullDistance = (y - state.pullStartY).coerceIn(0f, maxPull)
        state.pointerY = y
        state.angle = (dx / max(state.ropeRest, dp(56f))).coerceIn(-1.05f, 1.05f)
        state.ropeLength = state.ropeRest + state.pullDistance

        val now = SystemClock.uptimeMillis()
        val dt = ((now - state.lastPointerTime).coerceAtLeast(1L)) / 1000f
        state.pointerVX = ((x - state.lastPointerX) / dt).coerceIn(-2800f, 2800f)
        state.pointerVY = ((y - state.lastPointerY) / dt).coerceIn(-2800f, 2800f)
        state.lastPointerX = x
        state.lastPointerY = y
        state.lastPointerTime = now
        invalidate()
    }

    fun releasePull() {
        val state = activeMotion()
        if (!state.pulling) return
        state.pulling = false

        val extension = (state.ropeLength - state.ropeRest).coerceAtLeast(0f)
        val maxPull = min(screenH * 0.72f, dp(620f)).coerceAtLeast(dp(220f))
        val extensionRatio = (extension / maxPull).coerceIn(0f, 1f)

        if (extension > dp(5f)) {
            var launchSpeed = extension * 2.05f
            if (state.pointerVY < -110f) launchSpeed += abs(state.pointerVY) * 0.18f
            state.radialVelocity = -launchSpeed.coerceAtMost(dp(1050f))
        }

        if (extension > dp(5f) || abs(state.pointerVX) > 85f) {
            val direction = when {
                abs(state.pointerVX) > 85f -> sign(state.pointerVX)
                abs(state.angle) > 0.04f -> sign(state.angle)
                else -> 1f
            }
            val tangentialKick = (state.pointerVX / max(1f, state.ropeRest)) * 0.62f
            state.angularVelocity += tangentialKick.coerceIn(-4.2f, 4.2f)
            state.angularVelocity += direction * (0.42f + extensionRatio * 1.65f)
            state.angularVelocity = state.angularVelocity.coerceIn(-5.4f, 5.4f)
        }
        state.pullDistance = 0f
        invalidate()
    }

    /** Tap advances to the next charm; each charm keeps its own swing state. */
    fun nextCharm() {
        selectedCharm = (selectedCharm + 1) % charms.size
        lastFrameTime = SystemClock.uptimeMillis()
        invalidate()
    }

    fun isInBottomDropZone(rawY: Float): Boolean {
        val screenBottom = if (screenH > 0f) screenH else resources.displayMetrics.heightPixels.toFloat()
        return rawY >= screenBottom - dp(104f)
    }

    private fun activeMotion() = motions[selectedCharm]

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        screenW = width.toFloat()
        screenH = height.toFloat()
        if (screenW <= 0f || screenH <= 0f) return

        val now = SystemClock.uptimeMillis()
        val elapsed = ((now - lastFrameTime).coerceIn(1L, 32L)) / 1000f
        lastFrameTime = now

        val state = activeMotion()
        val bitmap = charms[selectedCharm]
        if (!state.pulling) stepPhysics(state, elapsed, now)

        val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        // Compact floating size: roughly 60% of the previous width, so the charm
        // reads as a small screen accessory instead of a large overlay.
        val maxCharmHeight = min(screenH * 0.36f, dp(300f))
        val maxCharmWidth = min(dp(112f), screenW * 0.31f)
        val charmWidth = min(maxCharmWidth, maxCharmHeight * aspect)
        val charmHeight = charmWidth / aspect

        val anchorX = screenW * 0.5f
        val safeHalfWidth = max(dp(10f), screenW * 0.5f - charmWidth * 0.5f - dp(12f))
        val maxAngle = asin((safeHalfWidth / max(state.ropeLength, 1f)).coerceIn(0f, 0.96f))
        val visibleAngle = state.angle.coerceIn(-maxAngle, maxAngle)
        val pivotX = anchorX + sin(visibleAngle) * state.ropeLength
        val pivotY = anchorY + cos(visibleAngle) * state.ropeLength

        drawCord(canvas, anchorX, anchorY, pivotX, pivotY, state, now)
        drawCharm(canvas, bitmap, pivotX, pivotY, charmWidth, charmHeight, visibleAngle)
        drawSpecularGlint(canvas, pivotX, pivotY, charmWidth, charmHeight, now)
        if (state.pulling && isInBottomDropZone(state.pointerY)) {
            drawBottomDropHint(canvas)
        }

        onCharmPosition?.invoke(pivotX, pivotY + charmHeight * 0.53f)
        postInvalidateOnAnimation()
    }

    /** Damped spring + pendulum with short substeps for stable, responsive motion. */
    private fun stepPhysics(state: MotionState, elapsed: Float, now: Long) {
        val steps = max(1, ceil(elapsed / 0.008f).toInt())
        val dt = elapsed / steps
        repeat(steps) {
            val extension = state.ropeLength - state.ropeRest
            val springStiffness = 48f
            val springDamping = 8.7f
            val radialAcceleration = -springStiffness * extension - springDamping * state.radialVelocity
            state.radialVelocity += radialAcceleration * dt
            state.ropeLength += state.radialVelocity * dt

            if (state.ropeLength < state.ropeRest) {
                state.ropeLength = state.ropeRest
                if (state.radialVelocity < 0f) state.radialVelocity *= -0.22f
            }
            state.ropeLength = state.ropeLength.coerceAtMost(state.ropeRest + dp(620f))

            val lengthDp = max(65f, state.ropeLength / density)
            val breeze = sin(now / 1750.0).toFloat() * 0.055f
            val angularAcceleration = -(1050f / lengthDp) * sin(state.angle) -
                (0.44f + abs(state.radialVelocity) / max(1f, state.ropeRest) * 0.065f) * state.angularVelocity + breeze
            state.angularVelocity += angularAcceleration * dt
            state.angle += state.angularVelocity * dt

            val screenSafeHalf = max(dp(8f), screenW * 0.5f - dp(70f))
            val limit = asin((screenSafeHalf / max(state.ropeLength, 1f)).coerceIn(0f, 0.96f))
            if (state.angle > limit) {
                state.angle = limit
                if (state.angularVelocity > 0f) state.angularVelocity *= -0.25f
            } else if (state.angle < -limit) {
                state.angle = -limit
                if (state.angularVelocity < 0f) state.angularVelocity *= -0.25f
            }

            if (abs(state.angularVelocity) < 0.006f && abs(state.angle) < 0.01f &&
                abs(state.ropeLength - state.ropeRest) < dp(0.8f)) {
                state.angle *= 0.985f
                state.angularVelocity *= 0.94f
                if (abs(state.radialVelocity) < dp(0.8f)) state.radialVelocity = 0f
            }
        }
    }

    private fun drawCord(
        c: Canvas, ax: Float, ay: Float, px: Float, py: Float,
        state: MotionState, now: Long
    ) {
        val tension = ((state.ropeLength - state.ropeRest) / dp(620f)).coerceIn(0f, 1f)
        val speed = abs(state.angularVelocity)
        val sag = if (state.pulling) 0f else dp(2f + 3.5f * (1f - min(1f, speed * 0.22f)))
        val sway = if (state.pulling) 0f else sin(now / 390.0).toFloat() * dp(1.4f)
        val midX = (ax + px) * 0.5f + sway
        val midY = (ay + py) * 0.5f + sag * (1f - tension)
        val cord = Path().apply {
            moveTo(ax, ay)
            quadTo(midX, midY, px, py)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = dp(4.2f)
        paint.color = Color.argb(75, 36, 20, 8)
        c.drawPath(cord, paint)
        paint.strokeWidth = dp(2.1f)
        paint.color = Color.rgb(145, 85, 26)
        c.drawPath(cord, paint)
        paint.strokeWidth = dp(0.85f)
        paint.color = Color.rgb(255, 215, 135)
        c.drawPath(cord, paint)

        if (state.pulling && state.pullDistance > dp(3f)) {
            paint.strokeWidth = dp(1.2f + tension * 1.5f)
            paint.color = Color.argb((85 + 125 * tension).toInt(), 255, 232, 178)
            c.drawPath(cord, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(255, 226, 174)
        c.drawCircle(ax, ay, dp(2.4f), paint)
    }

    private fun drawCharm(
        c: Canvas, bitmap: Bitmap, pivotX: Float, pivotY: Float,
        targetWidth: Float, targetHeight: Float, swingAngle: Float
    ) {
        val dst = RectF(
            pivotX - targetWidth * 0.5f,
            pivotY,
            pivotX + targetWidth * 0.5f,
            pivotY + targetHeight
        )
        c.save()
        val bodyRotation = Math.toDegrees(swingAngle.toDouble()).toFloat() * 0.24f
        c.rotate(bodyRotation, pivotX, pivotY + targetHeight * 0.14f)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.isFilterBitmap = true
        c.drawBitmap(bitmap, null, dst, paint)
        c.restore()
    }

    private fun drawSpecularGlint(
        c: Canvas, pivotX: Float, pivotY: Float, targetWidth: Float,
        targetHeight: Float, now: Long
    ) {
        val phase = now / 940.0 + selectedCharm * 0.91
        val pulse = sin(phase).toFloat() * 0.5f + 0.5f
        val x = pivotX + targetWidth * (0.25f + 0.07f * sin(phase * 0.7).toFloat())
        val y = pivotY + targetHeight * (0.43f + 0.05f * cos(phase).toFloat())
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dp(0.8f)
        paint.color = Color.argb((14 + pulse * 38).toInt(), 255, 255, 255)
        val radius = dp(2.4f + pulse * 1.7f)
        c.drawLine(x - radius, y, x + radius, y, paint)
        c.drawLine(x, y - radius, x, y + radius, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawBottomDropHint(c: Canvas) {
        val panel = RectF(
            screenW * 0.5f - dp(112f),
            screenH - dp(82f),
            screenW * 0.5f + dp(112f),
            screenH - dp(22f)
        )
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(220, 25, 28, 39)
        c.drawRoundRect(panel, dp(18f), dp(18f), paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = dp(14f)
        paint.isFakeBoldText = true
        c.drawText("Release to close", screenW * 0.5f, screenH - dp(46f), paint)
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.LEFT
    }

    private fun dp(value: Float): Float = value * density
}
