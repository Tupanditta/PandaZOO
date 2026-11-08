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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.testing.ConfiguracionDePrueba
import com.pandazoo.ui.navegacion.PandaZooNavegacion
import com.pandazoo.ui.pantalla.LocalContenedor
import com.pandazoo.ui.pantalla.PandaZooTema
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** T1.9 UI: navegación Hábitats a Espacios y ninguna pantalla muestra identificadores técnicos (RES-06). */
@OptIn(ExperimentalTestApi::class)
class Sprint1UiTest {
    private val regexUuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val codigosTecnicos = listOf("bosque_templado", "humedal_agua_dulce", "campos_de_esmeril", "insumo_medico", "bambu", "id_", "Ref")

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
        recorrer(onRoot(useUnmergedTree = true).fetchSemanticsNode())
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

    @Test
    fun `T1_9 la cabecera muestra saldo inventario y los beneficios sin claves`() =
        prueba { c ->
            esperar("2000.00")
            esperar("30 kg")
            onNodeWithContentDescription("Ver beneficios").performClick()
            esperar("Beneficios pendientes")
            esperar("🪙 0.00")
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `T1_9 flujo de compra de recursos de punta a punta`() =
        prueba { c ->
            onNodeWithContentDescription("Abrir tienda").performClick()
            esperar("Bambú — 🪙 0.50 por kg")
            onNodeWithText("Confirmar compra").assertDoesNotExist()
            onNodeWithText("Bambú — 🪙 0.50 por kg").performClick()
            onNodeWithText("Cantidad (kg)").performTextInput("10")
            esperar("Coste total: 🪙 5.00")
            sinIdentificadoresTecnicos(c)
            onNodeWithText("Confirmar compra").assertIsEnabled().performScrollTo().performClick()
            esperar("Compra realizada: 🪙 5.00. Saldo: 🪙 1995.00.")
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `T1_9 saldo insuficiente se informa con el motivo y no cambia el saldo`() =
        prueba {
            onNodeWithContentDescription("Abrir tienda").performClick()
            esperar("Medicina — 🪙 10.00 por unidad")
            onNodeWithText("Medicina — 🪙 10.00 por unidad").performClick()
            onNodeWithText("Cantidad (unidades)").performTextInput("201")
            onNodeWithText("Confirmar compra").performScrollTo().performClick()
            esperar("Saldo insuficiente: cuesta 🪙 2010.00 y dispones de 🪙 2000.00.")
            esperar("🪙 2000.00", subcadena = true)
        }

    @Test
    fun `T1_9 navegar de habitats a espacios comprar y configurar un espacio`() =
        prueba { c ->
            esperar("Bosque templado")
            sinIdentificadoresTecnicos(c)

            onNodeWithText("Bosque templado").performClick()
            esperar("Comprar espacio")
            onNodeWithText("Comprar espacio").performClick()

            // La denominación solo se pide tras elegir la capacidad, y comprar solo se ofrece con un nombre válido.
            esperar("6 animales")
            onAllNodesWithText("Denominación del espacio").fetchSemanticsNodes().isEmpty() shouldBe true
            onNodeWithText("6 animales").performClick()
            esperar("Denominación del espacio")
            onAllNodesWithText("Comprar — Gratis").fetchSemanticsNodes().isEmpty() shouldBe true
            onNodeWithText("Denominación del espacio").performTextInput("Pradera")
            esperar("Comprar — Gratis")
            onNodeWithText("Comprar — Gratis").performClick()

            esperar("Pradera")
            esperar("0/6")
            sinIdentificadoresTecnicos(c)

            onNodeWithText("Pradera").performClick()
            esperar("Configurar espacio")
            onNodeWithText("Configurar espacio").performClick()
            esperar("Ampliar capacidad")
            onNodeWithText("Denominación").performTextReplacement("Dehesa")
            onNodeWithText("Guardar").performClick()

            // Se vuelve a la pantalla del espacio y, desde ella, a la lista del bosque con el nuevo nombre.
            esperar("Configurar espacio")
            onNodeWithContentDescription("Volver").performClick()
            esperar("Dehesa")
            sinIdentificadoresTecnicos(c)
            c.consultarZoo.espacios(c.idPartida, "${c.idPartida}/bosque_templado").map { it.nombre } shouldBe listOf("Dehesa")
        }
}
