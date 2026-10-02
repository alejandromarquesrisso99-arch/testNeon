package com.district9.neonsteps

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.Toast
import com.district9.neonsteps.audio.AmbientSound
import com.district9.neonsteps.data.RainMode
import com.district9.neonsteps.data.SensorMode
import com.district9.neonsteps.data.StepRepository
import com.district9.neonsteps.data.StreakPerks
import com.district9.neonsteps.service.StepCounterService
import com.district9.neonsteps.ui.Neon
import com.district9.neonsteps.ui.hud.HeaderView
import com.district9.neonsteps.ui.hud.HistoryView
import com.district9.neonsteps.ui.hud.HudPanelView
import com.district9.neonsteps.ui.hud.NeonButtonView
import com.district9.neonsteps.ui.hud.ProfileView
import com.district9.neonsteps.ui.hud.TickerItem
import com.district9.neonsteps.ui.hud.TickerView
import com.district9.neonsteps.ui.scene.EasterEgg
import com.district9.neonsteps.ui.scene.SceneView
import com.district9.neonsteps.util.Format
import com.district9.neonsteps.widget.StepsWidget
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : Activity(), StepRepository.Listener, SensorEventListener {

    private lateinit var repo: StepRepository
    private lateinit var scene: SceneView
    private lateinit var header: HeaderView
    private lateinit var hud: HudPanelView
    private lateinit var goalButton: NeonButtonView
    private lateinit var historyButton: NeonButtonView
    private lateinit var rainButton: NeonButtonView
    private lateinit var soundButton: NeonButtonView
    private val ambient = AmbientSound()
    private lateinit var ticker: TickerView
    private lateinit var history: HistoryView
    private lateinit var profile: ProfileView
    private var sensorManager: SensorManager? = null

    private val handler = Handler(Looper.getMainLooper())
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss")
    private var lastSteps = -1
    private var backCallback: Any? = null
    private val titleBand = RectF()

    private val tick = object : Runnable {
        override fun run() {
            updateUi()
            handler.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goEdgeToEdge()
        setContentView(R.layout.activity_main)
        repo = StepRepository.get(this)
        sensorManager = getSystemService(SensorManager::class.java)

        scene = findViewById(R.id.scene)
        header = findViewById(R.id.header)
        hud = findViewById(R.id.hud)
        goalButton = findViewById(R.id.btn_goal)
        historyButton = findViewById(R.id.btn_history)
        rainButton = findViewById(R.id.btn_rain)
        soundButton = findViewById(R.id.btn_sound)
        ticker = findViewById(R.id.ticker)
        history = findViewById(R.id.history)
        profile = findViewById(R.id.profile)

        goalButton.accent = Neon.CYAN
        historyButton.accent = Neon.YELLOW
        rainButton.accent = Neon.MAGENTA
        soundButton.accent = Neon.VIOLET
        soundButton.setOnClickListener {
            repo.soundOn = !repo.soundOn
            if (repo.soundOn) ambient.start() else ambient.stop()
        }
        scene.setAudio(ambient)

        goalButton.setOnClickListener { cycleGoal() }
        historyButton.setOnClickListener { if (history.isOpen) closeHistory() else openHistory() }
        rainButton.setOnClickListener {
            repo.rainMode = RainMode.entries[(repo.rainMode.ordinal + 1) % RainMode.entries.size]
        }
        header.onPermissionRequest = ::onPermissionTap
        history.onDismiss = ::closeHistory
        hud.setOnClickListener { openProfile() }
        profile.onDismiss = ::closeProfile
        profile.onChange = { height, weight ->
            repo.heightCm = height
            repo.weightKg = weight
        }
        scene.setOnClickListener { scene.strike(0.55f) } // tap the street: thunder on demand
        scene.onEasterEgg = ::onEasterEgg
        ticker.provider = ::headlines

        applyInsets()
        val overlay = findViewById<View>(R.id.overlay)
        header.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            // Long-pressing the step count is the blackout's secret switch.
            header.titleBand(titleBand)
            titleBand.offset((v.left + overlay.left).toFloat(), (v.top + overlay.top).toFloat())
            scene.setBlackoutTrigger(titleBand)
        }
        hud.addOnLayoutChangeListener { v, _, top, _, _, _, _, _, _ ->
            scene.setStreetLimit(top.toFloat())
            // Keep the history modal between the title and the readouts.
            history.setPadding(0, header.bottom, 0, (findViewById<View>(R.id.root).height - v.top).coerceAtLeast(0))
            profile.setPadding(0, header.bottom, 0, (findViewById<View>(R.id.root).height - v.top).coerceAtLeast(0))
        }
        if (savedInstanceState == null) requestMissingPermissions()
    }

    override fun onStart() {
        super.onStart()
        isInForeground = true
        if (repo.soundOn) ambient.start()
        repo.addListener(this)
        if (StepCounterService.hasActivityPermission(this)) StepCounterService.start(this)
        lastSteps = -1
        handler.post(tick)
        val sm = sensorManager
        val gravity = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (gravity != null) sm?.registerListener(this, gravity, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onStop() {
        isInForeground = false
        ambient.stop()
        StepsWidget.refresh(this, force = true) // leave the home screen up to date
        repo.removeListener(this)
        handler.removeCallbacks(tick)
        sensorManager?.unregisterListener(this)
        super.onStop()
    }

    override fun onStepsChanged() = updateUi()

    // Device tilt drives the skyline parallax.
    override fun onSensorChanged(event: SensorEvent) {
        scene.setTilt((-event.values[0] / SensorManager.GRAVITY_EARTH * 2.2f).coerceIn(-1f, 1f))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateUi() {
        val steps = repo.stepsToday()
        val goal = repo.goal
        val percent = Format.percent(steps, goal)
        val cadence = repo.cadence()
        val streak = repo.streak()
        val permitted = StepCounterService.hasActivityPermission(this)
        val state = when {
            !permitted -> HeaderView.State.NEED_PERMISSION
            StepCounterService.isRunning && repo.sensorMode == SensorMode.NONE -> HeaderView.State.NO_SENSOR
            else -> HeaderView.State.OK
        }
        header.setData(steps, goal, state)
        hud.setData(LocalTime.now().format(clockFormat), Format.km(steps, repo.strideMeters), Format.kcal(steps, repo.kcalPerStep), percent)

        goalButton.setText(getString(R.string.btn_goal), Format.steps(goal), getString(R.string.cd_goal_button, Format.steps(goal)))
        if (profile.isOpen) profile.setData(repo.heightCm, repo.weightKg, repo.strideMeters, repo.kcalPerStep)
        if (history.isOpen) {
            historyButton.setText(getString(R.string.btn_history), getString(R.string.btn_history_close), getString(R.string.cd_history_close))
            history.setData(repo.history(7), goal, streak)
        } else {
            historyButton.setText(getString(R.string.btn_history), getString(R.string.btn_history_value), getString(R.string.cd_history_button))
        }
        val rainLabel = getString(
            when (repo.rainMode) {
                RainMode.AUTO -> R.string.rain_auto
                RainMode.DRIZZLE -> R.string.rain_drizzle
                RainMode.RAIN -> R.string.rain_rain
                RainMode.DOWNPOUR -> R.string.rain_downpour
            },
        )
        rainButton.setText(getString(R.string.btn_rain), rainLabel, getString(R.string.cd_rain_button, rainLabel))
        val soundLabel = getString(if (repo.soundOn) R.string.sound_on else R.string.sound_off)
        soundButton.setText(getString(R.string.btn_sound), soundLabel, getString(R.string.cd_sound_button, soundLabel))

        val walk = (cadence / 120f).coerceIn(0f, 1f)
        scene.setActivity(walk)
        scene.setRainIntensity(
            when (repo.rainMode) {
                RainMode.AUTO -> 0.4f + 0.6f * walk
                RainMode.DRIZZLE -> 0.25f
                RainMode.RAIN -> 0.62f
                RainMode.DOWNPOUR -> 1f
            },
        )

        // After 30 days in a row the technician finally fixes the "L" for good; otherwise it's
        // fixed for the day someone fixes it, and dies again at midnight.
        scene.setHotelFixed(repo.hotelFixedToday() || streak >= StreakPerks.HOTEL_FOREVER)
        scene.setStreak(streak)
        header.setStreak(streak)

        // The goal: Tower 61 lit and fireworks until midnight. The celebration plays once a
        // day — live if the app is open, otherwise the first time it's opened afterwards.
        val goalMet = permitted && steps >= goal
        scene.setCelebrating(goalMet)
        if (goalMet && !repo.goalCelebratedToday()) {
            repo.markGoalCelebrated()
            // On opening the app, give the street a beat to appear before the show starts.
            handler.postDelayed({
                scene.celebrate()
                val perk = StreakPerks.unlockedAt(streak)
                ticker.breaking(
                    if (perk != null) {
                        TickerItem("NUEVO EN DISTRICT 9 · ${perk.headline}", highlight = true)
                    } else {
                        TickerItem("META CUMPLIDA · RACHA DE $streak ${if (streak == 1) "DÍA" else "DÍAS"}", highlight = true)
                    },
                )
            }, if (lastSteps < 0) 700L else 0L)
        } else if (lastSteps in 0 until steps && steps / 1000 > lastSteps / 1000) {
            scene.strike(0.6f) // every thousand steps
        }
        lastSteps = steps
    }

    private fun onEasterEgg(egg: EasterEgg) {
        when (egg) {
            EasterEgg.HOTEL_FIXED -> {
                repo.markHotelFixed()
                ticker.breaking(TickerItem("ÚLTIMA HORA · ¡ALGUIEN HA ARREGLADO LA «L» DEL HOTEL!", highlight = true))
            }
            EasterEgg.GOLDEN_KOI ->
                ticker.breaking(TickerItem("ÚLTIMA HORA · AVISTAMIENTO DE UN KOI DORADO SOBRE DISTRICT 9", highlight = true))
            EasterEgg.BLACKOUT ->
                ticker.breaking(TickerItem("ÚLTIMA HORA · APAGÓN EN DISTRICT 9 · SOLO TUS PASOS SIGUEN BRILLANDO", highlight = true))
            EasterEgg.KAGE_BUNSHIN ->
                ticker.breaking(TickerItem("ÚLTIMA HORA · ¡KAGE BUNSHIN NO JUTSU! NINJAS CORRIENDO POR LA ACERA DEL MERCADO", highlight = true))
            EasterEgg.RAMEN_HOLOGRAM ->
                ticker.breaking(TickerItem("ÚLTIMA HORA · LA TORRE 61 PROYECTA RAMEN · ICHIRAKU ABIERTO 24H", highlight = true))
        }
    }

    private fun cycleGoal() {
        val options = StepRepository.GOAL_OPTIONS
        val next = options.firstOrNull { it > repo.goal } ?: options.first()
        repo.goal = next
    }

    private fun openHistory() {
        closeProfile()
        history.setData(repo.history(7), repo.goal, repo.streak())
        history.show()
        syncBackCallback()
        updateUi()
    }

    private fun closeHistory() {
        history.hide()
        syncBackCallback()
        updateUi()
    }

    private fun openProfile() {
        closeHistory()
        profile.setData(repo.heightCm, repo.weightKg, repo.strideMeters, repo.kcalPerStep)
        profile.show()
        syncBackCallback()
    }

    private fun closeProfile() {
        profile.hide()
        syncBackCallback()
    }

    /** Back closes whichever modal is open; with none open it's the system's again. */
    private fun closeModal(): Boolean = when {
        profile.isOpen -> { closeProfile(); true }
        history.isOpen -> { closeHistory(); true }
        else -> false
    }

    private fun syncBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val anyOpen = profile.isOpen || history.isOpen
        if (anyOpen && backCallback == null) {
            val cb = OnBackInvokedCallback { closeModal() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
            backCallback = cb
        } else if (!anyOpen && backCallback != null) {
            (backCallback as? OnBackInvokedCallback)?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
            backCallback = null
        }
    }

    // Below Android 13, back (button or gesture) arrives as a key event.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && closeModal()) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun headlines(): List<TickerItem> {
        val steps = repo.stepsToday()
        val goal = repo.goal
        val cadence = repo.cadence()
        val best = repo.history(7).maxByOrNull { it.steps }
        return buildList {
            add(TickerItem("PASOS HOY ${Format.steps(steps)}", highlight = true))
            if (steps < goal) {
                add(TickerItem("FALTAN ${Format.steps(goal - steps)} PASOS PARA LA META"))
            } else {
                add(TickerItem("META CUMPLIDA · LA TORRE 61 SE ENCIENDE EN TU HONOR", highlight = true))
                add(TickerItem("FUEGOS ARTIFICIALES SOBRE DISTRICT 9 HASTA MEDIANOCHE"))
            }
            add(TickerItem("DESLIZA EL DEDO PARA RECORRER EL DISTRITO", highlight = true))
            if (repo.heightCm == 0 || repo.weightKg == 0) {
                add(TickerItem("TOCA EL PANEL DE DATOS PARA AJUSTAR TU ALTURA Y PESO", highlight = true))
            }
            val streak = repo.streak()
            val lost = repo.lostStreak()
            when {
                streak > 0 -> {
                    val next = StreakPerks.next(streak)
                    add(
                        TickerItem(
                            "RACHA DE $streak ${if (streak == 1) "DÍA" else "DÍAS"}" +
                                (next?.let { " · A LOS ${it.days}: ${it.headline}" } ?: " · DISTRICT 9 YA ES TUYO"),
                            highlight = true,
                        ),
                    )
                }
                lost >= 2 -> add(TickerItem("SE ROMPIÓ UNA RACHA DE $lost DÍAS · LOS PUESTOS NUEVOS HAN CERRADO"))
            }
            add(TickerItem("BOMBAS DE DRENAJE AL 140%"))
            add(TickerItem("${Format.km(steps, repo.strideMeters)} RECORRIDOS BAJO LA LLUVIA"))
            add(
                when {
                    repo.hotelFixedToday() -> TickerItem("LA «L» DEL HOTEL FUNCIONA · DE MOMENTO")
                    repo.hotelEverFixed() -> TickerItem("LA «L» DEL HOTEL HA VUELTO A FALLAR · EL TÉCNICO: «MAÑANA»")
                    else -> TickerItem("EL LETRERO DEL HOTEL SIGUE SIN SU «L» · EL TÉCNICO VENDRÁ «MAÑANA»")
                },
            )
            if (cadence > 0) add(TickerItem("RITMO ACTUAL $cadence PASOS/MIN", highlight = true))
            add(TickerItem("NIGHT MARKET", highlight = true))
            add(TickerItem("EL KOI DE LA TORRE 61 NADA MÁS RÁPIDO CUANDO CAMINAS"))
            if (best != null && best.steps > 0) add(TickerItem("MEJOR DÍA DE LA SEMANA: ${Format.steps(best.steps)} PASOS"))
            if (repo.sensorMode == SensorMode.ACCELEROMETER) {
                add(TickerItem("SIN PODÓMETRO · CONTANDO CON EL ACELERÓMETRO CON LA PANTALLA ENCENDIDA", highlight = true))
            }
            add(TickerItem("HOY HAS QUEMADO ${Format.ramenBowls(steps, repo.kcalPerStep)} DE RAMEN · DATTEBAYO"))
            if (steps >= goal) add(TickerItem("RAMEN GRATIS EN ICHIRAKU PARA QUIEN LLEGA A LA META", highlight = true))
            add(TickerItem("RAMEN 24H EN EL PUESTO 3"))
        }
    }

    // --- Permissions -------------------------------------------------------------------------

    private fun missingPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private fun requestMissingPermissions() {
        val missing = missingPermissions()
        if (missing.isEmpty()) return
        uiPrefs().edit().putBoolean(KEY_ASKED, true).apply()
        requestPermissions(missing, REQUEST_PERMISSIONS)
    }

    private fun onPermissionTap() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val asked = uiPrefs().getBoolean(KEY_ASKED, false)
        if (!asked || shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION)) {
            requestMissingPermissions()
        } else {
            // Denied for good: the system won't show the dialog again, so send the user to Settings.
            Toast.makeText(this, R.string.toast_permission_settings, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS && StepCounterService.hasActivityPermission(this)) {
            StepCounterService.start(this)
        }
        updateUi()
    }

    private fun uiPrefs() = getSharedPreferences("ui", MODE_PRIVATE)

    // --- Window ------------------------------------------------------------------------------

    private fun goEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Needed on Android 11–14; from 15 edge-to-edge is the default.
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun applyInsets() {
        val root = findViewById<View>(R.id.root)
        val overlay = findViewById<View>(R.id.overlay)
        val headerTop = header.paddingTop
        root.setOnApplyWindowInsetsListener { _, insets ->
            val top: Int
            val bottom: Int
            val left: Int
            val right: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                top = bars.top; bottom = bars.bottom; left = bars.left; right = bars.right
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
                @Suppress("DEPRECATION")
                left = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                right = insets.systemWindowInsetRight
            }
            overlay.setPadding(left, 0, right, 0)
            header.setPadding(header.paddingLeft, headerTop + top, header.paddingRight, header.paddingBottom)
            ticker.setPadding(0, 0, 0, bottom)
            insets
        }
    }

    companion object {
        /** Read by the step service: while the app is on screen it celebrates there, not in a notification. */
        @Volatile
        var isInForeground = false
            private set

        private const val REQUEST_PERMISSIONS = 9
        private const val KEY_ASKED = "asked_permissions"
    }
}
