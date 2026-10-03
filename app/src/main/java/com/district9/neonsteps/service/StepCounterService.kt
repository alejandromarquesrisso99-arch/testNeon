package com.district9.neonsteps.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.district9.neonsteps.MainActivity
import com.district9.neonsteps.R
import com.district9.neonsteps.data.Cosmetics
import com.district9.neonsteps.data.MissionState
import com.district9.neonsteps.data.SensorMode
import com.district9.neonsteps.data.StepRepository
import com.district9.neonsteps.util.CityClock
import com.district9.neonsteps.util.Format
import com.district9.neonsteps.widget.StepsWidget

/**
 * Keeps a step sensor registered while the app is in the background. The hardware step
 * counter only accumulates while some app holds a registration, so a foreground service is
 * what makes the daily total reliable.
 */
class StepCounterService : Service(), SensorEventListener {

    private lateinit var repo: StepRepository
    private lateinit var sensorManager: SensorManager
    private val accelDetector = AccelerometerStepDetector()
    private var lastNotifiedSteps = -1
    private var lastNotifyAt = 0L

    private val repoListener = StepRepository.Listener {
        maybeUpdateNotification()
        maybeNotifyGoal()
        maybeNotifyMission()
        StepsWidget.refresh(this)
    }

    override fun onCreate() {
        super.onCreate()
        repo = StepRepository.get(this)
        sensorManager = getSystemService(SensorManager::class.java)
        createChannel()
        if (!enterForeground()) {
            stopSelf()
            return
        }
        registerBestSensor()
        repo.addListener(repoListener)
        maybeNotifyGoal()
        maybeNotifyMission()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every startForegroundService() must be answered with startForeground(), even when running.
        if (isRunning && !enterForeground()) stopSelf()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        if (::repo.isInitialized) {
            repo.removeListener(repoListener)
            repo.flush()
        }
        isRunning = false
        super.onDestroy()
    }

    private fun enterForeground(): Boolean = try {
        val notification = buildNotification(repo.stepsToday())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isRunning = true
        true
    } catch (e: RuntimeException) {
        // SecurityException when the activity-recognition permission was revoked, or
        // ForegroundServiceStartNotAllowedException when started from the background.
        Log.w(TAG, "Could not enter the foreground", e)
        false
    }

    private fun registerBestSensor() {
        val counter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val detector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        repo.sensorMode = when {
            counter != null && sensorManager.registerListener(this, counter, SensorManager.SENSOR_DELAY_UI) ->
                SensorMode.STEP_COUNTER
            detector != null && sensorManager.registerListener(this, detector, SensorManager.SENSOR_DELAY_UI) ->
                SensorMode.STEP_DETECTOR
            accel != null && sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME) ->
                SensorMode.ACCELEROMETER
            else -> SensorMode.NONE
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> repo.onStepCounter(event.values[0].toLong(), bootCount())
            Sensor.TYPE_STEP_DETECTOR -> repo.addSteps(1) // one event per detected step
            Sensor.TYPE_ACCELEROMETER -> {
                if (accelDetector.onSample(event.values[0], event.values[1], event.values[2], event.timestamp)) {
                    repo.addSteps(1)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun bootCount(): Int =
        Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)

    private fun maybeUpdateNotification() {
        val steps = repo.stepsToday()
        val now = SystemClock.elapsedRealtime()
        if (steps == lastNotifiedSteps || now - lastNotifyAt < NOTIFY_INTERVAL_MS) return
        lastNotifiedSteps = steps
        lastNotifyAt = now
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(steps))
    }

    /** Once a day, when the goal falls: a sounding notification, unless the app is on screen. */
    private fun maybeNotifyGoal() {
        if (!repo.goalReachedToday() || repo.goalNotifiedToday()) return
        repo.markGoalNotified()
        if (MainActivity.isInForeground) return // the city celebrates on screen instead
        val steps = repo.stepsToday()
        val notification = Notification.Builder(this, GOAL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_steps)
            .setColor(getColor(R.color.neon_magenta))
            .setContentTitle(getString(R.string.notif_goal_title))
            .setContentText(getString(R.string.notif_goal_text, Format.steps(steps)))
            .setStyle(Notification.BigTextStyle().bigText(getString(R.string.notif_goal_text, Format.steps(steps))))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        getSystemService(NotificationManager::class.java).notify(GOAL_NOTIFICATION_ID, notification)
    }

    /**
     * The day's mission: announced with the first steps after 7:00, and again when it's done.
     * While the app is on screen its card says it all, so no notification then.
     */
    private fun maybeNotifyMission() {
        val state = repo.evaluateMission()
        val mission = repo.mission()
        val reward = Cosmetics.rewardName(mission.reward)
        val nm = getSystemService(NotificationManager::class.java)
        if (state == MissionState.ACTIVE && !repo.missionAnnounced() && CityClock.now().hour >= 7) {
            repo.markMissionAnnounced()
            if (!MainActivity.isInForeground) {
                nm.notify(MISSION_NOTIFICATION_ID, missionNotification(getString(R.string.notif_mission_title), getString(R.string.notif_mission_text, mission.title, reward)))
            }
        }
        if (state == MissionState.DONE && !repo.missionDoneNotified()) {
            repo.markMissionDoneNotified()
            if (!MainActivity.isInForeground) {
                nm.notify(MISSION_NOTIFICATION_ID, missionNotification(getString(R.string.notif_mission_done_title), getString(R.string.notif_mission_done_text, reward)))
            }
        }
    }

    private fun missionNotification(title: String, text: String): Notification =
        Notification.Builder(this, MISSION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_steps)
            .setColor(0xFFFF8A1E.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(steps: Int): Notification {
        val open = openAppIntent()
        val goal = repo.goal
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_steps)
            .setColor(getColor(R.color.neon_magenta))
            .setContentTitle(getString(R.string.notif_title, Format.steps(steps)))
            .setContentText(getString(R.string.notif_text, Format.percent(steps, goal), Format.km(steps, repo.strideMeters)))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        val goals = NotificationChannel(
            GOAL_CHANNEL_ID,
            getString(R.string.notif_goal_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = getString(R.string.notif_goal_channel_desc) }
        val missions = NotificationChannel(
            MISSION_CHANNEL_ID,
            getString(R.string.notif_mission_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = getString(R.string.notif_mission_channel_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(channel, goals, missions))
    }

    companion object {
        private const val TAG = "StepCounterService"
        private const val CHANNEL_ID = "steps"
        private const val NOTIFICATION_ID = 61
        private const val GOAL_CHANNEL_ID = "goals"
        private const val GOAL_NOTIFICATION_ID = 62
        private const val MISSION_CHANNEL_ID = "missions"
        private const val MISSION_NOTIFICATION_ID = 63
        private const val NOTIFY_INTERVAL_MS = 5_000L

        @Volatile
        var isRunning = false
            private set

        /** Activity recognition is a runtime permission from Android 10; before that it's implicit. */
        fun hasActivityPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED

        /** Starts the service if the permission it needs is granted. Returns whether it was started. */
        fun start(context: Context): Boolean {
            if (!hasActivityPermission(context)) return false
            if (isRunning) return true
            return try {
                context.startForegroundService(Intent(context, StepCounterService::class.java))
                true
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not start step service", e)
                false
            }
        }
    }
}
