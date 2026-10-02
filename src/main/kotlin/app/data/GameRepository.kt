package app.data

import app.config.DatasetPaths
import app.domain.model.Game

class GameRepository(
    private val session: DuckDbSession,
    private val paths: DatasetPaths,
) {
    fun loadTrain(): List<Game> {
        val path = DuckDbSession.quotePath(paths.trainGames)
        val sql = """
            SELECT match_id, stage, home_team_id, away_team_id,
                   TRY_CAST(home_goals AS INTEGER) AS home_goals,
                   TRY_CAST(away_goals AS INTEGER) AS away_goals,
                   rink_type, n_events
            FROM read_csv_auto($path, header=true)
        """.trimIndent()
        return session.query(sql) { rs ->
            Game(
                matchId = rs.requireString("match_id"),
                stage = rs.requireString("stage"),
                homeTeamId = rs.requireString("home_team_id"),
                awayTeamId = rs.requireString("away_team_id"),
                homeGoals = rs.getNullableInt("home_goals"),
                awayGoals = rs.getNullableInt("away_goals"),
                rinkType = rs.requireString("rink_type"),
                nEvents = rs.getInt("n_events"),
                league = null,
                season = null,
                gameSeq = null,
            )
        }
    }

    fun loadTest(): List<Game> {
        val path = DuckDbSession.quotePath(paths.testGames)
        val sql = """
            SELECT match_id, league, season, stage,
                   TRY_CAST(game_seq AS INTEGER) AS game_seq,
                   home_team_id, away_team_id,
                   TRY_CAST(home_goals AS INTEGER) AS home_goals,
                   TRY_CAST(away_goals AS INTEGER) AS away_goals,
                   rink_type, n_events
            FROM read_csv_auto($path, header=true)
        """.trimIndent()
        return session.query(sql) { rs ->
            Game(
                matchId = rs.requireString("match_id"),
                stage = rs.requireString("stage"),
                homeTeamId = rs.requireString("home_team_id"),
                awayTeamId = rs.requireString("away_team_id"),
                homeGoals = rs.getNullableInt("home_goals"),
                awayGoals = rs.getNullableInt("away_goals"),
                rinkType = rs.requireString("rink_type"),
                nEvents = rs.getInt("n_events"),
                league = rs.getNullableString("league"),
                season = rs.getNullableString("season"),
                gameSeq = rs.getNullableInt("game_seq"),
            )
        }
    }
}
