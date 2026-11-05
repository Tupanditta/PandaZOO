package com.pandazoo.domain.ciclo

import com.pandazoo.domain.animal.Edad
import com.pandazoo.domain.economia.Dinero
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** T5.2 a T5.7 (parte de dominio): edad, alimentación, salud, contadores críticos, longevidad y beneficio. */
class CicloTest {
    private fun d(v: Double) = Dinero.desdeDecimal(v)

    @Test
    fun `T5_2 cinco ciclos son 30 dias y un mes y la transicion de anio es correcta`() {
        var e = Edad(20, 0, 0)
        repeat(5) { e = ReglasCiclo.edadTrasCiclo(e) }
        e shouldBe Edad(20, 1, 0)
        ReglasCiclo.edadTrasCiclo(Edad(20, 11, 24)) shouldBe Edad(21, 0, 0)
        ReglasCiclo.edadTrasCiclo(Edad(20, 11, 29)) shouldBe Edad(21, 0, 5)
        var dias = 0
        repeat(60) { dias = ReglasCiclo.edadTrasCiclo(dias) }
        dias shouldBe 360
    }

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
        ReglasCiclo.causaDeMuerte(100, 1000, 3, 0) shouldBe CausaMuerte.CICLOS_CRITICOS
        ReglasCiclo.causaDeMuerte(100, 1000, 1, 3) shouldBe CausaMuerte.CICLOS_CRITICOS
        ReglasCiclo.causaDeMuerte(100, 1000, 2, 2) shouldBe null
    }

    @Test
    fun `T5_6 la longevidad se alcanza con edad mayor o igual que la maxima`() {
        ReglasCiclo.causaDeMuerte(999, 1000, 0, 0) shouldBe null
        ReglasCiclo.causaDeMuerte(1000, 1000, 0, 0) shouldBe CausaMuerte.LONGEVIDAD
        ReglasCiclo.causaDeMuerte(1005, 1000, 0, 0) shouldBe CausaMuerte.LONGEVIDAD
    }

    @Test
    fun `T5_7 beneficio con casos calculados a mano`() {
        // 0.5 · (4,00 + 8,00 + 14,00) = 13,00
        ReglasCiclo.beneficio(0.5, listOf(d(4.0), d(8.0), d(14.0))) shouldBe d(13.0)
        // Satisfacción fraccionaria: 0,3333 · (22 + 35) = 18,9981 → 19,00
        ReglasCiclo.beneficio(0.3333, listOf(d(22.0), d(35.0))) shouldBe d(19.0)
        // 0,125 · 4,00 = 0,50 y medio hacia arriba: 0,125 · 4,20 = 0,525 → 0,53
        ReglasCiclo.beneficio(0.125, listOf(d(4.2))) shouldBe d(0.53)
        ReglasCiclo.beneficio(1.0, emptyList()) shouldBe Dinero.CERO
        ReglasCiclo.beneficio(0.0, listOf(d(35.0))) shouldBe Dinero.CERO
    }
}
