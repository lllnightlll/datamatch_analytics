package app.service

import app.data.GameRepository
import app.data.ShiftEventRepository
import app.domain.model.DatasetKind
import app.domain.rating.MatchBootstrap
import app.domain.rating.OnIceStintBuilder
import app.domain.rating.PlayerRating
import app.domain.rating.RatingReport
import app.domain.rating.RatingRules
import app.domain.rating.RidgeRapm
import app.domain.rating.StintActionAggregator
import app.domain.toi.ToiRules
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class RatingService(
    private val actionValueService: ActionValueService,
    private val toiService: ToiService,
    private val shiftEventRepository: ShiftEventRepository,
    private val gameRepository: GameRepository,
    private val stintBuilder: OnIceStintBuilder,
    private val aggregator: StintActionAggregator,
    private val ridge: RidgeRapm,
    private val bootstrap: MatchBootstrap,
    private val ratingRules: RatingRules,
    private val toiRules: ToiRules,
) {
    private val logger = LoggerFactory.getLogger(RatingService::class.java)
    private val cached: RatingReport by lazy { build() }

    fun get(): RatingReport = cached

    private fun build(): RatingReport {
        val started = System.currentTimeMillis()
        val toi = toiService.get(DatasetKind.TEST)
        val credits = actionValueService.testCredits()
        val games = gameRepository.loadTest()
        val timeline = shiftEventRepository.loadTimeline(isTest = true)
        val stints = stintBuilder.build(timeline, games)
        val agg = aggregator.aggregate(stints, credits)
        val design = ridge.compile(agg.stints)
        val matchIds = games.map { it.matchId }
        val point = ridge.fitDesign(design, matchIds)

        val bootByPlayer = linkedMapOf<String, MutableList<Double>>()
        for (sample in bootstrap.resample(matchIds)) {
            val fit = ridge.fitDesign(design, sample)
            for (row in toi.players) {
                val rapm = fit.playerEffects[row.playerId] ?: 0.0
                bootByPlayer.getOrPut(row.playerId) { mutableListOf() }
                    .add(ratingRules.shrink(rapm, row.toiMinutes()))
            }
        }

        val valueByPlayer = actionValueService.get().players.associateBy { it.playerId }
        val evByPlayer = agg.playerMatches.groupBy { it.playerId }.mapValues { (_, rows) ->
            rows.sumOf { it.evValue } to rows.sumOf { it.specialValue }
        }

        val players = toi.players.map { ice ->
            val actions = valueByPlayer[ice.playerId]
            val evSpecial = evByPlayer[ice.playerId] ?: (0.0 to 0.0)
            val rapm = point.playerEffects[ice.playerId] ?: 0.0
            val rating = ratingRules.shrink(rapm, ice.toiMinutes())
            val samples = bootByPlayer[ice.playerId].orEmpty()
            val interval = if (samples.size >= ratingRules.minBootstrapHits) {
                bootstrap.interval(samples)
            } else {
                rating to rating
            }
            PlayerRating(
                playerId = ice.playerId,
                positionName = ice.positionName,
                primaryTeamId = ice.primaryTeamId,
                playedForAvangard = ice.playedForAvangard,
                nGames = ice.nGames,
                nActions = actions?.nActions ?: 0,
                toiSeconds = ice.toiSeconds,
                toiFiveOnFiveSeconds = ice.toiFiveOnFiveSeconds,
                toiSpecialSeconds = ice.toiSpecialSeconds,
                rawPer60 = ratingRules.per60(actions?.totalValue ?: 0.0, ice.toiSeconds),
                evPer60 = ratingRules.per60(evSpecial.first, ice.toiFiveOnFiveSeconds),
                specialPer60 = ratingRules.per60(evSpecial.second, ice.toiSpecialSeconds),
                rapm = rapm,
                rating = rating,
                lo = minOf(interval.first, interval.second),
                hi = maxOf(interval.first, interval.second),
                nBootstrapHits = samples.size,
                meetsMinToi = ice.meetsMinToi(toiRules),
            )
        }.sortedByDescending { it.rating }

        val rated = players.filter { it.meetsMinToi }
        val report = RatingReport(
            rules = ratingRules,
            minToiMinutes = toiRules.minRatingToiMinutes,
            avangardTeamId = toiRules.avangardTeamId,
            nStints = stints.size,
            nRapmStints = agg.nRapmStints,
            nRated = players.size,
            nWithMinToi = rated.size,
            nAvangard = rated.count { it.playedForAvangard },
            medianRating = median(rated.map { it.rating }),
            players = players,
        )
        logger.info(
            "Рейтинг: stints={}, rapm={}, rated={}, minTOI={}, median={}, {} мс",
            report.nStints,
            report.nRapmStints,
            report.nRated,
            report.nWithMinToi,
            "%.3f".format(report.medianRating),
            System.currentTimeMillis() - started,
        )
        return report
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    companion object {
        fun loadRules(
            resourceName: String = "rating-rules.json",
            classLoader: ClassLoader = RatingService::class.java.classLoader,
        ): RatingRules {
            val json = classLoader.getResourceAsStream(resourceName)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Не найден ресурс $resourceName")
            return Json.decodeFromString<RatingRules>(json)
        }
    }
}
