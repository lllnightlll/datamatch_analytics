package app.domain.rating

import kotlin.random.Random

class MatchBootstrap(private val rules: RatingRules) {

    fun resample(matchIds: List<String>, draws: Int = rules.bootstrapDraws): List<List<String>> {
        require(matchIds.isNotEmpty()) { "Нет матчей для bootstrap" }
        val random = Random(rules.bootstrapSeed)
        return List(draws) {
            List(matchIds.size) { matchIds[random.nextInt(matchIds.size)] }
        }
    }

    fun interval(samples: List<Double>): Pair<Double, Double> {
        if (samples.isEmpty()) return 0.0 to 0.0
        val sorted = samples.sorted()
        return quantile(sorted, rules.loQuantile) to quantile(sorted, rules.hiQuantile)
    }

    fun quantile(sorted: List<Double>, q: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val clamped = q.coerceIn(0.0, 1.0)
        val index = ((sorted.size - 1) * clamped).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}
