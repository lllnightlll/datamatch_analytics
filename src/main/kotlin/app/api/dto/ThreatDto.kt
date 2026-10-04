package app.api.dto

import app.domain.threat.ThreatReport
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class ThreatResponse(
    val globalRate: Double,
    val nObservations: Int,
    val lookaheadEvents: Int,
    val cellSizeM: Double,
    val xMin: Double,
    val xMax: Double,
    val yMin: Double,
    val yMax: Double,
    val heatmap: List<List<Double>>,
    val families: List<ThreatFamilyDto>,
    val topTestPlayers: List<ThreatPlayerDto>,
)

@Serializable
data class ThreatFamilyDto(
    val family: String,
    val nActions: Int,
    val totalValue: Double,
)

@Serializable
data class ThreatPlayerDto(
    val playerId: String,
    val position: String,
    val nActions: Int,
    val totalValue: Double,
    val byFamily: Map<String, Double>,
)

object ThreatMapper {
    private const val TOP_PLAYERS = 40

    fun toDto(report: ThreatReport): ThreatResponse {
        val familyNames = report.families.map { it.family }
        return ThreatResponse(
            globalRate = round4(report.globalRate),
            nObservations = report.nObservations,
            lookaheadEvents = report.rules.lookaheadEvents,
            cellSizeM = report.rules.cellSizeM,
            xMin = report.rules.xMin,
            xMax = report.rules.xMax,
            yMin = report.rules.yMin,
            yMax = report.rules.yMax,
            heatmap = report.heatmap.map { row -> row.map(::round4) },
            families = report.families.map {
                ThreatFamilyDto(it.family, it.nActions, round3(it.totalValue))
            },
            topTestPlayers = report.players.take(TOP_PLAYERS).map { row ->
                ThreatPlayerDto(
                    playerId = row.playerId,
                    position = row.positionName,
                    nActions = row.nActions,
                    totalValue = round3(row.totalValue),
                    byFamily = familyNames.associateWith { name ->
                        round3(row.byFamily[name] ?: 0.0)
                    },
                )
            },
        )
    }

    fun toCsv(report: ThreatReport): String {
        val familyNames = report.families.map { it.family }
        val header = (listOf("player_id", "position", "n_actions", "total_value") + familyNames)
            .joinToString(",")
        val lines = report.players.take(TOP_PLAYERS).map { row ->
            val core = listOf(
                row.playerId,
                row.positionName,
                row.nActions.toString(),
                fmt(row.totalValue),
            )
            val familyCols = familyNames.map { name -> fmt(row.byFamily[name] ?: 0.0) }
            (core + familyCols).joinToString(",")
        }
        return (listOf(header) + lines).joinToString("\n") + "\n"
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.4f", value)

    private fun round3(value: Double): Double = kotlin.math.round(value * 1000.0) / 1000.0

    private fun round4(value: Double): Double = kotlin.math.round(value * 10000.0) / 10000.0
}
