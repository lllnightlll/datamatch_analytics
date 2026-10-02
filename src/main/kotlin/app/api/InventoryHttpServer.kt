package app.api

import app.api.dto.ErrorDto
import app.api.dto.HealthDto
import app.api.dto.InventoryMapper
import app.config.AppConfig
import app.service.DatasetInventoryService
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
) {
    private val logger = LoggerFactory.getLogger(InventoryHttpServer::class.java)

    fun start(wait: Boolean = true) {
        val inventory = inventoryService.get()
        logger.info(
            "Инвентарь готов: train games={}, test games={}, shared event types={}",
            inventory.train.nGames,
            inventory.test.nGames,
            inventory.nSharedEventTypes,
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
                    call.respond(HealthDto(status = "ok", step = "1-inventory"))
                }
                get("/api/inventory") {
                    call.respond(InventoryMapper.toDto(inventoryService.get()))
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
}
