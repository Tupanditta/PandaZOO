package com.pandazoo.domain.mision

import com.pandazoo.domain.config.ProbabilidadRango
import com.pandazoo.domain.config.SorteoPrestigioCfg
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Sorteo por rango: primero el rango según una tabla y después la especie dentro del rango. */
class SorteoAnimalesTest {
    private val porRango = mapOf(
        2 to listOf(EspecieElegible("a", 1), EspecieElegible("b", 1)),
        3 to listOf(EspecieElegible("c", 1)),
        4 to listOf(EspecieElegible("d", 1)),
        5 to listOf(EspecieElegible("x", 30), EspecieElegible("y", 70)),
    )
    private val prestigio = SorteoPrestigioCfg()
    private val sorteo = SorteoAnimales(porRango, prestigio)
    private fun tabla(vararg p: Pair<Int, Int>) = p.map { ProbabilidadRango(it.first, it.second) }

    @Test
    fun `la tabla de prestigio depende de si es multiplo de 10, de 5 o ninguno`() {
        sorteo.tablaPrestigio(10) shouldBe prestigio.multiplo10
        sorteo.tablaPrestigio(100) shouldBe prestigio.multiplo10
        sorteo.tablaPrestigio(5) shouldBe prestigio.multiplo5
        sorteo.tablaPrestigio(15) shouldBe prestigio.multiplo5
        sorteo.tablaPrestigio(1) shouldBe prestigio.base
        sorteo.tablaPrestigio(7) shouldBe prestigio.base
        sorteo.tablaPrestigio(0) shouldBe prestigio.base
    }

    @Test
    fun `el rango se elige por tramos acumulados con el limite exclusivo`() {
        val t = tabla(2 to 50, 3 to 50)
        sorteo.sortear(t, 0.0, 0.0) shouldBe "a"
        sorteo.sortear(t, 0.4999, 0.0) shouldBe "a"
        sorteo.sortear(t, 0.5, 0.0) shouldBe "c"
        sorteo.sortear(t, 0.9999, 0.0) shouldBe "c"
    }

    @Test
    fun `la especie se elige por pesos dentro del rango`() {
        val t = tabla(5 to 100)
        sorteo.sortear(t, 0.0, 0.0) shouldBe "x"
        sorteo.sortear(t, 0.0, 0.2999) shouldBe "x"
        sorteo.sortear(t, 0.0, 0.3) shouldBe "y"
        sorteo.sortear(t, 0.0, 0.9999) shouldBe "y"
    }

    @Test
    fun `una tabla que no suma 100 no sortea`() {
        sorteo.sortear(tabla(2 to 50, 3 to 40), 0.1, 0.1) shouldBe null
    }

    @Test
    fun `un rango sin especies no sortea`() {
        SorteoAnimales(mapOf(2 to emptyList()), prestigio).sortear(tabla(2 to 100), 0.1, 0.1) shouldBe null
    }

    @Test
    fun `las especies excluidas no salen salvo que el rango se quede sin ninguna`() {
        val t = tabla(2 to 100)
        sorteo.sortear(t, 0.0, 0.0, excluir = setOf("a")) shouldBe "b"
        sorteo.sortear(t, 0.0, 0.0, excluir = setOf("a", "b")) shouldBe "a"
    }

    @Test
    fun `el rango de una especie se conoce y las desconocidas devuelven nulo`() {
        sorteo.rangoDe("a") shouldBe 2
        sorteo.rangoDe("y") shouldBe 5
        sorteo.rangoDe("zzz") shouldBe null
    }
}
