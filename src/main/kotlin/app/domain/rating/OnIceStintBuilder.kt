package app.domain.rating

import app.domain.model.Game
import app.domain.toi.StrengthKind
import app.domain.toi.TimelineEvent
import app.domain.toi.ToiRules

class OnIceStintBuilder(private val rules: ToiRules) {

    fun build(events: List<TimelineEvent>, games: List<Game>): List<IceStint> {
        val byMatch = games.associateBy { it.matchId }
        return events.groupBy { it.matchId }.flatMap { (matchId, matchEvents) ->
            val game = byMatch[matchId] ?: return@flatMap emptyList()
            buildMatch(matchEvents.sortedBy { it.seq }, game)
        }
    }

    fun buildMatch(events: List<TimelineEvent>, game: Game): List<IceStint> {
        val onIce = linkedMapOf<String, String?>()
        val stints = mutableListOf<IceStint>()
        var prevPeriod: Int? = null
        var prevClock = 0.0
        var prevSeq = 0
        var nHome = rules.defaultNHome
        var nAway = rules.defaultNAway

        for (event in events) {
            if (rules.isIgnoredPeriod(event.period)) continue
            val clock = event.clockS.coerceAtLeast(0.0)
            if (prevPeriod != null && event.period != prevPeriod) {
                emit(stints, game, onIce, prevSeq, event.seq, rules.regulationPeriodSeconds - prevClock, nHome, nAway)
                onIce.clear()
                prevClock = 0.0
                prevSeq = event.seq
            } else if (prevPeriod != null) {
                emit(stints, game, onIce, prevSeq, event.seq, clock - prevClock, nHome, nAway)
            } else {
                prevSeq = event.seq
            }
            applyShift(event, onIce)
            if (event.nHome != null) nHome = event.nHome
            if (event.nAway != null) nAway = event.nAway
            prevPeriod = event.period
            prevClock = clock
            prevSeq = event.seq
        }
        return stints
    }

    private fun emit(
        stints: MutableList<IceStint>,
        game: Game,
        onIce: Map<String, String?>,
        startSeq: Int,
        endSeq: Int,
        dt: Double,
        nHome: Int,
        nAway: Int,
    ) {
        val duration = dt.coerceAtLeast(0.0)
        if (duration <= 0.0 || onIce.isEmpty()) return
        val byTeam = linkedMapOf<String, MutableSet<String>>()
        for ((playerId, teamId) in onIce) {
            if (teamId.isNullOrBlank()) continue
            byTeam.getOrPut(teamId) { linkedSetOf() }.add(playerId)
        }
        if (byTeam.isEmpty()) return
        stints += IceStint(
            matchId = game.matchId,
            startSeq = startSeq,
            endSeq = endSeq,
            durationSeconds = duration,
            playersByTeam = byTeam,
            nHome = nHome,
            nAway = nAway,
            fiveOnFive = rules.classifyStrength(nHome, nAway) == StrengthKind.FIVE_ON_FIVE,
            rinkType = game.rinkType,
            homeTeamId = game.homeTeamId,
            awayTeamId = game.awayTeamId,
        )
    }

    private fun applyShift(event: TimelineEvent, onIce: MutableMap<String, String?>) {
        if (!rules.isSkaterShift(event.eventType)) return
        event.otherId?.let { onIce.remove(it) }
        val enterId = event.actorId ?: return
        onIce[enterId] = event.actorTeamId
    }
}
