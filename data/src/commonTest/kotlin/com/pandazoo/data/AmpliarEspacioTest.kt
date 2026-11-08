package com.pandazoo.data

import com.pandazoo.application.caso.ComandoAmpliarEspacio
import com.pandazoo.application.caso.ComandoDarDeBajaEspacio
import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.domain.economia.Dinero
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class AmpliarEspacioTest {
    private lateinit var e: Entorno
    private var solicitud = 0

    @BeforeTest
    fun preparar() {
        e = Entorno()
    }

    @AfterTest
    fun cerrar() = e.cerrar()

    private fun espacio(capacidad: Int) = e.datos.espacio(e.idPartida, e.habitat("bosque_templado"), "Madriguera", capacidad)

    private fun ampliar(id: String, capacidad: Int) =
        e.ampliarEspacio.ejecutar(ComandoAmpliarEspacio(e.idPartida, id, capacidad, "sol-${++solicitud}"))

    private fun darFondos(importe: Double) =
        e.bd.escribir("UPDATE Partida SET fondos = ? WHERE id_partida = ?", Dinero.desdeDecimal(importe).paraBd(), e.idPartida)

    private fun capacidad(id: String) = e.zoo.espacio(e.idPartida, id)!!.capacidadMax

    @Test
    fun amplia_pagando_la_diferencia_mas_un_cuarto() {
        val id = espacio(10)
        darFondos(5000.0)
        val r = ampliar(id, 30) as Resultado.Exito
        r.valor.coste shouldBe Dinero.desdeDecimal(975.0)
        capacidad(id) shouldBe 30
        e.fondos() shouldBe Dinero.desdeDecimal(4025.0)
        e.zoo.costePagado(e.idPartida, id) shouldBe Dinero.desdeDecimal(975.0)
    }

    @Test
    fun no_se_puede_reducir_ni_dejar_igual() {
        val id = espacio(15)
        darFondos(5000.0)
        (ampliar(id, 10) as Resultado.Rechazado).motivo shouldBe MotivoRechazo.CapacidadNoAmpliable
        (ampliar(id, 15) as Resultado.Rechazado).motivo shouldBe MotivoRechazo.CapacidadNoAmpliable
        capacidad(id) shouldBe 15
    }

    @Test
    fun no_supera_la_capacidad_maxima() {
        val id = espacio(30)
        darFondos(5000.0)
        (ampliar(id, 35) as Resultado.Rechazado).motivo shouldBe MotivoRechazo.CapacidadNoPermitida
        (ampliar(id, 30) as Resultado.Rechazado).motivo shouldBe MotivoRechazo.CapacidadNoAmpliable
    }

    @Test
    fun saldo_insuficiente_no_cambia_nada() {
        val id = espacio(10)
        darFondos(100.0)
        (ampliar(id, 20) as Resultado.Rechazado).motivo.codigo shouldBe "SALDO_INSUFICIENTE"
        capacidad(id) shouldBe 10
        e.fondos() shouldBe Dinero.desdeDecimal(100.0)
    }

    @Test
    fun al_vender_se_recupera_lo_pagado_por_la_ampliacion() {
        val id = espacio(10)
        darFondos(2000.0)
        ampliar(id, 25)
        e.darDeBajaEspacio.ejecutar(ComandoDarDeBajaEspacio(e.idPartida, id, "sol-baja"))
        e.fondos() shouldBe Dinero.desdeDecimal(2000.0)
    }
}
