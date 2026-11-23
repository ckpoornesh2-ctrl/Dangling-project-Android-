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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/**
 * Lucky Dangle renderer.
 * One glossy 3D charm is shown at a time. Each charm has its own physical
 * personality, idle motion and subtle visual accent. The cord is rendered
 * independently so it remains attached while the charm swings.
 */
class DangleView(context: Context) : View(context), SensorEventListener {
    companion object {
        private val CHARM_NAMES = listOf(
            "Evil Eye", "Chili & Lemon", "Daruma", "Lucky Cat", "Oni Mask"
        )
    }

    private data class CharmPersonality(
        val springStiffness: Float,
        val springDamping: Float,
        val gravity: Float,
        val angularDamping: Float,
        val launchMultiplier: Float,
        val idleForce: Float,
        val bodyRotation: Float,
        val idleTiltDegrees: Float,
        val swayPeriodMs: Double,
        val glowColor: Int
    )

    private val personalities = listOf(
        // Evil Eye: balanced, steady movement with a cool blue shimmer.
        CharmPersonality(58f, 9.8f, 1040f, 0.78f, 0.96f, 0.020f, 0.22f, 0.45f, 1700.0, Color.rgb(65, 165, 255)),
        // Chili & Lemon: a longer, heavier cluster with a little more side sway.
        CharmPersonality(43f, 8.7f, 1190f, 0.58f, 0.94f, 0.038f, 0.40f, 1.15f, 1280.0, Color.rgb(117, 190, 69)),
        // Daruma: rounded and springy, with a gentle settling wobble.
        CharmPersonality(63f, 10.1f, 1320f, 0.88f, 0.92f, 0.016f, 0.62f, 1.65f, 1920.0, Color.rgb(255, 185, 75)),
        // Lucky Cat: lighter, lively motion and a warm bell-like glint.
        CharmPersonality(70f, 8.2f, 930f, 0.62f, 1.10f, 0.044f, 0.30f, 0.95f, 1080.0, Color.rgb(255, 205, 105)),
        // Oni Mask: heavier, slower swing with stronger damping.
        CharmPersonality(38f, 10.9f, 1480f, 1.03f, 0.82f, 0.010f, 0.16f, 0.35f, 2150.0, Color.rgb(255, 94, 66))
    )

    private data class RopeNode(
        var x: Float,
        var y: Float,
        var oldX: Float,
        var oldY: Float
    )

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
        var pointerVY: Float = 0f,
        val ropeNodes: ArrayList<RopeNode> = ArrayList()
    )

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }
    private val charms = ArrayList<Bitmap>(CHARM_NAMES.size)
    private val motions = ArrayList<MotionState>(CHARM_NAMES.size)

    // Front-facing 3D hook asset. The cord's first point is aligned with its lower eyelet.
    private lateinit var hookBitmap: Bitmap
    private val hookPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }
    private val hookHeightDp = 62f

    // Smooth, round satin-gold cord rendered as a shaded cylinder (no repeated/twisted texture).
    private val cordPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cordCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cordShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cordHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cordLowlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val tiltSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var sensorRegistered = false
    private var sensorInitialized = false
    private var filteredGravityX = 0f
    private var filteredGravityY = 9.81f
    private var targetPhoneTilt = 0f
    private var smoothedPhoneTilt = 0f
    private var phoneMotionEnabled = true

    // The compact 92dp size is the default; menu controls select 72/92/112dp.
    private val sizeWidthsDp = floatArrayOf(72f, 92f, 112f)
    private var sizeIndex = 1

    private var selectedCharm = 0
    private var lastFrameTime = SystemClock.uptimeMillis()
    private var screenW = 0f
    private var screenH = 0f
    private var anchorY = dp(60f)
    private var anchorXOverride = Float.NaN
    private var dangleVisible = true
    private var paused = false

    /** Center of the active charm, used by the service to align the touch area. */
    var onCharmPosition: ((Float, Float) -> Unit)? = null

    fun setAnchorPosition(centerX: Float, cordStartY: Float) {
        anchorXOverride = centerX
        anchorY = cordStartY
        invalidate()
    }

    fun setDangleVisible(visible: Boolean) {
        if (dangleVisible == visible) return
        dangleVisible = visible
        if (!visible && sensorRegistered) {
            sensorManager.unregisterListener(this)
            sensorRegistered = false
        } else if (visible && isAttachedToWindow && !sensorRegistered) {
            tiltSensor?.let { sensor ->
                sensorRegistered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        lastFrameTime = SystemClock.uptimeMillis()
        invalidate()
    }

    fun isDangleVisible(): Boolean = dangleVisible

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

        hookBitmap = context.assets.open("charms/gold_hook.png").use {
            BitmapFactory.decodeStream(it)
        } ?: throw IllegalStateException("Missing front hook asset: charms/gold_hook.png")
        require(hookBitmap.width > 0 && hookBitmap.height > 0) {
            "Hook image gold_hook.png has invalid dimensions"
        }

        cordPaint.apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = dp(4.6f)
            color = Color.rgb(190, 124, 29)
            isFilterBitmap = true
            isDither = true
        }
        cordCenterPaint.apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = dp(2.7f)
            color = Color.rgb(232, 181, 83)
        }
        cordShadowPaint.apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = dp(6.7f)
            color = Color.argb(145, 57, 34, 10)
        }
        cordHighlightPaint.apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = dp(0.82f)
            color = Color.argb(238, 255, 239, 190)
        }
        cordLowlightPaint.apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = dp(0.9f)
            color = Color.argb(205, 106, 65, 18)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (dangleVisible) {
            tiltSensor?.let { sensor ->
                sensorRegistered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    override fun onDetachedFromWindow() {
        if (sensorRegistered) sensorManager.unregisterListener(this)
        sensorRegistered = false
        super.onDetachedFromWindow()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.size < 2) return
        val alpha = if (event.sensor.type == Sensor.TYPE_GRAVITY) 0.10f else 0.035f
        if (!sensorInitialized) {
            filteredGravityX = event.values[0]
            filteredGravityY = event.values[1]
            sensorInitialized = true
        } else {
            filteredGravityX += (event.values[0] - filteredGravityX) * alpha
            filteredGravityY += (event.values[1] - filteredGravityY) * alpha
        }
        targetPhoneTilt = if (phoneMotionEnabled) {
            (atan2(-filteredGravityX, max(3.5f, abs(filteredGravityY))) * 0.72f)
                .coerceIn(-0.24f, 0.24f)
        } else 0f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun beginPull(x: Float, y: Float) {
        // Do not wake a paused charm on touch-down: a stationary long press
        // should open the menu while preserving the user's paused state.
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
        if (paused) paused = false

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

        val profile = personalities[selectedCharm]
        val extension = (state.ropeLength - state.ropeRest).coerceAtLeast(0f)
        val maxPull = min(screenH * 0.72f, dp(620f)).coerceAtLeast(dp(220f))
        val extensionRatio = (extension / maxPull).coerceIn(0f, 1f)

        if (extension > dp(5f)) {
            // Stored elastic energy plus a small amount of upward-flick velocity.
            var launchSpeed = extension * 2.05f * profile.launchMultiplier
            if (state.pointerVY < -110f) launchSpeed += abs(state.pointerVY) * 0.18f
            state.radialVelocity = -launchSpeed.coerceAtMost(dp(1050f))
        }

        if (extension > dp(5f) || abs(state.pointerVX) > 85f) {
            val direction = when {
                abs(state.pointerVX) > 85f -> sign(state.pointerVX)
                abs(state.angle) > 0.04f -> sign(state.angle)
                else -> 1f
            }
            val tangentialKick = (state.pointerVX / max(1f, state.ropeRest)) * 0.66f
            state.angularVelocity += tangentialKick.coerceIn(-4.3f, 4.3f)
            state.angularVelocity += direction * (0.42f + extensionRatio * 1.65f) * profile.launchMultiplier
            state.angularVelocity = state.angularVelocity.coerceIn(-5.5f, 5.5f)
        }
        state.pullDistance = 0f
        lastFrameTime = SystemClock.uptimeMillis()
        invalidate()
    }

    /** Cancels the press used to open the hold menu without adding sling energy. */
    fun cancelPull() {
        val state = activeMotion()
        if (!state.pulling) return
        state.pulling = false
        state.ropeLength = state.ropeRest
        state.pullDistance = 0f
        state.radialVelocity = 0f
        state.angularVelocity *= 0.45f
        state.angle *= 0.45f
        invalidate()
    }

    /** Short tap advances to the next charm. Every charm keeps its own motion. */
    fun nextCharm() {
        selectedCharm = (selectedCharm + 1) % charms.size
        paused = false
        lastFrameTime = SystemClock.uptimeMillis()
        invalidate()
    }

    fun togglePaused(): Boolean {
        paused = !paused
        lastFrameTime = SystemClock.uptimeMillis()
        invalidate()
        return paused
    }

    fun isPaused(): Boolean = paused

    /** The compact 92dp size is the default; the menu can move one step smaller or larger. */
    fun decreaseSize(): String {
        sizeIndex = (sizeIndex - 1).coerceAtLeast(0)
        invalidate()
        return currentSizeLabel()
    }

    fun increaseSize(): String {
        sizeIndex = (sizeIndex + 1).coerceAtMost(sizeWidthsDp.lastIndex)
        invalidate()
        return currentSizeLabel()
    }

    fun currentSizeLabel(): String = "${sizeWidthsDp[sizeIndex].toInt()}dp"

    fun togglePhoneMotion(): Boolean {
        phoneMotionEnabled = !phoneMotionEnabled
        if (!phoneMotionEnabled) targetPhoneTilt = 0f
        invalidate()
        return phoneMotionEnabled
    }

    fun isPhoneMotionEnabled(): Boolean = phoneMotionEnabled

    fun currentCharmName(): String = CHARM_NAMES[selectedCharm]

    /** The close zone begins just below the screen midpoint, not at the very bottom. */
    fun isInBottomDropZone(rawY: Float): Boolean {
        val screenHeight = if (screenH > 0f) screenH else resources.displayMetrics.heightPixels.toFloat()
        return rawY >= screenHeight * 0.56f
    }

    private fun activeMotion() = motions[selectedCharm]

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        screenW = width.toFloat()
        screenH = height.toFloat()
        if (screenW <= 0f || screenH <= 0f || !dangleVisible) return

        val now = SystemClock.uptimeMillis()
        val elapsed = if (paused) 0f else ((now - lastFrameTime).coerceIn(1L, 32L)) / 1000f
        lastFrameTime = now

        val state = activeMotion()
        val profile = personalities[selectedCharm]
        val bitmap = charms[selectedCharm]
        if (!paused && !state.pulling) stepPhysics(state, elapsed, now, profile)

        val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val sizeScale = sizeWidthsDp[sizeIndex] / 92f
        val maxCharmHeight = min(screenH * 0.34f, dp(270f) * sizeScale)
        val maxCharmWidth = min(dp(sizeWidthsDp[sizeIndex]), screenW * 0.31f)
        val charmWidth = min(maxCharmWidth, maxCharmHeight * aspect)
        val charmHeight = charmWidth / aspect

        val anchorX = if (anchorXOverride.isNaN()) screenW * 0.5f else anchorXOverride.coerceIn(dp(24f), screenW - dp(24f))
        val leftClearance = max(dp(10f), anchorX - charmWidth * 0.5f - dp(12f))
        val rightClearance = max(dp(10f), screenW - anchorX - charmWidth * 0.5f - dp(12f))
        val leftAngleLimit = asin((leftClearance / max(state.ropeLength, 1f)).coerceIn(0f, 0.96f))
        val rightAngleLimit = asin((rightClearance / max(state.ropeLength, 1f)).coerceIn(0f, 0.96f))
        val visibleAngle = state.angle.coerceIn(-leftAngleLimit, rightAngleLimit)
        val pivotX = anchorX + sin(visibleAngle) * state.ropeLength
        val pivotY = anchorY + cos(visibleAngle) * state.ropeLength

        updateRopeNodes(state, anchorX, anchorY, pivotX, pivotY, elapsed)
        drawCord(canvas, anchorX, anchorY, pivotX, pivotY, state, profile, now)
        drawTopHook(canvas, anchorX, anchorY)
        drawCharm(canvas, bitmap, pivotX, pivotY, charmWidth, charmHeight, visibleAngle, profile, now)
        drawPersonalityEffect(canvas, pivotX, pivotY, charmWidth, charmHeight, state, now)

        if (state.pulling && isInBottomDropZone(state.pointerY)) drawBottomDropHint(canvas)
        onCharmPosition?.invoke(pivotX, pivotY + charmHeight * 0.53f)

        // When paused, render a still frame and stop the animation callback loop.
        if (!paused) postInvalidateOnAnimation()
    }

    /** Stable damped spring + pendulum integration using short substeps. */
    private fun stepPhysics(state: MotionState, elapsed: Float, now: Long, profile: CharmPersonality) {
        val steps = max(1, ceil(elapsed / 0.008f).toInt())
        val dt = elapsed / steps
        repeat(steps) {
            val extension = state.ropeLength - state.ropeRest
            val radialAcceleration = -profile.springStiffness * extension - profile.springDamping * state.radialVelocity
            state.radialVelocity += radialAcceleration * dt
            state.ropeLength += state.radialVelocity * dt

            if (state.ropeLength < state.ropeRest) {
                state.ropeLength = state.ropeRest
                if (state.radialVelocity < 0f) state.radialVelocity *= -0.24f
            }
            state.ropeLength = state.ropeLength.coerceAtMost(state.ropeRest + dp(620f))

            val lengthDp = max(65f, state.ropeLength / density)
            // Phone tilt is filtered twice: sensor noise first, then a slow physical follow response.
            // This makes the charm drift with the handset instead of snapping to sensor readings.
            val desiredTilt = if (phoneMotionEnabled) targetPhoneTilt else 0f
            val follow = (1f - exp((-dt * 2.15f).toDouble()).toFloat()).coerceIn(0f, 1f)
            smoothedPhoneTilt += (desiredTilt - smoothedPhoneTilt) * follow
            val breeze = sin(now / profile.swayPeriodMs).toFloat() * profile.idleForce
            val relativeAngle = state.angle - smoothedPhoneTilt
            val angularAcceleration = -(profile.gravity / lengthDp) * sin(relativeAngle) -
                (profile.angularDamping + abs(state.radialVelocity) / max(1f, state.ropeRest) * 0.055f) * state.angularVelocity + breeze
            state.angularVelocity += angularAcceleration * dt
            state.angle += state.angularVelocity * dt

            val baseAnchorX = if (anchorXOverride.isNaN()) screenW * 0.5f else anchorXOverride
            val leftClearance = max(dp(8f), baseAnchorX - dp(70f))
            val rightClearance = max(dp(8f), screenW - baseAnchorX - dp(70f))
            val leftLimit = asin((leftClearance / max(state.ropeLength, 1f)).coerceIn(0f, 0.96f))
            val rightLimit = asin((rightClearance / max(state.ropeLength, 1f)).coerceIn(0f, 0.96f))
            if (state.angle > rightLimit) {
                state.angle = rightLimit
                if (state.angularVelocity > 0f) state.angularVelocity *= -0.26f
            } else if (state.angle < -leftLimit) {
                state.angle = -leftLimit
                if (state.angularVelocity < 0f) state.angularVelocity *= -0.26f
            }

            if (abs(state.angularVelocity) < 0.006f && abs(state.angle) < 0.01f &&
                abs(state.ropeLength - state.ropeRest) < dp(0.8f)) {
                state.angle *= 0.985f
                state.angularVelocity *= 0.94f
                if (abs(state.radialVelocity) < dp(0.8f)) state.radialVelocity = 0f
            }
        }
    }

    /**
     * Verlet rope simulation: eight short segments with damped inertia and
     * repeated length constraints. This gives the cord its own gentle lag and
     * curve while the pendant remains attached to the final node.
     */
    private fun updateRopeNodes(
        state: MotionState, ax: Float, ay: Float, ex: Float, ey: Float, elapsed: Float
    ) {
        val segmentCount = 8
        if (state.ropeNodes.size != segmentCount + 1) {
            state.ropeNodes.clear()
            for (i in 0..segmentCount) {
                val t = i / segmentCount.toFloat()
                val x = ax + (ex - ax) * t
                val y = ay + (ey - ay) * t
                state.ropeNodes.add(RopeNode(x, y, x, y))
            }
        }

        val nodes = state.ropeNodes
        val last = nodes.lastIndex
        nodes[0].x = ax
        nodes[0].y = ay
        nodes[last].x = ex
        nodes[last].y = ey

        val dt = elapsed.coerceIn(0.001f, 0.032f)
        val dtSquared = dt * dt
        val gravity = dp(if (state.pulling) 260f else 690f)
        val breeze = if (state.pulling) 0f else sin(SystemClock.uptimeMillis() / 720.0).toFloat() * dp(12f)

        for (i in 1 until last) {
            val node = nodes[i]
            val vx = (node.x - node.oldX) * 0.985f
            val vy = (node.y - node.oldY) * 0.985f
            node.oldX = node.x
            node.oldY = node.y
            node.x += vx + breeze * dtSquared
            node.y += vy + gravity * dtSquared
        }

        val dx = ex - ax
        val dy = ey - ay
        val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        // A few percent of slack gives a visible, natural catenary curve.
        val targetLength = (distance / segmentCount) * (if (state.pulling) 1.002f else 1.035f)

        repeat(7) {
            nodes[0].x = ax
            nodes[0].y = ay
            nodes[last].x = ex
            nodes[last].y = ey
            for (i in 0 until last) {
                val a = nodes[i]
                val b = nodes[i + 1]
                val sx = b.x - a.x
                val sy = b.y - a.y
                val length = sqrt(sx * sx + sy * sy).coerceAtLeast(0.001f)
                val correction = (length - targetLength) / length
                val cx = sx * correction
                val cy = sy * correction
                when {
                    i == 0 -> { b.x -= cx; b.y -= cy }
                    i + 1 == last -> { a.x += cx; a.y += cy }
                    else -> {
                        a.x += cx * 0.5f; a.y += cy * 0.5f
                        b.x -= cx * 0.5f; b.y -= cy * 0.5f
                    }
                }
            }
        }
        nodes[0].x = ax
        nodes[0].y = ay
        nodes[last].x = ex
        nodes[last].y = ey
    }

    private fun drawCord(
        c: Canvas, ax: Float, ay: Float, px: Float, py: Float,
        state: MotionState, profile: CharmPersonality, now: Long
    ) {
        val tension = ((state.ropeLength - state.ropeRest) / dp(620f)).coerceIn(0f, 1f)
        val cord = Path()
        val nodes = state.ropeNodes
        if (nodes.size >= 2) {
            cord.moveTo(nodes.first().x, nodes.first().y)
            for (i in 1 until nodes.lastIndex) {
                val current = nodes[i]
                val next = nodes[i + 1]
                val midX = (current.x + next.x) * 0.5f
                val midY = (current.y + next.y) * 0.5f
                cord.quadTo(current.x, current.y, midX, midY)
            }
            cord.lineTo(nodes.last().x, nodes.last().y)
        } else {
            cord.moveTo(ax, ay)
            cord.lineTo(px, py)
        }

        // A continuous, round satin-gold filament: dark edge, warm body, fine centre sheen.
        // No bitmap tiling, diagonal bands, knots or spiral-like end caps.
        c.drawPath(cord, cordShadowPaint)
        c.drawPath(cord, cordPaint)
        c.drawPath(cord, cordCenterPaint)

        // Offset highlights follow the mostly vertical cord and give it a cylindrical profile.
        val highlight = Path()
        cord.offset(-dp(1.05f), 0f, highlight)
        c.drawPath(highlight, cordHighlightPaint)
        val lowlight = Path()
        cord.offset(dp(1.2f), 0f, lowlight)
        c.drawPath(lowlight, cordLowlightPaint)

        if (state.pulling && state.pullDistance > dp(3f)) {
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = dp(1.0f + tension * 0.9f)
            val base = profile.glowColor
            paint.color = Color.argb(55 + (85 * tension).toInt(), Color.red(base), Color.green(base), Color.blue(base))
            c.drawPath(cord, paint)
        }

        // The cord ends under the charm's existing top eyelet/bead stack. No loose end dot.
    }

    /**
     * Draw the front-facing hook so its lower eyelet meets the cord's first point.
     * The service positions anchorY at the bottom of this image, with its top
     * aligned to the bottom edge of the persistent floating handle.
     */
    private fun drawTopHook(canvas: Canvas, anchorX: Float, cordStartY: Float) {
        val hookHeight = dp(hookHeightDp)
        val hookWidth = hookHeight * hookBitmap.width.toFloat() / hookBitmap.height.toFloat()
        val dst = RectF(
            anchorX - hookWidth * 0.5f,
            cordStartY - hookHeight,
            anchorX + hookWidth * 0.5f,
            cordStartY
        )
        canvas.drawBitmap(hookBitmap, null, dst, hookPaint)
    }

    private fun drawCharm(
        c: Canvas, bitmap: Bitmap, pivotX: Float, pivotY: Float,
        targetWidth: Float, targetHeight: Float, swingAngle: Float,
        profile: CharmPersonality, now: Long
    ) {
        val dst = RectF(
            pivotX - targetWidth * 0.5f,
            pivotY,
            pivotX + targetWidth * 0.5f,
            pivotY + targetHeight
        )
        val speedFactor = 0.18f + min(1f, abs(activeMotion().angularVelocity) / 4f)
        val idleWobble = sin(now / profile.swayPeriodMs * 1.6).toFloat() * profile.idleTiltDegrees * speedFactor
        c.save()
        val bodyRotation = Math.toDegrees(swingAngle.toDouble()).toFloat() * profile.bodyRotation + idleWobble
        c.rotate(bodyRotation, pivotX, pivotY + targetHeight * 0.14f)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.isFilterBitmap = true
        c.drawBitmap(bitmap, null, dst, paint)
        c.restore()
    }

    /** Subtle movement-linked highlights make each ornament feel different. */
    private fun drawPersonalityEffect(
        c: Canvas, x: Float, y: Float, w: Float, h: Float,
        state: MotionState, now: Long
    ) {
        val phase = now / personalities[selectedCharm].swayPeriodMs
        val energy = min(1f, abs(state.angularVelocity) / 4.5f)
        val alpha = (20 + energy * 75).toInt().coerceIn(20, 95)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        when (selectedCharm) {
            0 -> {
                // Evil Eye: a cool, slow halo glides around the glass pendant.
                paint.strokeWidth = dp(1.1f)
                paint.color = Color.argb(alpha, 76, 175, 255)
                val halo = RectF(x - w * 0.53f, y + h * 0.32f, x + w * 0.53f, y + h * 0.88f)
                c.drawArc(halo, (phase * 50.0).toFloat(), 92f, false, paint)
            }
            1 -> {
                // Chili & Lemon: a few tiny green leaf glints follow its wider sway.
                paint.style = Paint.Style.FILL
                for (i in 0..2) {
                    val t = phase + i * 2.1
                    val px = x + sin(t).toFloat() * w * 0.57f
                    val py = y + h * (0.42f + i * 0.12f)
                    paint.color = Color.argb(alpha, 112, 180, 74)
                    c.drawOval(RectF(px - dp(2f), py - dp(4f), px + dp(2f), py + dp(4f)), paint)
                }
            }
            2 -> {
                // Daruma: warm gold sparkles pulse at the edges as it settles.
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(1.1f)
                paint.color = Color.argb(alpha, 255, 205, 110)
                for (side in -1..1 step 2) {
                    val sx = x + side * w * 0.55f
                    val sy = y + h * (0.36f + (sin(phase * 1.8).toFloat() * 0.03f))
                    c.drawLine(sx - dp(3f), sy, sx + dp(3f), sy, paint)
                    c.drawLine(sx, sy - dp(3f), sx, sy + dp(3f), paint)
                }
            }
            3 -> {
                // Lucky Cat: a bell glint flashes briefly on the forward part of its swing.
                val pulse = (sin(now / 180.0).toFloat() * 0.5f + 0.5f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(1f)
                paint.color = Color.argb((alpha * pulse).toInt().coerceIn(0, 100), 255, 220, 125)
                val gx = x + w * 0.12f
                val gy = y + h * 0.52f
                val r = dp(2.2f + pulse * 1.6f)
                c.drawLine(gx - r, gy, gx + r, gy, paint)
                c.drawLine(gx, gy - r, gx, gy + r, paint)
            }
            else -> {
                // Oni Mask: slow ember-like red/orange particles around the lower edge.
                paint.style = Paint.Style.FILL
                for (i in 0..3) {
                    val t = phase * 0.7 + i * 1.57
                    val px = x + cos(t).toFloat() * w * 0.56f
                    val py = y + h * (0.72f + sin(t).toFloat() * 0.15f)
                    paint.color = Color.argb(alpha, 255, 105, 65)
                    c.drawCircle(px, py, dp(1.2f + energy), paint)
                }
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawBottomDropHint(c: Canvas) {
        val hintCenterX = if (anchorXOverride.isNaN()) screenW * 0.5f else anchorXOverride
        val panel = RectF(
            (hintCenterX - dp(112f)).coerceAtLeast(dp(8f)),
            screenH * 0.70f,
            (hintCenterX + dp(112f)).coerceAtMost(screenW - dp(8f)),
            screenH * 0.70f + dp(58f)
        )
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(220, 25, 28, 39)
        c.drawRoundRect(panel, dp(18f), dp(18f), paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = dp(14f)
        paint.isFakeBoldText = true
        c.drawText("Release to close", hintCenterX, screenH * 0.70f + dp(36f), paint)
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.LEFT
    }

    private fun dp(value: Float): Float = value * density
}
