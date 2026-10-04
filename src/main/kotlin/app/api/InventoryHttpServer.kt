package app.api

import app.api.dto.ErrorDto
import app.api.dto.HealthDto
import app.api.dto.InventoryMapper
import app.api.dto.DeliverableMapper
import app.api.dto.RatingMapper
import app.api.dto.ValidationMapper
import app.api.dto.ThreatMapper
import app.api.dto.ToiMapper
import app.api.dto.XgMapper
import app.config.AppConfig
import app.domain.model.DatasetKind
import app.service.ActionValueService
import app.service.DatasetInventoryService
import app.service.DeliverableService
import app.service.RatingService
import app.service.ToiService
import app.service.ValidationService
import app.service.XgService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class InventoryHttpServer(
    private val config: AppConfig,
    private val inventoryService: DatasetInventoryService,
    private val toiService: ToiService,
    private val xgService: XgService,
    private val actionValueService: ActionValueService,
    private val ratingService: RatingService,
    private val validationService: ValidationService,
    private val deliverableService: DeliverableService,
) {
    private val logger = LoggerFactory.getLogger(InventoryHttpServer::class.java)

    fun start(wait: Boolean = true) {
        val inventory = inventoryService.get()
        val testToi = toiService.get(DatasetKind.TEST)
        val xg = xgService.get()
        val threat = actionValueService.get()
        val ratings = ratingService.get()
        val validation = validationService.get()
        val deliverable = deliverableService.get()
        logger.info(
            "Инвентарь готов: train games={}, test games={}, shared event types={}",
            inventory.train.nGames,
            inventory.test.nGames,
            inventory.nSharedEventTypes,
        )
        logger.info(
            "ТОИ теста: полевых={}, с порогом {} мин: {}",
            testToi.summary.nSkaters,
            testToi.rules.minRatingToiMinutes,
            testToi.summary.nWithMinToi,
        )
        logger.info(
            "xG: holdout logloss={}, test goals={}, test xG={}",
            xg.holdout.logLoss,
            xg.test.nGoals,
            xg.test.sumXg,
        )
        logger.info(
            "Угроза: obs={}, rate={}, test skaters={}",
            threat.nObservations,
            threat.globalRate,
            threat.players.size,
        )
        logger.info(
            "Рейтинг: minTOI={}, median={}, avangard={}",
            ratings.nWithMinToi,
            ratings.medianRating,
            ratings.nAvangard,
        )
        logger.info(
            "Split-half: players={}, correlations={}",
            validation.nPlayers,
            validation.correlations.size,
        )
        logger.info(
            "Сдача: {} строк={}, T113={}",
            deliverable.path.fileName,
            deliverable.nRows,
            deliverable.nAvangard,
        )
        logger.info("Откройте http://127.0.0.1:{}", config.httpPort)
        embeddedServer(Netty, port = config.httpPort, host = "127.0.0.1") {
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        encodeDefaults = true
                    },
                )
            }
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
            }
            install(CallLogging)
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    logger.error("Ошибка запроса", cause)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorDto(error = cause.message ?: cause.javaClass.simpleName),
                    )
                }
            }
            routing {
                get("/api/health") {
                    call.respond(HealthDto(status = "ok", step = "7-csv"))
                }
                get("/api/test-ratings") {
                    call.respond(DeliverableMapper.toDto(deliverableService.get()))
                }
                get("/api/test_ratings.csv") {
                    val csv = deliverableService.csv()
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename=\"test_ratings.csv\"",
                    )
                    call.respondText(csv, ContentType.parse("text/csv; charset=utf-8"))
                }
                get("/api/validate") {
                    call.respond(ValidationMapper.toDto(validationService.get()))
                }
                get("/api/validate.csv") {
                    val csv = ValidationMapper.toCsv(validationService.get())
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename=\"split_half_correlations.csv\"",
                    )
                    call.respondText(csv, ContentType.parse("text/csv; charset=utf-8"))
                }
                get("/api/ratings") {
                    call.respond(RatingMapper.toDto(ratingService.get()))
                }
                get("/api/ratings.csv") {
                    val csv = RatingMapper.toCsv(ratingService.get())
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename=\"test_ratings_preview.csv\"",
                    )
                    call.respondText(csv, ContentType.parse("text/csv; charset=utf-8"))
                }
                get("/api/threat") {
                    call.respond(ThreatMapper.toDto(actionValueService.get()))
                }
                get("/api/threat.csv") {
                    val csv = ThreatMapper.toCsv(actionValueService.get())
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename=\"threat_test_players.csv\"",
                    )
                    call.respondText(csv, ContentType.parse("text/csv; charset=utf-8"))
                }
                get("/api/xg") {
                    call.respond(XgMapper.toDto(xgService.get()))
                }
                get("/api/xg.csv") {
                    val csv = XgMapper.toCsv(xgService.get())
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename=\"xg_test_players.csv\"",
                    )
                    call.respondText(csv, ContentType.parse("text/csv; charset=utf-8"))
                }
                get("/api/inventory") {
                    call.respond(InventoryMapper.toDto(inventoryService.get()))
                }
                get("/api/toi") {
                    val kind = parseSplit(call.request.queryParameters["split"])
                    call.respond(ToiMapper.toDto(kind.name.lowercase(), toiService.get(kind)))
                }
                get("/api/toi.csv") {
                    val kind = parseSplit(call.request.queryParameters["split"])
                    val csv = ToiMapper.toCsv(toiService.get(kind))
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename=\"toi_${kind.name.lowercase()}.csv\"",
                    )
                    call.respondText(csv, ContentType.parse("text/csv; charset=utf-8"))
                }
                get("/api/event-types.csv") {
                    val csv = InventoryMapper.toCsv(inventoryService.get())
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename=\"event_types.csv\"",
                    )
                    call.respondText(csv, ContentType.parse("text/csv; charset=utf-8"))
                }
                staticFiles("/", config.frontendRoot.toFile())
            }
        }.start(wait = wait)
    }

    private fun parseSplit(raw: String?): DatasetKind {
        return when (raw?.lowercase()) {
            "train" -> DatasetKind.TRAIN
            else -> DatasetKind.TEST
        }
    }
}
