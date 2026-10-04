package app.data

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

fun ResultSet.getNullableString(column: String): String? {
    return getString(column)?.takeIf { it.isNotBlank() }
}

fun ResultSet.requireString(column: String): String {
    return getNullableString(column) ?: error("Пустое обязательное поле $column")
}

fun ResultSet.getNullableInt(column: String): Int? {
    val value = getInt(column)
    return if (wasNull()) null else value
}

fun ResultSet.getNullableDouble(column: String): Double? {
    val value = getDouble(column)
    return if (wasNull()) null else value
}

class DuckDbSession : AutoCloseable {
    private val connection: Connection

    init {
        Class.forName("org.duckdb.DuckDBDriver")
        connection = DriverManager.getConnection("jdbc:duckdb:")
    }

    fun <T> query(
        sql: String,
        binder: (PreparedStatement) -> Unit = {},
        mapper: (ResultSet) -> T,
    ): List<T> {
        connection.prepareStatement(sql).use { statement ->
            binder(statement)
            statement.executeQuery().use { resultSet ->
                val rows = mutableListOf<T>()
                while (resultSet.next()) {
                    rows += mapper(resultSet)
                }
                return rows
            }
        }
    }

    fun forEachRow(
        sql: String,
        consume: (ResultSet) -> Unit,
    ) {
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { resultSet ->
                while (resultSet.next()) {
                    consume(resultSet)
                }
            }
        }
    }

    fun <T> querySingle(
        sql: String,
        binder: (PreparedStatement) -> Unit = {},
        mapper: (ResultSet) -> T,
    ): T {
        val rows = query(sql, binder, mapper)
        require(rows.size == 1) { "Ожидалась одна строка, получено ${rows.size}: $sql" }
        return rows.first()
    }

    override fun close() {
        connection.close()
    }

    companion object {
        fun quotePath(path: Path): String {
            val normalized = path.toAbsolutePath().normalize().toString().replace('\\', '/')
            require('\'' !in normalized) { "Недопустимый символ в пути: $path" }
            return "'$normalized'"
        }
    }
}
