package app.data

import app.config.DatasetPaths
import app.domain.xg.ShotAttempt
import app.domain.xg.XgRules

class ShotEventRepository(
    private val session: DuckDbSession,
    private val paths: DatasetPaths,
    private val rules: XgRules,
) {
    fun load(isTest: Boolean): List<ShotAttempt> {
        val events = if (isTest) paths.testEvents else paths.trainEvents
        val games = if (isTest) paths.testGames else paths.trainGames
        val types = rules.attemptTypes.joinToString(", ") { "'$it'" }
        val ignored = rules.ignoredPeriods.joinToString(",")
        val sql = """
            SELECT e.match_id, e.seq, e.event_type, e.actor_id, e.actor_team_id,
                   e.x, e.y, e.zone, e.n_home, e.n_away,
                   g.rink_type, g.home_team_id
            FROM read_parquet(${DuckDbSession.quotePath(events)}) e
            JOIN read_csv_auto(${DuckDbSession.quotePath(games)}, header=true) g
              ON e.match_id = g.match_id
            WHERE e.event_type IN ($types)
              AND e.period NOT IN ($ignored)
              AND e.x IS NOT NULL AND e.y IS NOT NULL
        """.trimIndent()
        return session.query(sql) { rs ->
            val eventType = rs.requireString("event_type")
            val teamId = rs.getNullableString("actor_team_id")
            val homeId = rs.getNullableString("home_team_id")
            ShotAttempt(
                matchId = rs.requireString("match_id"),
                seq = rs.getInt("seq"),
                playerId = rs.getNullableString("actor_id"),
                actorTeamId = teamId,
                eventType = eventType,
                x = rs.getDouble("x"),
                y = rs.getDouble("y"),
                zone = rs.getNullableString("zone"),
                nHome = rs.getNullableInt("n_home"),
                nAway = rs.getNullableInt("n_away"),
                rinkType = rs.getNullableString("rink_type") ?: rules.defaultRinkType,
                isHomeShooter = teamId != null && teamId == homeId,
                isGoal = rules.isGoal(eventType),
            )
        }
    }
}
