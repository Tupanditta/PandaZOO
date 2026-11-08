package com.pandazoo.data

import com.pandazoo.application.caso.ComandoReclamarBeneficios
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Sprint 10: retención y purga completas (BBDD 5.2 y 5.3, RNF-14, RES-11). */
class Sprint10Test {
    private lateinit var e: Entorno

    @BeforeTest
    fun preparar() {
        e = Entorno(valorAzar = 0.5)
    }

    @AfterTest
    fun cerrar() = e.cerrar()

    private fun espacio() = e.datos.espacio(e.idPartida, e.habitat("bosque_templado"), "Madriguera", 30)

    private fun animal(sp: String, nombre: String, alimentacion: Int = 100) =
        e.datos.animal(e.idPartida, sp, "erizo", nombre, alimentacion, false)

    private fun inactivar(id: String, estado: String = "MUERTO") =
        e.bd.escribir("UPDATE Animal SET estado_vital = ?, id_espacio = NULL WHERE id_animal = ?", estado, id)

    private fun enCiclo(n: Int) = e.bd.escribir("UPDATE EstadoSimulacion SET numero_ciclo_ultimo = ?", n)

    private fun existe(id: String) = e.contar("Animal", "id_animal = '$id'") == 1

    @Test
    fun `el prestigio 100 exige un 95 por ciento de satisfaccion y la tabla no decrece`() {
        val tabla = e.cfg.tablas.nivelesPrestigio
        tabla.last().nivel shouldBe 100
        tabla.last().umbralSatisfaccion shouldBe 0.95
        tabla.zipWithNext().all { (a, b) -> b.umbralSatisfaccion >= a.umbralSatisfaccion } shouldBe true
        tabla.first { it.nivel == 60 }.umbralSatisfaccion shouldBe 0.8857
    }

    @Test
    fun `T10_6 la poda borra un animal inactivo sin descendencia ni reproduccion`() {
        val sp = espacio()
        val solo = animal(sp, "Solo")
        inactivar(solo)
        e.purgarRetencion.ejecutar(e.idPartida) // sin ciclo vigente no hace nada
        enCiclo(1)
        e.purgarRetencion.ejecutar(e.idPartida)
        existe(solo) shouldBe false
    }

    @Test
    fun `T10_6 la poda conserva a los progenitores de un animal vivo y no toca a los vivos`() {
        val sp = espacio()
        val padre = animal(sp, "Padre")
        val madre = animal(sp, "Madre")
        val cria = animal(sp, "Cria")
        e.bd.escribir("UPDATE Animal SET id_padre = ?, id_madre = ? WHERE id_animal = ?", padre, madre, cria)
        inactivar(padre)
        inactivar(madre, "VENDIDO")
        enCiclo(1)
        e.purgarRetencion.ejecutar(e.idPartida)
        existe(padre) shouldBe true
        existe(madre) shouldBe true
        existe(cria) shouldBe true
    }

    @Test
    fun `T10_6 la poda libera la cadena genealogica completa cuando muere el ultimo descendiente`() {
        val sp = espacio()
        val abuelo = animal(sp, "Abuelo")
        val padre = animal(sp, "Padre")
        val nieto = animal(sp, "Nieto")
        e.bd.escribir("UPDATE Animal SET id_padre = ? WHERE id_animal = ?", abuelo, padre)
        e.bd.escribir("UPDATE Animal SET id_padre = ? WHERE id_animal = ?", padre, nieto)
        listOf(abuelo, padre, nieto).forEach { inactivar(it) }
        enCiclo(1)
        e.purgarRetencion.ejecutar(e.idPartida)
        e.contar("Animal") shouldBe 0
    }

    @Test
    fun `T10_6 la poda respeta una reproduccion concebida y la libera al purgarla`() {
        val sp = espacio()
        val a = animal(sp, "A")
        val b = animal(sp, "B")
        val (menor, mayor) = listOf(a, b).sorted()
        listOf(a, b).forEach { inactivar(it) }
        e.bd.escribir(
            "INSERT INTO ReproduccionCiclo (id_partida, numero_ciclo, id_animal_a, id_animal_b, estado, ciclo_disponible, ciclo_expiracion) " +
                "VALUES (?, 1, ?, ?, 'CONCEBIDA', 2, 4)",
            e.idPartida, menor, mayor,
        )
        enCiclo(50)
        e.purgarRetencion.ejecutar(e.idPartida)
        e.contar("ReproduccionCiclo") shouldBe 1
        e.contar("Animal") shouldBe 2

        e.bd.escribir("UPDATE ReproduccionCiclo SET estado = 'EXPIRADA'")
        e.purgarRetencion.ejecutar(e.idPartida)
        e.contar("ReproduccionCiclo") shouldBe 0
        e.contar("Animal") shouldBe 0
    }

    @Test
    fun `T10_6 una reproduccion finalizada dentro de la ventana se conserva`() {
        val sp = espacio()
        val a = animal(sp, "A")
        val b = animal(sp, "B")
        val (menor, mayor) = listOf(a, b).sorted()
        e.bd.escribir(
            "INSERT INTO ReproduccionCiclo (id_partida, numero_ciclo, id_animal_a, id_animal_b, estado, ciclo_disponible, ciclo_expiracion) " +
                "VALUES (?, 10, ?, ?, 'RECHAZADA', 11, 13)",
            e.idPartida, menor, mayor,
        )
        enCiclo(10 + e.cfg.retencion.reproduccionFinalizada)
        e.purgarRetencion.ejecutar(e.idPartida)
        e.contar("ReproduccionCiclo") shouldBe 1
        enCiclo(11 + e.cfg.retencion.reproduccionFinalizada)
        e.purgarRetencion.ejecutar(e.idPartida)
        e.contar("ReproduccionCiclo") shouldBe 0
    }

    @Test
    fun `T10_6 simulacion larga con purga mantiene acotado el historico`() {
        // Ventanas cortas para que el histórico se estabilice pronto y la prueba corra dentro del límite de la web.
        e.cerrar()
        e = Entorno(Entorno.configuracionBase.let { it.copy(retencion = it.retencion.copy(auditoriaEvento = 20, movimientoEconomico = 20, idempotencia = 3)) }, 0.5)
        val sp = espacio()
        val ids = (1..5).map { animal(sp, "Zoo$it") }
        fun historico() = listOf("AuditoriaEvento", "MovimientoEconomico", "CicloSimulacion", "BeneficioPendiente", "Notificacion")
            .sumOf { e.contar(it) }
        var tras60 = 0
        for (n in 1..100) {
            // Se mantiene a los animales vivos y alimentados para que el ciclo genere histórico en cada vuelta.
            e.bd.escribir("UPDATE Animal SET nivel_alimentacion = 100, estado_salud = 'SANO', ciclos_desnutricion = 0, ciclos_enfermedad = 0 WHERE estado_vital = 'VIVO'")
            e.ejecutarCiclo.siguiente(e.idPartida)
            // El jugador reclama de vez en cuando; los beneficios PENDIENTE nunca se purgan (BBDD 5.2).
            if (n % 20 == 0) e.reclamarBeneficios.ejecutar(ComandoReclamarBeneficios(e.idPartida, null, "rec-$n"))
            e.purgarRetencion.ejecutar(e.idPartida)
            if (n == 60) tras60 = historico()
        }
        val tras100 = historico()
        (tras100 <= tras60 + 20) shouldBe true
        ids.forEach { existe(it) shouldBe true }
        e.contar("CicloSimulacion") shouldBe e.cfg.retencion.cicloSimulacion + 1
    }
}
