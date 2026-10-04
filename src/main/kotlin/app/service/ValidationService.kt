package app.service

import app.data.GameRepository
import app.data.OutcomeEventRepository
import app.data.PlayerRepository
import app.data.ShiftEventRepository
import app.domain.rating.OnIceStintBuilder
import app.domain.rating.RatingRules
import app.domain.rating.RidgeRapm
import app.domain.rating.StintActionAggregator
import app.domain.rating.WeightedStint
import app.domain.validate.CorrelationRow
import app.domain.validate.MatchHalfSplitter
import app.domain.validate.PlayerHalfLedger
import app.domain.validate.PlusMinusAssigner
import app.domain.validate.RankCorrelation
import app.domain.validate.SplitPlayer
import app.domain.validate.ValidationReport
import app.domain.validate.ValidationRules
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class ValidationService(
    private val actionValueService: ActionValueService,
    private val xgService: XgService,
    private val gameRepository: GameRepository,
    private val shiftEventRepository: ShiftEventRepository,
    private val outcomeEventRepository: OutcomeEventRepository,
    private val playerRepository: PlayerRepository,
    private val stintBuilder: OnIceStintBuilder,
    private val aggregator: StintActionAggregator,
    private val ridge: RidgeRapm,
    private val plusMinusAssigner: PlusMinusAssigner,
    private val splitter: MatchHalfSplitter,
    private val ratingRules: RatingRules,
    private val rules: ValidationRules,
) {
    private val logger = LoggerFactory.getLogger(ValidationService::class.java)
    private val cached: ValidationReport by lazy { build() }

    fun get(): ValidationReport = cached

    private fun build(): ValidationReport {
        val started = System.currentTimeMillis()
        val games = gameRepository.loadTrain()
        val halves = splitter.split(games.map { it.matchId })
        val ledger = PlayerHalfLedger(rules, halves)
        val timeline = shiftEventRepository.loadTimeline(isTest = false)
        val stints = stintBuilder.build(timeline, games)
        stints.forEach { ledger.addStint(it) }

        val credits = actionValueService.trainCredits()
        credits.forEach { ledger.addCredit(it) }

        val outcomes = outcomeEventRepository.load(isTest = false)
        outcomes.forEach { ledger.addOutcome(it) }
        val goals = outcomes.filter { rules.isGoal(it.eventType) }
        plusMinusAssigner.apply(stints, goals, ledger::addPlusMinus)

        xgService.trainScoredShots().forEach { shot ->
            ledger.addXg(shot.matchId, shot.playerId, shot.xg)
        }

        val roster = playerRepository.loadTrainRoster().associateBy { it.playerId }
        val eligible = ledger.playerIds().filter { playerId ->
            val player = roster[playerId] ?: return@filter false
            player.position.isSkater() && ledger.snapshot(playerId) != null
        }.toSet()

        val agg = aggregator.aggregate(
            stints.filter { it.matchId in halves.fit },
            credits.filter { it.matchId in halves.fit },
        )
        val restricted = agg.stints.mapNotNull { it.restrict(eligible) }
        val fit = ridge.fit(restricted)

        val players = eligible.mapNotNull { playerId ->
            val player = roster[playerId] ?: return@mapNotNull null
            val (a, b) = ledger.snapshot(playerId) ?: return@mapNotNull null
            val rapm = fit.playerEffects[playerId] ?: 0.0
            val rating = ratingRules.shrink(rapm, a.toiMinutes())
            SplitPlayer(
                playerId = playerId,
                positionName = player.position.name,
                rating = rating,
                rawPer60 = ratingRules.per60(a.actionValue, a.toiSeconds),
                rapm = rapm,
                fit = a,
                out = b,
            )
        }.sortedByDescending { it.rating }

        val correlations = correlationsOf(players)
        val report = ValidationReport(
            rules = rules,
            nFitMatches = halves.fit.size,
            nOutMatches = halves.out.size,
            nPlayers = players.size,
            correlations = correlations,
            players = players,
        )
        val headline = correlations.firstOrNull {
            it.predictor == "rating" && it.outcome == "ga_per60"
        }
        logger.info(
            "Split-half: players={}, fit={}, out={}, spearman rating→G+A/60={}, {} мс",
            players.size,
            halves.fit.size,
            halves.out.size,
            headline?.spearman?.let { "%.3f".format(it) } ?: "n/a",
            System.currentTimeMillis() - started,
        )
        return report
    }

    private fun correlationsOf(players: List<SplitPlayer>): List<CorrelationRow> {
        if (players.size < 3) return emptyList()
        val predictors = listOf(
            "rating" to players.map { it.rating },
            "raw_per60" to players.map { it.rawPer60 },
            "ga_per60" to players.map { it.fit.per60(it.fit.ga().toDouble()) },
            "shots_per60" to players.map { it.fit.per60(it.fit.nShots.toDouble()) },
            "plus_minus_per60" to players.map { it.fit.per60(it.fit.plusMinus.toDouble()) },
        )
        val outcomes = listOf(
            "ga_per60" to players.map { it.out.per60(it.out.ga().toDouble()) },
            "xg_per60" to players.map { it.out.per60(it.out.sumXg) },
            "av_per60" to players.map { it.out.per60(it.out.actionValue) },
            "goals" to players.map { it.out.nGoals.toDouble() },
        )
        return predictors.flatMap { (predName, xs) ->
            outcomes.map { (outName, ys) ->
                CorrelationRow(
                    predictor = predName,
                    outcome = outName,
                    pearson = RankCorrelation.pearson(xs, ys),
                    spearman = RankCorrelation.spearman(xs, ys),
                    n = players.size,
                )
            }
        }
    }

    private fun WeightedStint.restrict(eligible: Set<String>): WeightedStint? {
        val plus = plusPlayerIds.filter { it in eligible }
        val minus = minusPlayerIds.filter { it in eligible }
        if (plus.isEmpty() || minus.isEmpty()) return null
        return copy(plusPlayerIds = plus, minusPlayerIds = minus)
    }

    companion object {
        fun loadRules(
            resourceName: String = "validation-rules.json",
            classLoader: ClassLoader = ValidationService::class.java.classLoader,
        ): ValidationRules {
            val json = classLoader.getResourceAsStream(resourceName)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Не найден ресурс $resourceName")
            return Json.decodeFromString<ValidationRules>(json)
        }
    }
}
