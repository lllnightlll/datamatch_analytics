package app.domain.toi

class ToiReconstructor(private val rules: ToiRules) {

    fun reconstruct(events: List<TimelineEvent>): Map<String, MutablePlayerToi> {
        val ledger = linkedMapOf<String, MutablePlayerToi>()
        events.groupBy { it.matchId }.values.forEach { matchEvents ->
            reconstructMatch(matchEvents.sortedBy { it.seq }, ledger)
        }
        return ledger
    }

    private fun reconstructMatch(
        events: List<TimelineEvent>,
        ledger: MutableMap<String, MutablePlayerToi>,
    ) {
        val onIce = linkedMapOf<String, OnIceState>()
        var prevPeriod: Int? = null
        var prevClock = 0.0
        var nHome = rules.defaultNHome
        var nAway = rules.defaultNAway

        for (event in events) {
            if (rules.isIgnoredPeriod(event.period)) {
                continue
            }
            val clock = event.clockS.coerceAtLeast(0.0)
            val strength = rules.classifyStrength(nHome, nAway)

            if (prevPeriod != null && event.period != prevPeriod) {
                val tail = (rules.regulationPeriodSeconds - prevClock).coerceAtLeast(0.0)
                if (tail > 0.0) {
                    credit(onIce, ledger, tail, strength)
                }
                onIce.clear()
                prevClock = 0.0
            } else if (prevPeriod != null) {
                val dt = (clock - prevClock).coerceAtLeast(0.0)
                if (dt > 0.0) {
                    credit(onIce, ledger, dt, strength)
                }
            }

            applyShift(event, onIce)
            if (event.nHome != null) nHome = event.nHome
            if (event.nAway != null) nAway = event.nAway
            prevPeriod = event.period
            prevClock = clock
        }
    }

    private fun applyShift(event: TimelineEvent, onIce: MutableMap<String, OnIceState>) {
        if (!rules.isSkaterShift(event.eventType)) {
            return
        }
        event.otherId?.let { onIce.remove(it) }
        val enterId = event.actorId ?: return
        onIce[enterId] = OnIceState(
            matchId = event.matchId,
            teamId = event.actorTeamId,
            period = event.period,
            clockS = event.clockS.coerceAtLeast(0.0),
        )
    }

    private fun credit(
        onIce: Map<String, OnIceState>,
        ledger: MutableMap<String, MutablePlayerToi>,
        dt: Double,
        strength: StrengthKind,
    ) {
        for ((playerId, state) in onIce) {
            ledger.getOrPut(playerId) { MutablePlayerToi(playerId) }
                .add(dt, strength, state.matchId, state.teamId)
        }
    }
}

internal data class OnIceState(
    val matchId: String,
    val teamId: String?,
    val period: Int,
    val clockS: Double,
)

class MutablePlayerToi(
    val playerId: String,
) {
    var toiSeconds: Double = 0.0
        private set
    var toiFiveOnFiveSeconds: Double = 0.0
        private set
    var toiSpecialSeconds: Double = 0.0
        private set
    private val matchIds = linkedSetOf<String>()
    private val teamSeconds = linkedMapOf<String, Double>()

    fun add(dt: Double, strength: StrengthKind, matchId: String, teamId: String?) {
        toiSeconds += dt
        when (strength) {
            StrengthKind.FIVE_ON_FIVE -> toiFiveOnFiveSeconds += dt
            StrengthKind.SPECIAL -> toiSpecialSeconds += dt
            StrengthKind.EVEN_OTHER -> Unit
        }
        matchIds += matchId
        if (!teamId.isNullOrBlank()) {
            teamSeconds[teamId] = (teamSeconds[teamId] ?: 0.0) + dt
        }
    }

    fun nGames(): Int = matchIds.size

    fun primaryTeamId(): String? = teamSeconds.maxByOrNull { it.value }?.key

    fun playedFor(teamId: String): Boolean = teamId in teamSeconds
}
