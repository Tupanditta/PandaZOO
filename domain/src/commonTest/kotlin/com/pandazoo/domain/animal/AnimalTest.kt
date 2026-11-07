package com.pandazoo.domain.animal

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** T3.2 (parte de dominio): fichas con catálogos cerrados. */
class AnimalTest {
    @Test
    fun `catalogo exige exactamente dos distintos y del catalogo`() {
        val c = CatalogoFicha(
            habilidades = listOf(OpcionFicha("lider", "Líder"), OpcionFicha("cuidador", "Cuidador")),
            rasgos = listOf(OpcionFicha("curioso", "Curioso"), OpcionFicha("timido", "Tímido")),
        )
        c.habilidadesValidas(listOf("lider", "cuidador")) shouldBe true
        c.habilidadesValidas(listOf("lider")) shouldBe false
        c.habilidadesValidas(listOf("lider", "lider")) shouldBe false
        c.habilidadesValidas(listOf("lider", "inventada")) shouldBe false
        c.rasgosValidos(listOf("curioso", "timido", "curioso")) shouldBe false
        c.rasgosValidos(listOf("timido", "curioso")) shouldBe true
    }
}

/** T4.4 (parte de dominio): la alimentación solo toma valores `0` o múltiplos de `10`. */
class CuidadoTest {
    @Test
    fun `una racion suma diez puntos con tope en cien`() {
        ReglasCuidado.nivelTrasRacion(60) shouldBe 70
        ReglasCuidado.nivelTrasRacion(0) shouldBe 10
        ReglasCuidado.nivelTrasRacion(100) shouldBe 100
        ReglasCuidado.nivelTrasRacion(95) shouldBe 100
    }

    @Test
    fun `incrementos y kilos hasta el maximo`() {
        ReglasCuidado.incrementosHastaMaximo(60) shouldBe 4
        ReglasCuidado.incrementosHastaMaximo(100) shouldBe 0
        ReglasCuidado.kilosHastaMaximo(0, 3) shouldBe 30
        ReglasCuidado.kilosHastaMaximo(90, 5) shouldBe 5
    }

    @Test
    fun `cualquier secuencia de raciones mantiene multiplo de diez`() {
        for (inicio in 0..100 step 10) {
            var n = inicio
            repeat(12) {
                n = ReglasCuidado.nivelTrasRacion(n)
                (n % 10) shouldBe 0
                (n in 0..100) shouldBe true
            }
            n shouldBe 100
        }
    }
}
