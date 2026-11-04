package com.pandazoo.ui.navegacion

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.pandazoo.ui.pantalla.PantallaComprarEspacio
import com.pandazoo.ui.pantalla.PantallaConfigurarEspacio
import com.pandazoo.ui.pantalla.PantallaEspacios
import com.pandazoo.ui.pantalla.PantallaHabitats
import com.pandazoo.ui.pantalla.PantallaInicio
import com.pandazoo.ui.pantalla.PantallaTienda
import com.pandazoo.ui.pantalla.Ref

private const val INICIO = "inicio"
private const val TIENDA = "tienda"
private const val HABITATS = "habitats"
private const val ESPACIOS = "espacios/{ref}"
private const val COMPRAR = "comprar/{ref}"
private const val CONFIGURAR = "configurar/{ref}"

/** Codifica una referencia opaca para usarla como segmento de ruta (los identificadores técnicos pueden llevar `/`). */
internal fun codificar(valor: String): String = valor.replace("%", "%25").replace("/", "%2F")

internal fun decodificar(valor: String): String = valor.replace("%2F", "/").replace("%25", "%")

/** Las rutas llevan referencias opacas codificadas; nunca se muestran al usuario (RES-06). */
@Composable
fun PandaZooNavegacion() {
    val nav = rememberNavController()
    val arg = listOf(navArgument("ref") { type = NavType.StringType })
    NavHost(nav, startDestination = INICIO) {
        composable(INICIO) {
            PantallaInicio(alTienda = { nav.navigate(TIENDA) }, alZoo = { nav.navigate(HABITATS) })
        }
        composable(TIENDA) { PantallaTienda(alVolver = { nav.popBackStack() }) }
        composable(HABITATS) {
            PantallaHabitats(
                alVolver = { nav.popBackStack() },
                alHabitat = { nav.navigate("espacios/${codificar(it.valor)}") },
            )
        }
        composable(ESPACIOS, arg) { entrada ->
            val ref = Ref(decodificar(entrada.arguments!!.read { getString("ref") }))
            PantallaEspacios(
                habitat = ref,
                alVolver = { nav.popBackStack() },
                alComprar = { nav.navigate("comprar/${codificar(ref.valor)}") },
                alConfigurar = { nav.navigate("configurar/${codificar(it.valor)}") },
            )
        }
        composable(COMPRAR, arg) { entrada ->
            val ref = Ref(decodificar(entrada.arguments!!.read { getString("ref") }))
            PantallaComprarEspacio(ref, alVolver = { nav.popBackStack() })
        }
        composable(CONFIGURAR, arg) { entrada ->
            val ref = Ref(decodificar(entrada.arguments!!.read { getString("ref") }))
            PantallaConfigurarEspacio(ref, alVolver = { nav.popBackStack() })
        }
    }
}
