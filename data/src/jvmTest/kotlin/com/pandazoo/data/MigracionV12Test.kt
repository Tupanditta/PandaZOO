package com.pandazoo.data

import com.pandazoo.data.db.BaseDatos
import com.pandazoo.data.db.abrirConexion
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.test.Test

class MigracionV12Test {
    @Test
    fun `la migracion a la version 12 anade los costes de 25 y 30`() {
        val f = File.createTempFile("pandazoo-v11", ".db")
        f.delete()
        BaseDatos.enArchivo(f.absolutePath).cerrar()
        val c = abrirConexion(f.absolutePath)
        c.ejecutar("DELETE FROM CosteEspacio WHERE capacidad_max IN (25, 30)")
        c.ejecutar("PRAGMA user_version = 11")
        c.cerrar()
        val bd = BaseDatos.enArchivo(f.absolutePath)
        try {
            bd.uno("PRAGMA user_version") { it.entero(0) } shouldBe 12
            bd.leer("SELECT capacidad_max FROM CosteEspacio ORDER BY capacidad_max") { it.entero(0) } shouldBe listOf(25, 30)
        } finally {
            bd.cerrar()
            f.delete()
        }
    }
}
