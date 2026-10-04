package app.domain.rating

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfficialRatingsExporterTest {
    private val rules = DeliverableRules(
        fileName = "test_ratings.csv",
        digits = 4,
        sortByPlayerId = true,
    )
    private val exporter = OfficialRatingsExporter(rules)

    @Test
    fun keepsFourColumnsAndMinToiOnly() {
        val csv = exporter.toCsv(report())
        val lines = csv.trim().split("\n")
        assertEquals("player_id,rating,lo,hi", lines.first())
        assertEquals(2, lines.size - 1)
        assertTrue(lines.none { it.contains("P_LOW") })
        assertTrue(lines[1].startsWith("P001,"))
        assertTrue(lines[2].startsWith("P002,"))
    }

    @Test
    fun clampsIntervalAroundRating() {
        val row = exporter.rows(report()).first { it.playerId == "P002" }
        assertTrue(row.lo <= row.rating)
        assertTrue(row.rating <= row.hi)
        assertEquals(0.5, row.rating, 1e-9)
        assertEquals(0.5, row.lo, 1e-9)
        assertEquals(0.8, row.hi, 1e-9)
    }

    @Test
    fun usesDotDecimals() {
        val csv = exporter.toCsv(report())
        assertTrue("," !in csv.split("\n")[1].split(",")[1])
        assertTrue(csv.contains("1.2500"))
    }

    private fun report(): RatingReport {
        return RatingReport(
            rules = RatingRules(
                ridgeLambda = 1.0,
                minStintSeconds = 1.0,
                bootstrapDraws = 2,
                bootstrapSeed = 1,
                loQuantile = 0.025,
                hiQuantile = 0.975,
                shrinkPriorMinutes = 40.0,
                rapmFiveOnFiveOnly = true,
                includeRinkEffects = false,
                minBootstrapHits = 1,
            ),
            minToiMinutes = 20.0,
            avangardTeamId = "T113",
            nStints = 1,
            nRapmStints = 1,
            nRated = 3,
            nWithMinToi = 2,
            nAvangard = 1,
            medianRating = 0.8,
            players = listOf(
                player("P002", 0.5, 0.9, 0.8, meets = true),
                player("P001", 1.25, 1.0, 1.4, meets = true),
                player("P_LOW", 0.1, 0.0, 0.2, meets = false),
            ),
        )
    }

    private fun player(
        id: String,
        rating: Double,
        lo: Double,
        hi: Double,
        meets: Boolean,
    ): PlayerRating {
        return PlayerRating(
            playerId = id,
            positionName = "C",
            primaryTeamId = "T113",
            playedForAvangard = true,
            nGames = 10,
            nActions = 10,
            toiSeconds = 2000.0,
            toiFiveOnFiveSeconds = 1500.0,
            toiSpecialSeconds = 200.0,
            rawPer60 = 0.0,
            evPer60 = 0.0,
            specialPer60 = 0.0,
            rapm = rating,
            rating = rating,
            lo = lo,
            hi = hi,
            nBootstrapHits = 10,
            meetsMinToi = meets,
        )
    }
}
