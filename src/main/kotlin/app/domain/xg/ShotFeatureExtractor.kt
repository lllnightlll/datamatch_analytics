package app.domain.xg

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

class ShotFeatureExtractor(
    private val rinkCatalog: RinkCatalog,
    private val rules: XgRules,
) {
    val featureNames: List<String> = FEATURE_NAMES

    fun extract(shot: ShotAttempt): DoubleArray {
        val goal = rinkCatalog.opponentGoal(shot.rinkType)
        val dx = goal.x - shot.x
        val dy = shot.y - goal.y
        val distance = hypot(dx, dy)
        val angle = atan2(abs(dy), kotlin.math.max(dx, 1e-3))
        val nUs = (if (shot.isHomeShooter) shot.nHome else shot.nAway) ?: rules.fiveOnFiveN
        val nThem = (if (shot.isHomeShooter) shot.nAway else shot.nHome) ?: rules.fiveOnFiveN
        val fiveOnFive = if (shot.nHome == rules.fiveOnFiveN && shot.nAway == rules.fiveOnFiveN) 1.0 else 0.0
        val extraSkaters = (nUs - nThem).toDouble()
        val offensive = if (shot.zone == ZONE_OFF) 1.0 else 0.0
        return doubleArrayOf(distance, distance * distance / 100.0, angle, offensive, fiveOnFive, extraSkaters)
    }

    companion object {
        const val ZONE_OFF: String = "off"
        val FEATURE_NAMES: List<String> = listOf(
            "distance_m",
            "distance_sq_100",
            "angle_rad",
            "zone_off",
            "five_on_five",
            "extra_skaters",
        )
    }
}
