package com.pandazoo.application.error

import com.pandazoo.domain.economia.Dinero

/** Los fallos de negocio son valores, no excepciones (design.md 2.3.7). */
sealed interface Resultado<out E> {
    /** `repetido` es verdadero cuando es el resultado persistido de una clave de idempotencia ya confirmada. */
    data class Exito<E>(val valor: E, val repetido: Boolean = false) : Resultado<E>

    data class Rechazado(val motivo: MotivoRechazo) : Resultado<Nothing>
}

/** Motivo tipado de rechazo. `codigo` es estable y es lo que se audita; nunca texto de interfaz. */
sealed class MotivoRechazo(val codigo: String) {
    data class SaldoInsuficiente(val coste: Dinero, val saldo: Dinero) : MotivoRechazo("SALDO_INSUFICIENTE")

    data object CantidadInvalida : MotivoRechazo("CANTIDAD_INVALIDA")

    data object RecursoNoElegible : MotivoRechazo("RECURSO_NO_ELEGIBLE")

    data object PrecioNoConfigurado : MotivoRechazo("PRECIO_NO_CONFIGURADO")

    data object CapacidadNoPermitida : MotivoRechazo("CAPACIDAD_NO_PERMITIDA")

    data object DenominacionInvalida : MotivoRechazo("DENOMINACION_INVALIDA")

    data object DenominacionDuplicada : MotivoRechazo("DENOMINACION_DUPLICADA")

    data object HabitatNoDisponible : MotivoRechazo("HABITAT_NO_DISPONIBLE")

    data object EspacioInexistente : MotivoRechazo("ESPACIO_INEXISTENTE")

    data object EspacioInactivo : MotivoRechazo("ESPACIO_INACTIVO")

    data object SinCambios : MotivoRechazo("SIN_CAMBIOS")

    data object ConflictoIdempotencia : MotivoRechazo("CONFLICTO_IDEMPOTENCIA")

    data object PartidaInexistente : MotivoRechazo("PARTIDA_INEXISTENTE")
}
