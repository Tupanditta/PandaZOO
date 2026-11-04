package com.pandazoo.domain.economia

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** TM.2 Equivalencia del dinero con los casos del Sprint 1. */
class DineroTest {
    @Test
    fun `TM_2 coste es cantidad por precio`() {
        Dinero.coste(10, Dinero.desdeDecimal(0.5)).toString() shouldBe "5.00"
        Dinero.coste(7, Dinero.desdeDecimal(10.0)).toString() shouldBe "70.00"
        Dinero.coste(3, Dinero.desdeDecimal(0.5)).toString() shouldBe "1.50"
    }

    @Test
    fun `TM_2 suma resta y comparacion exactas en el limite`() {
        val saldo = Dinero.desdeDecimal(1000.0)
        val coste = Dinero.coste(100, Dinero.desdeDecimal(10.0))
        (saldo >= coste) shouldBe true
        (saldo - coste).toString() shouldBe "0.00"
        (saldo < Dinero.coste(101, Dinero.desdeDecimal(10.0))) shouldBe true
        (Dinero.desdeDecimal(0.1) + Dinero.desdeDecimal(0.2)).toString() shouldBe "0.30"
    }

    @Test
    fun `TM_2 ida y vuelta con la base de datos no pierde centimos`() {
        for (c in listOf(0L, 1L, 9L, 99L, 100L, 9999L, 100_000L, 123_456_789L)) {
            Dinero.desdeBd(Dinero.deCentimos(c).paraBd()) shouldBe Dinero.deCentimos(c)
        }
    }

    @Test
    fun `TM_2 formato con dos decimales`() {
        Dinero.deCentimos(100_000).toString() shouldBe "1000.00"
        Dinero.deCentimos(5).toString() shouldBe "0.05"
        Dinero.deCentimos(99_99).toString() shouldBe "99.99"
        Dinero.CERO.toString() shouldBe "0.00"
    }
}
