package app.domain.math

import kotlin.math.abs

object LinearSystem {
    fun solve(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray {
        val n = vector.size
        require(matrix.size == n) { "Размер матрицы не совпадает с вектором" }
        val a = Array(n) { i -> matrix[i].copyOf() }
        val b = vector.copyOf()
        for (col in 0 until n) {
            var pivot = col
            var best = abs(a[col][col])
            for (row in col + 1 until n) {
                val value = abs(a[row][col])
                if (value > best) {
                    best = value
                    pivot = row
                }
            }
            require(best > 1e-12) { "Вырожденная система линейных уравнений" }
            if (pivot != col) {
                val tmp = a[col]
                a[col] = a[pivot]
                a[pivot] = tmp
                val tb = b[col]
                b[col] = b[pivot]
                b[pivot] = tb
            }
            val diag = a[col][col]
            for (row in col + 1 until n) {
                val factor = a[row][col] / diag
                for (k in col until n) a[row][k] -= factor * a[col][k]
                b[row] -= factor * b[col]
            }
        }
        val x = DoubleArray(n)
        for (i in n - 1 downTo 0) {
            var sum = b[i]
            for (k in i + 1 until n) sum -= a[i][k] * x[k]
            x[i] = sum / a[i][i]
        }
        return x
    }
}
