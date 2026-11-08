package com.pandazoo.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.testing.ConfiguracionDePrueba
import com.pandazoo.ui.pantalla.Atajos
import com.pandazoo.ui.pantalla.LocalAtajos
import com.pandazoo.ui.pantalla.LocalContenedor
import com.pandazoo.ui.pantalla.PandaZooTema
import com.pandazoo.ui.pantalla.PantallaGuia
import com.pandazoo.ui.pantalla.nombreCortoHabitat
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Guía de convivencia: el índice lleva al apartado y sus dos pestañas. */
@OptIn(ExperimentalTestApi::class)
class GuiaUiTest {
    @Test
    fun `el nombre corto de un habitat usa la primera y la ultima palabra significativas`() {
        nombreCortoHabitat("Campos de Esmeril") shouldBe "Campos Esmeril"
        nombreCortoHabitat("Bosque templado") shouldBe "Bosque templado"
        nombreCortoHabitat("Bosque australiano de eucaliptos") shouldBe "Bosque eucaliptos"
    }

    @Test
    fun `la guia abre convivencia y alterna entre habitats y animales`() =
        runComposeUiTest {
            val bd = BaseDatos.enMemoria()
            val c = AppContainer.crear(bd, conBienvenida = false) { ConfiguracionDePrueba.texto }
            val nada = {}
            setContent {
                CompositionLocalProvider(
                    LocalContenedor provides c,
                    LocalAtajos provides Atajos(nada, nada, nada, nada, nada, nada, nada, nada),
                ) { PandaZooTema { PantallaGuia(alVolver = nada) } }
            }
            try {
                onNodeWithText("Cómo se llevan los animales entre sí y con cada hábitat.").performClick()
                onNodeWithText("Desierto Aram").assertExists()
                onNodeWithText("😊 bien").assertExists()
                onNodeWithText("Animales").performClick()
                onNodeWithText("Elige un animal").assertExists()
            } finally {
                bd.cerrar()
            }
        }
}
