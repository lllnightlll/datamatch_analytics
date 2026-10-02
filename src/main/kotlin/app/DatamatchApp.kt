package app

import app.api.InventoryHttpServer
import app.config.AppConfig
import app.data.DuckDbSession
import app.data.EventCatalogRepository
import app.data.GameRepository
import app.data.PlayerRepository
import app.data.RinkGeometryRepository
import app.domain.catalog.EventFamilyClassifier
import app.service.DatasetInventoryService
import java.util.logging.Logger

/**
 * Точка входа шага 1: загрузка данных и инвентарь типов событий.
 * Следующие шаги (ТОИ, xG, RAPM) подключаются сюда же через те же репозитории.
 */
fun main() {
    val logger = Logger.getLogger("DatamatchApp")
    val config = AppConfig.load()
    logger.info("Корень проекта: ${config.projectRoot}")
    logger.info("Данные: ${config.datasetPaths.dataRoot}")

    DuckDbSession().use { session ->
        val classifier = EventFamilyClassifier.fromResource()
        val inventoryService = DatasetInventoryService(
            gameRepository = GameRepository(session, config.datasetPaths),
            playerRepository = PlayerRepository(session, config.datasetPaths),
            eventCatalogRepository = EventCatalogRepository(session, config.datasetPaths, classifier),
            rinkGeometryRepository = RinkGeometryRepository(session, config.datasetPaths),
        )
        InventoryHttpServer(config, inventoryService).start(wait = true)
    }
}
