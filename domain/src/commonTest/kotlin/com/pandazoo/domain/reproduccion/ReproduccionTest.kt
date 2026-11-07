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
    fun `la probabilidad nunca es negativa`() {
        ReglasReproduccion.probabilidad(0.5, 0.2, 0) shouldBe 0.5
        ReglasReproduccion.probabilidad(0.5, 0.2, 2) shouldBe (0.5 - 0.2 * 2)
        ReglasReproduccion.probabilidad(0.5, 0.2, 3) shouldBe 0.0
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
