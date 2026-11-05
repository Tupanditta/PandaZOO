package com.pandazoo.data

import com.pandazoo.data.db.AlmacenWeb
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.data.db.ErrorSql
import com.pandazoo.data.db.RespaldoPersistente
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test

/**
 * Persistencia de la versión web (PWA): la base de datos se copia al respaldo (IndexedDB en el navegador) tras cada
 * confirmación y puede reabrirse desde esa copia. Aquí el respaldo es un doble que guarda los bytes en memoria.
 */
class PersistenciaWebTest {
    private class RespaldoFalso : RespaldoPersistente {
        val guardados = mutableListOf<Pair<String, ByteArray>>()

        override fun guardar(nombre: String, datos: ByteArray) {
            guardados += nombre to datos
        }
    }

    /** Espera de reloj real: el guardado usa `setTimeout`, que el reloj virtual de `runTest` no adelanta. */
    private suspend fun esperar(milisegundos: Long) = withContext(Dispatchers.Default) { delay(milisegundos) }

    private fun insertarBioma(bd: BaseDatos, id: String) =
        bd.ejecutar { bd.escribir("INSERT INTO Bioma (id_bioma, nombre) VALUES (?, ?)", id, "Bioma $id") }

    @Test
    fun `una confirmacion se guarda en el respaldo sin pasar por cerrar`() = runTest {
        val nombre = rutaTemporal("web-guardado.db")
        val respaldo = RespaldoFalso()
        AlmacenWeb.respaldo = respaldo
        val bd = BaseDatos.enArchivo(nombre)
        insertarBioma(bd, "uno")
        esperar(500)
        run {
            try {
                (respaldo.guardados.isNotEmpty()) shouldBe true
                val ultimo = respaldo.guardados.last()
                ultimo.first shouldBe nombre
                (ultimo.second.size > 4096) shouldBe true
                // Reabrir desde los bytes guardados recupera lo escrito.
                bd.cerrar()
                AlmacenWeb.cache.clear()
                AlmacenWeb.cache[nombre] = ultimo.second
                val reabierta = BaseDatos.enArchivo(nombre)
                reabierta.uno("SELECT nombre FROM Bioma WHERE id_bioma = 'uno'") { it.texto(0) } shouldBe "Bioma uno"
                reabierta.cerrar()
            } finally {
                AlmacenWeb.respaldo = null
                borrarArchivoDeBd(nombre)
            }
        }
    }

    @Test
    fun `varias confirmaciones seguidas se agrupan en un solo guardado`() = runTest {
        val nombre = rutaTemporal("web-agrupado.db")
        val respaldo = RespaldoFalso()
        AlmacenWeb.respaldo = respaldo
        val bd = BaseDatos.enArchivo(nombre)
        esperar(500)
        val antes = respaldo.guardados.size
        (1..5).forEach { insertarBioma(bd, "b$it") }
        // El temporizador aún no ha vencido: ningún guardado nuevo.
        respaldo.guardados.size shouldBe antes
        esperar(500)
        try {
            respaldo.guardados.size shouldBe 2 // el de la creación del esquema y el de las cinco inserciones
            bd.cerrar()
        } finally {
            AlmacenWeb.respaldo = null
            borrarArchivoDeBd(nombre)
        }
    }

    @Test
    fun `tras guardar siguen activas las claves foraneas`() = runTest {
        val nombre = rutaTemporal("web-fk.db")
        AlmacenWeb.respaldo = RespaldoFalso()
        val bd = BaseDatos.enArchivo(nombre)
        insertarBioma(bd, "fk")
        esperar(500)
        run {
            try {
                // `export` de sql.js reinicia los PRAGMA; la conexión debe volver a activarlos.
                bd.uno("PRAGMA foreign_keys") { it.entero(0) } shouldBe 1
                val error = shouldThrow<ErrorSql> {
                    bd.escribir("INSERT INTO Habitat (id_habitat, id_partida, codigo, id_bioma, nombre) VALUES ('h', 'no', 'c', 'no', 'n')")
                }
                (error.message ?: "").contains("FOREIGN KEY") shouldBe true
                bd.cerrar()
            } finally {
                AlmacenWeb.respaldo = null
                borrarArchivoDeBd(nombre)
            }
        }
    }

    @Test
    fun `una escritura suelta fuera de transaccion tambien se guarda`() = runTest {
        // Regresión: el temporizador del ciclo guarda su remanente con `escribir` sin transacción.
        val nombre = rutaTemporal("web-suelta.db")
        val respaldo = RespaldoFalso()
        AlmacenWeb.respaldo = respaldo
        val bd = BaseDatos.enArchivo(nombre)
        try {
            esperar(500)
            val antes = respaldo.guardados.size
            bd.escribir("INSERT INTO Bioma (id_bioma, nombre) VALUES ('suelta', 'Suelta')")
            esperar(500)
            respaldo.guardados.size shouldBe antes + 1
        } finally {
            bd.cerrar()
            AlmacenWeb.respaldo = null
            borrarArchivoDeBd(nombre)
        }
    }

    @Test
    fun `una base en memoria no toca el respaldo`() {
        val respaldo = RespaldoFalso()
        AlmacenWeb.respaldo = respaldo
        try {
            val bd = BaseDatos.enMemoria()
            insertarBioma(bd, "mem")
            bd.cerrar()
            respaldo.guardados.size shouldBe 0
        } finally {
            AlmacenWeb.respaldo = null
        }
    }
}
