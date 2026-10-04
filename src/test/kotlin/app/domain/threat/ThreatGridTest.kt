package app.domain.threat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThreatGridTest {
    private val rules = sampleRules()

    @Test
    fun cellIndexMapsBottomLeft() {
        assertEquals(0, rules.cellIndex(-20.0, -10.0))
        assertEquals(1, rules.cellIndex(-9.0, -10.0))
        assertEquals(4, rules.cellIndex(-20.0, 0.0))
    }

    @Test
    fun sparseCellShrinksTowardPrior() {
        val builder = ThreatGridBuilder(rules)
        repeat(10) { builder.add(x = 15.0, y = 0.0, shotSoon = true) }
        repeat(90) { builder.add(x = -15.0, y = 0.0, shotSoon = false) }
        val grid = builder.build()
        val rawHigh = 1.0
        val shrunk = grid.threat(15.0, 0.0)
        assertTrue(shrunk < rawHigh)
        assertTrue(shrunk > grid.globalRate)
        assertEquals(100, grid.nObservations)
    }

    @Test
    fun denseCellKeepsEmpiricalRate() {
        val builder = ThreatGridBuilder(rules)
        repeat(50) { builder.add(x = 15.0, y = 0.0, shotSoon = true) }
        repeat(50) { builder.add(x = 15.0, y = 0.0, shotSoon = false) }
        val grid = builder.build()
        assertEquals(0.5, grid.threat(15.0, 0.0), 1e-9)
    }
}

class ActionValuatorTest {
    @Test
    fun completedPassUsesDestinationDelta() {
        val grid = denseSlotGrid()
        val valuator = ActionValuator(grid, sampleRules())
        val credit = valuator.evaluate(
            event(
                type = "pass_complete",
                x = -15.0,
                y = 0.0,
                x2 = 15.0,
                y2 = 0.0,
            ),
            shared,
        )
        requireNotNull(credit)
        assertEquals("pass", credit.family)
        assertTrue(credit.value > 0.0)
        assertEquals(grid.threat(15.0, 0.0) - grid.threat(-15.0, 0.0), credit.value, 1e-9)
    }

    @Test
    fun stealCreditsCurrentCell() {
        val grid = denseSlotGrid()
        val valuator = ActionValuator(grid, sampleRules())
        val credit = valuator.evaluate(event(type = "steal", x = 15.0, y = 0.0), shared)
        requireNotNull(credit)
        assertEquals(grid.threat(15.0, 0.0), credit.value, 1e-9)
    }

    @Test
    fun turnoverPenalizesCurrentCell() {
        val grid = denseSlotGrid()
        val valuator = ActionValuator(grid, sampleRules())
        val credit = valuator.evaluate(event(type = "turnover", x = 15.0, y = 0.0), shared)
        requireNotNull(credit)
        assertEquals(-grid.threat(15.0, 0.0), credit.value, 1e-9)
    }

    @Test
    fun trainOnlyTypeIsIgnored() {
        val valuator = ActionValuator(denseSlotGrid(), sampleRules())
        val credit = valuator.evaluate(event(type = "carry", x = 15.0, y = 0.0), shared)
        assertEquals(null, credit)
    }
}

class ShotLookaheadTest {
    @Test
    fun findsSameTeamShotInsideWindow() {
        val events = listOf(
            event(type = "touch", seq = 1, period = 1, team = "A"),
            event(type = "pass_complete", seq = 2, period = 1, team = "A"),
            event(type = "shot_on_target", seq = 3, period = 1, team = "A"),
        )
        assertEquals(true, ShotLookahead.shotSoon(events, 0, sampleRules()))
    }

    @Test
    fun stopsAtPeriodChange() {
        val events = listOf(
            event(type = "touch", seq = 1, period = 1, team = "A"),
            event(type = "shot_on_target", seq = 2, period = 2, team = "A"),
        )
        assertEquals(false, ShotLookahead.shotSoon(events, 0, sampleRules()))
    }
}

private val shared = setOf("pass_complete", "steal", "turnover", "touch", "shot_on_target")

private fun sampleRules(): ThreatRules {
    return ThreatRules(
        cellSizeM = 10.0,
        xMin = -20.0,
        xMax = 20.0,
        yMin = -10.0,
        yMax = 10.0,
        lookaheadEvents = 12,
        minCellCount = 40,
        priorStrength = 80.0,
        ignoredPeriods = setOf(0),
        shotTypes = setOf("goal", "shot_on_target"),
        locationTypes = setOf("touch", "pass_complete", "steal", "turnover"),
        neutralXAbs = 7.6,
        actions = listOf(
            ActionRule("pass_complete", ActionKind.MOVE, "pass"),
            ActionRule("steal", ActionKind.GAIN, "steal"),
            ActionRule("turnover", ActionKind.LOSS, "turnover"),
        ),
    )
}

private fun denseSlotGrid(): ThreatGrid {
    val builder = ThreatGridBuilder(sampleRules())
    repeat(80) { builder.add(x = 15.0, y = 0.0, shotSoon = true) }
    repeat(80) { builder.add(x = -15.0, y = 0.0, shotSoon = false) }
    return builder.build()
}

private fun event(
    type: String,
    x: Double = 0.0,
    y: Double = 0.0,
    x2: Double? = null,
    y2: Double? = null,
    seq: Int = 1,
    period: Int = 1,
    team: String = "A",
): PuckEvent {
    return PuckEvent(
        matchId = "G1",
        seq = seq,
        eventType = type,
        actorId = "P1",
        actorTeamId = team,
        x = x,
        y = y,
        x2 = x2,
        y2 = y2,
        period = period,
    )
}
