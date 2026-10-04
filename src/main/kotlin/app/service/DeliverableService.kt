package app.service

import app.domain.rating.DeliverableRules
import app.domain.rating.OfficialRatingRow
import app.domain.rating.OfficialRatingsExporter
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

data class DeliverableReport(
    val rules: DeliverableRules,
    val path: Path,
    val nRows: Int,
    val nAvangard: Int,
    val minToiMinutes: Double,
    val rows: List<OfficialRatingRow>,
)

/**
 * Пишет test_ratings.csv в корень репозитория — это файл сдачи, не превью.
 */
class DeliverableService(
    private val ratingService: RatingService,
    private val exporter: OfficialRatingsExporter,
    private val rules: DeliverableRules,
    private val outputPath: Path,
) {
    private val logger = LoggerFactory.getLogger(DeliverableService::class.java)
    private val cached: DeliverableReport by lazy { build() }

    fun get(): DeliverableReport = cached

    fun csv(): String = exporter.toCsv(ratingService.get())

    private fun build(): DeliverableReport {
        val report = ratingService.get()
        val rows = exporter.rows(report)
        val text = exporter.toCsv(report)
        Files.writeString(outputPath, text)
        val avangard = report.players.count { it.meetsMinToi && it.playedForAvangard }
        logger.info("Сдан файл {}: строк={}, T113={}", outputPath.fileName, rows.size, avangard)
        return DeliverableReport(
            rules = rules,
            path = outputPath,
            nRows = rows.size,
            nAvangard = avangard,
            minToiMinutes = report.minToiMinutes,
            rows = rows,
        )
    }

    companion object {
        fun loadRules(
            resourceName: String = "deliverable-rules.json",
            classLoader: ClassLoader = DeliverableService::class.java.classLoader,
        ): DeliverableRules {
            val json = classLoader.getResourceAsStream(resourceName)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Не найден ресурс $resourceName")
            return Json.decodeFromString<DeliverableRules>(json)
        }

        fun resolvePath(projectRoot: Path, rules: DeliverableRules): Path {
            val fromEnv = System.getenv("DATAMATCH_RATINGS_CSV")
            if (!fromEnv.isNullOrBlank()) {
                return Path.of(fromEnv).toAbsolutePath().normalize()
            }
            return projectRoot.resolve(rules.fileName).toAbsolutePath().normalize()
        }
    }
}
