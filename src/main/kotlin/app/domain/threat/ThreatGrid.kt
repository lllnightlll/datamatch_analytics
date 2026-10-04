package app.domain.threat

class ThreatGrid(
    val rules: ThreatRules,
    private val successes: IntArray,
    private val trials: IntArray,
    val globalRate: Double,
) {
    val cols: Int = rules.cols()
    val rows: Int = rules.rows()
    val nObservations: Int get() = trials.sum()

    fun threat(x: Double, y: Double): Double {
        val index = indexOf(x, y) ?: return globalRate
        return threatAt(index)
    }

    fun threatAt(index: Int): Double {
        val n = trials[index]
        if (n <= 0) return globalRate
        val raw = successes[index].toDouble() / n
        if (n >= rules.minCellCount) return raw
        val k = rules.priorStrength
        return (successes[index] + k * globalRate) / (n + k)
    }

    fun indexOf(x: Double, y: Double): Int? = rules.cellIndex(x, y)

    fun heatmap(): List<List<Double>> {
        return (0 until rows).map { row ->
            (0 until cols).map { col -> threatAt(row * cols + col) }
        }
    }

    fun regionMean(predicate: (x: Double, y: Double) -> Boolean): Double {
        var sum = 0.0
        var n = 0
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = rules.xMin + (col + 0.5) * rules.cellSizeM
                val y = rules.yMin + (row + 0.5) * rules.cellSizeM
                if (predicate(x, y)) {
                    sum += threatAt(row * cols + col)
                    n += 1
                }
            }
        }
        return if (n == 0) globalRate else sum / n
    }
}

class ThreatGridBuilder(private val rules: ThreatRules) {
    private val cells = rules.cols() * rules.rows()
    private val successes = IntArray(cells)
    private val trials = IntArray(cells)
    private var totalSuccess = 0
    private var totalTrials = 0

    fun add(x: Double, y: Double, shotSoon: Boolean) {
        val index = rules.cellIndex(x, y) ?: return
        trials[index] += 1
        totalTrials += 1
        if (shotSoon) {
            successes[index] += 1
            totalSuccess += 1
        }
    }

    fun build(): ThreatGrid {
        val rate = if (totalTrials == 0) 0.0 else totalSuccess.toDouble() / totalTrials
        return ThreatGrid(rules, successes.copyOf(), trials.copyOf(), rate)
    }
}
