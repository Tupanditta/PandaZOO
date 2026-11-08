package com.pandazoo.data

import com.pandazoo.application.caso.AlcanceRecalculo
import com.pandazoo.application.caso.ComandoAdquirirEspacio
import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.puerto.EstadoTienda
import com.pandazoo.domain.modelo.Seccion
import com.pandazoo.domain.prestigio.EstadoPrestigio
import com.pandazoo.domain.prestigio.MaximosHistoricos
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.measureTime

/** Sprint 6: prestigio (RF-35, RF-36), desbloqueos permanentes (RES-07), avisos informativos (RF-43) y CU-19. */
class Sprint6Test {
    private lateinit var e: Entorno
    private var solicitud = 0

    @BeforeTest
    fun preparar() {
        e = Entorno(valorAzar = 0.5)
    }

    @AfterTest
    fun cerrar() = e.cerrar()

    private fun nueva() = "sol-${++solicitud}"

    private fun espacio(nombre: String = "Madriguera", capacidad: Int = 20) =
        e.datos.espacio(e.idPartida, e.habitat("bosque_templado"), nombre, capacidad)

    /** [n] erizos con la alimentación indicada en un espacio nuevo, sin recalcular. */
    private fun animales(n: Int, alimentacion: Int = 100, enEspacio: String = espacio("Madriguera ${++solicitud}")): List<String> =
        List(n) { e.datos.animal(e.idPartida, enEspacio, "erizo", "Pincho ${++solicitud}", alimentacion) }

    private fun evaluar() = e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)

    private fun estado(): EstadoPrestigio =
        e.bd.uno(
            "SELECT prestigio_actual, prestigio_maximo_nivel, animales_vivos_maximos, satisfaccion_global_maxima FROM Partida",
        ) {
            EstadoPrestigio(it.entero(0), if (it.esNulo(1)) null else MaximosHistoricos(it.entero(1), it.entero(2), it.decimal(3)))
        }!!

    private fun satisfaccionGlobal() = e.consultarMonitor.ejecutar(e.idPartida).parque.let { (it as Seccion.Disponible).valor }

    private fun avisos(codigo: String) =
        e.bd.leer("SELECT id_entidad, tipo, severidad, estado, numero_ciclo FROM Notificacion WHERE codigo = ? ORDER BY id_notificacion", codigo) {
            listOf(it.texto(0), it.texto(1), it.texto(2), it.texto(3), it.entero(4).toString())
        }

    @Test
    fun `T6_1 con animales suficientes y satisfaccion suficiente sube de nivel, con una sola condicion no`() {
        // Un animal: no llega al umbral de animales del nivel 1 (2).
        animales(1)
        evaluar()
        estado().actual shouldBe 0

        // Dos animales bien alimentados cumplen las dos condiciones del nivel 1.
        animales(1)
        evaluar()
        (satisfaccionGlobal() >= 0.30) shouldBe true
        estado().actual shouldBe 1
    }

    @Test
    fun `T6_1 animales suficientes pero satisfaccion por debajo del umbral no dan nivel`() {
        animales(2, alimentacion = 0)
        evaluar()
        (satisfaccionGlobal() < 0.30) shouldBe true
        estado().actual shouldBe 0
    }

    @Test
    fun `T6_3 sin animales el prestigio es cero y no hay maximos`() {
        evaluar()
        estado() shouldBe EstadoPrestigio(0, null)
        e.contar("Notificacion", "tipo = 'INFORMATIVA'") shouldBe 0
    }

    @Test
    fun `T6_4 los maximos son independientes y el prestigio no baja`() {
        val a = animales(5)
        evaluar()
        val alto = estado()
        alto.actual shouldBe 7
        alto.maximos!!.nivel shouldBe 7
        alto.maximos!!.animalesVivos shouldBe 5

        // Mueren cuatro: las estadísticas bajan, pero el prestigio y los máximos se conservan.
        a.drop(1).forEach { e.animales.retirar(e.idPartida, it, "MUERTO") }
        evaluar()
        val bajo = estado()
        bajo.actual shouldBe 7
        bajo.maximos shouldBe alto.maximos

        // Un solo animal con más satisfacción que antes puede subir solo el máximo de satisfacción.
        e.bd.escribir("UPDATE Animal SET nivel_alimentacion = 100, estado_salud = 'SANO'")
        evaluar()
        estado().maximos!!.nivel shouldBe 7
        estado().maximos!!.animalesVivos shouldBe 5
    }

    @Test
    fun `T6_4 una partida nueva no tiene maximos`() {
        estado() shouldBe EstadoPrestigio(0, null)
    }

    @Test
    fun `T6_5 un desbloqueo es permanente y el prestigio no desciende aunque mueran los animales`() {
        val a = animales(3)
        evaluar()
        estado().actual shouldBe 3
        e.animales.especieTienda(e.idPartida, "suricata")!!.estado shouldBe EstadoTienda.COMERCIAL

        a.forEach { e.animales.retirar(e.idPartida, it, "MUERTO") }
        evaluar()
        estado().actual shouldBe 3
        e.animales.especieTienda(e.idPartida, "suricata")!!.estado shouldBe EstadoTienda.COMERCIAL
        e.contar("DesbloqueoEspecie", "id_especie = 'suricata' AND origen = 'PRESTIGIO'") shouldBe 1
    }

    @Test
    fun `T6_6 subir varios niveles en un recalculo emite un solo aviso del mas alto y no se repite`() {
        val a = animales(5)
        evaluar()
        estado().actual shouldBe 7
        avisos("PRESTIGIO_ASCENSO") shouldContainExactly listOf(listOf("7", "INFORMATIVA", "BAJA", "PENDIENTE", "0"))
        e.auditoriaDe("PRESTIGIO_ASCENSO") shouldHaveSize 1

        // Baja y recupera un nivel ya alcanzado: no hay aviso nuevo.
        a.drop(1).forEach { e.animales.retirar(e.idPartida, it, "MUERTO") }
        evaluar()
        animales(4)
        evaluar()
        estado().actual shouldBe 7
        avisos("PRESTIGIO_ASCENSO") shouldHaveSize 1

        // Un nivel superior sí se avisa.
        animales(1)
        evaluar()
        estado().actual shouldBe 9
        avisos("PRESTIGIO_ASCENSO").map { it[0] } shouldContainExactly listOf("7", "9")
    }

    @Test
    fun `T6_7 las especies y habitats desbloqueados avisan una sola vez como informativos`() {
        animales(3)
        evaluar()
        evaluar()
        evaluar()
        // Niveles 1 a 3: el hábitat Campos de Esmeril (nivel 1) y suricata (nivel 1), cebra (2) y mono (3).
        avisos("ESPECIE_DESBLOQUEADA").map { it[0] } shouldContainExactly listOf("suricata", "cebra", "mono")
        avisos("ESPECIE_DESBLOQUEADA").all { it[1] == "INFORMATIVA" && it[2] == "BAJA" && it[3] == "PENDIENTE" } shouldBe true
        avisos("HABITAT_DESBLOQUEADO") shouldHaveSize 1
        e.contar("DesbloqueoHabitat", "id_habitat = '${e.habitat("campos_de_esmeril")}'") shouldBe 1
        e.auditoriaDe("ESPECIE_DESBLOQUEADA") shouldHaveSize 3
        // Los desbloqueos iniciales (nivel 0) no generan aviso.
        avisos("ESPECIE_DESBLOQUEADA").none { it[0] == "erizo" } shouldBe true
    }

    @Test
    fun `T6_7 un salto de varios niveles desbloquea todo lo de los niveles intermedios`() {
        animales(5)
        evaluar()
        // Niveles 1 a 7: 2 especies en cada uno de los niveles 2, 4 y 7 y el hábitat del nivel 1.
        e.contar("DesbloqueoEspecie", "origen = 'PRESTIGIO'") shouldBe 6
        e.contar("DesbloqueoHabitat") shouldBe 3
        avisos("ESPECIE_DESBLOQUEADA") shouldHaveSize 6
    }

    @Test
    fun `T6_8 tras desbloquear un habitat se puede crear un espacio en el`() {
        val campos = e.habitat("campos_de_esmeril")
        val antes = e.adquirirEspacio.ejecutar(ComandoAdquirirEspacio(e.idPartida, campos, "Sabana", 8, nueva()))
        (antes as Resultado.Rechazado).motivo shouldBe MotivoRechazo.HabitatNoDisponible

        animales(2)
        evaluar()
        val despues = e.adquirirEspacio.ejecutar(ComandoAdquirirEspacio(e.idPartida, campos, "Sabana", 8, nueva()))
        despues.shouldBeInstanceOf<Resultado.Exito<*>>()
    }

    @Test
    fun `T6_9 la verificacion de prestigio sobre la carga de referencia tarda menos de 150 ms`() {
        e.datos.cargaReferencia(e.idPartida)!!.animales shouldBe 500
        evaluar()
        e.recalcularPrestigio.alConsolidar(e.idPartida) // calentamiento
        val duracion = measureTime { e.recalcularPrestigio.alConsolidar(e.idPartida) }
        duracion.inWholeMilliseconds.toInt() shouldBeLessThan 150
    }

    @Test
    fun `T6_10 consultar el panel no recalcula ni cambia nada`() {
        animales(2)
        evaluar()
        val invocaciones = e.recalcularPrestigio.invocaciones
        val satisfaccion = e.recalcular.invocaciones
        val auditorias = e.contar("AuditoriaEvento")
        repeat(3) { e.consultarPrestigio.ejecutar(e.idPartida) }
        e.recalcularPrestigio.invocaciones shouldBe invocaciones
        e.recalcular.invocaciones shouldBe satisfaccion
        e.contar("AuditoriaEvento") shouldBe auditorias
    }

    @Test
    fun `CU-19 el panel muestra nivel actual, maximos, censo y satisfaccion con el siguiente nivel`() {
        animales(2)
        evaluar()
        val p = e.consultarPrestigio.ejecutar(e.idPartida)
        (p.actual as Seccion.Disponible).valor shouldBe 1
        val m = (p.maximos as Seccion.Disponible).valor!!
        m.nivel shouldBe 1
        m.animalesVivos shouldBe 2
        (p.animalesVivos as Seccion.Disponible).valor shouldBe 2
        (p.satisfaccionGlobal as Seccion.Disponible).valor shouldBe (satisfaccionGlobal() plusOrMinus 1e-12)
        p.siguiente!!.nivel shouldBe 2
        p.siguiente!!.animalesVivos shouldBe 3
    }

    @Test
    fun `CU-19 una partida nueva muestra nivel 0 y sin maximos`() {
        val p = e.consultarPrestigio.ejecutar(e.idPartida)
        (p.actual as Seccion.Disponible).valor shouldBe 0
        (p.maximos as Seccion.Disponible).valor shouldBe null
        p.siguiente!!.nivel shouldBe 1
    }

    @Test
    fun `CU-19 sin metrica global la satisfaccion es no disponible y no se calcula`() {
        e.bd.escribir("DELETE FROM MetricaSatisfaccion WHERE nivel = 'PARTIDA'")
        val invocaciones = e.recalcular.invocaciones
        val p = e.consultarPrestigio.ejecutar(e.idPartida)
        p.satisfaccionGlobal.shouldBeInstanceOf<Seccion.NoDisponible>()
        p.actual.shouldBeInstanceOf<Seccion.Disponible<*>>()
        e.recalcular.invocaciones shouldBe invocaciones
        e.contar("MetricaSatisfaccion", "nivel = 'PARTIDA'") shouldBe 0
    }

    @Test
    fun `CU-19 sin partida todo es no disponible`() {
        val p = e.consultarPrestigio.ejecutar("no-existe")
        p.actual.shouldBeInstanceOf<Seccion.NoDisponible>()
        p.maximos.shouldBeInstanceOf<Seccion.NoDisponible>()
        p.animalesVivos.shouldBeInstanceOf<Seccion.NoDisponible>()
        p.satisfaccionGlobal.shouldBeInstanceOf<Seccion.NoDisponible>()
    }

    @Test
    fun `el ciclo evalua el prestigio y registra su numero de ciclo en los avisos`() {
        val sp = espacio("Cueva")
        animales(1, enEspacio = sp)
        e.datos.animal(e.idPartida, sp, "erizo", "Segundo")
        e.ejecutarCiclo.siguiente(e.idPartida) // ciclo 1: el censo de 2 animales sube al nivel 1
        estado().actual shouldBe 1
        avisos("PRESTIGIO_ASCENSO").single()[4] shouldBe "1"
        avisos("ESPECIE_DESBLOQUEADA").all { it[4] == "1" } shouldBe true
    }

    @Test
    fun `un fallo posterior en la transaccion revierte el prestigio y los desbloqueos`() {
        animales(2)
        try {
            e.bd.ejecutar {
                evaluar()
                estado().actual shouldBe 1
                error("fallo simulado")
            }
        } catch (_: IllegalStateException) {
        }
        estado() shouldBe EstadoPrestigio(0, null)
        e.contar("DesbloqueoEspecie", "origen = 'PRESTIGIO'") shouldBe 0
        e.contar("Notificacion", "tipo = 'INFORMATIVA'") shouldBe 0
    }
}
