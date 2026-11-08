package com.pandazoo.data

import com.pandazoo.application.caso.AlcanceRecalculo
import com.pandazoo.application.caso.EjecutarCiclo
import com.pandazoo.application.caso.EtapaReproduccion
import com.pandazoo.application.caso.Etapas
import com.pandazoo.application.caso.ResumenDiferido
import com.pandazoo.application.ciclo.MotorCiclos
import com.pandazoo.application.error.Resultado
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.measureTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Sprint 8: simulación en diferido (CU-12, RF-21 a RF-24, regla de reloj 5.2, RNF-09, RNF-12). */
@OptIn(ExperimentalCoroutinesApi::class)
class Sprint8Test {
    private lateinit var e: Entorno
    private val t0 = 1_700_000_000L

    // Azar alto: nadie enferma salvo que el test lo pida.
    @BeforeTest
    fun preparar() {
        e = Entorno(valorAzar = 0.99)
    }

    @AfterTest
    fun cerrar() = e.cerrar()

    private fun <T> exito(r: Resultado<T>): T = (r as Resultado.Exito<T>).valor

    private fun reiniciar(valorAzar: Double) {
        e.cerrar()
        e = Entorno(valorAzar = valorAzar)
    }

    private fun espacio(capacidad: Int = 20) = e.datos.espacio(e.idPartida, e.habitat("bosque_templado"), "Madriguera", capacidad)

    private fun animal(sp: String, nombre: String = "Pincho", alimentacion: Int = 100, enfermo: Boolean = false) =
        e.datos.animal(e.idPartida, sp, "erizo", nombre, alimentacion, enfermo)

    /** Deja el motor como si la app se hubiera cerrado en `t0` con ese remanente y se reabre `ausente` segundos después. */
    private fun cerradaEn(remanente: Int, ausente: Long) {
        e.bd.escribir("UPDATE EstadoSimulacion SET instante_ultimo_ciclo = ?, remanente_temporizador = ?", t0, remanente)
        e.reloj.segundos = t0 + ausente
    }

    private fun reabrir(maxCiclos: Int = e.cfg.retencion.maxCiclosOffline): ResumenDiferido =
        exito(e.procesarDiferido(maxCiclos).ejecutar(e.idPartida))

    private fun ultimoCiclo() = e.bd.uno("SELECT numero_ciclo_ultimo FROM EstadoSimulacion") { if (it.esNulo(0)) null else it.entero(0) }

    private fun remanente() = e.bd.uno("SELECT remanente_temporizador FROM EstadoSimulacion") { it.entero(0) }!!

    private fun marca() = e.bd.uno("SELECT instante_ultimo_ciclo FROM EstadoSimulacion") { if (it.esNulo(0)) null else it.largo(0) }

    private fun campoInt(id: String, col: String) = e.bd.uno("SELECT $col FROM Animal WHERE id_animal = ?", id) { it.entero(0) }!!

    // La poda (BBDD 5.3) borra tras el ciclo a los animales inactivos sin descendencia: una fila ausente equivale a MUERTO.
    private fun estadoVital(id: String) = e.bd.uno("SELECT estado_vital FROM Animal WHERE id_animal = ?", id) { it.texto(0) } ?: "MUERTO"

    private fun salud(id: String) = e.bd.uno("SELECT estado_salud FROM Animal WHERE id_animal = ?", id) { it.texto(0) }!!

    private fun fijar(id: String, col: String, v: Int) = e.bd.escribir("UPDATE Animal SET $col = ? WHERE id_animal = ?", v, id)

    // ---- T8.1 ----

    @Test
    fun `T8_1 menos tiempo que el remanente no ejecuta ningun ciclo y reduce el remanente`() {
        animal(espacio())
        cerradaEn(remanente = 300, ausente = 299)
        val r = reabrir()
        r.ciclosProcesados shouldBe 0
        ultimoCiclo() shouldBe null
        remanente() shouldBe 1
        marca() shouldBe t0 + 299
        e.contar("AuditoriaEvento", "tipo = 'RECUPERACION_DIFERIDA'") shouldBe 0
    }

    @Test
    fun `T8_1 tiempo igual al remanente ejecuta un ciclo`() {
        animal(espacio())
        cerradaEn(remanente = 300, ausente = 300)
        reabrir().ciclosProcesados shouldBe 1
        ultimoCiclo() shouldBe 1
        remanente() shouldBe 600
    }

    @Test
    fun `T8_1 remanente mas tres ciclos completos ejecuta cuatro ciclos`() {
        animal(espacio())
        cerradaEn(remanente = 300, ausente = 300 + 3 * 600L + 250)
        val r = reabrir()
        r.ciclosProcesados shouldBe 4
        r.ciclosDescartados shouldBe 0
        ultimoCiclo() shouldBe 4
        remanente() shouldBe 350
        e.contar("CicloSimulacion", "estado = 'CONFIRMADO'") shouldBe 4
        marca() shouldBe t0 + 300 + 3 * 600L + 250
    }

    // ---- T8.2 ----

    @Test
    fun `T8_2 mas tiempo que el maximo ejecuta el maximo y descarta el exceso`() {
        animal(espacio())
        cerradaEn(remanente = 300, ausente = 300 + 9 * 600L + 100)
        val r = reabrir(maxCiclos = 3)
        r.ciclosProcesados shouldBe 3
        r.ciclosDescartados shouldBe 7
        ultimoCiclo() shouldBe 3
        remanente() shouldBe 500 // se calcula igualmente con la formula habitual
        e.cfg.retencion.maxCiclosOffline shouldBe 1008
    }

    // ---- T8.3 ----

    @Test
    fun `T8_3 hambruna offline tabla completa de transiciones`() {
        val sp = espacio()
        val niveles = listOf(100, 80, 60, 40, 20, 0)
        val ids = niveles.associateWith { animal(sp, "N$it", alimentacion = it) }
        cerradaEn(remanente = 600, ausente = 600 + 4 * 600L) // cinco ciclos
        reabrir().ciclosProcesados shouldBe 5
        val final = niveles.associateWith { campoInt(ids.getValue(it), "nivel_alimentacion") }
        final shouldBe mapOf(100 to 40, 80 to 40, 60 to 40, 40 to 0, 20 to 0, 0 to 0)
        // Solo los que acaban en 0 computan, y un unico ciclo mortal.
        val contadores = niveles.associateWith { campoInt(ids.getValue(it), "ciclos_desnutricion") }
        contadores shouldBe mapOf(100 to 0, 80 to 0, 60 to 0, 40 to 1, 20 to 1, 0 to 1)
        niveles.forEach { estadoVital(ids.getValue(it)) shouldBe "VIVO" }
    }

    @Test
    fun `T8_3 un animal ya en cero con dos ciclos criticos muere con un unico ciclo mortal`() {
        val sp = espacio()
        val moribundo = animal(sp, "Moribundo", alimentacion = 0)
        fijar(moribundo, "ciclos_desnutricion", 2)
        val sano = animal(sp, "Sano", alimentacion = 0)
        cerradaEn(remanente = 600, ausente = 600 + 9 * 600L) // diez ciclos
        reabrir().ciclosProcesados shouldBe 10
        estadoVital(moribundo) shouldBe "MUERTO"
        estadoVital(sano) shouldBe "VIVO"
        campoInt(sano, "ciclos_desnutricion") shouldBe 1
    }

    // ---- T8.4 ----

    @Test
    fun `T8_4 el animal que enferma en la sesion computa un solo ciclo enfermo`() {
        reiniciar(valorAzar = 0.0)
        val sp = espacio()
        val a = animal(sp)
        fijar(a, "nivel_alimentacion", 100)
        e.bd.escribir("UPDATE Animal SET satisfaccion_actual = 0 WHERE id_animal = ?", a)
        cerradaEn(remanente = 600, ausente = 600 + 4 * 600L)
        reabrir().ciclosProcesados shouldBe 5
        salud(a) shouldBe "ENFERMO"
        campoInt(a, "ciclos_enfermedad") shouldBe 1
        estadoVital(a) shouldBe "VIVO"
    }

    @Test
    fun `T8_4 el animal ya enfermo acumula como maximo dos ciclos adicionales`() {
        val sp = espacio()
        val a = animal(sp, "Cero", enfermo = true)
        val b = animal(sp, "Uno", enfermo = true)
        fijar(b, "ciclos_enfermedad", 1)
        cerradaEn(remanente = 600, ausente = 600 + 4 * 600L)
        reabrir().ciclosProcesados shouldBe 5
        estadoVital(a) shouldBe "VIVO"
        campoInt(a, "ciclos_enfermedad") shouldBe 2
        // Con 1 acumulado, dos mas son 3: muere por ciclos criticos.
        estadoVital(b) shouldBe "MUERTO"
    }

    // ---- T8.5 ----

    @Test
    fun `T8_5 reabrir dos veces seguidas no repite ciclos`() {
        animal(espacio())
        cerradaEn(remanente = 300, ausente = 300 + 2 * 600L)
        reabrir().ciclosProcesados shouldBe 3
        val antes = listOf(ultimoCiclo(), e.contar("CicloSimulacion"), e.contar("BeneficioPendiente"), e.contar("AuditoriaEvento"))
        val r = reabrir()
        r.ciclosProcesados shouldBe 0
        listOf(ultimoCiclo(), e.contar("CicloSimulacion"), e.contar("BeneficioPendiente"), e.contar("AuditoriaEvento")) shouldBe antes
        e.contar("AuditoriaEvento", "tipo = 'RECUPERACION_DIFERIDA'") shouldBe 1
    }

    // ---- T8.6 ----

    @Test
    fun `T8_6 el remanente persiste en segundos y la reanudacion es exacta`() {
        animal(espacio())
        e.ciclos.guardarRemanente(e.idPartida, 350, t0)
        e.reloj.segundos = t0 + 100
        reabrir().ciclosProcesados shouldBe 0
        remanente() shouldBe 250
        marca() shouldBe t0 + 100
        e.reloj.segundos = t0 + 100 + 250
        reabrir().ciclosProcesados shouldBe 1
        remanente() shouldBe 600
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `T8_6 al pasar a segundo plano se guardan remanente y marca de actividad juntos`() =
        runTest {
            animal(espacio())
            e.reloj.segundos = t0
            val motor = MotorCiclos(
                scope = backgroundScope,
                idPartida = e.idPartida,
                ciclos = e.ciclos,
                ejecutarCiclo = e.ejecutarCiclo,
                purga = e.purgarRetencion,
                monotonico = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler),
                reloj = e.reloj,
            )
            motor.iniciar()
            advanceTimeBy(250_000)
            runCurrent()
            e.reloj.segundos = t0 + 250
            motor.detener()
            remanente() shouldBe 350
            marca() shouldBe t0 + 250
        }

    // ---- T8.7 ----

    @Test
    fun `T8_7 cierre forzado en cada etapa del ciclo conserva el ultimo estado consistente y se completa en menos de 3 s`() {
        val etapas = listOf(
            Etapas.ALIMENTACION, Etapas.SALUD, Etapas.CICLOS_CRITICOS, Etapas.DEFUNCIONES, Etapas.NIVEL,
            Etapas.REPRODUCCION, Etapas.BENEFICIOS, Etapas.MISIONES, Etapas.METRICAS, Etapas.PERSISTENCIA,
        )
        for (fallo in etapas) {
            reiniciar(valorAzar = 0.99)
            val sp = espacio()
            val a = animal(sp)
            e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
            cerradaEn(remanente = 600, ausente = 600 + 2 * 600L) // tres ciclos
            var enCiclo = 0
            var visitas = 0
            val defectuoso = EjecutarCiclo(
                e.ejecutor, e.ciclos, e.animales, e.auditoria, e.recalcular, e.azar, e.reloj,
                traza = { etapa, _ ->
                    if (etapa == Etapas.ALIMENTACION) enCiclo++
                    if (etapa == fallo && enCiclo == 2) { visitas++; error("cierre forzado en $fallo") }
                },
                reproduccion = EtapaReproduccion(e.reproduccion, e.auditoria, e.azar, e.reloj),
            )
            shouldThrow<IllegalStateException> { e.procesarDiferido(ciclo = defectuoso).ejecutar(e.idPartida) }
            visitas shouldBe 1
            // Ultimo estado consistente: solo el ciclo 1 confirmado, con su marca virtual y sin efectos del 2.
            ultimoCiclo() shouldBe 1
            e.contar("CicloSimulacion", "estado = 'CONFIRMADO'") shouldBe 1
            marca() shouldBe t0 + 600
            remanente() shouldBe 600
            campoInt(a, "nivel_alimentacion") shouldBe 80
            e.contar("AuditoriaEvento", "tipo = 'RECUPERACION_DIFERIDA'") shouldBe 0

            // Reapertura: se completan solo los ciclos no confirmados, sin duplicar efectos y en menos de 3 s.
            val duracion = measureTime { reabrir().ciclosProcesados shouldBe 2 }
            (duracion.inWholeMilliseconds < 3000) shouldBe true
            ultimoCiclo() shouldBe 3
            e.contar("CicloSimulacion", "estado = 'CONFIRMADO'") shouldBe 3
            campoInt(a, "nivel_alimentacion") shouldBe 40
            e.contar("BeneficioPendiente") shouldBe e.contar("BeneficioPendiente", "numero_ciclo IN (1, 2, 3)")
            (e.bd.uno("SELECT COUNT(DISTINCT numero_ciclo) FROM BeneficioPendiente") { it.entero(0) } ?: 0) shouldBe e.contar("BeneficioPendiente")
            e.contar("AuditoriaEvento", "tipo = 'RECUPERACION_DIFERIDA'") shouldBe 1
            remanente() shouldBe 600
        }
    }

    // ---- T8.8 ----

    @Test
    fun `T8_8 ausencia larga con moribundos reproduccion pendiente y caducada`() {
        reiniciar(valorAzar = 0.99)
        e.bd.escribir(
            "UPDATE RangoEspecie SET umbral_reproduccion = 0.0, probabilidad_reproduccion = 1.0, factores_hijos = '1;1;1;1;1;0' WHERE id_rango = 1",
        )
        val sp = espacio()
        val a = animal(sp, "Pincho")
        val b = animal(sp, "Espina")
        e.datos.parejas(e.idPartida, a, b)
        val moribundo = animal(sp, "Moribundo", alimentacion = 0)
        fijar(moribundo, "ciclos_desnutricion", 2)
        val enfermo = animal(sp, "Enfermo", enfermo = true)
        fijar(enfermo, "ciclos_enfermedad", 2)
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)

        cerradaEn(remanente = 100, ausente = 100 + 7 * 600L) // ocho ciclos
        val r = reabrir()
        r.ciclosProcesados shouldBe 8
        ultimoCiclo() shouldBe 8
        estadoVital(moribundo) shouldBe "MUERTO"
        estadoVital(enfermo) shouldBe "MUERTO"
        estadoVital(a) shouldBe "VIVO"
        // Las concepciones de los primeros ciclos caducaron dentro de la sesion y su aviso se descarto. Las finalizadas fuera de
        // la ventana de retencion (3 ciclos) ya se purgaron (BBDD 5.2); las que quedan siguen siendo coherentes con sus avisos.
        e.contar("ReproduccionCiclo", "numero_ciclo < ${8 - e.cfg.retencion.reproduccionFinalizada} AND estado <> 'CONCEBIDA'") shouldBe 0
        (e.contar("Notificacion", "codigo = 'REPRODUCCION_DISPONIBLE' AND estado = 'DESCARTADA'") >= 1) shouldBe true
        (e.contar("Notificacion", "codigo = 'REPRODUCCION_DISPONIBLE' AND estado = 'DESCARTADA'") >= e.contar("ReproduccionCiclo", "estado = 'EXPIRADA'")) shouldBe true
        // Sin duplicados de beneficios ni de alertas de salud abiertas por animal.
        (e.bd.uno("SELECT COUNT(DISTINCT numero_ciclo) FROM BeneficioPendiente") { it.entero(0) } ?: 0) shouldBe e.contar("BeneficioPendiente")
        e.contar("Notificacion", "codigo = 'ALERTA_SALUD' AND estado IN ('PENDIENTE', 'VISTA')") shouldBe 0
        e.contar("AuditoriaEvento", "tipo = 'RECUPERACION_DIFERIDA'") shouldBe 1
    }

    // ---- T8.9 ----

    @Test
    fun `T8_9 salto hacia atras da E cero y no regala ciclos`() {
        animal(espacio())
        e.bd.escribir("UPDATE EstadoSimulacion SET instante_ultimo_ciclo = ?, remanente_temporizador = 350", t0 + 1000)
        e.reloj.segundos = t0
        val r = reabrir()
        r.relojAnomalo shouldBe true
        r.ciclosProcesados shouldBe 0
        remanente() shouldBe 350
        marca() shouldBe t0
        e.contar("AuditoriaEvento", "tipo = 'RELOJ_ANOMALO'") shouldBe 1
        ultimoCiclo() shouldBe null

        // Atras y luego adelante: se cuenta desde la marca nueva, no se regala el tiempo.
        e.reloj.segundos = t0 + 100
        reabrir().ciclosProcesados shouldBe 0
        remanente() shouldBe 250
        ultimoCiclo() shouldBe null
    }

    @Test
    fun `T8_9 un salto hacia delante grande queda limitado por el maximo`() {
        animal(espacio())
        cerradaEn(remanente = 600, ausente = 10L * 365 * 24 * 3600)
        val r = reabrir(maxCiclos = 12)
        r.ciclosProcesados shouldBe 12
        (r.ciclosDescartados > 0) shouldBe true
        ultimoCiclo() shouldBe 12
        marca() shouldBe e.reloj.segundos
    }

    @Test
    fun `T8_9 marca ausente no procesa ciclos y reinicializa la marca`() {
        animal(espacio())
        e.bd.escribir("UPDATE EstadoSimulacion SET instante_ultimo_ciclo = NULL, remanente_temporizador = 200")
        e.reloj.segundos = t0 + 5000
        reabrir().ciclosProcesados shouldBe 0
        ultimoCiclo() shouldBe null
        remanente() shouldBe 200
        marca() shouldBe t0 + 5000
    }

    @Test
    fun `T8_9 simulacion en pausa no consume el tiempo ausente`() {
        animal(espacio())
        cerradaEn(remanente = 100, ausente = 10_000)
        e.bd.escribir("UPDATE EstadoSimulacion SET estado = 'PAUSADO'")
        reabrir().ciclosProcesados shouldBe 0
        ultimoCiclo() shouldBe null
        remanente() shouldBe 100
        marca() shouldBe t0 + 10_000
    }

    // ---- T8.10 ----

    @Test
    fun `T8_10 volver a primer plano con el proceso vivo da el mismo resultado que un arranque en frio`() {
        fun escenario(): Entorno {
            val x = Entorno(valorAzar = 0.99)
            val sp = x.datos.espacio(x.idPartida, x.habitat("bosque_templado"), "Madriguera", 20)
            listOf(100, 60, 20, 0).forEach { x.datos.animal(x.idPartida, sp, "erizo", "N$it", it) }
            x.bd.escribir("UPDATE EstadoSimulacion SET instante_ultimo_ciclo = ?, remanente_temporizador = 400", t0)
            x.reloj.segundos = t0 + 400 + 3 * 600L + 17
            return x
        }

        fun foto(x: Entorno) = listOf(
            x.bd.leer("SELECT id_animal, nivel_alimentacion, estado_salud, estado_vital, ciclos_desnutricion, ciclos_enfermedad FROM Animal ORDER BY id_animal") {
                listOf(it.texto(0), it.entero(1), it.texto(2), it.texto(3), it.entero(4), it.entero(5))
            },
            x.bd.uno("SELECT numero_ciclo_ultimo, remanente_temporizador, instante_ultimo_ciclo FROM EstadoSimulacion") {
                listOf(it.entero(0), it.entero(1), it.largo(2))
            },
            x.contar("BeneficioPendiente"),
            x.contar("AuditoriaEvento", "tipo = 'RECUPERACION_DIFERIDA'"),
        )

        val frio = escenario()
        val vivo = escenario()
        try {
            exito(frio.procesarDiferido().ejecutar(frio.idPartida)).ciclosProcesados shouldBe 4
            // Proceso vivo: el mismo caso de uso, ya instanciado, tras pasar a segundo plano y volver.
            val caso = vivo.procesarDiferido()
            exito(caso.ejecutar(vivo.idPartida)).ciclosProcesados shouldBe 4
            foto(vivo) shouldBe foto(frio)
        } finally {
            frio.cerrar()
            vivo.cerrar()
        }
    }

    @Test
    fun `T8_10 un cambio de hora con la app abierta no altera el ciclo en vivo`() =
        runTest {
            animal(espacio())
            e.reloj.segundos = t0
            val motor = MotorCiclos(
                scope = backgroundScope,
                idPartida = e.idPartida,
                ciclos = e.ciclos,
                ejecutarCiclo = e.ejecutarCiclo,
                purga = e.purgarRetencion,
                monotonico = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler),
                reloj = e.reloj,
            )
            motor.iniciar()
            advanceTimeBy(100_000)
            runCurrent()
            e.reloj.avanzar(50_000) // el usuario cambia la hora del dispositivo
            advanceTimeBy(499_000)
            runCurrent()
            ultimoCiclo() shouldBe null
            advanceTimeBy(1_000)
            runCurrent()
            ultimoCiclo() shouldBe 1
            motor.detener()
        }
}
