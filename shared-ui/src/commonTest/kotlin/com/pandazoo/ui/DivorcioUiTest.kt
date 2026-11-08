package com.pandazoo.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.pandazoo.application.caso.ComandoAdquirirEspacio
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.data.depuracion.DatosDePrueba
import com.pandazoo.testing.ConfiguracionDePrueba
import com.pandazoo.ui.navegacion.PandaZooNavegacion
import com.pandazoo.ui.pantalla.LocalContenedor
import com.pandazoo.ui.pantalla.PandaZooTema
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Divorcio desde la ficha: confirmación con consecuencias, ventana de ánimo y edición sin cambio de pareja. */
@OptIn(ExperimentalTestApi::class)
class DivorcioUiTest {
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
        onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach(::recorrer)
        return salida
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

    private fun preparar(c: AppContainer): Pair<String, String> {
        c.adquirirEspacio.ejecutar(ComandoAdquirirEspacio(c.idPartida, "${c.idPartida}/bosque_templado", "Madriguera", 8, "sol-1"))
        val sp = c.consultarZoo.espacios(c.idPartida, "${c.idPartida}/bosque_templado").first { it.nombre == "Madriguera" }.id
        val datos = DatosDePrueba(c.bd, c.ids)
        val pincho = datos.animal(c.idPartida, sp, "erizo", "Pincho")
        val zarza = datos.animal(c.idPartida, sp, "erizo", "Zarza")
        datos.parejas(c.idPartida, pincho, zarza)
        return pincho to zarza
    }

    private fun ComposeUiTest.abrirFicha() {
        esperar("Bosque templado")
        onNodeWithText("Bosque templado").performClick()
        esperar("Madriguera")
        onNodeWithText("Madriguera").performClick()
        esperar("Pincho")
        onNodeWithText("Pincho").performClick()
    }

    private fun pareja(c: AppContainer, nombre: String) =
        c.bd.uno("SELECT id_pareja FROM Animal WHERE nombre = ?", nombre) { it.textoONulo(0) }

    @Test
    fun `el boton de divorcio solo aparece con pareja y no hace nada hasta aceptar`() =
        prueba { c ->
            preparar(c)
            abrirFicha()
            esperar("Divorcio")
            pulsar("Divorcio")
            esperar("Acepto")
            esperar("Si tienen hijos en común", subcadena = true)
            // Cancelar no realiza la operación.
            onNodeWithText("Cancelar").performClick()
            waitForIdle()
            (pareja(c, "Pincho") != null) shouldBe true
            (pareja(c, "Zarza") != null) shouldBe true
            onAllNodesWithText("Acepto").fetchSemanticsNodes().size shouldBe 0
        }

    @Test
    fun `aceptar el divorcio quita la pareja y muestra la ventana de animo`() =
        prueba { c ->
            preparar(c)
            abrirFicha()
            esperar("Divorcio")
            pulsar("Divorcio")
            onNodeWithText("Acepto").performClick()
            esperar("Ánimo My Sheila", subcadena = true)
            pareja(c, "Pincho") shouldBe null
            pareja(c, "Zarza") shouldBe null
        }

    @Test
    fun `un animal sin pareja no tiene boton de divorcio`() =
        prueba { c ->
            c.adquirirEspacio.ejecutar(ComandoAdquirirEspacio(c.idPartida, "${c.idPartida}/bosque_templado", "Madriguera", 8, "sol-1"))
            val sp = c.consultarZoo.espacios(c.idPartida, "${c.idPartida}/bosque_templado").first { it.nombre == "Madriguera" }.id
            DatosDePrueba(c.bd, c.ids).animal(c.idPartida, sp, "erizo", "Pincho")
            abrirFicha()
            esperar("Editar ficha")
            onAllNodesWithText("Divorcio").fetchSemanticsNodes().size shouldBe 0
        }

    @Test
    fun `editar la ficha de un animal con pareja muestra quien es pero no permite cambiarla`() =
        prueba { c ->
            preparar(c)
            abrirFicha()
            esperar("Editar ficha")
            pulsar("Editar ficha")
            esperar("Para dejar a la pareja", subcadena = true)
            esperar("Zarza")
            onAllNodesWithText("Sin pareja").fetchSemanticsNodes().size shouldBe 0
        }
}
