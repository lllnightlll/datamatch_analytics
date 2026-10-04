package app.api.dto

import app.service.DeliverableReport
import kotlinx.serialization.Serializable

@Serializable
data class DeliverableResponse(
    val fileName: String,
    val path: String,
    val nRows: Int,
    val nAvangard: Int,
    val minToiMinutes: Double,
    val columns: List<String>,
    val rows: List<OfficialRowDto>,
)

@Serializable
data class OfficialRowDto(
    val playerId: String,
    val rating: Double,
    val lo: Double,
    val hi: Double,
)

object DeliverableMapper {
    private val COLUMNS = listOf("player_id", "rating", "lo", "hi")

    fun toDto(report: DeliverableReport): DeliverableResponse {
        return DeliverableResponse(
            fileName = report.rules.fileName,
            path = report.path.toString(),
            nRows = report.nRows,
            nAvangard = report.nAvangard,
            minToiMinutes = report.minToiMinutes,
            columns = COLUMNS,
            rows = report.rows.map {
                OfficialRowDto(
                    playerId = it.playerId,
                    rating = round4(it.rating),
                    lo = round4(it.lo),
                    hi = round4(it.hi),
                )
            },
        )
    }

    private fun round4(value: Double): Double = kotlin.math.round(value * 10000.0) / 10000.0
}
