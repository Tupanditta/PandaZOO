package com.pandazoo.domain.animal

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** La ración base de la especie corresponde al nivel 1 y crece un 5 % por nivel, redondeada hacia arriba. */
class RacionPorNivelTest {
    @Test
    fun `el nivel 1 usa la cantidad base`() {
        (1..5).forEach { ReglasCuidado.kilosPorRacion(it, 1) shouldBe it }
    }

    @Test
    fun `cada nivel suma un cinco por ciento redondeado hacia arriba`() {
        ReglasCuidado.kilosPorRacion(2, 2) shouldBe 3 // 2,10 -> 3
        ReglasCuidado.kilosPorRacion(5, 2) shouldBe 6 // 5,25 -> 6
        ReglasCuidado.kilosPorRacion(5, 5) shouldBe 6 // 6,00 -> 6
        ReglasCuidado.kilosPorRacion(5, 6) shouldBe 7 // 6,25 -> 7
        ReglasCuidado.kilosPorRacion(4, 21) shouldBe 8 // 8,00 -> 8
        ReglasCuidado.kilosPorRacion(4, 61) shouldBe 16 // 16,00 -> 16
    }

    @Test
    fun `un nivel menor que 1 se trata como 1`() {
        ReglasCuidado.kilosPorRacion(3, 0) shouldBe 3
        ReglasCuidado.kilosPorRacion(3, -4) shouldBe 3
    }

    @Test
    fun `la racion nunca baja al subir de nivel`() {
        (1..5).forEach { base ->
            var anterior = 0
            (1..61).forEach { nivel ->
                val k = ReglasCuidado.kilosPorRacion(base, nivel)
                (k >= anterior) shouldBe true
                anterior = k
            }
        }
    }

    @Test
    fun `kilos hasta el maximo multiplica la racion del nivel por los incrementos que faltan`() {
        ReglasCuidado.kilosHastaMaximo(60, 5, 6) shouldBe 4 * 7
        ReglasCuidado.kilosHastaMaximo(100, 5, 40) shouldBe 0
        ReglasCuidado.kilosHastaMaximo(0, 2, 2) shouldBe 10 * 3
        ReglasCuidado.kilosHastaMaximo(0, 3) shouldBe 30
    }
}
