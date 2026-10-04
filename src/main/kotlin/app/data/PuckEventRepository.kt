package app.data

import app.config.DatasetPaths
import app.domain.threat.PuckEvent
import app.domain.threat.ThreatRules

class PuckEventRepository(
    private val session: DuckDbSession,
    private val paths: DatasetPaths,
    private val rules: ThreatRules,
) {
    fun forEachMatch(isTest: Boolean, consume: (List<PuckEvent>) -> Unit) {
        val file = if (isTest) paths.testEvents else paths.trainEvents
        val types = (rules.locationTypes + rules.shotTypes).joinToString(", ") { "'$it'" }
        val periodFilter = if (rules.ignoredPeriods.isEmpty()) {
            "1=1"
        } else {
            "period NOT IN (${rules.ignoredPeriods.joinToString(",")})"
        }
        val sql = """
            SELECT match_id, seq, event_type, actor_id, actor_team_id,
                   x, y, x2, y2, period
            FROM read_parquet(${DuckDbSession.quotePath(file)})
            WHERE $periodFilter
              AND event_type IN ($types)
              AND x IS NOT NULL AND y IS NOT NULL
            ORDER BY match_id, seq
        """.trimIndent()
        var currentMatch: String? = null
        val buffer = mutableListOf<PuckEvent>()
        session.forEachRow(sql) { rs ->
            val event = PuckEvent(
                matchId = rs.requireString("match_id"),
                seq = rs.getInt("seq"),
                eventType = rs.requireString("event_type"),
                actorId = rs.getNullableString("actor_id"),
                actorTeamId = rs.getNullableString("actor_team_id"),
                x = rs.getDouble("x"),
                y = rs.getDouble("y"),
                x2 = rs.getNullableDouble("x2"),
                y2 = rs.getNullableDouble("y2"),
                period = rs.getInt("period"),
            )
            if (currentMatch != null && event.matchId != currentMatch) {
                consume(buffer.toList())
                buffer.clear()
            }
            currentMatch = event.matchId
            buffer += event
        }
        if (buffer.isNotEmpty()) {
            consume(buffer)
        }
    }
}
