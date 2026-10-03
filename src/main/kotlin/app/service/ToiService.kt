package app.service

import app.data.PlayerRepository
import app.data.ShiftEventRepository
import app.domain.model.DatasetKind
import app.domain.model.Player
import app.domain.toi.MutablePlayerToi
import app.domain.toi.PlayerToi
import app.domain.toi.ToiReconstructor
import app.domain.toi.ToiReport
import app.domain.toi.ToiRules
import app.domain.toi.ToiSummary
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class ToiService(
    private val shiftEventRepository: ShiftEventRepository,
    private val playerRepository: PlayerRepository,
    private val rules: ToiRules,
    private val reconstructor: ToiReconstructor,
) {
    private val logger = LoggerFactory.getLogger(ToiService::class.java)
    private val cache = mutableMapOf<DatasetKind, ToiReport>()

    fun rules(): ToiRules = rules

    fun get(kind: DatasetKind): ToiReport {
        return cache.getOrPut(kind) { build(kind) }
    }

    private fun build(kind: DatasetKind): ToiReport {
        val started = System.currentTimeMillis()
        val roster = when (kind) {
            DatasetKind.TRAIN -> playerRepository.loadTrainRoster()
            DatasetKind.TEST -> playerRepository.loadCombinedRoster()
        }.associateBy { it.playerId }
        val events = shiftEventRepository.loadTimeline(isTest = kind == DatasetKind.TEST)
        val ledger = reconstructor.reconstruct(events)
        val players = ledger.values.mapNotNull { acc ->
            val player = roster[acc.playerId] ?: return@mapNotNull null
            if (!player.position.isSkater()) return@mapNotNull null
            if (acc.toiSeconds <= 0.0) return@mapNotNull null
            acc.toSnapshot(player)
        }.sortedByDescending { it.toiSeconds }

        val report = ToiReport(
            rules = rules,
            players = players,
            summary = summarize(players),
        )
        logger.info(
            "ТОИ {}: игроков={}, смен+границ={}, {} мс",
            kind,
            players.size,
            events.size,
            System.currentTimeMillis() - started,
        )
        return report
    }

    private fun MutablePlayerToi.toSnapshot(player: Player): PlayerToi {
        return PlayerToi(
            playerId = playerId,
            positionName = player.position.name,
            primaryTeamId = primaryTeamId(),
            playedForAvangard = playedFor(rules.avangardTeamId),
            nGames = nGames(),
            toiSeconds = toiSeconds,
            toiFiveOnFiveSeconds = toiFiveOnFiveSeconds,
            toiSpecialSeconds = toiSpecialSeconds,
        )
    }

    private fun summarize(players: List<PlayerToi>): ToiSummary {
        val minutes = players.map { it.toiMinutes() }.sorted()
        return ToiSummary(
            nSkaters = players.size,
            nWithMinToi = players.count { it.meetsMinToi(rules) },
            nAvangardSkaters = players.count { it.playedForAvangard },
            medianToiMinutes = percentile(minutes, 0.50),
            p90ToiMinutes = percentile(minutes, 0.90),
        )
    }

    private fun percentile(sorted: List<Double>, q: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val index = ((sorted.size - 1) * q).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    companion object {
        fun loadRules(
            resourceName: String = "toi-rules.json",
            classLoader: ClassLoader = ToiService::class.java.classLoader,
        ): ToiRules {
            val json = classLoader.getResourceAsStream(resourceName)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Не найден ресурс $resourceName")
            return Json.decodeFromString<ToiRules>(json)
        }
    }
}
