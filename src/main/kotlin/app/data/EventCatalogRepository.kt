package app.data

import app.config.DatasetPaths
import app.domain.catalog.EventFamilyClassifier
import app.domain.catalog.EventTypeCatalog
import app.domain.catalog.EventTypeStat
import app.domain.model.Position

class EventCatalogRepository(
    private val session: DuckDbSession,
    private val paths: DatasetPaths,
    private val familyClassifier: EventFamilyClassifier,
) {
    fun loadCatalog(): EventTypeCatalog {
        val train = countByType(paths.trainEvents)
        val test = countByType(paths.testEvents)
        val allTypes = (train.keys + test.keys).sorted()
        val stats = allTypes.map { type ->
            EventTypeStat(
                eventType = type,
                family = familyClassifier.classify(type),
                trainCount = train[type] ?: 0L,
                testCount = test[type] ?: 0L,
            )
        }
        return EventTypeCatalog(stats)
    }

    fun countEvents(isTest: Boolean): Long {
        val file = if (isTest) paths.testEvents else paths.trainEvents
        val sql = "SELECT COUNT(*) AS n FROM read_parquet(${DuckDbSession.quotePath(file)})"
        return session.querySingle(sql) { it.getLong("n") }
    }

    fun countActorsByPosition(isTest: Boolean, roster: List<app.domain.model.Player>): Map<Position, Int> {
        val file = if (isTest) paths.testEvents else paths.trainEvents
        val sql = """
            SELECT DISTINCT actor_id
            FROM read_parquet(${DuckDbSession.quotePath(file)})
            WHERE actor_id IS NOT NULL
        """.trimIndent()
        val ids = session.query(sql) { it.getNullableString("actor_id") }
            .filterNotNull()
            .toSet()
        val byId = roster.associateBy { it.playerId }
        val counts = mutableMapOf<Position, Int>()
        for (id in ids) {
            val player = byId[id] ?: continue
            counts[player.position] = (counts[player.position] ?: 0) + 1
        }
        return counts
    }

    private fun countByType(file: java.nio.file.Path): Map<String, Long> {
        val sql = """
            SELECT event_type, COUNT(*) AS n
            FROM read_parquet(${DuckDbSession.quotePath(file)})
            GROUP BY event_type
        """.trimIndent()
        return session.query(sql) { rs ->
            rs.requireString("event_type") to rs.getLong("n")
        }.toMap()
    }
}
