package com.pandazoo.data

import com.pandazoo.data.db.BaseDatos
import com.pandazoo.data.db.abrirConexion
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.test.Test

class MigracionV13Test {
    @Test
    fun `la migracion a la version 13 multiplica por 5 compra y venta y cambia la reproduccion`() {
        val f = File.createTempFile("pandazoo-v12", ".db")
        f.delete()
        BaseDatos.enArchivo(f.absolutePath).cerrar()
        val c = abrirConexion(f.absolutePath)
        c.ejecutar("PRAGMA foreign_keys = OFF")
        // Estado de la versión 12: sin catálogo cargado, así que se siembran los rangos con sus valores antiguos.
        c.ejecutar("DELETE FROM CosteRango")
        c.ejecutar("DELETE FROM RangoEspecie")
        val antiguos = listOf(Triple(1, 40.0, 0.04), Triple(2, 160.0, 0.03), Triple(3, 450.0, 0.02), Triple(4, 700.0, 0.015), Triple(5, 4000.0, 0.01))
        antiguos.forEach { (id, venta, prob) ->
            c.ejecutar(
                "INSERT INTO RangoEspecie (id_rango, nombre, umbral_reproduccion, probabilidad_reproduccion, penalizacion_hijo_reproduccion, " +
                    "probabilidad_enfermedad, beneficio_base_animal_ciclo, nivel_maximo, multiplicador_nivel_maximo, valor_venta_base, " +
                    "factor_venta_nivel_maximo) VALUES ($id, 'R$id', 0.6, $prob, 0.01, 0.02, 6.0, 15, 3.0, $venta, 1.5)",
            )
            c.ejecutar("INSERT INTO CosteRango (id_rango, importe) VALUES ($id, ${if (id == 5) 0.0 else venta})")
        }
        c.ejecutar("ALTER TABLE RangoEspecie DROP COLUMN factores_hijos")
        c.ejecutar("PRAGMA user_version = 12")
        c.cerrar()

        val bd = BaseDatos.enArchivo(f.absolutePath)
        try {
            bd.uno("PRAGMA user_version") { it.entero(0) } shouldBe BaseDatos.VERSION
            bd.leer("SELECT importe FROM CosteRango ORDER BY id_rango") { it.decimal(0) } shouldBe listOf(200.0, 800.0, 2250.0, 3500.0, 0.0)
            bd.leer("SELECT valor_venta_base FROM RangoEspecie ORDER BY id_rango") { it.decimal(0) } shouldBe
                listOf(200.0, 800.0, 2250.0, 3500.0, 4000.0)
            bd.leer("SELECT probabilidad_reproduccion FROM RangoEspecie ORDER BY id_rango") { it.decimal(0) } shouldBe
                listOf(0.03, 0.0225, 0.015, 0.011, 0.0075)
            bd.uno("SELECT factores_hijos FROM RangoEspecie WHERE id_rango = 1") { it.texto(0) } shouldBe "1;0.125;0.0625;0.04;0.01;0"
            bd.uno("SELECT penalizacion_hijo_reproduccion FROM RangoEspecie WHERE id_rango = 1") { it.decimal(0) } shouldBe 0.0
        } finally {
            bd.cerrar()
            f.delete()
        }
    }
}
