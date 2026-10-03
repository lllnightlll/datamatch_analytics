package app

import app.api.InventoryHttpServer
import app.config.AppConfig
import app.data.DuckDbSession
import app.data.EventCatalogRepository
import app.data.GameRepository
import app.data.PlayerRepository
import app.data.RinkGeometryRepository
import app.data.ShiftEventRepository
import app.data.ShotEventRepository
import app.domain.catalog.EventFamilyClassifier
import app.domain.toi.ToiReconstructor
import app.service.DatasetInventoryService
import app.service.ToiService
import app.service.XgService
import java.util.logging.Logger

fun main() {
    val logger = Logger.getLogger("DatamatchApp")
    val config = AppConfig.load()
    logger.info("Корень проекта: ${config.projectRoot}")
    logger.info("Данные: ${config.datasetPaths.dataRoot}")

    DuckDbSession().use { session ->
        val classifier = EventFamilyClassifier.fromResource()
        val toiRules = ToiService.loadRules()
        val xgRules = XgService.loadRules()
        val playerRepository = PlayerRepository(session, config.datasetPaths)
        val rinkGeometryRepository = RinkGeometryRepository(session, config.datasetPaths)
        val inventoryService = DatasetInventoryService(
            gameRepository = GameRepository(session, config.datasetPaths),
            playerRepository = playerRepository,
            eventCatalogRepository = EventCatalogRepository(session, config.datasetPaths, classifier),
            rinkGeometryRepository = rinkGeometryRepository,
        )
        val toiService = ToiService(
            shiftEventRepository = ShiftEventRepository(session, config.datasetPaths, toiRules),
            playerRepository = playerRepository,
            rules = toiRules,
            reconstructor = ToiReconstructor(toiRules),
        )
        val xgService = XgService(
            shotEventRepository = ShotEventRepository(session, config.datasetPaths, xgRules),
            rinkGeometryRepository = rinkGeometryRepository,
            playerRepository = playerRepository,
            rules = xgRules,
        )
        InventoryHttpServer(config, inventoryService, toiService, xgService).start(wait = true)
    }
}
