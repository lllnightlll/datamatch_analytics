package app.domain.rating

import app.domain.threat.StampedCredit

class StintActionAggregator(private val rules: RatingRules) {

    fun aggregate(
        stints: List<IceStint>,
        credits: List<StampedCredit>,
    ): Aggregation {
        val creditsByMatch = credits.groupBy { it.matchId }
        val weighted = mutableListOf<WeightedStint>()
        val matchValues = linkedMapOf<String, MutableMap<String, MutableMatchValue>>()
        var nRapm = 0

        for ((matchId, matchStints) in stints.groupBy { it.matchId }) {
            val matchCredits = creditsByMatch[matchId].orEmpty().sortedBy { it.seq }
            val orderedStints = matchStints.sortedBy { it.startSeq }
            var creditIndex = 0
            for (stint in orderedStints) {
                var homeAv = 0.0
                var awayAv = 0.0
                while (creditIndex < matchCredits.size && matchCredits[creditIndex].seq <= stint.startSeq) {
                    creditIndex += 1
                }
                var look = creditIndex
                while (look < matchCredits.size && matchCredits[look].seq <= stint.endSeq) {
                    val credit = matchCredits[look]
                    if (stint.containsSeq(credit.seq)) {
                        when (credit.teamId) {
                            stint.homeTeamId -> homeAv += credit.value
                            stint.awayTeamId -> awayAv += credit.value
                        }
                        addPlayerMatch(matchValues, credit, stint)
                    }
                    look += 1
                }
                creditIndex = look
                if (stint.durationSeconds < rules.minStintSeconds) continue
                if (rules.rapmFiveOnFiveOnly && !stint.fiveOnFive) continue
                val home = stint.homePlayers()
                val away = stint.awayPlayers()
                if (home.isEmpty() || away.isEmpty()) continue
                weighted += WeightedStint(
                    matchId = matchId,
                    y = rules.per60(homeAv - awayAv, stint.durationSeconds),
                    weight = stint.durationSeconds,
                    plusPlayerIds = home.toList(),
                    minusPlayerIds = away.toList(),
                    rinkType = stint.rinkType,
                )
                nRapm += 1
            }
        }
        return Aggregation(
            stints = weighted,
            playerMatches = matchValues.values.flatMap { byPlayer ->
                byPlayer.values.map { it.snapshot() }
            },
            nRapmStints = nRapm,
        )
    }

    private fun addPlayerMatch(
        sink: MutableMap<String, MutableMap<String, MutableMatchValue>>,
        credit: StampedCredit,
        stint: IceStint,
    ) {
        val byPlayer = sink.getOrPut(stint.matchId) { linkedMapOf() }
        val acc = byPlayer.getOrPut(credit.playerId) {
            MutableMatchValue(credit.playerId, stint.matchId)
        }
        acc.actionValue += credit.value
        acc.nActions += 1
        if (stint.fiveOnFive) {
            acc.evValue += credit.value
        } else {
            acc.specialValue += credit.value
        }
    }

    data class Aggregation(
        val stints: List<WeightedStint>,
        val playerMatches: List<PlayerMatchValue>,
        val nRapmStints: Int,
    )

    private class MutableMatchValue(
        val playerId: String,
        val matchId: String,
    ) {
        var actionValue: Double = 0.0
        var evValue: Double = 0.0
        var specialValue: Double = 0.0
        var nActions: Int = 0

        fun snapshot(): PlayerMatchValue {
            return PlayerMatchValue(playerId, matchId, actionValue, evValue, specialValue, nActions)
        }
    }
}
