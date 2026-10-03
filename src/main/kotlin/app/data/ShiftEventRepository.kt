package app.data

import app.config.DatasetPaths
import app.domain.toi.TimelineEvent
import app.domain.toi.ToiRules

class ShiftEventRepository(
    private val session: DuckDbSession,
    private val paths: DatasetPaths,
    private val rules: ToiRules,
) {
    fun loadTimeline(isTest: Boolean): List<TimelineEvent> {
        val file = if (isTest) paths.testEvents else paths.trainEvents
        val ignored = rules.ignoredPeriods.joinToString(",")
        val boundaries = rules.periodBoundaryTypes.joinToString(", ") { "'$it'" }
        val prefix = rules.shiftPrefix.replace("'", "")
        val sql = """
            SELECT match_id, seq, event_type, actor_id, other_id, actor_team_id,
                   period, clock_s, n_home, n_away
            FROM read_parquet(${DuckDbSession.quotePath(file)})
            WHERE period NOT IN ($ignored)
              AND (
                event_type LIKE '$prefix%'
                OR event_type IN ($boundaries)
              )
            ORDER BY match_id, seq
        """.trimIndent()
        return session.query(sql) { rs ->
            TimelineEvent(
                matchId = rs.requireString("match_id"),
                seq = rs.getInt("seq"),
                eventType = rs.requireString("event_type"),
                actorId = rs.getNullableString("actor_id"),
                otherId = rs.getNullableString("other_id"),
                actorTeamId = rs.getNullableString("actor_team_id"),
                period = rs.getInt("period"),
                clockS = rs.getNullableDouble("clock_s") ?: 0.0,
                nHome = rs.getNullableInt("n_home"),
                nAway = rs.getNullableInt("n_away"),
            )
        }
    }
}
