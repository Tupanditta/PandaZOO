package com.pandazoo.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.testing.ConfiguracionDePrueba
import com.pandazoo.ui.pantalla.Iconos
import com.pandazoo.ui.pantalla.LocalContenedor
import com.pandazoo.ui.pantalla.PremioUi
import com.pandazoo.ui.pantalla.VentanaPremio
import com.pandazoo.ui.pantalla.PandaZooTema
import com.pandazoo.ui.pantalla.Textos
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Regalo de bienvenida de la partida nueva: una ventana por recurso, empezando por el dinero. */
@OptIn(ExperimentalTestApi::class)
class BienvenidaUiTest {
    @Test
    fun `una partida nueva ofrece una ventana por recurso y la existente no`() {
        val bd = BaseDatos.enMemoria()
        try {
            val nueva = AppContainer.crear(bd) { ConfiguracionDePrueba.texto }
            val premios = Textos.bienvenida(nueva.regaloBienvenida!!)
            premios.size shouldBe 3 + 6
            premios[0].valor shouldBe "🪙 2000.00"
            premios[1].valor shouldBe "30 kg de bambú"
            premios[2].valor shouldBe "3 medicinas"
            premios.drop(3).map { it.rango }.sortedBy { it!! }.size shouldBe 6
            AppContainer.crear(bd) { ConfiguracionDePrueba.texto }.regaloBienvenida shouldBe null
        } finally {
            bd.cerrar()
        }
    }

    @Test
    fun `la primera ventana muestra el regalo de dinero`() =
        runComposeUiTest {
            val bd = BaseDatos.enMemoria()
            val c = AppContainer.crear(bd) { ConfiguracionDePrueba.texto }
            setContent {
                CompositionLocalProvider(LocalContenedor provides c) { PandaZooTema { VentanasBienvenida(c) } }
            }
            try {
                waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Regalo de bienvenida").fetchSemanticsNodes().isNotEmpty() }
                onAllNodesWithText("🪙 2000.00").fetchSemanticsNodes().isNotEmpty() shouldBe true
            } finally {
                bd.cerrar()
            }
        }

    @Test
    fun `un premio sorteado muestra las probabilidades y solo revela el animal al pulsar suerte`() =
        runComposeUiTest {
            val premio = PremioUi(
                Iconos.Pata, "Panda", exclusivo = true, rango = "Exclusivo", titulo = "Regalo de bienvenida",
                probabilidades = listOf("Poco común" to 50, "Épico" to 30, "Exclusivo" to 20),
            )
            setContent { PandaZooTema { VentanaPremio(premio) {} } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("¡SUERTE!").fetchSemanticsNodes().isNotEmpty() }
            onAllNodesWithText("20 %").fetchSemanticsNodes().isNotEmpty() shouldBe true
            onAllNodesWithText("Panda").fetchSemanticsNodes().isEmpty() shouldBe true
            onNodeWithText("¡SUERTE!").performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Panda").fetchSemanticsNodes().isNotEmpty() }
            onAllNodesWithText("¡SUERTE!").fetchSemanticsNodes().isEmpty() shouldBe true
        }
}
