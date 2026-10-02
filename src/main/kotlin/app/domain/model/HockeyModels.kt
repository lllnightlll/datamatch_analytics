package app.domain.model

enum class Position {
    C, LW, RW, D, G, UNKNOWN;

    fun isSkater(): Boolean = this == C || this == LW || this == RW || this == D

    companion object {
        private val SLOT_TO_POSITION = mapOf(
            "C" to C,
            "LW" to LW,
            "RW" to RW,
            "LD" to D,
            "RD" to D,
            "G" to G,
        )

        fun fromRaw(raw: String): Position {
            val key = raw.trim().uppercase()
            return entries.firstOrNull { it.name == key && it != UNKNOWN }
                ?: error("Неизвестное амплуа: '$raw'")
        }

        fun infer(rawPosition: String?, lineupSlotMode: String?): Position {
            val fromPosition = rawPosition?.trim()?.uppercase().orEmpty()
            if (fromPosition.isNotEmpty()) {
                return fromRaw(fromPosition)
            }
            val slot = lineupSlotMode?.trim()?.uppercase().orEmpty()
            return SLOT_TO_POSITION[slot] ?: UNKNOWN
        }
    }
}

enum class DatasetKind {
    TRAIN, TEST
}

data class Game(
    val matchId: String,
    val stage: String,
    val homeTeamId: String,
    val awayTeamId: String,
    val homeGoals: Int?,
    val awayGoals: Int?,
    val rinkType: String,
    val nEvents: Int,
    val league: String?,
    val season: String?,
    val gameSeq: Int?,
)

data class Player(
    val playerId: String,
    val position: Position,
    val lineupSlotMode: String?,
    val shoots: String?,
)

data class Team(
    val teamId: String,
    val gamesInSample: Int,
)

data class RinkLandmark(
    val rinkType: String,
    val objectName: String,
    val kind: String,
    val x: Double?,
    val y: Double?,
)
