package com.pandazoo.application.puerto

import com.pandazoo.domain.modelo.Espacio
import com.pandazoo.domain.animal.ParametrosNivelRango
import com.pandazoo.domain.animal.ReglasNivel
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.modelo.Existencia
import com.pandazoo.domain.modelo.Habitat
import com.pandazoo.domain.modelo.IdPartida
import com.pandazoo.domain.modelo.OfertaRecurso
import com.pandazoo.domain.modelo.OpcionCapacidad
import com.pandazoo.domain.modelo.Recurso
import com.pandazoo.domain.satisfaccion.AnimalVivo
import com.pandazoo.domain.satisfaccion.MediasSatisfaccion
import kotlinx.coroutines.flow.Flow

/** Reloj de pared en segundos desde la época Unix. El dominio nunca consulta el reloj del sistema. */
interface Reloj {
    fun ahoraSegundos(): Long
}

/** Azar uniforme en `[0,1)`; los tests lo sustituyen por una secuencia fija. */
interface Azar {
    fun siguienteDouble(): Double
}

interface GeneradorId {
    fun nuevo(): String
}

/** Ejecuta el bloque como una única transacción: confirma si termina y revierte si lanza (RF-45). */
interface Transaccion {
    fun <T> ejecutar(bloque: () -> T): T
}

/** Emite tras cada transacción confirmada; la interfaz se repinta a partir de datos ya persistidos (RF-44). */
interface NotificadorCambios {
    val cambios: Flow<Unit>
}

enum class TipoMovimiento { COMPRA, VENTA, GASTO, INGRESO, RECOMPENSA }

interface EconomiaRepositorio {
    fun existePartida(idPartida: IdPartida): Boolean

    fun fondos(idPartida: IdPartida): Dinero

    fun actualizarFondos(idPartida: IdPartida, nuevo: Dinero)

    fun registrarMovimiento(
        idPartida: IdPartida,
        tipo: TipoMovimiento,
        importe: Dinero,
        origen: String,
        numeroCiclo: Int,
        fecha: Long,
    )

    /** Suma de beneficios generados por ciclos confirmados y aún no reclamados. */
    fun beneficiosPendientes(idPartida: IdPartida): Dinero
}

interface InventarioRepositorio {
    fun existencias(idPartida: IdPartida): List<Existencia>

    fun cantidad(idPartida: IdPartida, recurso: Recurso): Int?

    /** Suma `cantidad` al inventario; falla si el resultado fuese negativo (RF-50). */
    fun incrementar(idPartida: IdPartida, recurso: Recurso, cantidad: Int)
}

interface CatalogoRepositorio {
    /** Recursos que el gestor puede comprar. */
    fun recursosElegibles(): List<OfertaRecurso>

    fun oferta(recurso: Recurso): OfertaRecurso?

    /** Capacidades de espacio con su coste, ordenadas de menor a mayor. */
    fun opcionesCapacidad(): List<OpcionCapacidad>
}

/**
 * Consulta de hábitats y gestión de espacios. No ofrece ninguna operación de alta, edición ni baja de
 * hábitats (RES-05): solo la carga inicial autorizada los crea.
 */
interface ZooRepositorio {
    fun habitats(idPartida: IdPartida): List<Habitat>

    fun habitat(idPartida: IdPartida, idHabitat: String): Habitat?

    fun espacios(idPartida: IdPartida, idHabitat: String): List<Espacio>

    fun espacio(idPartida: IdPartida, idEspacio: String): Espacio?

    fun crearEspacio(idPartida: IdPartida, idEspacio: String, idHabitat: String, nombre: String, capacidadMax: Int)

    fun reconfigurarEspacio(idPartida: IdPartida, idEspacio: String, nombre: String, idHabitat: String)

    /** Espacios activos con aforo libre de hábitats utilizables, con el nombre de su hábitat. */
    fun espaciosConCupo(idPartida: IdPartida): List<EspacioConHabitat>

    /** Operaciones pendientes o avisos abiertos que dependen del espacio e impiden su baja lógica (CU-14, RF-07). */
    fun tieneDependencias(idPartida: IdPartida, idEspacio: String): Boolean

    /** Baja lógica: pasa a `INACTIVO` conservando capacidad y referencias. */
    fun darDeBaja(idPartida: IdPartida, idEspacio: String)
}

enum class ResultadoAuditoria { CONFIRMADO, RECHAZADO, REVERTIDO }

data class RegistroAuditoria(
    val idPartida: IdPartida,
    val tipo: String,
    val entidad: String,
    val idEntidad: String?,
    val origen: String,
    val resultado: ResultadoAuditoria,
    val numeroCiclo: Int,
    val claveIdempotencia: String?,
    val fecha: Long,
    val detalle: String,
)

interface AuditoriaRepositorio {
    /** Sin partida no hay a qué asociar una auditoría, así que ese rechazo no se registra. */
    fun existePartida(idPartida: IdPartida): Boolean

    /** Registro confirmado con esa clave de idempotencia, si existe dentro de la ventana de retención. */
    fun confirmadoPorClave(idPartida: IdPartida, clave: String): RegistroAuditoria?

    fun registrar(registro: RegistroAuditoria)
}

data class EspacioSatisfaccion(val id: String, val idHabitat: String, val idBioma: String)

/** Datos que necesita el recálculo de satisfacción y donde persiste su resultado (`EspecificacionBBDD.md`, 4.3). */
interface SatisfaccionRepositorio {
    fun espacios(idPartida: IdPartida): List<EspacioSatisfaccion>

    /** Animales con `estado_vital = 'VIVO'` e `id_espacio` no nulo: la única entrada del cálculo. */
    fun animalesVivosAlojados(idPartida: IdPartida): List<AnimalVivo>

    /** `Animal.satisfaccion_actual` persistida de los animales vivos alojados. */
    fun satisfaccionGuardada(idPartida: IdPartida): Map<String, Double>

    fun guardarSatisfaccionAnimales(idPartida: IdPartida, valores: Map<String, Double>)

    /**
     * Sustituye las métricas vigentes: una fila por espacio y hábitat con animales vivos y la fila de partida.
     * No guarda histórico, así que el número de filas no crece con los ciclos.
     */
    fun reemplazarMetricas(idPartida: IdPartida, medias: MediasSatisfaccion, fecha: Long)
}

data class HabitatBasico(val id: String, val nombre: String)

data class EspacioBasico(val id: String, val idHabitat: String, val nombre: String)

data class AnimalActivo(val idEspacio: String, val nombre: String, val especie: String, val valor: Double, val nivel: Int = 1)

/** Lecturas del monitor (CU-17). Todas son de solo lectura sobre valores ya persistidos. */
interface MonitorRepositorio {
    fun existePartida(idPartida: IdPartida): Boolean

    /** Métrica global persistida, o nulo si no existe fila. */
    fun satisfaccionParque(idPartida: IdPartida): Double?

    fun satisfaccionHabitats(idPartida: IdPartida): Map<String, Double>

    fun satisfaccionEspacios(idPartida: IdPartida): Map<String, Double>

    fun habitats(idPartida: IdPartida): List<HabitatBasico>

    fun espacios(idPartida: IdPartida): List<EspacioBasico>

    /** Solo animales vivos con espacio asignado. */
    fun animales(idPartida: IdPartida): List<AnimalActivo>
}

/** Estado de una especie en la tienda de animales (RF-10, RF-49). */
enum class EstadoTienda { BLOQUEADA, COMERCIAL, EXCLUSIVA }

data class EspecieTienda(
    val id: String,
    val nombre: String,
    val rango: String,
    val estado: EstadoTienda,
    /** Coste ordinario fijo del rango (`CosteRango`). */
    val coste: Dinero,
)

/** Animal con lo necesario para validar vínculos, traslados y ventas. */
data class AnimalResumen(
    val id: String,
    val idEspecie: String,
    val especie: String,
    val nombre: String,
    val idEspacio: String?,
    val nivel: Int,
    val ciclosNivel: Int,
    val parametros: ParametrosNivelRango,
    val alimentacion: Int,
    val enfermo: Boolean,
    val vivo: Boolean,
    val idPareja: String?,
    val idMejorAmigo: String?,
    val satisfaccion: Double,
    val rango: String,
)

/** Ficha técnica para mostrar (RF-25). Los identificadores solo sirven para navegar, nunca se muestran. */
data class AnimalFicha(
    val id: String,
    val nombre: String,
    val especie: String,
    val rango: String,
    val estadoVital: String,
    val idEspacio: String?,
    val nombreEspacio: String?,
    val nombreHabitat: String?,
    val nivel: Int,
    val ciclosNivel: Int,
    val parametros: ParametrosNivelRango,
    val alimentacion: Int,
    val enfermo: Boolean,
    val satisfaccion: Double,
    val idPareja: String?,
    val nombrePareja: String?,
    val idMejorAmigo: String?,
    val nombreMejorAmigo: String?,
    val habilidades: List<String>,
    val rasgos: List<String>,
)

data class NuevoAnimal(
    val id: String,
    val idEspecie: String,
    val idEspacio: String,
    val nombre: String,
    val habilidades: List<String>,
    val rasgos: List<String>,
    val idPareja: String?,
    val idMejorAmigo: String?,
    val idPadre: String? = null,
    val idMadre: String? = null,
)

interface AnimalRepositorio {
    /** Todas las especies con su estado de catálogo en la partida, ordenadas por rango y nombre. */
    fun especiesTienda(idPartida: IdPartida): List<EspecieTienda>

    fun especieTienda(idPartida: IdPartida, idEspecie: String): EspecieTienda?

    fun animal(idPartida: IdPartida, idAnimal: String): AnimalResumen?

    fun ficha(idPartida: IdPartida, idAnimal: String): AnimalFicha?

    /** Animales vivos alojados en el espacio, por nombre. */
    fun animalesDeEspacio(idPartida: IdPartida, idEspacio: String): List<AnimalResumen>

    /** Inserta el animal con su ficha y vínculos; la pareja se vuelve recíproca. La satisfacción se calcula después. */
    fun crear(idPartida: IdPartida, nuevo: NuevoAnimal)

    fun actualizarFicha(idPartida: IdPartida, idAnimal: String, nombre: String, habilidades: List<String>, rasgos: List<String>)

    /** Fija la pareja de ambos extremos de forma recíproca; `null` libera también a la pareja anterior. */
    fun fijarPareja(idPartida: IdPartida, idAnimal: String, idPareja: String?)

    fun fijarMejorAmigo(idPartida: IdPartida, idAnimal: String, idAmigo: String?)

    fun mover(idPartida: IdPartida, idAnimal: String, idEspacio: String)

    /** Baja lógica (`VENDIDO` o `MUERTO`): sin espacio, sin vínculos mutables y sin rasgos ni habilidades. Conserva la genealogía. */
    fun retirar(idPartida: IdPartida, idAnimal: String, estadoVital: String)

    /** Espacios de animales vivos alojados cuya satisfacción depende de este animal (vínculos y familia). */
    fun espaciosRelacionados(idPartida: IdPartida, idAnimal: String): Set<String>
}

/** Insumo médico y unidades que exige el tratamiento de un rango. */
data class ConsumoMedico(val idInsumo: String, val nombre: String, val cantidad: Int)

/** Lectura y escritura del cuidado manual (CU-13, CU-09). Las cantidades salen siempre del catálogo persistido. */
interface CuidadoRepositorio {
    /** Kilogramos de bambú por cada incremento de 10 puntos de la especie, o `null` si no están configurados. */
    fun kilosPorIncremento(idEspecie: String): Int?

    /** Insumos que consume el tratamiento del animal según su rango; vacío si no hay configuración. */
    fun consumoMedico(idPartida: IdPartida, idAnimal: String): List<ConsumoMedico>

    /** Fija el nivel de alimentación (múltiplo de 10) y, al ser mayor que 0, reinicia el contador de desnutrición. */
    fun fijarAlimentacion(idPartida: IdPartida, idAnimal: String, nivel: Int)

    /** Restablece la salud a sano y reinicia el contador de enfermedad. */
    fun curar(idPartida: IdPartida, idAnimal: String)

    /**
     * Cierra como `RESUELTA` el aviso crítico de salud abierto del animal cuando ya no queda ningún contador
     * activo (desnutrición ni enfermedad); si aún corresponde, lo conserva.
     */
    fun resolverAlertaSalud(idPartida: IdPartida, idAnimal: String)
}

data class EspacioConHabitat(val espacio: Espacio, val nombreHabitat: String)

/** Importe que acreditaría la venta del animal ahora, según su nivel y su rango (RF-52). */
fun AnimalResumen.valorVenta(): Dinero = ReglasNivel.valorVenta(parametros, nivel)

fun AnimalFicha.valorVenta(): Dinero = ReglasNivel.valorVenta(parametros, nivel)

/** Nivel en el que la venta devuelve exactamente el valor base (ni pérdida ni ganancia). */
fun ParametrosNivelRango.nivelEquilibrio(): Int = ReglasNivel.nivelEquilibrio(nivelMaximo)
