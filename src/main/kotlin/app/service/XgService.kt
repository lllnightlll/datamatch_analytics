package app.service

import app.data.PlayerRepository
import app.data.RinkGeometryRepository
import app.data.ShotEventRepository
import app.domain.xg.CalibrationBin
import app.domain.xg.LogisticRegression
import app.domain.xg.PlayerXg
import app.domain.xg.RinkCatalog
import app.domain.xg.ScoredShot
import app.domain.xg.ShotAttempt
import app.domain.xg.ShotFeatureExtractor
import app.domain.xg.XgReport
import app.domain.xg.XgRules
import app.domain.xg.XgSplitMetrics
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class XgService(
    private val shotEventRepository: ShotEventRepository,
    private val rinkGeometryRepository: RinkGeometryRepository,
    private val playerRepository: PlayerRepository,
    private val rules: XgRules,
) {
    private val logger = LoggerFactory.getLogger(XgService::class.java)
    private val cached: XgFit by lazy { build() }

    fun get(): XgReport = cached.report

    fun trainScoredShots(): List<ScoredShot> = cached.trainScored

    private fun build(): XgFit {
        val started = System.currentTimeMillis()
        val catalog = RinkCatalog(
            landmarks = rinkGeometryRepository.loadAll(),
            goalObject = rules.goalObject,
            defaultRinkType = rules.defaultRinkType,
        )
        val extractor = ShotFeatureExtractor(catalog, rules)
        val trainShots = shotEventRepository.load(isTest = false)
        val testShots = shotEventRepository.load(isTest = true)

        val fitShots = trainShots.filter { !rules.isHoldoutMatch(it.matchId) }
        val holdoutShots = trainShots.filter { rules.isHoldoutMatch(it.matchId) }
        val holdoutModel = fit(extractor, fitShots)
        val holdout = metrics(holdoutModel, extractor, holdoutShots)
        val calibration = calibration(holdoutModel, extractor, holdoutShots)

        val fullModel = fit(extractor, trainShots)
        val test = metrics(fullModel, extractor, testShots)
        val players = playerXg(fullModel, extractor, testShots)
        val trainScored = trainShots.map { shot ->
            ScoredShot(
                matchId = shot.matchId,
                playerId = shot.playerId,
                xg = fullModel.predictProba(extractor.extract(shot)),
                isGoal = shot.isGoal,
            )
        }

        val report = XgReport(
            rules = rules,
            coefficients = fullModel.coefficients(),
            holdout = holdout,
            test = test,
            calibration = calibration,
            topTestPlayers = players.take(25),
        )
        logger.info(
            "xG: train={}, holdout logloss={}, test goals={}, test xG={}, {} мс",
            trainShots.size,
            "%.4f".format(holdout.logLoss),
            test.nGoals,
            "%.1f".format(test.sumXg),
            System.currentTimeMillis() - started,
        )
        return XgFit(report, trainScored)
    }

    private data class XgFit(
        val report: XgReport,
        val trainScored: List<ScoredShot>,
    )

    private fun fit(extractor: ShotFeatureExtractor, shots: List<ShotAttempt>): LogisticRegression {
        val features = shots.map { extractor.extract(it) }
        val labels = DoubleArray(shots.size) { i -> if (shots[i].isGoal) 1.0 else 0.0 }
        return LogisticRegression.fit(
            featureNames = extractor.featureNames,
            rows = features,
            labels = labels,
            l2Lambda = rules.l2Lambda,
            maxIterations = rules.maxIterations,
        )
    }

    private fun metrics(
        model: LogisticRegression,
        extractor: ShotFeatureExtractor,
        shots: List<ShotAttempt>,
    ): XgSplitMetrics {
        val probabilities = shots.map { model.predictProba(extractor.extract(it)) }
        val labels = shots.map { if (it.isGoal) 1.0 else 0.0 }
        return XgSplitMetrics(
            nAttempts = shots.size,
            nGoals = shots.count { it.isGoal },
            sumXg = probabilities.sum(),
            logLoss = LogisticRegression.logLoss(probabilities, labels),
            brier = LogisticRegression.brier(probabilities, labels),
        )
    }

    private fun calibration(
        model: LogisticRegression,
        extractor: ShotFeatureExtractor,
        shots: List<ShotAttempt>,
    ): List<CalibrationBin> {
        if (shots.isEmpty()) return emptyList()
        val scored = shots.map { shot ->
            val p = model.predictProba(extractor.extract(shot))
            p to if (shot.isGoal) 1.0 else 0.0
        }
        val bins = rules.calibrationBins
        return (0 until bins).map { index ->
            val lo = index.toDouble() / bins
            val hi = (index + 1).toDouble() / bins
            val bucket = scored.filter { (p, _) ->
                if (index == bins - 1) (p >= lo) && (p <= hi) else (p >= lo) && (p < hi)
            }
            CalibrationBin(
                binIndex = index,
                predictedMean = bucket.map { it.first }.average().takeIf { bucket.isNotEmpty() } ?: 0.0,
                actualRate = bucket.map { it.second }.average().takeIf { bucket.isNotEmpty() } ?: 0.0,
                n = bucket.size,
            )
        }.filter { it.n > 0 }
    }

    private fun playerXg(
        model: LogisticRegression,
        extractor: ShotFeatureExtractor,
        shots: List<ShotAttempt>,
    ): List<PlayerXg> {
        val roster = playerRepository.loadCombinedRoster().associateBy { it.playerId }
        val grouped = shots.groupBy { it.playerId }
        return grouped.mapNotNull { (playerId, attempts) ->
            if (playerId == null) return@mapNotNull null
            val player = roster[playerId] ?: return@mapNotNull null
            if (!player.position.isSkater()) return@mapNotNull null
            val xg = attempts.sumOf { model.predictProba(extractor.extract(it)) }
            PlayerXg(
                playerId = playerId,
                positionName = player.position.name,
                nAttempts = attempts.size,
                nGoals = attempts.count { it.isGoal },
                sumXg = xg,
            )
        }.sortedByDescending { it.sumXg }
    }

    companion object {
        fun loadRules(
            resourceName: String = "xg-rules.json",
            classLoader: ClassLoader = XgService::class.java.classLoader,
        ): XgRules {
            val json = classLoader.getResourceAsStream(resourceName)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Не найден ресурс $resourceName")
            return Json.decodeFromString<XgRules>(json)
        }
    }
}
