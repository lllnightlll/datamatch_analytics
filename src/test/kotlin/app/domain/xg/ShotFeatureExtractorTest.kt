package app.domain.xg

import app.domain.model.RinkLandmark
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShotFeatureExtractorTest {
    private val rules = XgRules(
        attemptTypes = setOf("goal", "shot_on_target"),
        goalType = "goal",
        ignoredPeriods = setOf(0),
        goalObject = "goal_opp_center",
        defaultRinkType = "european_60x30",
        fiveOnFiveN = 6,
        l2Lambda = 0.1,
        maxIterations = 25,
        holdoutMatchMod = 5,
        calibrationBins = 8,
    )
    private val catalog = RinkCatalog(
        landmarks = listOf(
            RinkLandmark("european_60x30", "goal_opp_center", "point", 26.5, 0.0),
        ),
        goalObject = "goal_opp_center",
        defaultRinkType = "european_60x30",
    )
    private val extractor = ShotFeatureExtractor(catalog, rules)

    @Test
    fun slotShotHasDistanceAndZeroAngle() {
        val features = extractor.extract(shot(x = 20.0, y = 0.0))
        assertEquals(6.5, features[0], 1e-6)
        assertEquals(0.0, features[2], 1e-6)
        assertEquals(1.0, features[3], 1e-6)
        assertEquals(1.0, features[4], 1e-6)
    }

    @Test
    fun wideShotHasLargerAngle() {
        val slot = extractor.extract(shot(x = 20.0, y = 0.0))
        val wide = extractor.extract(shot(x = 20.0, y = 6.5))
        assertTrue(wide[2] > slot[2])
        assertEquals(PI / 4.0, wide[2], 1e-6)
    }

    @Test
    fun powerPlayCountsExtraSkater() {
        val features = extractor.extract(
            shot(x = 20.0, y = 0.0, nHome = 6, nAway = 5, isHome = true),
        )
        assertEquals(0.0, features[4], 1e-6)
        assertEquals(1.0, features[5], 1e-6)
    }

    private fun shot(
        x: Double,
        y: Double,
        nHome: Int = 6,
        nAway: Int = 6,
        isHome: Boolean = true,
    ): ShotAttempt {
        return ShotAttempt(
            matchId = "G1",
            seq = 1,
            playerId = "A",
            actorTeamId = "T001",
            eventType = "shot_on_target",
            x = x,
            y = y,
            zone = "off",
            nHome = nHome,
            nAway = nAway,
            rinkType = "european_60x30",
            isHomeShooter = isHome,
            isGoal = false,
        )
    }
}
