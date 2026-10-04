package app.domain.validate

import app.domain.rating.IceStint

class PlusMinusAssigner {
    fun apply(
        stints: List<IceStint>,
        goals: List<OutcomeEvent>,
        add: (playerId: String, matchId: String, delta: Int) -> Unit,
    ) {
        val byMatch = stints.groupBy { it.matchId }
        for (goal in goals) {
            val matchStints = byMatch[goal.matchId] ?: continue
            val stint = matchStints.firstOrNull { it.containsSeq(goal.seq) } ?: continue
            val scorerTeam = goal.teamId ?: continue
            val plus = stint.playersByTeam[scorerTeam].orEmpty()
            val minus = stint.playersByTeam.filterKeys { it != scorerTeam }.values.flatten()
            plus.forEach { add(it, goal.matchId, 1) }
            minus.forEach { add(it, goal.matchId, -1) }
        }
    }
}
