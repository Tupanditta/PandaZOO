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
import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.domain.modelo.OpcionCapacidad
import com.pandazoo.domain.modelo.Recurso
import com.pandazoo.domain.modelo.TipoRecurso
import com.pandazoo.application.caso.ComandoAbrirAviso
import com.pandazoo.application.caso.ComandoAceptarReproduccion
import com.pandazoo.application.caso.DetalleReproduccion
import com.pandazoo.application.caso.ComandoReclamarBeneficios
import com.pandazoo.application.caso.ComandoReclamarRecompensa
import com.pandazoo.application.caso.ComandoAdquirirAnimal
import com.pandazoo.application.puerto.SeveridadAviso
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.application.caso.ComandoAlimentarAnimal
import com.pandazoo.application.caso.ComandoAlimentarEspacio
import com.pandazoo.application.caso.ComandoTratarAnimal
import com.pandazoo.application.caso.ComandoDarDeBajaEspacio
import com.pandazoo.application.caso.ComandoEditarFicha
import com.pandazoo.application.caso.ComandoReubicarAnimal
import com.pandazoo.application.caso.ComandoVenderAnimal
import com.pandazoo.application.caso.FichaNueva
import com.pandazoo.domain.animal.OpcionFicha
import com.pandazoo.domain.animal.ReglasAnimal
import com.pandazoo.domain.animal.ReglasCuidado
import com.pandazoo.domain.animal.ReglasNivel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
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

    /** Como [observar], pero la consulta depende de una selección del usuario que puede cambiar. */
    @OptIn(ExperimentalCoroutinesApi::class)
    protected fun <K, T> observarPor(clave: Flow<K>, inicial: T, consulta: (K) -> T): StateFlow<T> =
        clave.flatMapLatest { k -> c.bd.cambios.onStart { emit(Unit) }.map { consulta(k) } }
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

/** Cabecera global: solo lee datos persistidos (RES-10). */
class CabeceraViewModel(c: AppContainer) : BaseViewModel(c) {
    val estado: StateFlow<CabeceraUi?> = observar<CabeceraUi?>(null) {
        val i = Textos.inicio(c.consultarInventario.ejecutar(c.idPartida))
        CabeceraUi(
            satisfaccion = Textos.monitor(c.consultarMonitor.ejecutar(c.idPartida)).parque,
            saldo = i.saldo?.removePrefix("${Textos.MONEDA} ") ?: "N/D",
            bambu = i.bambu ?: "N/D",
            insumos = i.insumosMedicos ?: "N/D",
            prestigio = Textos.nivelPrestigio(c.consultarPrestigio.ejecutar(c.idPartida)),
        )
    }
}

/** CU-19: solo lee el prestigio persistido; abrir o actualizar el panel nunca recalcula (RF-35, RF-44). */
class PrestigioViewModel(c: AppContainer) : BaseViewModel(c) {
    val estado: StateFlow<PrestigioUi?> = observar<PrestigioUi?>(null) { Textos.prestigio(c.consultarPrestigio.ejecutar(c.idPartida)) }
}

/** Beneficios pendientes y por ciclo (CU-16) y su reclamación (CU-08). */
class BeneficiosViewModel(c: AppContainer) : BaseViewModel(c) {
    val vista: StateFlow<BeneficiosUi?> = observar<BeneficiosUi?>(null) { Textos.beneficios(c.consultarBeneficios.ejecutar(c.idPartida)) }

    /** Importe mostrado en el diálogo de confirmación; se valida de nuevo al confirmar (CU-08, 4.a). */
    val confirmando = MutableStateFlow<Dinero?>(null)

    fun pedirReclamar() {
        val v = vista.value ?: return
        if (!v.puedeReclamar) {
            mensaje.value = Mensaje(Textos.motivo(MotivoRechazo.SinBeneficios), false)
        } else {
            descartarMensaje()
            confirmando.value = v.importe
        }
    }

    fun cancelar() {
        confirmando.value = null
    }

    fun reclamar() {
        val importe = confirmando.value ?: return
        confirmando.value = null
        ejecutar({ c.reclamarBeneficios.ejecutar(ComandoReclamarBeneficios(c.idPartida, importe, solicitud)) }) {
            "Has reclamado ${Textos.dinero(it.importe)}. Saldo: ${Textos.dinero(it.saldo)}."
        }
    }
}

/** Historial de avisos (CU-21). Listar no cambia ningún estado; abrir el detalle marca como vista un aviso pendiente. */
class AvisosViewModel(c: AppContainer) : BaseViewModel(c) {
    val filtro = MutableStateFlow<SeveridadAviso?>(null)

    val avisos: StateFlow<List<AvisoItemUi>> = observarPor(filtro, emptyList()) { f ->
        c.consultarAvisos.ejecutar(c.idPartida, f).map(Textos::aviso)
    }

    val detalle = MutableStateFlow<AvisoItemUi?>(null)

    fun filtrar(severidad: SeveridadAviso?) {
        filtro.value = severidad
    }

    fun abrir(aviso: AvisoItemUi) {
        viewModelScope.launch {
            val r = try {
                withContext(Dispatchers.Default) { c.abrirAviso.ejecutar(ComandoAbrirAviso(c.idPartida, aviso.id, solicitud)) }
            } catch (e: Exception) {
                mensaje.value = Mensaje(Textos.ERROR_TECNICO, false)
                return@launch
            }
            when (r) {
                is Resultado.Exito -> detalle.value = Textos.aviso(r.valor.aviso)
                is Resultado.Rechazado -> mensaje.value = Mensaje(Textos.motivo(r.motivo), false)
            }
            nuevaSolicitud()
        }
    }

    fun cerrarDetalle() {
        detalle.value = null
    }
}

/** Seguimiento crítico (CU-18): contadores persistidos y ciclos restantes. Solo lectura. */
class SeguimientoViewModel(c: AppContainer) : BaseViewModel(c) {
    val registros: StateFlow<List<CriticoUi>> = observar(emptyList()) {
        Textos.criticos(c.consultarSeguimiento.ejecutar(c.idPartida))
    }
}

class HabitatsViewModel(c: AppContainer) : BaseViewModel(c) {
    /** Solo modo de depuración: elimina espacios y animales de prueba, conserva los del usuario. */
    fun eliminarDatosDePrueba() {
        viewModelScope.launch {
            val texto = try {
                val r = withContext(Dispatchers.Default) { c.eliminarCargaDePrueba() }
                "Eliminados ${r.animales} animales y ${r.espacios} espacios de prueba."
            } catch (e: Exception) {
                Textos.ERROR_TECNICO
            }
            mensaje.value = Mensaje(texto, true)
        }
    }

    val habitats: StateFlow<List<HabitatUi>> = observar(emptyList()) {
        c.consultarZoo.habitats(c.idPartida).map(Textos::habitat)
    }

    /** Solo modo de depuración: ejecuta el siguiente ciclo sin esperar los 10 minutos. */
    fun adelantarCiclo() {
        viewModelScope.launch {
            val texto = try {
                if (withContext(Dispatchers.Default) { c.adelantarCiclo() }) "Ciclo ejecutado." else "No se pudo ejecutar el ciclo."
            } catch (e: Exception) {
                Textos.ERROR_TECNICO
            }
            mensaje.value = Mensaje(texto, true)
        }
    }

    /** Solo modo de depuración: añade un panda a la partida activa. */
    fun anadirPanda() {
        viewModelScope.launch {
            val texto = try {
                if (withContext(Dispatchers.Default) { c.anadirPanda() }) "Panda añadido."
                else "No hay espacio con cupo para el panda."
            } catch (e: Exception) {
                Textos.ERROR_TECNICO
            }
            mensaje.value = Mensaje(texto, true)
        }
    }

    /** Solo modo de depuración: genera la carga de referencia y deja la satisfacción calculada. */
    fun generarDatosDePrueba() {
        viewModelScope.launch {
            val texto = try {
                val generado = withContext(Dispatchers.Default) { c.generarCargaDePrueba() }
                if (generado) "Datos de prueba generados." else "La partida ya tiene animales; no se generó nada."
            } catch (e: Exception) {
                Textos.ERROR_TECNICO
            }
            mensaje.value = Mensaje(texto, true)
        }
    }
}

/** CU-17: solo lee métricas persistidas; abrir o actualizar el monitor nunca recalcula (RF-44). */
class MonitorViewModel(c: AppContainer) : BaseViewModel(c) {
    val estado: StateFlow<MonitorUi?> = observar<MonitorUi?>(null) { Textos.monitor(c.consultarMonitor.ejecutar(c.idPartida)) }
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

class EspaciosViewModel(c: AppContainer, private val habitat: Ref) : BaseViewModel(c) {
    val nombreHabitat: StateFlow<String> = observar("") {
        c.consultarZoo.habitats(c.idPartida).firstOrNull { it.id == habitat.valor }?.nombre?.let(Textos::nombreHabitat) ?: ""
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

/** CU-01, paso 2: especies de la tienda con su estado. Solo lectura. */
/** CU-20 y CU-15: misiones vigentes con su progreso persistido y reclamación con confirmación previa. */
class MisionesViewModel(c: AppContainer) : BaseViewModel(c) {
    val misiones: StateFlow<List<MisionUi>> = observar(emptyList()) {
        c.consultarMisiones.ejecutar(c.idPartida).map(Textos::mision)
    }

    /** Misión cuya recompensa se va a reclamar; hasta confirmar no se acredita nada (CU-15, 3.a). */
    val confirmando = MutableStateFlow<MisionUi?>(null)

    fun pedirReclamar(m: MisionUi) {
        descartarMensaje()
        confirmando.value = m
    }

    fun cancelar() {
        confirmando.value = null
    }

    fun reclamar() {
        val m = confirmando.value ?: return
        confirmando.value = null
        ejecutar({ c.reclamarRecompensa.ejecutar(ComandoReclamarRecompensa(c.idPartida, m.ref.valor)) }, Textos::recompensaReclamada)
    }
}

class TiendaAnimalesViewModel(c: AppContainer) : BaseViewModel(c) {
    val ofertas: StateFlow<List<OfertaAnimalUi>> = observar(emptyList()) {
        c.consultarTiendaAnimales.ejecutar(c.idPartida).map(Textos::ofertaAnimal)
    }
    val saldo: StateFlow<String> = observar("") {
        Textos.inicio(c.consultarInventario.ejecutar(c.idPartida)).saldo ?: "No disponible"
    }
}

/** Opciones de los catálogos cerrados de la ficha, con su nombre legible. */
data class OpcionUi(val ref: String, val nombre: String)

private fun opciones(l: List<OpcionFicha>) = l.map { OpcionUi(it.id, it.nombre) }

/** CU-01: hábitat, espacio y ficha, y alta atómica. */
class ComprarAnimalViewModel(c: AppContainer, private val especie: Ref) : BaseViewModel(c) {
    val oferta: StateFlow<OfertaAnimalUi?> = observar<OfertaAnimalUi?>(null) {
        c.consultarTiendaAnimales.ejecutar(c.idPartida).firstOrNull { it.especie.id == especie.valor }?.let(Textos::ofertaAnimal)
    }
    val saldo: StateFlow<String> = observar("") {
        Textos.inicio(c.consultarInventario.ejecutar(c.idPartida)).saldo ?: "No disponible"
    }
    val habitats: StateFlow<List<HabitatUi>> = observar(emptyList()) {
        c.consultarAnimales.habitatsConCupo(c.idPartida).map(Textos::habitat)
    }

    val habitatElegido = MutableStateFlow<Ref?>(null)
    val espacioElegido = MutableStateFlow<Ref?>(null)

    val espacios: StateFlow<List<EspacioUi>> = observarPor(habitatElegido, emptyList()) { h ->
        if (h == null) emptyList() else c.consultarAnimales.espaciosConCupo(c.idPartida, h.valor).map(Textos::espacio)
    }

    /** Animales del espacio elegido: candidatos a mejor amigo. */
    val companeros: StateFlow<List<CandidatoUi>> = observarPor(espacioElegido, emptyList()) { s ->
        if (s == null) emptyList() else c.consultarAnimales.animalesDe(c.idPartida, s.valor).let(Textos::candidatos)
    }

    val habilidades: List<OpcionUi> = opciones(c.catalogoFicha.habilidades)
    val rasgos: List<OpcionUi> = opciones(c.catalogoFicha.rasgos)
    val comprado = MutableStateFlow(false)

    fun elegirHabitat(h: Ref) {
        habitatElegido.value = h
        espacioElegido.value = null
        descartarMensaje()
    }

    fun elegirEspacio(s: Ref) {
        espacioElegido.value = s
        descartarMensaje()
    }

    fun comprar(nombre: String, habilidades: Set<String>, rasgos: Set<String>, amigo: Ref?) {
        val espacio = espacioElegido.value ?: return
        val comando = ComandoAdquirirAnimal(
            idPartida = c.idPartida,
            idEspecie = especie.valor,
            idEspacio = espacio.valor,
            ficha = FichaNueva(nombre, habilidades.toList(), rasgos.toList(), amigo?.valor),
            idSolicitud = solicitud,
        )
        ejecutar({ c.adquirirAnimal.ejecutar(comando) }) {
            comprado.value = true
            "Animal comprado por ${Textos.dinero(it.coste)}. Saldo: ${Textos.dinero(it.saldo)}."
        }
    }
}

/** Tercer nivel de la navegación (RF-08): animales de un espacio, con baja opcional del espacio vacío (CU-14). */
class AnimalesViewModel(c: AppContainer, private val espacio: Ref) : BaseViewModel(c) {
    val actual: StateFlow<EspacioUi?> = observar<EspacioUi?>(null) {
        c.consultarZoo.espacio(c.idPartida, espacio.valor)?.let(Textos::espacio)
    }
    val animales: StateFlow<List<AnimalItemUi>> = observar(emptyList()) {
        c.consultarAnimales.animalesDe(c.idPartida, espacio.valor).let(Textos::animalItems)
    }
    val ofreciendoBaja = MutableStateFlow(false)
    val dadoDeBaja = MutableStateFlow(false)

    /** Confirmación de la acción grupal (CU-13, paso 2): texto con el total y total mostrado para validarlo al confirmar. */
    val confirmandoGrupal = MutableStateFlow<GrupalUi?>(null)

    fun pedirAlimentarGrupo() {
        viewModelScope.launch {
            val previa = withContext(Dispatchers.Default) { c.consultarCuidado.grupal(c.idPartida, espacio.valor) }
            val kilos = previa.kilos
            when {
                previa.animales == 0 -> mensaje.value = Mensaje(Textos.motivo(MotivoRechazo.NadaQueAlimentar), false)
                kilos == null -> mensaje.value = Mensaje(Textos.motivo(MotivoRechazo.RacionNoConfigurada), false)
                else -> {
                    descartarMensaje()
                    val animales = if (previa.animales == 1) "1 animal" else "${previa.animales} animales"
                    confirmandoGrupal.value = GrupalUi(
                        kilos,
                        "Se alimentará hasta el 100 % a $animales. Total de bambú: $kilos kg (hay ${previa.bambuDisponible} kg).",
                    )
                }
            }
        }
    }

    fun cancelarGrupal() {
        confirmandoGrupal.value = null
    }

    fun confirmarGrupal() {
        val g = confirmandoGrupal.value ?: return
        confirmandoGrupal.value = null
        ejecutar({ c.alimentarEspacio.ejecutar(ComandoAlimentarEspacio(c.idPartida, espacio.valor, g.kilos, solicitud)) }) {
            val n = if (it.animales == 1) "1 animal" else "${it.animales} animales"
            "Se ha alimentado a $n con ${it.kilos} kg de bambú. Quedan ${it.bambuRestante} kg."
        }
    }

    fun pedirBaja() {
        ofreciendoBaja.value = true
    }

    fun cancelarBaja() {
        ofreciendoBaja.value = false
    }

    fun confirmarBaja() {
        ofreciendoBaja.value = false
        ejecutar({ c.darDeBajaEspacio.ejecutar(ComandoDarDeBajaEspacio(c.idPartida, espacio.valor, solicitud)) }) {
            dadoDeBaja.value = true
            "El espacio se ha dado de baja."
        }
    }
}

/** Ficha técnica de un animal (RF-25) con las acciones de vender (CU-06). */
class FichaAnimalViewModel(c: AppContainer, private val animal: Ref) : BaseViewModel(c) {
    val ficha: StateFlow<FichaAnimalUi?> = observar<FichaAnimalUi?>(null) {
        c.consultarAnimales.ficha(c.idPartida, animal.valor)?.let { Textos.ficha(it, c.catalogoFicha, c.ciclosPorNivel) }
    }
    val confirmandoVenta = MutableStateFlow(false)

    /** Texto de los diálogos de confirmación de CU-13 y CU-09; `null` si no hay diálogo abierto. */
    val confirmandoAlimentar = MutableStateFlow<String?>(null)
    val confirmandoTratar = MutableStateFlow<String?>(null)

    fun pedirAlimentar() {
        viewModelScope.launch {
            val previa = withContext(Dispatchers.Default) { c.consultarCuidado.racion(c.idPartida, animal.valor) }
            val f = ficha.value
            val kilos = previa?.kilos
            when {
                previa == null || f == null -> Unit
                f.alimentacion >= ReglasCuidado.ALIMENTACION_MAXIMA ->
                    mensaje.value = Mensaje(Textos.motivo(MotivoRechazo.AlimentacionCompleta), false)
                kilos == null -> mensaje.value = Mensaje(Textos.motivo(MotivoRechazo.RacionNoConfigurada), false)
                else -> {
                    descartarMensaje()
                    confirmandoAlimentar.value =
                        "Se consumirán $kilos kg de bambú (hay ${previa.bambuDisponible} kg) y la alimentación de ${f.nombre} " +
                            "subirá del ${f.alimentacion} % al ${ReglasCuidado.nivelTrasRacion(f.alimentacion)} %."
                }
            }
        }
    }

    fun cancelarAlimentar() {
        confirmandoAlimentar.value = null
    }

    fun alimentar() {
        confirmandoAlimentar.value = null
        ejecutar({ c.alimentarAnimal.ejecutar(ComandoAlimentarAnimal(c.idPartida, animal.valor, solicitud)) }) {
            "Alimentación al ${it.nivel} %. Se han consumido ${it.kilos} kg de bambú; quedan ${it.bambuRestante} kg."
        }
    }

    fun pedirTratamiento() {
        viewModelScope.launch {
            val lineas = withContext(Dispatchers.Default) { c.consultarCuidado.tratamiento(c.idPartida, animal.valor) }
            if (lineas.isEmpty()) {
                mensaje.value = Mensaje(Textos.motivo(MotivoRechazo.ConsumoMedicoNoConfigurado), false)
            } else {
                descartarMensaje()
                confirmandoTratar.value = "Se consumirán " +
                    lineas.joinToString(", ") { "${it.necesario} de ${it.nombre} (tienes ${it.disponible})" } +
                    " y el animal quedará sano."
            }
        }
    }

    fun cancelarTratamiento() {
        confirmandoTratar.value = null
    }

    fun tratar() {
        confirmandoTratar.value = null
        ejecutar({ c.tratarAnimal.ejecutar(ComandoTratarAnimal(c.idPartida, animal.valor, solicitud)) }) {
            "Tratamiento aplicado. El animal está sano; se han consumido ${it.unidades} unidades de insumos médicos."
        }
    }

    /** Espacio de origen vacío tras la venta: se ofrece su baja. `null` si no hay oferta. */
    val ofertaBaja = MutableStateFlow<Ref?>(null)
    val terminado = MutableStateFlow(false)

    fun pedirVenta() {
        confirmandoVenta.value = true
    }

    fun cancelarVenta() {
        confirmandoVenta.value = false
    }

    fun vender() {
        confirmandoVenta.value = false
        val confirmado = ficha.value?.importeVenta
        ejecutar({ c.venderAnimal.ejecutar(ComandoVenderAnimal(c.idPartida, animal.valor, solicitud, confirmado)) }) {
            if (it.origenVacio) ofertaBaja.value = Ref(it.idEspacioOrigen) else terminado.value = true
            "Animal vendido por ${Textos.dinero(it.importe)}."
        }
    }

    fun darDeBaja(origen: Ref) {
        ejecutar({ c.darDeBajaEspacio.ejecutar(ComandoDarDeBajaEspacio(c.idPartida, origen.valor, solicitud)) }) {
            ofertaBaja.value = null
            terminado.value = true
            "El espacio se ha dado de baja."
        }
    }

    fun mantenerEspacio() {
        ofertaBaja.value = null
        terminado.value = true
    }
}

/** Confirmación de alimentación grupal: total de kilos mostrado y texto del diálogo. */
data class GrupalUi(val kilos: Int, val texto: String)

/** Candidatos de la edición: parejas y amigos posibles dentro del espacio del animal. */
data class CandidatosUi(val parejas: List<CandidatoUi>, val amigos: List<CandidatoUi>)

/** CU-05: edición de nombre, habilidades, rasgos, pareja y mejor amigo. */
class EditarFichaViewModel(c: AppContainer, private val animal: Ref) : BaseViewModel(c) {
    val ficha: StateFlow<FichaAnimalUi?> = observar<FichaAnimalUi?>(null) {
        c.consultarAnimales.ficha(c.idPartida, animal.valor)?.let { Textos.ficha(it, c.catalogoFicha, c.ciclosPorNivel) }
    }

    val candidatos: StateFlow<CandidatosUi> = observar(CandidatosUi(emptyList(), emptyList())) {
        val espacio = c.consultarAnimales.ficha(c.idPartida, animal.valor)?.idEspacio
        val lista = if (espacio == null) emptyList() else c.consultarAnimales.animalesDe(c.idPartida, espacio)
        val yo = lista.firstOrNull { it.id == animal.valor }
        val otros = lista.filter { it.id != animal.valor }
        CandidatosUi(
            parejas = otros.filter {
                yo != null && yo.nivel >= ReglasNivel.NIVEL_MINIMO_PAREJA && it.idEspecie == yo.idEspecie &&
                    it.nivel >= ReglasNivel.NIVEL_MINIMO_PAREJA && (it.idPareja == null || it.id == yo.idPareja)
            }.let(Textos::candidatos),
            amigos = otros.let(Textos::candidatos),
        )
    }
    val habilidades: List<OpcionUi> = opciones(c.catalogoFicha.habilidades)
    val rasgos: List<OpcionUi> = opciones(c.catalogoFicha.rasgos)
    val guardado = MutableStateFlow(false)

    fun guardar(nombre: String, habilidades: Set<String>, rasgos: Set<String>, pareja: Ref?, amigo: Ref?) {
        val comando = ComandoEditarFicha(
            c.idPartida, animal.valor, nombre, habilidades.toList(), rasgos.toList(), pareja?.valor, amigo?.valor, solicitud,
        )
        ejecutar({ c.editarFicha.ejecutar(comando) }) {
            guardado.value = true
            "Ficha guardada."
        }
    }
}

/** CU-04: traslado a otro espacio activo con capacidad. */
class ReubicarViewModel(c: AppContainer, private val animal: Ref) : BaseViewModel(c) {
    val nombre: StateFlow<String> = observar("") { c.consultarAnimales.ficha(c.idPartida, animal.valor)?.nombre ?: "" }
    val destinos: StateFlow<List<DestinoUi>> = observar(emptyList()) {
        val actual = c.consultarAnimales.ficha(c.idPartida, animal.valor)?.idEspacio
        c.consultarAnimales.destinosPosibles(c.idPartida, actual).map(Textos::destino)
    }
    val ofertaBaja = MutableStateFlow<Ref?>(null)
    val terminado = MutableStateFlow(false)

    fun reubicar(destino: Ref) {
        ejecutar({ c.reubicarAnimal.ejecutar(ComandoReubicarAnimal(c.idPartida, animal.valor, destino.valor, solicitud)) }) {
            if (it.origenVacio) ofertaBaja.value = Ref(it.idEspacioOrigen) else terminado.value = true
            "Animal trasladado."
        }
    }

    fun darDeBaja(origen: Ref) {
        ejecutar({ c.darDeBajaEspacio.ejecutar(ComandoDarDeBajaEspacio(c.idPartida, origen.valor, solicitud)) }) {
            ofertaBaja.value = null
            terminado.value = true
            "El espacio se ha dado de baja."
        }
    }

    fun mantenerEspacio() {
        ofertaBaja.value = null
        terminado.value = true
    }
}

/** Crea ViewModels con el contenedor de dependencias; los argumentos de navegación son referencias opacas. */
inline fun <reified VM : ViewModel> fabrica(crossinline crear: () -> VM): ViewModelProvider.Factory =
    viewModelFactory { initializer { crear() } }

/** CU-10: detalle de la concepción y registro de la cría con su ficha. */
class CriaViewModel(c: AppContainer, private val aviso: Ref) : BaseViewModel(c) {
    private val id: Long? = aviso.valor.toLongOrNull()

    val detalle: StateFlow<DetalleReproduccion?> = observar<DetalleReproduccion?>(null) {
        id?.let { c.consultarReproduccion.ejecutar(c.idPartida, it) }
    }

    /** Animales del espacio de los progenitores: candidatos a mejor amigo de la cría. */
    val companeros: StateFlow<List<CandidatoUi>> = observar(emptyList()) {
        val espacio = id?.let { c.consultarReproduccion.ejecutar(c.idPartida, it) }?.idEspacio
        if (espacio == null) emptyList() else c.consultarAnimales.animalesDe(c.idPartida, espacio).let(Textos::candidatos)
    }

    val habilidades: List<OpcionUi> = opciones(c.catalogoFicha.habilidades)
    val rasgos: List<OpcionUi> = opciones(c.catalogoFicha.rasgos)
    val registrada = MutableStateFlow(false)

    fun registrar(nombre: String, habilidades: Set<String>, rasgos: Set<String>, amigo: Ref?) {
        val idAviso = id ?: return
        val comando = ComandoAceptarReproduccion(
            idPartida = c.idPartida,
            idNotificacion = idAviso,
            ficha = FichaNueva(nombre, habilidades.toList(), rasgos.toList(), amigo?.valor),
            idSolicitud = solicitud,
        )
        ejecutar({ c.aceptarReproduccion.ejecutar(comando) }) {
            registrada.value = true
            "Cría registrada."
        }
    }
}
