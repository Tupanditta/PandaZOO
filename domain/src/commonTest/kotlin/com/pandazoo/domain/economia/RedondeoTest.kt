package com.pandazoo.domain.economia

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** T0.1 Redondeo al centésimo con medios hacia arriba. Mismos casos que la primera versión con BigDecimal. */
class RedondeoTest {
    @Test
    fun `T0_1 dinero medio hacia arriba`() {
        Redondeo.dinero(0.005) shouldBe Dinero.deCentimos(1)
        Redondeo.dinero(0.004) shouldBe Dinero.deCentimos(0)
        Redondeo.dinero(1.235) shouldBe Dinero.deCentimos(124)
        Redondeo.dinero(0.5).toString() shouldBe "0.50"
        Redondeo.dinero(10.0).toString() shouldBe "10.00"
    }

    @Test
    fun `T0_1 porcentaje entero medio hacia arriba`() {
        Redondeo.porcentaje(0.495) shouldBe 50
        Redondeo.porcentaje(0.494) shouldBe 49
        Redondeo.porcentaje(0.0) shouldBe 0
        Redondeo.porcentaje(1.0) shouldBe 100
    }

    @Test
    fun `T0_1 umbral de animales de prestigio medio hacia arriba`() {
        Redondeo.entero(2.5) shouldBe 3
        Redondeo.entero(2.4999) shouldBe 2
        Redondeo.entero(41.322094) shouldBe 41
    }

    @Test
    fun `TM_2 valores en notacion cientifica y muy pequenos se leen sin error`() {
        Redondeo.dinero(1.0E-5) shouldBe Dinero.CERO
        Redondeo.dinero(5.0E-3) shouldBe Dinero.deCentimos(1)
        Redondeo.dinero(1.0E7) shouldBe Dinero.deCentimos(1_000_000_000)
        Decimales.decimales(0.5) shouldBe 1
        Decimales.decimales(10.0) shouldBe 0
        Decimales.decimales(1.234) shouldBe 3
    }
}
