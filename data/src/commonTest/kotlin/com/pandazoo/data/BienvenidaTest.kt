package com.pandazoo.data

import com.pandazoo.data.inicial.InicializadorPartida
import com.pandazoo.data.inicial.PartidaCreada
import com.pandazoo.data.repositorio.MisionSql
import com.pandazoo.testing.AzarFalso
import com.pandazoo.testing.GeneradorIdSecuencial
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Regalo de bienvenida de la partida nueva: fondos, medicinas y animales gratuitos sin depender del prestigio. */
class BienvenidaTest {
    private lateinit var e: Entorno

    @BeforeTest
    fun preparar() {
        e = Entorno()
    }

    @AfterTest
    fun cerrar() = e.cerrar()

    private fun crear(vararg azar: Double): PartidaCreada =
        InicializadorPartida(e.bd, e.reloj, GeneradorIdSecuencial("nueva"), AzarFalso(*azar)).crearPartidaConBienvenida(e.cfg)

    @Test
    fun `la partida nueva regala fondos bambu medicinas tres comunes un poco comun y dos sorteados`() {
        // 0.5 en todos los sorteos: primer sorteo (40 % raro, 60 % poco comun) da poco comun; segundo (50/30/20) da epico.
        val p = crear(0.5)
        p.regalo.fondos shouldBe 2000.0
        p.regalo.bambu shouldBe 30
        p.regalo.medicinas shouldBe 3
        p.regalo.animales.map { it.idRango } shouldBe listOf(1, 1, 1, 2, 2, 4)
        p.regalo.animales.map { it.idEspecie }.toSet().size shouldBe 6
        // Solo los dos sorteados muestran probabilidades.
        p.regalo.animales.map { it.probabilidades.size } shouldBe listOf(0, 0, 0, 0, 2, 3)
    }

    @Test
    fun `el sorteo puede dar raro y exclusivo`() {
        // Cuatro fijos; sorteo 1 con 0.0 = raro; sorteo 2 con 0.9 = exclusivo.
        val p = crear(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.9, 0.0)
        p.regalo.animales.map { it.idRango } shouldBe listOf(1, 1, 1, 2, 3, 5)
    }

    @Test
    fun `las especies regaladas se compran gratis una vez aunque el prestigio sea cero`() {
        val p = crear(0.5)
        val misiones = MisionSql(e.bd)
        val regaladas = p.regalo.animales.map { it.idEspecie }
        misiones.especiesConUnidad(p.idPartida) shouldContainExactlyInAnyOrder regaladas
        misiones.consumirUnidad(p.idPartida, regaladas.first()) shouldBe true
        misiones.consumirUnidad(p.idPartida, regaladas.first()) shouldBe false
        misiones.especiesConUnidad(p.idPartida) shouldContainExactlyInAnyOrder regaladas.drop(1)
    }

    @Test
    fun `crear una partida sin bienvenida no concede animales`() {
        e.bd.uno("SELECT COUNT(*) FROM UnidadGratuita") { it.entero(0) } shouldBe 0
    }
}
