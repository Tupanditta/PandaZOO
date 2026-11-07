package com.pandazoo.data

import com.pandazoo.application.caso.AlcanceRecalculo
import com.pandazoo.application.caso.ComandoAdquirirAnimal
import com.pandazoo.application.caso.ComandoReclamarRecompensa
import com.pandazoo.application.caso.ComandoVenderAnimal
import com.pandazoo.application.caso.FichaNueva
import com.pandazoo.application.caso.ReclamarRecompensa
import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.puerto.EstadoMision
import com.pandazoo.application.puerto.InstanciaMision
import com.pandazoo.application.puerto.MisionRepositorio
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.mision.EspecieElegible
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.measureTime

/** Sprint 9: misiones (CU-20), reclamación de recompensas (CU-15), unidad gratuita exclusiva (RF-38, RF-49). */
class Sprint9Test {
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

    private fun <T> exito(r: Resultado<T>): T = (r as Resultado.Exito<T>).valor

    private fun rechazo(r: Resultado<*>): MotivoRechazo = (r as Resultado.Rechazado).motivo

    private fun espacio(habitat: String = "bosque_templado", nombre: String = "Madriguera", capacidad: Int = 20) =
        e.datos.espacio(e.idPartida, e.habitat(habitat), nombre, capacidad)

    private fun animal(sp: String, especie: String = "erizo", nombre: String = "Pincho", padre: String? = null, madre: String? = null) =
        e.datos.animal(e.idPartida, sp, especie, nombre, idPadre = padre, idMadre = madre)

    private fun instancias() = e.misionesRepo.instancias(e.idPartida)

    private fun mision(tipo: String): InstanciaMision = instancias().first { it.idMision == tipo }

    private fun recalcular() = e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)

    private fun ficha(nombre: String = "Nuevo") =
        FichaNueva(nombre, e.catalogoFicha.habilidades.take(2).map { it.id }, e.catalogoFicha.rasgos.take(2).map { it.id })

    private fun comprar(sp: String, especie: String) =
        e.adquirirAnimal.ejecutar(ComandoAdquirirAnimal(e.idPartida, especie, sp, ficha(), nueva()))

    private fun vender(id: String) = e.venderAnimal.ejecutar(ComandoVenderAnimal(e.idPartida, id, nueva()))

    /** Edad en años completos más `dias` días del calendario 30×12; fija también una longevidad que no interfiera. */
    private fun edad(id: String, anios: Int, dias: Int = 0) {
        val total = anios * 360 + dias
        e.bd.escribir(
            "UPDATE Animal SET edad_anios = ?, edad_meses = ?, edad_dias = ?, edad_maxima_anios = 80 WHERE id_animal = ?",
            total / 360, (total % 360) / 30, total % 30, id,
        )
    }

    private fun reclamar(i: InstanciaMision) = e.reclamarRecompensa.ejecutar(ComandoReclamarRecompensa(e.idPartida, i.idInstancia))

    /** Deja una misión como cumplida sin pasar por su evento (para probar la reclamación aislada). */
    private fun cumplir(tipo: String) {
        val i = mision(tipo)
        e.misionesRepo.fijarProgreso(e.idPartida, i.idInstancia, ReglasObjetivo.de(i), EstadoMision.CUMPLIDA)
    }

    private object ReglasObjetivo {
        fun de(i: InstanciaMision) = com.pandazoo.domain.mision.ReglasMision.objetivo(i.idMision, i.numericos)!!
    }

    private fun progreso(tipo: String) = mision(tipo).progreso

    private fun estado(tipo: String) = mision(tipo).estado

    // ---- T9.1 ----

    @Test
    fun `T9_1 hay exactamente una instancia vigente por tipo tras inicializar y tras reclamar`() {
        instancias().size shouldBe 12
        instancias().map { it.idMision }.toSet().size shouldBe 12
        e.contar("ProgresoMision") shouldBe 12
        cumplir("total_animales")
        exito(reclamar(mision("total_animales")))
        instancias().size shouldBe 12
        instancias().map { it.idMision }.toSet().size shouldBe 12
        e.contar("ProgresoMision", "id_mision = 'total_animales'") shouldBe 1
        instancias().all { it.estado != EstadoMision.AGOTADA } shouldBe true
    }

    // ---- T9.2 un caso por tipo ----

    @Test
    fun `T9_2 comprar especie cuenta solo la especie pedida y se cumple en el limite`() {
        val sp = espacio()
        exito(comprar(sp, "erizo")) // otra especie: no cuenta
        progreso("comprar_especie") shouldBe 0.0
        exito(comprar(sp, "capibara"))
        progreso("comprar_especie") shouldBe 1.0
        estado("comprar_especie") shouldBe EstadoMision.PENDIENTE
        exito(comprar(sp, "capibara"))
        progreso("comprar_especie") shouldBe 2.0
        estado("comprar_especie") shouldBe EstadoMision.CUMPLIDA
    }

    @Test
    fun `T9_2 crecer especie cuenta al cruzar la edad en un ciclo posterior y no a quien ya la superaba`() {
        val sp = espacio()
        val antes = animal(sp, "nutria", "Antes")
        val justo = animal(sp, "nutria", "Justo")
        val ya = animal(sp, "nutria", "Ya")
        edad(antes, 23, 348) // 23 años y 348 días: tras +6 días aún 23 años completos
        edad(justo, 23, 354) // +6 días = 24 años exactos
        edad(ya, 24, 0) // ya la superaba al crearse la instancia
        e.ejecutarCiclo.siguiente(e.idPartida)
        progreso("crecer_especie") shouldBe 1.0
        estado("crecer_especie") shouldBe EstadoMision.CUMPLIDA
    }

    @Test
    fun `T9_2 crecer especie no cumple si nadie cruza`() {
        val sp = espacio()
        val a = animal(sp, "nutria", "Antes")
        edad(a, 23, 348)
        e.ejecutarCiclo.siguiente(e.idPartida)
        progreso("crecer_especie") shouldBe 0.0
        estado("crecer_especie") shouldBe EstadoMision.PENDIENTE
    }

    @Test
    fun `T9_2 total de animales es dinamico y solo cuenta vivos alojados`() {
        val sp = espacio()
        repeat(4) { animal(sp, nombre = "A$it") }
        val vendido = animal(sp, nombre = "Vendido")
        e.animales.retirar(e.idPartida, vendido, "VENDIDO")
        recalcular()
        progreso("total_animales") shouldBe 4.0
        estado("total_animales") shouldBe EstadoMision.PENDIENTE
        animal(sp, nombre = "Quinto")
        recalcular()
        progreso("total_animales") shouldBe 5.0
        estado("total_animales") shouldBe EstadoMision.CUMPLIDA
    }

    @Test
    fun `T9_2 animales en un habitat cuenta los de todos sus espacios`() {
        val a = espacio("bosque_templado", "A")
        val b = espacio("bosque_templado", "B")
        val otro = espacio("humedal_agua_dulce", "C")
        animal(a); animal(b); animal(otro)
        recalcular()
        progreso("animales_habitat") shouldBe 2.0
        estado("animales_habitat") shouldBe EstadoMision.PENDIENTE
        animal(b, nombre = "Otro")
        recalcular()
        progreso("animales_habitat") shouldBe 3.0
        estado("animales_habitat") shouldBe EstadoMision.CUMPLIDA
    }

    @Test
    fun `T9_2 espacios ocupados exige el minimo de animales por espacio`() {
        val a = espacio("humedal_agua_dulce", "A")
        val b = espacio("humedal_agua_dulce", "B")
        animal(a); animal(b)
        recalcular()
        progreso("espacios_ocupados_habitat") shouldBe 0.0 // mínimo 2 por espacio
        animal(a, nombre = "Segundo")
        recalcular()
        progreso("espacios_ocupados_habitat") shouldBe 1.0
        estado("espacios_ocupados_habitat") shouldBe EstadoMision.CUMPLIDA
    }

    @Test
    fun `T9_2 subir de prestigio compara el nivel actual con el requerido`() {
        e.seguimientoMisiones.evaluarDinamicas(e.idPartida)
        estado("subir_prestigio") shouldBe EstadoMision.PENDIENTE
        e.bd.escribir("UPDATE Partida SET prestigio_actual = 1")
        e.seguimientoMisiones.evaluarDinamicas(e.idPartida)
        progreso("subir_prestigio") shouldBe 1.0
        estado("subir_prestigio") shouldBe EstadoMision.CUMPLIDA
    }

    @Test
    fun `T9_2 vender especie y edad exige la especie y los anios completos`() {
        val sp = espacio()
        val joven = animal(sp, "erizo", "Joven")
        val otraEspecie = animal(sp, "nutria", "Otra")
        val justo = animal(sp, "erizo", "Justo")
        edad(joven, 21, 359)
        edad(otraEspecie, 30)
        edad(justo, 22)
        exito(vender(joven))
        exito(vender(otraEspecie))
        progreso("vender_especie_edad") shouldBe 0.0
        exito(vender(justo))
        progreso("vender_especie_edad") shouldBe 1.0
        estado("vender_especie_edad") shouldBe EstadoMision.CUMPLIDA
    }

    @Test
    fun `T9_2 vender por edad ignora la especie`() {
        val sp = espacio()
        val joven = animal(sp, "erizo", "Joven")
        val mayor = animal(sp, "nutria", "Mayor")
        edad(joven, 21)
        edad(mayor, 22)
        exito(vender(joven))
        estado("vender_edad") shouldBe EstadoMision.PENDIENTE
        exito(vender(mayor))
        estado("vender_edad") shouldBe EstadoMision.CUMPLIDA
        progreso("vender_edad") shouldBe 1.0
    }

    private fun familia(sp: String, especie: String, hijos: Int): List<String> {
        val padre = animal(sp, especie, "Padre")
        val madre = animal(sp, especie, "Madre")
        return List(hijos) { animal(sp, especie, "Hijo$it", padre, madre) }
    }

    @Test
    fun `T9_2 familia necesita padre madre y el minimo de hermanos vivos`() {
        val sp = espacio()
        familia(sp, "erizo", 1) // un solo hijo: sin hermanos
        recalcular()
        progreso("familia") shouldBe 0.0
        estado("familia") shouldBe EstadoMision.PENDIENTE
        val padre = e.bd.uno("SELECT id_padre FROM Animal WHERE nombre = 'Hijo0'") { it.texto(0) }!!
        val madre = e.bd.uno("SELECT id_madre FROM Animal WHERE nombre = 'Hijo0'") { it.texto(0) }!!
        animal(sp, "erizo", "Hijo1", padre, madre)
        recalcular()
        progreso("familia") shouldBe 1.0
        estado("familia") shouldBe EstadoMision.CUMPLIDA
    }

    @Test
    fun `T9_2 familia exige padre y madre vivos y alojados`() {
        val sp = espacio()
        val hijos = familia(sp, "erizo", 2)
        val padre = e.bd.uno("SELECT id_padre FROM Animal WHERE id_animal = ?", hijos[0]) { it.texto(0) }!!
        e.animales.retirar(e.idPartida, padre, "MUERTO")
        recalcular()
        progreso("familia") shouldBe 0.0
        estado("familia") shouldBe EstadoMision.PENDIENTE
    }

    @Test
    fun `T9_2 familia de una especie se limita a esa especie`() {
        val sp = espacio()
        familia(sp, "erizo", 3)
        recalcular()
        estado("familia") shouldBe EstadoMision.CUMPLIDA
        progreso("familia_especie") shouldBe 0.0
        estado("familia_especie") shouldBe EstadoMision.PENDIENTE
        familia(sp, "zarigueya", 2)
        recalcular()
        progreso("familia_especie") shouldBe 1.0
        estado("familia_especie") shouldBe EstadoMision.CUMPLIDA
    }

    @Test
    fun `T9_2 satisfaccion minima compara la mayor satisfaccion individual con el umbral`() {
        val sp = espacio()
        val a = animal(sp, "erizo", "A")
        e.bd.escribir("UPDATE Animal SET satisfaccion_actual = 0.59 WHERE id_animal = ?", a)
        e.seguimientoMisiones.evaluarDinamicas(e.idPartida)
        estado("satisfaccion_minima") shouldBe EstadoMision.PENDIENTE
        progreso("satisfaccion_minima") shouldBe 0.59
        e.bd.escribir("UPDATE Animal SET satisfaccion_actual = 0.6 WHERE id_animal = ?", a)
        e.seguimientoMisiones.evaluarDinamicas(e.idPartida)
        estado("satisfaccion_minima") shouldBe EstadoMision.CUMPLIDA
    }

    @Test
    fun `T9_2 satisfaccion minima por especie solo mira esa especie`() {
        val sp = espacio()
        val erizo = animal(sp, "erizo", "E")
        val tortuga = animal(sp, "tortuga", "T")
        e.bd.escribir("UPDATE Animal SET satisfaccion_actual = 0.9 WHERE id_animal = ?", erizo)
        e.bd.escribir("UPDATE Animal SET satisfaccion_actual = 0.5 WHERE id_animal = ?", tortuga)
        e.seguimientoMisiones.evaluarDinamicas(e.idPartida)
        estado("satisfaccion_minima_especie") shouldBe EstadoMision.PENDIENTE
        progreso("satisfaccion_minima_especie") shouldBe 0.5
        e.bd.escribir("UPDATE Animal SET satisfaccion_actual = 0.6 WHERE id_animal = ?", tortuga)
        e.seguimientoMisiones.evaluarDinamicas(e.idPartida)
        estado("satisfaccion_minima_especie") shouldBe EstadoMision.CUMPLIDA
    }

    // ---- T9.3 ----

    @Test
    fun `T9_3 el progreso acumulativo no baja al vender pero el dinamico si`() {
        val sp = espacio()
        val capibara = exito(comprar(sp, "capibara")).idAnimal
        repeat(3) { animal(sp, nombre = "A$it") }
        recalcular()
        progreso("comprar_especie") shouldBe 1.0
        progreso("total_animales") shouldBe 4.0
        exito(vender(capibara))
        progreso("comprar_especie") shouldBe 1.0 // acumulativo
        progreso("total_animales") shouldBe 3.0 // dinámico
        // También al morir un animal.
        val otro = animal(sp, nombre = "B")
        recalcular()
        progreso("total_animales") shouldBe 4.0
        e.animales.retirar(e.idPartida, otro, "MUERTO")
        recalcular()
        progreso("total_animales") shouldBe 3.0
    }

    // ---- T9.4 ----

    @Test
    fun `T9_4 reclamar progresa los parametros numericos y mantiene los categoricos`() {
        val sp = espacio()
        exito(comprar(sp, "capibara"))
        exito(comprar(sp, "capibara"))
        val antes = mision("comprar_especie")
        exito(reclamar(antes))
        val despues = mision("comprar_especie")
        despues.idInstancia shouldBe "${e.idPartida}/comprar_especie/2"
        despues.numericos["cantidad_requerida"] shouldBe 3.0
        despues.textos["id_especie"] shouldBe "capibara"
        despues.progreso shouldBe 0.0
        despues.estado shouldBe EstadoMision.PENDIENTE
    }

    @Test
    fun `T9_4 la progresion de satisfaccion se redondea al centesimo`() {
        cumplir("satisfaccion_minima")
        exito(reclamar(mision("satisfaccion_minima")))
        mision("satisfaccion_minima").numericos["satisfaccion_minima"] shouldBe 0.65
    }

    @Test
    fun `T9_4 cada parametro usa su propia operacion y constante`() {
        cumplir("crecer_especie")
        exito(reclamar(mision("crecer_especie")))
        val m = mision("crecer_especie")
        m.numericos["cantidad_animales"] shouldBe 2.0 // sumar 1
        m.numericos["edad_requerida"] shouldBe 28.0 // sumar 4
        m.textos["id_especie"] shouldBe "nutria"
    }

    @Test
    fun `T9_4 si un parametro supera su maximo el tipo se agota y no vuelve a generarse`() {
        val i = mision("comprar_especie")
        e.bd.escribir(
            "UPDATE ParametroProgresoMision SET valor_numero = 50 WHERE id_instancia_mision = ? AND nombre_parametro = 'cantidad_requerida'",
            i.idInstancia,
        )
        e.misionesRepo.fijarProgreso(e.idPartida, i.idInstancia, 50.0, EstadoMision.CUMPLIDA)
        val r = exito(reclamar(mision("comprar_especie")))
        r.agotada shouldBe true
        estado("comprar_especie") shouldBe EstadoMision.AGOTADA
        e.consultarMisiones.ejecutar(e.idPartida).none { it.idInstancia == i.idInstancia } shouldBe true
        e.consultarMisiones.ejecutar(e.idPartida).size shouldBe 11
        e.contar("ProgresoMision", "id_mision = 'comprar_especie'") shouldBe 1
    }

    @Test
    fun `T9_4 una instancia dinamica nueva puede quedar cumplida al instante`() {
        val sp = espacio()
        repeat(10) { animal(sp, nombre = "A$it") }
        recalcular()
        estado("total_animales") shouldBe EstadoMision.CUMPLIDA // objetivo 5
        exito(reclamar(mision("total_animales")))
        mision("total_animales").numericos["numero_animales"] shouldBe 10.0
        estado("total_animales") shouldBe EstadoMision.CUMPLIDA // 10 animales ya cumplen el nuevo objetivo
        progreso("total_animales") shouldBe 10.0
    }

    // ---- T9.5 ----

    @Test
    fun `T9_5 reclamar dos veces la misma mision no acredita dos veces`() {
        cumplir("total_animales")
        val i = mision("total_animales")
        val antes = e.fondos()
        val primera = reclamar(i)
        exito(primera)
        e.fondos() shouldBe antes + d("300.00")
        val segunda = reclamar(i)
        (segunda as Resultado.Exito).repetido shouldBe true
        e.fondos() shouldBe antes + d("300.00")
        e.contar("RecompensaMision") shouldBe 1
        e.auditoriaDe("RECOMPENSA_RECLAMADA").map { it.first } shouldBe listOf("CONFIRMADO")
    }

    @Test
    fun `T9_5 una misma instancia ya sustituida se rechaza si se pierde la clave de idempotencia`() {
        cumplir("total_animales")
        val i = mision("total_animales")
        exito(reclamar(i))
        e.bd.escribir("UPDATE AuditoriaEvento SET clave_idempotencia = NULL WHERE tipo = 'RECOMPENSA_RECLAMADA'")
        rechazo(reclamar(i)) shouldBe MotivoRechazo.MisionInexistente
    }

    @Test
    fun `T9_5 una mision no cumplida no se puede reclamar y no cambia nada`() {
        val antes = e.fondos()
        val i = mision("total_animales")
        rechazo(reclamar(i)) shouldBe MotivoRechazo.MisionNoCumplida
        e.fondos() shouldBe antes
        e.contar("RecompensaMision") shouldBe 0
        estado("total_animales") shouldBe EstadoMision.PENDIENTE
        rechazo(e.reclamarRecompensa.ejecutar(ComandoReclamarRecompensa(e.idPartida, "inexistente"))) shouldBe MotivoRechazo.MisionInexistente
    }

    // ---- recompensas de cada tipo ----

    @Test
    fun `T9_5 las recompensas de fondos alimento e insumo se acreditan una vez`() {
        val bambu = e.inventario.existencias(e.idPartida).firstOrNull { it.recurso.codigo == "bambu" }?.cantidad ?: 0
        val medicos = e.inventario.existencias(e.idPartida).firstOrNull { it.recurso.codigo == "insumo_medico" }?.cantidad ?: 0
        cumplir("crecer_especie") // 30 kg de bambú
        cumplir("animales_habitat") // 5 insumos médicos
        cumplir("espacios_ocupados_habitat") // 400.00
        val fondos = e.fondos()
        val r1 = exito(reclamar(mision("crecer_especie")))
        val r2 = exito(reclamar(mision("animales_habitat")))
        val r3 = exito(reclamar(mision("espacios_ocupados_habitat")))
        r1.nombreItem shouldBe e.misionesRepo.nombreRecurso("bambu")
        r2.nombreItem shouldBe e.misionesRepo.nombreRecurso("insumo_medico")
        e.inventario.existencias(e.idPartida).first { it.recurso.codigo == "bambu" }.cantidad shouldBe bambu + 30
        e.inventario.existencias(e.idPartida).first { it.recurso.codigo == "insumo_medico" }.cantidad shouldBe medicos + 5
        e.fondos() shouldBe fondos + d("400.00")
        r3.cantidad shouldBe 400.0
        e.auditoriaDe("RECOMPENSA_RECLAMADA").size shouldBe 3
        e.contar("MovimientoEconomico", "tipo = 'RECOMPENSA'") shouldBe 1
    }

    // ---- T9.6 ----

    /** Repositorio que falla al crear la instancia siguiente: simula una escritura fallida a mitad de la reclamación. */
    private class FallaAlSustituir(private val d: MisionRepositorio) : MisionRepositorio by d {
        override fun sustituirInstancia(
            idPartida: String,
            anterior: InstanciaMision,
            nuevaId: String,
            numericos: Map<String, Double>,
        ) = error("fallo de escritura simulado")
    }

    @Test
    fun `T9_6 la reclamacion es atomica credito retirada y nueva instancia ocurren juntos o ninguno`() {
        cumplir("total_animales")
        val i = mision("total_animales")
        val antes = e.fondos()
        val roto = ReclamarRecompensa(
            e.ejecutor, FallaAlSustituir(e.misionesRepo), e.economia, e.inventario, e.ciclos, e.seguimientoMisiones, e.azar, e.reloj,
            listOf(EspecieElegible("panda", 100)),
        )
        shouldThrow<IllegalStateException> { roto.ejecutar(ComandoReclamarRecompensa(e.idPartida, i.idInstancia)) }
        e.fondos() shouldBe antes
        e.contar("RecompensaMision") shouldBe 0
        e.contar("MovimientoEconomico", "tipo = 'RECOMPENSA'") shouldBe 0
        mision("total_animales").idInstancia shouldBe i.idInstancia
        estado("total_animales") shouldBe EstadoMision.CUMPLIDA
        e.auditoriaDe("RECOMPENSA_RECLAMADA").map { it.first } shouldBe listOf("REVERTIDO")
        // Se puede reintentar: la misión sigue cumplida y reclamable.
        exito(reclamar(mision("total_animales")))
        e.fondos() shouldBe antes + d("300.00")
    }

    // ---- T9.7 ----

    @Test
    fun `T9_7 la recompensa de animal sortea y persiste la especie y los reintentos no vuelven a sortear`() {
        cumplir("subir_prestigio")
        val i = mision("subir_prestigio")
        val primera = exito(reclamar(i))
        // Con azar 0.5 el tramo acumulado 48..62 % corresponde a la quinta especie de la lista.
        e.bd.uno("SELECT id_especie_seleccionada FROM RecompensaMision") { it.texto(0) } shouldBe "alce_leucistico"
        primera.nombreEspecie shouldBe e.misionesRepo.nombreEspecie("alce_leucistico")
        val repetida = reclamar(i)
        (repetida as Resultado.Exito).repetido shouldBe true
        repetida.valor shouldBe primera
        e.contar("RecompensaMision") shouldBe 1
    }

    @Test
    fun `T9_7 se permite repetir especie en recompensas sucesivas`() {
        repeat(2) {
            cumplir("subir_prestigio")
            exito(reclamar(mision("subir_prestigio")))
        }
        e.contar("RecompensaMision", "id_especie_seleccionada = 'alce_leucistico'") shouldBe 2
        e.contar("RecompensaMision", "unidad_pendiente = 1") shouldBe 2
        mision("subir_prestigio").numericos["nivel_prestigio_requerido"] shouldBe 3.0
    }

    // ---- T9.8 ----

    private fun concederAlce() {
        cumplir("subir_prestigio")
        exito(reclamar(mision("subir_prestigio")))
    }

    @Test
    fun `T9_8 la unidad exclusiva aparece gratis en la tienda y se compra una sola vez`() {
        val sp = espacio()
        val fondos = e.fondos()
        e.consultarTiendaAnimales.ejecutar(e.idPartida).first { it.especie.id == "alce_leucistico" }.let {
            it.gratuita shouldBe false
            it.asequible shouldBe false
        }
        rechazo(comprar(sp, "alce_leucistico")) shouldBe MotivoRechazo.EspecieExclusiva

        concederAlce()
        e.consultarTiendaAnimales.ejecutar(e.idPartida).first { it.especie.id == "alce_leucistico" }.let {
            it.gratuita shouldBe true
            it.asequible shouldBe true
        }
        e.contar("Notificacion", "codigo = 'ESPECIE_DESBLOQUEADA' AND id_entidad = 'alce_leucistico'") shouldBe 1

        val compra = exito(comprar(sp, "alce_leucistico"))
        compra.coste shouldBe Dinero.CERO
        e.fondos() shouldBe fondos
        e.contar("RecompensaMision", "unidad_pendiente = 1") shouldBe 0
        e.consultarTiendaAnimales.ejecutar(e.idPartida).first { it.especie.id == "alce_leucistico" }.gratuita shouldBe false
        rechazo(comprar(sp, "alce_leucistico")) shouldBe MotivoRechazo.EspecieExclusiva
        // Otra exclusiva sin unidad concedida sigue sin poder comprarse.
        rechazo(comprar(sp, "panda")) shouldBe MotivoRechazo.EspecieExclusiva
    }

    @Test
    fun `T9_8 la compra gratuita sigue el flujo normal y valida el espacio`() {
        concederAlce()
        val lleno = espacio(nombre = "Lleno", capacidad = 1)
        animal(lleno)
        rechazo(comprar(lleno, "alce_leucistico")) shouldBe MotivoRechazo.EspacioLleno
        e.contar("RecompensaMision", "unidad_pendiente = 1") shouldBe 1 // no se consume si la compra se rechaza
        rechazo(
            e.adquirirAnimal.ejecutar(ComandoAdquirirAnimal(e.idPartida, "alce_leucistico", espacio(nombre = "Otro"), FichaNueva("X", emptyList(), emptyList()), nueva())),
        ) shouldBe MotivoRechazo.FichaIncompleta
        e.contar("RecompensaMision", "unidad_pendiente = 1") shouldBe 1
    }

    @Test
    fun `T9_8 la unidad no caduca y no se purga mientras este pendiente`() {
        concederAlce()
        e.bd.escribir("UPDATE EstadoSimulacion SET numero_ciclo_ultimo = 1000")
        e.purgarRetencion.ejecutar(e.idPartida)
        e.contar("RecompensaMision") shouldBe 1
        val sp = espacio()
        exito(comprar(sp, "alce_leucistico"))
        e.purgarRetencion.ejecutar(e.idPartida)
        e.contar("RecompensaMision") shouldBe 0 // ya cerrada y fuera de la ventana de retención
    }

    @Test
    fun `T9_8 comprar la unidad gratuita cuenta para la mision de compra de esa especie`() {
        e.bd.escribir(
            "UPDATE ParametroProgresoMision SET valor_texto = 'alce_leucistico' WHERE nombre_parametro = 'id_especie' AND id_mision = 'comprar_especie'",
        )
        concederAlce()
        exito(comprar(espacio(), "alce_leucistico"))
        progreso("comprar_especie") shouldBe 1.0
    }

    // ---- T9.9 ----

    @Test
    fun `T9_9 una mision cumplida permanece visible y reclamable aunque el valor dinamico baje`() {
        val sp = espacio()
        val ids = List(5) { animal(sp, nombre = "A$it") }
        recalcular()
        estado("total_animales") shouldBe EstadoMision.CUMPLIDA
        ids.drop(1).forEach { e.animales.retirar(e.idPartida, it, "MUERTO") }
        recalcular()
        e.ejecutarCiclo.siguiente(e.idPartida)
        estado("total_animales") shouldBe EstadoMision.CUMPLIDA
        val vista = e.consultarMisiones.ejecutar(e.idPartida).first { it.descripcion.startsWith("Ten 5 animales en el zoo") }
        vista.reclamable shouldBe true
        vista.cumplida shouldBe true
        exito(reclamar(mision("total_animales")))
    }

    // ---- CU-20 ----

    @Test
    fun `CU_20 la consulta muestra nombres y no identificadores ni modifica nada`() {
        val sp = espacio()
        exito(comprar(sp, "capibara"))
        val antes = listOf(e.contar("AuditoriaEvento"), e.recalcular.invocaciones, e.contar("ProgresoMision"), progreso("comprar_especie"))
        val vistas = e.consultarMisiones.ejecutar(e.idPartida)
        vistas.size shouldBe 12
        vistas.forEach { v ->
            for (tecnico in listOf("capibara", "nutria", "bosque_templado", "humedal_agua_dulce", "bambu", "insumo_medico", "id_", e.idPartida)) {
                v.descripcion shouldNotContain tecnico
                v.progresoTexto shouldNotContain tecnico
            }
        }
        vistas.first { it.descripcion.startsWith("Compra") }.progresoTexto shouldBe "1 / 2"
        vistas.first { it.descripcion.contains("satisfacción") && !it.descripcion.contains("ejemplar") }.progresoTexto.endsWith("% / 60 %") shouldBe true
        listOf(e.contar("AuditoriaEvento"), e.recalcular.invocaciones, e.contar("ProgresoMision"), progreso("comprar_especie")) shouldBe antes
    }

    // ---- ciclo ----

    @Test
    fun `el ciclo evalua las misiones dinamicas tras las defunciones`() {
        val sp = espacio()
        repeat(5) { animal(sp, nombre = "A$it") }
        recalcular()
        estado("total_animales") shouldBe EstadoMision.CUMPLIDA
        val mortal = animal(sp, nombre = "Mortal")
        e.bd.escribir("UPDATE Animal SET edad_maxima_anios = 1, edad_anios = 1 WHERE id_animal = ?", mortal)
        e.ejecutarCiclo.siguiente(e.idPartida)
        e.bd.uno("SELECT estado_vital FROM Animal WHERE id_animal = ?", mortal) { it.texto(0) } shouldBe "MUERTO"
    }

    // ---- T9.10 ----

    @Test
    fun `T9_10 el recalculo de misiones sobre la carga de referencia cumple 150 ms`() {
        e.datos.cargaReferencia(e.idPartida)!!
        recalcular()
        e.seguimientoMisiones.evaluarDinamicas(e.idPartida) // calentamiento
        val duracion = measureTime { e.seguimientoMisiones.evaluarDinamicas(e.idPartida) }
        (duracion.inWholeMilliseconds < 150) shouldBe true
    }
}
