package app.data

import app.config.DatasetPaths
import app.domain.validate.OutcomeEvent
import app.domain.validate.ValidationRules

class OutcomeEventRepository(
    private val session: DuckDbSession,
    private val paths: DatasetPaths,
    private val rules: ValidationRules,
) {
    fun load(isTest: Boolean): List<OutcomeEvent> {
        val file = if (isTest) paths.testEvents else paths.trainEvents
        val types = (rules.shotTypes + rules.assistTypes + setOf(rules.goalType))
            .joinToString(", ") { "'$it'" }
        val sql = """
            SELECT match_id, seq, event_type, actor_id, actor_team_id
            FROM read_parquet(${DuckDbSession.quotePath(file)})
            WHERE event_type IN ($types)
              AND actor_id IS NOT NULL
        """.trimIndent()
        return session.query(sql) { rs ->
            OutcomeEvent(
                matchId = rs.requireString("match_id"),
                seq = rs.getInt("seq"),
                playerId = rs.requireString("actor_id"),
                teamId = rs.getNullableString("actor_team_id"),
                eventType = rs.requireString("event_type"),
            )
        }
    }
}
