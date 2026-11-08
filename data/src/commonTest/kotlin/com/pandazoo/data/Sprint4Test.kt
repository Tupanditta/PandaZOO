package com.pandazoo.data

import com.pandazoo.application.caso.AlcanceRecalculo
import com.pandazoo.application.caso.AlimentarAnimal
import com.pandazoo.application.caso.AlimentarEspacio
import com.pandazoo.application.caso.ComandoAlimentarAnimal
import com.pandazoo.application.caso.ComandoAlimentarEspacio
import com.pandazoo.application.caso.ComandoTratarAnimal
import com.pandazoo.application.caso.TratarAnimal
import com.pandazoo.application.error.FaltanteInsumo
import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.puerto.ConsumoMedico
import com.pandazoo.application.puerto.CuidadoRepositorio
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Sprint 4: alimentación individual y grupal (CU-13) y tratamiento médico (CU-09). */
class Sprint4Test {
    private lateinit var e: Entorno
    private var solicitud = 0

    @BeforeTest
    fun preparar() {
        e = Entorno()
    }

    @AfterTest
    fun cerrar() = e.cerrar()

    private fun nueva() = "sol-${++solicitud}"

    private fun rechazo(r: Resultado<*>): MotivoRechazo = (r as Resultado.Rechazado).motivo

    private fun <T> exito(r: Resultado<T>): T = (r as Resultado.Exito<T>).valor

    private fun espacio(nombre: String = "Madriguera", capacidad: Int = 10) =
        e.datos.espacio(e.idPartida, e.habitat("bosque_templado"), nombre, capacidad)

    private fun animal(sp: String, especie: String = "erizo", nombre: String = "Pincho", alimentacion: Int = 100, enfermo: Boolean = false) =
        e.datos.animal(e.idPartida, sp, especie, nombre, alimentacion, enfermo)

    private fun kg(especie: String) = e.cfg.tablas.alimentosPorEspecie.first { it.idEspecie == especie }.kilogramos

    private fun bambu() = e.bd.uno("SELECT cantidad FROM InventarioAlimento WHERE id_partida = ?", e.idPartida) { it.entero(0) }!!

    private fun insumos() = e.bd.uno("SELECT cantidad FROM InventarioMedico WHERE id_partida = ?", e.idPartida) { it.entero(0) }!!

    private fun fijarBambu(n: Int) = e.bd.escribir("UPDATE InventarioAlimento SET cantidad = ? WHERE id_partida = ?", n, e.idPartida)

    private fun fijarInsumos(n: Int) = e.bd.escribir("UPDATE InventarioMedico SET cantidad = ? WHERE id_partida = ?", n, e.idPartida)

    private fun nivel(id: String) = e.bd.uno("SELECT nivel_alimentacion FROM Animal WHERE id_animal = ?", id) { it.entero(0) }!!

    private fun satisfaccion(id: String) = e.bd.uno("SELECT satisfaccion_actual FROM Animal WHERE id_animal = ?", id) { it.decimal(0) }!!

    private fun salud(id: String) = e.bd.uno("SELECT estado_salud FROM Animal WHERE id_animal = ?", id) { it.texto(0) }!!

    private fun alimentar(id: String, sol: String = nueva()) = e.alimentarAnimal.ejecutar(ComandoAlimentarAnimal(e.idPartida, id, sol))

    private fun alimentarEspacio(sp: String, kilos: Int? = null, sol: String = nueva()) =
        e.alimentarEspacio.ejecutar(ComandoAlimentarEspacio(e.idPartida, sp, kilos, sol))

    private fun tratar(id: String, sol: String = nueva()) = e.tratarAnimal.ejecutar(ComandoTratarAnimal(e.idPartida, id, sol))

    private fun instantanea(): List<Any?> = listOf(
        bambu(), insumos(), e.contar("Animal"),
        e.bd.leer("SELECT id_animal, nivel_alimentacion, estado_salud, ciclos_desnutricion, ciclos_enfermedad, satisfaccion_actual FROM Animal ORDER BY id_animal") {
            listOf(it.texto(0), it.entero(1), it.texto(2), it.entero(3), it.entero(4), it.decimal(5))
        },
        e.bd.leer("SELECT nivel, id_espacio, id_habitat, valor FROM MetricaSatisfaccion ORDER BY id_metrica") {
            listOf(it.texto(0), it.textoONulo(1), it.textoONulo(2), it.decimal(3))
        },
        e.bd.leer("SELECT id_notificacion, estado FROM Notificacion ORDER BY id_notificacion") { it.entero(0) to it.texto(1) },
    )

    private fun alerta(idAnimal: String) {
        e.bd.escribir(
            "INSERT INTO Notificacion (id_partida, tipo, severidad, codigo, entidad, id_entidad, numero_ciclo, estado, fecha) " +
                "VALUES (?, 'CRITICA', 'ALTA', 'ALERTA_SALUD', 'ANIMAL', ?, 0, 'PENDIENTE', 0)",
            e.idPartida, idAnimal,
        )
    }

    private fun estadoAlerta(idAnimal: String) =
        e.bd.uno("SELECT estado FROM Notificacion WHERE id_entidad = ?", idAnimal) { it.texto(0) }

    // ---- T4.1 individual ----

    @Test
    fun `T4_1 alimentar de 60 a 70 consume los kilos de la especie`() {
        val sp = espacio()
        val a = animal(sp, alimentacion = 60)
        val r = exito(alimentar(a))
        r.nivel shouldBe 70
        r.kilos shouldBe kg("erizo")
        nivel(a) shouldBe 70
        bambu() shouldBe 30 - kg("erizo")
        r.bambuRestante shouldBe bambu()
        e.auditoriaDe("ALIMENTACION_INDIVIDUAL").map { it.first } shouldContainExactly listOf("CONFIRMADO")
    }

    @Test
    fun `T4_1 el limite es 100 y un animal al 100 o no vivo es rechazado sin cambios`() {
        val sp = espacio()
        val lleno = animal(sp, nombre = "Lleno")
        val casi = animal(sp, nombre = "Casi", alimentacion = 90)
        exito(alimentar(casi)).nivel shouldBe 100
        e.animales.retirar(e.idPartida, animal(sp, nombre = "Muerto", alimentacion = 10), "MUERTO")
        val antes = instantanea()
        rechazo(alimentar(casi)) shouldBe MotivoRechazo.AlimentacionCompleta
        rechazo(alimentar(lleno)) shouldBe MotivoRechazo.AlimentacionCompleta
        rechazo(alimentar("no-existe")) shouldBe MotivoRechazo.AnimalInexistente
        instantanea() shouldBe antes
    }

    @Test
    fun `T4_1 un animal no vivo es rechazado`() {
        val sp = espacio()
        val a = animal(sp, alimentacion = 50)
        e.animales.retirar(e.idPartida, a, "MUERTO")
        val antes = instantanea()
        rechazo(alimentar(a)) shouldBe MotivoRechazo.AnimalNoVivo
        instantanea() shouldBe antes
    }

    @Test
    fun `T4_1 alimentar desde 0 reinicia el contador de desnutricion`() {
        val sp = espacio()
        val a = animal(sp, alimentacion = 0)
        e.bd.escribir("UPDATE Animal SET ciclos_desnutricion = 2 WHERE id_animal = ?", a)
        exito(alimentar(a)).nivel shouldBe 10
        e.bd.uno("SELECT ciclos_desnutricion FROM Animal WHERE id_animal = ?", a) { it.entero(0) } shouldBe 0
    }

    // ---- T4.2 falta de bambú o de configuración ----

    @Test
    fun `T4_2 sin bambu suficiente rechaza con la cantidad exacta y no cambia nada`() {
        val sp = espacio()
        val a = animal(sp, alimentacion = 60)
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        fijarBambu(kg("erizo") - 1)
        val antes = instantanea()
        val m = rechazo(alimentar(a)) as MotivoRechazo.BambuInsuficiente
        m.requerido shouldBe kg("erizo")
        m.disponible shouldBe kg("erizo") - 1
        m.faltante shouldBe 1
        instantanea() shouldBe antes
        e.auditoriaDe("ALIMENTACION_INDIVIDUAL").map { it.first } shouldContainExactly listOf("RECHAZADO")
    }

    @Test
    fun `T4_2 sin cantidad configurada para la especie bloquea la operacion sin suponer racion`() {
        val sp = espacio()
        val a = animal(sp, alimentacion = 60)
        val sinConfiguracion = object : CuidadoRepositorio by e.cuidado {
            override fun kilosPorIncremento(idEspecie: String): Int? = null
        }
        val caso = AlimentarAnimal(e.ejecutor, e.animales, sinConfiguracion, e.inventario, e.recalcular)
        val antes = instantanea()
        rechazo(caso.ejecutar(ComandoAlimentarAnimal(e.idPartida, a, nueva()))) shouldBe MotivoRechazo.RacionNoConfigurada
        instantanea() shouldBe antes
    }

    // ---- T4.3 grupal ----

    @Test
    fun `T4_3 el total mostrado coincide con el consumo real y se excluyen los de 100 y los no vivos`() {
        val sp = espacio()
        val a = animal(sp, "erizo", "A", alimentacion = 70)
        val b = animal(sp, "nutria", "B", alimentacion = 0)
        animal(sp, "erizo", "Lleno", alimentacion = 100)
        val muerto = animal(sp, "erizo", "Muerto", alimentacion = 10)
        e.animales.retirar(e.idPartida, muerto, "MUERTO")
        val esperado = 3 * kg("erizo") + 10 * kg("nutria")

        val previa = e.consultarCuidado.grupal(e.idPartida, sp)
        previa.animales shouldBe 2
        previa.kilos shouldBe esperado
        previa.bambuDisponible shouldBe 30

        fijarBambu(esperado + 4)
        val r = exito(alimentarEspacio(sp, kilos = previa.kilos))
        r.kilos shouldBe esperado
        r.animales shouldBe 2
        nivel(a) shouldBe 100
        nivel(b) shouldBe 100
        bambu() shouldBe 4
        e.auditoriaDe("ALIMENTACION_GRUPAL").map { it.first } shouldContainExactly listOf("CONFIRMADO")
    }

    @Test
    fun `T4_3 inventario insuficiente no alimenta a nadie`() {
        val sp = espacio()
        val a = animal(sp, "erizo", "A", alimentacion = 50)
        val b = animal(sp, "erizo", "B", alimentacion = 50)
        val total = 10 * kg("erizo")
        fijarBambu(total - 1)
        val antes = instantanea()
        val m = rechazo(alimentarEspacio(sp)) as MotivoRechazo.BambuInsuficiente
        m.requerido shouldBe total
        m.faltante shouldBe 1
        nivel(a) shouldBe 50
        nivel(b) shouldBe 50
        instantanea() shouldBe antes
    }

    @Test
    fun `T4_3 si cambia el conjunto antes de confirmar se rechaza todo`() {
        val sp = espacio()
        val a = animal(sp, alimentacion = 50)
        val mostrado = e.consultarCuidado.grupal(e.idPartida, sp).kilos!!
        animal(sp, nombre = "Nuevo", alimentacion = 20)
        val antes = instantanea()
        val m = rechazo(alimentarEspacio(sp, kilos = mostrado)) as MotivoRechazo.ConjuntoCambiado
        m.requerido shouldBe mostrado + 8 * kg("erizo")
        nivel(a) shouldBe 50
        instantanea() shouldBe antes
    }

    @Test
    fun `T4_3 espacio sin animales por debajo del 100 no inicia la accion`() {
        val sp = espacio()
        animal(sp)
        val antes = instantanea()
        rechazo(alimentarEspacio(sp)) shouldBe MotivoRechazo.NadaQueAlimentar
        rechazo(alimentarEspacio("no-existe")) shouldBe MotivoRechazo.EspacioInexistente
        instantanea() shouldBe antes
    }

    @Test
    fun `T4_3 sin cantidad configurada para una especie bloquea todo el lote`() {
        val sp = espacio()
        val a = animal(sp, "erizo", "A", alimentacion = 50)
        animal(sp, "nutria", "B", alimentacion = 50)
        val sinNutria = object : CuidadoRepositorio by e.cuidado {
            override fun kilosPorIncremento(idEspecie: String): Int? =
                if (idEspecie == "nutria") null else e.cuidado.kilosPorIncremento(idEspecie)
        }
        val caso = AlimentarEspacio(e.ejecutor, e.zoo, e.animales, sinNutria, e.inventario, e.recalcular)
        val antes = instantanea()
        rechazo(caso.ejecutar(ComandoAlimentarEspacio(e.idPartida, sp, null, nueva()))) shouldBe MotivoRechazo.RacionNoConfigurada
        nivel(a) shouldBe 50
        instantanea() shouldBe antes
    }

    // ---- T4.4 propiedad ----

    @Test
    fun `T4_4 tras cualquier secuencia la alimentacion es 0 o multiplo de 10`() {
        val sp = espacio()
        val ids = listOf(0, 10, 40, 90, 100).map { animal(sp, nombre = "N$it", alimentacion = it) }
        fijarBambu(1000)
        repeat(6) { ronda ->
            if (ronda % 2 == 0) ids.forEach { alimentar(it) } else alimentarEspacio(sp)
            ids.forEach { (nivel(it) % 10) shouldBe 0 }
        }
        ids.forEach { nivel(it) shouldBe 100 }
    }

    // ---- T4.5 tratamiento ----

    @Test
    fun `T4_5 un animal sano o no vivo no se trata`() {
        val sp = espacio()
        val sano = animal(sp, nombre = "Sano")
        val muerto = animal(sp, nombre = "Muerto", enfermo = true)
        e.animales.retirar(e.idPartida, muerto, "MUERTO")
        val antes = instantanea()
        rechazo(tratar(sano)) shouldBe MotivoRechazo.AnimalSano
        rechazo(tratar(muerto)) shouldBe MotivoRechazo.AnimalNoVivo
        rechazo(tratar("no-existe")) shouldBe MotivoRechazo.AnimalInexistente
        instantanea() shouldBe antes
    }

    @Test
    fun `T4_5 con insumos parciales rechaza y no consume nada`() {
        val sp = espacio()
        val a = animal(sp, "leon", "Rey", enfermo = true) // rango 3: 3 unidades
        fijarInsumos(2)
        val antes = instantanea()
        val m = rechazo(tratar(a)) as MotivoRechazo.InsumosInsuficientes
        m.faltantes shouldBe listOf(FaltanteInsumo("Medicina", 3, 2))
        salud(a) shouldBe "ENFERMO"
        instantanea() shouldBe antes
    }

    @Test
    fun `T4_5 tratamiento correcto consume todo deja sano y reinicia el contador`() {
        val sp = espacio()
        val a = animal(sp, "leon", "Rey", enfermo = true)
        e.bd.escribir("UPDATE Animal SET ciclos_enfermedad = 2 WHERE id_animal = ?", a)
        fijarInsumos(5)
        val previa = e.consultarCuidado.tratamiento(e.idPartida, a)
        previa.map { Triple(it.nombre, it.necesario, it.disponible) } shouldBe listOf(Triple("Medicina", 3, 5))

        exito(tratar(a)).unidades shouldBe 3
        insumos() shouldBe 2
        salud(a) shouldBe "SANO"
        e.bd.uno("SELECT ciclos_enfermedad FROM Animal WHERE id_animal = ?", a) { it.entero(0) } shouldBe 0
        e.auditoriaDe("TRATAMIENTO").map { it.first } shouldContainExactly listOf("CONFIRMADO")
    }

    @Test
    fun `T4_5 sin consumo medico configurado para el rango bloquea el tratamiento`() {
        val sp = espacio()
        val a = animal(sp, enfermo = true)
        val sin = object : CuidadoRepositorio by e.cuidado {
            override fun consumoMedico(idPartida: String, idAnimal: String) = emptyList<ConsumoMedico>()
        }
        val caso = TratarAnimal(e.ejecutor, e.animales, sin, e.inventario, e.recalcular)
        rechazo(caso.ejecutar(ComandoTratarAnimal(e.idPartida, a, nueva()))) shouldBe MotivoRechazo.ConsumoMedicoNoConfigurado
        salud(a) shouldBe "ENFERMO"
    }

    // ---- T4.6 satisfacción ----

    @Test
    fun `T4_6 la satisfaccion sube tras alimentar y tras curar y solo se recalcula en esos eventos`() {
        val sp = espacio()
        val a = animal(sp, alimentacion = 50)
        val enfermo = animal(sp, nombre = "Enfermo", enfermo = true)
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        val inicialA = satisfaccion(a)
        val inicialE = satisfaccion(enfermo)

        // Un rechazo no recalcula nada.
        fijarBambu(0)
        rechazo(alimentar(a))
        satisfaccion(a) shouldBe inicialA

        fijarBambu(30)
        exito(alimentar(a))
        (satisfaccion(a) > inicialA) shouldBe true

        exito(tratar(enfermo))
        (satisfaccion(enfermo) > inicialE) shouldBe true
        e.contar("MetricaSatisfaccion", "nivel = 'ESPACIO'") shouldBe 1
    }

    // ---- Avisos críticos ----

    @Test
    fun `alimentar a un animal desnutrido resuelve su alerta critica`() {
        val sp = espacio()
        val a = animal(sp, alimentacion = 0)
        e.bd.escribir("UPDATE Animal SET ciclos_desnutricion = 2 WHERE id_animal = ?", a)
        alerta(a)
        exito(alimentar(a))
        estadoAlerta(a) shouldBe "RESUELTA"
        e.bd.uno("SELECT ciclo_cierre FROM Notificacion WHERE id_entidad = ?", a) { it.entero(0) } shouldBe 0
    }

    @Test
    fun `una alerta que aun corresponde por enfermedad se conserva y se resuelve al tratar`() {
        val sp = espacio()
        val a = animal(sp, "erizo", "Doble", alimentacion = 0, enfermo = true)
        e.bd.escribir("UPDATE Animal SET ciclos_desnutricion = 1, ciclos_enfermedad = 1 WHERE id_animal = ?", a)
        alerta(a)
        exito(alimentar(a))
        estadoAlerta(a) shouldBe "PENDIENTE"
        exito(tratar(a))
        estadoAlerta(a) shouldBe "RESUELTA"
    }

    // ---- T4.7 atomicidad ----

    @Test
    fun `T4_7 un fallo a mitad de la operacion grupal revierte inventario y animales`() {
        val sp = espacio()
        val a = animal(sp, "erizo", "A", alimentacion = 50)
        val b = animal(sp, "erizo", "B", alimentacion = 30)
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        val antes = instantanea()
        var llamadas = 0
        val falla = object : CuidadoRepositorio by e.cuidado {
            override fun fijarAlimentacion(idPartida: String, idAnimal: String, nivel: Int) {
                if (++llamadas == 2) error("fallo simulado")
                e.cuidado.fijarAlimentacion(idPartida, idAnimal, nivel)
            }
        }
        val caso = AlimentarEspacio(e.ejecutor, e.zoo, e.animales, falla, e.inventario, e.recalcular)
        shouldThrow<IllegalStateException> { caso.ejecutar(ComandoAlimentarEspacio(e.idPartida, sp, null, nueva())) }
        llamadas shouldBe 2
        nivel(a) shouldBe 50
        nivel(b) shouldBe 30
        instantanea() shouldBe antes
        e.auditoriaDe("ALIMENTACION_GRUPAL").map { it.first } shouldContainExactly listOf("REVERTIDO")
    }

    @Test
    fun `T4_7 un fallo durante el tratamiento revierte insumos y salud`() {
        val sp = espacio()
        val a = animal(sp, enfermo = true)
        val antes = instantanea()
        val falla = object : CuidadoRepositorio by e.cuidado {
            override fun curar(idPartida: String, idAnimal: String) = error("fallo simulado")
        }
        val caso = TratarAnimal(e.ejecutor, e.animales, falla, e.inventario, e.recalcular)
        shouldThrow<IllegalStateException> { caso.ejecutar(ComandoTratarAnimal(e.idPartida, a, nueva())) }
        insumos() shouldBe 3
        salud(a) shouldBe "ENFERMO"
        instantanea() shouldBe antes
    }

    // ---- T4.8 idempotencia ----

    @Test
    fun `T4_8 repetir una operacion confirmada devuelve el resultado sin volver a consumir`() {
        val sp = espacio()
        val a = animal(sp, alimentacion = 50)
        val sol = "misma"
        val primera = alimentar(a, sol)
        val consumido = bambu()
        val segunda = alimentar(a, sol)
        (segunda as Resultado.Exito).repetido shouldBe true
        segunda.valor shouldBe exito(primera)
        bambu() shouldBe consumido
        nivel(a) shouldBe 60

        val b = animal(sp, nombre = "B", alimentacion = 20)
        val g = alimentarEspacio(sp, sol = "grupal")
        val gastado = bambu()
        (alimentarEspacio(sp, sol = "grupal") as Resultado.Exito).repetido shouldBe true
        bambu() shouldBe gastado
        nivel(b) shouldBe 100
        exito(g).animales shouldBe 2

        val enfermo = animal(sp, "leon", "Rey", enfermo = true)
        exito(tratar(enfermo, "cura"))
        val tras = insumos()
        (tratar(enfermo, "cura") as Resultado.Exito).repetido shouldBe true
        insumos() shouldBe tras
        rechazo(alimentar(b, sol)) shouldBe MotivoRechazo.ConflictoIdempotencia
    }
}
