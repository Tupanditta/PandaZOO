package com.pandazoo.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.pandazoo.application.caso.ComandoAdquirirEspacio
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.data.depuracion.DatosDePrueba
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.modelo.Seccion
import com.pandazoo.testing.ConfiguracionDePrueba
import com.pandazoo.ui.navegacion.PandaZooNavegacion
import com.pandazoo.ui.pantalla.LocalContenedor
import com.pandazoo.ui.pantalla.PandaZooTema
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** T3.14 UI: compra de animales de punta a punta, ficha sin claves, edición, traslado, venta y baja del espacio vacío. */
@OptIn(ExperimentalTestApi::class)
class Sprint3UiTest {
    private val regexUuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val codigosTecnicos = listOf("bosque_templado", "humedal_agua_dulce", "erizo", "lider", "cuidador", "curioso", "timido", "id_", "Ref")

    private fun prueba(bloque: ComposeUiTest.(AppContainer) -> Unit) =
        runComposeUiTest {
            val bd = BaseDatos.enMemoria()
            val contenedor = AppContainer.crear(bd, conBienvenida = false) { ConfiguracionDePrueba.texto }
            setContent {
                CompositionLocalProvider(LocalContenedor provides contenedor) {
                    PandaZooTema { PandaZooNavegacion() }
                }
            }
            try {
                bloque(contenedor)
            } finally {
                bd.cerrar()
            }
        }

    private fun ComposeUiTest.textos(): List<String> {
        val salida = mutableListOf<String>()

        fun recorrer(n: SemanticsNode) {
            n.config.getOrNull(SemanticsProperties.Text)?.forEach { salida += it.text }
            n.config.getOrNull(SemanticsProperties.EditableText)?.let { salida += it.text }
            n.children.forEach(::recorrer)
        }
        // Un diálogo abierto es una segunda raíz de semántica.
        onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach(::recorrer)
        return salida
    }

    private fun ComposeUiTest.sinIdentificadoresTecnicos(c: AppContainer) {
        textos().forEach { t ->
            regexUuid.containsMatchIn(t) shouldBe false
            t.contains(c.idPartida) shouldBe false
            codigosTecnicos.forEach { codigo -> t.contains(codigo) shouldBe false }
        }
    }

    private fun ComposeUiTest.esperar(texto: String, subcadena: Boolean = false) {
        try {
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(texto, substring = subcadena).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            throw AssertionError("No aparece \"$texto\". Textos en pantalla: ${textos()}", e)
        }
    }

    private fun espacio(c: AppContainer, nombre: String, habitat: String = "bosque_templado", capacidad: Int = 8): String {
        c.adquirirEspacio.ejecutar(ComandoAdquirirEspacio(c.idPartida, "${c.idPartida}/$habitat", nombre, capacidad, "sol-$nombre"))
        return c.consultarZoo.espacios(c.idPartida, "${c.idPartida}/$habitat").first { it.nombre == nombre }.id
    }

    private fun ComposeUiTest.pulsar(texto: String) {
        onNodeWithText(texto).performScrollTo().performClick()
    }

    /** Del inicio al espacio indicado del bosque templado. */
    private fun ComposeUiTest.irAlEspacio(nombre: String = "Madriguera") {
        esperar("Bosque templado")
        onNodeWithText("Bosque templado").performClick()
        esperar(nombre)
        onNodeWithText(nombre).performClick()
    }

    private fun ComposeUiTest.elegirCuatroEtiquetas() {
        listOf("Líder", "Cuidador", "Curioso", "Tímido").forEach { pulsar(it) }
    }

    private fun fondos(c: AppContainer): Dinero = (c.consultarInventario.ejecutar(c.idPartida).fondos as Seccion.Disponible).valor

    @Test
    fun `T3_14 compra de un animal de punta a punta y ficha sin claves`() =
        prueba { c ->
            espacio(c, "Madriguera")
            val antes = fondos(c)
            onNodeWithContentDescription("Abrir tienda").performClick()
            pulsar("Comprar animales")
            esperar("Tienda de animales")
            esperar("Prestigio 1", subcadena = true) // las bloqueadas indican el nivel que las desbloquea
            esperar("Exclusivo · solo por misión", subcadena = true)
            textos().none { it.contains("Bloqueada") || it.contains("Disponible") } shouldBe true
            sinIdentificadoresTecnicos(c)

            onNodeWithText("Erizo").performClick()
            esperar("Comprar Erizo")
            esperar("Bosque templado")
            onNodeWithText("Bosque templado").performClick()
            esperar("Madriguera — Ocupación 0/8")
            onNodeWithText("Madriguera — Ocupación 0/8").performClick()

            esperar("Nombre del animal")
            onNodeWithText("Confirmar compra").performScrollTo().assertIsNotEnabled() // ficha incompleta
            onNodeWithText("Nombre del animal").performTextInput("Pincho")
            elegirCuatroEtiquetas()
            sinIdentificadoresTecnicos(c)
            onNodeWithText("Confirmar compra").performScrollTo().assertIsEnabled().performClick()

            esperar("Tienda de animales") // vuelve a la tienda tras comprar
            fondos(c) shouldBe antes - Dinero.desdeDecimal(40.0)

            onNodeWithContentDescription("Volver").performClick()
            onNodeWithContentDescription("Volver").performClick()
            irAlEspacio()
            esperar("Pincho")
            sinIdentificadoresTecnicos(c)
            onNodeWithText("Pincho").performClick()

            esperar("Pincho · Nivel 1") // nivel inicial junto al nombre
            esperar("0 de 72 ciclos sanos para el nivel 2")
            esperar("100%")
            esperar("Cuidador, Líder")
            esperar("Curioso, Tímido")
            sinIdentificadoresTecnicos(c)
            onNodeWithText("Editar ficha").assertIsEnabled()
        }

    @Test
    fun `T3_14 un nombre repetido se acepta y la lista distingue los homonimos sin claves`() =
        prueba { c ->
            val sp = espacio(c, "Madriguera")
            DatosDePrueba(c.bd, c.ids).animal(c.idPartida, sp, "erizo", "Pincho", nivel = 3)
            onNodeWithContentDescription("Abrir tienda").performClick()
            pulsar("Comprar animales")
            esperar("Erizo")
            onNodeWithText("Erizo").performClick()
            esperar("Bosque templado")
            onNodeWithText("Bosque templado").performClick()
            esperar("Madriguera — Ocupación 1/8")
            onNodeWithText("Madriguera — Ocupación 1/8").performClick()
            esperar("Nombre del animal")
            onNodeWithText("Nombre del animal").performTextInput("Pincho")
            elegirCuatroEtiquetas()
            pulsar("Confirmar compra")
            esperar("Tienda de animales")
            c.bd.uno("SELECT COUNT(*) FROM Animal") { it.entero(0) } shouldBe 2

            onNodeWithContentDescription("Volver").performClick()
            onNodeWithContentDescription("Volver").performClick()
            irAlEspacio()
            esperar("Pincho · nivel 1")
            esperar("Pincho · nivel 3")
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `T3_14 sin espacios con capacidad se avisa y no se puede elegir ubicacion`() =
        prueba { c ->
            onNodeWithContentDescription("Abrir tienda").performClick()
            pulsar("Comprar animales")
            esperar("Erizo")
            onNodeWithText("Erizo").performClick()
            esperar("No hay ningún espacio con capacidad libre. Compra un espacio antes de adquirir animales.")
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `T3_14 editar la ficha trasladar y vender con baja del espacio vacio`() =
        prueba { c ->
            val origen = espacio(c, "Madriguera")
            val refugio = espacio(c, "Cobijo")
            DatosDePrueba(c.bd, c.ids).animal(c.idPartida, origen, "erizo", "Pincho")
            irAlEspacio()
            esperar("Pincho")
            onNodeWithText("Pincho").performClick()

            // Editar: sin dos habilidades y dos rasgos no se puede guardar.
            esperar("Editar ficha")
            pulsar("Editar ficha")
            esperar("Especie, nivel, alimentación, salud y satisfacción no se pueden editar.")
            onNodeWithText("Guardar").performScrollTo().assertIsNotEnabled()
            elegirCuatroEtiquetas()
            onNodeWithText("Guardar").performScrollTo().assertIsEnabled().performClick()
            esperar("Cuidador, Líder")

            // Trasladar al otro espacio: el de origen queda vacío.
            pulsar("Trasladar")
            esperar("Espacio de destino")
            esperar("Bosque templado", subcadena = true)
            onNodeWithText("Confirmar traslado").performScrollTo().assertIsNotEnabled()
            onNodeWithText("Bosque templado", substring = true).performClick()
            esperar("Cobijo")
            esperar("0/8")
            onNodeWithText("Cobijo").performClick()
            onNodeWithText("Confirmar traslado").performScrollTo().assertIsEnabled().performClick()
            sinIdentificadoresTecnicos(c)
            esperar("Cuidador, Líder")
            c.consultarZoo.espacio(c.idPartida, origen)!!.estado.name shouldBe "ACTIVO"

            // Vender: exige confirmación con el importe según el nivel, lo acredita y deja vacío el espacio, que entonces puede venderse.
            val antes = fondos(c)
            val reembolsoOrigen = c.consultarZoo.costePagado(c.idPartida, origen)
            pulsar("Vender")
            esperar("Vender a Pincho")
            esperar("🪙 2.00", subcadena = true)
            esperar("nivel de equilibrio")
            onNodeWithText("Cancelar").performClick()
            c.bd.uno("SELECT COUNT(*) FROM Animal WHERE estado_vital = 'VIVO'") { it.entero(0) } shouldBe 1
            pulsar("Vender")
            onNodeWithText("Confirmar venta").performClick()

            // Tras la venta se vuelve a la pantalla del espacio original, ya vacío: ahí aparece el botón para venderlo.
            esperar("Vender espacio")
            c.bd.uno("SELECT estado_vital FROM Animal") { it.texto(0) } shouldBe "VENDIDO"
            fondos(c) shouldBe antes + Dinero.desdeDecimal(2.0)
            c.consultarZoo.espacio(c.idPartida, refugio)!!.estado.name shouldBe "ACTIVO" // el vacío no se da de baja solo
            esperar("Vender espacio")
            onNodeWithText("Vender espacio").performClick()
            esperar("recuperas")
            onNodeWithText("Confirmar venta del espacio").performClick()
            esperar("Cobijo") // vuelve a la lista del hábitat, donde el espacio vendido ya no figura
            c.consultarZoo.espacio(c.idPartida, origen) shouldBe null
            fondos(c) shouldBe antes + Dinero.desdeDecimal(2.0) + reembolsoOrigen // vender el espacio devuelve lo que costó
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `T3_14 dar de baja un espacio vacio desde su pantalla pide confirmacion`() =
        prueba { c ->
            val sp = espacio(c, "Madriguera")
            irAlEspacio()
            esperar("Vender espacio")
            onNodeWithText("Vender espacio").performClick()
            esperar("Confirmar venta del espacio")
            onNodeWithText("Cancelar").performClick()
            c.consultarZoo.espacio(c.idPartida, sp)!!.estado.name shouldBe "ACTIVO"
            onNodeWithText("Vender espacio").performClick()
            onNodeWithText("Confirmar venta del espacio").performClick()
            esperar("Comprar espacio") // vuelve al hábitat, donde ya no figura
            c.consultarZoo.espacio(c.idPartida, sp) shouldBe null
        }

    @Test
    fun `TN_12 la ficha muestra el nivel su progreso el rango y el valor de venta sin edad ni identificadores`() =
        prueba { c ->
            val sp = espacio(c, "Madriguera")
            DatosDePrueba(c.bd, c.ids).animal(c.idPartida, sp, "erizo", "Veterano", nivel = 15)
            DatosDePrueba(c.bd, c.ids).animal(c.idPartida, sp, "erizo", "Novato", nivel = 8, ciclosNivel = 30)
            irAlEspacio()
            esperar("Nivel 15 · Satisfacción", subcadena = true)
            onNodeWithText("Veterano").performClick()
            esperar("Veterano · Nivel 15")
            esperar("Nivel máximo alcanzado")
            esperar("🪙 60.00") // venta en el tope del rango 1
            textos().forEach { t ->
                t.contains("Edad", ignoreCase = true) shouldBe false
                t.contains("años") shouldBe false
            }
            sinIdentificadoresTecnicos(c)
            onNodeWithContentDescription("Volver").performClick()
            onNodeWithText("Novato").performClick()
            esperar("Novato · Nivel 8")
            esperar("30 de 72 ciclos sanos para el nivel 9")
            esperar("🪙 ", subcadena = true)
            sinIdentificadoresTecnicos(c)
        }
}
