package app.domain.rating

import kotlinx.serialization.Serializable

@Serializable
data class RatingRules(
    val ridgeLambda: Double,
    val minStintSeconds: Double,
    val bootstrapDraws: Int,
    val bootstrapSeed: Long,
    val loQuantile: Double,
    val hiQuantile: Double,
    val shrinkPriorMinutes: Double,
    val rapmFiveOnFiveOnly: Boolean,
    val includeRinkEffects: Boolean,
    val minBootstrapHits: Int,
) {
    fun shrink(value: Double, toiMinutes: Double): Double {
        val safeToi = toiMinutes.coerceAtLeast(0.0)
        val weight = safeToi / (safeToi + shrinkPriorMinutes.coerceAtLeast(0.0))
        return weight * value
    }

    fun per60(totalValue: Double, seconds: Double): Double {
        if (seconds <= 0.0) return 0.0
        return totalValue * 3600.0 / seconds
    }
}

data class IceStint(
    val matchId: String,
    val startSeq: Int,
    val endSeq: Int,
    val durationSeconds: Double,
    val playersByTeam: Map<String, Set<String>>,
    val nHome: Int,
    val nAway: Int,
    val fiveOnFive: Boolean,
    val rinkType: String,
    val homeTeamId: String,
    val awayTeamId: String,
) {
    fun homePlayers(): Set<String> = playersByTeam[homeTeamId].orEmpty()

    fun awayPlayers(): Set<String> = playersByTeam[awayTeamId].orEmpty()

    fun containsSeq(seq: Int): Boolean = seq > startSeq && seq <= endSeq
}

data class WeightedStint(
    val matchId: String,
    val y: Double,
    val weight: Double,
    val plusPlayerIds: List<String>,
    val minusPlayerIds: List<String>,
    val rinkType: String,
)

data class PlayerMatchValue(
    val playerId: String,
    val matchId: String,
    val actionValue: Double,
    val evValue: Double,
    val specialValue: Double,
    val nActions: Int,
)

data class RapmFit(
    val intercept: Double,
    val playerEffects: Map<String, Double>,
    val rinkEffects: Map<String, Double>,
    val nStints: Int,
    val nPlayers: Int,
)

data class PlayerRating(
    val playerId: String,
    val positionName: String,
    val primaryTeamId: String?,
    val playedForAvangard: Boolean,
    val nGames: Int,
    val nActions: Int,
    val toiSeconds: Double,
    val toiFiveOnFiveSeconds: Double,
    val toiSpecialSeconds: Double,
    val rawPer60: Double,
    val evPer60: Double,
    val specialPer60: Double,
    val rapm: Double,
    val rating: Double,
    val lo: Double,
    val hi: Double,
    val nBootstrapHits: Int,
    val meetsMinToi: Boolean,
) {
    fun toiMinutes(): Double = toiSeconds / 60.0
}

data class RatingReport(
    val rules: RatingRules,
    val minToiMinutes: Double,
    val avangardTeamId: String,
    val nStints: Int,
    val nRapmStints: Int,
    val nRated: Int,
    val nWithMinToi: Int,
    val nAvangard: Int,
    val medianRating: Double,
    val players: List<PlayerRating>,
)
