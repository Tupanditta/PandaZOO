package com.pandazoo.data

import com.pandazoo.application.caso.AlcanceRecalculo
import com.pandazoo.application.caso.ComandoEjecutarCiclo
import com.pandazoo.application.caso.ComandoVenderAnimal
import com.pandazoo.application.caso.ResumenDiferido
import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.puerto.valorVenta
import com.pandazoo.domain.animal.ReglasNivel
import com.pandazoo.domain.economia.Dinero
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.measureTime

/**
 * Sprint N: niveles de animal (RF-51), beneficio por animal (RF-15) y venta según nivel (RF-52).
 * TN.3, TN.4, TN.6, TN.7, TN.9, TN.13, TN.14 y TN.15 sobre una base de datos real en memoria.
 */
class SprintNTest {
    private lateinit var e: Entorno
    private var solicitud = 0
    private val t0 = 1_700_000_000L

    // Azar alto: nadie enferma salvo que el test lo pida.
    @BeforeTest
    fun preparar() {
        e = Entorno(valorAzar = 0.99)
    }

    @AfterTest
    fun cerrar() = e.cerrar()

    private fun nueva() = "sol-${++solicitud}"

    private fun d(v: Double) = Dinero.desdeDecimal(v)

    private fun <T> exito(r: Resultado<T>): T = (r as Resultado.Exito<T>).valor

    private fun rechazo(r: Resultado<*>): MotivoRechazo = (r as Resultado.Rechazado).motivo

    private var espacios = 0

    private fun espacio(capacidad: Int = 20) = e.datos.espacio(e.idPartida, e.habitat("bosque_templado"), "Madriguera ${++espacios}", capacidad)

    private fun animal(sp: String, nombre: String = "Pincho", especie: String = "erizo", alimentacion: Int = 100, enfermo: Boolean = false, nivel: Int = 1, ciclos: Int = 0) =
        e.datos.animal(e.idPartida, sp, especie, nombre, alimentacion, enfermo, nivel = nivel, ciclosNivel = ciclos)

    private fun ciclo() = e.ejecutarCiclo.siguiente(e.idPartida)

    private fun nivel(id: String) = e.bd.uno("SELECT nivel, ciclos_nivel FROM Animal WHERE id_animal = ?", id) { it.entero(0) to it.entero(1) }!!

    // La poda (BBDD 5.3) borra tras el ciclo a los animales inactivos sin descendencia: una fila ausente equivale a MUERTO.
    private fun vital(id: String) = e.bd.uno("SELECT estado_vital FROM Animal WHERE id_animal = ?", id) { it.texto(0) } ?: "MUERTO"

    private fun campo(id: String, col: String) = e.bd.uno("SELECT $col FROM Animal WHERE id_animal = ?", id) { it.entero(0) }!!

    private fun fijar(id: String, col: String, v: Int) = e.bd.escribir("UPDATE Animal SET $col = ? WHERE id_animal = ?", v, id)

    private fun fijarSatisfaccion(id: String, s: Double) = e.bd.escribir("UPDATE Animal SET satisfaccion_actual = ? WHERE id_animal = ?", s, id)

    /** Ciclo con alimentación completa: el animal llega al final con 80 %. */
    private fun cicloAlimentado(vararg animales: String) {
        animales.forEach { fijar(it, "nivel_alimentacion", 100) }
        exito(ciclo())
    }

    // ---- TN.3 subida de nivel ----

    @Test
    fun `TN_3 el nivel sube exactamente al ciclo 72 y no al 71`() {
        val a = animal(espacio())
        repeat(71) { cicloAlimentado(a) }
        nivel(a) shouldBe (1 to 71)
        cicloAlimentado(a)
        nivel(a) shouldBe (2 to 0)
        cicloAlimentado(a)
        nivel(a) shouldBe (2 to 1)
    }

    @Test
    fun `TN_3 no suma si enferma en el ciclo ni pierde lo acumulado`() {
        e.cerrar()
        e = Entorno(valorAzar = 0.0) // aleatorio 0: con satisfacción 0 todo sano enferma
        val a = animal(espacio(), ciclos = 10)
        exito(ciclo())
        campo(a, "ciclos_enfermedad") shouldBe 1
        nivel(a) shouldBe (1 to 10)
    }

    @Test
    fun `TN_3 no suma si termina con alimentacion 0 incluido 20 a 0 y retoma al alimentarse`() {
        val a = animal(espacio(), alimentacion = 20, ciclos = 10)
        exito(ciclo()) // 20 → 0: desnutrición 1, no suma
        campo(a, "nivel_alimentacion") shouldBe 0
        nivel(a) shouldBe (1 to 10)
        exito(ciclo()) // sigue en 0
        nivel(a) shouldBe (1 to 10)
        cicloAlimentado(a) // alimentado de nuevo: retoma desde su progreso
        nivel(a) shouldBe (1 to 11)
    }

    @Test
    fun `TN_3 un animal enfermo no sube y tras curarse retoma desde su progreso`() {
        val a = animal(espacio(), enfermo = true, ciclos = 5)
        cicloAlimentado(a)
        nivel(a) shouldBe (1 to 5)
        e.bd.escribir("UPDATE Animal SET estado_salud = 'SANO', ciclos_enfermedad = 0 WHERE id_animal = ?", a)
        cicloAlimentado(a)
        nivel(a) shouldBe (1 to 6)
    }

    @Test
    fun `TN_3 el animal que muere en el ciclo no suma`() {
        val a = animal(espacio(), alimentacion = 0, ciclos = 20)
        fijar(a, "ciclos_desnutricion", 2)
        exito(ciclo())
        vital(a) shouldBe "MUERTO"
        nivel(a) shouldBe (1 to 20)
    }

    @Test
    fun `TN_3 se detiene en el tope sin superarlo y no baja`() {
        val a = animal(espacio(), nivel = 14, ciclos = 71)
        cicloAlimentado(a)
        nivel(a) shouldBe (15 to 0)
        repeat(3) { cicloAlimentado(a) }
        nivel(a) shouldBe (15 to 0)
    }

    // ---- TN.4 propiedad ----

    @Test
    fun `TN_4 para cualquier secuencia de acciones el progreso avanza si y solo si el animal sigue sano y vivo`() {
        var total = 0
        for (semilla in 1..3) { // pocas semillas: en la web cada test tiene 2 s de límite
            val azar = Random(semilla)
            val a = animal(espacio(), "Animal $semilla", nivel = 12 + semilla % 3, ciclos = 60)
            var anterior = nivel(a)
            var ciclos = 0
            while (vital(a) == "VIVO" && ciclos < 120) {
                val enPeligro = campo(a, "ciclos_desnutricion") == 2 || campo(a, "ciclos_enfermedad") == 2
                when (if (enPeligro) 2 else azar.nextInt(10)) { // un animal a punto de morir se cura para seguir caminando
                    0 -> fijar(a, "nivel_alimentacion", 0)
                    1 -> e.bd.escribir("UPDATE Animal SET estado_salud = 'ENFERMO' WHERE id_animal = ?", a)
                    2, 3 -> {
                        e.bd.escribir("UPDATE Animal SET estado_salud = 'SANO', ciclos_enfermedad = 0 WHERE id_animal = ?", a)
                        if (enPeligro) fijar(a, "nivel_alimentacion", 100)
                    }
                    else -> fijar(a, "nivel_alimentacion", 100)
                }
                exito(ciclo())
                ciclos++
                val ahora = nivel(a)
                (ahora.first >= anterior.first) shouldBe true
                (ahora.first in 1..15) shouldBe true
                (ahora.second in 0 until ReglasNivel.CICLOS_POR_NIVEL) shouldBe true
                if (vital(a) == "VIVO") {
                    val sano = campo(a, "ciclos_desnutricion") == 0 && campo(a, "ciclos_enfermedad") == 0
                    when {
                        anterior.first == 15 -> ahora shouldBe (15 to 0)
                        sano -> (ahora != anterior) shouldBe true
                        else -> ahora shouldBe anterior
                    }
                } else {
                    ahora shouldBe anterior // el que muere en el ciclo no suma
                }
                anterior = ahora
            }
            total += ciclos
        }
        (total > 100) shouldBe true
    }

    // ---- TN.6 y TN.7 beneficio y vejez ----

    @Test
    fun `TN_6 el beneficio usa la satisfaccion individual y el nivel del animal`() {
        val sp = espacio()
        val bajo = animal(sp, "Bajo", nivel = 1)
        val alto = animal(sp, "Alto", nivel = 15)
        listOf(bajo, alto).forEach { fijarSatisfaccion(it, 0.5) }
        // Rango 1, base 2,40: 1,20 en el nivel 1 y 3,60 en el 15.
        exito(ciclo()).beneficio shouldBe d(4.8)
    }

    @Test
    fun `TN_6 el beneficio usa el nivel posterior a la etapa de nivel`() {
        val a = animal(espacio(), nivel = 1, ciclos = 71)
        fijarSatisfaccion(a, 1.0)
        // Sube al nivel 2 en este ciclo: M = 1 + 2 · 1 / 14 = 1,142857 → 2,4 · 1,142857 = 2,74.
        exito(ciclo()).beneficio shouldBe d(2.74)
    }

    @Test
    fun `TN_6 la satisfaccion global no interviene en el beneficio`() {
        val a = animal(espacio())
        fijarSatisfaccion(a, 0.5)
        e.bd.escribir("UPDATE MetricaSatisfaccion SET valor = 0.0 WHERE nivel = 'PARTIDA'")
        exito(ciclo()).beneficio shouldBe d(1.2)
    }

    @Test
    fun `TN_6 sin animales vivos no hay beneficio ni fila`() {
        espacio()
        exito(ciclo()).beneficio shouldBe Dinero.CERO
        e.contar("BeneficioPendiente") shouldBe 0
    }

    @Test
    fun `TN_7 un animal cuidado sobrevive a cientos de ciclos y solo muere por tres ciclos criticos`() {
        val a = animal(espacio())
        repeat(300) { cicloAlimentado(a) }
        vital(a) shouldBe "VIVO"
        nivel(a) shouldBe (5 to 12) // 300 = 4 · 72 + 12
        fijar(a, "nivel_alimentacion", 0)
        exito(ciclo())
        exito(ciclo())
        vital(a) shouldBe "VIVO"
        exito(ciclo())
        vital(a) shouldBe "MUERTO"
    }

    // ---- TN.9 recuperación offline ----

    private fun cerradaEn(remanente: Int, ausente: Long) {
        e.bd.escribir("UPDATE EstadoSimulacion SET instante_ultimo_ciclo = ?, remanente_temporizador = ?", t0, remanente)
        e.reloj.segundos = t0 + ausente
    }

    private fun reabrir(max: Int = e.cfg.retencion.maxCiclosOffline): ResumenDiferido = exito(e.procesarDiferido(max).ejecutar(e.idPartida))

    @Test
    fun `TN_9 N ciclos diferidos suman N ciclos sanos con los limites offline y un animal al 0 por ciento no sube`() {
        val sp = espacio()
        val alimentado = animal(sp, "Ok")
        val hambriento = animal(sp, "Hambre", alimentacion = 0)
        val enfermo = animal(sp, "Enfermo", enfermo = true)
        cerradaEn(remanente = 600, ausente = 600 + 9 * 600L) // diez ciclos
        reabrir().ciclosProcesados shouldBe 10
        nivel(alimentado) shouldBe (1 to 10) // el suelo del 40 % mantiene la alimentación por encima de 0
        nivel(hambriento) shouldBe (1 to 0)
        nivel(enfermo) shouldBe (1 to 0)
    }

    @Test
    fun `TN_9 el resultado queda acotado por el maximo de ciclos y por el tope y la repeticion es idempotente`() {
        val a = animal(espacio())
        cerradaEn(remanente = 600, ausente = 600 + 200 * 600L)
        val r = reabrir(max = 100)
        r.ciclosProcesados shouldBe 100
        r.ciclosDescartados shouldBe 101
        nivel(a) shouldBe (2 to 28)
        // Sin tiempo nuevo no hay ciclos nuevos.
        reabrir(max = 100).ciclosProcesados shouldBe 0
        nivel(a) shouldBe (2 to 28)

        val tope = animal(espacio(), "Cerca", nivel = 14, ciclos = 70)
        cerradaEn(remanente = 600, ausente = 600 + 9 * 600L)
        reabrir().ciclosProcesados shouldBe 10
        nivel(tope) shouldBe (15 to 0) // 70 + 10 = 80 ≥ 72: sube y se queda en el tope
    }

    @Test
    fun `TN_9 un animal que muere en la recuperacion no sube`() {
        val a = animal(espacio(), alimentacion = 0, ciclos = 5)
        fijar(a, "ciclos_desnutricion", 2)
        cerradaEn(remanente = 600, ausente = 600 + 4 * 600L)
        reabrir()
        vital(a) shouldBe "MUERTO"
        // Un animal muerto sin descendencia puede haberse podado (BBDD 5.3); si la fila sigue, su nivel no ha subido.
        e.bd.uno("SELECT nivel, ciclos_nivel FROM Animal WHERE id_animal = ?", a) { it.entero(0) to it.entero(1) }?.shouldBe(1 to 5)
    }

    // ---- TN.13 equilibrio económico ----

    @Test
    fun `TN_13 la compra de los rangos 1 a 4 se amortiza con satisfaccion 0_5 y 0_4`() {
        val t = e.cfg.tablas
        val precioBambu = t.costesAlimentos.first { it.idAlimento == "bambu" }.precio
        val resultados = (1..4).map { rango ->
            val r = t.rangos.first { it.id == rango }
            val coste = t.costesRangos.first { it.idRango == rango }.importe
            // Peor caso del rango: la especie que más bambú consume por ciclo (20 puntos = 2 incrementos de 10).
            val kilos = e.cfg.especies.filter { it.idRango == rango }
                .maxOf { s -> t.alimentosPorEspecie.first { it.idEspecie == s.id }.kilogramos }
            val gastoCiclo = 2 * kilos * precioBambu
            fun ciclosParaAmortizar(satisfaccion: Double): Int {
                var acumulado = 0.0
                var n = 0
                while (acumulado < coste) {
                    n++
                    val nivel = minOf(r.nivelMaximo, 1 + n / ReglasNivel.CICLOS_POR_NIVEL)
                    val beneficio = r.beneficioBase * satisfaccion * ReglasNivel.multiplicador(nivel, r.nivelMaximo, r.multiplicadorNivelMaximo)
                    acumulado += beneficio - gastoCiclo
                    check(n < 10_000) { "El rango $rango nunca se amortiza con S = $satisfaccion" }
                }
                return n
            }
            Triple(rango, ciclosParaAmortizar(0.5), ciclosParaAmortizar(0.4))
        }
        println("TN.13 ciclos de amortización (rango, S=0.5, S=0.4): $resultados")
        resultados.forEach { (rango, con05, con04) ->
            check(rango in 1..4)
            // Con los costes ×5 la compra tarda más en amortizarse (decisión de balance): solo se exige que lo haga y que
            // una satisfacción menor no la acorte.
            (con04 >= con05) shouldBe true
        }
    }

    // ---- TN.14 venta según nivel ----

    private fun especieDeRango(rango: Int) = e.cfg.especies.first { it.idRango == rango && it.categoria == "COMERCIAL" }.id

    @Test
    fun `TN_14 en el nivel de equilibrio la venta devuelve exactamente el coste de compra`() {
        for (rango in 1..4) {
            val coste = d(e.cfg.tablas.costesRangos.first { it.idRango == rango }.importe)
            val maximo = e.cfg.tablas.rangos.first { it.id == rango }.nivelMaximo
            val sp = espacio()
            val equilibrio = ReglasNivel.nivelEquilibrio(maximo)
            val a = animal(sp, "Eq$rango", especieDeRango(rango), nivel = equilibrio)
            val antes = e.fondos()
            val r = exito(e.venderAnimal.ejecutar(ComandoVenderAnimal(e.idPartida, a, nueva())))
            r.importe shouldBe coste
            e.fondos() shouldBe antes + coste
            // Un nivel por debajo se pierde y uno por encima se gana.
            val debajo = animal(sp, "Bajo$rango", especieDeRango(rango), nivel = equilibrio - 1)
            val encima = animal(sp, "Alto$rango", especieDeRango(rango), nivel = equilibrio + 1)
            (exito(e.venderAnimal.ejecutar(ComandoVenderAnimal(e.idPartida, debajo, nueva()))).importe < coste) shouldBe true
            (exito(e.venderAnimal.ejecutar(ComandoVenderAnimal(e.idPartida, encima, nueva()))).importe > coste) shouldBe true
        }
    }

    @Test
    fun `TN_14 valores de venta en el nivel 1 y en el tope por rango`() {
        val esperadosTope = listOf(300.0, 1400.0, 4500.0, 7875.0, 10000.0)
        val esperadosNivel1 = listOf(10.0, 40.0, 112.5, 175.0, 200.0)
        for (rango in 1..5) {
            val maximo = e.cfg.tablas.rangos.first { it.id == rango }.nivelMaximo
            val especie = e.cfg.especies.first { it.idRango == rango }.id
            val sp = espacio()
            val tope = animal(sp, "Tope$rango", especie, nivel = maximo)
            val uno = animal(sp, "Uno$rango", especie, nivel = 1)
            exito(e.venderAnimal.ejecutar(ComandoVenderAnimal(e.idPartida, tope, nueva()))).importe shouldBe d(esperadosTope[rango - 1])
            exito(e.venderAnimal.ejecutar(ComandoVenderAnimal(e.idPartida, uno, nueva()))).importe shouldBe d(esperadosNivel1[rango - 1])
        }
    }

    @Test
    fun `TN_14 la venta abona una sola vez registra el movimiento y el reintento es idempotente`() {
        val a = animal(espacio(), nivel = 10)
        val antes = e.fondos()
        val comando = ComandoVenderAnimal(e.idPartida, a, "venta-1")
        val primero = exito(e.venderAnimal.ejecutar(comando))
        primero.importe shouldBe d(200.0)
        val repetido = e.venderAnimal.ejecutar(comando) as Resultado.Exito
        repetido.repetido shouldBe true
        repetido.valor.importe shouldBe d(200.0)
        e.fondos() shouldBe antes + d(200.0)
        e.contar("MovimientoEconomico", "tipo = 'VENTA'") shouldBe 1
        e.contar("MovimientoEconomico", "tipo = 'VENTA' AND importe = 200.0") shouldBe 1
    }

    @Test
    fun `TN_14 se rechaza la venta si el importe confirmado ya no coincide sin cambiar nada`() {
        val a = animal(espacio(), nivel = 9, ciclos = 71)
        val visto = e.consultarAnimales.ficha(e.idPartida, a)!!.valorVenta() // importe mostrado al confirmar, en el nivel 9
        visto shouldBe d(178.89)
        cicloAlimentado(a) // el animal sube al nivel 10 y el importe cambia
        nivel(a) shouldBe (10 to 0)
        val antes = e.fondos()
        val motivo = rechazo(e.venderAnimal.ejecutar(ComandoVenderAnimal(e.idPartida, a, nueva(), visto)))
        motivo shouldBe MotivoRechazo.ImporteVentaCambiado(d(200.0))
        e.fondos() shouldBe antes
        vital(a) shouldBe "VIVO"
        // Con el importe actualizado se vende.
        exito(e.venderAnimal.ejecutar(ComandoVenderAnimal(e.idPartida, a, nueva(), d(200.0)))).importe shouldBe d(200.0)
    }

    @Test
    fun `TN_14 un animal enfermo o sin alimentacion sigue sin poder venderse`() {
        val sp = espacio()
        val enfermo = animal(sp, "Enfermo", enfermo = true, nivel = 10)
        val hambriento = animal(sp, "Hambre", alimentacion = 0, nivel = 10)
        val antes = e.fondos()
        rechazo(e.venderAnimal.ejecutar(ComandoVenderAnimal(e.idPartida, enfermo, nueva()))) shouldBe MotivoRechazo.AnimalEnfermo
        rechazo(e.venderAnimal.ejecutar(ComandoVenderAnimal(e.idPartida, hambriento, nueva()))) shouldBe MotivoRechazo.SinAlimentacion
        e.fondos() shouldBe antes
    }

    // ---- TN.15 rendimiento ----

    @Test
    fun `TN_15 un ciclo completo sobre la carga de referencia cumple 1000 ms`() {
        e.datos.cargaReferencia(e.idPartida)!!
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        exito(e.ejecutarCiclo.ejecutar(ComandoEjecutarCiclo(e.idPartida, 1))) // calentamiento
        val duracion = measureTime { exito(e.ejecutarCiclo.ejecutar(ComandoEjecutarCiclo(e.idPartida, 2))) }
        (duracion.inWholeMilliseconds < 1000) shouldBe true
    }
}
