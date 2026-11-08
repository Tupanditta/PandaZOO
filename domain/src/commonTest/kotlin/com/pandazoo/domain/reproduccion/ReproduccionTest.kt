package com.pandazoo.domain.reproduccion

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ReproduccionTest {
    @Test
    fun `el umbral es inclusivo y exige a los dos progenitores`() {
        ReglasReproduccion.cumpleUmbral(0.5, 0.5, 0.5) shouldBe true
        ReglasReproduccion.cumpleUmbral(0.4999, 0.9, 0.5) shouldBe false
        ReglasReproduccion.cumpleUmbral(0.9, 0.4999, 0.5) shouldBe false
    }

    @Test
    fun `la probabilidad es la base por el factor de los hijos vivos y se anula en el tope`() {
        val factores = listOf(1.0, 0.5, 0.25, 0.1, 0.05, 0.0)
        ReglasReproduccion.probabilidad(0.04, factores, 0) shouldBe 0.04
        ReglasReproduccion.probabilidad(0.04, factores, 1) shouldBe 0.04 * 0.5
        ReglasReproduccion.probabilidad(0.04, factores, 4) shouldBe 0.04 * 0.05
        ReglasReproduccion.probabilidad(0.04, factores, ReglasReproduccion.TOPE_HIJOS) shouldBe 0.0
        ReglasReproduccion.probabilidad(0.04, factores, 9) shouldBe 0.0
    }

    @Test
    fun `concibe solo si el aleatorio es estrictamente menor`() {
        ReglasReproduccion.concibe(0.0, 0.0) shouldBe false
        ReglasReproduccion.concibe(0.999, 1.0) shouldBe true
        ReglasReproduccion.concibe(0.25, 0.25) shouldBe false
    }

    @Test
    fun `la ventana va del ciclo siguiente a tres ciclos de disponibilidad`() {
        ReglasReproduccion.cicloDisponible(7) shouldBe 8
        ReglasReproduccion.cicloExpiracion(7) shouldBe 10
        ReglasReproduccion.vigente(10, 10) shouldBe true
        ReglasReproduccion.vigente(11, 10) shouldBe false
    }

    @Test
    fun `el progenitor de identificador menor es el padre`() {
        ReglasReproduccion.progenitores("b", "a") shouldBe ("a" to "b")
        ReglasReproduccion.progenitores("a", "b") shouldBe ("a" to "b")
    }
}
