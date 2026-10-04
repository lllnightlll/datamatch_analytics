package app.domain.validate

import app.domain.rating.IceStint
import app.domain.threat.StampedCredit

class PlayerHalfLedger(
    private val rules: ValidationRules,
    private val halves: MatchHalfSplitter.Halves,
) {
    private val fit = linkedMapOf<String, MutableHalf>()
    private val out = linkedMapOf<String, MutableHalf>()

    fun addStint(stint: IceStint) {
        val box = boxFor(stint.matchId) ?: return
        val players = stint.playersByTeam.values.flatten().toSet()
        for (playerId in players) {
            val acc = box.getOrPut(playerId) { MutableHalf() }
            acc.toiSeconds += stint.durationSeconds
            acc.matches += stint.matchId
        }
    }

    fun addCredit(credit: StampedCredit) {
        val acc = acc(credit.playerId, credit.matchId) ?: return
        acc.actionValue += credit.value
    }

    fun addOutcome(event: OutcomeEvent) {
        val acc = acc(event.playerId, event.matchId) ?: return
        if (rules.isGoal(event.eventType)) acc.nGoals += 1
        if (rules.isAssist(event.eventType)) acc.nAssists += 1
        if (rules.isShot(event.eventType)) acc.nShots += 1
    }

    fun addXg(matchId: String, playerId: String?, xg: Double) {
        if (playerId == null) return
        val acc = acc(playerId, matchId) ?: return
        acc.sumXg += xg
    }

    fun addPlusMinus(playerId: String, matchId: String, delta: Int) {
        val acc = acc(playerId, matchId) ?: return
        acc.plusMinus += delta
    }

    fun snapshot(playerId: String): Pair<HalfBox, HalfBox>? {
        val a = fit[playerId]?.toBox() ?: return null
        val b = out[playerId]?.toBox() ?: return null
        if (a.toiSeconds < rules.minHalfToiSeconds()) return null
        if (b.toiSeconds < rules.minHalfToiSeconds()) return null
        if (a.nGames < rules.minGamesPerHalf) return null
        if (b.nGames < rules.minGamesPerHalf) return null
        return a to b
    }

    fun playerIds(): Set<String> = fit.keys.intersect(out.keys)

    private fun acc(playerId: String, matchId: String): MutableHalf? {
        val box = boxFor(matchId) ?: return null
        return box.getOrPut(playerId) { MutableHalf() }
    }

    private fun boxFor(matchId: String): MutableMap<String, MutableHalf>? {
        return when (matchId) {
            in halves.fit -> fit
            in halves.out -> out
            else -> null
        }
    }

    private class MutableHalf {
        var toiSeconds: Double = 0.0
        var nGoals: Int = 0
        var nAssists: Int = 0
        var nShots: Int = 0
        var plusMinus: Int = 0
        var sumXg: Double = 0.0
        var actionValue: Double = 0.0
        val matches: MutableSet<String> = linkedSetOf()

        fun toBox(): HalfBox {
            return HalfBox(
                nGames = matches.size,
                toiSeconds = toiSeconds,
                nGoals = nGoals,
                nAssists = nAssists,
                nShots = nShots,
                plusMinus = plusMinus,
                sumXg = sumXg,
                actionValue = actionValue,
            )
        }
    }
}
