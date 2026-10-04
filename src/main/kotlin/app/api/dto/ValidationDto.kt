package app.api.dto

import app.domain.validate.ValidationReport
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class ValidationResponse(
    val nFitMatches: Int,
    val nOutMatches: Int,
    val nPlayers: Int,
    val minHalfToiMinutes: Double,
    val correlations: List<CorrelationDto>,
    val players: List<ValidationPlayerDto>,
)

@Serializable
data class CorrelationDto(
    val predictor: String,
    val outcome: String,
    val pearson: Double,
    val spearman: Double,
    val n: Int,
)

@Serializable
data class ValidationPlayerDto(
    val playerId: String,
    val position: String,
    val rating: Double,
    val rawPer60: Double,
    val fitGaPer60: Double,
    val outGaPer60: Double,
    val outXgPer60: Double,
    val outAvPer60: Double,
    val fitToiMin: Double,
    val outToiMin: Double,
    val fitGames: Int,
    val outGames: Int,
)

object ValidationMapper {
    private const val TOP_PLAYERS = 80

    fun toDto(report: ValidationReport): ValidationResponse {
        return ValidationResponse(
            nFitMatches = report.nFitMatches,
            nOutMatches = report.nOutMatches,
            nPlayers = report.nPlayers,
            minHalfToiMinutes = report.rules.minHalfToiMinutes,
            correlations = report.correlations.map {
                CorrelationDto(it.predictor, it.outcome, round3(it.pearson), round3(it.spearman), it.n)
            },
            players = report.players.take(TOP_PLAYERS).map { row ->
                ValidationPlayerDto(
                    playerId = row.playerId,
                    position = row.positionName,
                    rating = round3(row.rating),
                    rawPer60 = round3(row.rawPer60),
                    fitGaPer60 = round3(row.fit.per60(row.fit.ga().toDouble())),
                    outGaPer60 = round3(row.out.per60(row.out.ga().toDouble())),
                    outXgPer60 = round3(row.out.per60(row.out.sumXg)),
                    outAvPer60 = round3(row.out.per60(row.out.actionValue)),
                    fitToiMin = round1(row.fit.toiMinutes()),
                    outToiMin = round1(row.out.toiMinutes()),
                    fitGames = row.fit.nGames,
                    outGames = row.out.nGames,
                )
            },
        )
    }

    fun toCsv(report: ValidationReport): String {
        val header = "predictor,outcome,pearson,spearman,n"
        val lines = report.correlations.map { row ->
            listOf(
                row.predictor,
                row.outcome,
                fmt(row.pearson),
                fmt(row.spearman),
                row.n,
            ).joinToString(",")
        }
        return (listOf(header) + lines).joinToString("\n") + "\n"
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.4f", value)

    private fun round1(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0

    private fun round3(value: Double): Double = kotlin.math.round(value * 1000.0) / 1000.0
}
