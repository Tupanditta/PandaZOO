package com.pandazoo.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.data.depuracion.DatosDePrueba
import com.pandazoo.testing.ConfiguracionDePrueba
import com.pandazoo.ui.navegacion.PandaZooNavegacion
import com.pandazoo.ui.pantalla.LocalContenedor
import com.pandazoo.ui.pantalla.PandaZooTema
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Sprint 7 en la interfaz: aviso de reproducción, formulario de la cría (CU-10) y ausencia de identificadores (RES-06). */
@OptIn(ExperimentalTestApi::class)
class Sprint7UiTest {
    private val regexUuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val codigosTecnicos = listOf("bosque_templado", "erizo", "lider", "id_", "Ref", "REPRODUCCION")

    private fun prueba(bloque: ComposeUiTest.(AppContainer) -> Unit) =
        runComposeUiTest {
            val bd = BaseDatos.enMemoria()
            val contenedor = AppContainer.crear(bd, depuracion = true) { ConfiguracionDePrueba.texto }
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

    private fun ComposeUiTest.pulsar(texto: String) {
        onNodeWithText(texto).performScrollTo().performClick()
    }

    /** Pareja de erizos con satisfacción máxima y la reproducción garantizada en el siguiente ciclo. */
    private fun pareja(c: AppContainer, capacidad: Int = 4) {
        val d = DatosDePrueba(c.bd, c.ids)
        val espacio = d.espacio(c.idPartida, "${c.idPartida}/bosque_templado", "Madriguera", capacidad)
        val a = d.animal(c.idPartida, espacio, "erizo", "Pincho")
        val b = d.animal(c.idPartida, espacio, "erizo", "Espina")
        d.parejas(c.idPartida, a, b)
        c.bd.escribir("UPDATE Animal SET satisfaccion_actual = 1.0 WHERE id_partida = ?", c.idPartida)
        c.bd.escribir("UPDATE RangoEspecie SET umbral_reproduccion = 0.5, probabilidad_reproduccion = 1.0, penalizacion_hijo_reproduccion = 0.0")
    }

    private fun vivos(c: AppContainer) = c.bd.uno("SELECT COUNT(*) FROM Animal WHERE estado_vital = 'VIVO'") { it.entero(0) }

    @Test
    fun `el aviso de reproduccion se lista con nombres y abre el formulario de la cria`() =
        prueba { c ->
            pareja(c)
            c.adelantarCiclo() shouldBe true
            onNodeWithText("Avisos").performClick()
            esperar("esperan una cría", subcadena = true)
            sinIdentificadoresTecnicos(c)
            onNodeWithText("esperan una cría", substring = true).performClick()
            esperar("Registrar cría")
            onNodeWithText("Registrar cría").performClick()
            esperar("Ficha técnica de la cría")
            esperar("Progenitores")
            sinIdentificadoresTecnicos(c)

            onNodeWithText("Confirmar registro").performScrollTo().assertIsNotEnabled() // ficha incompleta
            onNodeWithText("Nombre de la cría").performTextInput("Brote")
            listOf("Líder", "Cuidador", "Curioso", "Tímido").forEach { pulsar(it) }
            onNodeWithText("Confirmar registro").performScrollTo().assertIsEnabled().performClick()

            esperar("Avisos")
            vivos(c) shouldBe 3
            esperar("Aceptada", subcadena = true)
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `con el espacio lleno el formulario muestra el motivo y no permite registrar`() =
        prueba { c ->
            pareja(c, capacidad = 2)
            c.adelantarCiclo() shouldBe true
            onNodeWithText("Avisos").performClick()
            esperar("esperan una cría", subcadena = true)
            onNodeWithText("esperan una cría", substring = true).performClick()
            onNodeWithText("Registrar cría").performClick()
            esperar("El espacio no tiene capacidad libre.")
            onNodeWithText("Nombre de la cría").performTextInput("Brote")
            listOf("Líder", "Cuidador", "Curioso", "Tímido").forEach { pulsar(it) }
            onNodeWithText("Confirmar registro").performScrollTo().assertIsNotEnabled()
            vivos(c) shouldBe 2
        }
}
