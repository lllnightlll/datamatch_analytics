package app.domain.validate

import kotlin.math.sqrt

object RankCorrelation {
    fun pearson(xs: List<Double>, ys: List<Double>): Double {
        require(xs.size == ys.size) { "Длины рядов для корреляции должны совпадать" }
        val n = xs.size
        if (n < 3) return 0.0
        var sumX = 0.0
        var sumY = 0.0
        var sumXy = 0.0
        var sumX2 = 0.0
        var sumY2 = 0.0
        for (i in 0 until n) {
            val x = xs[i]
            val y = ys[i]
            sumX += x
            sumY += y
            sumXy += x * y
            sumX2 += x * x
            sumY2 += y * y
        }
        val num = n * sumXy - sumX * sumY
        val den = sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY))
        if (den <= 1e-12) return 0.0
        return (num / den).coerceIn(-1.0, 1.0)
    }

    fun spearman(xs: List<Double>, ys: List<Double>): Double {
        return pearson(ranks(xs), ranks(ys))
    }

    fun ranks(values: List<Double>): List<Double> {
        val order = values.mapIndexed { index, value -> index to value }
            .sortedWith(compareBy({ it.second }, { it.first }))
        val ranks = DoubleArray(values.size)
        var i = 0
        while (i < order.size) {
            var j = i
            while (j + 1 < order.size && order[j + 1].second == order[i].second) {
                j += 1
            }
            val rank = (i + j) / 2.0 + 1.0
            for (k in i..j) {
                ranks[order[k].first] = rank
            }
            i = j + 1
        }
        return ranks.toList()
    }
}
