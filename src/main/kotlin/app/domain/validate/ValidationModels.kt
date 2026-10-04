package app.domain.validate

import kotlinx.serialization.Serializable

@Serializable
data class ValidationRules(
    val minHalfToiMinutes: Double,
    val minGamesPerHalf: Int,
    val goalType: String,
    val assistTypes: Set<String>,
    val shotTypes: Set<String>,
) {
    fun minHalfToiSeconds(): Double = minHalfToiMinutes * 60.0

    fun isGoal(eventType: String): Boolean = eventType == goalType

    fun isAssist(eventType: String): Boolean = eventType in assistTypes

    fun isShot(eventType: String): Boolean = eventType in shotTypes

    fun isScoring(eventType: String): Boolean {
        return isGoal(eventType) || isAssist(eventType) || isShot(eventType)
    }
}

data class OutcomeEvent(
    val matchId: String,
    val seq: Int,
    val playerId: String,
    val teamId: String?,
    val eventType: String,
)

data class HalfBox(
    val nGames: Int,
    val toiSeconds: Double,
    val nGoals: Int,
    val nAssists: Int,
    val nShots: Int,
    val plusMinus: Int,
    val sumXg: Double,
    val actionValue: Double,
) {
    fun toiMinutes(): Double = toiSeconds / 60.0

    fun ga(): Int = nGoals + nAssists

    fun per60(total: Double): Double {
        if (toiSeconds <= 0.0) return 0.0
        return total * 3600.0 / toiSeconds
    }
}

data class SplitPlayer(
    val playerId: String,
    val positionName: String,
    val rating: Double,
    val rawPer60: Double,
    val rapm: Double,
    val fit: HalfBox,
    val out: HalfBox,
)

data class CorrelationRow(
    val predictor: String,
    val outcome: String,
    val pearson: Double,
    val spearman: Double,
    val n: Int,
)

data class ValidationReport(
    val rules: ValidationRules,
    val nFitMatches: Int,
    val nOutMatches: Int,
    val nPlayers: Int,
    val correlations: List<CorrelationRow>,
    val players: List<SplitPlayer>,
)
