package app.domain.threat

import kotlinx.serialization.Serializable

@Serializable
enum class ActionKind {
    MOVE,
    GAIN,
    LOSS,
    VS_NEUTRAL,
    VS_OWN,
}

@Serializable
data class ActionRule(
    val eventType: String,
    val kind: ActionKind,
    val family: String,
)

@Serializable
data class ThreatRules(
    val cellSizeM: Double,
    val xMin: Double,
    val xMax: Double,
    val yMin: Double,
    val yMax: Double,
    val lookaheadEvents: Int,
    val minCellCount: Int,
    val priorStrength: Double,
    val ignoredPeriods: Set<Int>,
    val shotTypes: Set<String>,
    val locationTypes: Set<String>,
    val neutralXAbs: Double,
    val actions: List<ActionRule>,
) {
    fun isIgnoredPeriod(period: Int): Boolean = period in ignoredPeriods

    fun isShot(eventType: String): Boolean = eventType in shotTypes

    fun isLocation(eventType: String): Boolean = eventType in locationTypes

    fun actionOf(eventType: String): ActionRule? = actions.firstOrNull { it.eventType == eventType }

    fun cols(): Int = ((xMax - xMin) / cellSizeM).toInt()

    fun rows(): Int = ((yMax - yMin) / cellSizeM).toInt()

    fun cellIndex(x: Double, y: Double): Int? {
        if (x < xMin || x >= xMax || y < yMin || y >= yMax) return null
        val col = ((x - xMin) / cellSizeM).toInt()
        val row = ((y - yMin) / cellSizeM).toInt()
        val width = cols()
        val height = rows()
        if (col !in 0 until width || row !in 0 until height) return null
        return row * width + col
    }
}

data class PuckEvent(
    val matchId: String,
    val seq: Int,
    val eventType: String,
    val actorId: String?,
    val actorTeamId: String?,
    val x: Double,
    val y: Double,
    val x2: Double?,
    val y2: Double?,
    val period: Int,
)

data class ActionCredit(
    val playerId: String,
    val eventType: String,
    val family: String,
    val value: Double,
)

data class StampedCredit(
    val matchId: String,
    val seq: Int,
    val playerId: String,
    val teamId: String?,
    val eventType: String,
    val family: String,
    val value: Double,
)

data class PlayerActionValue(
    val playerId: String,
    val positionName: String,
    val nActions: Int,
    val totalValue: Double,
    val byFamily: Map<String, Double>,
)

data class FamilyValue(
    val family: String,
    val nActions: Int,
    val totalValue: Double,
)

data class ThreatReport(
    val rules: ThreatRules,
    val globalRate: Double,
    val nObservations: Int,
    val heatmap: List<List<Double>>,
    val families: List<FamilyValue>,
    val players: List<PlayerActionValue>,
)
