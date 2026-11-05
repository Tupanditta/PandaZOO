package com.pandazoo.domain.animal

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** T3.2 (parte de dominio): edad máxima fijada con el factor aleatorio en [0.90, 1.10]. */
class AnimalTest {
    @Test
    fun `el factor cubre el intervalo cerrado por abajo y abierto por arriba`() {
        ReglasAnimal.factorEdad(0.0) shouldBe 0.90
        ReglasAnimal.factorEdad(0.5) shouldBe 1.0
        (ReglasAnimal.factorEdad(0.999999) < 1.10) shouldBe true
    }

    @Test
    fun `edad maxima con casos calculados a mano`() {
        // 20 años * 360 días * 0.90 = 6480 días = 18 años exactos.
        ReglasAnimal.edadMaxima(20, 0.90) shouldBe Edad(18, 0, 0)
        // 20 * 360 * 1.00 = 7200 días = 20 años.
        ReglasAnimal.edadMaxima(20, 1.0) shouldBe Edad(20, 0, 0)
        // 15 * 360 * 1.10 = 5940 días = 16 años (5760) + 180 días = 6 meses.
        ReglasAnimal.edadMaxima(15, 1.10) shouldBe Edad(16, 6, 0)
        // 7 * 360 * 0.93 = 2343,6 → 2344 días = 6 años (2160) + 184 días = 6 meses y 4 días.
        ReglasAnimal.edadMaxima(7, 0.93) shouldBe Edad(6, 6, 4)
    }

    @Test
    fun `la edad maxima minima posible supera la edad de alta`() {
        // Con la menor edad media del catálogo (40 años) y factor 0.90 siempre se supera EDAD_ALTA (BBDD 2.3.1).
        (ReglasAnimal.edadMaxima(40, 0.90).totalDias > ReglasAnimal.EDAD_ALTA_ANIOS * Edad.DIAS_ANIO) shouldBe true
    }

    @Test
    fun `descomposicion en anios meses y dias`() {
        Edad.deDias(0) shouldBe Edad(0, 0, 0)
        Edad.deDias(359) shouldBe Edad(0, 11, 29)
        Edad.deDias(360) shouldBe Edad(1, 0, 0)
        Edad(3, 2, 1).totalDias shouldBe 3 * 360 + 60 + 1
    }

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
