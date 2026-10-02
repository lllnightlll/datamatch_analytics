package app.api.dto

import app.domain.catalog.EventTypeStat
import app.domain.catalog.FamilyTotal
import app.service.DatasetInventory
import app.service.SplitInventory
import kotlinx.serialization.Serializable

@Serializable
data class HealthDto(
    val status: String,
    val step: String,
)

@Serializable
data class ErrorDto(
    val error: String,
)

@Serializable
data class InventoryResponse(
    val train: SplitDto,
    val test: SplitDto,
    val nSharedEventTypes: Int,
    val nTrainOnlyEventTypes: Int,
    val nTestOnlyEventTypes: Int,
    val families: List<FamilyDto>,
    val eventTypes: List<EventTypeDto>,
)

@Serializable
data class SplitDto(
    val kind: String,
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

@Serializable
data class FamilyDto(
    val family: String,
    val trainCount: Long,
    val testCount: Long,
    val nTypes: Int,
    val nSharedTypes: Int,
)

@Serializable
data class EventTypeDto(
    val eventType: String,
    val family: String,
    val trainCount: Long,
    val testCount: Long,
    val status: String,
)

object InventoryMapper {
    fun toDto(inventory: DatasetInventory): InventoryResponse {
        return InventoryResponse(
            train = splitDto(inventory.train),
            test = splitDto(inventory.test),
            nSharedEventTypes = inventory.nSharedEventTypes,
            nTrainOnlyEventTypes = inventory.nTrainOnlyEventTypes,
            nTestOnlyEventTypes = inventory.nTestOnlyEventTypes,
            families = inventory.familyTotals.map { it.toDto() },
            eventTypes = inventory.eventCatalog.rows.map { it.toDto() },
        )
    }

    fun toCsv(inventory: DatasetInventory): String {
        val header = "event_type,family,train_count,test_count,status"
        val lines = inventory.eventCatalog.rows.map { row ->
            val status = statusOf(row)
            "${row.eventType},${row.family},${row.trainCount},${row.testCount},$status"
        }
        return (listOf(header) + lines).joinToString("\n") + "\n"
    }

    private fun splitDto(split: SplitInventory): SplitDto {
        return SplitDto(
            kind = split.kind.name,
            nGames = split.nGames,
            nEvents = split.nEvents,
            nRosterPlayers = split.nRosterPlayers,
            nRosterSkaters = split.nRosterSkaters,
            nRosterGoalies = split.nRosterGoalies,
            nEventActors = split.nEventActors,
            nEventSkaters = split.nEventSkaters,
            nEventGoalies = split.nEventGoalies,
            rinkTypeCounts = split.rinkTypeCounts,
            stageCounts = split.stageCounts,
            positionRosterCounts = split.positionRosterCounts,
        )
    }

    private fun FamilyTotal.toDto(): FamilyDto {
        return FamilyDto(
            family = family,
            trainCount = trainCount,
            testCount = testCount,
            nTypes = nTypes,
            nSharedTypes = nSharedTypes,
        )
    }

    private fun EventTypeStat.toDto(): EventTypeDto {
        return EventTypeDto(
            eventType = eventType,
            family = family,
            trainCount = trainCount,
            testCount = testCount,
            status = statusOf(this),
        )
    }

    private fun statusOf(row: EventTypeStat): String {
        return when {
            row.isShared -> "shared"
            row.trainOnly -> "train_only"
            else -> "test_only"
        }
    }
}
