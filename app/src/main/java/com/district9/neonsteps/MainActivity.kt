package com.district9.neonsteps

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
import com.district9.neonsteps.data.RainMode
import com.district9.neonsteps.data.SensorMode
import com.district9.neonsteps.data.StepRepository
import com.district9.neonsteps.service.StepCounterService
import com.district9.neonsteps.ui.Neon
import com.district9.neonsteps.ui.hud.HeaderView
import com.district9.neonsteps.ui.hud.HistoryView
import com.district9.neonsteps.ui.hud.HudPanelView
import com.district9.neonsteps.ui.hud.NeonButtonView
import com.district9.neonsteps.ui.hud.TickerItem
import com.district9.neonsteps.ui.hud.TickerView
import com.district9.neonsteps.ui.scene.SceneView
import com.district9.neonsteps.util.Format
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
    private lateinit var ticker: TickerView
    private lateinit var history: HistoryView
    private var sensorManager: SensorManager? = null

    private val handler = Handler(Looper.getMainLooper())
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss")
    private var lastSteps = -1
    private var backCallback: Any? = null

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
        ticker = findViewById(R.id.ticker)
        history = findViewById(R.id.history)

        goalButton.accent = Neon.CYAN
        historyButton.accent = Neon.YELLOW
        rainButton.accent = Neon.MAGENTA

        goalButton.setOnClickListener { cycleGoal() }
        historyButton.setOnClickListener { if (history.isOpen) closeHistory() else openHistory() }
        rainButton.setOnClickListener {
            repo.rainMode = RainMode.entries[(repo.rainMode.ordinal + 1) % RainMode.entries.size]
        }
        header.onPermissionRequest = ::onPermissionTap
        history.onDismiss = ::closeHistory
        scene.setOnClickListener { scene.strike(0.55f) } // tap the sky: thunder on demand
        ticker.provider = ::headlines

        applyInsets()
        hud.addOnLayoutChangeListener { v, _, top, _, _, _, _, _, _ ->
            scene.setStreetLimit(top.toFloat())
            // Keep the history modal between the title and the readouts.
            history.setPadding(0, header.bottom, 0, (findViewById<View>(R.id.root).height - v.top).coerceAtLeast(0))
        }
        if (savedInstanceState == null) requestMissingPermissions()
    }

    override fun onStart() {
        super.onStart()
        repo.addListener(this)
        if (StepCounterService.hasActivityPermission(this)) StepCounterService.start(this)
        lastSteps = -1
        handler.post(tick)
        val sm = sensorManager
        val gravity = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (gravity != null) sm?.registerListener(this, gravity, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onStop() {
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
        val permitted = StepCounterService.hasActivityPermission(this)
        val state = when {
            !permitted -> HeaderView.State.NEED_PERMISSION
            StepCounterService.isRunning && repo.sensorMode == SensorMode.NONE -> HeaderView.State.NO_SENSOR
            else -> HeaderView.State.OK
        }
        header.setData(steps, goal, state)
        hud.setData(LocalTime.now().format(clockFormat), Format.km(steps), Format.kcal(steps), percent)

        goalButton.setText(getString(R.string.btn_goal), Format.steps(goal), getString(R.string.cd_goal_button, Format.steps(goal)))
        if (history.isOpen) {
            historyButton.setText(getString(R.string.btn_history), getString(R.string.btn_history_close), getString(R.string.cd_history_close))
            history.setData(repo.history(7), goal)
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

        // Milestones strike lightning: every thousand steps, and twice for the goal.
        if (lastSteps in 0 until steps) {
            if (lastSteps < goal && steps >= goal) {
                scene.strike(1f)
                handler.postDelayed({ scene.strike(0.8f) }, 900)
            } else if (steps / 1000 > lastSteps / 1000) {
                scene.strike(0.6f)
            }
        }
        lastSteps = steps
    }

    private fun cycleGoal() {
        val options = StepRepository.GOAL_OPTIONS
        val next = options.firstOrNull { it > repo.goal } ?: options.first()
        repo.goal = next
    }

    private fun openHistory() {
        history.setData(repo.history(7), repo.goal)
        history.show()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val cb = OnBackInvokedCallback { closeHistory() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
            backCallback = cb
        }
        updateUi()
    }

    private fun closeHistory() {
        history.hide()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (backCallback as? OnBackInvokedCallback)?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
            backCallback = null
        }
        updateUi()
    }

    // Below Android 13, back (button or gesture) arrives as a key event.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && history.isOpen) {
            closeHistory()
            return true
        }
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
                add(TickerItem("META CUMPLIDA · LA TORRE 61 TE SALUDA", highlight = true))
            }
            add(TickerItem("BOMBAS DE DRENAJE AL 140%"))
            add(TickerItem("${Format.km(steps)} RECORRIDOS BAJO LA LLUVIA"))
            add(TickerItem("EL LETRERO DEL HOTEL SIGUE SIN SU «L» · EL TÉCNICO VENDRÁ «MAÑANA»"))
            if (cadence > 0) add(TickerItem("RITMO ACTUAL $cadence PASOS/MIN", highlight = true))
            add(TickerItem("NIGHT MARKET", highlight = true))
            add(TickerItem("EL KOI DE LA TORRE 61 NADA MÁS RÁPIDO CUANDO CAMINAS"))
            if (best != null && best.steps > 0) add(TickerItem("MEJOR DÍA DE LA SEMANA: ${Format.steps(best.steps)} PASOS"))
            if (repo.sensorMode == SensorMode.ACCELEROMETER) {
                add(TickerItem("SIN PODÓMETRO · CONTANDO CON EL ACELERÓMETRO CON LA PANTALLA ENCENDIDA", highlight = true))
            }
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

    private companion object {
        const val REQUEST_PERMISSIONS = 9
        const val KEY_ASKED = "asked_permissions"
    }
}
