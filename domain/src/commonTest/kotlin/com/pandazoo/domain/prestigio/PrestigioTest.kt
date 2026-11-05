package com.pandazoo.domain.prestigio

import com.pandazoo.domain.config.DesbloqueoNivelCfg
import com.pandazoo.domain.config.MultiplicadoresCfg
import com.pandazoo.domain.config.PrestigioReglasCfg
import com.pandazoo.domain.config.UmbralesCfg
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** T6.1 a T6.4 y T6.6 (parte de dominio): umbrales, redondeo sin acumulación, máximos y avisos. */
class PrestigioTest {
    private val cfg = PrestigioReglasCfg(UmbralesCfg(2, 0.30), MultiplicadoresCfg(1.40, 1.10), 10)
    private val tabla = ReglasPrestigio.generarTabla(cfg)
    private val reglas = ReglasPrestigio(
        tabla,
        (0..10).map { DesbloqueoNivelCfg(it, listOf("especie$it"), if (it % 5 == 0) listOf("habitat$it") else emptyList()) },
    )

    private val animalesEsperados = listOf(2, 3, 4, 5, 8, 11, 15, 21, 30, 41)

    @Test
    fun `T6_1 la tabla generada coincide con los umbrales de la configuracion`() {
        tabla.map { it.umbralAnimalesComparacion } shouldBe animalesEsperados
        tabla[0].umbralSatisfaccion shouldBe (0.30 plusOrMinus 1e-12)
        tabla[9].umbralSatisfaccion shouldBe (0.707384 plusOrMinus 1e-6)
    }

    @Test
    fun `T6_1 cada nivel exige a la vez animales y satisfaccion en el umbral, por encima y por debajo`() {
        for (nivel in tabla) {
            val a = nivel.umbralAnimalesComparacion
            val s = nivel.umbralSatisfaccion
            reglas.nivelActual(a, s) shouldBe nivel.nivel // iguales a ambos
            reglas.nivelActual(a + 100, 1.0) shouldBe 10 // muy por encima
            // Solo una condición: se queda en el nivel anterior.
            reglas.nivelActual(a - 1, 1.0) shouldBe animalesEsperados.count { it <= a - 1 }
            reglas.nivelActual(a + 100, s - 0.0001) shouldBe nivel.nivel - 1
        }
    }

    @Test
    fun `T6_1 con animales suficientes y satisfaccion insuficiente no sube`() {
        reglas.nivelActual(41, 0.29) shouldBe 0
        reglas.nivelActual(41, 0.30) shouldBe 1
        reglas.nivelActual(1, 1.0) shouldBe 0
    }

    @Test
    fun `T6_2 el umbral de animales se calcula desde el valor sin redondear`() {
        // 2 * 1.4^4 = 7.6832 -> 8; el nivel 6 parte de 7.6832 y no de 8: 10.756 -> 11 (con 8 daría 11.2 -> 11 también),
        // pero el nivel 7 distingue: 15.059 -> 15 (desde 11 daría 15.4 -> 15). El nivel 9 sí difiere: 29.5 -> 30.
        tabla[4].umbralAnimalesSinRedondear shouldBe (7.6832 plusOrMinus 1e-9)
        tabla[8].umbralAnimalesSinRedondear shouldBe (29.515781 plusOrMinus 1e-6)
        tabla[8].umbralAnimalesComparacion shouldBe 30
    }

    @Test
    fun `T6_3 ningun nivel cumplido da prestigio cero`() {
        reglas.nivelActual(0, 0.0) shouldBe 0
        reglas.nivelActual(1, 0.29) shouldBe 0
    }

    @Test
    fun `T6_4 los maximos son independientes y no disminuyen`() {
        reglas.actualizarMaximos(null, 0, 0, 0.0) shouldBe null
        val m1 = reglas.actualizarMaximos(null, 1, 5, 0.40)
        m1 shouldBe MaximosHistoricos(1, 5, 0.40)
        // Baja el nivel y la satisfacción, sube el censo: cada componente aparte.
        reglas.actualizarMaximos(m1, 0, 7, 0.10) shouldBe MaximosHistoricos(1, 7, 0.40)
        // Prestigio actual y cantidad bajan: no disminuye nada.
        reglas.actualizarMaximos(m1, 0, 0, 0.0) shouldBe m1
        // La terna puede combinar momentos distintos.
        reglas.actualizarMaximos(MaximosHistoricos(1, 7, 0.40), 2, 4, 0.60) shouldBe MaximosHistoricos(2, 7, 0.60)
    }

    @Test
    fun `T6_6 el ascenso solo se avisa por encima del maximo historico previo`() {
        reglas.avisaAscenso(null, 3) shouldBe true
        reglas.avisaAscenso(2, 3) shouldBe true
        reglas.avisaAscenso(3, 3) shouldBe false
        reglas.avisaAscenso(5, 3) shouldBe false
        reglas.avisaAscenso(null, 0) shouldBe false
    }

    @Test
    fun `desbloqueos hasta un nivel incluyen todos los menores`() {
        reglas.desbloqueosHasta(0).map { it.nivel } shouldBe listOf(0)
        reglas.desbloqueosHasta(7).map { it.nivel } shouldBe (0..7).toList()
    }
}
