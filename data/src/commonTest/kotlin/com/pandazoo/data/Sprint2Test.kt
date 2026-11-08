package com.pandazoo.data

import com.pandazoo.application.caso.AdquirirInsumos
import com.pandazoo.application.caso.AlcanceRecalculo
import com.pandazoo.application.caso.ComandoAdquirirEspacio
import com.pandazoo.application.caso.ComandoAdquirirInsumos
import com.pandazoo.application.caso.ConsultarMonitorSatisfaccion
import com.pandazoo.application.puerto.AnimalActivo
import com.pandazoo.application.puerto.MonitorRepositorio
import com.pandazoo.data.depuracion.DatosDePrueba
import com.pandazoo.domain.modelo.Seccion
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.measureTime

/** Sprint 2: motor de satisfacción, persistencia de métricas y monitor (CU-17). */
class Sprint2Test {
    private lateinit var e: Entorno
    private val tol = 1e-12

    @BeforeTest
    fun preparar() {
        e = Entorno()
    }

    @AfterTest
    fun cerrar() = e.cerrar()

    private fun nombreHabitat(codigo: String) = e.cfg.catalogos.habitats.first { it.codigo == codigo }.nombre

    private val esmeril get() = nombreHabitat("campos_de_esmeril")
    private val templado get() = nombreHabitat("bosque_templado")

    private fun espacio(habitat: String, nombre: String) = e.datos.espacio(e.idPartida, e.habitat(habitat), nombre)

    private fun animal(espacio: String, especie: String, nombre: String, alimentacion: Int = 100, enfermo: Boolean = false) =
        e.datos.animal(e.idPartida, espacio, especie, nombre, alimentacion, enfermo)

    private fun satisfaccionDe(id: String): Double =
        e.bd.uno("SELECT satisfaccion_actual FROM Animal WHERE id_animal = ?", id) { it.decimal(0) }!!

    private fun metrica(nivel: String, columna: String? = null, id: String? = null): Double? =
        e.bd.uno(
            "SELECT valor FROM MetricaSatisfaccion WHERE nivel = ?" + (if (columna != null) " AND $columna = ?" else ""),
            *listOfNotNull(nivel, id).toTypedArray(),
        ) { it.decimal(0) }

    private fun foto(): List<String> =
        e.bd.leer("SELECT nivel, id_espacio, id_habitat, valor, fecha FROM MetricaSatisfaccion ORDER BY id_metrica") {
            "${it.texto(0)}|${it.textoONulo(1)}|${it.textoONulo(2)}|${it.decimal(3)}|${it.largo(4)}"
        } + e.bd.leer("SELECT id_animal, satisfaccion_actual FROM Animal ORDER BY id_animal") { "${it.texto(0)}|${it.decimal(1)}" }

    // ---- Persistencia de la regla (RF-15, BBDD 4.3) ----

    @Test
    fun `un leon solo en su bioma natural persiste 0_5 y las metricas de los tres niveles`() {
        val sp = espacio("campos_de_esmeril", "Sabana")
        val a = animal(sp, "leon", "Simba")
        e.recalcular.alConsolidar(e.idPartida)

        satisfaccionDe(a) shouldBe (0.5 plusOrMinus tol) // (0.5·1 + 0.5·0)·P_hab 1.0·M_crit 1
        metrica("ESPACIO", "id_espacio", sp) shouldBe (0.5 plusOrMinus tol)
        metrica("HABITAT", "id_habitat", e.habitat("campos_de_esmeril")) shouldBe (0.5 plusOrMinus tol)
        metrica("PARTIDA") shouldBe (0.5 plusOrMinus tol)
    }

    @Test
    fun `T2_4 P_hab modifica la satisfaccion pero no impide ubicar al animal`() {
        val natural = animal(espacio("campos_de_esmeril", "Sabana"), "leon", "Simba")
        val hostil = animal(espacio("bosque_templado", "Hielo"), "leon", "Scar") // leon-templado = 0.4, ubicado sin objeción
        e.recalcular.alConsolidar(e.idPartida)

        satisfaccionDe(natural) shouldBe (0.5 plusOrMinus tol)
        satisfaccionDe(hostil) shouldBe (0.2 plusOrMinus tol) // 0.5 · 0.4
        e.contar("Animal", "id_espacio IS NOT NULL") shouldBe 2
    }

    @Test
    fun `recalcular dos veces no crece el numero de filas de metricas`() {
        val sp = espacio("campos_de_esmeril", "Sabana")
        animal(sp, "leon", "Simba")
        e.recalcular.alConsolidar(e.idPartida)
        val filas = e.contar("MetricaSatisfaccion")
        repeat(3) { e.recalcular.alConsolidar(e.idPartida) }
        e.contar("MetricaSatisfaccion") shouldBe filas
        e.contar("MetricaSatisfaccion", "nivel = 'PARTIDA'") shouldBe 1
    }

    @Test
    fun `animales muertos o sin espacio no entran y el parque sin animales vale cero`() {
        val sp = espacio("campos_de_esmeril", "Sabana")
        val vivo = animal(sp, "leon", "Simba")
        val muerto = animal(sp, "leon", "Mufasa")
        e.bd.escribir("UPDATE Animal SET estado_vital = 'MUERTO', id_espacio = NULL WHERE id_animal = ?", muerto)
        e.recalcular.alConsolidar(e.idPartida)
        satisfaccionDe(muerto) shouldBe 0.0 // sin tocar
        metrica("ESPACIO", "id_espacio", sp) shouldBe (0.5 plusOrMinus tol) // solo el vivo
        e.bd.escribir("UPDATE Animal SET estado_vital = 'VENDIDO', id_espacio = NULL WHERE id_animal = ?", vivo)
        e.recalcular.alConsolidar(e.idPartida)
        e.contar("MetricaSatisfaccion", "nivel IN ('ESPACIO', 'HABITAT')") shouldBe 0
        metrica("PARTIDA") shouldBe 0.0
        e.contar("MetricaSatisfaccion", "nivel = 'PARTIDA'") shouldBe 1
    }

    @Test
    fun `la regla recalculada coincide con la persistida para una familia con pareja amigo y crias`() {
        val sp = espacio("campos_de_esmeril", "Sabana")
        val padre = animal(sp, "leon", "Padre")
        val madre = animal(sp, "leon", "Madre")
        val amigo = animal(sp, "cebra", "Amigo")
        val cria = e.datos.animal(e.idPartida, sp, "leon", "Cría", idPadre = padre, idMadre = madre)
        e.datos.parejas(e.idPartida, padre, madre)
        e.datos.amigos(e.idPartida, padre, amigo)
        e.recalcular.alConsolidar(e.idPartida)

        // La afinidad cuenta a todos los demás del espacio: familiares 0.8 (afinidad familiar) y cebra 0.1 (leon-cebra).
        // Padre: pareja .1 + amigo .1 + afinidad .6·(madre .8 + cebra .1 + cría .8)/3 = .34 → S_soc = .54; S = .5 + .27 = .77
        satisfaccionDe(padre) shouldBe (0.77 plusOrMinus tol)
        // Cría: padre .05 + madre .05 + afinidad .6·(padre .8 + madre .8 + cebra .1)/3 = .34 → S_soc = .44; S = .5 + .22 = .72
        satisfaccionDe(cria) shouldBe (0.72 plusOrMinus tol)
    }

    // ---- T2.7 ----

    @Test
    fun `T2_7 abrir pantallas y consultar no ejecuta el recalculo ni modifica nada`() {
        val sp = espacio("campos_de_esmeril", "Sabana")
        animal(sp, "leon", "Simba")
        e.recalcular.alConsolidar(e.idPartida)
        val invocaciones = e.recalcular.invocaciones
        val antes = foto()

        e.reloj.avanzar(3_600)
        repeat(5) {
            e.consultarMonitor.ejecutar(e.idPartida)
            e.consultarInventario.ejecutar(e.idPartida)
            e.consultarZoo.habitats(e.idPartida)
            e.consultarZoo.espacios(e.idPartida, e.habitat("campos_de_esmeril"))
            e.consultarTienda.recursos()
        }

        e.recalcular.invocaciones shouldBe invocaciones
        foto() shouldBe antes
        e.contar("AuditoriaEvento", "tipo LIKE '%SATISFACCION%'") shouldBe 0
    }

    @Test
    fun `T2_7 las compras de insumos y espacios de este sprint no ejecutan el recalculo`() {
        val antes = e.recalcular.invocaciones
        e.adquirirEspacio.ejecutar(ComandoAdquirirEspacio(e.idPartida, e.habitat("campos_de_esmeril"), "Nuevo", 8, "sol-1"))
        e.adquirirInsumos.ejecutar(ComandoAdquirirInsumos(e.idPartida, AdquirirInsumos.bambu(), 5, "sol-2"))
        e.recalcular.invocaciones shouldBe antes
    }

    // ---- T2.8 ----

    @Test
    fun `T2_8 tras un cambio en un espacio se recalculan sus animales y no los de otros espacios`() {
        val a = espacio("campos_de_esmeril", "Sabana")
        val b = espacio("campos_de_esmeril", "Llanura")
        val enA = animal(a, "leon", "Simba")
        val enB = animal(b, "leon", "Nala")
        e.recalcular.alConsolidar(e.idPartida)
        satisfaccionDe(enA) shouldBe (0.5 plusOrMinus tol)

        // Marca centinela en B: si el recálculo lo tocase, se perdería.
        e.bd.escribir("UPDATE Animal SET satisfaccion_actual = 0.123 WHERE id_animal = ?", enB)
        // Mutación confirmada en A (el animal pasa a alimentación 0).
        e.bd.escribir("UPDATE Animal SET nivel_alimentacion = 0 WHERE id_animal = ?", enA)
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Espacios(setOf(a)))

        satisfaccionDe(enA) shouldBe (0.0 plusOrMinus tol) // sBio 0 y sin social
        satisfaccionDe(enB) shouldBe 0.123 // intacto
        metrica("ESPACIO", "id_espacio", a) shouldBe (0.0 plusOrMinus tol)
        metrica("ESPACIO", "id_espacio", b) shouldBe (0.123 plusOrMinus tol) // media sobre lo persistido
        metrica("HABITAT", "id_habitat", e.habitat("campos_de_esmeril")) shouldBe (0.0615 plusOrMinus tol)
        metrica("PARTIDA") shouldBe (0.0615 plusOrMinus tol)
    }

    // ---- Atomicidad ----

    @Test
    fun `si la transaccion que contiene el recalculo se revierte no quedan satisfaccion ni metricas parciales`() {
        val sp = espacio("campos_de_esmeril", "Sabana")
        animal(sp, "leon", "Simba")
        val antes = foto()
        runCatching {
            e.bd.ejecutar {
                e.recalcular.alConsolidar(e.idPartida)
                error("fallo posterior")
            }
        }
        foto() shouldBe antes
    }

    // ---- T2.9 ----

    @Test
    fun `T2_9 el recalculo completo de la carga de referencia tarda menos de 1000 ms`() {
        val resumen = e.datos.cargaReferencia(e.idPartida)!!
        resumen.animales shouldBe 500
        resumen.espacios shouldBe 50
        e.contar("Animal", "estado_vital = 'VIVO'") shouldBe 500
        e.contar("Espacio") shouldBe 50

        e.recalcular.alConsolidar(e.idPartida) // calentamiento de la JIT y de las sentencias
        val duracion = measureTime { e.recalcular.alConsolidar(e.idPartida) }

        (duracion.inWholeMilliseconds.toInt()) shouldBeLessThan 1000
        e.contar("MetricaSatisfaccion", "nivel = 'ESPACIO'") shouldBe 50
        e.contar("MetricaSatisfaccion", "nivel = 'HABITAT'") shouldBe 5
        e.contar("MetricaSatisfaccion", "nivel = 'PARTIDA'") shouldBe 1
        e.contar("Animal", "satisfaccion_actual > 0 AND satisfaccion_actual <= 1") shouldBe 500
    }

    @Test
    fun `la carga de referencia no se mezcla con animales existentes`() {
        e.datos.cargaReferencia(e.idPartida)!!
        e.datos.cargaReferencia(e.idPartida) shouldBe null
        e.contar("Animal") shouldBe 500
    }

    // ---- CU-17 ----

    @Test
    fun `CU-17 muestra parque habitats espacios y animales con sus valores persistidos`() {
        val sp = espacio("campos_de_esmeril", "Sabana")
        animal(sp, "leon", "Simba")
        animal(sp, "leon", "Nala", alimentacion = 0)
        e.recalcular.alConsolidar(e.idPartida)

        val m = e.consultarMonitor.ejecutar(e.idPartida)
        // Afinidad entre leones no familiares (misma especie): 0.5, S_soc = 0.6·0.5 = 0.30.
        // Simba: (0.5·1 + 0.5·0.30)·1·1 = 0.65. Nala (alimentación 0): (0 + 0.5·0.30)·1·0.3 = 0.045.
        (m.parque as Seccion.Disponible).valor shouldBe ((0.65 + 0.045) / 2 plusOrMinus tol)
        val esmerilUi = (m.habitats as Seccion.Disponible).valor.first { it.nombre == esmeril }
        val espacio = (esmerilUi.espacios as Seccion.Disponible).valor.single()
        (espacio.valor as Seccion.Disponible).valor shouldBe ((0.65 + 0.045) / 2 plusOrMinus tol)
        (espacio.animales as Seccion.Disponible).valor.map { it.nombre } shouldBe listOf("Nala", "Simba")
        (espacio.animales as Seccion.Disponible).valor.first { it.nombre == "Nala" }.valor shouldBe (0.045 plusOrMinus tol)
    }

    @Test
    fun `CU-17 un habitat sin animales se muestra sin metrica y no como cero`() {
        val sp = espacio("campos_de_esmeril", "Sabana")
        animal(sp, "leon", "Simba")
        e.recalcular.alConsolidar(e.idPartida)
        val m = e.consultarMonitor.ejecutar(e.idPartida)
        val habitats = (m.habitats as Seccion.Disponible).valor
        habitats shouldHaveSize 5
        val conAnimales = habitats.first { it.nombre == esmeril }
        (conAnimales.valor as Seccion.Disponible).valor.shouldBeInstanceOf<Double>()
        val vacio = habitats.first { it.nombre != esmeril }
        (vacio.valor as Seccion.Disponible).valor shouldBe null
        val espacios = (conAnimales.espacios as Seccion.Disponible).valor
        espacios shouldHaveSize 1
        espacios[0].nombre shouldBe "Sabana"
        ((espacios[0].animales as Seccion.Disponible).valor.map { it.nombre to it.especie }) shouldBe listOf("Simba" to "León")
    }

    @Test
    fun `CU-17 2a sin metrica global persistida el valor no esta disponible y no es cero`() {
        e.bd.escribir("DELETE FROM MetricaSatisfaccion WHERE id_partida = ?", e.idPartida)
        val m = e.consultarMonitor.ejecutar(e.idPartida)
        m.parque.shouldBeInstanceOf<Seccion.NoDisponible>()
    }

    @Test
    fun `CU-17 1a sin partida informa de que no hay metricas`() {
        val m = e.consultarMonitor.ejecutar("no-existe")
        m.parque.shouldBeInstanceOf<Seccion.NoDisponible>()
        m.habitats.shouldBeInstanceOf<Seccion.NoDisponible>()
    }

    @Test
    fun `CU-17 2b un fallo en un nivel se aisla y el resto de metricas sigue disponible`() {
        val sp = espacio("campos_de_esmeril", "Sabana")
        animal(sp, "leon", "Simba")
        e.recalcular.alConsolidar(e.idPartida)
        val roto = object : MonitorRepositorio by e.monitor {
            override fun satisfaccionEspacios(idPartida: String): Map<String, Double> = error("lectura fallida")
        }
        val m = ConsultarMonitorSatisfaccion(roto).ejecutar(e.idPartida)

        (m.parque as Seccion.Disponible).valor shouldBe (0.5 plusOrMinus tol)
        val habitatEsmeril = (m.habitats as Seccion.Disponible).valor.first { it.nombre == esmeril }
        (habitatEsmeril.valor as Seccion.Disponible).valor shouldBe (0.5 plusOrMinus tol)
        val espacio = (habitatEsmeril.espacios as Seccion.Disponible).valor.single()
        espacio.valor.shouldBeInstanceOf<Seccion.NoDisponible>()
        (espacio.animales as Seccion.Disponible).valor shouldHaveSize 1
    }

    @Test
    fun `CU-17 4a los animales muertos o vendidos no aparecen en las metricas activas`() {
        val sp = espacio("campos_de_esmeril", "Sabana")
        animal(sp, "leon", "Simba")
        val muerto = animal(sp, "leon", "Mufasa")
        e.bd.escribir("UPDATE Animal SET estado_vital = 'MUERTO', id_espacio = NULL WHERE id_animal = ?", muerto)
        e.recalcular.alConsolidar(e.idPartida)
        val animales: List<AnimalActivo> = e.monitor.animales(e.idPartida)
        animales.map { it.nombre } shouldBe listOf("Simba")
    }

    @Test
    fun `CU-17 3a entidades con el mismo nombre en distintos habitats se distinguen por la jerarquia`() {
        val s1 = espacio("campos_de_esmeril", "Mirador")
        val s2 = espacio("bosque_templado", "Mirador")
        animal(s1, "leon", "Simba")
        animal(s2, "cebra", "Marty")
        e.recalcular.alConsolidar(e.idPartida)
        val habitats = (e.consultarMonitor.ejecutar(e.idPartida).habitats as Seccion.Disponible).valor
        val conEspacios = habitats.filter { (it.espacios as Seccion.Disponible).valor.isNotEmpty() }
        conEspacios.map { it.nombre } shouldBe listOf(templado, esmeril)
    }

    @Test
    fun `el generador solo existe como herramienta de datos y no cobra ni audita`() {
        val fondos = e.fondos()
        val auditorias = e.contar("AuditoriaEvento")
        e.datos.cargaReferencia(e.idPartida)
        e.fondos() shouldBe fondos
        e.contar("AuditoriaEvento") shouldBe auditorias
        DatosDePrueba.ESPACIOS shouldBe 50
    }
}
