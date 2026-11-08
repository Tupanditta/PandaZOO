package com.pandazoo.data

import com.pandazoo.application.caso.AlcanceRecalculo
import com.pandazoo.application.caso.ComandoAbrirAviso
import com.pandazoo.application.caso.ComandoAlimentarAnimal
import com.pandazoo.application.caso.ComandoEjecutarCiclo
import com.pandazoo.application.caso.ComandoReclamarBeneficios
import com.pandazoo.application.caso.ComandoTratarAnimal
import com.pandazoo.application.caso.EjecutarCiclo
import com.pandazoo.application.caso.EtapaCiclo
import com.pandazoo.application.caso.Etapas
import com.pandazoo.application.ciclo.MotorCiclos
import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.puerto.SeveridadAviso
import com.pandazoo.domain.economia.Dinero
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
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

/** Sprint 5: ciclo de simulación (CU-11), beneficios (CU-08), seguimiento crítico (CU-18) y avisos (CU-21). */
@OptIn(ExperimentalCoroutinesApi::class)
class Sprint5Test {
    private lateinit var e: Entorno
    private var solicitud = 0

    @BeforeTest
    fun preparar() {
        e = Entorno(valorAzar = 0.5)
    }

    @AfterTest
    fun cerrar() = e.cerrar()

    private fun nueva() = "sol-${++solicitud}"

    private fun d(v: String) = Dinero.desdeDecimal(v.toDouble())

    private fun rechazo(r: Resultado<*>): MotivoRechazo = (r as Resultado.Rechazado).motivo

    private fun <T> exito(r: Resultado<T>): T = (r as Resultado.Exito<T>).valor

    private fun espacio(nombre: String = "Madriguera", capacidad: Int = 10) =
        e.datos.espacio(e.idPartida, e.habitat("bosque_templado"), nombre, capacidad)

    private fun animal(sp: String, especie: String = "erizo", nombre: String = "Pincho", alimentacion: Int = 100, enfermo: Boolean = false) =
        e.datos.animal(e.idPartida, sp, especie, nombre, alimentacion, enfermo)

    private fun ciclo() = e.ejecutarCiclo.siguiente(e.idPartida)

    private fun campoInt(id: String, col: String) = e.bd.uno("SELECT $col FROM Animal WHERE id_animal = ?", id) { it.entero(0) }!!

    private fun estadoVital(id: String) = e.bd.uno("SELECT estado_vital FROM Animal WHERE id_animal = ?", id) { it.texto(0) }!!

    private fun salud(id: String) = e.bd.uno("SELECT estado_salud FROM Animal WHERE id_animal = ?", id) { it.texto(0) }!!

    private fun fijarSatisfaccion(id: String, s: Double) =
        e.bd.escribir("UPDATE Animal SET satisfaccion_actual = ? WHERE id_animal = ?", s, id)

    private fun pendientes() = e.beneficios.pendientes(e.idPartida)

    private fun ultimoCiclo() = e.bd.uno("SELECT numero_ciclo_ultimo FROM EstadoSimulacion") { if (it.esNulo(0)) null else it.entero(0) }

    private fun alertas() =
        e.bd.leer("SELECT id_entidad, estado FROM Notificacion WHERE codigo = 'ALERTA_SALUD' ORDER BY id_notificacion") { it.texto(0) to it.texto(1) }

    private fun instantanea(): List<Any?> = listOf(
        e.fondos(), e.contar("Animal"), e.contar("CicloSimulacion"), e.contar("BeneficioPendiente"), e.contar("Notificacion"),
        ultimoCiclo(),
        e.bd.leer("SELECT id_animal, nivel, ciclos_nivel, nivel_alimentacion, estado_salud, estado_vital, ciclos_desnutricion, ciclos_enfermedad, satisfaccion_actual FROM Animal ORDER BY id_animal") {
            listOf(it.texto(0), it.entero(1), it.entero(2), it.entero(3), it.texto(4), it.texto(5), it.entero(6), it.entero(7), it.decimal(8))
        },
        e.bd.leer("SELECT nivel, id_espacio, valor FROM MetricaSatisfaccion ORDER BY id_metrica") { listOf(it.texto(0), it.textoONulo(1), it.decimal(2)) },
    )

    // ---- T5.1 orden de etapas ----

    @Test
    fun `T5_1 las etapas van en orden y cada una ve el resultado de la anterior`() {
        val sp = espacio()
        val a = animal(sp, alimentacion = 100)
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        val vistas = mutableListOf<Triple<String, Int, Int>>() // etapa, ciclos de nivel, alimentación
        val extension = mutableListOf<Int>()
        val ext = EtapaCiclo { _, _, vivos -> extension += vivos.single().alimentacion }
        val caso = EjecutarCiclo(
            e.ejecutor, e.ciclos, e.animales, e.auditoria, e.recalcular, e.azar, e.reloj,
            traza = { etapa, animales -> vistas += Triple(etapa, animales.single().ciclosNivel, animales.single().alimentacion) },
            reproduccion = ext,
            misiones = ext,
        )
        exito(caso.ejecutar(ComandoEjecutarCiclo(e.idPartida, 1)))

        vistas.map { it.first } shouldContainExactly listOf(
            Etapas.ALIMENTACION, Etapas.SALUD, Etapas.CICLOS_CRITICOS, Etapas.DEFUNCIONES, Etapas.NIVEL, Etapas.REPRODUCCION,
            Etapas.BENEFICIOS, Etapas.MISIONES, Etapas.METRICAS, Etapas.PERSISTENCIA,
        )
        // La alimentación ya baja en su etapa; el progreso de nivel solo suma en la etapa de nivel, tras las defunciones.
        vistas.first { it.first == Etapas.ALIMENTACION }.let { it.second shouldBe 0; it.third shouldBe 80 }
        vistas.first { it.first == Etapas.DEFUNCIONES }.let { it.second shouldBe 0; it.third shouldBe 80 }
        vistas.first { it.first == Etapas.NIVEL }.let { it.second shouldBe 1; it.third shouldBe 80 }
        // Las extensiones se ejecutan una vez cada una y ya ven la alimentación reducida por la etapa 1.
        extension shouldContainExactly listOf(80, 80)
    }

    // ---- T5.3 alimentación ----

    @Test
    fun `T5_3 la alimentacion baja de 20 en 20 y nunca es negativa`() {
        val sp = espacio()
        val a = animal(sp)
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        val serie = ArrayList<Int>()
        repeat(5) { exito(ciclo()); serie += campoInt(a, "nivel_alimentacion") }
        serie shouldContainExactly listOf(80, 60, 40, 20, 0)
        e.bd.escribir("UPDATE Animal SET nivel_alimentacion = 10 WHERE id_animal = ?", a)
        exito(ciclo())
        campoInt(a, "nivel_alimentacion") shouldBe 0
    }

    // ---- T5.4 enfermedad ----

    private fun probabilidadRango1() =
        e.bd.uno("SELECT probabilidad_enfermedad FROM RangoEspecie WHERE id_rango = 1") { it.decimal(0) }!!

    @Test
    fun `T5_4 con aleatorio 0 y satisfaccion 0 el animal sano enferma`() {
        e.cerrar()
        e = Entorno(valorAzar = 0.0)
        val sp = espacio()
        val a = animal(sp)
        fijarSatisfaccion(a, 0.0)
        val r = exito(ciclo())
        salud(a) shouldBe "ENFERMO"
        r.nuevosEnfermos shouldBe 1
        e.auditoriaDe("CAMBIO_ESTADO_SALUD").map { it.first } shouldContainExactly listOf("CONFIRMADO")
    }

    @Test
    fun `T5_4 satisfaccion 1 o probabilidad de umbral 0 nunca enferman`() {
        e.cerrar()
        e = Entorno(valorAzar = 0.0)
        val sp = espacio()
        val a = animal(sp)
        fijarSatisfaccion(a, 1.0) // 1 - S = 0: el umbral es 0 y aleatorio 0 no es menor
        exito(ciclo()) // en un solo ciclo: después la satisfacción se recalcula y ya no vale 1
        salud(a) shouldBe "SANO"
    }

    @Test
    fun `T5_4 la frontera aleatorio igual al umbral no enferma`() {
        val umbral = probabilidadRango1() * (1.0 - 0.5)
        e.cerrar()
        e = Entorno(valorAzar = umbral)
        val sp = espacio()
        val a = animal(sp)
        fijarSatisfaccion(a, 0.5)
        exito(ciclo())
        salud(a) shouldBe "SANO"
    }

    @Test
    fun `T5_4 solo se sortean los vivos alojados y sanos`() {
        e.cerrar()
        e = Entorno(valorAzar = 0.0)
        val sp = espacio()
        val enfermo = animal(sp, nombre = "Enfermo", enfermo = true)
        val vendido = animal(sp, nombre = "Vendido")
        e.animales.retirar(e.idPartida, vendido, "VENDIDO")
        fijarSatisfaccion(enfermo, 0.0)
        fijarSatisfaccion(vendido, 0.0)
        val r = exito(ciclo())
        r.nuevosEnfermos shouldBe 0
        estadoVital(vendido) shouldBe "VENDIDO"
        salud(vendido) shouldBe "SANO"
        salud(enfermo) shouldBe "ENFERMO"
    }

    // ---- T5.5 contadores críticos ----

    @Test
    fun `T5_5 tres ciclos con alimentacion cero provocan la muerte`() {
        val sp = espacio()
        val a = animal(sp, alimentacion = 0)
        exito(ciclo())
        campoInt(a, "ciclos_desnutricion") shouldBe 1
        exito(ciclo())
        campoInt(a, "ciclos_desnutricion") shouldBe 2
        estadoVital(a) shouldBe "VIVO"
        val r = exito(ciclo())
        r.muertos shouldBe 1
        estadoVital(a) shouldBe "MUERTO"
        e.auditoriaDe("DEFUNCION").map { it.first } shouldContainExactly listOf("CONFIRMADO")
    }

    @Test
    fun `T5_5 los contadores son independientes y se reinician al recuperarse`() {
        val sp = espacio()
        val enfermo = animal(sp, nombre = "Enfermo", enfermo = true)
        exito(ciclo())
        campoInt(enfermo, "ciclos_enfermedad") shouldBe 1
        campoInt(enfermo, "ciclos_desnutricion") shouldBe 0
        exito(e.tratarAnimal.ejecutar(ComandoTratarAnimal(e.idPartida, enfermo, nueva())))
        campoInt(enfermo, "ciclos_enfermedad") shouldBe 0
        exito(ciclo())
        campoInt(enfermo, "ciclos_enfermedad") shouldBe 0
        salud(enfermo) shouldBe "SANO"
    }

    @Test
    fun `T5_5 con ambas condiciones se usa el mayor contador para la defuncion`() {
        val sp = espacio()
        val a = animal(sp, "erizo", "Doble", alimentacion = 0, enfermo = true)
        e.bd.escribir("UPDATE Animal SET ciclos_desnutricion = 0, ciclos_enfermedad = 2 WHERE id_animal = ?", a)
        exito(ciclo()) // desnutrición 1, enfermedad 3: la enfermedad alcanza el límite
        estadoVital(a) shouldBe "MUERTO"
    }

    // ---- T5.6 defunción (TN.7: ya no hay muerte por vejez) ----

    @Test
    fun `T5_6 la muerte por ciclos criticos libera aforo anula vinculos y conserva la genealogia`() {
        val sp = espacio(capacidad = 4)
        val padre = animal(sp, nombre = "Padre")
        val madre = animal(sp, nombre = "Madre")
        val amigo = animal(sp, nombre = "Amigo")
        val hijo = e.datos.animal(e.idPartida, sp, "erizo", "Hijo", 100, false, padre, madre)
        e.animales.fijarPareja(e.idPartida, padre, madre)
        e.animales.fijarMejorAmigo(e.idPartida, amigo, padre)
        // El padre llega al tercer ciclo de desnutrición con este ciclo.
        e.bd.escribir("UPDATE Animal SET nivel_alimentacion = 0, ciclos_desnutricion = 2 WHERE id_animal = ?", padre)
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        e.consultarZoo.espacio(e.idPartida, sp)!!.ocupacion shouldBe 4

        val r = exito(ciclo())
        r.muertos shouldBe 1
        estadoVital(padre) shouldBe "MUERTO"
        e.consultarZoo.espacio(e.idPartida, sp)!!.ocupacion shouldBe 3
        e.bd.uno("SELECT id_espacio FROM Animal WHERE id_animal = ?", padre) { it.textoONulo(0) } shouldBe null
        e.bd.uno("SELECT id_pareja FROM Animal WHERE id_animal = ?", madre) { it.textoONulo(0) } shouldBe null
        e.bd.uno("SELECT id_mejor_amigo FROM Animal WHERE id_animal = ?", amigo) { it.textoONulo(0) } shouldBe null
        e.bd.uno("SELECT id_padre FROM Animal WHERE id_animal = ?", hijo) { it.textoONulo(0) } shouldBe padre
        e.auditoriaDe("DEFUNCION").size shouldBe 1
    }

    // ---- T5.7 beneficio ----

    @Test
    fun `T5_7 el beneficio sigue la formula y se calcula tras las defunciones`() {
        val sp = espacio()
        val a = animal(sp, "erizo", "A") // rango 1: base 6,00
        val b = animal(sp, "erizo", "B")
        val rey = animal(sp, "leon", "Rey") // rango 3: base 20,00
        listOf(a, b, rey).forEach { fijarSatisfaccion(it, 0.5) }
        // Por animal, nivel 1: 6 · 0,5 + 6 · 0,5 + 20 · 0,5 = 16,00
        exito(ciclo()).beneficio shouldBe d("16.00")
        pendientes() shouldBe d("16.00")
        e.contar("BeneficioPendiente") shouldBe 1

        // Uno de los erizos muere en el ciclo 2 (tercer ciclo crítico): no cuenta (3 + 10 = 13,00).
        e.bd.escribir("UPDATE Animal SET nivel_alimentacion = 0, ciclos_desnutricion = 2 WHERE id_animal = ?", b)
        listOf(a, b, rey).forEach { fijarSatisfaccion(it, 0.5) } // el recálculo del ciclo 1 cambió la satisfacción
        exito(ciclo()).beneficio shouldBe d("13.00")
        pendientes() shouldBe d("29.00")
    }

    @Test
    fun `T5_7 sin animales vivos el beneficio es cero y no hay fila`() {
        espacio()
        exito(ciclo()).beneficio shouldBe Dinero.CERO
        e.contar("BeneficioPendiente") shouldBe 0
        pendientes() shouldBe Dinero.CERO
    }

    @Test
    fun `T5_7 satisfaccion fraccionaria redondea al centimo con los medios hacia arriba`() {
        val sp = espacio()
        val a = animal(sp)
        fijarSatisfaccion(a, 0.0025) // 6,00 · 0,0025 = 0,015 → 0,02 (medio hacia arriba)
        exito(ciclo()).beneficio shouldBe d("0.02")
        val b = animal(sp, nombre = "B")
        fijarSatisfaccion(a, 0.3333)
        fijarSatisfaccion(b, 0.3333) // 6 · 0,3333 + 6 · 0,3333 = 3,9996 → 4,00
        exito(ciclo()).beneficio shouldBe d("4.00")
    }

    // ---- T5.8 mutaciones entre ciclos ----

    @Test
    fun `T5_8 las mutaciones entre ciclos no alteran el acumulado reclamable`() {
        val sp = espacio()
        val a = animal(sp, alimentacion = 60)
        fijarSatisfaccion(a, 0.5)
        exito(ciclo())
        val acumulado = pendientes()
        (acumulado > Dinero.CERO) shouldBe true

        exito(e.alimentarAnimal.ejecutar(ComandoAlimentarAnimal(e.idPartida, a, nueva())))
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        animal(sp, "leon", "Rey")
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        pendientes() shouldBe acumulado
        e.contar("BeneficioPendiente") shouldBe 1
    }

    // ---- T5.9 reclamar ----

    @Test
    fun `T5_9 reclamar acredita el total marca los registros y una segunda vez no acredita nada`() {
        val sp = espacio()
        val a = animal(sp)
        fijarSatisfaccion(a, 1.0)
        repeat(3) { fijarSatisfaccion(a, 1.0); exito(ciclo()) }
        pendientes() shouldBe d("18.00")
        val antes = e.fondos()

        val r = exito(e.reclamarBeneficios.ejecutar(ComandoReclamarBeneficios(e.idPartida, d("18.00"), nueva())))
        r.importe shouldBe d("18.00")
        r.saldo shouldBe antes + d("18.00")
        e.fondos() shouldBe antes + d("18.00")
        pendientes() shouldBe Dinero.CERO
        e.contar("BeneficioPendiente", "estado = 'RECLAMADO' AND fecha_reclamacion IS NOT NULL") shouldBe 3
        e.contar("MovimientoEconomico", "tipo = 'INGRESO' AND importe = 18.0") shouldBe 1

        rechazo(e.reclamarBeneficios.ejecutar(ComandoReclamarBeneficios(e.idPartida, null, nueva()))) shouldBe MotivoRechazo.SinBeneficios
        e.fondos() shouldBe antes + d("18.00")
        e.contar("MovimientoEconomico", "tipo = 'INGRESO'") shouldBe 1
    }

    @Test
    fun `T5_9 si el importe mostrado ya no coincide se rechaza y el reintento es idempotente`() {
        val sp = espacio()
        val a = animal(sp)
        fijarSatisfaccion(a, 1.0)
        exito(ciclo())
        val antes = e.fondos()
        rechazo(e.reclamarBeneficios.ejecutar(ComandoReclamarBeneficios(e.idPartida, d("3.00"), nueva()))) shouldBe
            MotivoRechazo.BeneficiosCambiados(d("6.00"))
        e.fondos() shouldBe antes

        val mismo = ComandoReclamarBeneficios(e.idPartida, d("6.00"), "reclamo-1")
        exito(e.reclamarBeneficios.ejecutar(mismo))
        val repetido = e.reclamarBeneficios.ejecutar(mismo) as Resultado.Exito
        repetido.repetido shouldBe true
        e.fondos() shouldBe antes + d("6.00")
    }

    // ---- T5.10 idempotencia del ciclo ----

    @Test
    fun `T5_10 el mismo ciclo ejecutado dos veces produce un unico efecto`() {
        val sp = espacio()
        val a = animal(sp)
        fijarSatisfaccion(a, 1.0)
        val primero = exito(e.ejecutarCiclo.ejecutar(ComandoEjecutarCiclo(e.idPartida, 1)))
        val despues = instantanea()
        val segundo = e.ejecutarCiclo.ejecutar(ComandoEjecutarCiclo(e.idPartida, 1)) as Resultado.Exito
        segundo.repetido shouldBe true
        segundo.valor shouldBe primero
        instantanea() shouldBe despues
        e.contar("CicloSimulacion") shouldBe 1
        e.contar("BeneficioPendiente") shouldBe 1
        campoInt(a, "nivel_alimentacion") shouldBe 80
    }

    @Test
    fun `T5_10 un ciclo fuera de orden o con la simulacion pausada se rechaza sin cambios`() {
        val sp = espacio()
        animal(sp)
        val antes = instantanea()
        rechazo(e.ejecutarCiclo.ejecutar(ComandoEjecutarCiclo(e.idPartida, 5))) shouldBe MotivoRechazo.CicloFueraDeOrden(1)
        e.bd.escribir("UPDATE EstadoSimulacion SET estado = 'PAUSADO'")
        rechazo(ciclo()) shouldBe MotivoRechazo.SimulacionNoActiva
        e.bd.escribir("UPDATE EstadoSimulacion SET estado = 'ACTIVO'")
        e.bd.escribir("UPDATE Partida SET estado = 'PAUSADA'")
        rechazo(ciclo()) shouldBe MotivoRechazo.SimulacionNoActiva
        instantanea() shouldBe antes
    }

    // ---- T5.11 alertas críticas ----

    @Test
    fun `T5_11 una sola alerta por animal que se mantiene entre ciclos y desaparece al alimentar`() {
        val sp = espacio()
        val a = animal(sp, alimentacion = 0)
        exito(ciclo())
        alertas() shouldContainExactly listOf(a to "PENDIENTE")
        exito(ciclo())
        alertas() shouldContainExactly listOf(a to "PENDIENTE")
        exito(e.alimentarAnimal.ejecutar(ComandoAlimentarAnimal(e.idPartida, a, nueva())))
        alertas() shouldContainExactly listOf(a to "RESUELTA")
    }

    @Test
    fun `T5_11 si sigue enfermo la alerta continua con la causa restante`() {
        val sp = espacio()
        val a = animal(sp, "erizo", "Doble", alimentacion = 0, enfermo = true)
        exito(ciclo())
        exito(e.alimentarAnimal.ejecutar(ComandoAlimentarAnimal(e.idPartida, a, nueva())))
        alertas() shouldContainExactly listOf(a to "PENDIENTE")
        exito(e.tratarAnimal.ejecutar(ComandoTratarAnimal(e.idPartida, a, nueva())))
        alertas() shouldContainExactly listOf(a to "RESUELTA")
    }

    @Test
    fun `T5_11 la alerta se resuelve cuando el animal muere`() {
        val sp = espacio()
        val a = animal(sp, alimentacion = 0)
        repeat(3) { exito(ciclo()) }
        estadoVital(a) shouldBe "MUERTO"
        alertas() shouldContainExactly listOf(a to "RESUELTA")
    }

    // ---- T5.12 panel crítico ----

    @Test
    fun `T5_12 los ciclos restantes son 3 menos el mayor contador y se identifica por nombre`() {
        val sp = espacio()
        val hambre = animal(sp, "erizo", "Hambriento", alimentacion = 0)
        val doble = animal(sp, "erizo", "Doble", alimentacion = 0, enfermo = true)
        animal(sp, "erizo", "Sano")
        e.bd.escribir("UPDATE Animal SET ciclos_desnutricion = 1 WHERE id_animal = ?", hambre)
        e.bd.escribir("UPDATE Animal SET ciclos_desnutricion = 1, ciclos_enfermedad = 2 WHERE id_animal = ?", doble)

        val panel = e.consultarSeguimiento.ejecutar(e.idPartida)
        panel.map { it.animal.nombre } shouldContainExactly listOf("Doble", "Hambriento")
        panel[0].ciclosRestantes shouldBe 1
        panel[0].ciclosDesnutricion shouldBe 1
        panel[0].ciclosEnfermedad shouldBe 2
        panel[1].ciclosRestantes shouldBe 2
        panel.none { it.animal.nombre == "Sano" } shouldBe true
        // Consultar no modifica nada.
        val antes = instantanea()
        e.consultarSeguimiento.ejecutar(e.idPartida)
        instantanea() shouldBe antes
    }

    // ---- T5.13 cierre forzado a mitad de ciclo ----

    @Test
    fun `T5_13 un fallo a mitad del ciclo no deja ciclo parcial y se puede reprocesar`() {
        val sp = espacio()
        val a = animal(sp, alimentacion = 0)
        val b = animal(sp, nombre = "B")
        fijarSatisfaccion(a, 0.5)
        fijarSatisfaccion(b, 0.5)
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        val antes = instantanea()
        val falla = EjecutarCiclo(
            e.ejecutor, e.ciclos, e.animales, e.auditoria, e.recalcular, e.azar, e.reloj,
            misiones = { _, _, _ -> error("cierre forzado") },
        )
        shouldThrow<IllegalStateException> { falla.ejecutar(ComandoEjecutarCiclo(e.idPartida, 1)) }
        instantanea() shouldBe antes
        e.contar("CicloSimulacion") shouldBe 0
        e.contar("BeneficioPendiente") shouldBe 0
        e.auditoriaDe("CICLO_CONFIRMADO").map { it.first } shouldContainExactly listOf("REVERTIDO")

        // La recuperación reprocesa el mismo número de ciclo y deja un único efecto.
        exito(e.ejecutarCiclo.ejecutar(ComandoEjecutarCiclo(e.idPartida, 1)))
        ultimoCiclo() shouldBe 1
        e.contar("CicloSimulacion", "estado = 'CONFIRMADO'") shouldBe 1
        campoInt(b, "nivel_alimentacion") shouldBe 80
    }

    // ---- T5.14 temporizador ----

    @Test
    fun `T5_14 el remanente persiste y el temporizador continua donde corresponde sin sondeo`() =
        runTest {
            val sp = espacio()
            animal(sp)
            val despachador = StandardTestDispatcher(testScheduler)
            fun motor() = MotorCiclos(
                scope = backgroundScope,
                idPartida = e.idPartida,
                ciclos = e.ciclos,
                ejecutarCiclo = e.ejecutarCiclo,
                purga = e.purgarRetencion,
                monotonico = { testScheduler.currentTime },
                dispatcher = despachador,
                reloj = e.reloj,
            )

            val primero = motor()
            primero.iniciar()
            advanceTimeBy(250_000)
            runCurrent()
            primero.detener() // pasa a segundo plano a los 250 s
            e.bd.uno("SELECT remanente_temporizador FROM EstadoSimulacion") { it.entero(0) } shouldBe 350
            ultimoCiclo() shouldBe null

            val segundo = motor()
            segundo.iniciar()
            advanceTimeBy(349_000)
            runCurrent()
            ultimoCiclo() shouldBe null // sin ciclos antes de vencer
            advanceTimeBy(1_000)
            runCurrent()
            ultimoCiclo() shouldBe 1
            e.bd.uno("SELECT remanente_temporizador FROM EstadoSimulacion") { it.entero(0) } shouldBe 600

            // En reposo no hay más trabajo hasta el siguiente fin de ciclo: ni ciclos ni lecturas del motor.
            advanceTimeBy(599_000)
            runCurrent()
            ultimoCiclo() shouldBe 1
            advanceTimeBy(1_000)
            runCurrent()
            ultimoCiclo() shouldBe 2
            segundo.detener()
        }

    // ---- T5.15 rendimiento ----

    @Test
    fun `T5_15 un ciclo completo sobre la carga de referencia tarda menos de 1000 ms`() {
        e.datos.cargaReferencia(e.idPartida)!!
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        exito(ciclo()) // calentamiento de la JIT y de las sentencias
        var resultado: Resultado<*>? = null
        val duracion = measureTime { resultado = ciclo() }
        (resultado is Resultado.Exito) shouldBe true
        (duracion.inWholeMilliseconds < 1000) shouldBe true
        ultimoCiclo() shouldBe 2
    }

    // ---- T5.16 historial de avisos ----

    private fun aviso(codigo: String, tipo: String, severidad: String, estado: String, idEntidad: String, ciclo: Int, cierre: Int?) {
        e.bd.escribir(
            "INSERT INTO Notificacion (id_partida, tipo, severidad, codigo, entidad, id_entidad, numero_ciclo, ciclo_cierre, estado, fecha) " +
                "VALUES (?, ?, ?, ?, 'ANIMAL', ?, ?, ?, ?, 0)",
            e.idPartida, tipo, severidad, codigo, idEntidad, ciclo, cierre, estado,
        )
    }

    @Test
    fun `T5_16 el historial se filtra por severidad y listar no cambia ningun estado`() {
        val sp = espacio()
        val a = animal(sp, alimentacion = 0)
        exito(ciclo())
        aviso("PRESTIGIO_ASCENSO", "INFORMATIVA", "BAJA", "PENDIENTE", "1", 1, null)
        val antes = e.contar("Notificacion", "estado = 'PENDIENTE'")

        e.consultarAvisos.ejecutar(e.idPartida).size shouldBe 2
        e.consultarAvisos.ejecutar(e.idPartida, SeveridadAviso.ALTA).map { it.codigo } shouldContainExactly listOf("ALERTA_SALUD")
        e.consultarAvisos.ejecutar(e.idPartida, SeveridadAviso.BAJA).map { it.codigo } shouldContainExactly listOf("PRESTIGIO_ASCENSO")
        e.consultarAvisos.ejecutar(e.idPartida, SeveridadAviso.MEDIA) shouldBe emptyList()
        e.contar("Notificacion", "estado = 'PENDIENTE'") shouldBe antes
        e.consultarAvisos.ejecutar(e.idPartida, SeveridadAviso.ALTA).single().texto.contains("Pincho") shouldBe true
        e.consultarAvisos.ejecutar(e.idPartida, SeveridadAviso.ALTA).single().texto.contains(a) shouldBe false
    }

    @Test
    fun `T5_16 abrir el detalle marca como vista un aviso pendiente y solo ese`() {
        val sp = espacio()
        animal(sp, alimentacion = 0)
        exito(ciclo())
        val critico = e.consultarAvisos.ejecutar(e.idPartida).single()
        critico.estado shouldBe "PENDIENTE"
        val abierto = exito(e.abrirAviso.ejecutar(ComandoAbrirAviso(e.idPartida, critico.id, nueva())))
        abierto.aviso.estado shouldBe "VISTA"
        rechazo(e.abrirAviso.ejecutar(ComandoAbrirAviso(e.idPartida, 9999, nueva()))) shouldBe MotivoRechazo.AvisoInexistente
    }

    @Test
    fun `T5_16 la retencion acota los avisos cerrados y conserva los pendientes`() {
        for (i in 1..150) aviso("ALERTA_SALUD", "CRITICA", "ALTA", "RESUELTA", "x$i", i, i)
        aviso("ALERTA_SALUD", "CRITICA", "ALTA", "PENDIENTE", "vivo", 1, null)
        e.bd.escribir("UPDATE EstadoSimulacion SET numero_ciclo_ultimo = 200")
        val ventana = e.cfg.retencion.notificacionCerrada

        e.purgarRetencion.ejecutar(e.idPartida)

        // Fuera de la ventana de 72 ciclos (cierre < 128) y por encima del máximo de filas: solo quedan los cerrados recientes.
        val cerrados = e.contar("Notificacion", "estado = 'RESUELTA'")
        (cerrados <= ventana.maximoFilas) shouldBe true
        e.contar("Notificacion", "estado = 'RESUELTA' AND ciclo_cierre < ${200 - ventana.ventanaCiclos}") shouldBe 0
        e.contar("Notificacion", "estado = 'PENDIENTE'") shouldBe 1
    }

    // ---- Migración de la versión 1 a la 2 (fondos iniciales) ----

    @Test
    fun `migrar de la version 1 a la 2 suma 1000 monedas una sola vez`() {
        val antes = e.fondos()
        e.bd.migrarDe1a2()
        e.fondos() shouldBe antes + d("1000.00")
        e.contar("MovimientoEconomico", "origen = 'COMPENSACION_INICIAL' AND importe = 1000.0") shouldBe 1
        e.bd.uno("PRAGMA user_version") { it.entero(0) } shouldBe 2
    }

    @Test
    fun `una partida nueva empieza con 2000 monedas`() {
        e.fondos() shouldBe d("2000.00")
    }
}
