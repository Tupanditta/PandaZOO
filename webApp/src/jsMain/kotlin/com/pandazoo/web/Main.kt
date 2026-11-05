package com.pandazoo.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.pandazoo.data.db.BaseDatosWeb
import com.pandazoo.ui.PandaZooRaiz
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Contenedor web: solo decide dónde vive la base de datos; toda la interfaz es código compartido. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Pide que el navegador no desaloje la partida si falta espacio. Instalar la PWA en la pantalla de inicio refuerza la garantía.
    CoroutineScope(Dispatchers.Default).launch { BaseDatosWeb.pedirAlmacenamientoPersistente() }
    ComposeViewport(document.body!!) {
        PandaZooRaiz(abrirBaseDatos = { BaseDatosWeb.abrir() }, registrarVisibilidad = ::alCambiarVisibilidad)
    }
}

/**
 * Primer y segundo plano en el navegador. Al ocultar o cerrar la pestaña el temporizador del ciclo guarda su
 * remanente (RF-24) de forma síncrona, antes de que el sistema suspenda la página; `BaseDatosWeb` copia esa
 * escritura a IndexedDB en el mismo instante porque la página ya está oculta.
 */
private fun alCambiarVisibilidad(receptor: (Boolean) -> Unit): () -> Unit {
    val oculta = { _: dynamic -> receptor(false) }
    val visible = { _: dynamic -> receptor(true) }
    val cambio = { _: dynamic -> receptor(document.asDynamic().visibilityState != "hidden") }
    val ventana = window.asDynamic()
    ventana.addEventListener("pagehide", oculta)
    ventana.addEventListener("pageshow", visible)
    ventana.addEventListener("visibilitychange", cambio)
    return {
        ventana.removeEventListener("pagehide", oculta)
        ventana.removeEventListener("pageshow", visible)
        ventana.removeEventListener("visibilitychange", cambio)
    }
}
