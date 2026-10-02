package app.domain.catalog

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream

@Serializable
data class EventFamilyRule(
    val family: String,
    val prefixes: List<String> = emptyList(),
    val exact: List<String> = emptyList(),
)

@Serializable
data class EventFamilyConfigFile(
    val rules: List<EventFamilyRule>,
)

class EventFamilyClassifier(private val rules: List<EventFamilyRule>) {

    fun classify(eventType: String): String {
        for ((family, prefixes, exact) in rules) {
            if (eventType in exact) return family
            if (prefixes.any { eventType.startsWith(it) }) return family
        }
        return OTHER_FAMILY
    }

    companion object {
        const val OTHER_FAMILY: String = "other"

        fun fromResource(
            resourceName: String = "event-families.json",
            classLoader: ClassLoader = EventFamilyClassifier::class.java.classLoader,
        ): EventFamilyClassifier {
            val stream: InputStream = classLoader.getResourceAsStream(resourceName)
                ?: error("Не найден ресурс $resourceName")
            val json = stream.bufferedReader().use { it.readText() }
            val parsed = Json.decodeFromString<EventFamilyConfigFile>(json)
            require(parsed.rules.isNotEmpty()) { "Список семейств событий пуст" }
            return EventFamilyClassifier(parsed.rules)
        }
    }
}

data class EventTypeStat(
    val eventType: String,
    val family: String,
    val trainCount: Long,
    val testCount: Long,
) {
    val isShared: Boolean get() = trainCount > 0L && testCount > 0L
    val trainOnly: Boolean get() = trainCount > 0L && testCount == 0L
    val testOnly: Boolean get() = testCount > 0L && trainCount == 0L
}

class EventTypeCatalog(stats: List<EventTypeStat>) {
    val rows: List<EventTypeStat> = stats.sortedWith(
        compareByDescending<EventTypeStat> { it.trainCount + it.testCount }
            .thenBy { it.eventType },
    )

    fun sharedTypes(): Set<String> = rows.filter { it.isShared }.map { it.eventType }.toSet()

    fun trainOnlyTypes(): List<EventTypeStat> = rows.filter { it.trainOnly }

    fun testOnlyTypes(): List<EventTypeStat> = rows.filter { it.testOnly }

    fun familyTotals(): List<FamilyTotal> {
        return rows.groupBy { it.family }.map { (family, group) ->
            FamilyTotal(
                family = family,
                trainCount = group.sumOf { it.trainCount },
                testCount = group.sumOf { it.testCount },
                nTypes = group.size,
                nSharedTypes = group.count { it.isShared },
            )
        }.sortedByDescending { it.trainCount }
    }
}

data class FamilyTotal(
    val family: String,
    val trainCount: Long,
    val testCount: Long,
    val nTypes: Int,
    val nSharedTypes: Int,
)

class SharedEventPolicy(private val catalog: EventTypeCatalog) {
    private val eligible: Set<String> = catalog.sharedTypes()

    fun isEligible(eventType: String): Boolean = eventType in eligible

    fun eligibleTypes(): Set<String> = eligible
}
