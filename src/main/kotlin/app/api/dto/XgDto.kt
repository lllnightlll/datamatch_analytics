package app.api.dto

import app.domain.xg.XgReport
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class XgResponse(
    val holdout: XgMetricsDto,
    val test: XgMetricsDto,
    val coefficients: List<XgCoefficientDto>,
    val calibration: List<XgCalibrationDto>,
    val topTestPlayers: List<XgPlayerDto>,
)

@Serializable
data class XgMetricsDto(
    val nAttempts: Int,
    val nGoals: Int,
    val sumXg: Double,
    val logLoss: Double,
    val brier: Double,
)

@Serializable
data class XgCoefficientDto(
    val name: String,
    val weight: Double,
)

@Serializable
data class XgCalibrationDto(
    val bin: Int,
    val predicted: Double,
    val actual: Double,
    val n: Int,
)

@Serializable
data class XgPlayerDto(
    val playerId: String,
    val position: String,
    val nAttempts: Int,
    val nGoals: Int,
    val sumXg: Double,
)

object XgMapper {
    fun toDto(report: XgReport): XgResponse {
        return XgResponse(
            holdout = XgMetricsDto(
                nAttempts = report.holdout.nAttempts,
                nGoals = report.holdout.nGoals,
                sumXg = round3(report.holdout.sumXg),
                logLoss = round4(report.holdout.logLoss),
                brier = round4(report.holdout.brier),
            ),
            test = XgMetricsDto(
                nAttempts = report.test.nAttempts,
                nGoals = report.test.nGoals,
                sumXg = round3(report.test.sumXg),
                logLoss = round4(report.test.logLoss),
                brier = round4(report.test.brier),
            ),
            coefficients = report.coefficients.map {
                XgCoefficientDto(it.name, round4(it.weight))
            },
            calibration = report.calibration.map {
                XgCalibrationDto(
                    bin = it.binIndex,
                    predicted = round4(it.predictedMean),
                    actual = round4(it.actualRate),
                    n = it.n,
                )
            },
            topTestPlayers = report.topTestPlayers.map {
                XgPlayerDto(
                    playerId = it.playerId,
                    position = it.positionName,
                    nAttempts = it.nAttempts,
                    nGoals = it.nGoals,
                    sumXg = round2(it.sumXg),
                )
            },
        )
    }

    fun toCsv(report: XgReport): String {
        val header = "player_id,position,n_attempts,n_goals,sum_xg"
        val lines = report.topTestPlayers.map { row ->
            listOf(
                row.playerId,
                row.positionName,
                row.nAttempts,
                row.nGoals,
                String.format(Locale.US, "%.3f", row.sumXg),
            ).joinToString(",")
        }
        return (listOf(header) + lines).joinToString("\n") + "\n"
    }

    private fun round2(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0

    private fun round3(value: Double): Double = kotlin.math.round(value * 1000.0) / 1000.0

    private fun round4(value: Double): Double = kotlin.math.round(value * 10000.0) / 10000.0
}
