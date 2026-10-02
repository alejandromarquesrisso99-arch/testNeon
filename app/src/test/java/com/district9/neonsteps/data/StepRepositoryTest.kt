package com.district9.neonsteps.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class StepRepositoryTest {
    private var now = 1_000_000L
    private var today = LocalDate.of(2026, 10, 2)
    private val prefs by lazy {
        RuntimeEnvironment.getApplication().getSharedPreferences("test", Context.MODE_PRIVATE)
    }

    private fun repo() = StepRepository(prefs, clock = { now }, today = { today })

    @Before
    fun clear() {
        prefs.edit().clear().commit()
    }

    @Test
    fun firstCounterReadingOnlySetsBaseline() {
        val repo = repo()
        repo.onStepCounter(5_000, bootCount = 3)
        assertEquals(0, repo.stepsToday())
        repo.onStepCounter(5_120, bootCount = 3)
        assertEquals(120, repo.stepsToday())
    }

    @Test
    fun rebootCountsWholeNewReading() {
        val repo = repo()
        repo.onStepCounter(5_000, bootCount = 3)
        repo.onStepCounter(5_100, bootCount = 3)
        // After a reboot the hardware counter restarts near zero.
        repo.onStepCounter(40, bootCount = 4)
        assertEquals(140, repo.stepsToday())
    }

    @Test
    fun counterGoingBackwardsWithoutBootCountIsTreatedAsReset() {
        val repo = repo()
        repo.onStepCounter(900, bootCount = -1)
        repo.onStepCounter(1_000, bootCount = -1)
        repo.onStepCounter(30, bootCount = -1)
        assertEquals(130, repo.stepsToday())
    }

    @Test
    fun stepsRollOverAtMidnightAndHistoryKeepsYesterday() {
        val repo = repo()
        repo.addSteps(7_500)
        today = today.plusDays(1)
        assertEquals(0, repo.stepsToday())
        repo.addSteps(250)
        val week = repo.history(7)
        assertEquals(7, week.size)
        assertEquals(today, week.last().date)
        assertEquals(250, week.last().steps)
        assertEquals(7_500, week[5].steps)
    }

    @Test
    fun stateSurvivesProcessRestart() {
        val first = repo()
        first.onStepCounter(10_000, bootCount = 1)
        first.onStepCounter(10_300, bootCount = 1)
        first.flush()
        ShadowLooper.idleMainLooper()

        val second = repo()
        assertEquals(300, second.stepsToday())
        // Steps taken while the app was dead are recovered from the cumulative counter.
        second.onStepCounter(10_800, bootCount = 1)
        assertEquals(800, second.stepsToday())
    }

    @Test
    fun goalFlagsLastOneDay() {
        val repo = repo()
        repo.goal = 4_000
        repo.addSteps(4_100)
        assertTrue(repo.goalReachedToday())
        assertFalse(repo.goalNotifiedToday())
        repo.markGoalNotified()
        repo.markGoalCelebrated()
        assertTrue(repo.goalNotifiedToday())
        assertTrue(repo.goalCelebratedToday())

        today = today.plusDays(1)
        assertFalse(repo.goalReachedToday())
        assertFalse(repo.goalNotifiedToday())
        assertFalse(repo.goalCelebratedToday())
    }

    @Test
    fun profileDrivesStrideAndEnergy() {
        val repo = repo()
        assertEquals(0.75, repo.strideMeters, 1e-9)
        assertEquals(0.039375, repo.kcalPerStep, 1e-9) // 70 kg over a 0.75 m stride
        repo.heightCm = 180
        repo.weightKg = 90
        assertEquals(0.7452, repo.strideMeters, 1e-9)
        assertEquals(0.75 * 90 * 0.7452 / 1000, repo.kcalPerStep, 1e-9)
    }

    @Test
    fun streakCountsDaysThatMetTheirOwnGoal() {
        val repo = repo()
        repo.goal = 5_000
        // Three days ago: met. Two days ago: met under a lower goal of the day. Yesterday: met.
        repeat(3) { back ->
            today = LocalDate.of(2026, 10, 2).minusDays((3 - back).toLong())
            repo.goal = if (back == 1) 3_000 else 5_000
            repo.addSteps(if (back == 1) 3_500 else 6_000)
            repo.flush()
        }
        today = LocalDate.of(2026, 10, 2)
        repo.goal = 5_000
        // Today isn't met yet, but the streak is still alive.
        assertEquals(3, repo.streak())
        repo.addSteps(5_000)
        assertEquals(4, repo.streak())
    }

    @Test
    fun aMissedDayBreaksTheStreakAndIsReportedOnce() {
        val repo = repo()
        repo.goal = 4_000
        for (back in 4 downTo 2) {
            today = LocalDate.of(2026, 10, 2).minusDays(back.toLong())
            repo.addSteps(4_500)
            repo.flush()
        }
        today = LocalDate.of(2026, 10, 2).minusDays(1)
        repo.addSteps(1_000) // yesterday fell short
        repo.flush()
        today = LocalDate.of(2026, 10, 2)
        assertEquals(0, repo.streak())
        assertEquals(3, repo.lostStreak())
        repo.addSteps(4_000)
        assertEquals(1, repo.streak())
        assertEquals(0, repo.lostStreak())
    }

    @Test
    fun cadenceReflectsRecentStepsAndDecays() {
        val repo = repo()
        repeat(60) {
            now += 500
            repo.addSteps(1)
        }
        assertEquals(120, repo.cadence())
        now += 20_000
        assertEquals(0, repo.cadence())
    }
}
