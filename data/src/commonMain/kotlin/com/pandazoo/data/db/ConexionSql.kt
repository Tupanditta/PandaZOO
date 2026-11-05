package com.pandazoo.data.db

/** Error de la base de datos (restricción `CHECK`/`FOREIGN KEY` incumplida, SQL inválido, etc.). Igual en todas las plataformas. */
open class ErrorSql(mensaje: String, causa: Throwable? = null) : RuntimeException(mensaje, causa)

/**
 * Conexión SQLite mínima que necesita [BaseDatos]. Existe para que el código común no dependa de ningún
 * motor concreto: Android y JVM usan el SQLite empaquetado de `androidx.sqlite`, y la web usa sql.js
 * (SQLite compilado a WebAssembly), que `androidx.sqlite` no soporta.
 */
interface ConexionSql {
    /** Si el motor admite el modo WAL en esta conexión (la web trabaja en memoria y lo omite). */
    val admiteWal: Boolean get() = true

    fun preparar(sql: String): SentenciaSql

    /** Ejecuta una sentencia sin parámetros ni resultados. */
    fun ejecutar(sql: String)

    /** Se invoca tras cada `COMMIT` de la transacción más externa; la web lo usa para guardar la base en el navegador. */
    fun alConfirmar() {}

    fun cerrar()
}

/** Sentencia preparada; los índices de enlace empiezan en 1 y los de columna en 0. */
interface SentenciaSql {
    fun bindNull(i: Int)

    fun bindText(i: Int, valor: String)

    fun bindLong(i: Int, valor: Long)

    fun bindDouble(i: Int, valor: Double)

    /** Avanza a la siguiente fila; `false` cuando no quedan más. */
    fun step(): Boolean

    fun getText(i: Int): String

    fun getLong(i: Int): Long

    fun getDouble(i: Int): Double

    fun isNull(i: Int): Boolean

    fun close()
}

internal inline fun <T> SentenciaSql.usar(bloque: (SentenciaSql) -> T): T =
    try {
        bloque(this)
    } finally {
        close()
    }

/** Abre la conexión del motor de la plataforma. `":memory:"` es una base volátil; otro nombre, una base persistente. */
internal expect fun abrirConexion(nombre: String): ConexionSql
