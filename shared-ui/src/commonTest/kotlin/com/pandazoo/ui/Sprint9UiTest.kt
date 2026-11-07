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
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.modelo.Seccion
import com.pandazoo.testing.ConfiguracionDePrueba
import com.pandazoo.ui.navegacion.PandaZooNavegacion
import com.pandazoo.ui.pantalla.LocalContenedor
import com.pandazoo.ui.pantalla.PandaZooTema
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Sprint 9 UI: misiones (CU-20) y reclamación de recompensas (CU-15) sin identificadores técnicos (RES-06). */
@OptIn(ExperimentalTestApi::class)
class Sprint9UiTest {
    private val regexUuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val codigosTecnicos = listOf("bosque_templado", "capibara", "nutria", "humedal_agua_dulce", "insumo_medico", "id_", "Ref")

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

    private fun fondos(c: AppContainer): Dinero = (c.consultarInventario.ejecutar(c.idPartida).fondos as Seccion.Disponible).valor

    /** Deja cumplida solo «Ten 5 animales en el zoo», para que haya un único botón de reclamar. */
    private fun cumplirTotal(c: AppContainer) {
        c.bd.escribir("UPDATE ProgresoMision SET estado = 'CUMPLIDA', progreso = 5 WHERE id_partida = ? AND id_mision = 'total_animales'", c.idPartida)
    }

    @Test
    fun `T9 las misiones se muestran con nombres descriptivos y la cumplida ofrece reclamar`() =
        prueba { c ->
            cumplirTotal(c)
            esperar("Misiones")
            onNodeWithText("Misiones").performClick()
            esperar("Ten 5 animales en el zoo.")
            esperar("Compra 2 ejemplares de", subcadena = true)
            esperar("Reclamar recompensa")
            textos().forEach { t ->
                regexUuid.containsMatchIn(t) shouldBe false
                t.contains(c.idPartida) shouldBe false
                codigosTecnicos.forEach { codigo -> t.contains(codigo) shouldBe false }
            }
        }

    @Test
    fun `T9 reclamar pide confirmacion acredita la recompensa y renueva la mision`() =
        prueba { c ->
            cumplirTotal(c)
            val antes = fondos(c)
            esperar("Misiones")
            onNodeWithText("Misiones").performClick()
            esperar("Ten 5 animales en el zoo.")

            // Cancelar no acredita nada (CU-15, 3.a).
            onNodeWithText("Reclamar recompensa").performScrollTo().performClick()
            esperar("Confirmar reclamación")
            onNodeWithText("Cancelar").performClick()
            fondos(c) shouldBe antes

            onNodeWithText("Reclamar recompensa").performScrollTo().performClick()
            esperar("Confirmar reclamación")
            onNodeWithText("Confirmar reclamación").performClick()
            esperar("Has recibido", subcadena = true)
            fondos(c) shouldBe antes + Dinero.desdeDecimal(300.0)
            // La misión se renueva con el objetivo progresado (5 + 5 = 10).
            esperar("Ten 10 animales en el zoo.")
        }
}
