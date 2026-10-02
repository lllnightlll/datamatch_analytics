package app.config

import java.nio.file.Files
import java.nio.file.Path

/**
 * Конфигурация приложения. Пути и порт читаются из окружения,
 * чтобы не размазывать абсолютные пути по коду.
 *
 * Переменные:
 * - DATAMATCH_ROOT — корень репозитория (по умолчанию user.dir)
 * - DATAMATCH_DATA_DIR — папка с CSV/parquet (по умолчанию <root>/data)
 * - DATAMATCH_FRONTEND_DIR — статика HTML (по умолчанию <root>/frontend)
 * - DATAMATCH_PORT — HTTP-порт (по умолчанию 8787)
 */
data class AppConfig(
    val projectRoot: Path,
    val datasetPaths: DatasetPaths,
    val frontendRoot: Path,
    val httpPort: Int,
) {
    init {
        require(Files.isDirectory(projectRoot)) {
            "Корень проекта не найден: $projectRoot"
        }
        require(Files.isDirectory(frontendRoot)) {
            "Папка frontend не найдена: $frontendRoot"
        }
        datasetPaths.requireExists()
        require(httpPort in 1..65535) { "Некорректный HTTP-порт: $httpPort" }
    }

    companion object {
        fun load(): AppConfig {
            val projectRoot = Path.of(
                System.getenv("DATAMATCH_ROOT") ?: System.getProperty("user.dir"),
            ).toAbsolutePath().normalize()
            val dataRoot = Path.of(
                System.getenv("DATAMATCH_DATA_DIR") ?: projectRoot.resolve("data").toString(),
            ).toAbsolutePath().normalize()
            val frontendRoot = Path.of(
                System.getenv("DATAMATCH_FRONTEND_DIR") ?: projectRoot.resolve("frontend").toString(),
            ).toAbsolutePath().normalize()
            val httpPort = System.getenv("DATAMATCH_PORT")?.toIntOrNull() ?: 8787
            return AppConfig(
                projectRoot = projectRoot,
                datasetPaths = DatasetPaths(dataRoot),
                frontendRoot = frontendRoot,
                httpPort = httpPort,
            )
        }
    }
}

data class DatasetPaths(
    val dataRoot: Path,
) {
    val trainEvents: Path = dataRoot.resolve("events.parquet")
    val trainGames: Path = dataRoot.resolve("games.csv")
    val trainPlayers: Path = dataRoot.resolve("players.csv")
    val playerTeams: Path = dataRoot.resolve("player_teams.csv")
    val teams: Path = dataRoot.resolve("teams.csv")
    val rinkGeometry: Path = dataRoot.resolve("rink_geometry.csv")

    val testEvents: Path = dataRoot.resolve("test").resolve("events_test.parquet")
    val testGames: Path = dataRoot.resolve("test").resolve("games_test.csv")
    val testPlayersNew: Path = dataRoot.resolve("test").resolve("players_new.csv")
    val testPlayerSeasons: Path = dataRoot.resolve("test").resolve("player_seasons_test.csv")

    fun requiredFiles(): List<Path> = listOf(
        trainEvents,
        trainGames,
        trainPlayers,
        playerTeams,
        teams,
        rinkGeometry,
        testEvents,
        testGames,
        testPlayersNew,
        testPlayerSeasons,
    )

    fun requireExists() {
        require(Files.isDirectory(dataRoot)) { "Папка данных не найдена: $dataRoot" }
        requiredFiles().forEach { path ->
            require(Files.isRegularFile(path)) { "Нет файла данных: $path" }
        }
    }
}
