package app.domain.toi

import kotlinx.serialization.Serializable

@Serializable
data class ToiRules(
    val shiftPrefix: String,
    val goalieShiftType: String,
    val ignoredPeriods: Set<Int>,
    val fiveOnFiveN: Int,
    val defaultNHome: Int,
    val defaultNAway: Int,
    val regulationPeriodSeconds: Double,
    val minRatingToiMinutes: Double,
    val avangardTeamId: String,
    val periodBoundaryTypes: Set<String>,
) {
    fun isShift(eventType: String): Boolean = eventType.startsWith(shiftPrefix)

    fun isSkaterShift(eventType: String): Boolean {
        return isShift(eventType) && eventType != goalieShiftType
    }

    fun isIgnoredPeriod(period: Int): Boolean = period in ignoredPeriods

    fun minRatingToiSeconds(): Double = minRatingToiMinutes * 60.0

    fun classifyStrength(nHome: Int, nAway: Int): StrengthKind {
        return when {
            nHome == fiveOnFiveN && nAway == fiveOnFiveN -> StrengthKind.FIVE_ON_FIVE
            nHome == nAway -> StrengthKind.EVEN_OTHER
            else -> StrengthKind.SPECIAL
        }
    }
}

enum class StrengthKind {
    FIVE_ON_FIVE,
    EVEN_OTHER,
    SPECIAL,
}

data class TimelineEvent(
    val matchId: String,
    val seq: Int,
    val eventType: String,
    val actorId: String?,
    val otherId: String?,
    val actorTeamId: String?,
    val period: Int,
    val clockS: Double,
    val nHome: Int?,
    val nAway: Int?,
)

data class PlayerToi(
    val playerId: String,
    val positionName: String,
    val primaryTeamId: String?,
    val playedForAvangard: Boolean,
    val nGames: Int,
    val toiSeconds: Double,
    val toiFiveOnFiveSeconds: Double,
    val toiSpecialSeconds: Double,
) {
    fun toiMinutes(): Double = toiSeconds / 60.0

    fun meetsMinToi(rules: ToiRules): Boolean = toiSeconds >= rules.minRatingToiSeconds()
}

data class ToiSummary(
    val nSkaters: Int,
    val nWithMinToi: Int,
    val nAvangardSkaters: Int,
    val medianToiMinutes: Double,
    val p90ToiMinutes: Double,
)

data class ToiReport(
    val rules: ToiRules,
    val players: List<PlayerToi>,
    val summary: ToiSummary,
)
