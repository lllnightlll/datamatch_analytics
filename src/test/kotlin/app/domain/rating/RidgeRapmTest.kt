package app.domain.rating

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RidgeRapmTest {
    private val rules = RatingRules(
        ridgeLambda = 1.0,
        minStintSeconds = 1.0,
        bootstrapDraws = 20,
        bootstrapSeed = 1,
        loQuantile = 0.025,
        hiQuantile = 0.975,
        shrinkPriorMinutes = 40.0,
        rapmFiveOnFiveOnly = true,
        includeRinkEffects = false,
        minBootstrapHits = 5,
    )

    @Test
    fun plusPlayerGetsPositiveEffect() {
        val fit = RidgeRapm(rules).fit(
            listOf(
                stint(y = 2.0, plus = listOf("A"), minus = listOf("B")),
                stint(y = 2.0, plus = listOf("A"), minus = listOf("C")),
                stint(y = -2.0, plus = listOf("B"), minus = listOf("A")),
            ),
        )
        assertTrue(fit.playerEffects.getValue("A") > fit.playerEffects.getValue("B"))
    }

    @Test
    fun strongerPenaltyShrinksTowardZero() {
        val stints = listOf(
            stint(y = 4.0, plus = listOf("A"), minus = listOf("B")),
            stint(y = 4.0, plus = listOf("A"), minus = listOf("B")),
            stint(y = -4.0, plus = listOf("B"), minus = listOf("A")),
        )
        val weak = RidgeRapm(rules.copy(ridgeLambda = 0.5)).fit(stints)
        val strong = RidgeRapm(rules.copy(ridgeLambda = 80.0)).fit(stints)
        val weakNorm = weak.playerEffects.values.sumOf { it * it }
        val strongNorm = strong.playerEffects.values.sumOf { it * it }
        assertTrue(strongNorm < weakNorm, "strong=$strongNorm weak=$weakNorm")
    }
}

class RatingRulesTest {
    private val rules = RatingRules(
        ridgeLambda = 80.0,
        minStintSeconds = 3.0,
        bootstrapDraws = 10,
        bootstrapSeed = 1,
        loQuantile = 0.025,
        hiQuantile = 0.975,
        shrinkPriorMinutes = 40.0,
        rapmFiveOnFiveOnly = true,
        includeRinkEffects = true,
        minBootstrapHits = 5,
    )

    @Test
    fun per60ScalesTwentyMinutesToHour() {
        assertEquals(3.0, rules.per60(1.0, 20.0 * 60.0), 1e-9)
    }

    @Test
    fun shrinkHalvesAtPriorMinutes() {
        assertEquals(0.5, rules.shrink(1.0, 40.0), 1e-9)
        assertTrue(rules.shrink(1.0, 20.0) < rules.shrink(1.0, 80.0))
    }
}

class MatchBootstrapTest {
    @Test
    fun intervalCoversSortedQuantiles() {
        val rules = RatingRules(
            ridgeLambda = 1.0,
            minStintSeconds = 1.0,
            bootstrapDraws = 10,
            bootstrapSeed = 7,
            loQuantile = 0.0,
            hiQuantile = 1.0,
            shrinkPriorMinutes = 40.0,
            rapmFiveOnFiveOnly = true,
            includeRinkEffects = false,
            minBootstrapHits = 1,
        )
        val boot = MatchBootstrap(rules)
        val (lo, hi) = boot.interval(listOf(1.0, 2.0, 3.0, 4.0))
        assertEquals(1.0, lo, 1e-9)
        assertEquals(4.0, hi, 1e-9)
    }

    @Test
    fun resampleKeepsMatchCount() {
        val rules = RatingRules(
            ridgeLambda = 1.0,
            minStintSeconds = 1.0,
            bootstrapDraws = 8,
            bootstrapSeed = 113,
            loQuantile = 0.025,
            hiQuantile = 0.975,
            shrinkPriorMinutes = 40.0,
            rapmFiveOnFiveOnly = true,
            includeRinkEffects = false,
            minBootstrapHits = 1,
        )
        val draws = MatchBootstrap(rules).resample(listOf("M1", "M2", "M3"))
        assertEquals(8, draws.size)
        assertTrue(draws.all { it.size == 3 })
    }
}

class OnIceStintBuilderTest {
    @Test
    fun buildsDurationBetweenShiftAndPeriodEnd() {
        val rules = app.domain.toi.ToiRules(
            shiftPrefix = "shift_",
            goalieShiftType = "shift_G",
            ignoredPeriods = setOf(0),
            fiveOnFiveN = 6,
            defaultNHome = 6,
            defaultNAway = 6,
            regulationPeriodSeconds = 1200.0,
            minRatingToiMinutes = 20.0,
            avangardTeamId = "T113",
            periodBoundaryTypes = setOf("period_start", "intermission", "game_end"),
        )
        val game = app.domain.model.Game(
            matchId = "G1",
            stage = "regular",
            homeTeamId = "T001",
            awayTeamId = "T002",
            homeGoals = null,
            awayGoals = null,
            rinkType = "european_60x30",
            nEvents = 4,
            league = null,
            season = null,
            gameSeq = null,
        )
        val stints = OnIceStintBuilder(rules).buildMatch(
            listOf(
                TimelineEvent("G1", 1, "shift_F1", "A", null, "T001", 1, 0.0, 6, 6),
                TimelineEvent("G1", 2, "shift_F1", "B", null, "T002", 1, 0.0, 6, 6),
                TimelineEvent("G1", 3, "game_end", null, null, null, 1, 60.0, null, null),
            ),
            game,
        )
        assertEquals(1, stints.size)
        assertEquals(60.0, stints.first().durationSeconds, 0.01)
        assertTrue("A" in stints.first().homePlayers())
        assertTrue("B" in stints.first().awayPlayers())
    }
}

private fun TimelineEvent(
    matchId: String,
    seq: Int,
    type: String,
    actor: String?,
    other: String?,
    team: String?,
    period: Int,
    clock: Double,
    nHome: Int?,
    nAway: Int?,
): app.domain.toi.TimelineEvent {
    return app.domain.toi.TimelineEvent(
        matchId = matchId,
        seq = seq,
        eventType = type,
        actorId = actor,
        otherId = other,
        actorTeamId = team,
        period = period,
        clockS = clock,
        nHome = nHome,
        nAway = nAway,
    )
}

private fun stint(
    y: Double,
    plus: List<String>,
    minus: List<String>,
    matchId: String = "M1",
): WeightedStint {
    return WeightedStint(
        matchId = matchId,
        y = y,
        weight = 30.0,
        plusPlayerIds = plus,
        minusPlayerIds = minus,
        rinkType = "european_60x30",
    )
}
