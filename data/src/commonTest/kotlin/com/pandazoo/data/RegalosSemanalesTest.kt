package com.pandazoo.data

import com.pandazoo.application.caso.ComandoAdquirirAnimal
import com.pandazoo.application.caso.ConcederRegalosSemanales
import com.pandazoo.application.caso.FichaNueva
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.puerto.Reloj
import com.pandazoo.data.repositorio.RegalosSemanalesSql
import com.pandazoo.domain.config.ProbabilidadRango
import com.pandazoo.domain.config.RegaloSemanalCfg
import com.pandazoo.domain.config.ValidadorConfiguracion
import com.pandazoo.domain.mision.SorteoAnimales
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Regalos semanales: una unidad gratuita los lunes y los viernes, con identificador propio y sin repetirse. */
class RegalosSemanalesTest {
    private lateinit var e: Entorno
    private var solicitud = 0

    // Días de 2026 a medianoche UTC: lunes 9 de noviembre, martes 10, viernes 13, sábado 14 y lunes 16.
    private val lunes9 = 1_794_182_400L
    private val martes10 = lunes9 + 86_400
    private val viernes13 = lunes9 + 4 * 86_400
    private val sabado14 = lunes9 + 5 * 86_400
    private val lunes16 = lunes9 + 7 * 86_400

    @BeforeTest
    fun preparar() {
        e = Entorno(valorAzar = 0.0)
        e.reloj.segundos = lunes9 + 3_600
    }

    @AfterTest
    fun cerrar() = e.cerrar()

    private fun casoDe(entorno: Entorno, reloj: Reloj = entorno.reloj, regalos: List<RegaloSemanalCfg> = entorno.cfg.regalosSemanales) =
        ConcederRegalosSemanales(
            entorno.ejecutor, RegalosSemanalesSql(entorno.bd), SorteoAnimales.desde(entorno.cfg), entorno.azar, reloj, regalos,
            entorno.cfg.especies.associate { it.id to it.nombre },
        )

    private fun caso() = casoDe(e)

    private fun unidades() = e.contar("UnidadGratuita", "origen = 'SEMANAL' AND unidad_pendiente = 1")

    @Test
    fun `la configuracion trae el regalo del lunes y el del viernes con sus probabilidades`() {
        val regalos = e.cfg.regalosSemanales.associateBy { it.id }
        regalos.keys shouldBe setOf("lunes", "viernes")
        regalos.getValue("lunes").diaSemana shouldBe 1
        regalos.getValue("lunes").sorteo.associate { it.idRango to it.porcentaje } shouldBe mapOf(5 to 5, 4 to 20, 3 to 35, 2 to 40)
        regalos.getValue("viernes").diaSemana shouldBe 5
        regalos.getValue("viernes").sorteo.associate { it.idRango to it.porcentaje } shouldBe mapOf(5 to 5, 4 to 10, 3 to 20, 1 to 35, 2 to 30)
    }

    @Test
    fun `el lunes concede una unidad gratuita con su identificador y la registra`() {
        val concedidos = caso().ejecutar(e.idPartida)
        concedidos.size shouldBe 1
        concedidos[0].idRegalo shouldBe "lunes:2026-11-09"
        unidades() shouldBe 1
        e.contar("RegaloSemanal", "id_regalo = 'lunes:2026-11-09' AND id_especie = '${concedidos[0].idEspecie}'") shouldBe 1
        e.auditoriaDe("REGALO_SEMANAL").map { it.first } shouldBe listOf("CONFIRMADO")
    }

    @Test
    fun `no se da dos veces el mismo dia ni aunque caduque la clave de idempotencia`() {
        caso().ejecutar(e.idPartida).size shouldBe 1
        caso().ejecutar(e.idPartida).size shouldBe 0
        e.reloj.segundos = lunes9 + 80_000 // sigue siendo lunes
        caso().ejecutar(e.idPartida).size shouldBe 0
        // La clave de idempotencia de la auditoría caduca con la purga; el registro del regalo no.
        e.bd.escribir("UPDATE AuditoriaEvento SET clave_idempotencia = NULL")
        caso().ejecutar(e.idPartida).size shouldBe 0
        unidades() shouldBe 1
        e.contar("RegaloSemanal") shouldBe 1
        e.auditoriaDe("REGALO_SEMANAL").size shouldBe 1 // sin rechazos ni duplicados que ensucien la auditoría
    }

    @Test
    fun `solo se da en lunes y viernes`() {
        e.reloj.segundos = martes10
        caso().ejecutar(e.idPartida).size shouldBe 0
        e.reloj.segundos = sabado14
        caso().ejecutar(e.idPartida).size shouldBe 0
        unidades() shouldBe 0
        e.reloj.segundos = viernes13 + 10
        caso().ejecutar(e.idPartida).map { it.idRegalo } shouldBe listOf("viernes:2026-11-13")
        e.reloj.segundos = lunes16
        caso().ejecutar(e.idPartida).map { it.idRegalo } shouldBe listOf("lunes:2026-11-16")
        unidades() shouldBe 3 - 1 // lunes 9 no se abrió el juego: solo el viernes 13 y el lunes 16
        e.contar("RegaloSemanal") shouldBe 2
    }

    @Test
    fun `la zona horaria decide que dia es`() {
        val relojLocal = object : Reloj {
            override fun ahoraSegundos() = lunes9 + 1_800 // lunes 00:30 UTC
            override fun desplazamientoLocalSegundos() = -3_600 // UTC-1: todavía domingo 23:30
        }
        casoDe(e, relojLocal).ejecutar(e.idPartida).size shouldBe 0
    }

    @Test
    fun `el rango sale de la tabla del dia`() {
        // Con azar 0, el primer tramo de cada tabla: exclusivo (5 %) el lunes y el viernes.
        caso().ejecutar(e.idPartida)[0].idRango shouldBe 5
        e.reloj.segundos = viernes13
        caso().ejecutar(e.idPartida)[0].idRango shouldBe 5

        // Con azar casi 1, el último tramo: poco común el lunes (40 %) y poco común el viernes (30 %).
        val alto = Entorno(valorAzar = 0.9999)
        try {
            alto.reloj.segundos = lunes9
            casoDe(alto).ejecutar(alto.idPartida)[0].idRango shouldBe 2
            alto.reloj.segundos = viernes13
            casoDe(alto).ejecutar(alto.idPartida)[0].idRango shouldBe 2
        } finally {
            alto.cerrar()
        }

        // Con azar 0,60 el viernes cae en el cuarto tramo (común): 5 + 10 + 20 = 35 <= 60 < 70.
        val medio = Entorno(valorAzar = 0.60)
        try {
            medio.reloj.segundos = viernes13
            casoDe(medio).ejecutar(medio.idPartida)[0].idRango shouldBe 1
        } finally {
            medio.cerrar()
        }
    }

    @Test
    fun `la unidad se compra gratis en la tienda`() {
        val regalo = caso().ejecutar(e.idPartida).single()
        val sp = e.datos.espacio(e.idPartida, e.habitat("bosque_templado"), "Madriguera", 10)
        val fondos = e.fondos()
        val ficha = FichaNueva("Regalo", e.catalogoFicha.habilidades.take(2).map { it.id }, e.catalogoFicha.rasgos.take(2).map { it.id })
        val r = e.adquirirAnimal.ejecutar(ComandoAdquirirAnimal(e.idPartida, regalo.idEspecie, sp, ficha, "compra-${++solicitud}"))
        (r is Resultado.Exito) shouldBe true
        e.fondos() shouldBe fondos
        unidades() shouldBe 0
    }

    @Test
    fun `la purga conserva solo los ultimos regalos`() {
        repeat(30) { i ->
            e.bd.escribir(
                "INSERT INTO RegaloSemanal (id_partida, id_regalo, id_especie, fecha) VALUES (?, ?, 'erizo', ?)",
                e.idPartida, "lunes:2026-01-" + (i + 10), 1_000L + i,
            )
        }
        e.bd.escribir("UPDATE EstadoSimulacion SET numero_ciclo_ultimo = 1")
        e.purgarRetencion.ejecutar(e.idPartida)
        e.contar("RegaloSemanal") shouldBe 20
    }

    @Test
    fun `la migracion 14 a 15 crea RegaloSemanal, amplia el origen de las unidades y conserva las existentes`() {
        // Estado de la versión 14: sin RegaloSemanal y con el origen limitado a BIENVENIDA.
        e.bd.escribir("DROP TABLE RegaloSemanal")
        e.bd.escribir("DROP TABLE UnidadGratuita")
        e.bd.escribir(
            "CREATE TABLE UnidadGratuita (id_unidad INTEGER PRIMARY KEY, id_partida TEXT NOT NULL, id_especie TEXT NOT NULL, " +
                "origen TEXT NOT NULL CHECK (origen IN ('BIENVENIDA')), unidad_pendiente INTEGER NOT NULL DEFAULT 1 " +
                "CHECK (unidad_pendiente IN (0, 1)), fecha INTEGER NOT NULL)",
        )
        e.bd.escribir("INSERT INTO UnidadGratuita (id_partida, id_especie, origen, unidad_pendiente, fecha) VALUES (?, 'erizo', 'BIENVENIDA', 1, 5)", e.idPartida)

        e.bd.migrarDe14a15()

        e.bd.leer("SELECT name FROM sqlite_master WHERE name = 'RegaloSemanal'") { it.texto(0) } shouldBe listOf("RegaloSemanal")
        e.bd.uno("SELECT sql FROM sqlite_master WHERE name = 'UnidadGratuita'") { it.texto(0).contains("'SEMANAL'") } shouldBe true
        e.bd.leer("SELECT id_especie, origen, unidad_pendiente FROM UnidadGratuita") { Triple(it.texto(0), it.texto(1), it.entero(2)) } shouldBe
            listOf(Triple("erizo", "BIENVENIDA", 1))
        // Ya se pueden conceder regalos semanales.
        caso().ejecutar(e.idPartida).size shouldBe 1
    }

    @Test
    fun `el validador rechaza sorteos que no suman 100 y dias repetidos`() {
        val base = e.cfg
        val roto = base.copy(regalosSemanales = listOf(base.regalosSemanales[0].copy(sorteo = listOf(ProbabilidadRango(2, 90)))))
        ValidadorConfiguracion.validar(roto).any { it.contains("sorteo debe sumar 100") } shouldBe true
        val repetido = base.copy(regalosSemanales = listOf(base.regalosSemanales[0], base.regalosSemanales[0].copy(id = "otro")))
        ValidadorConfiguracion.validar(repetido).any { it.contains("un día de la semana") } shouldBe true
        ValidadorConfiguracion.validar(base) shouldBe emptyList()
    }
}
