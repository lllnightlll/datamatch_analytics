package app.domain.threat

class ActionValuator(
    private val grid: ThreatGrid,
    private val rules: ThreatRules,
) {
    private val neutralBaseline: Double = grid.regionMean { x, _ -> kotlin.math.abs(x) <= rules.neutralXAbs }
    private val ownBaseline: Double = grid.regionMean { x, _ -> x < -rules.neutralXAbs }

    fun evaluate(event: PuckEvent, sharedTypes: Set<String>): ActionCredit? {
        if (event.actorId == null) return null
        if (event.eventType !in sharedTypes) return null
        val rule = rules.actionOf(event.eventType) ?: return null
        val here = grid.threat(event.x, event.y)
        val value = when (rule.kind) {
            ActionKind.MOVE -> {
                val destX = event.x2 ?: return null
                val destY = event.y2 ?: return null
                grid.threat(destX, destY) - here
            }
            ActionKind.GAIN -> here
            ActionKind.LOSS -> -here
            ActionKind.VS_NEUTRAL -> here - neutralBaseline
            ActionKind.VS_OWN -> here - ownBaseline
        }
        return ActionCredit(
            playerId = event.actorId,
            eventType = event.eventType,
            family = rule.family,
            value = value,
        )
    }
}

object ShotLookahead {
    fun shotSoon(events: List<PuckEvent>, index: Int, rules: ThreatRules): Boolean {
        val start = events[index]
        val limit = minOf(events.lastIndex, index + rules.lookaheadEvents)
        for (j in index + 1..limit) {
            val next = events[j]
            if (next.period != start.period) return false
            if (rules.isShot(next.eventType) && next.actorTeamId == start.actorTeamId) {
                return true
            }
        }
        return false
    }
}
