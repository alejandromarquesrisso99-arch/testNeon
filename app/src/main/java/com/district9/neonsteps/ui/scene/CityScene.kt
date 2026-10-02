package com.district9.neonsteps.ui.scene

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.district9.neonsteps.ui.Neon
import com.district9.neonsteps.ui.NeonFonts

/** The whole District 9 street at night, laid out for one view size. */
internal class CityScene(fonts: NeonFonts, width: Int, height: Int, horizon: Float) {
    val frame = SceneFrame(width, height, horizon)
    private val sprites = Sprites()

    /** Furthest the drag can take the near layer (rubber band included), plus tilt. */
    val panRange = frame.s(200f)
    private val tiltRange = frame.s(55f)
    private val maxShift = panRange * 1.15f + tiltRange
    private val frontMargin = maxShift * FRONT_PARALLAX + frame.s(40f)

    private val skyline = Skyline(frame, maxShift, fonts.bold)
    private val hologram = Hologram(frame, fonts.mono, skyline.projectorX, skyline.projectorY, sprites)
    private val market = Market(frame, sprites, frontMargin)
    private val pedestrians = Pedestrians(frame, sprites, maxShift * FRONT_PARALLAX)
    private val street = WetStreet(frame, frontMargin)
    private val rain = Rain(frame)
    private val lightning = Lightning(frame)
    private val fireworks = Fireworks(frame, sprites)
    private val signSparks = Sparks(frame)

    /** The HOTEL sign whose "L" never lights, unless someone fixes it. */
    private val hotel = NeonSign.word(
        frame, "HOTEL", 0xFF3FD0E0.toInt(), fonts.bold, 92f, 1063f, 333f, 1155f,
        SignTemper(baseLevel = 0.6f, eventGap = 6f..16f, darkGlyphs = setOf(4)), seed = 31,
    )

    private val signs: List<NeonSign> = listOf(
        NeonSign.vertical(
            frame, "チカムスサロ", 0xFF9B5CFF.toInt(), 792f, 412f, 72f,
            SignTemper(baseLevel = 0.3f, eventGap = 2f..6f, glyphDropGap = 3f..9f), seed = 11,
        ),
        NeonSign.vertical(
            frame, "コリホモンエ", Neon.CYAN, 335f, 665f, 73f,
            SignTemper(eventGap = 5f..14f, deadGlyphs = setOf(3)), seed = 23,
        ),
        hotel,
        NeonSign.vertical(
            frame, "ステップ", Neon.YELLOW, 817f, 866f, 73f,
            SignTemper(eventGap = 6f..15f, glyphDropGap = 9f..24f), seed = 47,
        ),
        NeonSign.word(
            frame, "RAMEN", Neon.MAGENTA, fonts.bold, 590f, 1032f, 852f, 1123f,
            SignTemper(eventGap = 3f..9f), seed = 53,
        ),
        NeonSign.vertical(
            frame, "ラメン", Neon.MAGENTA, 1064f, 700f, 72f,
            SignTemper(eventGap = 4f..10f), seed = 67,
        ),
    )

    private val skyPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, frame.horizon,
            intArrayOf(0xFF07010F.toInt(), 0xFF140428.toInt(), 0xFF2A0A47.toInt(), 0xFF46104F.toInt()),
            floatArrayOf(0f, 0.35f, 0.75f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val smogX = floatArrayOf(0.1f, 0.42f, 0.8f, 0.25f, 0.65f)
    private val smogY = floatArrayOf(520f, 380f, 600f, 860f, 820f)
    private val smogR = floatArrayOf(420f, 520f, 460f, 560f, 500f)
    private val smogC = intArrayOf(0xFF5A1060.toInt(), 0xFF2B0B5A.toInt(), 0xFF6A1468.toInt(), 0xFF3A0C5E.toInt(), 0xFF5C1366.toInt())
    private val smogSpeed = floatArrayOf(6f, -4f, 3f, -5f, 4f)

    private val layerDx = FloatArray(4)
    private var camera = 0f

    /** Camera pan from the user's drag, in px at the near layer. */
    var pan = 0f
    private var tilt = 0f
    private var tiltTarget = 0f
    private var hasTilt = false
    private var time = 0f
    private var rebakeAt = 0f

    /** 0 standing still … 1 brisk walk. Feeds the koi. */
    var activity = 0f

    /** Goal met today: Tower 61 lights up and fireworks fill the sky until midnight. */
    var celebrating = false
        set(value) {
            field = value
            fireworks.active = value
        }
    private var towerLevel = 0f
    private var secondStrikeAt = -1f

    private var rainTarget = 0.7f

    fun setRainIntensity(value: Float) {
        rainTarget = value.coerceIn(0f, 1f)
    }

    /** Device tilt in -1..1; without it the camera drifts slowly by itself. */
    fun setTilt(x: Float) {
        hasTilt = true
        tiltTarget = x.coerceIn(-1f, 1f)
    }

    fun strike(strength: Float) = lightning.strike(strength)

    // --- Easter eggs -------------------------------------------------------------------------

    enum class Tap { NONE, CONSUMED, HOTEL_FIXED, GOLDEN_KOI }

    private var hotelTaps = 0
    private var lastHotelTap = -10f
    private var koiTaps = 0
    private var lastKoiTap = -10f
    private var blackoutT = -1f
    private val hitRect = android.graphics.RectF()

    /** Whether the HOTEL's "L" is fixed (it is, for the rest of the day, once someone fixes it). */
    var hotelFixed: Boolean
        get() = hotel.isRepaired
        set(value) {
            if (value && !hotel.isRepaired) hotel.repair(time, animate = false)
            if (!value && hotel.isRepaired) hotel.unrepair()
        }

    val blackoutRunning: Boolean get() = blackoutT >= 0f

    /** Lights out across District 9; only the rain, the umbrellas and your steps keep glowing. */
    fun blackout() {
        if (blackoutT < 0f) blackoutT = 0f
    }

    /** Screen hit boxes of the HOTEL's "L" and the koi, for tests. */
    fun debugTargets(): Pair<android.graphics.RectF, android.graphics.RectF>? {
        val l = hotel.darkElementBounds() ?: return null
        return android.graphics.RectF(l).apply { offset(layerDx[3], 0f) } to android.graphics.RectF(hologram.koiRect)
    }

    /** Taps on the koi or the HOTEL's dead "L" feed their easter eggs instead of calling lightning. */
    fun tap(x: Float, y: Float): Tap {
        val t = time
        if (hologram.koiRect.width() > 0f) {
            hitRect.set(hologram.koiRect)
            hitRect.inset(-frame.s(30f), -frame.s(30f))
            if (hitRect.contains(x, y)) {
                hologram.poke(t)
                if (hologram.isLoose(t)) return Tap.CONSUMED
                koiTaps = if (t - lastKoiTap > TAP_WINDOW) 1 else koiTaps + 1
                lastKoiTap = t
                if (koiTaps >= 3) {
                    koiTaps = 0
                    hologram.release(t, GOLDEN_KOI_SECONDS)
                    return Tap.GOLDEN_KOI
                }
                return Tap.CONSUMED
            }
        }
        val l = hotel.darkElementBounds() ?: return Tap.NONE
        if (hotel.isRepaired) return Tap.NONE
        val near = layerDx[3]
        hitRect.set(l.left + near, l.top, l.right + near, l.bottom)
        hitRect.inset(-frame.s(34f), -frame.s(34f))
        if (!hitRect.contains(x, y)) return Tap.NONE
        hotelTaps = if (t - lastHotelTap > TAP_WINDOW) 1 else hotelTaps + 1
        lastHotelTap = t
        // Each tap shorts the tube: it spits sparks and fails to strike...
        hotel.poke(t)
        signSparks.emit(l.centerX(), l.top + frame.s(6f), 5 + hotelTaps * 2, 0xFF9FF6FF.toInt(), frame.s(260f), frame.s(1100f), 0.6f)
        if (hotelTaps < 5) return Tap.CONSUMED
        // ...until the fifth, when it catches with a shower of sparks onto the street.
        hotelTaps = 0
        hotel.repair(t, animate = true)
        signSparks.emit(l.centerX(), l.top, 70, 0xFFBFF8FF.toInt(), frame.s(460f), frame.s(1200f), 1.1f)
        signSparks.emit(l.centerX(), l.bottom, 30, Neon.YELLOW, frame.s(300f), frame.s(1200f), 1f, up = false)
        return Tap.HOTEL_FIXED
    }

    /** The moment the goal falls: a double lightning strike and an opening salvo. */
    fun celebrate() {
        celebrating = true
        lightning.strike(1f)
        secondStrikeAt = time + 0.9f
        fireworks.salvo()
    }

    init {
        bakeReflection()
    }

    private fun bakeReflection() {
        street.bake { c ->
            c.drawRect(-frontMargin, 0f, frame.width + frontMargin, frame.horizon, skyPaint)
            for (i in 0 until 4) skyline.drawLayer(c, i, 0f)
            market.drawStalls(c, 0f, 0f)
        }
    }

    fun update(dt: Float) {
        time += dt
        val t = time
        if (!hasTilt) tiltTarget = 0.45f * wave(t, 70f)
        tilt += (tiltTarget - tilt) * (dt * 2.5f).coerceAtMost(1f)
        // Near layers slide further than far ones: that difference is the depth.
        camera = pan + tilt * tiltRange
        for (i in 0 until 4) layerDx[i] = camera * skyline.layers[i].parallax

        rain.intensity += (rainTarget - rain.intensity) * (dt * 0.8f).coerceAtMost(1f)
        if (blackoutT >= 0f) {
            blackoutT += dt
            if (blackoutT > Blackout.TOTAL) blackoutT = -1f
        }
        skyline.update(t)
        for ((i, s) in signs.withIndex()) {
            // Signs restart in a staggered order after a blackout.
            s.powerScale = Blackout.power(blackoutT, 0.15f + 0.55f * i / signs.size)
            s.update(t)
        }
        signSparks.update(dt)
        hologram.update(t, dt, activity)
        hologram.updateHitBox(layerDx[2])
        market.update(dt)
        pedestrians.update(dt, frame.width)
        rain.update(dt)
        lightning.update(dt)
        fireworks.update(dt)
        if (secondStrikeAt in 0f..t) {
            lightning.strike(0.8f)
            secondStrikeAt = -1f
        }
        // Tower 61 powers on floor by floor, and dims faster if the goal is raised past you.
        towerLevel = if (celebrating) (towerLevel + dt * 0.55f).coerceAtMost(1f) else (towerLevel - dt * 1.5f).coerceAtLeast(0f)

        if (t >= rebakeAt) {
            // Windows switch over time; refresh their blurred reflection now and then.
            if (rebakeAt > 0f) bakeReflection()
            rebakeAt = t + 12f
        }
    }

    fun draw(canvas: Canvas) {
        val t = time
        val w = frame.width.toFloat()
        val near = layerDx[3]
        val front = camera * FRONT_PARALLAX

        canvas.drawRect(0f, 0f, w, frame.horizon, skyPaint)
        for (i in smogX.indices) {
            val span = w + frame.s(600f)
            val x = ((smogX[i] * w + smogSpeed[i] * frame.s(1f) * t + camera * 0.05f) % span + span) % span - frame.s(300f)
            sprites.drawBlob(canvas, x, frame.y(smogY[i]), frame.s(smogR[i]), frame.s(smogR[i] * 0.45f), smogC[i], 0.42f)
        }
        // The city's own glow on the smog goes out with the grid.
        val cityGlow = Blackout.power(blackoutT, 0.4f)
        sprites.drawBlob(canvas, w / 2f, frame.y(1180f), w * 0.85f, frame.s(300f), 0xFF8A1A70.toInt(), 0.32f * (0.35f + 0.65f * cityGlow))
        if (fireworks.hasSomethingToDraw) fireworks.draw(canvas, camera * 0.06f)
        lightning.drawSky(canvas)

        val b = blackoutT
        val late = Blackout.power(b, 0.95f) // Tower 61 and its projector come back last
        skyline.drawLayer(canvas, 0, layerDx[0], b)
        skyline.drawLayer(canvas, 1, layerDx[1], b)
        skyline.drawLayer(canvas, 2, layerDx[2], b)
        skyline.drawTowerLights(canvas, sprites, t, layerDx[2], towerLevel * late)
        skyline.drawTowerLabel(canvas, t, layerDx[2], towerLevel, late)
        hologram.drawProjection(canvas, t, layerDx[2], late)
        // In its beam the koi swims behind the street front; loose and golden, it flies over it.
        val koiInFront = hologram.freedom > 0.5f
        if (!koiInFront) hologram.drawKoi(canvas, t, layerDx[2], late)
        skyline.drawLayer(canvas, 3, near, b)
        skyline.drawBeacons(canvas, sprites, t, layerDx, Blackout.power(b, 0.85f))

        for (s in signs) s.drawWash(canvas, sprites, near)
        for (s in signs) s.draw(canvas, near)
        signSparks.draw(canvas, near)
        if (koiInFront) hologram.drawKoi(canvas, t, layerDx[2], late)

        street.drawGround(canvas)
        street.drawReflection(canvas, t, front, signs, near, Blackout.power(b, 0.5f)) { c ->
            market.drawBulbs(c, t, front, 0.5f, b)
            pedestrians.draw(c, front, 0.28f)
        }
        street.drawKerb(canvas)
        market.drawStalls(canvas, t, front, b)
        market.drawSteam(canvas, front)
        pedestrians.draw(canvas, front)

        rain.drawSplashes(canvas)
        rain.drawDrops(canvas)
        lightning.drawOverlay(canvas)
    }

    private companion object {
        /** The market and pedestrians are in front of the near towers, so they move further still. */
        const val FRONT_PARALLAX = 1.2f

        /** Max seconds between taps that still count toward an easter egg. */
        const val TAP_WINDOW = 1.6f
        const val GOLDEN_KOI_SECONDS = 60f
    }
}
