package app.domain.xg

import kotlinx.serialization.Serializable

@Serializable
data class XgRules(
    val attemptTypes: Set<String>,
    val goalType: String,
    val ignoredPeriods: Set<Int>,
    val goalObject: String,
    val defaultRinkType: String,
    val fiveOnFiveN: Int,
    val l2Lambda: Double,
    val maxIterations: Int,
    val holdoutMatchMod: Int,
    val calibrationBins: Int,
) {
    fun isAttempt(eventType: String): Boolean = eventType in attemptTypes

    fun isGoal(eventType: String): Boolean = eventType == goalType

    fun isIgnoredPeriod(period: Int): Boolean = period in ignoredPeriods

    fun isHoldoutMatch(matchId: String): Boolean {
        return kotlin.math.abs(matchId.hashCode()) % holdoutMatchMod == 0
    }
}

data class Point(
    val x: Double,
    val y: Double,
)

data class ShotAttempt(
    val matchId: String,
    val seq: Int,
    val playerId: String?,
    val actorTeamId: String?,
    val eventType: String,
    val x: Double,
    val y: Double,
    val zone: String?,
    val nHome: Int?,
    val nAway: Int?,
    val rinkType: String,
    val isHomeShooter: Boolean,
    val isGoal: Boolean,
)

data class Coefficient(
    val name: String,
    val weight: Double,
)

data class CalibrationBin(
    val binIndex: Int,
    val predictedMean: Double,
    val actualRate: Double,
    val n: Int,
)

data class XgSplitMetrics(
    val nAttempts: Int,
    val nGoals: Int,
    val sumXg: Double,
    val logLoss: Double,
    val brier: Double,
)

data class ScoredShot(
    val matchId: String,
    val playerId: String?,
    val xg: Double,
    val isGoal: Boolean,
)

data class PlayerXg(
    val playerId: String,
    val positionName: String,
    val nAttempts: Int,
    val nGoals: Int,
    val sumXg: Double,
)

data class XgReport(
    val rules: XgRules,
    val coefficients: List<Coefficient>,
    val holdout: XgSplitMetrics,
    val test: XgSplitMetrics,
    val calibration: List<CalibrationBin>,
    val topTestPlayers: List<PlayerXg>,
)
