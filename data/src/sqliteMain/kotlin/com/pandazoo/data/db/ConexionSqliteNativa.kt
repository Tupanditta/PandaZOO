package com.pandazoo.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL

/** Conexión sobre el SQLite empaquetado de `androidx.sqlite` (Android y JVM). Traduce sus errores a [ErrorSql]. */
internal class ConexionSqliteNativa(private val c: SQLiteConnection) : ConexionSql {
    override fun preparar(sql: String): SentenciaSql = traducir { SentenciaSqliteNativa(c.prepare(sql)) }

    override fun ejecutar(sql: String) = traducir { c.execSQL(sql) }

    override fun cerrar() = c.close()
}

private class SentenciaSqliteNativa(private val s: SQLiteStatement) : SentenciaSql {
    override fun bindNull(i: Int) = traducir { s.bindNull(i) }

    override fun bindText(i: Int, valor: String) = traducir { s.bindText(i, valor) }

    override fun bindLong(i: Int, valor: Long) = traducir { s.bindLong(i, valor) }

    override fun bindDouble(i: Int, valor: Double) = traducir { s.bindDouble(i, valor) }

    override fun step(): Boolean = traducir { s.step() }

    override fun getText(i: Int): String = s.getText(i)

    override fun getLong(i: Int): Long = s.getLong(i)

    override fun getDouble(i: Int): Double = s.getDouble(i)

    override fun isNull(i: Int): Boolean = s.isNull(i)

    override fun close() = s.close()
}

private inline fun <T> traducir(bloque: () -> T): T =
    try {
        bloque()
    } catch (e: SQLiteException) {
        throw ErrorSql(e.message ?: "Error de SQLite", e)
    }

internal actual fun abrirConexion(nombre: String): ConexionSql = ConexionSqliteNativa(BundledSQLiteDriver().open(nombre))
