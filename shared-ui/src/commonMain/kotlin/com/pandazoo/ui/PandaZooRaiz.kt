package com.pandazoo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.ui.generated.resources.Res
import com.pandazoo.ui.navegacion.PandaZooNavegacion
import com.pandazoo.ui.pantalla.LocalContenedor
import com.pandazoo.ui.pantalla.PandaZooTema
import com.pandazoo.ui.pantalla.Textos
import com.pandazoo.ui.pantalla.VentanaPremio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Punto de entrada común de la interfaz. Cada contenedor de plataforma solo aporta la apertura de la base de
 * datos (su ubicación es específica de la plataforma y en la web se abre de forma asíncrona); todo lo demás es código compartido.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun PandaZooRaiz(
    abrirBaseDatos: suspend () -> BaseDatos,
    /**
     * Señal síncrona de primer y segundo plano que aporta el contenedor cuando el ciclo de vida de Compose no basta
     * (la web: al cerrar o suspender la pestaña no hay tiempo de esperar a una corrutina). Recibe el receptor del
     * cambio (`true` = primer plano) y devuelve la función que lo da de baja.
     */
    registrarVisibilidad: ((Boolean) -> Unit) -> () -> Unit = { {} },
) {
    var contenedor by remember { mutableStateOf<AppContainer?>(null) }
    LaunchedEffect(Unit) {
        // La configuración se lee del recurso compartido: crea la partida nueva y alimenta la regla de satisfacción.
        val texto = Res.readBytes("files/valores_y_pesos.json").decodeToString()
        contenedor = withContext(Dispatchers.Default) { AppContainer.crear(abrirBaseDatos()) { texto } }
    }
    PandaZooTema {
        val c = contenedor
        if (c == null) {
            Text("Cargando…", color = MaterialTheme.colorScheme.onBackground)
        } else {
            // Un único temporizador ligado al fin de ciclo: corre en primer plano y guarda el remanente al salir (RF-24).
            LifecycleResumeEffect(c) {
                c.alPrimerPlano()
                onPauseOrDispose { c.alSegundoPlano() }
            }
            DisposableEffect(c) {
                val baja = registrarVisibilidad { visible -> if (visible) c.alPrimerPlano() else c.alSegundoPlano() }
                onDispose(baja)
            }
            CompositionLocalProvider(LocalContenedor provides c) {
                PandaZooNavegacion()
                VentanasBienvenida(c)
            }
        }
    }
}

/** Ventanas del regalo de bienvenida: una por recurso, de una en una; al cerrar una aparece la siguiente. */
@Composable
internal fun VentanasBienvenida(c: AppContainer) {
    var pendientes by remember(c) { mutableStateOf(c.regaloBienvenida?.let(Textos::bienvenida).orEmpty()) }
    pendientes.firstOrNull()?.let { VentanaPremio(it) { pendientes = pendientes.drop(1) } }
    // Los regalos semanales esperan a que se cierre el de bienvenida y se muestran de uno en uno.
    val semanales by c.regalosSemanales.collectAsState()
    if (pendientes.isEmpty()) semanales.firstOrNull()?.let { VentanaPremio(Textos.regaloSemanal(it)) { c.descartarRegaloSemanal() } }
}
