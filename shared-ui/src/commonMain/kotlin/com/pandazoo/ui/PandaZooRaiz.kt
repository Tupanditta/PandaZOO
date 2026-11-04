package com.pandazoo.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.ui.generated.resources.Res
import com.pandazoo.ui.navegacion.PandaZooNavegacion
import com.pandazoo.ui.pantalla.LocalContenedor
import com.pandazoo.ui.pantalla.PandaZooTema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Punto de entrada común de la interfaz. Cada contenedor de plataforma solo aporta la apertura de la base de
 * datos (su ruta es específica del sistema operativo); todo lo demás es código compartido.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun PandaZooRaiz(abrirBaseDatos: () -> BaseDatos) {
    var contenedor by remember { mutableStateOf<AppContainer?>(null) }
    LaunchedEffect(Unit) {
        // La configuración se lee del recurso compartido; solo se usa si hay que crear la partida.
        val texto = Res.readBytes("files/valores_y_pesos.json").decodeToString()
        contenedor = withContext(Dispatchers.Default) { AppContainer.crear(abrirBaseDatos()) { texto } }
    }
    PandaZooTema {
        val c = contenedor
        if (c == null) {
            Text("Cargando…")
        } else {
            CompositionLocalProvider(LocalContenedor provides c) { PandaZooNavegacion() }
        }
    }
}
