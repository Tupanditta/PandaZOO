package com.pandazoo.ui

import com.pandazoo.application.caso.RecompensaReclamada
import com.pandazoo.application.caso.RegaloSemanalConcedido
import com.pandazoo.application.puerto.TipoRecompensa
import com.pandazoo.data.inicial.AnimalDeBienvenida
import com.pandazoo.data.inicial.RegaloBienvenida
import com.pandazoo.domain.config.ProbabilidadRango
import com.pandazoo.ui.pantalla.Textos
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** El panda luce toques de diamante, pero sigue siendo un animal exclusivo. */
class DiamanteTest {
    private fun regalo(idEspecie: String, nombre: String, idRango: Int) =
        RegaloSemanalConcedido("lunes:2026-11-09", "Regalo del lunes", idEspecie, nombre, idRango, listOf(ProbabilidadRango(5, 100)))

    @Test
    fun `solo el panda es diamante`() {
        Textos.esDiamante("panda") shouldBe true
        Textos.esDiamante("leon") shouldBe false
        Textos.esDiamante(null) shouldBe false
    }

    @Test
    fun `el premio del panda es diamante y sigue siendo exclusivo`() {
        val panda = Textos.regaloSemanal(regalo("panda", "Panda", 5))
        panda.diamante shouldBe true
        panda.exclusivo shouldBe true
        panda.rango shouldBe "Exclusivo"
    }

    @Test
    fun `el panda tambien es diamante al tocar un animal gratis aleatorio de una mision`() {
        val sorteo = listOf(ProbabilidadRango(5, 100))
        val panda = Textos.premio(RecompensaReclamada(TipoRecompensa.ESPECIE, 1.0, null, "Panda", false, 5, sorteo, "panda"))
        panda.diamante shouldBe true
        panda.exclusivo shouldBe true
        val otro = Textos.premio(RecompensaReclamada(TipoRecompensa.ESPECIE, 1.0, null, "Ballena azul", false, 5, sorteo, "ballena_azul"))
        otro.diamante shouldBe false
    }

    @Test
    fun `el panda del regalo de bienvenida tambien es diamante`() {
        val regalo = RegaloBienvenida(
            2000.0, 30, 3,
            listOf(AnimalDeBienvenida("panda", "Panda", 5, listOf(ProbabilidadRango(2, 50), ProbabilidadRango(5, 50)))),
        )
        Textos.bienvenida(regalo).last().diamante shouldBe true
    }

    @Test
    fun `otro exclusivo es exclusivo pero no diamante`() {
        val otro = Textos.regaloSemanal(regalo("ballena_azul", "Ballena azul", 5))
        otro.diamante shouldBe false
        otro.exclusivo shouldBe true
    }

    @Test
    fun `un animal no exclusivo no es diamante`() {
        val comun = Textos.regaloSemanal(regalo("erizo", "Erizo", 1))
        comun.diamante shouldBe false
        comun.exclusivo shouldBe false
    }
}
