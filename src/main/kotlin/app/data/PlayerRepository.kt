package app.data

import app.config.DatasetPaths
import app.domain.model.Player
import app.domain.model.Position

class PlayerRepository(
    private val session: DuckDbSession,
    private val paths: DatasetPaths,
) {
    fun loadTrainRoster(): List<Player> = loadPlayersCsv(paths.trainPlayers)

    fun loadNewTestPlayers(): List<Player> = loadPlayersCsv(paths.testPlayersNew)

    fun loadCombinedRoster(): List<Player> {
        val byId = LinkedHashMap<String, Player>()
        loadTrainRoster().forEach { byId[it.playerId] = it }
        loadNewTestPlayers().forEach { player ->
            byId.putIfAbsent(player.playerId, player)
        }
        return byId.values.toList()
    }

    private fun loadPlayersCsv(file: java.nio.file.Path): List<Player> {
        val path = DuckDbSession.quotePath(file)
        val sql = """
            SELECT player_id, position, lineup_slot_mode, shoots
            FROM read_csv_auto($path, header=true)
        """.trimIndent()
        return session.query(sql) { rs ->
            val playerId = rs.requireString("player_id")
            val lineupSlotMode = rs.getNullableString("lineup_slot_mode")
            val shoots = rs.getNullableString("shoots")
            Player(
                playerId = playerId,
                position = Position.infer(rs.getNullableString("position"), lineupSlotMode),
                lineupSlotMode = lineupSlotMode,
                shoots = shoots,
            )
        }
    }
}
