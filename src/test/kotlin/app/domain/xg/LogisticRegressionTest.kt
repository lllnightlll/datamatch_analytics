package app.domain.xg

import app.domain.math.LinearSystem
import kotlin.test.Test
import kotlin.test.assertTrue

class LogisticRegressionTest {
    @Test
    fun separatesHighAndLowDistance() {
        val rows = mutableListOf<DoubleArray>()
        val labels = mutableListOf<Double>()
        repeat(40) {
            rows += doubleArrayOf(4.0, 0.16, 0.1, 1.0, 1.0, 0.0)
            labels += 1.0
            rows += doubleArrayOf(22.0, 4.84, 1.2, 0.0, 1.0, 0.0)
            labels += 0.0
        }
        val model = LogisticRegression.fit(
            featureNames = ShotFeatureExtractor.FEATURE_NAMES,
            rows = rows,
            labels = labels.toDoubleArray(),
            l2Lambda = 0.1,
            maxIterations = 25,
        )
        val close = model.predictProba(doubleArrayOf(4.0, 0.16, 0.1, 1.0, 1.0, 0.0))
        val far = model.predictProba(doubleArrayOf(22.0, 4.84, 1.2, 0.0, 1.0, 0.0))
        assertTrue(close > 0.7, "близкий бросок должен иметь высокое xG, было $close")
        assertTrue(far < 0.3, "дальний бросок должен иметь низкое xG, было $far")
        assertTrue(close > far)
    }

    @Test
    fun solveRecoversKnownSystem() {
        val matrix = arrayOf(
            doubleArrayOf(2.0, 1.0),
            doubleArrayOf(1.0, 2.0),
        )
        val x = LinearSystem.solve(matrix, doubleArrayOf(4.0, 5.0))
        assertTrue(kotlin.math.abs(x[0] - 1.0) < 1e-9)
        assertTrue(kotlin.math.abs(x[1] - 2.0) < 1e-9)
    }
}
