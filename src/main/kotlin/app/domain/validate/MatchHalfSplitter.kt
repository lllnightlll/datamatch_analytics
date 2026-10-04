package app.domain.validate

class MatchHalfSplitter {
    fun split(matchIds: Collection<String>): Halves {
        val sorted = matchIds.distinct().sorted()
        val fit = sorted.filterIndexed { index, _ -> index % 2 == 0 }.toSet()
        val out = sorted.filterIndexed { index, _ -> index % 2 == 1 }.toSet()
        return Halves(fit, out)
    }

    data class Halves(
        val fit: Set<String>,
        val out: Set<String>,
    )
}
