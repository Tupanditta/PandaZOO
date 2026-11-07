package com.pandazoo.domain.ciclo

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** T8.1 a T8.4 (parte de dominio): plan de ciclos vencidos y límites offline de hambruna y enfermedad. */
class DiferidoTest {
    private val max = 1008

    @Test
    fun `T8_1 menos tiempo que el remanente no vence ningun ciclo`() {
        ReglasDiferido.planificar(299, 300, max) shouldBe PlanDiferido(0, 0, 1)
        ReglasDiferido.planificar(0, 300, max) shouldBe PlanDiferido(0, 0, 300)
    }

    @Test
    fun `T8_1 tiempo igual al remanente vence un ciclo y el remanente vuelve a 600`() {
        ReglasDiferido.planificar(300, 300, max) shouldBe PlanDiferido(1, 0, 600)
        ReglasDiferido.planificar(599 + 300, 300, max) shouldBe PlanDiferido(1, 0, 1)
    }

    @Test
    fun `T8_1 remanente mas tres ciclos completos son cuatro ciclos`() {
        ReglasDiferido.planificar(300 + 3 * 600, 300, max) shouldBe PlanDiferido(4, 0, 600)
        ReglasDiferido.planificar(300 + 3 * 600 + 250, 300, max) shouldBe PlanDiferido(4, 0, 350)
    }

    @Test
    fun `T8_1 con remanente 0 el ciclo pendiente vence aunque no haya pasado tiempo`() {
        ReglasDiferido.planificar(0, 0, max) shouldBe PlanDiferido(1, 0, 600)
    }

    @Test
    fun `T8_2 los ciclos que exceden el maximo se descartan y el remanente se calcula igualmente`() {
        ReglasDiferido.planificar(300 + 9 * 600 + 100, 300, 3) shouldBe PlanDiferido(3, 7, 500)
        ReglasDiferido.planificar(0, 600, 0) shouldBe PlanDiferido(0, 0, 600)
    }

    @Test
    fun `T8_9 un tiempo negativo no es un valor valido para el calculo`() {
        shouldThrow<IllegalArgumentException> { ReglasDiferido.planificar(-1, 300, max) }
    }

    @Test
    fun `T8_3 tabla de transiciones de alimentacion offline`() {
        // inicial -> serie de niveles tras 1, 2, 3 y 4 ciclos diferidos
        fun serie(inicial: Int): List<Int> {
            var n = inicial
            return List(4) { n = ReglasDiferido.alimentacionTrasCiclo(n, inicial); n }
        }
        serie(100) shouldBe listOf(80, 60, 40, 40)
        serie(80) shouldBe listOf(60, 40, 40, 40)
        serie(60) shouldBe listOf(40, 40, 40, 40)
        serie(40) shouldBe listOf(20, 0, 0, 0)
        serie(20) shouldBe listOf(0, 0, 0, 0)
        serie(0) shouldBe listOf(0, 0, 0, 0)
    }

    @Test
    fun `T8_3 un animal con alimentacion cero computa un unico ciclo mortal por sesion`() {
        var contador = 1
        val serie = List(4) { contador = ReglasDiferido.contadorDesnutricion(contador, inicial = 1, alimentacion = 0); contador }
        serie shouldBe listOf(2, 2, 2, 2)
        // Un animal que llega a 0 durante la sesion tambien computa uno solo.
        var c = 0
        c = ReglasDiferido.contadorDesnutricion(c, 0, 20)
        c shouldBe 0
        c = ReglasDiferido.contadorDesnutricion(c, 0, 0)
        c shouldBe 1
        c = ReglasDiferido.contadorDesnutricion(c, 0, 0)
        c shouldBe 1
    }

    @Test
    fun `T8_4 enfermedad offline un ciclo si enferma y dos adicionales si ya estaba enfermo`() {
        // Enferma durante la sesion: contador inicial 0 y solo suma uno.
        var nuevo = 0
        val serieNuevo = List(4) { nuevo = ReglasDiferido.contadorEnfermedad(nuevo, 0, enfermo = true, enfermoInicial = false); nuevo }
        serieNuevo shouldBe listOf(1, 1, 1, 1)
        // Ya enfermo con 1 ciclo acumulado: como maximo dos adicionales.
        var previo = 1
        val seriePrevio = List(4) { previo = ReglasDiferido.contadorEnfermedad(previo, 1, enfermo = true, enfermoInicial = true); previo }
        seriePrevio shouldBe listOf(2, 3, 3, 3)
        // Sano: se reinicia.
        ReglasDiferido.contadorEnfermedad(2, 1, enfermo = false, enfermoInicial = true) shouldBe 0
    }
}
