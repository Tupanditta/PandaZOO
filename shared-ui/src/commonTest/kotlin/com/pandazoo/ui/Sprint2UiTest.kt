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

/** T2.10 y T2.7 en la interfaz: el monitor muestra los cuatro niveles con etiquetas legibles y no recalcula. */
@OptIn(ExperimentalTestApi::class)
class Sprint2UiTest {
    private val regexUuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val codigosTecnicos = listOf("campos_de_esmeril", "bosque_templado", "leon", "id_", "Ref")

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

    private fun ComposeUiTest.esperar(texto: String) {
        try {
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            throw AssertionError("No aparece \"$texto\". Textos en pantalla: ${textos()}", e)
        }
    }

    private fun datos(c: AppContainer) {
        val d = DatosDePrueba(c.bd, c.ids)
        val espacio = d.espacio(c.idPartida, "${c.idPartida}/campos_de_esmeril", "Sabana")
        d.animal(c.idPartida, espacio, "leon", "Simba")
        d.animal(c.idPartida, espacio, "leon", "Nala", alimentacion = 0)
        c.recalcularSatisfaccion.alConsolidar(c.idPartida, AlcanceRecalculo.Parque)
    }

    @Test
    fun `T2_10 el monitor muestra parque habitat espacio y animal con porcentajes enteros y sin claves`() =
        prueba { c ->
            datos(c)
            onNodeWithContentDescription("Abrir monitor de satisfacción").performClick()

            // (0.71 + 0.063) / 2 = 0.3865, que se muestra como 39 %.
            esperar("39%")
            esperar("▸ Campos de Esmeril")
            esperar("Bosque templado")
            esperar("Sin animales")
            sinIdentificadoresTecnicos(c)

            onNodeWithText("▸ Campos de Esmeril").performClick()
            esperar("▸ Sabana")
            onNodeWithText("▸ Sabana").performClick()
            esperar("Simba (León)")
            esperar("71%")
            esperar("Nala (León)")
            esperar("6%")
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `T2_10 sin animales el parque muestra 0 por ciento y todos los niveles sin metrica`() =
        prueba { c ->
            onNodeWithContentDescription("Abrir monitor de satisfacción").performClick()
            esperar("0%")
            esperar("Sin animales")
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `T2_7 abrir y recorrer pantallas no ejecuta el recalculo`() =
        prueba { c ->
            datos(c)
            val invocaciones = c.recalcularSatisfaccion.invocaciones
            onNodeWithContentDescription("Abrir monitor de satisfacción").performClick()
            esperar("39%")
            onNodeWithContentDescription("Volver").performClick()
            esperar("Campos de Esmeril")
            c.recalcularSatisfaccion.invocaciones shouldBe invocaciones
        }
}
