package com.pandazoo.data.db

import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

/**
 * Lugar donde viven los bytes de las bases de datos con nombre. [cache] es la fuente síncrona que lee
 * [abrirConexion]; el [respaldo] (IndexedDB en el navegador) recibe cada copia nueva para que sobreviva al cierre
 * de la pestaña. En los tests de Node no hay respaldo y la caché hace de disco.
 */
internal object AlmacenWeb {
    val cache = HashMap<String, ByteArray>()
    var respaldo: RespaldoPersistente? = null
}

internal interface RespaldoPersistente {
    fun guardar(nombre: String, datos: ByteArray)
}

/** Motor sql.js ya cargado: lo deja en `globalThis.__pandazooSqlJs` la página (navegador) o el preludio de los tests (Node). */
private fun motor(): dynamic =
    js("globalThis.__pandazooSqlJs") ?: throw ErrorSql("El motor SQL (sql.js) no está cargado")

private fun nuevaBd(datos: ByteArray?): dynamic {
    val sql = motor()
    return if (datos == null) {
        js("new sql.Database()")
    } else {
        val i8 = datos.unsafeCast<Int8Array>()
        val u8 = Uint8Array(i8.buffer, i8.byteOffset, i8.length)
        js("new sql.Database(u8)")
    }
}

internal class ConexionSqlJs(private var bd: dynamic, private val nombre: String?) : ConexionSql {
    /** sql.js no admite WAL: la base vive en memoria y se guarda entera tras cada confirmación. */
    override val admiteWal: Boolean get() = false

    private var sucia = false
    private var temporizador: dynamic = null
    private var cerrada = false

    init {
        if (nombre != null) {
            // Guardar al ocultar o cerrar la pestaña: en iOS es la última oportunidad antes de que el sistema suspenda la página.
            val g = js("globalThis")
            if (g.addEventListener != null) {
                g.addEventListener("pagehide", { _: dynamic -> guardarAhora() })
                g.addEventListener("visibilitychange", { _: dynamic ->
                    if (g.document != null && g.document.visibilityState == "hidden") guardarAhora()
                })
            }
        }
    }

    override fun preparar(sql: String): SentenciaSql = traducir { SentenciaSqlJs(bd.prepare(sql)) }

    override fun ejecutar(sql: String) {
        traducir { bd.run(sql) }
    }

    override fun alConfirmar() {
        if (nombre == null) return
        sucia = true
        // Con la página oculta puede que no haya otra oportunidad: se guarda ya, sin esperar al temporizador.
        val g = js("globalThis")
        if (g.document != null && g.document.visibilityState == "hidden") {
            guardarAhora()
            return
        }
        if (temporizador == null) {
            // Se agrupa lo que llegue en un instante; el guardado no bloquea la interfaz.
            temporizador = js("setTimeout")({ guardarAhora() }, 200)
        }
    }

    /** Copia la base entera a la caché y al respaldo. No hace nada si no hay cambios pendientes. */
    fun guardarAhora(forzar: Boolean = false) {
        if (nombre == null || cerrada) return
        if (temporizador != null) {
            js("clearTimeout")(temporizador)
            temporizador = null
        }
        if (!sucia && !forzar) return
        sucia = false
        // `export` cierra y reabre la base de sql.js, lo que devuelve los PRAGMA a su valor por defecto.
        val u8 = bd.export().unsafeCast<Uint8Array>()
        bd.run("PRAGMA foreign_keys = ON")
        val datos = Int8Array(u8.buffer, u8.byteOffset, u8.length).unsafeCast<ByteArray>().copyOf()
        AlmacenWeb.cache[nombre] = datos
        AlmacenWeb.respaldo?.guardar(nombre, datos)
    }

    override fun cerrar() {
        guardarAhora(forzar = true)
        cerrada = true
        bd.close()
    }
}

private class SentenciaSqlJs(private val s: dynamic) : SentenciaSql {
    private val parametros = ArrayList<Any?>()
    private var enlazada = false
    private var fila: dynamic = null

    private fun fijar(i: Int, valor: Any?) {
        while (parametros.size < i) parametros.add(null)
        parametros[i - 1] = valor
    }

    override fun bindNull(i: Int) = fijar(i, null)

    override fun bindText(i: Int, valor: String) = fijar(i, valor)

    override fun bindLong(i: Int, valor: Long) = fijar(i, valor.toDouble())

    override fun bindDouble(i: Int, valor: Double) = fijar(i, valor)

    override fun step(): Boolean = traducir {
        if (!enlazada) {
            enlazada = true
            if (parametros.isNotEmpty()) s.bind(parametros.toTypedArray())
        }
        val hay = s.step() as Boolean
        fila = if (hay) s.get() else null
        hay
    }

    override fun getText(i: Int): String {
        val v = fila[i]
        return if (v == null) "" else v.toString()
    }

    override fun getLong(i: Int): Long {
        val v = fila[i]
        return when {
            v == null -> 0L
            jsTypeOf(v) == "number" -> (v as Double).toLong()
            else -> v.toString().toLongOrNull() ?: 0L
        }
    }

    override fun getDouble(i: Int): Double {
        val v = fila[i]
        return when {
            v == null -> 0.0
            jsTypeOf(v) == "number" -> v as Double
            else -> v.toString().toDoubleOrNull() ?: 0.0
        }
    }

    override fun isNull(i: Int): Boolean = fila[i] == null

    override fun close() {
        s.free()
    }
}

private inline fun <T> traducir(bloque: () -> T): T =
    try {
        bloque()
    } catch (e: ErrorSql) {
        throw e
    } catch (e: Throwable) {
        throw ErrorSql(e.message ?: "Error de SQLite", e)
    }

internal actual fun abrirConexion(nombre: String): ConexionSql {
    val persistente = nombre != ":memory:"
    return ConexionSqlJs(nuevaBd(if (persistente) AlmacenWeb.cache[nombre] else null), if (persistente) nombre else null)
}
