package com.pandazoo.domain.modelo

import com.pandazoo.domain.economia.Dinero

/** Identificador de partida. Es un identificador técnico: nunca se muestra (RES-06). */
typealias IdPartida = String

enum class TipoRecurso(val unidad: String) {
    ALIMENTO("kg"),
    INSUMO("unidades"),
}

/** Recurso comprable: bambú (alimento) o insumo médico. */
data class Recurso(val tipo: TipoRecurso, val codigo: String)

/** Recurso con su nombre legible, unidad y precio unitario. `precio` es nulo si no está configurado. */
data class OfertaRecurso(
    val recurso: Recurso,
    val nombre: String,
    val precio: Dinero?,
) {
    val unidad: String get() = recurso.tipo.unidad
}

data class Existencia(
    val recurso: Recurso,
    val nombre: String,
    val cantidad: Int,
) {
    val unidad: String get() = recurso.tipo.unidad
}

enum class EstadoEntidad { ACTIVO, INACTIVO }

/** Hábitat precargado y de solo lectura (RES-05). `desbloqueado` indica si hay fila de desbloqueo. */
data class Habitat(
    val id: String,
    val codigo: String,
    val nombre: String,
    val estado: EstadoEntidad,
    val desbloqueado: Boolean,
) {
    /** Un hábitat solo admite espacios nuevos o traslados cuando está activo y desbloqueado. */
    val utilizable: Boolean get() = estado == EstadoEntidad.ACTIVO && desbloqueado
}

data class Espacio(
    val id: String,
    val idHabitat: String,
    val nombre: String,
    val capacidadMax: Int,
    val estado: EstadoEntidad,
    val ocupacion: Int,
)

/** Capacidad ofrecida para un nuevo espacio y su coste. */
data class OpcionCapacidad(val capacidad: Int, val coste: Dinero)

data class InventarioFinanzas(
    val fondos: Seccion<Dinero>,
    val existencias: Seccion<List<Existencia>>,
    val beneficiosPendientes: Seccion<Dinero>,
)

/** Sección de una consulta que puede no estar disponible sin presentarla como cero (CU-16, 2.a y 3.a). */
sealed interface Seccion<out T> {
    data class Disponible<T>(val valor: T) : Seccion<T>

    data class NoDisponible(val motivo: String) : Seccion<Nothing>
}
