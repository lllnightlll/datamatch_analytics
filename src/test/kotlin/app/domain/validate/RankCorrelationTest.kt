package app.domain.validate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RankCorrelationTest {
    @Test
    fun pearsonIsOneOnLine() {
        val xs = listOf(1.0, 2.0, 3.0, 4.0)
        val ys = listOf(2.0, 4.0, 6.0, 8.0)
        assertEquals(1.0, RankCorrelation.pearson(xs, ys), 1e-9)
    }

    @Test
    fun spearmanIsOneWhenMonotone() {
        val xs = listOf(1.0, 4.0, 9.0, 16.0)
        val ys = listOf(10.0, 20.0, 30.0, 40.0)
        assertEquals(1.0, RankCorrelation.spearman(xs, ys), 1e-9)
    }

    @Test
    fun ranksAverageTies() {
        val ranks = RankCorrelation.ranks(listOf(3.0, 1.0, 3.0, 2.0))
        assertEquals(listOf(3.5, 1.0, 3.5, 2.0), ranks)
    }
}

class MatchHalfSplitterTest {
    @Test
    fun evenIndexGoesToFit() {
        val halves = MatchHalfSplitter().split(listOf("M3", "M1", "M2", "M4"))
        assertEquals(setOf("M1", "M3"), halves.fit)
        assertEquals(setOf("M2", "M4"), halves.out)
    }
}

class PlusMinusAssignerTest {
    @Test
    fun creditsOnIceForGoal() {
        val stint = app.domain.rating.IceStint(
            matchId = "G1",
            startSeq = 1,
            endSeq = 20,
            durationSeconds = 30.0,
            playersByTeam = mapOf(
                "T1" to setOf("A", "B"),
                "T2" to setOf("C"),
            ),
            nHome = 6,
            nAway = 6,
            fiveOnFive = true,
            rinkType = "european_60x30",
            homeTeamId = "T1",
            awayTeamId = "T2",
        )
        val deltas = mutableMapOf<String, Int>()
        PlusMinusAssigner().apply(
            listOf(stint),
            listOf(OutcomeEvent("G1", 10, "A", "T1", "goal")),
        ) { playerId, _, delta ->
            deltas[playerId] = (deltas[playerId] ?: 0) + delta
        }
        assertEquals(1, deltas["A"])
        assertEquals(1, deltas["B"])
        assertEquals(-1, deltas["C"])
        assertTrue("A" in deltas)
    }
}
