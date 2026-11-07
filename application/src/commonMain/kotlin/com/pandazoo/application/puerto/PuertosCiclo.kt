package com.pandazoo.application.puerto

import com.pandazoo.domain.animal.ParametrosNivelRango
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.modelo.IdPartida

/** Estado persistido del motor de simulación (`EstadoSimulacion`) junto con el de la partida. */
data class EstadoMotor(
    val numeroUltimo: Int?,
    val remanenteSegundos: Int,
    val partidaActiva: Boolean,
    val simulacionActiva: Boolean,
    /**
     * Marca de última actividad en segundos desde la época Unix (regla de reloj 5.2): se escribe en cada ciclo
     * confirmado, al pasar a segundo plano y al terminar una recuperación diferida. Es `instante_ultimo_ciclo`.
     */
    val marcaActividad: Long? = null,
) {
    val siguiente: Int get() = (numeroUltimo ?: 0) + 1
}

/** Animal vivo y alojado tal y como entra al ciclo: su nivel y la satisfacción persistida al inicio. */
data class AnimalCiclo(
    val id: String,
    val idEspecie: String,
    val nivel: Int,
    val ciclosNivel: Int,
    val alimentacion: Int,
    val enfermo: Boolean,
    val satisfaccion: Double,
    val probabilidadEnfermedad: Double,
    val parametros: ParametrosNivelRango,
    val ciclosDesnutricion: Int,
    val ciclosEnfermedad: Int,
)

/** Resultado de las etapas biológicas que se persiste de un animal que sigue vivo. */
data class EstadoAnimalCiclo(
    val id: String,
    val nivel: Int,
    val ciclosNivel: Int,
    val alimentacion: Int,
    val enfermo: Boolean,
    val ciclosDesnutricion: Int,
    val ciclosEnfermedad: Int,
)

/** Persistencia del ciclo de simulación (CU-11). Todas las escrituras ocurren dentro de la transacción del ciclo. */
interface CicloRepositorio {
    fun estadoMotor(idPartida: IdPartida): EstadoMotor?

    /** Animales con `estado_vital = 'VIVO'` e `id_espacio` no nulo, ordenados por identificador. */
    fun animalesVivosAlojados(idPartida: IdPartida): List<AnimalCiclo>

    fun guardarEstados(idPartida: IdPartida, estados: List<EstadoAnimalCiclo>)

    /** Inserta el ciclo como `CONFIRMADO`. */
    fun registrarCiclo(idPartida: IdPartida, numero: Int, inicio: Long, fin: Long)

    /** Fija el último ciclo, su instante y el remanente del temporizador. */
    fun actualizarEstadoMotor(idPartida: IdPartida, numero: Int, instante: Long, remanenteSegundos: Int)

    /**
     * Persiste el remanente y, si se indica, la marca de última actividad en la misma sentencia (regla 5.2, punto 1).
     * Sin `marca` la marca anterior no cambia.
     */
    fun guardarRemanente(idPartida: IdPartida, segundos: Int, marca: Long? = null)

    fun registrarBeneficio(idPartida: IdPartida, numeroCiclo: Int, importe: Dinero, clave: String, fecha: Long)

    /** Identificadores de animales con aviso crítico de salud abierto (`PENDIENTE` o `VISTA`). */
    fun alertasCriticasAbiertas(idPartida: IdPartida): Set<String>

    fun abrirAlertasCriticas(idPartida: IdPartida, idsAnimal: Collection<String>, numeroCiclo: Int, fecha: Long)

    fun resolverAlertasCriticas(idPartida: IdPartida, idsAnimal: Collection<String>, numeroCiclo: Int)
}

data class BeneficioCiclo(val numeroCiclo: Int, val importe: Dinero, val reclamado: Boolean)

/** Beneficios acumulados y su reclamación (CU-08, CU-16). */
interface BeneficioRepositorio {
    fun pendientes(idPartida: IdPartida): Dinero

    /** Marca como `RECLAMADO` todos los pendientes; devuelve cuántos registros incluía. */
    fun reclamarTodos(idPartida: IdPartida, fecha: Long): Int

    /** Últimos beneficios por ciclo, el más reciente primero. */
    fun recientes(idPartida: IdPartida, limite: Int): List<BeneficioCiclo>
}

enum class CausaCritica { DESNUTRICION, ENFERMEDAD }

/** Fila del seguimiento crítico (CU-18). Los contadores son los persistidos; no se calcula ningún sustituto. */
data class AnimalCritico(
    val id: String,
    val nombre: String,
    val especie: String,
    val nivel: Int,
    val sinAlimento: Boolean,
    val enfermo: Boolean,
    val ciclosDesnutricion: Int,
    val ciclosEnfermedad: Int,
)

interface SeguimientoRepositorio {
    /** Animales vivos alojados con alimentación 0 % o enfermos. */
    fun criticos(idPartida: IdPartida): List<AnimalCritico>
}

enum class SeveridadAviso { BAJA, MEDIA, ALTA }

enum class TipoAviso { INFORMATIVA, OPERATIVA, CRITICA }

data class Aviso(
    val id: Long,
    val tipo: TipoAviso,
    val severidad: SeveridadAviso,
    val estado: String,
    val codigo: String,
    val texto: String,
    val fecha: Long,
    val numeroCiclo: Int,
    /** Id del animal de una alerta de salud; solo sirve para navegar a su ficha. */
    val idAnimal: String?,
)

interface AvisoRepositorio {
    /** Avisos de la partida, el más reciente primero, filtrados opcionalmente por severidad. */
    fun historial(idPartida: IdPartida, severidad: SeveridadAviso?): List<Aviso>

    fun aviso(idPartida: IdPartida, id: Long): Aviso?

    /** `PENDIENTE` pasa a `VISTA`. Devuelve `false` si ya no estaba pendiente. */
    fun marcarVista(idPartida: IdPartida, id: Long, numeroCiclo: Int): Boolean
}

/** Purga acotada por la ventana de retención (`EspecificacionBBDD.md`, 5.2). */
interface RetencionRepositorio {
    /** Devuelve las filas borradas en total. */
    fun purgar(idPartida: IdPartida, numeroCiclo: Int, retencion: VentanasRetencion): Int
}

data class VentanasRetencion(
    val auditoria: Int,
    val movimientos: Int,
    val avisosCerrados: Int,
    val maximoAvisosCerrados: Int,
    val beneficiosCerrados: Int,
    val ciclos: Int,
    val idempotencia: Int,
    val recompensasCerradas: Int,
)
