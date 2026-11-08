package com.pandazoo.data

import com.pandazoo.application.caso.AlcanceRecalculo
import com.pandazoo.application.caso.ComandoAlimentarAnimal
import com.pandazoo.application.caso.ComandoAlimentarHabitat
import com.pandazoo.application.caso.ComandoAdquirirEspacio
import com.pandazoo.application.caso.ComandoDarDeBajaEspacio
import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.domain.animal.ReglasCuidado
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.modelo.Seccion
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Cobertura de las funciones añadidas después del Sprint N (ver `Otros/DecisionesImplementacion.md`): ración según nivel,
 * alimentación por hábitat, estadísticas del animal, panel de prestigio, espacio gratuito, tienda de ampliaciones, venta de
 * espacios y migraciones del esquema.
 */
class CoberturaEstadoActualTest {
    private lateinit var e: Entorno
    private var solicitud = 0

    @BeforeTest
    fun preparar() {
        e = Entorno()
    }

    @AfterTest
    fun cerrar() = e.cerrar()

    private fun nueva() = "sol-${++solicitud}"

    private fun d(texto: String) = Dinero.desdeDecimal(texto.toDouble())

    private fun espacio(nombre: String = "Madriguera", habitat: String = "bosque_templado", capacidad: Int = 10) =
        e.datos.espacio(e.idPartida, e.habitat(habitat), nombre, capacidad)

    private fun kg(especie: String) = e.cfg.tablas.alimentosPorEspecie.first { it.idEspecie == especie }.kilogramos

    private fun bambu() = e.bd.uno("SELECT cantidad FROM InventarioAlimento WHERE id_partida = ?", e.idPartida) { it.entero(0) }!!

    private fun fijarBambu(n: Int) = e.bd.escribir("UPDATE InventarioAlimento SET cantidad = ? WHERE id_partida = ?", n, e.idPartida)

    private fun alimentacion(id: String) = e.bd.uno("SELECT nivel_alimentacion FROM Animal WHERE id_animal = ?", id) { it.entero(0) }!!

    private fun rechazo(r: Resultado<*>): MotivoRechazo = (r as Resultado.Rechazado).motivo

    private fun <T> exito(r: Resultado<T>): T = (r as Resultado.Exito<T>).valor

    private fun fondos(importe: Double) =
        e.bd.escribir("UPDATE Partida SET fondos = ? WHERE id_partida = ?", Dinero.desdeDecimal(importe).paraBd(), e.idPartida)

    private fun alimentarHabitat(codigo: String, kilos: Int? = null, sol: String = nueva()) =
        e.alimentarHabitat.ejecutar(ComandoAlimentarHabitat(e.idPartida, e.habitat(codigo), kilos, sol))

    // ---------------------------------------------------------------- Ración según nivel (CU-13)

    @Test
    fun `la alimentacion individual consume la racion del nivel del animal`() {
        val sp = espacio()
        val base = kg("erizo")
        val a = e.datos.animal(e.idPartida, sp, "erizo", "Veterano", alimentacion = 50, nivel = 21)
        val esperado = ReglasCuidado.kilosPorRacion(base, 21)
        (esperado > base) shouldBe true

        val antes = bambu()
        val r = exito(e.alimentarAnimal.ejecutar(ComandoAlimentarAnimal(e.idPartida, a, nueva())))
        r.kilos shouldBe esperado
        r.nivel shouldBe 60
        bambu() shouldBe antes - esperado
    }

    @Test
    fun `un animal de nivel 1 consume la cantidad base`() {
        val sp = espacio()
        val a = e.datos.animal(e.idPartida, sp, "erizo", "Novato", alimentacion = 50, nivel = 1)
        exito(e.alimentarAnimal.ejecutar(ComandoAlimentarAnimal(e.idPartida, a, nueva()))).kilos shouldBe kg("erizo")
    }

    @Test
    fun `las vistas previas usan la racion del nivel`() {
        val sp = espacio()
        val a = e.datos.animal(e.idPartida, sp, "erizo", "Veterano", alimentacion = 60, nivel = 21)
        e.datos.animal(e.idPartida, sp, "erizo", "Novato", alimentacion = 80, nivel = 1)
        e.consultarCuidado.racion(e.idPartida, a)!!.kilos shouldBe ReglasCuidado.kilosPorRacion(kg("erizo"), 21)
        val total = ReglasCuidado.kilosHastaMaximo(60, kg("erizo"), 21) + ReglasCuidado.kilosHastaMaximo(80, kg("erizo"), 1)
        e.consultarCuidado.grupal(e.idPartida, sp).kilos shouldBe total
        e.consultarCuidado.grupal(e.idPartida, sp).animales shouldBe 2
    }

    // ---------------------------------------------------------------- Alimentación por hábitat (CU-13)

    @Test
    fun `alimentar un habitat lleva al cien por cien a todos los animales de todos sus espacios`() {
        val s1 = espacio("Uno")
        val s2 = espacio("Dos")
        val otro = espacio("Fuera", habitat = "humedal_agua_dulce")
        val a = e.datos.animal(e.idPartida, s1, "erizo", "A", alimentacion = 40)
        val b = e.datos.animal(e.idPartida, s2, "erizo", "B", alimentacion = 90, nivel = 11)
        val lleno = e.datos.animal(e.idPartida, s2, "erizo", "C", alimentacion = 100)
        val ajeno = e.datos.animal(e.idPartida, otro, "nutria", "D", alimentacion = 20)
        val esperado = ReglasCuidado.kilosHastaMaximo(40, kg("erizo"), 1) + ReglasCuidado.kilosHastaMaximo(90, kg("erizo"), 11)
        fijarBambu(200)

        val r = exito(alimentarHabitat("bosque_templado", kilos = esperado))
        r.animales shouldBe 2
        r.kilos shouldBe esperado
        r.bambuRestante shouldBe 200 - esperado
        bambu() shouldBe 200 - esperado
        alimentacion(a) shouldBe 100
        alimentacion(b) shouldBe 100
        alimentacion(lleno) shouldBe 100
        alimentacion(ajeno) shouldBe 20
        e.auditoriaDe("ALIMENTACION_HABITAT").map { it.first } shouldContainExactly listOf("CONFIRMADO")
    }

    @Test
    fun `la vista previa del habitat coincide con lo que consume la operacion`() {
        val s1 = espacio("Uno")
        val s2 = espacio("Dos")
        e.datos.animal(e.idPartida, s1, "erizo", "A", alimentacion = 40)
        e.datos.animal(e.idPartida, s2, "erizo", "B", alimentacion = 70, nivel = 5)
        fijarBambu(200)
        val previa = e.consultarCuidado.grupalHabitat(e.idPartida, e.habitat("bosque_templado"))
        previa.animales shouldBe 2
        previa.bambuDisponible shouldBe 200
        exito(alimentarHabitat("bosque_templado", kilos = previa.kilos)).kilos shouldBe previa.kilos
    }

    @Test
    fun `sin bambu suficiente el habitat completo se rechaza sin cambios`() {
        val s1 = espacio("Uno")
        val s2 = espacio("Dos")
        val a = e.datos.animal(e.idPartida, s1, "erizo", "A", alimentacion = 0)
        val b = e.datos.animal(e.idPartida, s2, "erizo", "B", alimentacion = 0)
        fijarBambu(3)
        val m = rechazo(alimentarHabitat("bosque_templado"))
        m.shouldBeInstanceOf<MotivoRechazo.BambuInsuficiente>()
        bambu() shouldBe 3
        alimentacion(a) shouldBe 0
        alimentacion(b) shouldBe 0
        e.auditoriaDe("ALIMENTACION_HABITAT").map { it.first } shouldContainExactly listOf("RECHAZADO")
    }

    @Test
    fun `si cambia el conjunto de animales la operacion pide confirmar de nuevo`() {
        val s1 = espacio("Uno")
        e.datos.animal(e.idPartida, s1, "erizo", "A", alimentacion = 40)
        fijarBambu(200)
        val visto = e.consultarCuidado.grupalHabitat(e.idPartida, e.habitat("bosque_templado")).kilos
        e.datos.animal(e.idPartida, s1, "erizo", "Llegado", alimentacion = 10)
        val m = rechazo(alimentarHabitat("bosque_templado", kilos = visto))
        m.shouldBeInstanceOf<MotivoRechazo.ConjuntoCambiado>()
        bambu() shouldBe 200
    }

    @Test
    fun `un habitat sin animales con hambre se rechaza y uno inexistente tambien`() {
        espacio("Uno")
        rechazo(alimentarHabitat("bosque_templado")) shouldBe MotivoRechazo.NadaQueAlimentar
        rechazo(e.alimentarHabitat.ejecutar(ComandoAlimentarHabitat(e.idPartida, "no-existe", null, nueva()))) shouldBe
            MotivoRechazo.HabitatNoDisponible
    }

    @Test
    fun `repetir la misma solicitud no vuelve a consumir bambu`() {
        val s1 = espacio("Uno")
        e.datos.animal(e.idPartida, s1, "erizo", "A", alimentacion = 40)
        fijarBambu(100)
        val sol = nueva()
        val primera = exito(alimentarHabitat("bosque_templado", sol = sol))
        val restante = bambu()
        val segunda = exito(alimentarHabitat("bosque_templado", sol = sol))
        segunda shouldBe primera
        bambu() shouldBe restante
    }

    // ---------------------------------------------------------------- Estadísticas del animal (CU-23)

    @Test
    fun `las estadisticas desglosan la satisfaccion y el beneficio de un animal`() {
        val sp = espacio()
        val a = e.datos.animal(e.idPartida, sp, "erizo", "A", alimentacion = 80, nivel = 3)
        e.datos.animal(e.idPartida, sp, "nutria", "B")
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)

        val s = e.consultarEstadisticas.ejecutar(e.idPartida, a)!!
        val ficha = e.animales.ficha(e.idPartida, a)!!
        s.satisfaccion.desglose.valor shouldBe (ficha.satisfaccion plusOrMinus 1e-9)
        s.satisfaccion.social.aportaciones.map { it.nombre } shouldContainExactly
            listOf("Pareja", "Mejor amigo", "Padre", "Madre", "Hermanos", "Afinidad con el espacio")
        s.satisfaccion.social.aportaciones.sumOf { it.peso } shouldBe (1.0 plusOrMinus 1e-9)
        s.satisfaccion.pesoBio + s.satisfaccion.pesoSoc shouldBe (1.0 plusOrMinus 1e-9)
        s.beneficio.nivel shouldBe 3
        s.beneficio.satisfaccion shouldBe (ficha.satisfaccion plusOrMinus 1e-9)
        s.beneficio.porCiclo shouldBe (s.beneficio.beneficioBase * s.beneficio.satisfaccion * s.beneficio.multiplicadorNivel plusOrMinus 1e-9)
    }

    @Test
    fun `consultar estadisticas no recalcula ni audita ni cambia nada`() {
        val sp = espacio()
        val a = e.datos.animal(e.idPartida, sp, "erizo", "A")
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        val recalculos = e.recalcular.invocaciones
        val auditorias = e.contar("AuditoriaEvento")
        val metricas = e.contar("MetricaSatisfaccion")
        repeat(3) { e.consultarEstadisticas.ejecutar(e.idPartida, a) }
        e.recalcular.invocaciones shouldBe recalculos
        e.contar("AuditoriaEvento") shouldBe auditorias
        e.contar("MetricaSatisfaccion") shouldBe metricas
    }

    @Test
    fun `no hay estadisticas de un animal inexistente, muerto o sin espacio`() {
        val sp = espacio()
        val muerto = e.datos.animal(e.idPartida, sp, "erizo", "Muerto")
        e.animales.retirar(e.idPartida, muerto, "MUERTO")
        e.consultarEstadisticas.ejecutar(e.idPartida, muerto) shouldBe null
        e.consultarEstadisticas.ejecutar(e.idPartida, "no-existe") shouldBe null
    }

    // ---------------------------------------------------------------- Panel de prestigio: desbloqueo siguiente (CU-19)

    @Test
    fun `el panel nombra lo que desbloquea el prestigio siguiente`() {
        val p = e.consultarPrestigio.ejecutar(e.idPartida)
        val nivel1 = p.desbloqueoSiguiente!!
        nivel1.especies shouldContainExactly listOf("Suricata")
        nivel1.habitats shouldContainExactly listOf("Campos de Esmeril")
    }

    @Test
    fun `un nivel que no abre nada no muestra desbloqueo y el maximo no tiene siguiente`() {
        e.bd.escribir("UPDATE Partida SET prestigio_actual = 5 WHERE id_partida = ?", e.idPartida)
        // El nivel 6 no desbloquea nada.
        val p = e.consultarPrestigio.ejecutar(e.idPartida)
        p.siguiente!!.nivel shouldBe 6
        p.desbloqueoSiguiente!!.especies shouldBe emptyList()
        p.desbloqueoSiguiente!!.habitats shouldBe emptyList()

        e.bd.escribir("UPDATE Partida SET prestigio_actual = 100 WHERE id_partida = ?", e.idPartida)
        val tope = e.consultarPrestigio.ejecutar(e.idPartida)
        tope.siguiente shouldBe null
        tope.desbloqueoSiguiente shouldBe null
    }

    // ---------------------------------------------------------------- Espacio gratuito, ampliaciones y venta (CU-03, CU-14)

    private fun comprarEspacio(nombre: String, capacidad: Int, sol: String = nueva()) =
        e.adquirirEspacio.ejecutar(ComandoAdquirirEspacio(e.idPartida, e.habitat("bosque_templado"), nombre, capacidad, sol))

    @Test
    fun `el primer espacio de seis animales es gratis y el segundo se cobra`() {
        val inicial = e.fondos()
        e.consultarTienda.capacidades(e.idPartida).first { it.capacidad == 6 }.coste shouldBe Dinero.CERO
        val primero = exito(comprarEspacio("Primero", 6))
        primero.coste shouldBe Dinero.CERO
        e.fondos() shouldBe inicial
        e.contar("MovimientoEconomico") shouldBe 0
        e.zoo.costePagado(e.idPartida, primero.idEspacio) shouldBe Dinero.CERO
        e.zoo.espacioGratuitoDisponible(e.idPartida) shouldBe false

        val coste6 = e.catalogo.opcionesCapacidad().first { it.capacidad == 6 }.coste
        e.consultarTienda.capacidades(e.idPartida).first { it.capacidad == 6 }.coste shouldBe coste6
        val segundo = exito(comprarEspacio("Segundo", 6))
        segundo.coste shouldBe coste6
        e.fondos() shouldBe inicial - coste6
        e.zoo.costePagado(e.idPartida, segundo.idEspacio) shouldBe coste6
    }

    @Test
    fun `otras capacidades no consumen el espacio gratuito`() {
        val coste8 = e.catalogo.opcionesCapacidad().first { it.capacidad == 8 }.coste
        exito(comprarEspacio("Ocho", 8)).coste shouldBe coste8
        e.zoo.espacioGratuitoDisponible(e.idPartida) shouldBe true
    }

    @Test
    fun `una compra rechazada no consume el espacio gratuito`() {
        exito(comprarEspacio("Repetido", 8))
        rechazo(comprarEspacio("Repetido", 6)) shouldBe MotivoRechazo.DenominacionDuplicada
        e.zoo.espacioGratuitoDisponible(e.idPartida) shouldBe true
    }

    @Test
    fun `vender el espacio gratuito no devuelve dinero ni el derecho a otro gratis`() {
        val gratis = exito(comprarEspacio("Gratis", 6)).idEspacio
        val antes = e.fondos()
        val r = exito(e.darDeBajaEspacio.ejecutar(ComandoDarDeBajaEspacio(e.idPartida, gratis, nueva())))
        r.reembolso shouldBe Dinero.CERO
        e.fondos() shouldBe antes
        e.contar("MovimientoEconomico", "tipo = 'VENTA'") shouldBe 0
        e.zoo.espacioGratuitoDisponible(e.idPartida) shouldBe false
    }

    @Test
    fun `las ampliaciones disponibles muestran el coste real con recargo`() {
        val opciones = e.catalogo.opcionesCapacidad().associate { it.capacidad to it.coste }
        val lista = e.consultarTienda.ampliaciones(10)
        lista.map { it.capacidad } shouldContainExactly listOf(15, 20, 25, 30)
        lista.forEach {
            it.coste.centimos shouldBe ((opciones.getValue(it.capacidad).centimos - opciones.getValue(10).centimos) * 5 + 2) / 4
        }
        e.consultarTienda.ampliaciones(30) shouldBe emptyList()
    }

    @Test
    fun `vender un espacio vacio lo borra con su metrica y devuelve lo pagado`() {
        val coste8 = e.catalogo.opcionesCapacidad().first { it.capacidad == 8 }.coste
        val id = exito(comprarEspacio("Ocho", 8)).idEspacio
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        val antes = e.fondos()

        e.consultarZoo.costePagado(e.idPartida, id) shouldBe coste8
        val r = exito(e.darDeBajaEspacio.ejecutar(ComandoDarDeBajaEspacio(e.idPartida, id, nueva())))
        r.reembolso shouldBe coste8
        e.fondos() shouldBe antes + coste8
        e.zoo.espacio(e.idPartida, id) shouldBe null
        e.contar("MetricaSatisfaccion", "id_espacio = '$id'") shouldBe 0
        e.contar("MovimientoEconomico", "tipo = 'VENTA'") shouldBe 1
        e.zoo.espacios(e.idPartida, e.habitat("bosque_templado")).none { it.id == id } shouldBe true
    }

    @Test
    fun `repetir la venta de un espacio devuelve el mismo reembolso sin acreditar dos veces`() {
        val id = exito(comprarEspacio("Ocho", 8)).idEspacio
        val sol = nueva()
        val primera = exito(e.darDeBajaEspacio.ejecutar(ComandoDarDeBajaEspacio(e.idPartida, id, sol)))
        val fondos = e.fondos()
        val segunda = exito(e.darDeBajaEspacio.ejecutar(ComandoDarDeBajaEspacio(e.idPartida, id, sol)))
        segunda shouldBe primera
        e.fondos() shouldBe fondos
    }

    @Test
    fun `no se vende un espacio con animales ni uno que ya no existe`() {
        val sp = espacio()
        e.datos.animal(e.idPartida, sp, "erizo", "A")
        rechazo(e.darDeBajaEspacio.ejecutar(ComandoDarDeBajaEspacio(e.idPartida, sp, nueva()))) shouldBe MotivoRechazo.EspacioNoVacio
        e.zoo.espacio(e.idPartida, sp)!!.capacidadMax shouldBe 10
        rechazo(e.darDeBajaEspacio.ejecutar(ComandoDarDeBajaEspacio(e.idPartida, "no-existe", nueva()))) shouldBe
            MotivoRechazo.EspacioInexistente
    }

    @Test
    fun `un espacio vendido deja de contar en el recalculo`() {
        val vacio = exito(comprarEspacio("Vacio", 8)).idEspacio
        val sp = espacio()
        e.datos.animal(e.idPartida, sp, "erizo", "A")
        exito(e.darDeBajaEspacio.ejecutar(ComandoDarDeBajaEspacio(e.idPartida, vacio, nueva())))
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        e.consultarMonitor.ejecutar(e.idPartida).parque.shouldBeInstanceOf<Seccion.Disponible<*>>()
        e.contar("MetricaSatisfaccion", "nivel = 'ESPACIO'") shouldBe 1
    }

    // ---------------------------------------------------------------- Migraciones del esquema

    private fun version() = e.bd.uno("PRAGMA user_version") { it.entero(0) }!!

    @Test
    fun `la base nueva esta en la version vigente`() {
        version() shouldBe com.pandazoo.data.db.BaseDatos.VERSION
    }

    @Test
    fun `migracion 4 a 5 reduce a la mitad el beneficio base`() {
        val antes = e.bd.leer("SELECT id_rango, beneficio_base_animal_ciclo FROM RangoEspecie ORDER BY id_rango") { it.entero(0) to it.decimal(1) }
        e.bd.migrarDe4a5()
        val despues = e.bd.leer("SELECT id_rango, beneficio_base_animal_ciclo FROM RangoEspecie ORDER BY id_rango") { it.entero(0) to it.decimal(1) }
        despues.map { it.second } shouldContainExactly antes.map { it.second / 2.0 }
        version() shouldBe 5
    }

    @Test
    fun `migracion 5 a 6 recoloca los desbloqueos y amplia la mision de prestigio`() {
        e.bd.migrarDe5a6()
        e.bd.uno("SELECT nivel_desbloqueo FROM Especie WHERE id_especie = 'tigre'") { it.entero(0) } shouldBe 95
        e.bd.uno("SELECT nivel_desbloqueo FROM Especie WHERE id_especie = 'erizo'") { it.entero(0) } shouldBe 0
        e.bd.uno(
            "SELECT valor_maximo FROM ParametroMision WHERE id_mision = 'subir_prestigio' AND nombre_parametro = 'nivel_prestigio_requerido'",
        ) { it.decimal(0) } shouldBe 100.0
        version() shouldBe 6
    }

    @Test
    fun `migracion 6 a 7 fija los costes de espacio y retira los espacios inactivos vacios`() {
        val inactivo = espacio("Antiguo", capacidad = 6)
        e.bd.escribir("UPDATE Espacio SET estado = 'INACTIVO' WHERE id_espacio = ?", inactivo)
        val activo = espacio("Vigente", capacidad = 6)
        e.bd.migrarDe6a7()
        e.zoo.espacio(e.idPartida, inactivo) shouldBe null
        e.zoo.espacio(e.idPartida, activo)!!.nombre shouldBe "Vigente"
        e.bd.uno("SELECT importe FROM CosteEspacio WHERE capacidad_max = 6") { it.decimal(0) } shouldBe 325.0
        e.bd.uno("SELECT importe FROM CosteEspacio WHERE capacidad_max = 20") { it.decimal(0) } shouldBe 910.0
        e.bd.uno("SELECT probabilidad_enfermedad FROM RangoEspecie WHERE id_rango = 5") { it.decimal(0) } shouldBe 0.025
        version() shouldBe 7
    }

    @Test
    fun `migracion 7 a 8 reparte los desbloqueos y retira los que aun no tocan`() {
        e.bd.escribir(
            "INSERT INTO DesbloqueoEspecie (id_partida, id_especie, origen, fecha) VALUES (?, 'tigre', 'PRESTIGIO', 0)",
            e.idPartida,
        )
        e.bd.migrarDe7a8()
        e.bd.uno("SELECT nivel_desbloqueo FROM Especie WHERE id_especie = 'suricata'") { it.entero(0) } shouldBe 1
        e.bd.uno("SELECT nivel_desbloqueo FROM Especie WHERE id_especie = 'tigre'") { it.entero(0) } shouldBe 87
        e.contar("DesbloqueoEspecie", "id_especie = 'tigre'") shouldBe 0
        e.contar("DesbloqueoEspecie", "id_especie = 'erizo'") shouldBe 1
        version() shouldBe 8
    }

    @Test
    fun `migracion 8 a 9 borra todos los datos y deja el esquema vigente`() {
        e.contar("Partida") shouldBe 1
        e.bd.migrarDe8a9()
        e.contar("Partida") shouldBe 0
        e.contar("Especie") shouldBe 0
        version() shouldBe 9
        // El esquema recreado admite una partida nueva con todas las columnas actuales.
        val nueva = com.pandazoo.data.inicial.InicializadorPartida(e.bd, e.reloj, com.pandazoo.testing.GeneradorIdSecuencial("n"))
            .crearPartida(e.cfg)
        e.zoo.espacioGratuitoDisponible(nueva) shouldBe true
    }

    @Test
    fun `migracion 9 a 10 repara los mejores amigos que no eran reciprocos`() {
        val sp = espacio()
        val a = e.datos.animal(e.idPartida, sp, "erizo", "A")
        val b = e.datos.animal(e.idPartida, sp, "erizo", "B")
        val c = e.datos.animal(e.idPartida, sp, "erizo", "C")
        e.bd.escribir("UPDATE Animal SET id_mejor_amigo = ? WHERE id_animal = ?", b, a)
        // C apunta a B, que ya tiene a A como amigo tras la reparación: el vínculo de C debe desaparecer.
        e.bd.escribir("UPDATE Animal SET id_mejor_amigo = ? WHERE id_animal = ?", b, c)
        e.bd.migrarDe9a10()
        fun amigo(id: String) = e.bd.uno("SELECT id_mejor_amigo FROM Animal WHERE id_animal = ?", id) { it.textoONulo(0) }
        amigo(b) shouldBe a
        amigo(a) shouldBe b
        amigo(c) shouldBe null
        version() shouldBe 10
    }

    @Test
    fun `migracion 11 a 12 anade las capacidades de 25 y 30 y se puede repetir`() {
        e.bd.escribir("DELETE FROM CosteEspacio WHERE capacidad_max IN (25, 30)")
        e.bd.migrarDe11a12()
        e.bd.migrarDe11a12()
        e.bd.uno("SELECT importe FROM CosteEspacio WHERE capacidad_max = 25") { it.decimal(0) } shouldBe 1105.0
        e.bd.uno("SELECT importe FROM CosteEspacio WHERE capacidad_max = 30") { it.decimal(0) } shouldBe 1300.0
        version() shouldBe 12
    }

    // ---------------------------------------------------------------- Ampliación: idempotencia y errores

    @Test
    fun `repetir una ampliacion confirmada no cobra dos veces`() {
        fondos(5000.0)
        val id = espacio(capacidad = 10)
        val sol = nueva()
        val cmd = com.pandazoo.application.caso.ComandoAmpliarEspacio(e.idPartida, id, 20, sol)
        val primera = exito(e.ampliarEspacio.ejecutar(cmd))
        val saldo = e.fondos()
        exito(e.ampliarEspacio.ejecutar(cmd)) shouldBe primera
        e.fondos() shouldBe saldo
        e.contar("MovimientoEconomico", "origen = 'CU-03'") shouldBe 1
        e.auditoriaDe("AMPLIACION_ESPACIO").map { it.first } shouldContainExactly listOf("CONFIRMADO")
    }

    @Test
    fun `ampliar un espacio inexistente se rechaza`() {
        rechazo(e.ampliarEspacio.ejecutar(com.pandazoo.application.caso.ComandoAmpliarEspacio(e.idPartida, "no-existe", 20, nueva()))) shouldBe
            MotivoRechazo.EspacioInexistente
    }

    @Test
    fun `ampliar a una capacidad que no esta en la tabla se rechaza`() {
        val id = espacio(capacidad = 10)
        rechazo(e.ampliarEspacio.ejecutar(com.pandazoo.application.caso.ComandoAmpliarEspacio(e.idPartida, id, 12, nueva()))) shouldBe
            MotivoRechazo.CapacidadNoPermitida
    }

    @Test
    fun `un espacio ampliado admite mas animales`() {
        fondos(5000.0)
        val id = espacio(capacidad = 6)
        repeat(6) { e.datos.animal(e.idPartida, id, "erizo", "A$it") }
        e.zoo.espacio(e.idPartida, id)!!.let { it.ocupacion shouldBe it.capacidadMax }
        exito(e.ampliarEspacio.ejecutar(com.pandazoo.application.caso.ComandoAmpliarEspacio(e.idPartida, id, 8, nueva())))
        e.zoo.espacio(e.idPartida, id)!!.let { (it.ocupacion < it.capacidadMax) shouldBe true }
    }
}
