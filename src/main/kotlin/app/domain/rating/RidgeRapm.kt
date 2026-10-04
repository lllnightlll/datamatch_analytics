package app.domain.rating

import app.domain.math.LinearSystem

class RidgeRapm(private val rules: RatingRules) {

    /**
     * Одна Gram на все отрезки. Для train нельзя копить блок на матч:
     * 500 матчей × 2000 игроков не влезают в кучу.
     */
    fun fit(stints: List<WeightedStint>): RapmFit {
        if (stints.isEmpty()) {
            return RapmFit(0.0, emptyMap(), emptyMap(), 0, 0)
        }
        val index = indexOf(stints)
        val gram = Array(index.dim) { DoubleArray(index.dim) }
        val rhs = DoubleArray(index.dim)
        for (row in stints) {
            index.accumulateRow(gram, rhs, row)
        }
        return solve(index, gram, rhs, stints.size)
    }

    fun compile(stints: List<WeightedStint>): RapmDesign {
        val index = indexOf(stints)
        val blocks = stints.groupBy { it.matchId }.map { (matchId, rows) ->
            index.block(matchId, rows)
        }
        return RapmDesign(index, blocks)
    }

    private fun indexOf(stints: List<WeightedStint>): FeatureIndex {
        val players = stints.flatMap { it.plusPlayerIds + it.minusPlayerIds }.distinct().sorted()
        val rinks = if (rules.includeRinkEffects) {
            val counts = stints.groupingBy { it.rinkType }.eachCount()
            val baseline = counts.maxByOrNull { it.value }?.key
            counts.keys.filter { it != baseline }.sorted()
        } else {
            emptyList()
        }
        return FeatureIndex(players, rinks)
    }

    private fun solve(
        index: FeatureIndex,
        gram: Array<DoubleArray>,
        rhs: DoubleArray,
        nStints: Int,
    ): RapmFit {
        for (j in 1 until gram.size) {
            gram[j][j] += rules.ridgeLambda
        }
        val beta = LinearSystem.solve(gram, rhs)
        return RapmDesign(index, emptyList()).toFit(beta, nStints)
    }

    fun fitDesign(design: RapmDesign, matchIds: Collection<String>): RapmFit {
        if (design.playerIds.isEmpty() || matchIds.isEmpty()) {
            return RapmFit(0.0, emptyMap(), emptyMap(), 0, 0)
        }
        val system = design.accumulate(matchIds)
        for (j in 1 until system.gram.size) {
            system.gram[j][j] += rules.ridgeLambda
        }
        val beta = LinearSystem.solve(system.gram, system.rhs)
        val nStints = matchIds.sumOf { id -> design.blockOf(id)?.nStints ?: 0 }
        return design.toFit(beta, nStints)
    }

    class FeatureIndex(
        val playerIds: List<String>,
        val rinkTypes: List<String>,
    ) {
        private val playerAt: Map<String, Int> =
            playerIds.withIndex().associate { it.value to (1 + it.index) }
        private val rinkAt: Map<String, Int> =
            rinkTypes.withIndex().associate { it.value to (1 + playerIds.size + it.index) }
        val dim: Int = 1 + playerIds.size + rinkTypes.size

        fun block(matchId: String, rows: List<WeightedStint>): MatchRapmBlock {
            val gram = Array(dim) { DoubleArray(dim) }
            val rhs = DoubleArray(dim)
            for (row in rows) {
                accumulateRow(gram, rhs, row)
            }
            return MatchRapmBlock(matchId, gram, rhs, rows.size)
        }

        fun accumulateRow(gram: Array<DoubleArray>, rhs: DoubleArray, row: WeightedStint) {
            val signed = signedPairs(row)
            val w = row.weight.coerceAtLeast(0.0)
            if (w <= 0.0 || signed.isEmpty()) return
            for (a in signed.indices) {
                val (i, si) = signed[a]
                rhs[i] += w * si * row.y
                for (b in signed.indices) {
                    val (j, sj) = signed[b]
                    gram[i][j] += w * si * sj
                }
            }
        }

        private fun signedPairs(row: WeightedStint): List<Pair<Int, Double>> {
            val pairs = ArrayList<Pair<Int, Double>>(row.plusPlayerIds.size + row.minusPlayerIds.size + 2)
            pairs += 0 to 1.0
            rinkAt[row.rinkType]?.let { pairs += it to 1.0 }
            row.plusPlayerIds.forEach { id -> playerAt[id]?.let { pairs += it to 1.0 } }
            row.minusPlayerIds.forEach { id -> playerAt[id]?.let { pairs += it to -1.0 } }
            return pairs
        }
    }

    class MatchRapmBlock(
        val matchId: String,
        val gram: Array<DoubleArray>,
        val rhs: DoubleArray,
        val nStints: Int,
    )

    class RapmDesign(
        val index: FeatureIndex,
        val blocks: List<MatchRapmBlock>,
    ) {
        val playerIds: List<String> get() = index.playerIds
        private val byMatch = blocks.associateBy { it.matchId }

        fun blockOf(matchId: String): MatchRapmBlock? = byMatch[matchId]

        fun accumulate(matchIds: Collection<String>): NormalEquations {
            val dim = index.dim
            val gram = Array(dim) { DoubleArray(dim) }
            val rhs = DoubleArray(dim)
            for (matchId in matchIds) {
                val block = byMatch[matchId] ?: continue
                add(gram, rhs, block)
            }
            return NormalEquations(gram, rhs)
        }

        fun toFit(beta: DoubleArray, nStints: Int): RapmFit {
            val effects = index.playerIds.mapIndexed { i, id -> id to beta[1 + i] }.toMap()
            val rinks = index.rinkTypes.mapIndexed { i, name ->
                name to beta[1 + index.playerIds.size + i]
            }.toMap()
            return RapmFit(
                intercept = beta[0],
                playerEffects = effects,
                rinkEffects = rinks,
                nStints = nStints,
                nPlayers = index.playerIds.size,
            )
        }

        private fun add(gram: Array<DoubleArray>, rhs: DoubleArray, block: MatchRapmBlock) {
            val dim = gram.size
            for (i in 0 until dim) {
                rhs[i] += block.rhs[i]
                val src = block.gram[i]
                val dst = gram[i]
                for (j in 0 until dim) dst[j] += src[j]
            }
        }
    }

    data class NormalEquations(
        val gram: Array<DoubleArray>,
        val rhs: DoubleArray,
    )
}
