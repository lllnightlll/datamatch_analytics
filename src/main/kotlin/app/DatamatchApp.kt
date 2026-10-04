package app

import app.api.InventoryHttpServer
import app.config.AppConfig
import app.data.DuckDbSession
import app.data.EventCatalogRepository
import app.data.GameRepository
import app.data.PlayerRepository
import app.data.RinkGeometryRepository
import app.data.PuckEventRepository
import app.data.ShiftEventRepository
import app.data.OutcomeEventRepository
import app.data.ShotEventRepository
import app.domain.catalog.EventFamilyClassifier
import app.domain.rating.MatchBootstrap
import app.domain.rating.OfficialRatingsExporter
import app.domain.rating.OnIceStintBuilder
import app.domain.rating.RidgeRapm
import app.domain.rating.StintActionAggregator
import app.domain.toi.ToiReconstructor
import app.domain.validate.MatchHalfSplitter
import app.domain.validate.PlusMinusAssigner
import app.service.ActionValueService
import app.service.DatasetInventoryService
import app.service.DeliverableService
import app.service.RatingService
import app.service.ToiService
import app.service.ValidationService
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
        val threatRules = ActionValueService.loadRules()
        val ratingRules = RatingService.loadRules()
        val validationRules = ValidationService.loadRules()
        val playerRepository = PlayerRepository(session, config.datasetPaths)
        val gameRepository = GameRepository(session, config.datasetPaths)
        val shiftEventRepository = ShiftEventRepository(session, config.datasetPaths, toiRules)
        val rinkGeometryRepository = RinkGeometryRepository(session, config.datasetPaths)
        val inventoryService = DatasetInventoryService(
            gameRepository = gameRepository,
            playerRepository = playerRepository,
            eventCatalogRepository = EventCatalogRepository(session, config.datasetPaths, classifier),
            rinkGeometryRepository = rinkGeometryRepository,
        )
        val toiService = ToiService(
            shiftEventRepository = shiftEventRepository,
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
        val actionValueService = ActionValueService(
            puckEventRepository = PuckEventRepository(session, config.datasetPaths, threatRules),
            playerRepository = playerRepository,
            inventoryService = inventoryService,
            rules = threatRules,
        )
        val stintBuilder = OnIceStintBuilder(toiRules)
        val aggregator = StintActionAggregator(ratingRules)
        val ridge = RidgeRapm(ratingRules)
        val ratingService = RatingService(
            actionValueService = actionValueService,
            toiService = toiService,
            shiftEventRepository = shiftEventRepository,
            gameRepository = gameRepository,
            stintBuilder = stintBuilder,
            aggregator = aggregator,
            ridge = ridge,
            bootstrap = MatchBootstrap(ratingRules),
            ratingRules = ratingRules,
            toiRules = toiRules,
        )
        val validationService = ValidationService(
            actionValueService = actionValueService,
            xgService = xgService,
            gameRepository = gameRepository,
            shiftEventRepository = shiftEventRepository,
            outcomeEventRepository = OutcomeEventRepository(session, config.datasetPaths, validationRules),
            playerRepository = playerRepository,
            stintBuilder = stintBuilder,
            aggregator = aggregator,
            ridge = ridge,
            plusMinusAssigner = PlusMinusAssigner(),
            splitter = MatchHalfSplitter(),
            ratingRules = ratingRules,
            rules = validationRules,
        )
        val deliverableRules = DeliverableService.loadRules()
        val deliverableService = DeliverableService(
            ratingService = ratingService,
            exporter = OfficialRatingsExporter(deliverableRules),
            rules = deliverableRules,
            outputPath = DeliverableService.resolvePath(config.projectRoot, deliverableRules),
        )
        InventoryHttpServer(
            config,
            inventoryService,
            toiService,
            xgService,
            actionValueService,
            ratingService,
            validationService,
            deliverableService,
        ).start(wait = true)
    }
}
