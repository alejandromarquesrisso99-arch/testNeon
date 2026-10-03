package com.district9.neonsteps.ui.scene

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.district9.neonsteps.ui.Neon
import kotlin.math.sin

/**
 * Flying cars on three skyway lanes at different depths. Far lanes are just lights and trails;
 * near cars show a body, an underglow and headlight cones cutting through the rain. How busy
 * the lanes are follows the hour ([density], from [DayCycle]).
 */
internal class AirTraffic(private val frame: SceneFrame, private val sprites: Sprites, private val panMargin: Float) {

    private class Lane(
        val yTopRef: Float,
        val yBottomRef: Float,
        val size: Float,
        val minSpeed: Float,
        val maxSpeed: Float,
        val parallax: Float,
        val gap: Float, // seconds between cars at full density
    ) {
        val capacity = 8
        val active = BooleanArray(capacity)
        val x = FloatArray(capacity)
        val y = FloatArray(capacity)
        val dir = FloatArray(capacity)
        val speed = FloatArray(capacity)
        val glow = IntArray(capacity)
        val phase = FloatArray(capacity)
        var nextSpawn = 0f
    }

    private val lanes = listOf(
        Lane(330f, 470f, 0.55f, 50f, 85f, 0.2f, 1.1f),
        Lane(520f, 640f, 0.78f, 110f, 170f, 0.5f, 1.7f),
        Lane(700f, 860f, 1.1f, 210f, 310f, 0.85f, 2.6f),
    )

    private val glowColors = intArrayOf(Neon.CYAN, Neon.MAGENTA, Neon.VIOLET, 0xFFFFB347.toInt())
    private val rng = Rng(808)
    private var time = 0f

    /** 0 empty skies … 1 rush hour. */
    var density = 0.6f

    /** Told when a car on the near lane sets off, for a fly-by sound. */
    var onNearPass: (() -> Unit)? = null

    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0D0618.toInt() }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = frame.s(1.2f)
    }
    private val glass = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val beam = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cone = Path()
    private val rect = RectF()

    init {
        // Start with some traffic already in the air.
        for (lane in lanes) {
            repeat(3) { spawn(lane, rng.range(0f, frame.width.toFloat())) }
            lane.nextSpawn = rng.range(0f, lane.gap)
        }
    }

    private fun spawn(lane: Lane, atX: Float? = null) {
        val i = lane.active.indexOfFirst { !it }
        if (i < 0) return
        val d = if (rng.chance(0.5f)) 1f else -1f
        val pad = frame.s(120f) * lane.size + panMargin * lane.parallax
        lane.active[i] = true
        lane.dir[i] = d
        lane.x[i] = atX ?: if (d > 0f) -pad else frame.width + pad
        lane.y[i] = frame.y(rng.range(lane.yTopRef, lane.yBottomRef))
        lane.speed[i] = frame.s(rng.range(lane.minSpeed, lane.maxSpeed))
        lane.glow[i] = glowColors[rng.int(0, glowColors.size)]
        lane.phase[i] = rng.next() * 6f
        if (lane === lanes[2] && atX == null) onNearPass?.invoke()
    }

    fun update(dt: Float) {
        time += dt
        for (lane in lanes) {
            val pad = frame.s(140f) * lane.size + panMargin * lane.parallax
            for (i in 0 until lane.capacity) {
                if (!lane.active[i]) continue
                lane.x[i] += lane.dir[i] * lane.speed[i] * dt
                if (lane.x[i] < -pad - frame.s(10f) || lane.x[i] > frame.width + pad + frame.s(10f)) lane.active[i] = false
            }
            if (density > 0.02f && time >= lane.nextSpawn) {
                spawn(lane)
                // Busier hours mean shorter gaps; a little randomness keeps it from looking scheduled.
                lane.nextSpawn = time + lane.gap / density.coerceAtLeast(0.08f) * rng.range(0.5f, 1.5f)
            }
        }
    }

    /** Draws one lane (0 far, 1 mid, 2 near) with the camera offset [camera]. */
    fun drawLane(canvas: Canvas, index: Int, camera: Float) {
        val lane = lanes[index]
        val dx = camera * lane.parallax
        val s = lane.size * frame.s(1f)
        for (i in 0 until lane.capacity) {
            if (!lane.active[i]) continue
            val x = lane.x[i] + dx
            val y = lane.y[i] + sin(time * 1.6f + lane.phase[i]) * 2.5f * s
            val d = lane.dir[i]
            val noseX = x + d * 24f * s
            val tailX = x - d * 24f * s

            // Light trails behind: taillight red over the underglow colour, fading out.
            for (k in 0 until 3) {
                val len = (60f + k * 40f) * s
                trail.strokeWidth = (3.2f - k) * s
                trail.color = Neon.alpha(if (k == 0) 0xFFFF3B3B.toInt() else lane.glow[i], 0.35f / (k + 1))
                canvas.drawLine(tailX, y + k * 1.5f * s, tailX - d * len, y + k * 1.5f * s, trail)
            }

            if (index == 0) {
                // Far away it's just a pair of lights and a hint of underglow.
                sprites.drawBlob(canvas, x, y + 3f * s, 26f * s, 9f * s, lane.glow[i], 0.35f)
                sprites.drawBlob(canvas, noseX, y, 14f * s, 10f * s, 0xFFFFF4D6.toInt(), 0.95f)
                sprites.drawBlob(canvas, tailX, y, 11f * s, 8f * s, 0xFFFF3B3B.toInt(), 0.85f)
                continue
            }

            // Headlight cone through the rain.
            cone.reset()
            cone.moveTo(noseX, y - 2f * s)
            cone.lineTo(noseX + d * 150f * s, y - 26f * s)
            cone.lineTo(noseX + d * 150f * s, y + 30f * s)
            cone.close()
            beam.color = Neon.alpha(0xFFFFF1D0.toInt(), 0.09f)
            canvas.drawPath(cone, beam)

            // Underglow, body, canopy.
            sprites.drawBlob(canvas, x, y + 9f * s, 36f * s, 12f * s, lane.glow[i], 0.5f)
            rect.set(x - 25f * s, y - 6f * s, x + 25f * s, y + 6f * s)
            canvas.drawRoundRect(rect, 6f * s, 6f * s, body)
            rim.color = Neon.alpha(lane.glow[i], 0.7f)
            canvas.drawRoundRect(rect, 6f * s, 6f * s, rim)
            rect.set(x - 4f * s + d * 4f * s, y - 12f * s, x + 12f * s + d * 4f * s, y - 4f * s)
            glass.color = Neon.alpha(0xFF6BE8FF.toInt(), 0.55f)
            canvas.drawRoundRect(rect, 5f * s, 5f * s, glass)

            sprites.drawBlob(canvas, noseX, y, 10f * s, 7f * s, 0xFFFFF4D6.toInt(), 0.95f)
            sprites.drawBlob(canvas, tailX, y, 8f * s, 6f * s, 0xFFFF3B3B.toInt(), 0.85f)
            if (index == 2 && (time * 1.3f + lane.phase[i]).toInt() % 2 == 0) {
                sprites.drawBlob(canvas, x, y - 9f * s, 6f * s, 6f * s, 0xFFFF3B3B.toInt(), 0.8f)
            }
        }
    }
}
