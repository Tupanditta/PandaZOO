package com.pandazoo.domain.animal

import com.pandazoo.domain.economia.Dinero
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** TN.3, TN.4, TN.5, TN.6 y TN.14 (parte de dominio): nivel, multiplicador, beneficio por animal y valor de venta. */
class NivelTest {
    private fun d(v: Double) = Dinero.desdeDecimal(v)

    /** Rangos 1 a 5 de `valores_y_pesos.json` 1.1. */
    private val rangos = listOf(
        ParametrosNivelRango(12.0, 15, 3.0, d(200.0), 1.5),
        ParametrosNivelRango(24.0, 29, 3.5, d(450.0), 1.75),
        ParametrosNivelRango(40.0, 41, 4.0, d(900.0), 2.0),
        ParametrosNivelRango(64.0, 51, 4.5, d(1800.0), 2.25),
        ParametrosNivelRango(100.0, 61, 5.0, d(4000.0), 2.5),
    )

    private fun progreso(nivelMaximo: Int, ciclosSanos: Int): ProgresoNivel {
        var p = ProgresoNivel(1, 0)
        repeat(ciclosSanos) { p = ReglasNivel.tras(p, sano = true, nivelMaximo = nivelMaximo) }
        return p
    }

    @Test
    fun `TN_3 el nivel sube exactamente al ciclo 72 y no al 71`() {
        progreso(15, 71) shouldBe ProgresoNivel(1, 71)
        progreso(15, 72) shouldBe ProgresoNivel(2, 0)
        progreso(15, 73) shouldBe ProgresoNivel(2, 1)
    }

    @Test
    fun `TN_3 un ciclo no sano no suma ni resta`() {
        val antes = ProgresoNivel(3, 40)
        ReglasNivel.tras(antes, sano = false, nivelMaximo = 15) shouldBe antes
        ReglasNivel.tras(ReglasNivel.tras(antes, sano = false, nivelMaximo = 15), sano = true, nivelMaximo = 15) shouldBe ProgresoNivel(3, 41)
    }

    @Test
    fun `TN_3 se detiene en el tope tras 1008 ciclos sanos en el rango 1 y 4320 en el 5`() {
        progreso(15, 1007) shouldBe ProgresoNivel(14, 71)
        progreso(15, 1008) shouldBe ProgresoNivel(15, 0)
        progreso(15, 5000) shouldBe ProgresoNivel(15, 0)
        progreso(61, 4319) shouldBe ProgresoNivel(60, 71)
        progreso(61, 4320) shouldBe ProgresoNivel(61, 0)
        progreso(61, 9000) shouldBe ProgresoNivel(61, 0)
    }

    @Test
    fun `TN_4 propiedad el nivel nunca baja y se mantiene en rango para cualquier secuencia`() {
        var p = ProgresoNivel(1, 0)
        var azar = 12345L
        repeat(20_000) {
            azar = (azar * 6364136223846793005L + 1442695040888963407L)
            val sano = (azar ushr 33) % 3 != 0L
            val siguiente = ReglasNivel.tras(p, sano, nivelMaximo = 15)
            (siguiente.nivel >= p.nivel) shouldBe true
            (siguiente.nivel in 1..15) shouldBe true
            (siguiente.ciclosNivel in 0 until ReglasNivel.CICLOS_POR_NIVEL) shouldBe true
            if (siguiente.nivel == 15) siguiente.ciclosNivel shouldBe 0
            // El progreso avanza si y solo si el ciclo fue sano (salvo en el tope).
            if (p.nivel < 15) (siguiente != p) shouldBe sano
            p = siguiente
        }
    }

    @Test
    fun `TN_3 un ciclo sano exige alimentacion mayor que cero y contadores a cero`() {
        ReglasNivel.cicloSano(100, 0, 0) shouldBe true
        ReglasNivel.cicloSano(0, 1, 0) shouldBe false
        ReglasNivel.cicloSano(0, 0, 0) shouldBe false
        ReglasNivel.cicloSano(60, 0, 1) shouldBe false
    }

    @Test
    fun `TN_5 multiplicador de nivel en los extremos y valor intermedio`() {
        ReglasNivel.multiplicador(1, 15, 3.0) shouldBe 1.0
        ReglasNivel.multiplicador(15, 15, 3.0) shouldBe 3.0
        // Rango 1, nivel 8: 1 + 2 · 7 / 14 = 2.
        ReglasNivel.multiplicador(8, 15, 3.0) shouldBe 2.0
        rangos.forEach { r ->
            var anterior = 0.0
            for (n in 1..r.nivelMaximo) {
                val m = ReglasNivel.multiplicador(n, r.nivelMaximo, r.multiplicadorNivelMaximo)
                (m > anterior) shouldBe true
                anterior = m
            }
            ReglasNivel.multiplicador(r.nivelMaximo, r.nivelMaximo, r.multiplicadorNivelMaximo) shouldBe r.multiplicadorNivelMaximo
        }
    }

    @Test
    fun `TN_6 beneficio por animal con casos calculados a mano`() {
        // Rango 1, S = 0.5: 12 · 0.5 · 1 = 6.00 en el nivel 1 y 12 · 0.5 · 3 = 18.00 en el 15.
        ReglasNivel.beneficioCiclo(listOf(ReglasNivel.beneficioAnimal(rangos[0], 0.5, 1))) shouldBe d(6.0)
        ReglasNivel.beneficioCiclo(listOf(ReglasNivel.beneficioAnimal(rangos[0], 0.5, 15))) shouldBe d(18.0)
    }

    @Test
    fun `TN_6 la suma se redondea una sola vez con los medios hacia arriba`() {
        // 0.0125 + 0.0125 = 0.025 → 0.03 con un único redondeo (redondeando cada uno daría 0.02).
        ReglasNivel.beneficioCiclo(listOf(0.0125, 0.0125)) shouldBe d(0.03)
        ReglasNivel.beneficioCiclo(listOf(0.004, 0.004)) shouldBe d(0.01)
        ReglasNivel.beneficioCiclo(emptyList()) shouldBe Dinero.CERO
        // Rangos mezclados: rango 1 nivel 15 S=1 (36) + rango 5 nivel 1 S=0.5 (50) = 86.
        val total = ReglasNivel.beneficioCiclo(
            listOf(ReglasNivel.beneficioAnimal(rangos[0], 1.0, 15), ReglasNivel.beneficioAnimal(rangos[4], 0.5, 1)),
        )
        total shouldBe d(86.0)
    }

    @Test
    fun `TN_14 niveles de equilibrio por rango`() {
        rangos.map { ReglasNivel.nivelEquilibrio(it.nivelMaximo) } shouldBe listOf(10, 19, 27, 34, 41)
    }

    @Test
    fun `TN_14 el factor de venta vale 0_05 en el nivel 1 y 1 en el equilibrio`() {
        rangos.forEach { r ->
            val eq = ReglasNivel.nivelEquilibrio(r.nivelMaximo)
            ReglasNivel.factorVenta(1, r.nivelMaximo, r.factorVentaNivelMaximo) shouldBe 0.05
            ReglasNivel.factorVenta(eq, r.nivelMaximo, r.factorVentaNivelMaximo) shouldBe 1.0
            ReglasNivel.factorVenta(r.nivelMaximo, r.nivelMaximo, r.factorVentaNivelMaximo) shouldBe r.factorVentaNivelMaximo
        }
    }

    @Test
    fun `TN_14 en el equilibrio se recupera exactamente el coste y en el tope se vende por los valores previstos`() {
        listOf(200.0, 450.0, 900.0, 1800.0).forEachIndexed { i, coste ->
            val r = rangos[i]
            ReglasNivel.valorVenta(r, ReglasNivel.nivelEquilibrio(r.nivelMaximo)) shouldBe d(coste)
        }
        rangos.map { ReglasNivel.valorVenta(it, it.nivelMaximo) } shouldBe listOf(d(300.0), d(787.5), d(1800.0), d(4050.0), d(10000.0))
        // Nivel 1: 5 % del valor base.
        rangos.map { ReglasNivel.valorVenta(it, 1) } shouldBe listOf(d(10.0), d(22.5), d(45.0), d(90.0), d(200.0))
    }

    @Test
    fun `TN_14 por debajo del equilibrio se pierde y por encima se gana, con V estrictamente creciente`() {
        rangos.forEachIndexed { i, r ->
            val eq = ReglasNivel.nivelEquilibrio(r.nivelMaximo)
            if (i < 4) {
                (ReglasNivel.valorVenta(r, eq - 1) < r.valorVentaBase) shouldBe true
                (ReglasNivel.valorVenta(r, eq + 1) > r.valorVentaBase) shouldBe true
            }
            var anterior = Dinero.CERO
            for (n in 1..r.nivelMaximo) {
                val v = ReglasNivel.valorVenta(r, n)
                (v > anterior) shouldBe true
                anterior = v
            }
        }
        // La venta en el tope del rango 5 supera a la de todos los demás.
        val topes = rangos.map { ReglasNivel.valorVenta(it, it.nivelMaximo) }
        (topes[4] > topes.take(4).max()) shouldBe true
    }
}
