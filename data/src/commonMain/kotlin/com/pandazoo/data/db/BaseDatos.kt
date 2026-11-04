package com.pandazoo.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.pandazoo.application.puerto.NotificadorCambios
import com.pandazoo.application.puerto.Transaccion
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Lectura de una fila de resultados; los índices de columna empiezan en 0. */
class Fila internal constructor(private val s: SQLiteStatement) {
    fun texto(i: Int): String = s.getText(i)

    fun textoONulo(i: Int): String? = if (s.isNull(i)) null else s.getText(i)

    fun entero(i: Int): Int = s.getLong(i).toInt()

    fun largo(i: Int): Long = s.getLong(i)

    fun decimal(i: Int): Double = s.getDouble(i)

    fun esNulo(i: Int): Boolean = s.isNull(i)
}

/**
 * Base de datos SQLite de la aplicación, común a Android e iOS.
 *
 * El DDL de `EspecificacionBBDD.md` (sección 2) es la fuente de verdad del esquema y se aplica tal cual
 * (`schema/esquema.sql`): no hay una segunda definición que pueda divergir. Ambas plataformas usan el mismo
 * SQLite empaquetado (RNF-16), así que `CHECK`, índices parciales y `WITHOUT ROWID` se comportan igual.
 *
 * Una sola conexión protegida por un candado reentrante. Implementa [Transaccion] (una operación = una
 * transacción `BEGIN IMMEDIATE`, RF-45) y [NotificadorCambios] (emite tras cada confirmación para que la
 * interfaz se repinte sin sondeo, RES-10).
 */
class BaseDatos private constructor(private val conexion: SQLiteConnection) : Transaccion, NotificadorCambios {
    private val candado = Candado()
    private var profundidad = 0

    private val emisor = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val cambios: Flow<Unit> get() = emisor

    fun <T> leer(sql: String, vararg args: Any?, mapa: (Fila) -> T): List<T> =
        candado.conCandado {
            conexion.prepare(sql).use { s ->
                enlazar(s, args)
                val filas = ArrayList<T>()
                val fila = Fila(s)
                while (s.step()) filas += mapa(fila)
                filas
            }
        }

    fun <T> uno(sql: String, vararg args: Any?, mapa: (Fila) -> T): T? =
        candado.conCandado {
            conexion.prepare(sql).use { s ->
                enlazar(s, args)
                if (s.step()) mapa(Fila(s)) else null
            }
        }

    fun escribir(sql: String, vararg args: Any?) {
        candado.conCandado {
            conexion.prepare(sql).use { s ->
                enlazar(s, args)
                while (s.step()) {
                    // Las sentencias de escritura no devuelven filas.
                }
            }
        }
    }

    override fun <T> ejecutar(bloque: () -> T): T {
        var externa = false
        val resultado = candado.conCandado {
            if (profundidad > 0) return@conCandado bloque()
            externa = true
            conexion.execSQL("BEGIN IMMEDIATE")
            profundidad++
            try {
                val r = bloque()
                conexion.execSQL("COMMIT")
                r
            } catch (e: Throwable) {
                runCatching { conexion.execSQL("ROLLBACK") }
                throw e
            } finally {
                profundidad--
            }
        }
        // Solo la transacción más externa notifica; una anidada se confirma con ella.
        if (externa) emisor.tryEmit(Unit)
        return resultado
    }

    fun cerrar() = conexion.close()

    /** Identificador de la partida activa, si existe alguna. Es un dato técnico, nunca para la interfaz. */
    fun partidaActiva(): String? =
        uno("SELECT id_partida FROM Partida WHERE estado = 'ACTIVA' ORDER BY id_partida LIMIT 1") { it.texto(0) }

    private fun enlazar(s: SQLiteStatement, args: Array<out Any?>) {
        args.forEachIndexed { i, a ->
            val p = i + 1
            when (a) {
                null -> s.bindNull(p)
                is String -> s.bindText(p, a)
                is Int -> s.bindLong(p, a.toLong())
                is Long -> s.bindLong(p, a)
                is Double -> s.bindDouble(p, a)
                is Boolean -> s.bindLong(p, if (a) 1 else 0)
                else -> error("Tipo de parámetro no soportado: ${a::class.simpleName}")
            }
        }
    }

    companion object {
        const val VERSION = 1

        /** Base de datos en memoria; desaparece al cerrarla (tests). */
        fun enMemoria(): BaseDatos = abrir(":memory:", archivo = false)

        /** Base de datos en el fichero indicado por el contenedor de plataforma. */
        fun enArchivo(ruta: String): BaseDatos = abrir(ruta, archivo = true)

        private fun abrir(nombre: String, archivo: Boolean): BaseDatos {
            val conexion = BundledSQLiteDriver().open(nombre)
            val bd = BaseDatos(conexion)
            bd.configurar(archivo)
            return bd
        }
    }

    private fun configurar(archivo: Boolean) {
        val nuevo = (uno("PRAGMA user_version") { it.entero(0) } ?: 0) == 0
        if (nuevo) {
            // Debe fijarse antes de crear la primera tabla y antes de activar el modo WAL.
            conexion.execSQL("PRAGMA auto_vacuum = INCREMENTAL")
        }
        if (archivo) uno("PRAGMA journal_mode = WAL") { it.texto(0) }
        conexion.execSQL("PRAGMA foreign_keys = ON")
        if (nuevo) {
            ejecutar {
                EsquemaGenerado.sentencias.forEach { conexion.execSQL(it) }
                conexion.execSQL("PRAGMA user_version = $VERSION")
            }
        } else {
            check((uno("PRAGMA user_version") { it.entero(0) }) == VERSION) { "Versión de base de datos no soportada" }
        }
    }
}
