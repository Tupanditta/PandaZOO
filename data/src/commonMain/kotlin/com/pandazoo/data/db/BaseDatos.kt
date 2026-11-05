package com.pandazoo.data.db

import com.pandazoo.application.puerto.NotificadorCambios
import com.pandazoo.application.puerto.Transaccion
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Lectura de una fila de resultados; los índices de columna empiezan en 0. */
class Fila internal constructor(private val s: SentenciaSql) {
    fun texto(i: Int): String = s.getText(i)

    fun textoONulo(i: Int): String? = if (s.isNull(i)) null else s.getText(i)

    fun entero(i: Int): Int = s.getLong(i).toInt()

    fun largo(i: Int): Long = s.getLong(i)

    fun decimal(i: Int): Double = s.getDouble(i)

    fun esNulo(i: Int): Boolean = s.isNull(i)
}

/**
 * Base de datos SQLite de la aplicación, común a Android y a la web (PWA para iOS).
 *
 * El DDL de `EspecificacionBBDD.md` (sección 2) es la fuente de verdad del esquema y se aplica tal cual
 * (`schema/esquema.sql`): no hay una segunda definición que pueda divergir. Las plataformas usan el mismo
 * SQLite (el empaquetado en Android y JVM, sql.js en la web; RNF-16), así que `CHECK`, índices parciales y
 * `WITHOUT ROWID` se comportan igual.
 *
 * Una sola conexión protegida por un candado reentrante. Implementa [Transaccion] (una operación = una
 * transacción `BEGIN IMMEDIATE`, RF-45) y [NotificadorCambios] (emite tras cada confirmación para que la
 * interfaz se repinte sin sondeo, RES-10).
 */
class BaseDatos private constructor(private val conexion: ConexionSql) : Transaccion, NotificadorCambios {
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
            conexion.preparar(sql).usar { s ->
                enlazar(s, args)
                val filas = ArrayList<T>()
                val fila = Fila(s)
                while (s.step()) filas += mapa(fila)
                filas
            }
        }

    fun <T> uno(sql: String, vararg args: Any?, mapa: (Fila) -> T): T? =
        candado.conCandado {
            conexion.preparar(sql).usar { s ->
                enlazar(s, args)
                if (s.step()) mapa(Fila(s)) else null
            }
        }

    fun escribir(sql: String, vararg args: Any?) {
        val autoconfirmada = candado.conCandado {
            conexion.preparar(sql).usar { s ->
                enlazar(s, args)
                while (s.step()) {
                    // Las sentencias de escritura no devuelven filas.
                }
            }
            profundidad == 0
        }
        // Fuera de una transacción la sentencia se confirma sola; el motor de la web debe guardar esa escritura también.
        if (autoconfirmada) conexion.alConfirmar()
    }

    override fun <T> ejecutar(bloque: () -> T): T {
        var externa = false
        val resultado = candado.conCandado {
            if (profundidad > 0) return@conCandado bloque()
            externa = true
            conexion.ejecutar("BEGIN IMMEDIATE")
            profundidad++
            try {
                val r = bloque()
                conexion.ejecutar("COMMIT")
                r
            } catch (e: Throwable) {
                runCatching { conexion.ejecutar("ROLLBACK") }
                throw e
            } finally {
                profundidad--
            }
        }
        // Solo la transacción más externa notifica; una anidada se confirma con ella.
        if (externa) {
            conexion.alConfirmar()
            emisor.tryEmit(Unit)
        }
        return resultado
    }

    fun cerrar() = conexion.cerrar()

    /** Identificador de la partida activa, si existe alguna. Es un dato técnico, nunca para la interfaz. */
    fun partidaActiva(): String? =
        uno("SELECT id_partida FROM Partida WHERE estado = 'ACTIVA' ORDER BY id_partida LIMIT 1") { it.texto(0) }

    private fun enlazar(s: SentenciaSql, args: Array<out Any?>) {
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
        /**
         * Versión 2: la partida nueva empieza con 2000 monedas. Las partidas creadas con la versión 1 reciben
         * 1000 monedas de compensación una sola vez, al migrar (`migrarDe1a2`).
         */
        const val VERSION = 2

        /** Compensación única a las partidas creadas con la versión 1 (que empezaban con 1000 monedas). */
        const val COMPENSACION_V2 = 1000.0

        /** Base de datos en memoria; desaparece al cerrarla (tests). */
        fun enMemoria(): BaseDatos = abrir(":memory:", archivo = false)

        /** Base de datos en el fichero indicado por el contenedor de plataforma. */
        fun enArchivo(ruta: String): BaseDatos = abrir(ruta, archivo = true)

        private fun abrir(nombre: String, archivo: Boolean): BaseDatos = sobre(abrirConexion(nombre), archivo)

        /** Base de datos sobre una conexión ya abierta (la web la abre tras cargar el motor y los datos guardados). */
        internal fun sobre(conexion: ConexionSql, archivo: Boolean): BaseDatos {
            val bd = BaseDatos(conexion)
            bd.configurar(archivo)
            return bd
        }
    }

    private fun configurar(archivo: Boolean) {
        val nuevo = (uno("PRAGMA user_version") { it.entero(0) } ?: 0) == 0
        if (nuevo) {
            // Debe fijarse antes de crear la primera tabla y antes de activar el modo WAL.
            conexion.ejecutar("PRAGMA auto_vacuum = INCREMENTAL")
        }
        if (archivo && conexion.admiteWal) uno("PRAGMA journal_mode = WAL") { it.texto(0) }
        conexion.ejecutar("PRAGMA foreign_keys = ON")
        if (nuevo) {
            ejecutar {
                EsquemaGenerado.sentencias.forEach { conexion.ejecutar(it) }
                conexion.ejecutar("PRAGMA user_version = $VERSION")
            }
        } else {
            val actual = uno("PRAGMA user_version") { it.entero(0) }
            if (actual == 1) migrarDe1a2()
            check((uno("PRAGMA user_version") { it.entero(0) }) == VERSION) { "Versión de base de datos no soportada" }
        }
    }

    /** Suma 1000 monedas a cada partida existente y lo registra como ingreso; atómico y de una sola vez. */
    internal fun migrarDe1a2() {
        ejecutar {
            escribir(
                "INSERT INTO MovimientoEconomico (id_partida, tipo, importe, origen, numero_ciclo, fecha) " +
                    "SELECT id_partida, 'INGRESO', ?, 'COMPENSACION_INICIAL', 0, CAST(strftime('%s', 'now') AS INTEGER) FROM Partida",
                COMPENSACION_V2,
            )
            escribir("UPDATE Partida SET fondos = ROUND(fondos + ?, 2)", COMPENSACION_V2)
            conexion.ejecutar("PRAGMA user_version = 2")
        }
    }
}
