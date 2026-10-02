package com.district9.neonsteps.service

import android.Manifest
import android.app.NotificationManager
import com.district9.neonsteps.data.StepRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class GoalNotificationTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        StepRepository.resetForTests()
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() = StepRepository.resetForTests()

    private fun goalNotifications() =
        shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
            .count { it.channelId == "goals" }

    @Test
    fun goalNotifiesOnceADay() {
        val service = Robolectric.buildService(StepCounterService::class.java).create().get()
        val repo = StepRepository.get(service)
        repo.goal = 4_000

        repo.addSteps(3_999)
        assertEquals(0, goalNotifications())

        repo.addSteps(1)
        assertEquals(1, goalNotifications())

        // More steps, or the goal being met again after raising it, don't sound again today.
        repo.addSteps(500)
        repo.goal = 6_000
        repo.addSteps(2_000)
        assertEquals(1, goalNotifications())
    }
}
