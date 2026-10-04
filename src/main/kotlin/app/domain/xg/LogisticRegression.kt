package app.domain.xg

import app.domain.math.LinearSystem
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

class LogisticRegression(
    val featureNames: List<String>,
    val weights: DoubleArray,
    val means: DoubleArray,
    val scales: DoubleArray,
) {
    init {
        require(weights.size == featureNames.size + 1)
        require(means.size == featureNames.size)
        require(scales.size == featureNames.size)
    }

    fun predictProba(rawFeatures: DoubleArray): Double {
        require(rawFeatures.size == featureNames.size)
        var z = weights[0]
        for (i in rawFeatures.indices) {
            z += weights[i + 1] * standardize(rawFeatures[i], i)
        }
        return sigmoid(z)
    }

    fun coefficients(): List<Coefficient> {
        val rows = mutableListOf(Coefficient("intercept", weights[0]))
        featureNames.forEachIndexed { index, name ->
            rows += Coefficient(name, weights[index + 1])
        }
        return rows
    }

    private fun standardize(value: Double, index: Int): Double {
        return (value - means[index]) / scales[index]
    }

    companion object {
        fun fit(
            featureNames: List<String>,
            rows: List<DoubleArray>,
            labels: DoubleArray,
            l2Lambda: Double,
            maxIterations: Int,
        ): LogisticRegression {
            require(rows.size == labels.size)
            require(rows.isNotEmpty()) { "Нет бросков для обучения xG" }
            val dim = featureNames.size
            val means = DoubleArray(dim)
            val scales = DoubleArray(dim) { 1.0 }
            for (j in 0 until dim) {
                val column = rows.map { it[j] }
                means[j] = column.average()
                val variance = column.map { (it - means[j]) * (it - means[j]) }.average()
                scales[j] = sqrt(variance).coerceAtLeast(1e-6)
            }
            val design = rows.map { raw ->
                DoubleArray(dim + 1) { k ->
                    if (k == 0) 1.0 else (raw[k - 1] - means[k - 1]) / scales[k - 1]
                }
            }
            val weights = newton(design, labels, l2Lambda, maxIterations)
            return LogisticRegression(featureNames, weights, means, scales)
        }

        fun logLoss(probabilities: List<Double>, labels: List<Double>): Double {
            require(probabilities.size == labels.size)
            if (probabilities.isEmpty()) return 0.0
            val eps = 1e-12
            var sum = 0.0
            for (i in probabilities.indices) {
                val p = probabilities[i].coerceIn(eps, 1.0 - eps)
                val y = labels[i]
                sum += -(y * ln(p) + (1.0 - y) * ln(1.0 - p))
            }
            return sum / probabilities.size
        }

        fun brier(probabilities: List<Double>, labels: List<Double>): Double {
            if (probabilities.isEmpty()) return 0.0
            var sum = 0.0
            for (i in probabilities.indices) {
                val d = probabilities[i] - labels[i]
                sum += d * d
            }
            return sum / probabilities.size
        }

        fun sigmoid(z: Double): Double {
            return when {
                z >= 30.0 -> 1.0
                z <= -30.0 -> 0.0
                else -> 1.0 / (1.0 + exp(-z))
            }
        }

        private fun newton(
            design: List<DoubleArray>,
            labels: DoubleArray,
            l2Lambda: Double,
            maxIterations: Int,
        ): DoubleArray {
            val p = design.first().size
            val weights = DoubleArray(p)
            val hessian = Array(p) { DoubleArray(p) }
            val grad = DoubleArray(p)
            repeat(maxIterations) {
                for (j in 0 until p) {
                    grad[j] = 0.0
                    for (k in 0 until p) hessian[j][k] = 0.0
                }
                for (i in design.indices) {
                    val x = design[i]
                    var z = 0.0
                    for (j in 0 until p) z += weights[j] * x[j]
                    val pred = sigmoid(z)
                    val residual = pred - labels[i]
                    val w = pred * (1.0 - pred)
                    for (j in 0 until p) {
                        grad[j] += residual * x[j]
                        for (k in 0 until p) {
                            hessian[j][k] += w * x[j] * x[k]
                        }
                    }
                }
                for (j in 1 until p) {
                    grad[j] += l2Lambda * weights[j]
                    hessian[j][j] += l2Lambda
                }
                val step = LinearSystem.solve(hessian, grad)
                var shift = 0.0
                for (j in 0 until p) {
                    weights[j] -= step[j]
                    shift += abs(step[j])
                }
                if (shift < 1e-6) return weights
            }
            return weights
        }
    }
}
