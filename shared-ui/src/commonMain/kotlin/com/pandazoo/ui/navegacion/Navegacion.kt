package com.pandazoo.ui.navegacion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.pandazoo.ui.pantalla.PantallaComprarEspacio
import com.pandazoo.ui.pantalla.PantallaConfigurarEspacio
import com.pandazoo.ui.pantalla.PantallaEspacios
import com.pandazoo.ui.pantalla.PantallaHabitats
import com.pandazoo.ui.pantalla.Atajos
import com.pandazoo.ui.pantalla.LocalAtajos
import com.pandazoo.ui.pantalla.LocalSeccion
import com.pandazoo.ui.pantalla.Seccion3
import com.pandazoo.ui.pantalla.PantallaBeneficios
import com.pandazoo.ui.pantalla.PantallaMisiones
import com.pandazoo.ui.pantalla.PantallaNotificaciones
import com.pandazoo.ui.pantalla.PantallaSeguimientoCritico
import com.pandazoo.ui.pantalla.PantallaGuia
import com.pandazoo.ui.pantalla.PantallaMonitor
import com.pandazoo.ui.pantalla.PantallaPrestigio
import com.pandazoo.ui.pantalla.PantallaTienda
import com.pandazoo.ui.pantalla.PantallaAnimales
import com.pandazoo.ui.pantalla.PantallaComprarAnimal
import com.pandazoo.ui.pantalla.PantallaEditarFicha
import com.pandazoo.ui.pantalla.PantallaFichaAnimal
import com.pandazoo.ui.pantalla.PantallaReubicar
import com.pandazoo.ui.pantalla.PantallaRegistrarCria
import com.pandazoo.ui.pantalla.PantallaTiendaAnimales
import com.pandazoo.ui.pantalla.Ref

private const val BENEFICIOS = "beneficios"
private const val MISIONES = "misiones"
private const val NOTIFICACIONES = "notificaciones"
private const val CRITICO = "critico"
private const val TIENDA = "tienda"
private const val HABITATS = "habitats"
private const val MONITOR = "monitor"
private const val PRESTIGIO = "prestigio"
private const val GUIA = "guia"
private const val ESPACIOS = "espacios/{ref}"
private const val COMPRAR = "comprar/{ref}"
private const val CONFIGURAR = "configurar/{ref}"
private const val TIENDA_ANIMALES = "tienda-animales"
private const val COMPRAR_ANIMAL = "comprar-animal/{ref}"

// Compra iniciada desde un espacio: el espacio viaja por la tienda y la ficha de compra, donde queda fijo.
private const val TIENDA_ANIMALES_ESPACIO = "tienda-animales/espacio/{espacio}"
private const val COMPRAR_ANIMAL_ESPACIO = "comprar-animal/{ref}/espacio/{espacio}"
private const val ANIMALES = "animales/{ref}"
private const val FICHA = "ficha/{ref}"
private const val EDITAR = "editar/{ref}"
private const val REUBICAR = "reubicar/{ref}"
private const val CRIA = "cria/{ref}"

/** Codifica una referencia opaca para usarla como segmento de ruta (los identificadores técnicos pueden llevar `/`). */
internal fun codificar(valor: String): String = valor.replace("%", "%25").replace("/", "%2F")

internal fun decodificar(valor: String): String = valor.replace("%2F", "/").replace("%25", "%")

/** Sección de la barra inferior resaltada en cada pantalla; la tienda, el monitor, el prestigio y los beneficios no cuelgan de ninguna. */
private fun seccionDe(ruta: String?): Seccion3? =
    when {
        ruta == null -> null
        ruta == MISIONES -> Seccion3.MISIONES
        ruta == NOTIFICACIONES || ruta == CRITICO || ruta == CRIA -> Seccion3.NOTIFICACIONES
        ruta in setOf(BENEFICIOS, MONITOR, PRESTIGIO, GUIA, TIENDA, TIENDA_ANIMALES, COMPRAR_ANIMAL, TIENDA_ANIMALES_ESPACIO, COMPRAR_ANIMAL_ESPACIO) -> null
        else -> Seccion3.HABITATS
    }

/** Las rutas llevan referencias opacas codificadas; nunca se muestran al usuario (RES-06). */
@Composable
fun PandaZooNavegacion() {
    val nav = rememberNavController()
    val arg = listOf(navArgument("ref") { type = NavType.StringType })
    val argEspacio = listOf(navArgument("espacio") { type = NavType.StringType })
    val raiz = { destino: String ->
        nav.navigate(destino) {
            popUpTo(HABITATS)
            launchSingleTop = true
        }
    }
    val atajos = Atajos(
        alTienda = { nav.navigate(TIENDA) { launchSingleTop = true } },
        alMonitor = { nav.navigate(MONITOR) { launchSingleTop = true } },
        alPrestigio = { nav.navigate(PRESTIGIO) { launchSingleTop = true } },
        alBeneficios = { nav.navigate(BENEFICIOS) { launchSingleTop = true } },
        alGuia = { nav.navigate(GUIA) { launchSingleTop = true } },
        alHabitats = { raiz(HABITATS) },
        alMisiones = { raiz(MISIONES) },
        alNotificaciones = { raiz(NOTIFICACIONES) },
    )
    val ruta = nav.currentBackStackEntryAsState().value?.destination?.route
    CompositionLocalProvider(LocalAtajos provides atajos, LocalSeccion provides seccionDe(ruta)) {
    NavHost(nav, startDestination = HABITATS) {
        composable(MONITOR) { PantallaMonitor(alVolver = { nav.popBackStack() }) }
        composable(PRESTIGIO) { PantallaPrestigio(alVolver = { nav.popBackStack() }) }
        composable(GUIA) { PantallaGuia(alVolver = { nav.popBackStack() }) }
        composable(TIENDA) {
            PantallaTienda(alVolver = { nav.popBackStack() }, alAnimales = { nav.navigate(TIENDA_ANIMALES) { launchSingleTop = true } })
        }
        composable(TIENDA_ANIMALES) {
            PantallaTiendaAnimales(
                alVolver = { nav.popBackStack() },
                alElegir = { nav.navigate("comprar-animal/${codificar(it.valor)}") },
            )
        }
        composable(TIENDA_ANIMALES_ESPACIO, argEspacio) { entrada ->
            val espacio = decodificar(entrada.arguments!!.read { getString("espacio") })
            PantallaTiendaAnimales(
                alVolver = { nav.popBackStack() },
                alElegir = { nav.navigate("comprar-animal/${codificar(it.valor)}/espacio/${codificar(espacio)}") },
            )
        }
        composable(COMPRAR_ANIMAL, arg) { entrada ->
            val ref = Ref(decodificar(entrada.arguments!!.read { getString("ref") }))
            PantallaComprarAnimal(ref, alVolver = { nav.popBackStack() })
        }
        composable(COMPRAR_ANIMAL_ESPACIO, arg + argEspacio) { entrada ->
            val ref = Ref(decodificar(entrada.arguments!!.read { getString("ref") }))
            val espacio = Ref(decodificar(entrada.arguments!!.read { getString("espacio") }))
            // Comprado desde un espacio, se vuelve a él sin pasar por la tienda.
            PantallaComprarAnimal(ref, espacioFijo = espacio, alVolver = { nav.popBackStack(ANIMALES, inclusive = false) })
        }
        composable(ANIMALES, arg) { entrada ->
            val ref = Ref(decodificar(entrada.arguments!!.read { getString("ref") }))
            PantallaAnimales(
                espacio = ref,
                alVolver = { nav.popBackStack() },
                alAnimal = { nav.navigate("ficha/${codificar(it.valor)}") },
                alConfigurar = { nav.navigate("configurar/${codificar(ref.valor)}") },
                alComprar = { nav.navigate("tienda-animales/espacio/${codificar(ref.valor)}") },
            )
        }
        composable(FICHA, arg) { entrada ->
            val ref = Ref(decodificar(entrada.arguments!!.read { getString("ref") }))
            PantallaFichaAnimal(
                animal = ref,
                alVolver = { nav.popBackStack() },
                alEditar = { nav.navigate("editar/${codificar(ref.valor)}") },
                alReubicar = { nav.navigate("reubicar/${codificar(ref.valor)}") },
            )
        }
        composable(EDITAR, arg) { entrada ->
            val ref = Ref(decodificar(entrada.arguments!!.read { getString("ref") }))
            PantallaEditarFicha(ref, alVolver = { nav.popBackStack() })
        }
        composable(REUBICAR, arg) { entrada ->
            val ref = Ref(decodificar(entrada.arguments!!.read { getString("ref") }))
            PantallaReubicar(ref, alVolver = { nav.popBackStack() })
        }
        composable(CRIA, arg) { entrada ->
            val ref = Ref(decodificar(entrada.arguments!!.read { getString("ref") }))
            PantallaRegistrarCria(ref, alVolver = { nav.popBackStack() })
        }
        composable(BENEFICIOS) { PantallaBeneficios(alVolver = { nav.popBackStack() }) }
        composable(MISIONES) { PantallaMisiones() }
        composable(NOTIFICACIONES) { PantallaNotificaciones(
                alSeguimiento = { nav.navigate(CRITICO) { launchSingleTop = true } },
                alRegistrarCria = { nav.navigate("cria/${codificar(it.valor)}") },
            ) }
        composable(CRITICO) {
            PantallaSeguimientoCritico(
                alVolver = { nav.popBackStack() },
                alFicha = { nav.navigate("ficha/${codificar(it.valor)}") },
            )
        }
        composable(HABITATS) {
            PantallaHabitats(alHabitat = { nav.navigate("espacios/${codificar(it.valor)}") })
        }
        composable(ESPACIOS, arg) { entrada ->
            val ref = Ref(decodificar(entrada.arguments!!.read { getString("ref") }))
            PantallaEspacios(
                habitat = ref,
                alVolver = { nav.popBackStack() },
                alComprar = { nav.navigate("comprar/${codificar(ref.valor)}") },
                alAnimales = { nav.navigate("animales/${codificar(it.valor)}") },
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
}
