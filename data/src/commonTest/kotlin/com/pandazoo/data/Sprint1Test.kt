package com.pandazoo.data

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import com.pandazoo.application.caso.AdquirirInsumos
import com.pandazoo.application.caso.ComandoAdquirirEspacio
import com.pandazoo.application.caso.ComandoDarDeBajaEspacio
import com.pandazoo.application.caso.ComandoAdquirirInsumos
import com.pandazoo.application.caso.ComandoConfigurarEspacio
import com.pandazoo.application.caso.CompraRealizada
import com.pandazoo.application.caso.ConsultarInventarioFinanzas
import com.pandazoo.application.caso.EspacioAdquirido
import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.puerto.EconomiaRepositorio
import com.pandazoo.domain.modelo.EstadoEntidad
import com.pandazoo.domain.modelo.Recurso
import com.pandazoo.domain.modelo.Seccion
import com.pandazoo.domain.modelo.TipoRecurso
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import com.pandazoo.domain.economia.Dinero
import kotlin.random.Random

class Sprint1Test {
    private fun d(texto: String): Dinero = Dinero.desdeDecimal(texto.toDouble())

    private lateinit var e: Entorno
    private val bambu = AdquirirInsumos.bambu()
    private val insumo = Recurso(TipoRecurso.INSUMO, "insumo_medico")
    private var solicitud = 0

    @BeforeTest
    fun preparar() {
        e = Entorno()
    }

    @AfterTest
    fun cerrar() = e.cerrar()

    private fun nueva() = "sol-${++solicitud}"

    private fun comprar(r: Recurso, cantidad: Int, id: String = nueva()) =
        e.adquirirInsumos.ejecutar(ComandoAdquirirInsumos(e.idPartida, r, cantidad, id))

    private fun comprarEspacio(habitat: String, nombre: String, capacidad: Int, id: String = nueva()) =
        e.adquirirEspacio.ejecutar(ComandoAdquirirEspacio(e.idPartida, e.habitat(habitat), nombre, capacidad, id))

    private fun configurar(idEspacio: String, nombre: String?, habitat: String?, id: String = nueva()) =
        e.configurarEspacio.ejecutar(
            ComandoConfigurarEspacio(e.idPartida, idEspacio, nombre, habitat?.let { e.habitat(it) }, id),
        )

    private fun darFondos(importe: String) =
        e.bd.escribir("UPDATE Partida SET fondos = ? WHERE id_partida = ?", d(importe).paraBd(), e.idPartida)

    private fun rechazo(r: Resultado<*>): MotivoRechazo = (r as Resultado.Rechazado).motivo

    private fun <T> exito(r: Resultado<T>): T = (r as Resultado.Exito<T>).valor

    private fun cantidad(r: Recurso) = e.inventario.cantidad(e.idPartida, r)

    private fun instantanea(): List<Any?> = listOf(
        e.fondos(), cantidad(bambu), cantidad(insumo), e.contar("Espacio"), e.contar("MovimientoEconomico"),
        e.bd.leer("SELECT id_espacio, nombre, id_habitat FROM Espacio ORDER BY id_espacio") {
            listOf(it.texto(0), it.texto(1), it.texto(2))
        },
    )

    // ---- T1.1 ----
    @Test
    fun `T1_1 compra de bambu actualiza saldo e inventario en una transaccion`() {
        val r = exito(comprar(bambu, 10))
        r shouldBe CompraRealizada(d("5.00"), d("1995.00"), 40)
        e.fondos() shouldBe d("1995.00")
        cantidad(bambu) shouldBe 40
        e.contar("MovimientoEconomico", "tipo = 'COMPRA' AND importe = 5.0 AND origen = 'CU-07'") shouldBe 1
    }

    @Test
    fun `T1_1 compra de insumos medicos usa el precio por unidad`() {
        exito(comprar(insumo, 7)) shouldBe CompraRealizada(d("70.00"), d("1930.00"), 10)
    }

    @Test
    fun `T1_1 cantidad cero o negativa se rechaza sin cambios`() {
        val antes = instantanea()
        rechazo(comprar(bambu, 0)) shouldBe MotivoRechazo.CantidadInvalida
        rechazo(comprar(bambu, -3)) shouldBe MotivoRechazo.CantidadInvalida
        instantanea() shouldBe antes
    }

    @Test
    fun `T1_1 recurso fuera del catalogo sin precio o partida inexistente`() {
        val antes = instantanea()
        rechazo(comprar(Recurso(TipoRecurso.ALIMENTO, "cebo"), 1)) shouldBe MotivoRechazo.RecursoNoElegible
        e.bd.escribir("DELETE FROM CosteAlimento")
        rechazo(comprar(bambu, 1)) shouldBe MotivoRechazo.PrecioNoConfigurado
        instantanea() shouldBe antes
        val otra = e.adquirirInsumos.ejecutar(ComandoAdquirirInsumos("no-existe", insumo, 1, nueva()))
        rechazo(otra) shouldBe MotivoRechazo.PartidaInexistente
    }

    // ---- T1.2 ----
    @Test
    fun `T1_2 saldo insuficiente no cambia nada e informa el motivo exacto`() {
        val antes = instantanea()
        val motivo = rechazo(comprar(insumo, 201))
        motivo shouldBe MotivoRechazo.SaldoInsuficiente(d("2010.00"), d("2000.00"))
        instantanea() shouldBe antes
    }

    @Test
    fun `T1_2 limite exacto saldo igual al coste se acepta`() {
        exito(comprar(insumo, 200)).saldo shouldBe d("0.00")
        e.fondos() shouldBe d("0.00")
        rechazo(comprar(bambu, 1)).shouldBeInstanceOf<MotivoRechazo.SaldoInsuficiente>()
    }

    // ---- T1.3 ----
    @Test
    fun `T1_3 ninguna secuencia aleatoria deja saldos ni inventarios negativos`() {
        for (semilla in 1..6) {
            val azar = Random(semilla)
            val e2 = Entorno()
            try {
                var n = 0
                repeat(60) {
                    val id = "s$semilla-${n++}"
                    when (azar.nextInt(4)) {
                        0 -> e2.adquirirInsumos.ejecutar(ComandoAdquirirInsumos(e2.idPartida, bambu, azar.nextInt(-5, 900), id))
                        1 -> e2.adquirirInsumos.ejecutar(ComandoAdquirirInsumos(e2.idPartida, insumo, azar.nextInt(-5, 60), id))
                        2 -> e2.adquirirEspacio.ejecutar(
                            ComandoAdquirirEspacio(
                                e2.idPartida,
                                e2.habitat(listOf("bosque_templado", "humedal_agua_dulce", "campos_de_esmeril").random(azar)),
                                "E${azar.nextInt(6)}",
                                listOf(1, 2, 4, 6, 8, 10, 15, 20).random(azar),
                                id,
                            ),
                        )
                        else -> {
                            val ids = e2.bd.leer("SELECT id_espacio FROM Espacio") { it.texto(0) }
                            if (ids.isNotEmpty()) {
                                e2.configurarEspacio.ejecutar(
                                    ComandoConfigurarEspacio(e2.idPartida, ids.random(azar), "N${azar.nextInt(4)}", null, id),
                                )
                            }
                        }
                    }
                    (e2.fondos() >= Dinero.CERO) shouldBe true
                    e2.inventario.existencias(e2.idPartida).all { it.cantidad >= 0 } shouldBe true
                    val gastado = e2.bd.uno("SELECT COALESCE(SUM(importe), 0) FROM MovimientoEconomico WHERE tipo = 'COMPRA'") {
                        it.decimal(0)
                    }!!
                    // Conservación del dinero: fondos + total gastado = fondos iniciales.
                    (Dinero.desdeBd(gastado) + e2.fondos()) shouldBe d("2000.00")
                }
            } finally {
                e2.cerrar()
            }
        }
    }

    // ---- T1.4 ----
    @Test
    fun `T1_4 cada capacidad de la tabla se acepta con su coste`() {
        darFondos("20000.00")
        // El primer espacio de 6 es gratis; los demás cuestan lo que indica la tabla.
        val tabla = mapOf(6 to "0.00", 8 to "415.00", 10 to "520.00", 15 to "715.00", 20 to "910.00", 25 to "1105.00", 30 to "1300.00")
        tabla.forEach { (capacidad, coste) ->
            val r = exito(comprarEspacio("bosque_templado", "Zona $capacidad", capacidad))
            r.coste shouldBe d(coste)
        }
        e.contar("Espacio") shouldBe 7
        e.bd.leer("SELECT capacidad_max FROM Espacio ORDER BY capacidad_max") { it.entero(0) }
            .shouldContainExactly(6, 8, 10, 15, 20, 25, 30)
        exito(comprarEspacio("bosque_templado", "Segundo de seis", 6)).coste shouldBe d("325.00")
    }

    @Test
    fun `el primer espacio de 6 es gratis una sola vez aunque se de de baja`() {
        val saldo = e.fondos()
        val primero = exito(comprarEspacio("bosque_templado", "Primero", 6))
        primero.coste shouldBe Dinero.CERO
        e.fondos() shouldBe saldo
        e.consultarTienda.capacidades(e.idPartida).first { it.capacidad == 6 }.coste shouldBe d("325.00")
        e.darDeBajaEspacio.ejecutar(ComandoDarDeBajaEspacio(e.idPartida, primero.idEspacio, nueva()))
        exito(comprarEspacio("bosque_templado", "Otro", 6)).coste shouldBe d("325.00")
        e.fondos() shouldBe saldo - d("325.00")
    }

    @Test
    fun `al vender un espacio vacio se recupera todo lo que costo y el gratuito no devuelve nada`() {
        val gratis = exito(comprarEspacio("bosque_templado", "Gratis", 6))
        val pagado = exito(comprarEspacio("bosque_templado", "Pagado", 8))
        pagado.coste shouldBe d("415.00")
        val saldo = e.fondos()
        val libre = e.darDeBajaEspacio.ejecutar(ComandoDarDeBajaEspacio(e.idPartida, gratis.idEspacio, nueva()))
        (libre as Resultado.Exito).valor.reembolso shouldBe Dinero.CERO
        e.fondos() shouldBe saldo
        val vendido = e.darDeBajaEspacio.ejecutar(ComandoDarDeBajaEspacio(e.idPartida, pagado.idEspacio, nueva()))
        (vendido as Resultado.Exito).valor.reembolso shouldBe d("415.00")
        e.fondos() shouldBe saldo + d("415.00")
    }

    @Test
    fun `solo el espacio de 6 puede ser gratis y otras capacidades cuestan siempre`() {
        e.consultarTienda.capacidades(e.idPartida).map { it.capacidad to it.coste.toString() }.shouldContainExactly(
            6 to "0.00", 8 to "415.00", 10 to "520.00", 15 to "715.00", 20 to "910.00", 25 to "1105.00", 30 to "1300.00",
        )
        exito(comprarEspacio("bosque_templado", "Ocho", 8)).coste shouldBe d("415.00")
        e.consultarTienda.capacidades(e.idPartida).first { it.capacidad == 6 }.coste shouldBe Dinero.CERO
    }

    @Test
    fun `no existen espacios de 2 ni de 4 animales`() {
        listOf(2, 4).forEach { rechazo(comprarEspacio("bosque_templado", "Chico", it)) shouldBe MotivoRechazo.CapacidadNoPermitida }
    }

    @Test
    fun `T1_4 capacidad fuera de la tabla se rechaza`() {
        val antes = instantanea()
        listOf(0, 1, 3, 5, 7, 12, 35, -4).forEach {
            rechazo(comprarEspacio("bosque_templado", "Zona", it)) shouldBe MotivoRechazo.CapacidadNoPermitida
        }
        instantanea() shouldBe antes
    }

    @Test
    fun `T1_4 nombre duplicado en el mismo habitat se rechaza y en otro habitat se acepta`() {
        darFondos("5000.00")
        exito(comprarEspacio("bosque_templado", "Pradera", 8))
        rechazo(comprarEspacio("bosque_templado", "Pradera", 8)) shouldBe MotivoRechazo.DenominacionDuplicada
        rechazo(comprarEspacio("bosque_templado", "  pradera ", 10)) shouldBe MotivoRechazo.DenominacionDuplicada
        exito(comprarEspacio("humedal_agua_dulce", "Pradera", 8))
        e.contar("Espacio") shouldBe 2
    }

    @Test
    fun `T1_4 denominacion vacia o demasiado larga se rechaza`() {
        rechazo(comprarEspacio("bosque_templado", "   ", 8)) shouldBe MotivoRechazo.DenominacionInvalida
        rechazo(comprarEspacio("bosque_templado", "x".repeat(41), 8)) shouldBe MotivoRechazo.DenominacionInvalida
        e.contar("Espacio") shouldBe 0
    }

    @Test
    fun `T1_4 espacio con saldo insuficiente no se crea ni se cobra`() {
        darFondos("99.99")
        rechazo(comprarEspacio("bosque_templado", "Pradera", 8)) shouldBe
            MotivoRechazo.SaldoInsuficiente(d("415.00"), d("99.99"))
        e.contar("Espacio") shouldBe 0
        e.fondos() shouldBe d("99.99")
    }

    // ---- T1.5 ----
    @Test
    fun `T1_5 espacio en habitat bloqueado o inexistente se rechaza`() {
        val antes = instantanea()
        rechazo(comprarEspacio("campos_de_esmeril", "Pradera", 4)) shouldBe MotivoRechazo.HabitatNoDisponible
        rechazo(
            e.adquirirEspacio.ejecutar(ComandoAdquirirEspacio(e.idPartida, "inexistente", "Pradera", 4, nueva())),
        ) shouldBe MotivoRechazo.HabitatNoDisponible
        instantanea() shouldBe antes
    }

    @Test
    fun `T1_5 un habitat inactivo no admite espacios`() {
        e.bd.escribir("UPDATE Habitat SET estado = 'INACTIVO' WHERE id_habitat = ?", e.habitat("bosque_templado"))
        rechazo(comprarEspacio("bosque_templado", "Pradera", 4)) shouldBe MotivoRechazo.HabitatNoDisponible
    }

    // ---- T1.6 ----
    private fun espacioNuevo(nombre: String = "Pradera", habitat: String = "bosque_templado", capacidad: Int = 6): String {
        darFondos("5000.00")
        return exito(comprarEspacio(habitat, nombre, capacidad)).idEspacio
    }

    private fun capacidadDe(id: String) = e.zoo.espacio(e.idPartida, id)!!.capacidadMax

    @Test
    fun `T1_6 renombrar conserva hábitat y capacidad`() {
        val id = espacioNuevo()
        val r = exito(configurar(id, "Sabana", null))
        r.nombre shouldBe "Sabana"
        val esp = e.zoo.espacio(e.idPartida, id)!!
        esp.nombre shouldBe "Sabana"
        esp.idHabitat shouldBe e.habitat("bosque_templado")
        esp.capacidadMax shouldBe 6
    }

    @Test
    fun `T1_6 cambiar de habitat y ambos a la vez`() {
        val id = espacioNuevo()
        exito(configurar(id, null, "humedal_agua_dulce"))
        e.zoo.espacio(e.idPartida, id)!!.idHabitat shouldBe e.habitat("humedal_agua_dulce")
        exito(configurar(id, "Charca", "bosque_templado"))
        val esp = e.zoo.espacio(e.idPartida, id)!!
        esp.nombre shouldBe "Charca"
        esp.idHabitat shouldBe e.habitat("bosque_templado")
        capacidadDe(id) shouldBe 6
    }

    @Test
    fun `T1_6 habitat de destino bloqueado se rechaza sin cambios`() {
        val id = espacioNuevo()
        val antes = instantanea()
        rechazo(configurar(id, null, "campos_de_esmeril")) shouldBe MotivoRechazo.HabitatNoDisponible
        rechazo(configurar(id, "Otro", "campos_de_esmeril")) shouldBe MotivoRechazo.HabitatNoDisponible
        instantanea() shouldBe antes
    }

    @Test
    fun `T1_6 si falla una validacion no se aplica parcialmente la configuracion`() {
        val a = espacioNuevo("Pradera", "bosque_templado")
        espacioNuevo("Charca", "humedal_agua_dulce")
        val antes = instantanea()
        // Cambio de hábitat válido más una denominación que choca en el destino.
        rechazo(configurar(a, "Charca", "humedal_agua_dulce")) shouldBe MotivoRechazo.DenominacionDuplicada
        instantanea() shouldBe antes
        e.zoo.espacio(e.idPartida, a)!!.nombre shouldBe "Pradera"
    }

    @Test
    fun `T1_6 mover un espacio a un habitat donde su nombre ya existe se rechaza`() {
        val a = espacioNuevo("Pradera", "bosque_templado")
        espacioNuevo("Pradera", "humedal_agua_dulce")
        rechazo(configurar(a, null, "humedal_agua_dulce")) shouldBe MotivoRechazo.DenominacionDuplicada
    }

    @Test
    fun `T1_6 sin cambios espacio inexistente o inactivo se rechazan`() {
        val id = espacioNuevo()
        rechazo(configurar(id, "Pradera", null)) shouldBe MotivoRechazo.SinCambios
        rechazo(configurar(id, null, "bosque_templado")) shouldBe MotivoRechazo.SinCambios
        rechazo(configurar("no-existe", "X", null)) shouldBe MotivoRechazo.EspacioInexistente
        rechazo(configurar(id, "   ", null)) shouldBe MotivoRechazo.DenominacionInvalida
        e.bd.escribir("UPDATE Espacio SET estado = 'INACTIVO' WHERE id_espacio = ?", id)
        rechazo(configurar(id, "Otro", null)) shouldBe MotivoRechazo.EspacioInactivo
    }

    @Test
    fun `T1_6 la capacidad no se puede cambiar por esta via y el espacio mantiene su aforo`() {
        val id = espacioNuevo(capacidad = 15)
        exito(configurar(id, "Nuevo", "humedal_agua_dulce"))
        capacidadDe(id) shouldBe 15
        e.zoo.espacio(e.idPartida, id)!!.estado shouldBe EstadoEntidad.ACTIVO
    }

    // ---- T1.7 ----
    @Test
    fun `T1_7 reintentar la misma compra de recursos no cobra dos veces`() {
        val primera = comprar(bambu, 10, "fija")
        val segunda = comprar(bambu, 10, "fija")
        e.fondos() shouldBe d("1995.00")
        cantidad(bambu) shouldBe 40
        e.contar("MovimientoEconomico") shouldBe 1
        (segunda as Resultado.Exito).repetido shouldBe true
        segunda.valor shouldBe exito(primera)
    }

    @Test
    fun `T1_7 reintentar la misma compra de espacio no crea otro espacio`() {
        val primera = comprarEspacio("bosque_templado", "Pradera", 8, "fija")
        val segunda = comprarEspacio("bosque_templado", "Pradera", 8, "fija")
        e.contar("Espacio") shouldBe 1
        e.fondos() shouldBe d("1585.00")
        exito(segunda) shouldBe exito(primera)
        (segunda as Resultado.Exito).repetido shouldBe true
    }

    @Test
    fun `T1_7 la misma solicitud con otro contenido es un conflicto`() {
        comprar(bambu, 10, "fija")
        rechazo(comprar(bambu, 99, "fija")) shouldBe MotivoRechazo.ConflictoIdempotencia
        e.fondos() shouldBe d("1995.00")
    }

    @Test
    fun `T1_7 reintentar una configuracion confirmada devuelve el mismo resultado`() {
        val id = espacioNuevo()
        val a = configurar(id, "Sabana", null, "cfg")
        val b = configurar(id, "Sabana", null, "cfg")
        exito(b) shouldBe exito(a)
        (b as Resultado.Exito).repetido shouldBe true
    }

    @Test
    fun `T1_7 un rechazo no consume la clave y se puede reintentar tras corregir la causa`() {
        rechazo(comprar(insumo, 250, "reintento")).shouldBeInstanceOf<MotivoRechazo.SaldoInsuficiente>()
        darFondos("3000.00")
        exito(comprar(insumo, 250, "reintento")).saldo shouldBe d("500.00")
    }

    // ---- T1.8 ----
    @Test
    fun `T1_8 compra confirmada y rechazada generan auditoria con motivo`() {
        comprar(bambu, 2, "ok")
        comprar(bambu, 0, "mal")
        val filas = e.bd.leer(
            "SELECT resultado, clave_idempotencia, detalle, origen, entidad FROM AuditoriaEvento WHERE tipo = 'COMPRA_RECURSO' ORDER BY id_evento",
        ) { listOf(it.texto(0), it.textoONulo(1), it.texto(2), it.texto(3), it.texto(4)) }
        filas.size shouldBe 2
        filas[0][0] shouldBe "CONFIRMADO"
        filas[0][1] shouldBe "${e.idPartida}:COMPRA_RECURSO:ok"
        filas[0][3] shouldBe "CU-07"
        filas[0][4] shouldBe "INVENTARIO"
        filas[1] shouldBe listOf("RECHAZADO", null, "CANTIDAD_INVALIDA", "CU-07", "INVENTARIO")
    }

    @Test
    fun `T1_8 espacios y configuracion se auditan con su origen`() {
        val id = espacioNuevo()
        configurar(id, "Sabana", null, "c1")
        configurar(id, "Sabana", null, "c2") // sin cambios
        e.auditoriaDe("COMPRA_ESPACIO").map { it.first } shouldContainExactly listOf("CONFIRMADO")
        e.auditoriaDe("CONFIGURACION_ESPACIO").map { it.first } shouldContainExactly listOf("CONFIRMADO", "RECHAZADO")
    }

    @Test
    fun `T1_8 las consultas no generan auditoria ni movimientos`() {
        val auditoriaAntes = e.contar("AuditoriaEvento")
        val movimientosAntes = e.contar("MovimientoEconomico")
        e.consultarInventario.ejecutar(e.idPartida)
        e.consultarTienda.recursos()
        e.consultarTienda.capacidades()
        e.consultarZoo.habitats(e.idPartida)
        e.consultarZoo.espacios(e.idPartida, e.habitat("bosque_templado"))
        e.contar("AuditoriaEvento") shouldBe auditoriaAntes
        e.contar("MovimientoEconomico") shouldBe movimientosAntes
    }

    // ---- CU-16 ----
    @Test
    fun `CU-16 muestra fondos existencias y beneficios pendientes persistidos`() {
        val v = e.consultarInventario.ejecutar(e.idPartida)
        (v.fondos as Seccion.Disponible).valor shouldBe d("2000.00")
        (v.beneficiosPendientes as Seccion.Disponible).valor shouldBe d("0.00")
        (v.existencias as Seccion.Disponible).valor.map { it.nombre to it.cantidad }
            .shouldContainExactly("Bambú" to 30, "Medicina" to 3)
        e.bd.escribir(
            "INSERT INTO BeneficioPendiente (id_partida, importe, estado, origen, numero_ciclo, clave_idempotencia, fecha_creacion, fecha_reclamacion) " +
                "VALUES (?, 12.34, 'PENDIENTE', 'CICLO', 1, 'b1', 0, NULL), (?, 5.00, 'RECLAMADO', 'CICLO', 2, 'b2', 0, 1), (?, 0.66, 'PENDIENTE', 'CICLO', 3, 'b3', 0, NULL)",
            e.idPartida, e.idPartida, e.idPartida,
        )
        val w = e.consultarInventario.ejecutar(e.idPartida)
        (w.beneficiosPendientes as Seccion.Disponible).valor shouldBe d("13.00")
    }

    @Test
    fun `CU-16 una seccion que falla se informa sin presentarla como cero y conserva las demas`() {
        val rota = object : EconomiaRepositorio by e.economia {
            override fun beneficiosPendientes(idPartida: String): Dinero = error("sin datos")
        }
        val v = ConsultarInventarioFinanzas(rota, e.inventario).ejecutar(e.idPartida)
        v.beneficiosPendientes.shouldBeInstanceOf<Seccion.NoDisponible>()
        v.fondos.shouldBeInstanceOf<Seccion.Disponible<Dinero>>()
        v.existencias.shouldBeInstanceOf<Seccion.Disponible<*>>()
    }

    @Test
    fun `tienda ofrece bambu e insumo con unidad y precio y las capacidades con su coste`() {
        val recursos = e.consultarTienda.recursos()
        recursos.map { Triple(it.nombre, it.unidad, it.precio) }.shouldContainExactly(
            Triple("Bambú", "kg", d("0.50")),
            Triple("Medicina", "unidades", d("10.00")),
        )
        e.consultarTienda.capacidades().map { it.capacidad to it.coste.toString() }.shouldContainExactly(
            6 to "325.00", 8 to "415.00", 10 to "520.00", 15 to "715.00", 20 to "910.00", 25 to "1105.00", 30 to "1300.00",
        )
    }

    @Test
    fun `navegacion de habitats indica cuales estan desbloqueados`() {
        val h = e.consultarZoo.habitats(e.idPartida)
        h.size shouldBe 5
        h.filter { it.desbloqueado }.map { it.codigo }.toSet() shouldBe setOf("bosque_templado", "humedal_agua_dulce")
        h.all { it.nombre.startsWith("Hábitat") } shouldBe true
    }

    // ---- Reactividad (RES-10) ----
    @Test
    fun `las transacciones confirmadas notifican el cambio y las revertidas no`() = runTest {
        val recibidos = mutableListOf<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { e.bd.cambios.collect { recibidos += it } }
        comprar(bambu, 1)
        yield()
        runCatching { e.bd.ejecutar { error("falla") } }
        yield()
        comprar(bambu, 0) // rechazo: confirma solo la auditoría
        yield()
        job.cancel()
        recibidos.size shouldBe 2
    }
}
