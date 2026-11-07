package com.pandazoo.application.puerto

import com.pandazoo.domain.mision.Progresion
import com.pandazoo.domain.modelo.IdPartida

enum class EstadoMision { PENDIENTE, CUMPLIDA, AGOTADA }

enum class TipoRecompensa { FONDOS, ALIMENTO, INSUMO, ESPECIE }

/** Recompensa fija del tipo de misión (`Mision`): no progresa con las instancias. */
data class RecompensaConfigurada(val tipo: TipoRecompensa, val idItem: String?, val cantidad: Double)

/** Instancia vigente de un tipo de misión, con los parámetros actuales ya progresados. */
data class InstanciaMision(
    val idInstancia: String,
    val idMision: String,
    val tipoObjetivo: String,
    /** `true` si el progreso es dinámico (se recalcula); `false` si es acumulativo. */
    val dinamica: Boolean,
    val progreso: Double,
    val estado: EstadoMision,
    val numericos: Map<String, Double>,
    val textos: Map<String, String>,
    val recompensa: RecompensaConfigurada,
)

/** Recompensa de una instancia cumplida, tal y como se persiste (`RecompensaMision`). */
data class RecompensaRegistrada(
    val idInstancia: String,
    val tipo: TipoRecompensa,
    val idItem: String?,
    val cantidad: Double,
    val idEspecieSeleccionada: String?,
    val unidadPendiente: Boolean,
)

/** Unidad de coste cero concedida por una recompensa de animal (RF-38). */
interface UnidadesGratuitas {
    /** Especies con al menos una unidad pendiente de comprar. */
    fun especiesConUnidad(idPartida: IdPartida): Set<String>

    /** Marca como comprada una unidad pendiente de la especie. Devuelve `false` si no había ninguna. */
    fun consumirUnidad(idPartida: IdPartida, idEspecie: String): Boolean
}

/** Persistencia de misiones, progreso y recompensas (CU-15, CU-20). */
interface MisionRepositorio : UnidadesGratuitas {
    /** Instancias de la partida (incluidas las agotadas), ordenadas por tipo. */
    fun instancias(idPartida: IdPartida): List<InstanciaMision>

    fun instancia(idPartida: IdPartida, idInstancia: String): InstanciaMision?

    /** Progresión configurada de cada parámetro numérico progresable del tipo. */
    fun progresiones(idMision: String): Map<String, Progresion>

    fun fijarProgreso(idPartida: IdPartida, idInstancia: String, progreso: Double, estado: EstadoMision)

    /** Retira la instancia y crea la siguiente (`PENDIENTE`, progreso 0) con los parámetros indicados, en el mismo tipo. */
    fun sustituirInstancia(idPartida: IdPartida, anterior: InstanciaMision, nuevaId: String, numericos: Map<String, Double>)

    /** La instancia pasa a `AGOTADA`: el tipo deja de mostrarse y no se vuelve a generar. */
    fun marcarAgotada(idPartida: IdPartida, idInstancia: String)

    /** Valor actual de una misión dinámica, calculado sobre lo persistido. */
    fun valorDinamico(idPartida: IdPartida, instancia: InstanciaMision): Double

    fun recompensa(idPartida: IdPartida, idInstancia: String): RecompensaRegistrada?

    /** Registra la recompensa reclamada; `unidadPendiente` solo para recompensas de animal. */
    fun registrarRecompensa(
        idPartida: IdPartida,
        idMision: String,
        idInstancia: String,
        recompensa: RecompensaConfigurada,
        idEspecie: String?,
        numeroCiclo: Int,
        fecha: Long,
    )

    /** Desbloquea la especie con origen `RECOMPENSA` y emite el aviso informativo si no existía. */
    fun desbloquearEspecie(idPartida: IdPartida, idEspecie: String, numeroCiclo: Int, fecha: Long)

    fun nombreEspecie(idEspecie: String): String?

    fun nombreHabitat(idPartida: IdPartida, codigo: String): String?

    fun nombreRecurso(idItem: String): String?
}
