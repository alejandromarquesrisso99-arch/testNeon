package com.district9.neonsteps.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import com.district9.neonsteps.data.Cosmetics
import com.district9.neonsteps.data.MissionKind
import com.district9.neonsteps.data.StepRepository
import com.district9.neonsteps.util.CityClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class MissionNotificationTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        StepRepository.resetForTests()
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS)
        app.getSharedPreferences("neon_steps", 0).edit()
            .putString("mission_day", LocalDate.now().toString())
            .putString("mission_kind", MissionKind.STEPS_BY_HOUR.name)
            .putInt("mission_target", 3_000)
            .putInt("mission_deadline", 14)
            .putString("mission_reward", "koi_sakura")
            .putString("mission_state", "ACTIVE")
            .commit()
    }

    @After
    fun tearDown() {
        StepRepository.resetForTests()
        CityClock.fixed = null
    }

    private fun missionNotifications() =
        shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
            .filter { it.channelId == "missions" }

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()
    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    @Test
    fun announcedAfterSevenThenNotifiedWhenDone() {
        CityClock.fixed = LocalTime.of(6, 30)
        val service = Robolectric.buildService(StepCounterService::class.java).create().get()
        val repo = StepRepository.get(service)
        repo.addSteps(20)
        assertEquals(0, missionNotifications().size) // nobody wants a job offer at 6:30

        CityClock.fixed = LocalTime.of(8, 5)
        repo.addSteps(20)
        val announce = missionNotifications().single()
        assertEquals("ENCARGO DEL DÍA", announce.title())
        assertEquals("LLEGA A 3.000 PASOS ANTES DE LAS 14:00. Recompensa: KOI SAKURA.", announce.text())

        repo.addSteps(500)
        assertEquals("ENCARGO DEL DÍA", missionNotifications().single().title()) // not repeated

        repo.addSteps(2_460)
        val done = missionNotifications().single()
        assertEquals("¡ENCARGO CUMPLIDO!", done.title())
        assertTrue(done.text().contains("KOI SAKURA"))
        assertEquals("koi_sakura", repo.style(Cosmetics.Slot.KOI)?.id)

        // Shown once: walking on doesn't bring it back after it's dismissed.
        app.getSystemService(NotificationManager::class.java).cancelAll()
        repo.addSteps(1_000)
        assertEquals(0, missionNotifications().size)
    }
}
