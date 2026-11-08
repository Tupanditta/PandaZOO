package com.pandazoo.domain.regalo

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ReglasRegaloSemanalTest {
    @Test
    fun `el 1 de enero de 1970 fue jueves`() {
        ReglasRegaloSemanal.diaSemana(0) shouldBe 4
        ReglasRegaloSemanal.fecha(0) shouldBe "1970-01-01"
    }

    @Test
    fun `fechas y dias de la semana conocidos`() {
        // 2026-11-09 fue lunes (día 20766 desde 1970), 2026-11-13 viernes y 2026-11-15 domingo.
        ReglasRegaloSemanal.fecha(20_766) shouldBe "2026-11-09"
        ReglasRegaloSemanal.diaSemana(20_766) shouldBe 1
        ReglasRegaloSemanal.diaSemana(20_770) shouldBe 5
        ReglasRegaloSemanal.fecha(20_770) shouldBe "2026-11-13"
        ReglasRegaloSemanal.diaSemana(20_772) shouldBe 7
        ReglasRegaloSemanal.fecha(19_782) shouldBe "2024-02-29" // año bisiesto
    }

    @Test
    fun `la zona horaria cambia el dia local`() {
        val medianocheUtc = 20_766L * 86_400
        ReglasRegaloSemanal.diasLocales(medianocheUtc, 0) shouldBe 20_766
        ReglasRegaloSemanal.diasLocales(medianocheUtc, -3_600) shouldBe 20_765 // UTC-1: todavía domingo
        ReglasRegaloSemanal.diasLocales(medianocheUtc - 1, 3_600) shouldBe 20_766 // UTC+1: ya lunes
        ReglasRegaloSemanal.diaSemana(ReglasRegaloSemanal.diasLocales(-1, 0)) shouldBe 3 // 1969-12-31, miércoles
    }

    @Test
    fun `el identificador incluye la fecha`() {
        ReglasRegaloSemanal.idRegalo("lunes", "2026-11-09") shouldBe "lunes:2026-11-09"
    }
}
