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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
    private val codigosTecnicos = listOf("bosque_templado", "humedal_agua_dulce", "campos_de_africa", "insumo_medico", "bambu", "id_", "Ref")

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
    fun `T1_9 inicio muestra saldo inventario y beneficios sin claves`() =
        prueba { c ->
            esperar("Saldo")
            esperar("🪙 1000.00")
            esperar("Bambú")
            esperar("30 kg")
            esperar("3 unidades")
            esperar("🪙 0.00")
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `T1_9 flujo de compra de recursos de punta a punta`() =
        prueba { c ->
            esperar("Tienda de recursos")
            onNodeWithText("Tienda de recursos").performClick()
            esperar("Bambú — 🪙 0.50 por kg")
            onNodeWithText("Confirmar compra").assertDoesNotExist()
            onNodeWithText("Bambú — 🪙 0.50 por kg").performClick()
            onNodeWithText("Cantidad (kg)").performTextInput("10")
            esperar("Coste total: 🪙 5.00")
            sinIdentificadoresTecnicos(c)
            onNodeWithText("Confirmar compra").assertIsEnabled().performClick()
            esperar("Compra realizada: 🪙 5.00. Saldo: 🪙 995.00.")
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `T1_9 saldo insuficiente se informa con el motivo y no cambia el saldo`() =
        prueba {
            esperar("Tienda de recursos")
            onNodeWithText("Tienda de recursos").performClick()
            esperar("Insumo médico — 🪙 10.00 por unidad")
            onNodeWithText("Insumo médico — 🪙 10.00 por unidad").performClick()
            onNodeWithText("Cantidad (unidades)").performTextInput("101")
            onNodeWithText("Confirmar compra").performClick()
            esperar("Saldo insuficiente: cuesta 🪙 1010.00 y dispones de 🪙 1000.00.")
            esperar("🪙 1000.00", subcadena = true)
        }

    @Test
    fun `T1_9 navegar de habitats a espacios comprar y configurar un espacio`() =
        prueba { c ->
            esperar("Hábitats y espacios")
            onNodeWithText("Hábitats y espacios").performClick()
            esperar("Hábitat Bosque templado")
            esperar("Bloqueado")
            sinIdentificadoresTecnicos(c)

            onNodeWithText("Hábitat Bosque templado").performClick()
            esperar("Este hábitat aún no tiene espacios.")
            onNodeWithText("Comprar espacio").performClick()

            esperar("Denominación del espacio")
            onNodeWithText("Confirmar compra").assertIsNotEnabled()
            onNodeWithText("Denominación del espacio").performTextInput("Pradera")
            onNodeWithText("2 animales — 🪙 100.00").performClick()
            sinIdentificadoresTecnicos(c)
            onNodeWithText("Confirmar compra").assertIsEnabled().performClick()

            esperar("Pradera")
            esperar("Capacidad 2 · Ocupación 0/2")
            sinIdentificadoresTecnicos(c)

            onNodeWithText("Pradera").performClick()
            esperar("La capacidad no se puede modificar.")
            esperar("Hábitat Humedal de agua dulce")
            onNodeWithText("Hábitat Humedal de agua dulce").performClick()
            onNodeWithText("Guardar").performClick()

            // El espacio ya no está en el bosque: se vuelve a su lista, que queda vacía.
            esperar("Este hábitat aún no tiene espacios.")
            sinIdentificadoresTecnicos(c)
            c.consultarZoo.espacios(c.idPartida, "${c.idPartida}/humedal_agua_dulce").map { it.nombre } shouldBe listOf("Pradera")
        }
}
