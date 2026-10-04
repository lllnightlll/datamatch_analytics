package app.service

import app.data.PlayerRepository
import app.data.PuckEventRepository
import app.domain.threat.ActionCredit
import app.domain.threat.ActionValuator
import app.domain.threat.FamilyValue
import app.domain.threat.PlayerActionValue
import app.domain.threat.PuckEvent
import app.domain.threat.ShotLookahead
import app.domain.threat.StampedCredit
import app.domain.threat.ThreatGrid
import app.domain.threat.ThreatGridBuilder
import app.domain.threat.ThreatReport
import app.domain.threat.ThreatRules
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class ActionValueService(
    private val puckEventRepository: PuckEventRepository,
    private val playerRepository: PlayerRepository,
    private val inventoryService: DatasetInventoryService,
    private val rules: ThreatRules,
) {
    private val logger = LoggerFactory.getLogger(ActionValueService::class.java)
    private val cached: ThreatFit by lazy { build() }

    fun get(): ThreatReport = cached.report

    fun testCredits(): List<StampedCredit> = cached.testCredits

    fun trainCredits(): List<StampedCredit> = cached.trainCredits

    private fun build(): ThreatFit {
        val started = System.currentTimeMillis()
        val sharedTypes = inventoryService.get().sharedPolicy.eligibleTypes()
        val grid = fitGrid(sharedTypes)
        val valuator = ActionValuator(grid, rules)
        val playerTotals = LinkedHashMap<String, PlayerAccumulator>()
        val familyTotals = LinkedHashMap<String, FamilyAccumulator>()
        val testCredits = mutableListOf<StampedCredit>()
        val trainCredits = mutableListOf<StampedCredit>()
        rules.actions.map { it.family }.distinct().forEach { family ->
            familyTotals[family] = FamilyAccumulator()
        }

        puckEventRepository.forEachMatch(isTest = false) { events ->
            creditMatch(events, valuator, sharedTypes, null, null, trainCredits)
        }
        puckEventRepository.forEachMatch(isTest = true) { events ->
            creditMatch(events, valuator, sharedTypes, playerTotals, familyTotals, testCredits)
        }

        val roster = playerRepository.loadCombinedRoster().associateBy { it.playerId }
        val players = playerTotals.mapNotNull { (playerId, acc) ->
            val player = roster[playerId] ?: return@mapNotNull null
            if (!player.position.isSkater()) return@mapNotNull null
            PlayerActionValue(
                playerId = playerId,
                positionName = player.position.name,
                nActions = acc.nActions,
                totalValue = acc.total,
                byFamily = acc.byFamily.toMap(),
            )
        }.sortedByDescending { it.totalValue }

        val report = ThreatReport(
            rules = rules,
            globalRate = grid.globalRate,
            nObservations = grid.nObservations,
            heatmap = grid.heatmap(),
            families = familyTotals.map { (family, acc) ->
                FamilyValue(family, acc.nActions, acc.total)
            },
            players = players,
        )
        logger.info(
            "Угроза: obs={}, rate={}, test skaters={}, {} мс",
            report.nObservations,
            "%.3f".format(report.globalRate),
            players.size,
            System.currentTimeMillis() - started,
        )
        return ThreatFit(report, testCredits, trainCredits)
    }

    private fun fitGrid(sharedTypes: Set<String>): ThreatGrid {
        val builder = ThreatGridBuilder(rules)
        puckEventRepository.forEachMatch(isTest = false) { events ->
            events.forEachIndexed { index, event ->
                if (!rules.isLocation(event.eventType)) return@forEachIndexed
                if (event.eventType !in sharedTypes) return@forEachIndexed
                builder.add(event.x, event.y, ShotLookahead.shotSoon(events, index, rules))
            }
        }
        return builder.build()
    }

    private fun creditMatch(
        events: List<PuckEvent>,
        valuator: ActionValuator,
        sharedTypes: Set<String>,
        playerTotals: MutableMap<String, PlayerAccumulator>?,
        familyTotals: MutableMap<String, FamilyAccumulator>?,
        sink: MutableList<StampedCredit>,
    ) {
        for (event in events) {
            val credit = valuator.evaluate(event, sharedTypes) ?: continue
            playerTotals?.getOrPut(credit.playerId) { PlayerAccumulator() }?.add(credit)
            familyTotals?.getOrPut(credit.family) { FamilyAccumulator() }?.add(credit)
            sink += StampedCredit(
                matchId = event.matchId,
                seq = event.seq,
                playerId = credit.playerId,
                teamId = event.actorTeamId,
                eventType = credit.eventType,
                family = credit.family,
                value = credit.value,
            )
        }
    }

    private data class ThreatFit(
        val report: ThreatReport,
        val testCredits: List<StampedCredit>,
        val trainCredits: List<StampedCredit>,
    )

    private class PlayerAccumulator {
        var nActions: Int = 0
        var total: Double = 0.0
        val byFamily: MutableMap<String, Double> = linkedMapOf()

        fun add(credit: ActionCredit) {
            nActions += 1
            total += credit.value
            byFamily[credit.family] = (byFamily[credit.family] ?: 0.0) + credit.value
        }
    }

    private class FamilyAccumulator {
        var nActions: Int = 0
        var total: Double = 0.0

        fun add(credit: ActionCredit) {
            nActions += 1
            total += credit.value
        }
    }

    companion object {
        fun loadRules(
            resourceName: String = "threat-rules.json",
            classLoader: ClassLoader = ActionValueService::class.java.classLoader,
        ): ThreatRules {
            val json = classLoader.getResourceAsStream(resourceName)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Не найден ресурс $resourceName")
            return Json.decodeFromString<ThreatRules>(json)
        }
    }
}
