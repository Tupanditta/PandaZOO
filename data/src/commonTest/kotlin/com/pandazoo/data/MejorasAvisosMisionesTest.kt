package com.pandazoo.data

import com.pandazoo.application.caso.ComandoDescartarAviso
import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.puerto.EstadoMision
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Avisos descartables y caducos, y orden de las misiones. */
class MejorasAvisosMisionesTest {
    private lateinit var e: Entorno
    private var solicitud = 0

    @BeforeTest
    fun preparar() {
        e = Entorno()
    }

    @AfterTest
    fun cerrar() = e.cerrar()

    private fun nueva() = "mej-${++solicitud}"

    private fun aviso(codigo: String, tipo: String, severidad: String, ciclo: Int, entidad: String = "x"): Long {
        e.bd.escribir(
            "INSERT INTO Notificacion (id_partida, tipo, severidad, codigo, entidad, id_entidad, numero_ciclo, estado, fecha) " +
                "VALUES (?, ?, ?, ?, 'ANIMAL', ?, ?, 'PENDIENTE', 0)",
            e.idPartida, tipo, severidad, codigo, entidad, ciclo,
        )
        return e.bd.uno("SELECT MAX(id_notificacion) FROM Notificacion") { it.largo(0) }!!
    }

    private fun abiertos() = e.consultarAvisos.ejecutar(e.idPartida).filter { !it.cerrado }.map { it.id }

    private fun purgarEnCiclo(n: Int) {
        e.bd.escribir("UPDATE EstadoSimulacion SET numero_ciclo_ultimo = ?", n)
        e.purgarRetencion.ejecutar(e.idPartida)
    }

    @Test
    fun `un aviso bajo o medio se descarta y uno alto no`() {
        val bajo = aviso("PRESTIGIO_ASCENSO", "INFORMATIVA", "BAJA", 0)
        val medio = aviso("ESPECIE_DESBLOQUEADA", "OPERATIVA", "MEDIA", 0)
        val alto = aviso("ALERTA_SALUD", "CRITICA", "ALTA", 0)

        (e.descartarAviso.ejecutar(ComandoDescartarAviso(e.idPartida, bajo, nueva())) is Resultado.Exito) shouldBe true
        (e.descartarAviso.ejecutar(ComandoDescartarAviso(e.idPartida, medio, nueva())) is Resultado.Exito) shouldBe true
        (e.descartarAviso.ejecutar(ComandoDescartarAviso(e.idPartida, alto, nueva())) as Resultado.Rechazado).motivo shouldBe
            MotivoRechazo.AvisoNoDescartable
        (e.descartarAviso.ejecutar(ComandoDescartarAviso(e.idPartida, 9999, nueva())) as Resultado.Rechazado).motivo shouldBe
            MotivoRechazo.AvisoInexistente

        abiertos() shouldBe listOf(alto)
    }

    @Test
    fun `un aviso bajo caduca al pasar un ciclo y uno medio a los tres`() {
        val bajo = aviso("PRESTIGIO_ASCENSO", "INFORMATIVA", "BAJA", 5)
        val medio = aviso("ESPECIE_DESBLOQUEADA", "OPERATIVA", "MEDIA", 5)
        val alto = aviso("ALERTA_SALUD", "CRITICA", "ALTA", 5)

        purgarEnCiclo(5)
        abiertos().toSet() shouldBe setOf(bajo, medio, alto)
        purgarEnCiclo(6)
        abiertos().toSet() shouldBe setOf(medio, alto)
        purgarEnCiclo(7)
        purgarEnCiclo(8)
        abiertos().toSet() shouldBe setOf(alto)
    }

    @Test
    fun `la oferta de una cria no caduca por la regla general de los avisos medios`() {
        val cria = aviso("REPRODUCCION_DISPONIBLE", "OPERATIVA", "MEDIA", 5, "5:a:b")
        purgarEnCiclo(9)
        abiertos() shouldBe listOf(cria)
    }

    @Test
    fun `las misiones cumplidas se numeran en el orden en que se cumplen`() {
        val porTipo = e.misionesRepo.instancias(e.idPartida).take(3)
        val (a, b, c) = porTipo
        listOf(c, a).forEach { e.misionesRepo.fijarProgreso(e.idPartida, it.idInstancia, 1.0, EstadoMision.CUMPLIDA) }
        e.misionesRepo.fijarProgreso(e.idPartida, b.idInstancia, 0.5, EstadoMision.PENDIENTE)

        val vistas = e.consultarMisiones.ejecutar(e.idPartida)
        vistas.take(2).map { it.idInstancia } shouldBe listOf(c.idInstancia, a.idInstancia)
        vistas.take(2).all { it.reclamable } shouldBe true
        vistas.drop(2).none { it.reclamable } shouldBe true
    }

    @Test
    fun `las misiones sin cumplir van de mas a menos avanzadas`() {
        val (a, b, _) = e.misionesRepo.instancias(e.idPartida).filter { it.idMision != "subir_prestigio" }.take(3)
        val objetivoA = com.pandazoo.domain.mision.ReglasMision.objetivo(a.idMision, a.numericos)!!
        val objetivoB = com.pandazoo.domain.mision.ReglasMision.objetivo(b.idMision, b.numericos)!!
        e.misionesRepo.fijarProgreso(e.idPartida, a.idInstancia, objetivoA * 0.2, EstadoMision.PENDIENTE)
        e.misionesRepo.fijarProgreso(e.idPartida, b.idInstancia, objetivoB * 0.9, EstadoMision.PENDIENTE)

        val orden = e.consultarMisiones.ejecutar(e.idPartida).map { it.idInstancia }
        (orden.indexOf(b.idInstancia) < orden.indexOf(a.idInstancia)) shouldBe true
    }

    @Test
    fun `el desglose de beneficios suma animales, espacios y habitats con la formula del ciclo`() {
        val sp = e.datos.espacio(e.idPartida, e.habitat("bosque_templado"), "Madriguera", 4)
        val a = e.datos.animal(e.idPartida, sp, "erizo", "A")
        val b = e.datos.animal(e.idPartida, sp, "erizo", "B")
        e.bd.escribir("UPDATE Animal SET satisfaccion_actual = 1.0 WHERE id_animal = ?", a)
        e.bd.escribir("UPDATE Animal SET satisfaccion_actual = 0.5 WHERE id_animal = ?", b)

        val h = e.consultarDesgloseBeneficios.ejecutar(e.idPartida).single()
        h.importe.toString() shouldBe "9.00" // rango 1: base 6,00 · (1,0 + 0,5)
        h.espacios.single().animales.map { it.importe.toString() } shouldBe listOf("6.00", "3.00")
    }
}
