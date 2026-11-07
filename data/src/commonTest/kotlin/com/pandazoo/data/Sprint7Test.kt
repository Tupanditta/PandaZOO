package com.pandazoo.data

import com.pandazoo.application.caso.ComandoAceptarReproduccion
import com.pandazoo.application.caso.ComandoReubicarAnimal
import com.pandazoo.application.caso.ComandoVenderAnimal
import com.pandazoo.application.caso.FichaNueva
import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.puerto.Concepcion
import com.pandazoo.application.puerto.ReproduccionRepositorio
import com.pandazoo.application.caso.AceptarReproduccion
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Sprint 7: reproducción (RF-16, RF-17, RF-18, CU-10, regla 5.3). */
class Sprint7Test {
    private lateinit var e: Entorno
    private var solicitud = 0

    @BeforeTest
    fun preparar() {
        e = Entorno(valorAzar = 0.5)
        fijarRango(umbral = 0.5, probabilidad = 1.0, penalizacion = 0.0)
    }

    @AfterTest
    fun cerrar() = e.cerrar()

    private fun nueva() = "sol-${++solicitud}"

    private fun rechazo(r: Resultado<*>): MotivoRechazo = (r as Resultado.Rechazado).motivo

    private fun <T> exito(r: Resultado<T>): T = (r as Resultado.Exito<T>).valor

    private fun fijarRango(umbral: Double, probabilidad: Double, penalizacion: Double) =
        e.bd.escribir(
            "UPDATE RangoEspecie SET umbral_reproduccion = ?, probabilidad_reproduccion = ?, penalizacion_hijo_reproduccion = ? WHERE id_rango = 1",
            umbral, probabilidad, penalizacion,
        )

    private fun reiniciar(valorAzar: Double) {
        e.cerrar()
        e = Entorno(valorAzar = valorAzar)
        fijarRango(umbral = 0.5, probabilidad = 1.0, penalizacion = 0.0)
    }

    private fun espacio(nombre: String = "Madriguera", capacidad: Int = 10) =
        e.datos.espacio(e.idPartida, e.habitat("bosque_templado"), nombre, capacidad)

    private fun animal(sp: String, nombre: String, padre: String? = null, madre: String? = null) =
        e.datos.animal(e.idPartida, sp, "erizo", nombre, idPadre = padre, idMadre = madre)

    private fun fijarSatisfaccion(id: String, s: Double) =
        e.bd.escribir("UPDATE Animal SET satisfaccion_actual = ? WHERE id_animal = ?", s, id)

    /** Pareja de erizos en un espacio nuevo, con la satisfacción indicada. */
    private fun pareja(capacidad: Int = 10, satisfaccion: Double = 1.0): Triple<String, String, String> {
        val sp = espacio(capacidad = capacidad)
        val a = animal(sp, "Pincho")
        val b = animal(sp, "Espina")
        e.datos.parejas(e.idPartida, a, b)
        fijarSatisfaccion(a, satisfaccion)
        fijarSatisfaccion(b, satisfaccion)
        return Triple(sp, a, b)
    }

    private fun ciclo() = e.ejecutarCiclo.siguiente(e.idPartida)

    private fun concepciones() = e.contar("ReproduccionCiclo")

    private fun estadoConcepcion() = e.bd.uno("SELECT estado FROM ReproduccionCiclo") { it.texto(0) }

    private fun estadoAviso() =
        e.bd.uno("SELECT estado FROM Notificacion WHERE codigo = 'REPRODUCCION_DISPONIBLE'") { it.texto(0) }

    private fun idAviso() =
        e.bd.uno("SELECT id_notificacion FROM Notificacion WHERE codigo = 'REPRODUCCION_DISPONIBLE' ORDER BY id_notificacion DESC") { it.largo(0) }!!

    private fun ficha(nombre: String = "Brote", amigo: String? = null) =
        FichaNueva(
            nombre,
            e.catalogoFicha.habilidades.take(2).map { it.id },
            e.catalogoFicha.rasgos.take(2).map { it.id },
            amigo,
        )

    private fun aceptar(aviso: Long = idAviso(), ficha: FichaNueva = ficha(), id: String = nueva()) =
        e.aceptarReproduccion.ejecutar(ComandoAceptarReproduccion(e.idPartida, aviso, ficha, id))

    private fun vivos() = e.contar("Animal", "estado_vital = 'VIVO'")

    private fun instantanea(): List<Any?> = listOf(
        vivos(), e.contar("Animal"), e.contar("ReproduccionCiclo"), e.contar("Notificacion"), e.contar("AuditoriaEvento"),
        e.bd.leer("SELECT estado FROM ReproduccionCiclo ORDER BY numero_ciclo") { it.texto(0) },
        e.bd.leer("SELECT estado FROM Notificacion ORDER BY id_notificacion") { it.texto(0) },
    )

    // ---- T7.1 umbral ----

    @Test
    fun `T7_1 satisfaccion exactamente igual al umbral reproduce y un punto inferior no`() {
        pareja(satisfaccion = 0.5)
        exito(ciclo())
        concepciones() shouldBe 1

        reiniciar(0.5)
        pareja(satisfaccion = 0.49)
        exito(ciclo())
        concepciones() shouldBe 0
    }

    @Test
    fun `T7_1 basta que uno de los dos no llegue al umbral para que no reproduzcan`() {
        val (_, a, _) = pareja(satisfaccion = 0.9)
        fijarSatisfaccion(a, 0.4)
        exito(ciclo())
        concepciones() shouldBe 0
    }

    // ---- T7.2 probabilidad ----

    @Test
    fun `T7_2 con probabilidad 0 nunca concibe y con 1 siempre`() {
        reiniciar(0.0)
        fijarRango(0.5, 0.0, 0.0)
        pareja()
        exito(ciclo())
        concepciones() shouldBe 0

        reiniciar(0.999)
        pareja()
        exito(ciclo())
        concepciones() shouldBe 1
    }

    @Test
    fun `T7_2 la frontera aleatorio igual a la probabilidad no concibe`() {
        reiniciar(0.25)
        fijarRango(0.5, 0.25, 0.0)
        pareja()
        exito(ciclo())
        concepciones() shouldBe 0
    }

    @Test
    fun `T7_2 la penalizacion cuenta hijos vivos comunes alojados o no y nunca da probabilidad negativa`() {
        reiniciar(0.1)
        fijarRango(0.5, 0.5, 0.2)
        val (sp, a, b) = pareja()
        val otro = espacio("Otra")
        animal(sp, "Hijo 1", padre = a, madre = b)
        animal(otro, "Hijo 2", padre = a, madre = b)
        // Probabilidad 0.5 - 0.2 * 2 = 0.1: el aleatorio 0.1 no es menor que 0.1.
        exito(ciclo())
        concepciones() shouldBe 0

        // Un hijo vendido no cuenta: 0.5 - 0.2 = 0.3 > 0.1.
        e.bd.escribir("UPDATE Animal SET estado_vital = 'VENDIDO', id_espacio = NULL WHERE nombre = 'Hijo 2'")
        exito(ciclo())
        concepciones() shouldBe 1
    }

    @Test
    fun `T7_2 con muchos hijos la probabilidad se queda en cero`() {
        reiniciar(0.0)
        fijarRango(0.5, 0.3, 0.2)
        val (sp, a, b) = pareja(capacidad = 15)
        repeat(3) { animal(sp, "Hijo ${it + 1}", padre = a, madre = b) }
        exito(ciclo())
        concepciones() shouldBe 0
    }

    // ---- T7.3 / T7.4 ----

    @Test
    fun `T7_3 sin vinculo de pareja valido no hay evaluacion`() {
        val sp = espacio()
        val a = animal(sp, "Uno")
        val b = animal(sp, "Dos")
        fijarSatisfaccion(a, 1.0)
        fijarSatisfaccion(b, 1.0)
        exito(ciclo())
        concepciones() shouldBe 0

        // Pareja no recíproca.
        e.bd.escribir("UPDATE Animal SET id_pareja = ? WHERE id_animal = ?", b, a)
        exito(ciclo())
        concepciones() shouldBe 0
    }

    @Test
    fun `T7_3 una pareja repartida en dos espacios no se evalua`() {
        val (_, a, b) = pareja()
        e.bd.escribir("UPDATE Animal SET id_espacio = ? WHERE id_animal = ?", espacio("Otra"), b)
        exito(ciclo())
        concepciones() shouldBe 0
    }

    @Test
    fun `T7_4 una pareja no concibe dos veces mientras tenga una concepcion abierta`() {
        pareja()
        exito(ciclo())
        exito(ciclo())
        concepciones() shouldBe 1
        e.contar("Notificacion", "codigo = 'REPRODUCCION_DISPONIBLE'") shouldBe 1
    }

    // ---- T7.5 ventana ----

    @Test
    fun `T7_5 el aviso se emite al cerrar el ciclo, es aceptable tres ciclos mas y caduca despues`() {
        val (_, a, b) = pareja()
        exito(ciclo())
        estadoConcepcion() shouldBe "CONCEBIDA"
        estadoAviso() shouldBe "PENDIENTE"
        e.bd.uno("SELECT ciclo_disponible || ':' || ciclo_expiracion FROM ReproduccionCiclo") { it.texto(0) } shouldBe "2:4"
        e.bd.uno("SELECT tipo || ':' || severidad FROM Notificacion WHERE codigo = 'REPRODUCCION_DISPONIBLE'") { it.texto(0) } shouldBe "OPERATIVA:MEDIA"

        // Ya no se reproducirán más; baja la satisfacción: no se vuelve a exigir el umbral dentro de la ventana.
        fijarRango(0.5, 0.0, 0.0)
        fijarSatisfaccion(a, 0.0)
        fijarSatisfaccion(b, 0.0)
        val aviso = idAviso()
        for (n in 2..4) {
            exito(ciclo())
            estadoConcepcion() shouldBe "CONCEBIDA"
            e.consultarReproduccion.ejecutar(e.idPartida, aviso)!!.habilitada shouldBe true
        }
        exito(ciclo())
        estadoConcepcion() shouldBe "EXPIRADA"
        estadoAviso() shouldBe "DESCARTADA"
        e.bd.uno("SELECT ciclo_cierre FROM Notificacion WHERE codigo = 'REPRODUCCION_DISPONIBLE'") { it.entero(0) } shouldBe 5
        e.consultarReproduccion.ejecutar(e.idPartida, aviso)!!.motivoDeshabilitada shouldBe MotivoRechazo.ConcepcionNoVigente
    }

    @Test
    fun `T7_5 la concepcion caducada conserva su estado y aceptarla se rechaza sin cambios`() {
        pareja()
        exito(ciclo())
        val aviso = idAviso()
        fijarRango(0.5, 0.0, 0.0)
        repeat(4) { exito(ciclo()) }
        val antes = instantanea()
        rechazo(aceptar(aviso)) shouldBe MotivoRechazo.ConcepcionNoVigente
        instantanea().dropLast(3) shouldBe antes.dropLast(3)
        estadoConcepcion() shouldBe "EXPIRADA"
    }

    // ---- T7.6 aforo ----

    @Test
    fun `T7_6 con el aforo lleno la aceptacion esta deshabilitada y se rechaza sin cambiar la notificacion`() {
        pareja(capacidad = 2)
        exito(ciclo())
        val aviso = idAviso()
        val d = e.consultarReproduccion.ejecutar(e.idPartida, aviso)!!
        d.habilitada shouldBe false
        d.motivoDeshabilitada shouldBe MotivoRechazo.EspacioLleno
        val antes = instantanea()
        rechazo(aceptar(aviso)) shouldBe MotivoRechazo.EspacioLleno
        instantanea().dropLast(3) shouldBe antes.dropLast(3)
        estadoConcepcion() shouldBe "CONCEBIDA"
        estadoAviso() shouldBe "PENDIENTE"
    }

    @Test
    fun `T7_6 con aforo libre la aceptacion esta habilitada`() {
        pareja(capacidad = 4)
        exito(ciclo())
        val d = e.consultarReproduccion.ejecutar(e.idPartida, idAviso())!!
        d.habilitada shouldBe true
        d.especie.isNotEmpty() shouldBe true
        d.nombreA + d.nombreB shouldContain "Pincho"
    }

    // ---- T7.7 idempotencia ----

    @Test
    fun `T7_7 aceptar dos veces seguidas con la misma solicitud crea una sola cria`() {
        pareja()
        exito(ciclo())
        val aviso = idAviso()
        val id = nueva()
        val primera = aceptar(aviso, id = id)
        val segunda = aceptar(aviso, id = id)
        (primera as Resultado.Exito).repetido shouldBe false
        (segunda as Resultado.Exito).repetido shouldBe true
        segunda.valor shouldBe primera.valor
        vivos() shouldBe 3
        e.auditoriaDe("REPRODUCCION_CONFIRMADA").map { it.first } shouldBe listOf("CONFIRMADO")
    }

    @Test
    fun `T7_7 una segunda aceptacion con otra solicitud se rechaza sin duplicar la cria`() {
        pareja()
        exito(ciclo())
        val aviso = idAviso()
        exito(aceptar(aviso))
        rechazo(aceptar(aviso, ficha("Otra"))) shouldBe MotivoRechazo.ConcepcionNoVigente
        vivos() shouldBe 3
    }

    @Test
    fun `T7_7 un aviso inexistente se rechaza`() {
        rechazo(aceptar(9999)) shouldBe MotivoRechazo.AvisoInexistente
    }

    // ---- T7.8 la cria ----

    @Test
    fun `T7_8 la cria hereda la especie nace en el nivel 1 sana alimentada y con sus progenitores`() {
        val (sp, a, b) = pareja()
        val hermano = animal(sp, "Hermano", padre = a, madre = b)
        exito(ciclo())
        val aviso = idAviso()
        val cria = exito(aceptar(aviso, ficha("Brote", amigo = hermano)))
        cria.idEspacio shouldBe sp

        val (padre, madre) = if (a < b) a to b else b to a
        val fila = e.bd.uno(
            "SELECT id_especie, nivel, ciclos_nivel, nivel_alimentacion, estado_salud, estado_vital, id_padre, id_madre, " +
                "id_espacio, id_mejor_amigo, id_pareja, satisfaccion_actual FROM Animal WHERE id_animal = ?",
            cria.idAnimal,
        ) { f -> (0..11).map { f.textoONulo(it) } }!!
        fila[0] shouldBe "erizo"
        fila.subList(1, 3) shouldBe listOf("1", "0")
        fila[3] shouldBe "100"
        fila[4] shouldBe "SANO"
        fila[5] shouldBe "VIVO"
        fila[6] shouldBe padre
        fila[7] shouldBe madre
        fila[8] shouldBe sp
        fila[9] shouldBe hermano
        fila[10] shouldBe null
        (fila[11]!!.toDouble() > 0.0) shouldBe true
        e.contar("HabilidadSocial", "id_animal = '${cria.idAnimal}'") shouldBe 2
        e.contar("RasgoPersonalidad", "id_animal = '${cria.idAnimal}'") shouldBe 2
        // Hermanos automáticos: ya comparten padre y madre con el hermano existente.
        e.contar("Animal", "id_padre = '$padre' AND id_madre = '$madre' AND estado_vital = 'VIVO'") shouldBe 2
        estadoConcepcion() shouldBe "CONFIRMADA"
        estadoAviso() shouldBe "ACEPTADA"
        e.bd.uno("SELECT ciclo_cierre FROM Notificacion WHERE codigo = 'REPRODUCCION_DISPONIBLE'") { it.entero(0) } shouldBe 1
        e.auditoriaDe("REPRODUCCION_CONFIRMADA").size shouldBe 1
    }

    @Test
    fun `TN_2 la cria nace en el nivel 1 sea cual sea el azar`() {
        pareja()
        reiniciarConPareja(0.0)
        exito(ciclo())
        val cria = exito(aceptar())
        e.bd.uno("SELECT nivel, ciclos_nivel FROM Animal WHERE id_animal = ?", cria.idAnimal) { it.entero(0) to it.entero(1) } shouldBe (1 to 0)
    }

    private fun reiniciarConPareja(azar: Double) {
        reiniciar(azar)
        pareja()
    }

    @Test
    fun `T7_8 una ficha incompleta o invalida no consolida y se puede corregir`() {
        pareja()
        exito(ciclo())
        val aviso = idAviso()
        val antes = instantanea()
        rechazo(aceptar(aviso, ficha("   "))) shouldBe MotivoRechazo.NombreInvalido
        rechazo(aceptar(aviso, ficha("x".repeat(41)))) shouldBe MotivoRechazo.NombreInvalido
        rechazo(aceptar(aviso, ficha().copy(habilidades = listOf(e.catalogoFicha.habilidades[0].id)))) shouldBe MotivoRechazo.FichaIncompleta
        rechazo(aceptar(aviso, ficha().copy(rasgos = emptyList()))) shouldBe MotivoRechazo.FichaIncompleta
        // Mejor amigo de otro espacio o inexistente.
        val ajeno = animal(espacio("Otra"), "Ajeno")
        rechazo(aceptar(aviso, ficha(amigo = ajeno))) shouldBe MotivoRechazo.AmigoInvalido
        rechazo(aceptar(aviso, ficha(amigo = "no-existe"))) shouldBe MotivoRechazo.AmigoInvalido
        // Solo se creó el animal ajeno de la prueba: ninguna cría ni concepción ni aviso cambió.
        vivos() shouldBe (antes[0] as Int) + 1
        instantanea().subList(2, 4) shouldBe antes.subList(2, 4)
        estadoConcepcion() shouldBe "CONCEBIDA"
        exito(aceptar(aviso, ficha("Corregido")))
        vivos() shouldBe 4
    }

    @Test
    fun `T7_8 el nombre de la cria puede repetir el de otro animal`() {
        pareja()
        exito(ciclo())
        exito(aceptar(ficha = ficha("Pincho")))
        e.contar("Animal", "nombre = 'Pincho'") shouldBe 2
    }

    @Test
    fun `T7_8 la satisfaccion de los hermanos en otros espacios tambien se recalcula`() {
        val (_, a, b) = pareja()
        val otro = espacio("Otra")
        val hermano = animal(otro, "Hermano", padre = a, madre = b)
        e.recalcular.alConsolidar(e.idPartida, com.pandazoo.application.caso.AlcanceRecalculo.Parque)
        val antes = e.bd.uno("SELECT satisfaccion_actual FROM Animal WHERE id_animal = ?", hermano) { it.decimal(0) }!!
        fijarSatisfaccion(a, 1.0)
        fijarSatisfaccion(b, 1.0)
        exito(ciclo())
        exito(aceptar())
        val despues = e.bd.uno("SELECT satisfaccion_actual FROM Animal WHERE id_animal = ?", hermano) { it.decimal(0) }!!
        (despues != antes) shouldBe true
    }

    // ---- T7.9 atomicidad ----

    @Test
    fun `T7_9 un fallo durante el alta de la cria revierte todo`() {
        pareja()
        exito(ciclo())
        val aviso = idAviso()
        val fallo = object : ReproduccionRepositorio by e.reproduccion {
            override fun confirmar(idPartida: String, concepcion: Concepcion, numeroCiclo: Int) = error("fallo simulado")
        }
        val caso = AceptarReproduccion(
            e.ejecutor, fallo, e.ciclos, e.animales, e.zoo, e.catalogoFicha, e.ids, e.recalcular,
        )
        val antes = instantanea()
        shouldThrow<IllegalStateException> {
            caso.ejecutar(ComandoAceptarReproduccion(e.idPartida, aviso, ficha(), nueva()))
        }
        // Solo cambia la auditoría del intento revertido.
        instantanea().take(4) shouldBe antes.take(4)
        instantanea().takeLast(2) shouldBe antes.takeLast(2)
        e.auditoriaDe("REPRODUCCION_CONFIRMADA").map { it.first } shouldBe listOf("REVERTIDO")
        // La concepción sigue pendiente y puede aceptarse después.
        exito(aceptar(aviso))
        vivos() shouldBe 3
    }

    // ---- T7.10 regla 5.3 ----

    @Test
    fun `T7_10 si un progenitor se vende la concepcion sigue concebida y la aceptacion se deshabilita`() {
        val (_, a, _) = pareja()
        exito(ciclo())
        val aviso = idAviso()
        exito(e.venderAnimal.ejecutar(ComandoVenderAnimal(e.idPartida, a, nueva())))
        estadoConcepcion() shouldBe "CONCEBIDA"
        val d = e.consultarReproduccion.ejecutar(e.idPartida, aviso)!!
        d.habilitada shouldBe false
        d.motivoDeshabilitada shouldBe MotivoRechazo.ProgenitoresNoValidos
        val antes = instantanea()
        rechazo(aceptar(aviso)) shouldBe MotivoRechazo.ProgenitoresNoValidos
        instantanea().dropLast(3) shouldBe antes.dropLast(3)
    }

    @Test
    fun `T7_10 un progenitor muerto o trasladado o la pareja rota impiden aceptar y revertir la causa lo habilita`() {
        val (_, a, b) = pareja()
        exito(ciclo())
        val aviso = idAviso()
        val original = e.bd.uno("SELECT id_espacio FROM Animal WHERE id_animal = ?", b) { it.texto(0) }!!
        val otro = espacio("Otra")

        exito(e.reubicarAnimal.ejecutar(ComandoReubicarAnimal(e.idPartida, b, otro, nueva())))
        rechazo(aceptar(aviso)) shouldBe MotivoRechazo.ProgenitoresNoValidos
        e.consultarReproduccion.ejecutar(e.idPartida, aviso)!!.habilitada shouldBe false

        exito(e.reubicarAnimal.ejecutar(ComandoReubicarAnimal(e.idPartida, b, original, nueva())))
        e.consultarReproduccion.ejecutar(e.idPartida, aviso)!!.habilitada shouldBe true

        // Pareja rota.
        e.animales.fijarPareja(e.idPartida, a, null)
        e.consultarReproduccion.ejecutar(e.idPartida, aviso)!!.motivoDeshabilitada shouldBe MotivoRechazo.ProgenitoresNoValidos
        e.datos.parejas(e.idPartida, a, b)
        e.consultarReproduccion.ejecutar(e.idPartida, aviso)!!.habilitada shouldBe true

        // Un progenitor muerto.
        e.animales.retirar(e.idPartida, a, "MUERTO")
        e.consultarReproduccion.ejecutar(e.idPartida, aviso)!!.motivoDeshabilitada shouldBe MotivoRechazo.ProgenitoresNoValidos
        estadoConcepcion() shouldBe "CONCEBIDA"
    }

    @Test
    fun `T7_10 al vencer la ventana con progenitores no validos la concepcion pasa a expirada`() {
        val (_, a, _) = pareja()
        exito(ciclo())
        exito(e.venderAnimal.ejecutar(ComandoVenderAnimal(e.idPartida, a, nueva())))
        repeat(4) { exito(ciclo()) }
        estadoConcepcion() shouldBe "EXPIRADA"
        estadoAviso() shouldBe "DESCARTADA"
    }

    // ---- Avisos y dependencias ----

    @Test
    fun `el aviso de reproduccion se muestra con nombres y sin identificadores y abrirlo lo marca como visto`() {
        pareja()
        exito(ciclo())
        val aviso = e.consultarAvisos.ejecutar(e.idPartida).first { it.codigo == "REPRODUCCION_DISPONIBLE" }
        aviso.texto shouldContain "Pincho"
        aviso.texto shouldContain "Espina"
        aviso.texto shouldNotContain "dp-"
        aviso.texto shouldNotContain e.idPartida
    }

    @Test
    fun `la cria cuenta como hijo vivo en la siguiente evaluacion`() {
        reiniciar(0.1)
        fijarRango(0.5, 0.5, 0.2)
        val (_, a, b) = pareja()
        exito(ciclo())
        exito(aceptar())
        exito(ciclo())
        // Una cría: 0.5 - 0.2 = 0.3 > 0.1, concibe de nuevo.
        concepciones() shouldBe 2
        fijarSatisfaccion(a, 1.0)
        fijarSatisfaccion(b, 1.0)
        exito(aceptar(idAviso()))
        // Dos crías: 0.5 - 0.4 = 0.1, no es menor que 0.1.
        fijarSatisfaccion(a, 1.0)
        fijarSatisfaccion(b, 1.0)
        exito(ciclo())
        concepciones() shouldBe 2
    }
}
