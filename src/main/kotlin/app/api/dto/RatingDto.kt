package app.api.dto

import app.domain.rating.RatingReport
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class RatingResponse(
    val minToiMinutes: Double,
    val avangardTeamId: String,
    val nStints: Int,
    val nRapmStints: Int,
    val nRated: Int,
    val nWithMinToi: Int,
    val nAvangard: Int,
    val medianRating: Double,
    val bootstrapDraws: Int,
    val shrinkPriorMinutes: Double,
    val players: List<RatingPlayerDto>,
)

@Serializable
data class RatingPlayerDto(
    val playerId: String,
    val position: String,
    val primaryTeamId: String?,
    val playedForAvangard: Boolean,
    val nGames: Int,
    val nActions: Int,
    val toiMin: Double,
    val rawPer60: Double,
    val evPer60: Double,
    val specialPer60: Double,
    val rapm: Double,
    val rating: Double,
    val lo: Double,
    val hi: Double,
    val meetsMinToi: Boolean,
)

object RatingMapper {
    fun toDto(report: RatingReport): RatingResponse {
        return RatingResponse(
            minToiMinutes = report.minToiMinutes,
            avangardTeamId = report.avangardTeamId,
            nStints = report.nStints,
            nRapmStints = report.nRapmStints,
            nRated = report.nRated,
            nWithMinToi = report.nWithMinToi,
            nAvangard = report.nAvangard,
            medianRating = round3(report.medianRating),
            bootstrapDraws = report.rules.bootstrapDraws,
            shrinkPriorMinutes = report.rules.shrinkPriorMinutes,
            players = report.players.map { row ->
                RatingPlayerDto(
                    playerId = row.playerId,
                    position = row.positionName,
                    primaryTeamId = row.primaryTeamId,
                    playedForAvangard = row.playedForAvangard,
                    nGames = row.nGames,
                    nActions = row.nActions,
                    toiMin = round1(row.toiMinutes()),
                    rawPer60 = round3(row.rawPer60),
                    evPer60 = round3(row.evPer60),
                    specialPer60 = round3(row.specialPer60),
                    rapm = round3(row.rapm),
                    rating = round3(row.rating),
                    lo = round3(row.lo),
                    hi = round3(row.hi),
                    meetsMinToi = row.meetsMinToi,
                )
            },
        )
    }

    fun toCsv(report: RatingReport): String {
        val header = "player_id,position,primary_team_id,played_for_avangard,n_games,toi_min,raw_per60,ev_per60,special_per60,rapm,rating,lo,hi"
        val lines = report.players.filter { it.meetsMinToi }.map { row ->
            listOf(
                row.playerId,
                row.positionName,
                row.primaryTeamId ?: "",
                row.playedForAvangard,
                row.nGames,
                fmt(row.toiMinutes(), 2),
                fmt(row.rawPer60, 4),
                fmt(row.evPer60, 4),
                fmt(row.specialPer60, 4),
                fmt(row.rapm, 4),
                fmt(row.rating, 4),
                fmt(row.lo, 4),
                fmt(row.hi, 4),
            ).joinToString(",")
        }
        return (listOf(header) + lines).joinToString("\n") + "\n"
    }

    private fun fmt(value: Double, digits: Int): String {
        return String.format(Locale.US, "%.${digits}f", value)
    }

    private fun round1(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0

    private fun round3(value: Double): Double = kotlin.math.round(value * 1000.0) / 1000.0
}
