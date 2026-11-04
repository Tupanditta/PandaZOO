package com.pandazoo.application.puerto

import com.pandazoo.domain.modelo.Espacio
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.modelo.Existencia
import com.pandazoo.domain.modelo.Habitat
import com.pandazoo.domain.modelo.IdPartida
import com.pandazoo.domain.modelo.OfertaRecurso
import com.pandazoo.domain.modelo.OpcionCapacidad
import com.pandazoo.domain.modelo.Recurso
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
