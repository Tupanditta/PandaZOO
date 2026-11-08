package com.pandazoo.data

import com.pandazoo.application.caso.ConsultarGuiaConvivencia
import com.pandazoo.application.caso.NivelEncaje
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Guía de convivencia: la afinidad y la compatibilidad con el hábitat se traducen a tres niveles. */
class GuiaConvivenciaTest {
    private lateinit var e: Entorno
    private lateinit var guia: ConsultarGuiaConvivencia

    @BeforeTest
    fun preparar() {
        e = Entorno()
        guia = ConsultarGuiaConvivencia(e.cfg, e.regla)
    }

    @AfterTest
    fun cerrar() = e.cerrar()

    @Test
    fun `los tramos de afinidad y de habitat son inclusivos por abajo`() {
        ConsultarGuiaConvivencia.nivelAfinidad(0.6) shouldBe NivelEncaje.BIEN
        ConsultarGuiaConvivencia.nivelAfinidad(0.59) shouldBe NivelEncaje.REGULAR
        ConsultarGuiaConvivencia.nivelAfinidad(0.35) shouldBe NivelEncaje.REGULAR
        ConsultarGuiaConvivencia.nivelAfinidad(0.34) shouldBe NivelEncaje.MAL
        ConsultarGuiaConvivencia.nivelHabitat(1.0) shouldBe NivelEncaje.BIEN
        ConsultarGuiaConvivencia.nivelHabitat(0.75) shouldBe NivelEncaje.BIEN
        ConsultarGuiaConvivencia.nivelHabitat(0.4) shouldBe NivelEncaje.REGULAR
        ConsultarGuiaConvivencia.nivelHabitat(0.2) shouldBe NivelEncaje.MAL
    }

    @Test
    fun `la guia lista todas las especies y todos los habitats`() {
        guia.especies shouldHaveSize e.cfg.especies.size
        guia.habitats shouldHaveSize e.cfg.catalogos.biomas.size
        guia.encajesHabitat(guia.especies.first().id) shouldHaveSize guia.habitats.size
    }

    @Test
    fun `las relaciones excluyen a la propia especie y son simetricas`() {
        val a = guia.especies[0].id
        val b = guia.especies[1].id
        val deA = guia.relaciones(a)
        deA shouldHaveSize e.cfg.especies.size - 1
        deA.none { it.especie.id == a } shouldBe true
        deA.first { it.especie.id == b }.nivel shouldBe guia.relaciones(b).first { it.especie.id == a }.nivel
    }

    @Test
    fun `el encaje con el habitat sigue la compatibilidad de la configuracion`() {
        val c = e.cfg.tablas.compatibilidad.first()
        val esperado = ConsultarGuiaConvivencia.nivelHabitat(c.factor)
        val i = guia.habitats.indexOfFirst { it.idBioma == c.idBioma }
        guia.encajesHabitat(c.idEspecie)[i] shouldBe esperado
    }
}
