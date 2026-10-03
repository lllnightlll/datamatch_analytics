package app.domain.toi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToiReconstructorTest {
    private val rules = ToiRules(
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
    private val reconstructor = ToiReconstructor(rules)

    @Test
    fun creditsShiftUntilReplacementAndPeriodEnd() {
        val ledger = reconstructor.reconstruct(
            listOf(
                shift("G1", 1, "shift_F1", actor = "A", other = null, clock = 0.0),
                shift("G1", 2, "shift_F2", actor = "B", other = null, clock = 0.0),
                shift("G1", 3, "shift_F1", actor = "C", other = "A", clock = 40.0),
                marker("G1", 4, "intermission", period = 1, clock = 1200.0),
            ),
        )
        assertEquals(40.0, ledger.getValue("A").toiSeconds, 0.01)
        assertEquals(1200.0, ledger.getValue("B").toiSeconds, 0.01)
        assertEquals(1160.0, ledger.getValue("C").toiSeconds, 0.01)
        assertEquals(1, ledger.getValue("B").nGames())
    }

    @Test
    fun ignoresShootoutPeriod() {
        val ledger = reconstructor.reconstruct(
            listOf(
                shift("G1", 1, "shift_F1", actor = "A", other = null, clock = 0.0, period = 0),
                shift("G1", 2, "shift_F1", actor = "B", other = "A", clock = 30.0, period = 0),
            ),
        )
        assertTrue(ledger.isEmpty())
    }

    @Test
    fun splitsFiveOnFiveAndSpecialWhenPlayerLeavesWithoutReplacement() {
        val ledger = reconstructor.reconstruct(
            listOf(
                shift("G1", 1, "shift_F1", actor = "A", other = null, clock = 0.0, nHome = 6, nAway = 6),
                shift("G1", 2, "shift_F2", actor = "B", other = null, clock = 0.0, nHome = 6, nAway = 6),
                shift("G1", 3, "shift_F1", actor = null, other = "A", clock = 100.0, nHome = 6, nAway = 5),
                marker("G1", 4, "game_end", period = 1, clock = 200.0),
            ),
        )
        assertEquals(100.0, ledger.getValue("A").toiSeconds, 0.01)
        assertEquals(100.0, ledger.getValue("A").toiFiveOnFiveSeconds, 0.01)
        assertEquals(200.0, ledger.getValue("B").toiSeconds, 0.01)
        assertEquals(100.0, ledger.getValue("B").toiFiveOnFiveSeconds, 0.01)
        assertEquals(100.0, ledger.getValue("B").toiSpecialSeconds, 0.01)
    }

    @Test
    fun skipsGoalieShifts() {
        val ledger = reconstructor.reconstruct(
            listOf(
                shift("G1", 1, "shift_G", actor = "G1p", other = null, clock = 0.0),
                shift("G1", 2, "shift_F1", actor = "A", other = null, clock = 0.0),
                marker("G1", 3, "game_end", period = 1, clock = 60.0),
            ),
        )
        assertTrue("G1p" !in ledger)
        assertEquals(60.0, ledger.getValue("A").toiSeconds, 0.01)
    }

    private fun shift(
        matchId: String,
        seq: Int,
        type: String,
        actor: String?,
        other: String?,
        clock: Double,
        period: Int = 1,
        nHome: Int? = 6,
        nAway: Int? = 6,
        team: String? = "T001",
    ): TimelineEvent {
        return TimelineEvent(
            matchId = matchId,
            seq = seq,
            eventType = type,
            actorId = actor,
            otherId = other,
            actorTeamId = if (actor != null) team else null,
            period = period,
            clockS = clock,
            nHome = nHome,
            nAway = nAway,
        )
    }

    private fun marker(
        matchId: String,
        seq: Int,
        type: String,
        period: Int,
        clock: Double,
    ): TimelineEvent {
        return TimelineEvent(
            matchId = matchId,
            seq = seq,
            eventType = type,
            actorId = null,
            otherId = null,
            actorTeamId = null,
            period = period,
            clockS = clock,
            nHome = null,
            nAway = null,
        )
    }
}
