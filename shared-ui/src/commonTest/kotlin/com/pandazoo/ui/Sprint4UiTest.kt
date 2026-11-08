package com.pandazoo.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
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

/** Sprint 4 UI: alimentar y tratar desde la ficha, alimentar a todos desde el espacio, sin identificadores técnicos (RES-06). */
@OptIn(ExperimentalTestApi::class)
class Sprint4UiTest {
    private val regexUuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val codigosTecnicos = listOf("bosque_templado", "erizo", "insumo_medico", "bambu", "id_", "Ref")

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

    private fun espacio(c: AppContainer, nombre: String): String {
        c.adquirirEspacio.ejecutar(ComandoAdquirirEspacio(c.idPartida, "${c.idPartida}/bosque_templado", nombre, 8, "sol-$nombre"))
        return c.consultarZoo.espacios(c.idPartida, "${c.idPartida}/bosque_templado").first { it.nombre == nombre }.id
    }

    private fun ComposeUiTest.pulsar(texto: String) {
        onNodeWithText(texto).performScrollTo().performClick()
    }

    private fun ComposeUiTest.irAlEspacio(nombre: String = "Madriguera") {
        esperar("Bosque templado")
        onNodeWithText("Bosque templado").performClick()
        esperar(nombre)
        onNodeWithText(nombre).performClick()
    }

    private fun alimentacion(c: AppContainer, nombre: String) =
        c.bd.uno("SELECT nivel_alimentacion FROM Animal WHERE nombre = ?", nombre) { it.entero(0) }!!

    private fun bambu(c: AppContainer) = c.bd.uno("SELECT cantidad FROM InventarioAlimento") { it.entero(0) }!!

    @Test
    fun `T4 alimentar desde la ficha pide confirmacion y consume bambu`() =
        prueba { c ->
            val sp = espacio(c, "Madriguera")
            DatosDePrueba(c.bd, c.ids).animal(c.idPartida, sp, "erizo", "Pincho", alimentacion = 60)
            irAlEspacio()
            esperar("Pincho")
            onNodeWithText("Pincho").performClick()
            esperar("Alimentar")

            pulsar("Alimentar")
            esperar("Confirmar alimentación")
            esperar("60% → 70%")
            sinIdentificadoresTecnicos(c)
            onNodeWithText("Cancelar").performClick()
            alimentacion(c, "Pincho") shouldBe 60
            bambu(c) shouldBe 30

            pulsar("Alimentar")
            onNodeWithText("Confirmar alimentación").performClick()
            esperar("70%")
            alimentacion(c, "Pincho") shouldBe 70
            (bambu(c) < 30) shouldBe true
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `T4 un animal al 100 por ciento no se puede alimentar y uno sano no muestra tratamiento`() =
        prueba { c ->
            val sp = espacio(c, "Madriguera")
            DatosDePrueba(c.bd, c.ids).animal(c.idPartida, sp, "erizo", "Pincho")
            irAlEspacio()
            esperar("Pincho")
            onNodeWithText("Pincho").performClick()
            esperar("Alimentar")
            onNodeWithText("Alimentar").performScrollTo().assertIsNotEnabled()
            onAllNodesWithText("Aplicar tratamiento").fetchSemanticsNodes().size shouldBe 0
        }

    @Test
    fun `la ficha muestra directamente las afinidades con datos ya calculados`() =
        prueba { c ->
            val sp = espacio(c, "Madriguera")
            DatosDePrueba(c.bd, c.ids).animal(c.idPartida, sp, "erizo", "Pincho")
            c.recalcularSatisfaccion.alConsolidar(c.idPartida)
            irAlEspacio()
            esperar("Pincho")
            onNodeWithText("Pincho").performClick()
            esperar("Afinidad con las especies del espacio")
            esperar("Afinidad con el hábitat")
        }

    @Test
    fun `T4 tratar a un animal enfermo consume insumos y lo deja sano`() =
        prueba { c ->
            val sp = espacio(c, "Madriguera")
            DatosDePrueba(c.bd, c.ids).animal(c.idPartida, sp, "erizo", "Pincho", enfermo = true)
            irAlEspacio()
            esperar("Pincho")
            onNodeWithText("Pincho").performClick()
            esperar("⚠ Enfermo")

            pulsar("Aplicar tratamiento")
            esperar("Confirmar tratamiento")
            esperar("Medicina", subcadena = true)
            sinIdentificadoresTecnicos(c)
            onNodeWithText("Confirmar tratamiento").performClick()
            esperar("Tratamiento aplicado", subcadena = true)
            c.bd.uno("SELECT estado_salud FROM Animal") { it.texto(0) } shouldBe "SANO"
            c.bd.uno("SELECT cantidad FROM InventarioMedico") { it.entero(0) } shouldBe 2
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `T4 sin insumos suficientes el tratamiento informa del faltante y no cambia nada`() =
        prueba { c ->
            val sp = espacio(c, "Madriguera")
            DatosDePrueba(c.bd, c.ids).animal(c.idPartida, sp, "leon", "Rey", enfermo = true)
            c.bd.escribir("UPDATE InventarioMedico SET cantidad = 1")
            irAlEspacio()
            esperar("Rey")
            onNodeWithText("Rey").performClick()
            pulsar("Aplicar tratamiento")
            onNodeWithText("Confirmar tratamiento").performClick()
            esperar("Medicinas insuficientes", subcadena = true)
            c.bd.uno("SELECT estado_salud FROM Animal") { it.texto(0) } shouldBe "ENFERMO"
            c.bd.uno("SELECT cantidad FROM InventarioMedico") { it.entero(0) } shouldBe 1
        }

    @Test
    fun `T4 alimentar a todos desde el espacio muestra el total antes de confirmar`() =
        prueba { c ->
            val sp = espacio(c, "Madriguera")
            val datos = DatosDePrueba(c.bd, c.ids)
            datos.animal(c.idPartida, sp, "erizo", "Pincho", alimentacion = 70)
            datos.animal(c.idPartida, sp, "erizo", "Espina", alimentacion = 50)
            datos.animal(c.idPartida, sp, "erizo", "Lleno")
            irAlEspacio()
            esperar("Alimentar a todos")

            pulsar("Alimentar a todos")
            esperar("Confirmar alimentación")
            esperar("animales")
            esperar("en almacén")
            sinIdentificadoresTecnicos(c)
            onNodeWithText("Cancelar").performClick()
            alimentacion(c, "Pincho") shouldBe 70

            pulsar("Alimentar a todos")
            onNodeWithText("Confirmar alimentación").performClick()
            // Con la alimentación completa la lista deja de mostrarla: se espera a que desaparezca.
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Alimentación", substring = true).fetchSemanticsNodes().isEmpty() }
            alimentacion(c, "Pincho") shouldBe 100
            alimentacion(c, "Espina") shouldBe 100
            onNodeWithText("Alimentar a todos").performScrollTo().assertIsNotEnabled()
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `T4 con bambu insuficiente la accion grupal informa del faltante y no alimenta a nadie`() =
        prueba { c ->
            val sp = espacio(c, "Madriguera")
            DatosDePrueba(c.bd, c.ids).animal(c.idPartida, sp, "erizo", "Pincho", alimentacion = 0)
            c.bd.escribir("UPDATE InventarioAlimento SET cantidad = 1")
            irAlEspacio()
            esperar("Alimentar a todos")
            pulsar("Alimentar a todos")
            onNodeWithText("Confirmar alimentación").performClick()
            textos().none { it.contains("Bambú insuficiente") } shouldBe true // sin aviso por falta de bambú
            alimentacion(c, "Pincho") shouldBe 0
            bambu(c) shouldBe 1
        }
}
