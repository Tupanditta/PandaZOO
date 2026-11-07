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

    data object EspecieInexistente : MotivoRechazo("ESPECIE_INEXISTENTE")

    data object EspecieBloqueada : MotivoRechazo("ESPECIE_BLOQUEADA")

    data object EspecieExclusiva : MotivoRechazo("ESPECIE_EXCLUSIVA")

    data object EspacioLleno : MotivoRechazo("ESPACIO_LLENO")

    data object NombreInvalido : MotivoRechazo("NOMBRE_INVALIDO")

    data object FichaIncompleta : MotivoRechazo("FICHA_INCOMPLETA")

    data object ParejaInexistente : MotivoRechazo("PAREJA_INEXISTENTE")

    data object ParejaOtraEspecie : MotivoRechazo("PAREJA_OTRA_ESPECIE")

    data object ParejaOtroEspacio : MotivoRechazo("PAREJA_OTRO_ESPACIO")

    data object ParejaMenorDeEdad : MotivoRechazo("PAREJA_MENOR_DE_EDAD")

    data object ParejaOcupada : MotivoRechazo("PAREJA_OCUPADA")

    data object AmigoInvalido : MotivoRechazo("AMIGO_INVALIDO")

    data object AutoReferencia : MotivoRechazo("AUTOREFERENCIA")

    data object AnimalInexistente : MotivoRechazo("ANIMAL_INEXISTENTE")

    data object AnimalNoVivo : MotivoRechazo("ANIMAL_NO_VIVO")

    data object AnimalEnfermo : MotivoRechazo("ANIMAL_ENFERMO")

    data object SinAlimentacion : MotivoRechazo("SIN_ALIMENTACION")

    data object DestinoIgualAOrigen : MotivoRechazo("DESTINO_IGUAL_A_ORIGEN")

    data object EspacioNoVacio : MotivoRechazo("ESPACIO_NO_VACIO")

    data object EspacioConDependencias : MotivoRechazo("ESPACIO_CON_DEPENDENCIAS")

    /** Animal ya al 100 % de alimentación (CU-13, 4.a). */
    data object AlimentacionCompleta : MotivoRechazo("ALIMENTACION_COMPLETA")

    /** No hay animales vivos por debajo del 100 % en el espacio (CU-13, 1.a). */
    data object NadaQueAlimentar : MotivoRechazo("NADA_QUE_ALIMENTAR")

    /** Falta la cantidad de bambú de alguna especie incluida: no se supone ninguna ración (CU-13, 4.b). */
    data object RacionNoConfigurada : MotivoRechazo("RACION_NO_CONFIGURADA")

    /** El total grupal cambió entre la vista previa y la confirmación (CU-13, 4.a). */
    data class ConjuntoCambiado(val requerido: Int) : MotivoRechazo("CONJUNTO_CAMBIADO")

    data class BambuInsuficiente(val requerido: Int, val disponible: Int) : MotivoRechazo("BAMBU_INSUFICIENTE") {
        val faltante: Int get() = requerido - disponible
    }

    /** El animal no está enfermo (CU-09, 4.a). */
    data object AnimalSano : MotivoRechazo("ANIMAL_SANO")

    /** No hay consumo médico configurado para el rango del animal (CU-09, 4.b). */
    data object ConsumoMedicoNoConfigurado : MotivoRechazo("CONSUMO_MEDICO_NO_CONFIGURADO")

    data class InsumosInsuficientes(val faltantes: List<FaltanteInsumo>) : MotivoRechazo("INSUMOS_INSUFICIENTES")

    /** La partida o el motor de simulación están pausados o finalizados (CU-11, 2.b). */
    data object SimulacionNoActiva : MotivoRechazo("SIMULACION_NO_ACTIVA")

    /** El número de ciclo solicitado no es el siguiente al último confirmado. */
    data class CicloFueraDeOrden(val esperado: Int) : MotivoRechazo("CICLO_FUERA_DE_ORDEN")

    /** No hay beneficios pendientes de reclamar (CU-08, 1.a). */
    data object SinBeneficios : MotivoRechazo("SIN_BENEFICIOS")

    /** El importe pendiente cambió entre la vista previa y la confirmación (CU-08, 4.a). */
    data class BeneficiosCambiados(val vigente: Dinero) : MotivoRechazo("BENEFICIOS_CAMBIADOS")

    /** La concepción expiró, ya se aceptó o su aviso ya no está abierto (CU-10, 2.a y 4.b). */
    data object ConcepcionNoVigente : MotivoRechazo("CONCEPCION_NO_VIGENTE")

    /** Algún progenitor murió, se vendió, cambió de espacio o dejó de ser pareja (regla 5.3). */
    data object ProgenitoresNoValidos : MotivoRechazo("PROGENITORES_NO_VALIDOS")

    data object AvisoInexistente : MotivoRechazo("AVISO_INEXISTENTE")

    /** La instancia no existe o ya se reclamó y fue sustituida por la siguiente (CU-15, 4.a). */
    data object MisionInexistente : MotivoRechazo("MISION_INEXISTENTE")

    data object MisionNoCumplida : MotivoRechazo("MISION_NO_CUMPLIDA")

    /** Tipo, contenido o cantidad de la recompensa sin definir o no válido (CU-15, 4.b). */
    data object RecompensaNoValida : MotivoRechazo("RECOMPENSA_NO_VALIDA")

    /** Falta la operación o la constante de progresión, o el resultado no es válido (CU-15, 4.c). */
    data object ProgresionInvalida : MotivoRechazo("PROGRESION_INVALIDA")

    data object ConflictoIdempotencia : MotivoRechazo("CONFLICTO_IDEMPOTENCIA")

    data object PartidaInexistente : MotivoRechazo("PARTIDA_INEXISTENTE")
}

/** Insumo del que no hay existencias suficientes: nombre legible, cantidad necesaria y disponible. */
data class FaltanteInsumo(val nombre: String, val necesario: Int, val disponible: Int)
