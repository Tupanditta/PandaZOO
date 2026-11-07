package com.pandazoo.domain.ciclo

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** T5.3 a T5.7 (parte de dominio): alimentación, salud, contadores críticos y defunción. */
class CicloTest {
    @Test
    fun `T5_3 la alimentacion baja 20 puntos sin bajar de cero`() {
        var n = 100
        val serie = List(6) { n = ReglasCiclo.alimentacionTrasCiclo(n); n }
        serie shouldBe listOf(80, 60, 40, 20, 0, 0)
        ReglasCiclo.alimentacionTrasCiclo(10) shouldBe 0
    }

    @Test
    fun `T5_4 la enfermedad usa estrictamente menor que`() {
        // Probabilidad 0: nunca enferma, ni con aleatorio 0.
        ReglasCiclo.enferma(0.0, 0.0, 0.0) shouldBe false
        // Satisfacción 1: 1 - S = 0, nunca enferma.
        ReglasCiclo.enferma(0.0, 1.0, 1.0) shouldBe false
        // 1 - S = 1 y probabilidad 1: umbral 1, cualquier aleatorio en [0,1) enferma.
        ReglasCiclo.enferma(0.999999, 1.0, 0.0) shouldBe true
        // Frontera: umbral 0.5 · (1 - 0.5) = 0.25; con aleatorio == umbral no enferma.
        ReglasCiclo.enferma(0.25, 0.5, 0.5) shouldBe false
        ReglasCiclo.enferma(0.2499, 0.5, 0.5) shouldBe true
    }

    @Test
    fun `T5_5 contadores independientes y reinicio al recuperarse`() {
        ReglasCiclo.contadorDesnutricion(0, 0) shouldBe 1
        ReglasCiclo.contadorDesnutricion(2, 0) shouldBe 3
        ReglasCiclo.contadorDesnutricion(2, 20) shouldBe 0
        ReglasCiclo.contadorEnfermedad(1, true) shouldBe 2
        ReglasCiclo.contadorEnfermedad(2, false) shouldBe 0
        ReglasCiclo.contadorCritico(1, 2) shouldBe 2
        ReglasCiclo.ciclosRestantes(1, 2) shouldBe 1
        ReglasCiclo.ciclosRestantes(3, 0) shouldBe 0
        ReglasCiclo.ciclosRestantes(0, 0) shouldBe 3
    }

    @Test
    fun `T5_5 tres ciclos criticos provocan la muerte y con ambos cuenta el mayor`() {
        ReglasCiclo.causaDeMuerte(3, 0) shouldBe CausaMuerte.CICLOS_CRITICOS
        ReglasCiclo.causaDeMuerte(1, 3) shouldBe CausaMuerte.CICLOS_CRITICOS
        ReglasCiclo.causaDeMuerte(2, 2) shouldBe null
    }

    @Test
    fun `TN_7 ya no hay muerte por vejez y solo los contadores criticos matan`() {
        ReglasCiclo.causaDeMuerte(0, 0) shouldBe null
        ReglasCiclo.causaDeMuerte(2, 2) shouldBe null
        ReglasCiclo.causaDeMuerte(0, 3) shouldBe CausaMuerte.CICLOS_CRITICOS
    }
}
