package com.pandazoo.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.runComposeUiTest
import com.pandazoo.application.caso.ComandoAdquirirEspacio
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.data.depuracion.DatosDePrueba
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.modelo.Seccion
import com.pandazoo.testing.ConfiguracionDePrueba
import com.pandazoo.ui.navegacion.PandaZooNavegacion
import com.pandazoo.ui.pantalla.LocalContenedor
import com.pandazoo.ui.pantalla.PandaZooTema
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Sprint 5 UI: reclamar beneficios, historial de avisos y seguimiento crítico, sin identificadores técnicos (RES-06). */
@OptIn(ExperimentalTestApi::class)
class Sprint5UiTest {
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

    private fun fondos(c: AppContainer): Dinero = (c.consultarInventario.ejecutar(c.idPartida).fondos as Seccion.Disponible).valor

    private fun ciclo(c: AppContainer) {
        c.ejecutarCiclo.siguiente(c.idPartida)
    }

    @Test
    fun `T5 reclamar beneficios pide confirmacion acredita el importe y deja el pendiente a cero`() =
        prueba { c ->
            val sp = espacio(c, "Madriguera")
            val a = DatosDePrueba(c.bd, c.ids).animal(c.idPartida, sp, "erizo", "Pincho")
            c.bd.escribir("UPDATE Animal SET satisfaccion_actual = 1.0 WHERE id_animal = ?", a)
            ciclo(c)
            val antes = fondos(c)

            onNodeWithContentDescription("Ver beneficios").performClick()
            esperar("Reclamar beneficios")
            esperar("🪙 2.40", subcadena = true)
            esperar("▸ Bosque templado")
            pulsar("Reclamar beneficios")
            esperar("Confirmar reclamación")
            esperar("+🪙 2.40")
            sinIdentificadoresTecnicos(c)
            onNodeWithText("Cancelar").performClick()
            fondos(c) shouldBe antes

            pulsar("Reclamar beneficios")
            onNodeWithText("Confirmar reclamación").performClick()
            esperar("🪙 0.00", subcadena = true) // pendiente a cero
            fondos(c) shouldBe antes + Dinero.desdeDecimal(2.4)
            textos().none { it.contains("Has reclamado") } shouldBe true // sin aviso al reclamar
            onNodeWithText("Reclamar beneficios").performScrollTo().assertIsNotEnabled()
            sinIdentificadoresTecnicos(c)
        }

    @Test
    fun `T5 el historial muestra la alerta critica con severidad y texto y abrirla la marca como vista`() =
        prueba { c ->
            val sp = espacio(c, "Madriguera")
            DatosDePrueba(c.bd, c.ids).animal(c.idPartida, sp, "erizo", "Pincho", alimentacion = 0)
            ciclo(c)

            esperar("Avisos")
            onNodeWithText("Avisos").performClick()
            esperar("Pincho", subcadena = true)
            sinIdentificadoresTecnicos(c)
            c.bd.uno("SELECT estado FROM Notificacion") { it.texto(0) } shouldBe "PENDIENTE" // listar no cambia nada

            onNodeWithText("Media").performClick()
            esperar("No hay avisos por ahora.")
            onNodeWithText("Todos").performClick()
            esperar("Pincho", subcadena = true)

            onNodeWithText("Pincho", substring = true).performClick()
            esperar("Desnutrido", subcadena = true) // abrir un aviso crítico lleva directo al seguimiento
            c.bd.uno("SELECT estado FROM Notificacion") { it.texto(0) } shouldBe "VISTA"
            sinIdentificadoresTecnicos(c)
            esperar("Quedan 2 ciclos", subcadena = true)
        }

    @Test
    fun `T5 el seguimiento critico muestra contadores separados y lleva a la ficha`() =
        prueba { c ->
            val sp = espacio(c, "Madriguera")
            val a = DatosDePrueba(c.bd, c.ids).animal(c.idPartida, sp, "erizo", "Pincho", alimentacion = 0, enfermo = true)
            c.bd.escribir("UPDATE Animal SET ciclos_desnutricion = 1, ciclos_enfermedad = 2 WHERE id_animal = ?", a)
            esperar("Avisos")
            onNodeWithText("Avisos").performClick()
            esperar("Seguimiento crítico")
            onNodeWithText("Seguimiento crítico").performClick()
            esperar("Desnutrido y Enfermo", subcadena = true)
            esperar("Desnutrición: 1 ciclo · Enfermedad: 2 ciclos · Quedan 1 ciclo", subcadena = true)
            sinIdentificadoresTecnicos(c)

            onNodeWithText("Pincho", substring = true).performClick()
            esperar("Aplicar tratamiento")
        }

    @Test
    fun `T5 sin animales criticos el panel lo indica`() =
        prueba { c ->
            espacio(c, "Madriguera")
            esperar("Avisos")
            onNodeWithText("Avisos").performClick()
            esperar("No hay avisos por ahora.")
            onNodeWithText("Seguimiento crítico").performClick()
            esperar("No hay animales que requieran seguimiento.")
        }

    @Test
    fun `la barra inferior esta en cualquier pantalla y lleva a sus secciones`() =
        prueba { c ->
            espacio(c, "Madriguera")
            onNodeWithContentDescription("Abrir tienda").performClick()
            esperar("Saldo disponible")
            onNodeWithText("Misiones").performClick()
            esperar("Compra 2 ejemplares de", subcadena = true)
            onNodeWithContentDescription("Abrir monitor de satisfacción").performClick()
            esperar("Parque")
            onNodeWithText("Hábitats").performClick()
            esperar("Bosque templado")
        }

    @Test
    fun `deslizar un aviso bajo o medio lo descarta y los textos no llevan la severidad`() =
        prueba { c ->
            c.bd.escribir(
                "INSERT INTO Notificacion (id_partida, tipo, severidad, codigo, entidad, id_entidad, numero_ciclo, estado, fecha) " +
                    "VALUES (?, 'INFORMATIVA', 'BAJA', 'PRESTIGIO_ASCENSO', 'PARTIDA', '1', 0, 'PENDIENTE', 0)",
                c.idPartida,
            )
            esperar("Avisos")
            onNodeWithText("Avisos").performClick()
            esperar("Has alcanzado el prestigio 1.", subcadena = true)
            textos().forEach { t -> (t.contains("Informativa") || t.contains("Pendiente")) shouldBe false }
            onNodeWithText("Has alcanzado el prestigio 1.", substring = true).performTouchInput { swipeRight() }
            esperar("No hay avisos por ahora.")
            c.bd.uno("SELECT ciclo_cierre IS NOT NULL FROM Notificacion") { it.entero(0) } shouldBe 1
        }
}
