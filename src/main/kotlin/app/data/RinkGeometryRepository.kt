package app.data

import app.config.DatasetPaths
import app.domain.model.RinkLandmark

class RinkGeometryRepository(
    private val session: DuckDbSession,
    private val paths: DatasetPaths,
) {
    fun loadAll(): List<RinkLandmark> {
        val path = DuckDbSession.quotePath(paths.rinkGeometry)
        val sql = """
            SELECT rink_type, object, kind, x, y
            FROM read_csv_auto($path, header=true)
        """.trimIndent()
        return session.query(sql) { rs ->
            RinkLandmark(
                rinkType = rs.requireString("rink_type"),
                objectName = rs.requireString("object"),
                kind = rs.requireString("kind"),
                x = rs.getObject("x")?.let { (it as Number).toDouble() },
                y = rs.getObject("y")?.let { (it as Number).toDouble() },
            )
        }
    }
}
