package app.domain.rating

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class DeliverableRules(
    val fileName: String,
    val digits: Int,
    val sortByPlayerId: Boolean,
)

/**
 * Официальный файл жюри: ровно четыре колонки, только полевые с порогом ТОИ.
 * Одна строка на player_id, без имён и без вратарей.
 */
class OfficialRatingsExporter(private val rules: DeliverableRules) {

    fun rows(report: RatingReport): List<OfficialRatingRow> {
        val eligible = report.players.filter { it.meetsMinToi }
        val unique = eligible.distinctBy { it.playerId }
        val ordered = if (rules.sortByPlayerId) {
            unique.sortedBy { it.playerId }
        } else {
            unique.sortedByDescending { it.rating }
        }
        return ordered.map { row ->
            val lo = minOf(row.lo, row.rating)
            val hi = maxOf(row.hi, row.rating)
            OfficialRatingRow(
                playerId = row.playerId,
                rating = row.rating,
                lo = lo,
                hi = hi,
            )
        }
    }

    fun toCsv(report: RatingReport): String {
        val header = "player_id,rating,lo,hi"
        val lines = rows(report).map { row ->
            listOf(row.playerId, fmt(row.rating), fmt(row.lo), fmt(row.hi)).joinToString(",")
        }
        return (listOf(header) + lines).joinToString("\n") + "\n"
    }

    private fun fmt(value: Double): String {
        return String.format(Locale.US, "%.${rules.digits}f", value)
    }
}

data class OfficialRatingRow(
    val playerId: String,
    val rating: Double,
    val lo: Double,
    val hi: Double,
)
