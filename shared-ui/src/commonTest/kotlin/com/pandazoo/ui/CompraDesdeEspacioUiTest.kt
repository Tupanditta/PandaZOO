package com.pandazoo.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.pandazoo.application.caso.ComandoAdquirirEspacio
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.testing.ConfiguracionDePrueba
import com.pandazoo.ui.navegacion.PandaZooNavegacion
import com.pandazoo.ui.pantalla.LocalContenedor
import com.pandazoo.ui.pantalla.PandaZooTema
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Compra de un animal iniciada desde un espacio con cupo: hábitat y espacio vienen fijos y se vuelve al espacio. */
@OptIn(ExperimentalTestApi::class)
class CompraDesdeEspacioUiTest {
    private fun ComposeUiTest.esperar(texto: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.hay(texto: String) = onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `desde un espacio con cupo se compra con hábitat y espacio fijos y se vuelve al espacio`() =
        runComposeUiTest {
            val bd = BaseDatos.enMemoria()
            val c = AppContainer.crear(bd, conBienvenida = false) { ConfiguracionDePrueba.texto }
            c.adquirirEspacio.ejecutar(ComandoAdquirirEspacio(c.idPartida, "${c.idPartida}/bosque_templado", "Madriguera", 8, "sol-1"))
            setContent {
                CompositionLocalProvider(LocalContenedor provides c) { PandaZooTema { PandaZooNavegacion() } }
            }
            try {
                esperar("Bosque templado")
                onNodeWithText("Bosque templado").performClick()
                esperar("Madriguera")
                onNodeWithText("Madriguera").performClick()
                esperar("Comprar un animal para este espacio")
                onNodeWithText("Comprar un animal para este espacio").performClick()

                esperar("Tienda de animales")
                onNodeWithText("Erizo").performClick()
                esperar("Comprar Erizo")
                // Ubicación ya escrita: no hay listas para elegirla y la ficha técnica está disponible de inmediato.
                esperar("Madriguera")
                esperar("Ficha técnica")
                hay("Espacio") shouldBe true
                onAllNodesWithText("Madriguera — Ocupación 0/8").fetchSemanticsNodes().isEmpty() shouldBe true

                onNodeWithText("Nombre del animal").performTextInput("Pincho")
                listOf("Líder", "Cuidador", "Curioso", "Tímido").forEach { onNodeWithText(it).performScrollTo().performClick() }
                onNodeWithText("Confirmar compra").performScrollTo().assertIsEnabled().performClick()

                // Vuelve directamente al espacio, ya con el animal, sin pasar por la tienda.
                esperar("Pincho")
                hay("Tienda de animales") shouldBe false
                c.consultarZoo.espacios(c.idPartida, "${c.idPartida}/bosque_templado").first { it.nombre == "Madriguera" }.ocupacion shouldBe 1
            } finally {
                bd.cerrar()
            }
        }
}
