package app.service

import app.data.EventCatalogRepository
import app.data.GameRepository
import app.data.PlayerRepository
import app.data.RinkGeometryRepository
import app.domain.catalog.EventTypeCatalog
import app.domain.catalog.FamilyTotal
import app.domain.catalog.SharedEventPolicy
import app.domain.model.DatasetKind
import app.domain.model.Game
import app.domain.model.Player
import app.domain.model.Position

data class SplitInventory(
    val kind: DatasetKind,
    val nGames: Int,
    val nEvents: Long,
    val nRosterPlayers: Int,
    val nRosterSkaters: Int,
    val nRosterGoalies: Int,
    val nEventActors: Int,
    val nEventSkaters: Int,
    val nEventGoalies: Int,
    val rinkTypeCounts: Map<String, Int>,
    val stageCounts: Map<String, Int>,
    val positionRosterCounts: Map<String, Int>,
)

data class DatasetInventory(
    val train: SplitInventory,
    val test: SplitInventory,
    val eventCatalog: EventTypeCatalog,
    val sharedPolicy: SharedEventPolicy,
    val familyTotals: List<FamilyTotal>,
    val nSharedEventTypes: Int,
    val nTrainOnlyEventTypes: Int,
    val nTestOnlyEventTypes: Int,
)

class DatasetInventoryService(
    private val gameRepository: GameRepository,
    private val playerRepository: PlayerRepository,
    private val eventCatalogRepository: EventCatalogRepository,
    private val rinkGeometryRepository: RinkGeometryRepository,
) {
    private val cached: DatasetInventory by lazy { build() }

    fun get(): DatasetInventory = cached

    private fun build(): DatasetInventory {
        val trainGames = gameRepository.loadTrain()
        val testGames = gameRepository.loadTest()
        val trainRoster = playerRepository.loadTrainRoster()
        val combinedRoster = playerRepository.loadCombinedRoster()
        val catalog = eventCatalogRepository.loadCatalog()
        require(rinkGeometryRepository.loadAll().isNotEmpty()) { "rink_geometry.csv пуст" }

        val trainEvents = eventCatalogRepository.countEvents(isTest = false)
        val testEvents = eventCatalogRepository.countEvents(isTest = true)
        val trainActors = eventCatalogRepository.countActorsByPosition(isTest = false, trainRoster)
        val testActors = eventCatalogRepository.countActorsByPosition(isTest = true, combinedRoster)

        val policy = SharedEventPolicy(catalog)
        return DatasetInventory(
            train = toSplit(DatasetKind.TRAIN, trainGames, trainRoster, trainEvents, trainActors),
            test = toSplit(DatasetKind.TEST, testGames, combinedRoster, testEvents, testActors),
            eventCatalog = catalog,
            sharedPolicy = policy,
            familyTotals = catalog.familyTotals(),
            nSharedEventTypes = policy.eligibleTypes().size,
            nTrainOnlyEventTypes = catalog.trainOnlyTypes().size,
            nTestOnlyEventTypes = catalog.testOnlyTypes().size,
        )
    }

    private fun toSplit(
        kind: DatasetKind,
        games: List<Game>,
        roster: List<Player>,
        nEvents: Long,
        actorsByPosition: Map<Position, Int>,
    ): SplitInventory {
        val eventActors = actorsByPosition.values.sum()
        val eventSkaters = actorsByPosition.filterKeys { it.isSkater() }.values.sum()
        val eventGoalies = actorsByPosition[Position.G] ?: 0
        return SplitInventory(
            kind = kind,
            nGames = games.size,
            nEvents = nEvents,
            nRosterPlayers = roster.size,
            nRosterSkaters = roster.count { it.position.isSkater() },
            nRosterGoalies = roster.count { it.position == Position.G },
            nEventActors = eventActors,
            nEventSkaters = eventSkaters,
            nEventGoalies = eventGoalies,
            rinkTypeCounts = games.groupingBy { it.rinkType }.eachCount(),
            stageCounts = games.groupingBy { it.stage }.eachCount(),
            positionRosterCounts = roster.groupingBy { it.position.name }.eachCount(),
        )
    }
}
