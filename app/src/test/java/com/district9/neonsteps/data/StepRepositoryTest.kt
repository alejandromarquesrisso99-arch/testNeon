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

    private var hour = 9

    private fun repo() = StepRepository(prefs, clock = { now }, today = { today }, hourNow = { hour })

    /** Plants today's mission as if it had been generated earlier. */
    private fun seedMission(kind: MissionKind, target: Int, deadline: Int = 24, reward: String = "rain_cyan") {
        prefs.edit()
            .putString("mission_day", today.toString())
            .putString("mission_kind", kind.name)
            .putInt("mission_target", target)
            .putInt("mission_deadline", deadline)
            .putString("mission_reward", reward)
            .putString("mission_state", MissionState.ACTIVE.name)
            .commit()
    }

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

    @Test
    fun missionsAreStableForTheDayAndVaryDayToDay() {
        var previous: MissionKind? = null
        repeat(40) { i ->
            val repo = repo()
            val m = repo.mission()
            assertEquals(m, repo.mission())
            assertEquals(m, repo().mission()) // survives a restart
            assertTrue("kind repeated on day $i", m.kind != previous)
            assertTrue(m.target > 0)
            if (m.kind == MissionKind.BEAT_YESTERDAY) {
                assertEquals(prefs.getInt("day_" + today.minusDays(1), 0) + 1, m.target)
            }
            assertEquals("rain_cyan", m.reward) // the first locked style
            previous = m.kind
            // Alternate busy and lazy days so "beat yesterday" is sometimes possible.
            repo.addSteps(if (i % 2 == 0) 6_000 else 900)
            repo.flush()
            today = today.plusDays(1)
        }
    }

    @Test
    fun beatYesterdayOnlyAfterARealWalk() {
        repeat(20) {
            prefs.edit().putInt("day_" + today.minusDays(1), 1_200).commit()
            assertTrue(repo().mission().kind != MissionKind.BEAT_YESTERDAY)
            today = today.plusDays(1)
        }
    }

    @Test
    fun completingAMissionUnlocksAndWearsItsReward() {
        seedMission(MissionKind.STEPS_BY_HOUR, 3_000, deadline = 14, reward = "rain_cyan")
        val repo = repo()
        var changes = 0
        repo.addListener { changes++ }
        repo.addSteps(2_999)
        assertEquals(MissionState.ACTIVE, repo.evaluateMission())
        assertEquals(null, repo.style(Cosmetics.Slot.RAIN))
        repo.addSteps(1)
        changes = 0
        assertEquals(MissionState.DONE, repo.evaluateMission())
        assertTrue(changes > 0)
        assertEquals(setOf("rain_cyan"), repo.unlockedStyles())
        assertEquals("rain_cyan", repo.style(Cosmetics.Slot.RAIN)?.id)
        // Once done it stays done, even past the deadline.
        hour = 18
        assertEquals(MissionState.DONE, repo.evaluateMission())
        // Tomorrow pays the next style in the collection.
        today = today.plusDays(1)
        assertEquals("koi_sakura", repo().mission().reward)
    }

    @Test
    fun aMissedDeadlineFailsAndTheRewardWaits() {
        seedMission(MissionKind.MORNING_STEPS, 1_500, deadline = 10)
        val repo = repo()
        repo.addSteps(800)
        hour = 10
        assertEquals(MissionState.FAILED, repo.evaluateMission())
        repo.addSteps(2_000)
        assertEquals(MissionState.FAILED, repo.evaluateMission())
        assertTrue(repo.unlockedStyles().isEmpty())
        today = today.plusDays(1)
        assertEquals("rain_cyan", repo().mission().reward)
        assertEquals(MissionState.ACTIVE, repo().evaluateMission())
    }

    @Test
    fun briskMinutesOnlyCountAGoodPace() {
        seedMission(MissionKind.BRISK_MINUTES, 15)
        val repo = repo()
        val m = repo.mission()
        // Ten minutes strolling at 60 steps/min: doesn't count.
        repeat(600) {
            now += 1_000
            repo.addSteps(1)
        }
        assertEquals(0, repo.missionProgress(m))
        // Sixteen minutes at 120 steps/min does.
        repeat(16 * 120) {
            now += 500
            repo.addSteps(1)
        }
        assertTrue(repo.missionProgress(m) in 15..16)
        assertEquals(MissionState.DONE, repo.evaluateMission())
        assertEquals("15 / 15 MIN", m.progressLabel(repo.missionProgress(m)))
    }

    @Test
    fun distanceFollowsTheStride() {
        seedMission(MissionKind.DISTANCE, 1_000)
        val repo = repo()
        repo.heightCm = 180 // 0.7452 m per step
        val m = repo.mission()
        repo.addSteps(1_341)
        assertEquals(999, repo.missionProgress(m))
        assertEquals(MissionState.ACTIVE, repo.evaluateMission())
        repo.addSteps(1)
        assertEquals(MissionState.DONE, repo.evaluateMission())
        assertEquals("RECORRE 1,0 KM", m.title)
    }

    @Test
    fun stylesCanBeSwappedButOnlyForUnlockedOnes() {
        val repo = repo()
        repo.setStyle(Cosmetics.Slot.KOI, "koi_sakura")
        assertEquals(null, repo.style(Cosmetics.Slot.KOI)) // still locked
        seedMission(MissionKind.STRETCH, 100, reward = "koi_sakura")
        repo.addSteps(100)
        repo.evaluateMission()
        assertEquals("koi_sakura", repo.style(Cosmetics.Slot.KOI)?.id)
        repo.setStyle(Cosmetics.Slot.KOI, null)
        assertEquals(null, repo.style(Cosmetics.Slot.KOI))
    }

    @Test
    fun aFullCollectionPaysInFireworks() {
        prefs.edit().putStringSet("styles_unlocked", Cosmetics.all.map { it.id }.toSet()).commit()
        val m = repo().mission()
        assertEquals(Cosmetics.FIREWORKS, m.reward)
        assertEquals("SALVA DE FUEGOS ARTIFICIALES", Cosmetics.rewardName(m.reward))
    }
}
