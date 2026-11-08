package com.pandazoo.data

import com.pandazoo.data.db.ErrorSql
import com.pandazoo.application.caso.AlcanceRecalculo
import com.pandazoo.application.caso.ComandoAdquirirAnimal
import com.pandazoo.application.caso.ComandoConfigurarEspacio
import com.pandazoo.application.caso.ComandoDarDeBajaEspacio
import com.pandazoo.application.caso.ComandoEditarFicha
import com.pandazoo.application.caso.ComandoReubicarAnimal
import com.pandazoo.application.caso.ComandoVenderAnimal
import com.pandazoo.application.caso.FichaNueva
import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.puerto.EstadoTienda
import com.pandazoo.domain.economia.Dinero
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Sprint 3: tienda de animales y gestión de especímenes (CU-01, CU-04, CU-05, CU-06, CU-14). */
class Sprint3Test {
    private lateinit var e: Entorno
    private var solicitud = 0
    private val habilidades = listOf("lider", "cuidador")
    private val rasgos = listOf("curioso", "timido")

    @BeforeTest
    fun preparar() {
        e = Entorno()
    }

    @AfterTest
    fun cerrar() = e.cerrar()

    private fun nueva() = "sol-${++solicitud}"

    private fun d(texto: String) = Dinero.desdeDecimal(texto.toDouble())

    private fun espacio(nombre: String = "Madriguera", habitat: String = "bosque_templado", capacidad: Int = 4) =
        e.datos.espacio(e.idPartida, e.habitat(habitat), nombre, capacidad)

    private fun comprar(
        espacio: String,
        nombre: String,
        especie: String = "erizo",
        hab: List<String> = habilidades,
        ras: List<String> = rasgos,
        amigo: String? = null,
        id: String = nueva(),
        entorno: Entorno = e,
        pareja: String? = null,
    ) = entorno.adquirirAnimal.ejecutar(
        ComandoAdquirirAnimal(entorno.idPartida, especie, espacio, FichaNueva(nombre, hab, ras, amigo, pareja), id),
    )

    private fun editar(
        animal: String,
        nombre: String,
        pareja: String? = null,
        amigo: String? = null,
        hab: List<String> = habilidades,
        ras: List<String> = rasgos,
        id: String = nueva(),
    ) = e.editarFicha.ejecutar(ComandoEditarFicha(e.idPartida, animal, nombre, hab, ras, pareja, amigo, id))

    private fun reubicar(animal: String, destino: String, id: String = nueva()) =
        e.reubicarAnimal.ejecutar(ComandoReubicarAnimal(e.idPartida, animal, destino, id))

    private fun vender(animal: String, id: String = nueva()) =
        e.venderAnimal.ejecutar(ComandoVenderAnimal(e.idPartida, animal, id))

    private fun baja(espacio: String, id: String = nueva()) =
        e.darDeBajaEspacio.ejecutar(ComandoDarDeBajaEspacio(e.idPartida, espacio, id))

    private fun rechazo(r: Resultado<*>): MotivoRechazo = (r as Resultado.Rechazado).motivo

    private fun <T> exito(r: Resultado<T>): T = (r as Resultado.Exito<T>).valor

    /** Animal ya criado, alta directa para montar escenarios (los de compra nacen en el nivel 1; este, por defecto, en el 4). */
    private fun existente(espacio: String, especie: String, nombre: String, nivel: Int = 4, alimentacion: Int = 100, enfermo: Boolean = false): String =
        e.datos.animal(e.idPartida, espacio, especie, nombre, alimentacion, enfermo, nivel = nivel)

    private fun satisfaccion(id: String) = e.bd.uno("SELECT satisfaccion_actual FROM Animal WHERE id_animal = ?", id) { it.decimal(0) }!!

    private fun campo(id: String, columna: String) =
        e.bd.uno("SELECT $columna FROM Animal WHERE id_animal = ?", id) { it.textoONulo(0) }

    private fun instantanea(): List<Any?> = listOf(
        e.fondos(), e.contar("Animal"), e.contar("MovimientoEconomico"), e.contar("RasgoPersonalidad"), e.contar("HabilidadSocial"),
        e.bd.leer("SELECT id_animal, nombre, id_espacio, id_pareja, id_mejor_amigo, satisfaccion_actual, estado_vital FROM Animal ORDER BY id_animal") {
            listOf(it.texto(0), it.texto(1), it.textoONulo(2), it.textoONulo(3), it.textoONulo(4), it.decimal(5), it.texto(6))
        },
        e.bd.leer("SELECT nivel, id_espacio, id_habitat, valor FROM MetricaSatisfaccion ORDER BY id_metrica") {
            listOf(it.texto(0), it.textoONulo(1), it.textoONulo(2), it.decimal(3))
        },
    )


    // ---- Tienda: estados de catálogo (RF-10, RF-49) ----

    @Test
    fun `la tienda distingue bloqueadas comerciales desbloqueadas y exclusivas`() {
        val tienda = e.consultarTiendaAnimales.ejecutar(e.idPartida).associateBy { it.especie.id }
        tienda.getValue("erizo").especie.estado shouldBe EstadoTienda.COMERCIAL
        tienda.getValue("erizo").especie.coste shouldBe d("200.00")
        tienda.getValue("erizo").asequible shouldBe true
        tienda.getValue("leon").especie.estado shouldBe EstadoTienda.BLOQUEADA
        tienda.getValue("leon").asequible shouldBe false
        tienda.values.count { it.especie.estado == EstadoTienda.EXCLUSIVA } shouldBe 9
        tienda.values.count { it.especie.estado == EstadoTienda.COMERCIAL } shouldBe 6
        tienda.values.filter { it.especie.estado == EstadoTienda.EXCLUSIVA }.none { it.asequible } shouldBe true
    }

    @Test
    fun `una especie comercial deja de ser asequible cuando el saldo no cubre su coste`() {
        e.bd.escribir("UPDATE Partida SET fondos = ? WHERE id_partida = ?", d("39.99").paraBd(), e.idPartida)
        e.consultarTiendaAnimales.ejecutar(e.idPartida).first { it.especie.id == "erizo" }.asequible shouldBe false
    }

    // ---- T3.1 compra atómica ----

    @Test
    fun `T3_1 compra valida cambia saldo aforo animal satisfaccion y auditoria juntos`() {
        val sp = espacio()
        val r = exito(comprar(sp, "Pincho"))
        r.coste shouldBe d("200.00")
        r.saldo shouldBe d("1800.00")
        e.fondos() shouldBe d("1800.00")
        e.consultarZoo.espacio(e.idPartida, sp)!!.ocupacion shouldBe 1
        e.contar("Animal") shouldBe 1
        e.contar("RasgoPersonalidad") shouldBe 2
        e.contar("HabilidadSocial") shouldBe 2
        e.contar("MovimientoEconomico", "tipo = 'COMPRA' AND importe = 200.0") shouldBe 1
        (satisfaccion(r.idAnimal) > 0.0) shouldBe true // se calculó con la ficha completa
        e.contar("MetricaSatisfaccion", "nivel = 'ESPACIO'") shouldBe 1
        e.auditoriaDe("COMPRA_ANIMAL").map { it.first } shouldContainExactly listOf("CONFIRMADO")
    }

    @Test
    fun `T3_1 sin saldo sin cupo especie bloqueada o ficha incompleta no cambia nada`() {
        val sp = espacio(capacidad = 2)
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        e.bd.escribir("UPDATE Partida SET fondos = ? WHERE id_partida = ?", d("39.99").paraBd(), e.idPartida)
        var antes = instantanea()
        rechazo(comprar(sp, "Pincho")) shouldBe MotivoRechazo.SaldoInsuficiente(d("200.00"), d("39.99"))
        instantanea() shouldBe antes

        e.bd.escribir("UPDATE Partida SET fondos = ? WHERE id_partida = ?", d("1000.00").paraBd(), e.idPartida)
        existente(sp, "erizo", "A")
        existente(sp, "erizo", "B")
        antes = instantanea()
        rechazo(comprar(sp, "Pincho")) shouldBe MotivoRechazo.EspacioLleno
        instantanea() shouldBe antes

        val libre = espacio("Otra")
        antes = instantanea()
        rechazo(comprar(libre, "Rey", especie = "leon")) shouldBe MotivoRechazo.EspecieBloqueada
        rechazo(comprar(libre, "Pincho", hab = listOf("lider"))) shouldBe MotivoRechazo.FichaIncompleta
        instantanea() shouldBe antes
        e.auditoriaDe("COMPRA_ANIMAL").map { it.first }.distinct() shouldContainExactly listOf("RECHAZADO")
    }

    @Test
    fun `T3_1 saldo exactamente igual al coste permite la compra`() {
        val sp = espacio()
        e.bd.escribir("UPDATE Partida SET fondos = ? WHERE id_partida = ?", d("200.00").paraBd(), e.idPartida)
        exito(comprar(sp, "Pincho")).saldo shouldBe d("0.00")
    }

    @Test
    fun `T3_1 un fallo a mitad de la compra revierte cargo y alta`() {
        val sp = espacio()
        // El nombre pasa las validaciones de la aplicación pero la base rechaza la duplicidad exacta: se fuerza el fallo
        // dentro de la transacción insertando antes un animal con el mismo identificador que generará el caso de uso.
        val idSiguiente = "id-1"
        existente(sp, "nutria", "Otra").also { e.bd.escribir("UPDATE Animal SET id_animal = ? WHERE id_animal = ?", idSiguiente, it) }
        val antes = instantanea()
        shouldThrow<ErrorSql> { comprar(sp, "Pincho") }
        instantanea() shouldBe antes
        e.fondos() shouldBe d("2000.00")
        e.auditoriaDe("COMPRA_ANIMAL").map { it.first } shouldContainExactly listOf("REVERTIDO")
    }

    // ---- T3.2 valores iniciales ----

    @Test
    fun `TN_2 el animal comprado nace en el nivel 1 con 100 por ciento y sano`() {
        val sp = espacio()
        val id = exito(comprar(sp, "Pincho")).idAnimal
        val f = e.consultarAnimales.ficha(e.idPartida, id)!!
        f.nivel shouldBe 1
        f.ciclosNivel shouldBe 0
        f.parametros.nivelMaximo shouldBe 15 // erizo: rango Común
        f.alimentacion shouldBe 100
        f.enfermo shouldBe false
        f.estadoVital shouldBe "VIVO"
        f.rango shouldBe "Común"
        f.habilidades.toSet() shouldBe habilidades.toSet()
        f.rasgos.toSet() shouldBe rasgos.toSet()
    }

    @Test
    fun `TN_2 el nivel inicial no depende del azar`() {
        val bajo = Entorno(valorAzar = 0.0)
        val alto = Entorno(valorAzar = 0.999999)
        try {
            val a = exito(comprar(bajo.datos.espacio(bajo.idPartida, bajo.habitat("bosque_templado"), "E"), "Pincho", entorno = bajo))
            val b = exito(comprar(alto.datos.espacio(alto.idPartida, alto.habitat("bosque_templado"), "E"), "Pincho", entorno = alto))
            bajo.consultarAnimales.ficha(bajo.idPartida, a.idAnimal)!!.nivel shouldBe 1
            alto.consultarAnimales.ficha(alto.idPartida, b.idAnimal)!!.nivel shouldBe 1
        } finally {
            bajo.cerrar()
            alto.cerrar()
        }
    }

    // ---- T3.3 a T3.5 ficha y catálogo ----

    @Test
    fun `T3_3 el nombre no es unico en la misma especie ni entre especies pero no puede ser invalido`() {
        val sp = espacio(capacidad = 6)
        val a = exito(comprar(sp, "Pincho")).idAnimal
        val b = exito(comprar(sp, "Pincho")).idAnimal
        exito(comprar(sp, "  pincho ")) // también sin distinguir mayúsculas ni espacios
        exito(comprar(sp, "Pincho", especie = "nutria"))
        (a != b) shouldBe true
        e.contar("Animal", "nombre = 'Pincho'") shouldBe 3
        rechazo(comprar(sp, "   ")) shouldBe MotivoRechazo.NombreInvalido
        rechazo(comprar(sp, "x".repeat(41))) shouldBe MotivoRechazo.NombreInvalido
    }

    @Test
    fun `T3_3 el nombre de un animal vendido se puede reutilizar`() {
        val sp = espacio()
        val id = exito(comprar(sp, "Pincho")).idAnimal
        exito(vender(id))
        exito(comprar(sp, "Pincho"))
    }

    @Test
    fun `T3_4 la ficha exige exactamente dos habilidades y dos rasgos distintos del catalogo`() {
        val sp = espacio()
        rechazo(comprar(sp, "A", hab = listOf("lider"))) shouldBe MotivoRechazo.FichaIncompleta
        rechazo(comprar(sp, "A", hab = listOf("lider", "cuidador", "mediador"))) shouldBe MotivoRechazo.FichaIncompleta
        rechazo(comprar(sp, "A", ras = emptyList())) shouldBe MotivoRechazo.FichaIncompleta
        rechazo(comprar(sp, "A", ras = listOf("curioso", "curioso"))) shouldBe MotivoRechazo.FichaIncompleta
        rechazo(comprar(sp, "A", hab = listOf("lider", "inventada"))) shouldBe MotivoRechazo.FichaIncompleta
        e.contar("Animal") shouldBe 0
        exito(comprar(sp, "A", hab = listOf("mediador", "vigilante"), ras = listOf("valiente", "astuto")))
    }

    @Test
    fun `T3_5 una especie exclusiva no se puede comprar en la tienda ordinaria aunque haya saldo`() {
        val sp = espacio()
        val exclusiva = e.bd.uno("SELECT id_especie FROM Especie WHERE estado_catalogo = 'EXCLUSIVA' ORDER BY id_especie LIMIT 1") { it.texto(0) }!!
        rechazo(comprar(sp, "Rareza", especie = exclusiva)) shouldBe MotivoRechazo.EspecieExclusiva
        e.bd.escribir("INSERT INTO DesbloqueoEspecie (id_partida, id_especie, origen, fecha) VALUES (?, ?, 'RECOMPENSA', 0)", e.idPartida, exclusiva)
        rechazo(comprar(sp, "Rareza", especie = exclusiva)) shouldBe MotivoRechazo.EspecieExclusiva
        e.contar("Animal") shouldBe 0
    }

    @Test
    fun `una especie desconocida o un espacio inexistente o dado de baja se rechazan`() {
        val sp = espacio()
        rechazo(comprar(sp, "X", especie = "dragon")) shouldBe MotivoRechazo.EspecieInexistente
        rechazo(comprar("no-existe", "X")) shouldBe MotivoRechazo.EspacioInexistente
        exito(baja(sp))
        rechazo(comprar(sp, "X")) shouldBe MotivoRechazo.EspacioInexistente
    }

    // ---- T3.6 compatibilidad ----

    @Test
    fun `T3_6 un habitat incompatible esta permitido y solo baja la satisfaccion`() {
        val natural = espacio("Natural", "bosque_templado")
        val hostil = espacio("Hostil", "humedal_agua_dulce")
        val en = exito(comprar(natural, "Uno")).idAnimal
        val fuera = exito(comprar(hostil, "Dos")).idAnimal
        (satisfaccion(fuera) < satisfaccion(en)) shouldBe true // factor 0.4 frente a 1.0
        e.consultarZoo.espacio(e.idPartida, hostil)!!.ocupacion shouldBe 1
    }

    @Test
    fun `T3_6 comprar un compañero recalcula la satisfaccion de los animales del espacio`() {
        val sp = espacio()
        val primero = exito(comprar(sp, "Uno")).idAnimal
        val solo = satisfaccion(primero)
        exito(comprar(sp, "Dos", especie = "nutria"))
        satisfaccion(primero) shouldNotBe solo // la afinidad con el nuevo compañero entra en su S_soc
    }

    // ---- T3.7 vínculos ----

    @Test
    fun `TN_8 sin pareja elegida el animal nuevo no tiene vinculo`() {
        val sp = espacio()
        existente(sp, "erizo", "Zarza")
        val nuevo = exito(comprar(sp, "Pincho")).idAnimal
        campo(nuevo, "id_pareja") shouldBe null
    }

    @Test
    fun `la compra puede elegir pareja de la misma especie libre en el mismo espacio y es reciproca`() {
        val sp = espacio()
        val zarza = existente(sp, "erizo", "Zarza")
        val nuevo = exito(comprar(sp, "Pincho", pareja = zarza)).idAnimal
        campo(nuevo, "id_pareja") shouldBe zarza
        campo(zarza, "id_pareja") shouldBe nuevo
    }

    @Test
    fun `la compra rechaza una pareja de otra especie de otro espacio u ocupada sin cambiar nada`() {
        val sp = espacio(capacidad = 10)
        val otro = espacio("Otra")
        val nutria = existente(sp, "nutria", "Nut")
        val lejano = existente(otro, "erizo", "Lejano")
        val a = existente(sp, "erizo", "Par1")
        val b = existente(sp, "erizo", "Par2")
        e.datos.parejas(e.idPartida, a, b)
        val antes = instantanea()
        rechazo(comprar(sp, "Pincho", pareja = nutria)) shouldBe MotivoRechazo.ParejaOtraEspecie
        rechazo(comprar(sp, "Pincho", pareja = lejano)) shouldBe MotivoRechazo.ParejaOtroEspacio
        rechazo(comprar(sp, "Pincho", pareja = a)) shouldBe MotivoRechazo.ParejaOcupada
        rechazo(comprar(sp, "Pincho", pareja = "fantasma")) shouldBe MotivoRechazo.ParejaInexistente
        instantanea() shouldBe antes
    }

    @Test
    fun `TN_8 CU-05 acepta la pareja sin nivel minimo y es reciproca`() {
        val sp = espacio()
        val a = existente(sp, "erizo", "Alfa", nivel = 1)
        val b = existente(sp, "erizo", "Beta", nivel = 1)
        exito(editar(a, "Alfa", pareja = b))
        campo(a, "id_pareja") shouldBe b
        campo(b, "id_pareja") shouldBe a
    }

    @Test
    fun `T3_7 pareja de otra especie de otro espacio o ya ocupada se rechaza en la edicion`() {
        val sp = espacio(capacidad = 10)
        val otroEspacio = espacio("Otra")
        val a = existente(sp, "erizo", "Alfa")
        rechazo(editar(a, "Alfa", pareja = existente(sp, "nutria", "Nut"))) shouldBe MotivoRechazo.ParejaOtraEspecie
        rechazo(editar(a, "Alfa", pareja = existente(otroEspacio, "erizo", "Lejano"))) shouldBe MotivoRechazo.ParejaOtroEspacio
        val p1 = existente(sp, "erizo", "Par1")
        val p2 = existente(sp, "erizo", "Par2")
        e.datos.parejas(e.idPartida, p1, p2)
        rechazo(editar(a, "Alfa", pareja = p1)) shouldBe MotivoRechazo.ParejaOcupada
        rechazo(editar(a, "Alfa", pareja = "fantasma")) shouldBe MotivoRechazo.ParejaInexistente
        campo(a, "id_pareja") shouldBe null
    }

    @Test
    fun `T3_7 mejor amigo de otra especie se acepta y de otro espacio se rechaza`() {
        val sp = espacio()
        val otroEspacio = espacio("Otra")
        val amigo = existente(sp, "nutria", "Nut")
        val nuevo = exito(comprar(sp, "Pincho", amigo = amigo)).idAnimal
        campo(nuevo, "id_mejor_amigo") shouldBe amigo
        rechazo(comprar(sp, "Otro", amigo = existente(otroEspacio, "nutria", "Lejana"))) shouldBe MotivoRechazo.AmigoInvalido
    }

    @Test
    fun `T3_7 editar la ficha rechaza autorreferencias y vinculos invalidos sin cambios`() {
        val sp = espacio()
        val a = existente(sp, "erizo", "Alfa")
        val b = existente(sp, "erizo", "Beta")
        val nut = existente(sp, "nutria", "Nut")
        val antes = instantanea()
        rechazo(editar(a, "Alfa", pareja = a)) shouldBe MotivoRechazo.AutoReferencia
        rechazo(editar(a, "Alfa", amigo = a)) shouldBe MotivoRechazo.AutoReferencia
        rechazo(editar(a, "Alfa", pareja = nut)) shouldBe MotivoRechazo.ParejaOtraEspecie
        instantanea() shouldBe antes

        exito(editar(a, "Alfa", pareja = b, amigo = nut))
        campo(a, "id_pareja") shouldBe b
        campo(b, "id_pareja") shouldBe a
        campo(a, "id_mejor_amigo") shouldBe nut
        campo(nut, "id_mejor_amigo") shouldBe a // el mejor amigo es recíproco, aunque sea de otra especie

        // Con pareja asignada no se puede pasar directamente a otra.
        val c = existente(sp, "erizo", "Gamma")
        rechazo(editar(a, "Alfa", pareja = c, amigo = nut)) shouldBe MotivoRechazo.DivorcioRequerido
        rechazo(editar(c, "Gamma", pareja = b)) shouldBe MotivoRechazo.ParejaOcupada
    }

    @Test
    fun `el mejor amigo es reciproco y exige que los dos esten libres`() {
        val sp = espacio()
        val a = existente(sp, "erizo", "Alfa")
        val b = existente(sp, "nutria", "Beta")
        val c = existente(sp, "capibara", "Gamma")
        exito(editar(a, "Alfa", amigo = b))
        campo(a, "id_mejor_amigo") shouldBe b
        campo(b, "id_mejor_amigo") shouldBe a

        // Ni quien ya tiene amigo puede elegir otro, ni se puede elegir a quien ya tiene uno.
        rechazo(editar(a, "Alfa", amigo = c)) shouldBe MotivoRechazo.AmigoOcupado
        rechazo(editar(c, "Gamma", amigo = b)) shouldBe MotivoRechazo.AmigoOcupado

        // Al quitarlo quedan libres los dos y ya se puede elegir otro.
        exito(editar(a, "Alfa", amigo = null))
        campo(a, "id_mejor_amigo") shouldBe null
        campo(b, "id_mejor_amigo") shouldBe null
        exito(editar(c, "Gamma", amigo = b))
        campo(b, "id_mejor_amigo") shouldBe c
    }

    @Test
    fun `comprar un animal con mejor amigo enlaza a los dos y rechaza a quien ya tiene amigo`() {
        val sp = espacio()
        val a = existente(sp, "erizo", "Alfa")
        val nuevo = exito(comprar(sp, "Nuevo", especie = "nutria", amigo = a)).idAnimal
        campo(a, "id_mejor_amigo") shouldBe nuevo
        campo(nuevo, "id_mejor_amigo") shouldBe a
        rechazo(comprar(sp, "Otro", especie = "nutria", amigo = a)) shouldBe MotivoRechazo.AmigoOcupado
    }

    @Test
    fun `T3_7 el divorcio libera a los dos y los vinculos quedan coherentes`() {
        val sp = espacio()
        val a = existente(sp, "erizo", "Alfa")
        val b = existente(sp, "erizo", "Beta")
        exito(editar(a, "Alfa", pareja = b))
        // Editar la ficha no quita la pareja: solo el divorcio.
        rechazo(editar(a, "Alfa", pareja = null)) shouldBe MotivoRechazo.DivorcioRequerido
        exito(e.divorciarAnimal.ejecutar(com.pandazoo.application.caso.ComandoDivorciar(e.idPartida, a, nueva())))
        campo(a, "id_pareja") shouldBe null
        campo(b, "id_pareja") shouldBe null
        // Y ahora b puede emparejarse con otro.
        val c = existente(sp, "erizo", "Gamma")
        exito(editar(b, "Beta", pareja = c))
        campo(c, "id_pareja") shouldBe b
    }

    @Test
    fun `T3_7 un vinculo sin cambios no se revalida aunque la pareja se haya trasladado`() {
        val sp = espacio()
        val lejos = espacio("Lejos")
        val a = existente(sp, "erizo", "Alfa")
        val b = existente(sp, "erizo", "Beta")
        exito(editar(a, "Alfa", pareja = b))
        exito(reubicar(b, lejos))
        exito(editar(a, "Alfa Nuevo", pareja = b)) // solo renombra: el vínculo existente se conserva
        campo(a, "nombre") shouldBe "Alfa Nuevo"
        campo(a, "id_pareja") shouldBe b
    }

    @Test
    fun `trasladar a un animal rompe la amistad con su mejor amigo`() {
        val sp = espacio()
        val lejos = espacio("Lejos")
        val a = existente(sp, "erizo", "Alfa")
        val b = existente(sp, "erizo", "Beta")
        exito(editar(a, "Alfa", amigo = b))
        campo(b, "id_mejor_amigo") shouldBe a
        exito(reubicar(b, lejos))
        campo(a, "id_mejor_amigo") shouldBe null
        campo(b, "id_mejor_amigo") shouldBe null
    }

    // ---- T3.8 campos protegidos ----

    @Test
    fun `T3_8 editar la ficha no toca especie nivel alimentacion salud estado ni espacio`() {
        val sp = espacio()
        val id = existente(sp, "erizo", "Alfa", alimentacion = 60)
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        val columnas = "id_especie, nivel, ciclos_nivel, nivel_alimentacion, estado_salud, estado_vital, id_espacio, satisfaccion_actual"
        val antes = e.bd.uno("SELECT $columnas FROM Animal WHERE id_animal = ?", id) { f -> (0 until 8).map { f.textoONulo(it) } }
        exito(editar(id, "Alfa Dos", hab = listOf("mediador", "vigilante"), ras = listOf("valiente", "astuto")))
        e.bd.uno("SELECT $columnas FROM Animal WHERE id_animal = ?", id) { f -> (0 until 8).map { f.textoONulo(it) } } shouldBe antes
        val ficha = e.consultarAnimales.ficha(e.idPartida, id)!!
        ficha.nombre shouldBe "Alfa Dos"
        ficha.habilidades.toSet() shouldBe setOf("mediador", "vigilante")
        ficha.rasgos.toSet() shouldBe setOf("valiente", "astuto")
    }

    @Test
    fun `editar sin cambios no registra mutacion ni recalcula`() {
        val sp = espacio()
        val id = exito(comprar(sp, "Pincho")).idAnimal
        val invocaciones = e.recalcular.invocaciones
        rechazo(editar(id, "Pincho")) shouldBe MotivoRechazo.SinCambios
        e.recalcular.invocaciones shouldBe invocaciones
        rechazo(editar(id, "Pincho", hab = listOf("lider"))) shouldBe MotivoRechazo.FichaIncompleta
        rechazo(editar("fantasma", "X")) shouldBe MotivoRechazo.AnimalInexistente
    }

    @Test
    fun `editar el nombre a uno ya usado por otro animal de la especie se acepta`() {
        val sp = espacio()
        val a = existente(sp, "erizo", "Alfa")
        existente(sp, "erizo", "Beta")
        exito(editar(a, "Beta"))
        e.contar("Animal", "nombre = 'Beta'") shouldBe 2
        rechazo(editar(a, "  ")) shouldBe MotivoRechazo.NombreInvalido
    }

    // ---- T3.9 reubicar ----

    @Test
    fun `T3_9 el traslado actualiza ambos espacios y recalcula origen y destino`() {
        val origen = espacio("Origen", "bosque_templado")
        val destino = espacio("Destino", "humedal_agua_dulce")
        val id = exito(comprar(origen, "Pincho")).idAnimal
        val compañero = exito(comprar(origen, "Zarza")).idAnimal
        val natural = satisfaccion(id)
        val r = exito(reubicar(id, destino))
        r.origenVacio shouldBe false
        e.consultarZoo.espacio(e.idPartida, origen)!!.ocupacion shouldBe 1
        e.consultarZoo.espacio(e.idPartida, destino)!!.ocupacion shouldBe 1
        campo(id, "id_espacio") shouldBe destino
        (satisfaccion(id) < natural) shouldBe true // el humedal es poco apto para el erizo
        (satisfaccion(compañero) > 0.0) shouldBe true
        e.contar("MetricaSatisfaccion", "nivel = 'ESPACIO'") shouldBe 2
    }

    @Test
    fun `T3_9 aforo lleno destino inactivo mismo espacio y animal no vivo se rechazan sin cambios`() {
        val origen = espacio("Origen")
        val lleno = espacio("Lleno", capacidad = 2)
        val inactivo = espacio("Inactivo")
        exito(baja(inactivo))
        val id = exito(comprar(origen, "Pincho")).idAnimal
        existente(lleno, "nutria", "A")
        existente(lleno, "nutria", "B")
        val antes = instantanea()
        rechazo(reubicar(id, lleno)) shouldBe MotivoRechazo.EspacioLleno
        rechazo(reubicar(id, inactivo)) shouldBe MotivoRechazo.EspacioInexistente
        rechazo(reubicar(id, origen)) shouldBe MotivoRechazo.DestinoIgualAOrigen
        rechazo(reubicar(id, "no-existe")) shouldBe MotivoRechazo.EspacioInexistente
        rechazo(reubicar("fantasma", lleno)) shouldBe MotivoRechazo.AnimalInexistente
        instantanea() shouldBe antes
        exito(vender(id))
        rechazo(reubicar(id, espacio("Libre"))) shouldBe MotivoRechazo.AnimalNoVivo
    }

    @Test
    fun `T3_9 trasladar al ultimo animal deja el origen vacio y lo comunica`() {
        val origen = espacio("Origen")
        val destino = espacio("Destino")
        val id = exito(comprar(origen, "Pincho")).idAnimal
        exito(reubicar(id, destino)).origenVacio shouldBe true
        e.contar("MetricaSatisfaccion", "nivel = 'ESPACIO' AND id_espacio = '$origen'") shouldBe 0
    }

    @Test
    fun `destinos posibles excluye el espacio actual los llenos y los inactivos`() {
        val a = espacio("A")
        val b = espacio("B")
        val lleno = espacio("Lleno", capacidad = 2)
        val desusado = espacio("Desusado")
        existente(lleno, "nutria", "X")
        existente(lleno, "nutria", "Y")
        exito(baja(desusado))
        e.consultarAnimales.destinosPosibles(e.idPartida, a).map { it.espacio.id } shouldContainExactly listOf(b)
        e.consultarAnimales.habitatsConCupo(e.idPartida).map { it.codigo } shouldContainExactly listOf("bosque_templado")
    }

    // ---- T3.10 vender ----

    @Test
    fun `T3_10 la venta correcta abona el importe por nivel libera aforo y mantiene la genealogia`() {
        val sp = espacio()
        val padre = existente(sp, "erizo", "Padre")
        val madre = existente(sp, "erizo", "Madre")
        val cria = e.datos.animal(e.idPartida, sp, "erizo", "Cria", idPadre = padre, idMadre = madre)
        e.datos.parejas(e.idPartida, padre, madre)
        e.datos.amigos(e.idPartida, padre, cria)
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        val fondos = e.fondos()
        val satisfaccionCria = satisfaccion(cria)

        val r = exito(vender(padre))
        r.origenVacio shouldBe false
        r.importe shouldBe Dinero.desdeDecimal(73.33) // erizo (rango 1, base 200) en el nivel 4: 200 · (0.05 + 0.95 · 3 / 9)
        e.fondos() shouldBe fondos + r.importe
        campo(padre, "estado_vital") shouldBe "VENDIDO"
        campo(padre, "id_espacio") shouldBe null
        campo(padre, "id_pareja") shouldBe null
        campo(madre, "id_pareja") shouldBe null // se anulan también los vínculos de otros hacia el vendido
        campo(cria, "id_mejor_amigo") shouldBe null
        campo(cria, "id_padre") shouldBe padre // la genealogía se conserva
        e.consultarZoo.espacio(e.idPartida, sp)!!.ocupacion shouldBe 2
        e.contar("MovimientoEconomico", "tipo = 'VENTA' AND importe = 73.33") shouldBe 1
        e.contar("RasgoPersonalidad", "id_animal = '$padre'") shouldBe 0
        (satisfaccion(cria) < satisfaccionCria) shouldBe true // perdió padre y amigo vivos: se recalculó
    }

    @Test
    fun `T3_10 un animal enfermo o sin alimentacion no se vende`() {
        val sp = espacio()
        val enfermo = existente(sp, "erizo", "Enfermo", enfermo = true)
        val hambriento = existente(sp, "erizo", "Hambriento", alimentacion = 0)
        val antes = instantanea()
        rechazo(vender(enfermo)) shouldBe MotivoRechazo.AnimalEnfermo
        rechazo(vender(hambriento)) shouldBe MotivoRechazo.SinAlimentacion
        rechazo(vender("fantasma")) shouldBe MotivoRechazo.AnimalInexistente
        instantanea() shouldBe antes
        e.auditoriaDe("VENTA_ANIMAL").map { it.first }.distinct() shouldContainExactly listOf("RECHAZADO")
    }

    @Test
    fun `T3_10 vender dos veces el mismo animal no repite la venta`() {
        val sp = espacio()
        val id = exito(comprar(sp, "Pincho")).idAnimal
        exito(vender(id))
        rechazo(vender(id)) shouldBe MotivoRechazo.AnimalNoVivo
        e.contar("MovimientoEconomico", "tipo = 'VENTA'") shouldBe 1
    }

    @Test
    fun `vender recalcula los espacios de los familiares alojados en otros espacios`() {
        val casa = espacio("Casa")
        val otra = espacio("Otra")
        val padre = existente(casa, "erizo", "Padre")
        val hijo = e.datos.animal(e.idPartida, otra, "erizo", "Hijo", idPadre = padre)
        e.recalcular.alConsolidar(e.idPartida, AlcanceRecalculo.Parque)
        val antes = satisfaccion(hijo)
        exito(vender(padre))
        (satisfaccion(hijo) < antes) shouldBe true // ya no cuenta con un padre vivo aunque viva en otro espacio
    }

    // ---- T3.11 ----

    @Test
    fun `T3_11 no se puede borrar fisicamente un animal con descendencia`() {
        val sp = espacio()
        val padre = existente(sp, "erizo", "Padre")
        e.datos.animal(e.idPartida, sp, "erizo", "Cria", idPadre = padre)
        shouldThrow<ErrorSql> { e.bd.escribir("DELETE FROM Animal WHERE id_animal = ?", padre) }
        exito(vender(padre))
        e.contar("Animal", "id_animal = '$padre'") shouldBe 1 // sigue ahí como VENDIDO
    }

    // ---- T3.12 baja de espacio ----

    @Test
    fun `T3_12 el espacio con animales no se da de baja`() {
        val sp = espacio()
        exito(comprar(sp, "Pincho"))
        rechazo(baja(sp)) shouldBe MotivoRechazo.EspacioNoVacio
        e.consultarZoo.espacio(e.idPartida, sp)!!.estado.name shouldBe "ACTIVO"
    }

    @Test
    fun `T3_12 un espacio con referencias pendientes no se da de baja`() {
        val sp = espacio()
        e.bd.escribir(
            "INSERT INTO Notificacion (id_partida, tipo, severidad, codigo, entidad, id_entidad, numero_ciclo, estado, fecha) " +
                "VALUES (?, 'INFORMATIVA', 'BAJA', 'HABITAT_DESBLOQUEADO', 'ESPACIO', ?, 0, 'PENDIENTE', 0)",
            e.idPartida, sp,
        )
        rechazo(baja(sp)) shouldBe MotivoRechazo.EspacioConDependencias
        e.bd.escribir("UPDATE Notificacion SET estado = 'VISTA' WHERE id_entidad = ?", sp)
        rechazo(baja(sp)) shouldBe MotivoRechazo.EspacioConDependencias // una notificación vista pero no cerrada sigue abierta
    }

    @Test
    fun `T3_12 un espacio vacio y limpio se da de baja y desaparece de la base de datos`() {
        val sp = espacio(capacidad = 6)
        exito(baja(sp))
        e.consultarZoo.espacio(e.idPartida, sp) shouldBe null
        e.contar("Espacio", "id_espacio = '$sp'") shouldBe 0
        e.contar("MetricaSatisfaccion", "id_espacio = '$sp'") shouldBe 0
        rechazo(baja(sp)) shouldBe MotivoRechazo.EspacioInexistente
        rechazo(baja("no-existe")) shouldBe MotivoRechazo.EspacioInexistente
    }

    @Test
    fun `T3_12 tras vender o trasladar al ultimo animal se puede dar de baja el espacio vacio`() {
        val sp = espacio()
        val id = exito(comprar(sp, "Pincho")).idAnimal
        exito(vender(id)).origenVacio shouldBe true
        exito(baja(sp))
    }

    // ---- T3.13 idempotencia ----

    @Test
    fun `T3_13 reintentar la misma compra no cobra ni crea otro animal`() {
        val sp = espacio()
        val primera = comprar(sp, "Pincho", id = "fija")
        val segunda = comprar(sp, "Pincho", id = "fija")
        (segunda as Resultado.Exito<*>).repetido shouldBe true
        exito(segunda) shouldBe exito(primera)
        e.fondos() shouldBe d("1800.00")
        e.contar("Animal") shouldBe 1
        e.contar("MovimientoEconomico") shouldBe 1
        // La misma clave con otra ficha es un conflicto y no cambia nada.
        rechazo(comprar(sp, "Otro", id = "fija")) shouldBe MotivoRechazo.ConflictoIdempotencia
        e.contar("Animal") shouldBe 1
    }

    @Test
    fun `T3_13 reintentar el mismo traslado devuelve el resultado original sin repetir efectos`() {
        val origen = espacio("Origen")
        val destino = espacio("Destino")
        val id = exito(comprar(origen, "Pincho")).idAnimal
        val primera = reubicar(id, destino, "fija")
        val invocaciones = e.recalcular.invocaciones
        val segunda = reubicar(id, destino, "fija")
        (segunda as Resultado.Exito<*>).repetido shouldBe true
        exito(segunda) shouldBe exito(primera)
        e.recalcular.invocaciones shouldBe invocaciones
        e.auditoriaDe("TRASLADO_ANIMAL").map { it.first } shouldContainExactly listOf("CONFIRMADO")
    }

    @Test
    fun `T3_13 reintentar la misma baja de espacio devuelve el resultado original`() {
        val sp = espacio()
        exito(baja(sp, "fija"))
        (baja(sp, "fija") as Resultado.Exito<*>).repetido shouldBe true
    }

    // ---- CU-02 con animales ----

    @Test
    fun `cambiar un espacio con animales de habitat recalcula su satisfaccion`() {
        val sp = espacio("Bosque", "bosque_templado")
        val id = exito(comprar(sp, "Pincho")).idAnimal
        val antes = satisfaccion(id)
        exito(
            e.configurarEspacio.ejecutar(
                ComandoConfigurarEspacio(e.idPartida, sp, null, e.habitat("humedal_agua_dulce"), nueva()),
            ),
        )
        (satisfaccion(id) < antes) shouldBe true
        e.contar("MetricaSatisfaccion", "nivel = 'HABITAT' AND id_habitat = '${e.habitat("humedal_agua_dulce")}'") shouldBe 1
        e.contar("MetricaSatisfaccion", "nivel = 'HABITAT' AND id_habitat = '${e.habitat("bosque_templado")}'") shouldBe 0
    }

    // ---- Lectura (sin recálculo, sin auditoría) ----

    @Test
    fun `consultar tienda animales y fichas no recalcula ni audita`() {
        val sp = espacio()
        val id = exito(comprar(sp, "Pincho")).idAnimal
        val invocaciones = e.recalcular.invocaciones
        val auditorias = e.contar("AuditoriaEvento")
        repeat(3) {
            e.consultarTiendaAnimales.ejecutar(e.idPartida)
            e.consultarAnimales.ficha(e.idPartida, id)
            e.consultarAnimales.animalesDe(e.idPartida, sp)
            e.consultarAnimales.habitatsConCupo(e.idPartida)
        }
        e.recalcular.invocaciones shouldBe invocaciones
        e.contar("AuditoriaEvento") shouldBe auditorias
        e.consultarAnimales.animalesDe(e.idPartida, sp) shouldHaveSize 1
    }

    @Test
    fun `la ficha lleva nombres legibles de espacio habitat pareja y amigo`() {
        val sp = espacio("Madriguera")
        val amigo = existente(sp, "nutria", "Nut")
        val id = exito(comprar(sp, "Pincho", amigo = amigo)).idAnimal
        val f = e.consultarAnimales.ficha(e.idPartida, id)!!
        f.nombreEspacio shouldBe "Madriguera"
        f.nombreHabitat shouldBe "Hábitat Bosque templado"
        f.nombrePareja shouldBe null
        f.nombreMejorAmigo shouldBe "Nut"
        f.especie shouldBe "Erizo"
        (f.satisfaccion > 0.0) shouldBe true
    }
}
