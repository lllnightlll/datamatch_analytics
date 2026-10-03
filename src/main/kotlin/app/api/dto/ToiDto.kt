package app.api.dto

import app.domain.toi.PlayerToi
import app.domain.toi.ToiReport
import app.domain.toi.ToiRules
import kotlinx.serialization.Serializable

@Serializable
data class ToiResponse(
    val split: String,
    val minToiMinutes: Double,
    val avangardTeamId: String,
    val summary: ToiSummaryDto,
    val players: List<ToiPlayerDto>,
)

@Serializable
data class ToiSummaryDto(
    val nSkaters: Int,
    val nWithMinToi: Int,
    val nAvangardSkaters: Int,
    val medianToiMinutes: Double,
    val p90ToiMinutes: Double,
)

@Serializable
data class ToiPlayerDto(
    val playerId: String,
    val position: String,
    val primaryTeamId: String?,
    val playedForAvangard: Boolean,
    val nGames: Int,
    val toiMin: Double,
    val toi5v5Min: Double,
    val toiSpecialMin: Double,
    val meetsMinToi: Boolean,
)

object ToiMapper {
    fun toDto(split: String, report: ToiReport): ToiResponse {
        return ToiResponse(
            split = split,
            minToiMinutes = report.rules.minRatingToiMinutes,
            avangardTeamId = report.rules.avangardTeamId,
            summary = ToiSummaryDto(
                nSkaters = report.summary.nSkaters,
                nWithMinToi = report.summary.nWithMinToi,
                nAvangardSkaters = report.summary.nAvangardSkaters,
                medianToiMinutes = report.summary.medianToiMinutes,
                p90ToiMinutes = report.summary.p90ToiMinutes,
            ),
            players = report.players.map { it.toDto(report.rules) },
        )
    }

    fun toCsv(report: ToiReport): String {
        val header = "player_id,position,primary_team_id,played_for_avangard,n_games,toi_min,toi_5v5_min,toi_special_min,meets_min_toi"
        val lines = report.players.map { row ->
            listOf(
                row.playerId,
                row.positionName,
                row.primaryTeamId ?: "",
                row.playedForAvangard,
                row.nGames,
                formatMinutes(row.toiMinutes()),
                formatMinutes(row.toiFiveOnFiveSeconds / 60.0),
                formatMinutes(row.toiSpecialSeconds / 60.0),
                row.meetsMinToi(report.rules),
            ).joinToString(",")
        }
        return (listOf(header) + lines).joinToString("\n") + "\n"
    }

    private fun PlayerToi.toDto(rules: ToiRules): ToiPlayerDto {
        return ToiPlayerDto(
            playerId = playerId,
            position = positionName,
            primaryTeamId = primaryTeamId,
            playedForAvangard = playedForAvangard,
            nGames = nGames,
            toiMin = round1(toiMinutes()),
            toi5v5Min = round1(toiFiveOnFiveSeconds / 60.0),
            toiSpecialMin = round1(toiSpecialSeconds / 60.0),
            meetsMinToi = meetsMinToi(rules),
        )
    }

    private fun formatMinutes(value: Double): String {
        return String.format(java.util.Locale.US, "%.2f", value)
    }

    private fun round1(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0
}
