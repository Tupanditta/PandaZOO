package com.pandazoo.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.pandazoo.application.caso.AlcanceRecalculo
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.data.depuracion.DatosDePrueba
import com.pandazoo.testing.ConfiguracionDePrueba
import com.pandazoo.ui.navegacion.PandaZooNavegacion
import com.pandazoo.ui.pantalla.LocalContenedor
import com.pandazoo.ui.pantalla.PandaZooTema
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Sprint 6 en la interfaz: indicador y panel de prestigio (CU-19, T6.10), avisos informativos y sin identificadores (RES-06). */
@OptIn(ExperimentalTestApi::class)
class Sprint6UiTest {
    private val regexUuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val codigosTecnicos = listOf("campos_de_esmeril", "bosque_templado", "leon", "id_", "Ref")

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

    private fun datos(c: AppContainer, n: Int = 2) {
        val d = DatosDePrueba(c.bd, c.ids)
        val espacio = d.espacio(c.idPartida, "${c.idPartida}/bosque_templado", "Madriguera")
        repeat(n) { d.animal(c.idPartida, espacio, "erizo", "Pincho ${it + 1}") }
        c.recalcularSatisfaccion.alConsolidar(c.idPartida, AlcanceRecalculo.Parque)
    }

    @Test
    fun `la cabecera muestra el nivel y el panel lista nivel, estadisticas y maximos sin claves`() =
        prueba { c ->
            esperar("Prestigio 0")
            onNodeWithContentDescription("Abrir panel de prestigio").performClick()
            esperar("Prestigio actual")
            esperar("Sin registrar")
            esperar("Siguiente prestigio · Prestigio 1")
            esperar("30%")
            esperar("Desbloquea hábitats")
            esperar("Campos de Esmeril")
            esperar("Animales vivos alojados")
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `tras una mutacion el panel muestra el nuevo nivel y los maximos`() =
        prueba { c ->
            datos(c)
            esperar("Prestigio 1")
            onNodeWithContentDescription("Abrir panel de prestigio").performClick()
            esperar("Siguiente prestigio · Prestigio 2")
            esperar("Animales vivos alojados")
            esperar("2")
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `los desbloqueos y el ascenso aparecen como avisos informativos legibles`() =
        prueba { c ->
            datos(c, 3)
            onNodeWithText("Avisos").performClick()
            esperar("Has alcanzado el prestigio 3.", subcadena = true)
            esperar("Nuevo hábitat disponible: Campos de Esmeril.", subcadena = true)
            esperar("Nueva especie disponible", subcadena = true)
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `T6_10 abrir el panel y volver no ejecuta ningun recalculo`() =
        prueba { c ->
            datos(c)
            val prestigio = c.recalcularPrestigio.invocaciones
            val satisfaccion = c.recalcularSatisfaccion.invocaciones
            onNodeWithContentDescription("Abrir panel de prestigio").performClick()
            esperar("Prestigio actual")
            onNodeWithContentDescription("Volver").performClick()
            esperar("Campos de Esmeril")
            c.recalcularPrestigio.invocaciones shouldBe prestigio
            c.recalcularSatisfaccion.invocaciones shouldBe satisfaccion
        }
}
