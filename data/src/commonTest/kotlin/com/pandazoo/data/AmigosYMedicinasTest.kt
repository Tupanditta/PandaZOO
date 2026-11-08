package com.pandazoo.data

import com.pandazoo.application.caso.ComandoAdquirirAnimal
import com.pandazoo.application.caso.ComandoDivorciar
import com.pandazoo.application.caso.ComandoEditarFicha
import com.pandazoo.application.caso.ComandoTratarAnimal
import com.pandazoo.application.caso.FichaNueva
import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.error.FaltanteInsumo
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Un familiar no puede ser mejor amigo, y el tratamiento médico crece con el nivel del animal. */
class AmigosYMedicinasTest {
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

    private fun rechazo(r: Resultado<*>): MotivoRechazo = (r as Resultado.Rechazado).motivo

    private fun espacio() = e.datos.espacio(e.idPartida, e.habitat("bosque_templado"), "Madriguera", 12)

    private fun animal(sp: String, nombre: String, padre: String? = null, madre: String? = null, especie: String = "erizo") =
        e.datos.animal(e.idPartida, sp, especie, nombre, idPadre = padre, idMadre = madre)

    private fun editar(id: String, nombre: String, pareja: String? = null, amigo: String? = null) =
        e.editarFicha.ejecutar(ComandoEditarFicha(e.idPartida, id, nombre, habilidades, rasgos, pareja, amigo, nueva()))

    private fun amigo(id: String) = e.bd.uno("SELECT id_mejor_amigo FROM Animal WHERE id_animal = ?", id) { it.textoONulo(0) }

    @Test
    fun `la pareja no puede ser el mejor amigo`() {
        val sp = espacio()
        val a = animal(sp, "A")
        val b = animal(sp, "B")
        e.datos.parejas(e.idPartida, a, b)
        rechazo(editar(a, "A", pareja = b, amigo = b)) shouldBe MotivoRechazo.AmigoFamiliar
        amigo(a) shouldBe null
        amigo(b) shouldBe null
    }

    @Test
    fun `los padres hijos y hermanos no pueden ser mejores amigos`() {
        val sp = espacio()
        val padre = animal(sp, "Padre")
        val madre = animal(sp, "Madre")
        val h1 = animal(sp, "Hijo1", padre = padre, madre = madre)
        val h2 = animal(sp, "Hijo2", padre = padre, madre = madre)
        val medio = animal(sp, "Medio", padre = padre) // medio hermano: comparte solo el padre
        rechazo(editar(h1, "Hijo1", amigo = padre)) shouldBe MotivoRechazo.AmigoFamiliar
        rechazo(editar(padre, "Padre", amigo = h1)) shouldBe MotivoRechazo.AmigoFamiliar
        rechazo(editar(h1, "Hijo1", amigo = h2)) shouldBe MotivoRechazo.AmigoFamiliar
        rechazo(editar(h2, "Hijo2", amigo = medio)) shouldBe MotivoRechazo.AmigoFamiliar
        rechazo(editar(madre, "Madre", amigo = h1)) shouldBe MotivoRechazo.AmigoFamiliar
        listOf(padre, madre, h1, h2, medio).forEach { amigo(it) shouldBe null }
    }

    @Test
    fun `un animal sin parentesco si puede ser el mejor amigo`() {
        val sp = espacio()
        val padre = animal(sp, "Padre")
        val hijo = animal(sp, "Hijo", padre = padre)
        val vecino = animal(sp, "Vecino")
        (editar(hijo, "Hijo", amigo = vecino) is Resultado.Exito) shouldBe true
        amigo(hijo) shouldBe vecino
        amigo(vecino) shouldBe hijo
    }

    private fun divorciar(id: String) = e.divorciarAnimal.ejecutar(ComandoDivorciar(e.idPartida, id, nueva()))

    private fun pareja(id: String) = e.bd.uno("SELECT id_pareja FROM Animal WHERE id_animal = ?", id) { it.textoONulo(0) }

    private fun padres(id: String) = e.bd.uno("SELECT id_padre, id_madre FROM Animal WHERE id_animal = ?", id) { it.textoONulo(0) to it.textoONulo(1) }

    @Test
    fun `dos mejores amigos pueden pasar a ser pareja y dejan de ser amigos`() {
        val sp = espacio()
        val a = animal(sp, "A")
        val b = animal(sp, "B")
        e.datos.amigos(e.idPartida, a, b)
        // El formulario mantiene al amigo actual al elegirlo como pareja: la pareja prevalece y la amistad termina.
        (editar(a, "A", pareja = b, amigo = b) is Resultado.Exito) shouldBe true
        pareja(a) shouldBe b
        pareja(b) shouldBe a
        amigo(a) shouldBe null
        amigo(b) shouldBe null
    }

    @Test
    fun `un amigo puede pasar a pareja y el animal elige a la vez otro amigo`() {
        val sp = espacio()
        val a = animal(sp, "A")
        val b = animal(sp, "B")
        val c = animal(sp, "C")
        e.datos.amigos(e.idPartida, a, b)
        (editar(a, "A", pareja = b, amigo = c) is Resultado.Exito) shouldBe true
        pareja(a) shouldBe b
        amigo(a) shouldBe c
        amigo(c) shouldBe a
        amigo(b) shouldBe null
    }

    @Test
    fun `una pareja no puede pasar a ser mejores amigos sin divorciarse antes`() {
        val sp = espacio()
        val a = animal(sp, "A")
        val b = animal(sp, "B")
        e.datos.parejas(e.idPartida, a, b)
        rechazo(editar(a, "A", pareja = b, amigo = b)) shouldBe MotivoRechazo.AmigoFamiliar
        // Quitar la pareja y hacerse amigos en la misma edición tampoco: primero el divorcio.
        rechazo(editar(a, "A", pareja = null, amigo = b)) shouldBe MotivoRechazo.DivorcioRequerido
        pareja(a) shouldBe b
        amigo(a) shouldBe null
        // Tras el divorcio ya pueden ser mejores amigos.
        (divorciar(a) is Resultado.Exito) shouldBe true
        pareja(a) shouldBe null
        pareja(b) shouldBe null
        (editar(a, "A", pareja = null, amigo = b) is Resultado.Exito) shouldBe true
        amigo(a) shouldBe b
        amigo(b) shouldBe a
    }

    @Test
    fun `no se puede divorciar quien no tiene pareja ni un animal inexistente`() {
        val sp = espacio()
        val a = animal(sp, "A")
        rechazo(divorciar(a)) shouldBe MotivoRechazo.SinPareja
        rechazo(divorciar("fantasma")) shouldBe MotivoRechazo.AnimalInexistente
    }

    @Test
    fun `el divorcio audita y un reintento con la misma solicitud no repite efectos`() {
        val sp = espacio()
        val a = animal(sp, "A")
        val b = animal(sp, "B")
        e.datos.parejas(e.idPartida, a, b)
        val comando = ComandoDivorciar(e.idPartida, a, "div-1")
        (e.divorciarAnimal.ejecutar(comando) as Resultado.Exito).repetido shouldBe false
        (e.divorciarAnimal.ejecutar(comando) as Resultado.Exito).repetido shouldBe true
        e.auditoriaDe("DIVORCIO").map { it.first } shouldBe listOf("CONFIRMADO")
    }

    @Test
    fun `elegir a la vez como pareja y como amigo a quien no es amigo se rechaza`() {
        val sp = espacio()
        val a = animal(sp, "A")
        val b = animal(sp, "B")
        rechazo(editar(a, "A", pareja = b, amigo = b)) shouldBe MotivoRechazo.AmigoFamiliar
        pareja(a) shouldBe null
        amigo(a) shouldBe null
    }

    @Test
    fun `el divorcio deja a los hijos comunes sin padres ni hermanos y conserva el resto`() {
        val sp = espacio()
        val a = animal(sp, "A")
        val b = animal(sp, "B")
        val x = animal(sp, "X")
        e.datos.parejas(e.idPartida, a, b)
        val (padre, madre) = if (a < b) a to b else b to a
        val h1 = animal(sp, "Hijo1", padre = padre, madre = madre)
        val h2 = animal(sp, "Hijo2", padre = padre, madre = madre)
        val otro = animal(sp, "Otro", padre = a, madre = x) // hijo de A con otra pareja: no es hijo de la pareja
        e.animales.familiares(e.idPartida, h1) shouldBe setOf(padre, madre, h2, otro)

        (divorciar(a) is Resultado.Exito) shouldBe true

        pareja(a) shouldBe null
        pareja(b) shouldBe null
        padres(h1) shouldBe (null to null)
        padres(h2) shouldBe (null to null)
        padres(otro) shouldBe (a to x) // los hijos de otra relación conservan a sus padres
        e.animales.familiares(e.idPartida, h1) shouldBe emptySet()
        e.animales.familiares(e.idPartida, a) shouldBe setOf(otro)
        e.contar("Animal") shouldBe 6 // nadie se borra: solo cambian los vínculos
    }

    @Test
    fun `tras el divorcio se puede elegir otra pareja o ser mejores amigos con la ex pareja`() {
        val sp = espacio()
        val a = animal(sp, "A")
        val b = animal(sp, "B")
        val c = animal(sp, "C")
        e.datos.parejas(e.idPartida, a, b)
        // No se pasa directamente de una pareja a otra.
        rechazo(editar(a, "A", pareja = c)) shouldBe MotivoRechazo.DivorcioRequerido
        (divorciar(a) is Resultado.Exito) shouldBe true
        (editar(a, "A", pareja = c) is Resultado.Exito) shouldBe true
        pareja(a) shouldBe c
        pareja(c) shouldBe a
        pareja(b) shouldBe null
    }

    @Test
    fun `la compra rechaza como amigo a la pareja elegida en la misma compra`() {
        val sp = espacio()
        val zarza = animal(sp, "Zarza")
        val r = e.adquirirAnimal.ejecutar(
            ComandoAdquirirAnimal(e.idPartida, "erizo", sp, FichaNueva("Pincho", habilidades, rasgos, zarza, zarza), nueva()),
        )
        rechazo(r) shouldBe MotivoRechazo.AmigoFamiliar
        e.contar("Animal") shouldBe 1
    }

    @Test
    fun `la consulta de familiares incluye pareja padres hijos y hermanos`() {
        val sp = espacio()
        val padre = animal(sp, "Padre")
        val madre = animal(sp, "Madre")
        val h1 = animal(sp, "Hijo1", padre = padre, madre = madre)
        val h2 = animal(sp, "Hijo2", padre = padre, madre = madre)
        val otro = animal(sp, "Otro")
        e.datos.parejas(e.idPartida, padre, madre)
        e.animales.familiares(e.idPartida, h1) shouldBe setOf(padre, madre, h2)
        e.animales.familiares(e.idPartida, padre) shouldBe setOf(madre, h1, h2)
        e.animales.familiares(e.idPartida, otro) shouldBe emptySet()
        e.animales.familiaresDeCria(e.idPartida, padre, madre) shouldBe setOf(padre, madre, h1, h2)
    }

    @Test
    fun `la migracion 15 a 16 quita los mejores amigos que son familiares y conserva los demas`() {
        val sp = espacio()
        val padre = animal(sp, "Padre")
        val madre = animal(sp, "Madre")
        val hijo = animal(sp, "Hijo", padre = padre, madre = madre)
        val a = animal(sp, "A")
        val b = animal(sp, "B")
        e.datos.amigos(e.idPartida, padre, hijo) // familiares: se quita
        e.datos.amigos(e.idPartida, a, b) // sin parentesco: se conserva
        e.bd.migrarDe15a16()
        amigo(padre) shouldBe null
        amigo(hijo) shouldBe null
        amigo(a) shouldBe b
        amigo(b) shouldBe a
    }

    @Test
    fun `el panda tiene compatibilidad 1 con todos los habitats y los demas no cambian`() {
        val panda = e.cfg.tablas.compatibilidad.filter { it.idEspecie == "panda" }
        panda.size shouldBe e.cfg.catalogos.biomas.size
        panda.all { it.factor == 1.0 } shouldBe true
        // Otras especies siguen con sus factores (el león no es 1 en todos los biomas).
        e.cfg.tablas.compatibilidad.filter { it.idEspecie == "leon" }.any { it.factor < 1.0 } shouldBe true
        // Y la satisfaccion del panda no pierde nada por el habitat.
        e.regla.pHab("panda", e.cfg.catalogos.biomas.first().id) shouldBe 1.0
        e.cfg.catalogos.biomas.all { e.regla.pHab("panda", it.id) == 1.0 } shouldBe true
    }

    @Test
    fun `la migracion 16 a 17 pone el panda a 1 en el catalogo de la partida`() {
        e.bd.escribir("UPDATE CompatibilidadBioma SET factor_bioma = 0.4 WHERE id_especie = 'panda'")
        e.bd.migrarDe16a17()
        e.bd.leer("SELECT DISTINCT factor_bioma FROM CompatibilidadBioma WHERE id_especie = 'panda'") { it.decimal(0) } shouldBe listOf(1.0)
        (e.bd.uno("SELECT COUNT(*) FROM CompatibilidadBioma WHERE id_especie = 'leon' AND factor_bioma < 1") { it.entero(0) }!! > 0) shouldBe true
    }

    @Test
    fun `el tratamiento consume mas medicina cuanto mayor es el nivel`() {
        val sp = espacio()
        // Leon (rango 3): 3 unidades base. Cada nivel por encima del 1 suma un 5 % y se redondea hacia arriba.
        val esperadas = mapOf(1 to 3, 2 to 4, 10 to 5, 30 to 8, 41 to 9)
        esperadas.forEach { (nivel, unidades) ->
            val a = e.datos.animal(e.idPartida, sp, "leon", "Rey$nivel", enfermo = true, nivel = nivel)
            e.consultarCuidado.tratamiento(e.idPartida, a).map { it.necesario } shouldBe listOf(unidades)
        }
    }

    @Test
    fun `el tratamiento descuenta las unidades segun el nivel y rechaza si no alcanzan`() {
        val sp = espacio()
        val a = e.datos.animal(e.idPartida, sp, "leon", "Rey", enfermo = true, nivel = 30) // 8 unidades
        e.bd.escribir("UPDATE InventarioMedico SET cantidad = 7 WHERE id_partida = ?", e.idPartida)
        val m = rechazo(e.tratarAnimal.ejecutar(ComandoTratarAnimal(e.idPartida, a, nueva()))) as MotivoRechazo.InsumosInsuficientes
        m.faltantes shouldBe listOf(FaltanteInsumo("Medicina", 8, 7))
        e.bd.escribir("UPDATE InventarioMedico SET cantidad = 10 WHERE id_partida = ?", e.idPartida)
        (e.tratarAnimal.ejecutar(ComandoTratarAnimal(e.idPartida, a, nueva())) as Resultado.Exito).valor.unidades shouldBe 8
        e.bd.uno("SELECT cantidad FROM InventarioMedico WHERE id_partida = ?", e.idPartida) { it.entero(0) } shouldBe 2
    }
}
