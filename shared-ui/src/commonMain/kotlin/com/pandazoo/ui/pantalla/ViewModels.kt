package com.pandazoo.ui.pantalla

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pandazoo.ui.AppContainer
import com.pandazoo.application.caso.ComandoAdquirirEspacio
import com.pandazoo.application.caso.ComandoAdquirirInsumos
import com.pandazoo.application.caso.ComandoConfigurarEspacio
import com.pandazoo.application.error.Resultado
import com.pandazoo.domain.modelo.OpcionCapacidad
import com.pandazoo.domain.modelo.Recurso
import com.pandazoo.domain.modelo.TipoRecurso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
private fun nuevoId(): String = Uuid.random().toString()

/** Resultado de una acción del usuario, ya traducido a texto legible. */
data class Mensaje(val texto: String, val exito: Boolean)

/**
 * Los ViewModels solo leen datos persistidos y reaccionan a las transacciones confirmadas (RES-10): ninguno
 * calcula nada al abrir una pantalla ni consulta por intervalos.
 */
abstract class BaseViewModel(protected val c: AppContainer) : ViewModel() {
    val mensaje = MutableStateFlow<Mensaje?>(null)

    /** Reejecuta `consulta` al abrirse y tras cada transacción confirmada. */
    protected fun <T> observar(inicial: T, consulta: () -> T): StateFlow<T> =
        c.bd.cambios.onStart { emit(Unit) }
            .map { consulta() }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), inicial)

    /** La `id_solicitud` se genera al iniciar la acción y se reutiliza si hubo un fallo técnico (RNF-12). */
    protected var solicitud: String = nuevoId()

    protected fun nuevaSolicitud() {
        solicitud = nuevoId()
    }

    protected fun <R> ejecutar(accion: () -> Resultado<R>, alExito: (R) -> String) {
        viewModelScope.launch {
            val r = try {
                withContext(Dispatchers.Default) { accion() }
            } catch (e: Exception) {
                mensaje.value = Mensaje(Textos.ERROR_TECNICO, false)
                return@launch
            }
            when (r) {
                is Resultado.Exito -> {
                    mensaje.value = Mensaje(alExito(r.valor), true)
                }
                is Resultado.Rechazado -> mensaje.value = Mensaje(Textos.motivo(r.motivo), false)
            }
            nuevaSolicitud()
        }
    }

    fun descartarMensaje() {
        mensaje.value = null
    }
}

class InicioViewModel(c: AppContainer) : BaseViewModel(c) {
    val estado: StateFlow<InicioUi?> = observar<InicioUi?>(null) { Textos.inicio(c.consultarInventario.ejecutar(c.idPartida)) }
}

class TiendaViewModel(c: AppContainer) : BaseViewModel(c) {
    val ofertas: StateFlow<List<OfertaUi>> = observar(emptyList()) { c.consultarTienda.recursos().map(Textos::oferta) }
    val saldo: StateFlow<String> = observar("") {
        Textos.inicio(c.consultarInventario.ejecutar(c.idPartida)).saldo ?: "No disponible"
    }

    fun comprar(oferta: OfertaUi, cantidad: Int) {
        val tipo = if (oferta.unidad == "kg") TipoRecurso.ALIMENTO else TipoRecurso.INSUMO
        val comando = ComandoAdquirirInsumos(c.idPartida, Recurso(tipo, oferta.ref.valor), cantidad, solicitud)
        ejecutar({ c.adquirirInsumos.ejecutar(comando) }) {
            "Compra realizada: ${Textos.dinero(it.coste)}. Saldo: ${Textos.dinero(it.saldo)}."
        }
    }
}

class HabitatsViewModel(c: AppContainer) : BaseViewModel(c) {
    val habitats: StateFlow<List<HabitatUi>> = observar(emptyList()) {
        c.consultarZoo.habitats(c.idPartida).map(Textos::habitat)
    }
}

class EspaciosViewModel(c: AppContainer, private val habitat: Ref) : BaseViewModel(c) {
    val nombreHabitat: StateFlow<String> = observar("") {
        c.consultarZoo.habitats(c.idPartida).firstOrNull { it.id == habitat.valor }?.nombre ?: ""
    }
    val espacios: StateFlow<List<EspacioUi>> = observar(emptyList()) {
        c.consultarZoo.espacios(c.idPartida, habitat.valor).map(Textos::espacio)
    }
}

class ComprarEspacioViewModel(c: AppContainer, private val habitat: Ref) : BaseViewModel(c) {
    val capacidades: StateFlow<List<CapacidadUi>> = observar(emptyList()) {
        c.consultarTienda.capacidades().map(::capacidadUi)
    }
    val saldo: StateFlow<String> = observar("") {
        Textos.inicio(c.consultarInventario.ejecutar(c.idPartida)).saldo ?: "No disponible"
    }
    val creado = MutableStateFlow(false)

    private fun capacidadUi(o: OpcionCapacidad) =
        CapacidadUi(o.capacidad, o.coste, "${o.capacidad} animales — ${Textos.dinero(o.coste)}")

    fun comprar(nombre: String, capacidad: Int) {
        val comando = ComandoAdquirirEspacio(c.idPartida, habitat.valor, nombre, capacidad, solicitud)
        ejecutar({ c.adquirirEspacio.ejecutar(comando) }) {
            creado.value = true
            "Espacio creado por ${Textos.dinero(it.coste)}. Saldo: ${Textos.dinero(it.saldo)}."
        }
    }
}

class ConfigurarEspacioViewModel(c: AppContainer, private val espacio: Ref) : BaseViewModel(c) {
    val actual: StateFlow<EspacioUi?> = observar<EspacioUi?>(null) {
        c.consultarZoo.espacio(c.idPartida, espacio.valor)?.let(Textos::espacio)
    }
    val habitatActual: StateFlow<Ref?> = observar<Ref?>(null) {
        c.consultarZoo.espacio(c.idPartida, espacio.valor)?.let { Ref(it.idHabitat) }
    }
    val habitats: StateFlow<List<HabitatUi>> = observar(emptyList()) {
        c.consultarZoo.habitats(c.idPartida).map(Textos::habitat).filter { it.disponible }
    }
    val guardado = MutableStateFlow(false)

    fun guardar(nombre: String, habitat: Ref?) {
        val idSolicitud = solicitud
        ejecutar({
            val actual = c.consultarZoo.espacio(c.idPartida, espacio.valor)
            c.configurarEspacio.ejecutar(
                ComandoConfigurarEspacio(
                    idPartida = c.idPartida,
                    idEspacio = espacio.valor,
                    nuevoNombre = nombre,
                    nuevoIdHabitat = habitat?.valor?.takeIf { it != actual?.idHabitat },
                    idSolicitud = idSolicitud,
                ),
            )
        }) {
            guardado.value = true
            "Configuración guardada."
        }
    }
}

/** Crea ViewModels con el contenedor de dependencias; los argumentos de navegación son referencias opacas. */
inline fun <reified VM : ViewModel> fabrica(crossinline crear: () -> VM): ViewModelProvider.Factory =
    viewModelFactory { initializer { crear() } }
