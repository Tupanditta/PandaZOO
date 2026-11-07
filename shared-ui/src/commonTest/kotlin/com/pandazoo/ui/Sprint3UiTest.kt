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
            val contenedor = AppContainer.crear(bd) { ConfiguracionDePrueba.texto }
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

    private fun espacio(c: AppContainer, nombre: String, habitat: String = "bosque_templado", capacidad: Int = 4): String {
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
            esperar("Bloqueada", subcadena = true)
            esperar("Exclusivo · solo por misión", subcadena = true)
            sinIdentificadoresTecnicos(c)

            onNodeWithText("Erizo").performClick()
            esperar("Comprar Erizo")
            esperar("Bosque templado")
            onNodeWithText("Bosque templado").performClick()
            esperar("Madriguera — Ocupación 0/4")
            onNodeWithText("Madriguera — Ocupación 0/4").performClick()

            esperar("Nombre del animal")
            onNodeWithText("Confirmar compra").performScrollTo().assertIsNotEnabled() // ficha incompleta
            onNodeWithText("Nombre del animal").performTextInput("Pincho")
            elegirCuatroEtiquetas()
            sinIdentificadoresTecnicos(c)
            onNodeWithText("Confirmar compra").performScrollTo().assertIsEnabled().performClick()

            esperar("Tienda de animales") // vuelve a la tienda tras comprar
            fondos(c) shouldBe antes - Dinero.desdeDecimal(200.0)

            onNodeWithContentDescription("Volver").performClick()
            onNodeWithContentDescription("Volver").performClick()
            irAlEspacio()
            esperar("Pincho")
            sinIdentificadoresTecnicos(c)
            onNodeWithText("Pincho").performClick()

            esperar("1 de 15") // nivel inicial y tope del rango Común
            esperar("0 de 72 ciclos sanos para el nivel 2")
            esperar("Común")
            esperar("100%")
            esperar("Sano")
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
            esperar("Madriguera — Ocupación 1/4")
            onNodeWithText("Madriguera — Ocupación 1/4").performClick()
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
            val refugio = espacio(c, "Refugio")
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

            // Trasladar al otro espacio: el de origen queda vacío y se ofrece su baja.
            pulsar("Trasladar")
            esperar("Espacio de destino")
            esperar("Bosque templado · Refugio (0/4)")
            onNodeWithText("Confirmar traslado").performScrollTo().assertIsNotEnabled()
            onNodeWithText("Bosque templado · Refugio (0/4)").performClick()
            onNodeWithText("Confirmar traslado").performScrollTo().assertIsEnabled().performClick()
            esperar("Espacio vacío")
            sinIdentificadoresTecnicos(c)
            onNodeWithText("Mantener").performClick()
            esperar("Cuidador, Líder")
            c.consultarZoo.espacio(c.idPartida, origen)!!.estado.name shouldBe "ACTIVO"

            // Vender: exige confirmación con el importe según el nivel, lo acredita y ofrece la baja del espacio vacío.
            val antes = fondos(c)
            pulsar("Vender")
            esperar("Vender animal")
            esperar("Vas a vender a Pincho (nivel 1) por 🪙 10.00", subcadena = true)
            esperar("En el nivel 10 se recupera el coste de compra", subcadena = true)
            onNodeWithText("Cancelar").performClick()
            c.bd.uno("SELECT COUNT(*) FROM Animal WHERE estado_vital = 'VIVO'") { it.entero(0) } shouldBe 1
            pulsar("Vender")
            onNodeWithText("Confirmar venta").performClick()
            esperar("Espacio vacío")
            onNodeWithText("Dar de baja").performClick()

            // Tras la venta se vuelve a la pantalla del espacio original, activo y vacío.
            esperar("Este espacio no tiene animales.")
            c.bd.uno("SELECT estado_vital FROM Animal") { it.texto(0) } shouldBe "VENDIDO"
            fondos(c) shouldBe antes + Dinero.desdeDecimal(10.0)
            c.consultarZoo.espacio(c.idPartida, refugio)!!.estado.name shouldBe "INACTIVO"
            c.consultarZoo.espacio(c.idPartida, origen)!!.estado.name shouldBe "ACTIVO"
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `T3_14 dar de baja un espacio vacio desde su pantalla pide confirmacion`() =
        prueba { c ->
            val sp = espacio(c, "Madriguera")
            irAlEspacio()
            esperar("Dar de baja el espacio")
            onNodeWithText("Dar de baja el espacio").performClick()
            esperar("Confirmar baja")
            onNodeWithText("Cancelar").performClick()
            c.consultarZoo.espacio(c.idPartida, sp)!!.estado.name shouldBe "ACTIVO"
            onNodeWithText("Dar de baja el espacio").performClick()
            onNodeWithText("Confirmar baja").performClick()
            esperar("De baja", subcadena = true) // vuelve a la lista del hábitat, donde figura de baja
            c.consultarZoo.espacio(c.idPartida, sp)!!.estado.name shouldBe "INACTIVO"
        }

    @Test
    fun `TN_12 la ficha muestra el nivel su progreso el rango y el valor de venta sin edad ni identificadores`() =
        prueba { c ->
            val sp = espacio(c, "Madriguera")
            DatosDePrueba(c.bd, c.ids).animal(c.idPartida, sp, "erizo", "Veterano", nivel = 15)
            DatosDePrueba(c.bd, c.ids).animal(c.idPartida, sp, "erizo", "Novato", nivel = 8, ciclosNivel = 30)
            irAlEspacio()
            esperar("Nivel 15 · Alimentación 100%", subcadena = true)
            onNodeWithText("Veterano").performClick()
            esperar("15 de 15")
            esperar("Nivel máximo alcanzado")
            esperar("Común")
            esperar("🪙 300.00") // venta en el tope del rango 1
            textos().forEach { t ->
                t.contains("Edad", ignoreCase = true) shouldBe false
                t.contains("años") shouldBe false
            }
            sinIdentificadoresTecnicos(c)
            onNodeWithContentDescription("Volver").performClick()
            onNodeWithText("Novato").performClick()
            esperar("8 de 15")
            esperar("30 de 72 ciclos sanos para el nivel 9")
            esperar("🪙 ", subcadena = true)
            sinIdentificadoresTecnicos(c)
        }
}
