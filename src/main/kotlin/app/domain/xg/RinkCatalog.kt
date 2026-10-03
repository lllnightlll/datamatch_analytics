package app.domain.xg

import app.domain.model.RinkLandmark

class RinkCatalog(
    landmarks: List<RinkLandmark>,
    private val goalObject: String,
    private val defaultRinkType: String,
) {
    private val goals: Map<String, Point> = landmarks
        .filter { it.objectName == goalObject && it.x != null && it.y != null }
        .associate { it.rinkType to Point(it.x!!, it.y!!) }

    init {
        require(goals.isNotEmpty()) { "В геометрии нет объекта $goalObject" }
        require(goals.containsKey(defaultRinkType)) {
            "Нет ворот для площадки по умолчанию $defaultRinkType"
        }
    }

    fun opponentGoal(rinkType: String): Point {
        return goals[rinkType] ?: goals.getValue(defaultRinkType)
    }
}
