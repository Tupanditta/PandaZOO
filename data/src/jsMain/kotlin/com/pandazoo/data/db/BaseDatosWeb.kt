package com.pandazoo.data.db

import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

/**
 * Apertura de la base de datos en el navegador. El motor SQL (sql.js) y el contenido guardado se cargan de forma
 * asíncrona; después la base se usa de forma síncrona como en cualquier otra plataforma y cada confirmación se
 * copia a IndexedDB, de modo que el progreso sobrevive al cierre de la pestaña.
 */
object BaseDatosWeb {
    private const val NOMBRE_IDB = "pandazoo"
    private const val ALMACEN = "bd"

    /** Carga sql.js si la página no lo ha hecho ya y abre la base guardada con ese [nombre] (o crea una nueva). */
    suspend fun abrir(nombre: String = "pandazoo.db"): BaseDatos {
        cargarMotor()
        if (AlmacenWeb.respaldo == null) {
            AlmacenWeb.respaldo = RespaldoIndexedDb
            RespaldoIndexedDb.leer(nombre)?.let { AlmacenWeb.cache[nombre] = it }
        }
        return BaseDatos.enArchivo(nombre)
    }

    /** Pide al navegador que no borre los datos de la aplicación por falta de espacio (no siempre lo concede). */
    suspend fun pedirAlmacenamientoPersistente(): Boolean =
        try {
            val almacenamiento = js("navigator.storage")
            if (almacenamiento == null || almacenamiento.persist == null) {
                false
            } else {
                (almacenamiento.persist() as Promise<Boolean>).await()
            }
        } catch (e: Throwable) {
            false
        }

    private suspend fun cargarMotor() {
        if (js("globalThis.__pandazooSqlJs") != null) return
        val inicializar = js("globalThis.initSqlJs") ?: throw ErrorSql("Falta sql-wasm-browser.js en la página")
        val config = js("({})")
        config.locateFile = { archivo: String -> archivo }
        js("globalThis").__pandazooSqlJs = (inicializar(config) as Promise<dynamic>).await()
    }

    private object RespaldoIndexedDb : RespaldoPersistente {
        private var conexion: Promise<dynamic>? = null

        private fun idb(): Promise<dynamic> =
            conexion ?: Promise<dynamic> { resolver, rechazar ->
                val nombreIdb = NOMBRE_IDB
                val peticion = js("indexedDB.open(nombreIdb, 1)")
                peticion.onupgradeneeded = { _: dynamic -> peticion.result.createObjectStore(ALMACEN) }
                peticion.onsuccess = { _: dynamic -> resolver(peticion.result) }
                peticion.onerror = { _: dynamic -> rechazar(Error("IndexedDB no está disponible")) }
            }.also { conexion = it }

        suspend fun leer(nombre: String): ByteArray? {
            val base = idb().await()
            val resultado = Promise<dynamic> { resolver, rechazar ->
                val peticion = base.transaction(ALMACEN, "readonly").objectStore(ALMACEN).get(nombre)
                peticion.onsuccess = { _: dynamic -> resolver(peticion.result) }
                peticion.onerror = { _: dynamic -> rechazar(Error("No se pudo leer la partida guardada")) }
            }.await()
            if (resultado == null) return null
            val u8 = resultado.unsafeCast<Uint8Array>()
            return Int8Array(u8.buffer, u8.byteOffset, u8.length).unsafeCast<ByteArray>().copyOf()
        }

        override fun guardar(nombre: String, datos: ByteArray) {
            val i8 = datos.unsafeCast<Int8Array>()
            val u8 = Uint8Array(i8.buffer, i8.byteOffset, i8.length)
            idb().then<Unit>(
                { base: dynamic ->
                    val tx = base.transaction(ALMACEN, "readwrite")
                    tx.objectStore(ALMACEN).put(u8, nombre)
                    tx.onerror = { _: dynamic -> console.error("PandaZOO: no se pudo guardar la partida", tx.error) }
                },
                { error: Throwable -> console.error("PandaZOO: IndexedDB no disponible", error) },
            )
        }
    }
}
