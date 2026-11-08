package com.pandazoo.application.puerto

import com.pandazoo.domain.modelo.IdPartida
import com.pandazoo.domain.prestigio.EstadoPrestigio

/** Censo que alimenta el prestigio: animales vivos alojados y su satisfacción media directa (`0` si no hay ninguno). */
data class CensoGlobal(val animalesVivos: Int, val satisfaccionGlobal: Double)

/** Persistencia del prestigio y de los desbloqueos por nivel (RF-35, RF-36, RF-43, RES-07). */
interface PrestigioRepositorio {
    /** Nivel actual y máximos persistidos; `null` si la partida no existe. */
    fun estado(idPartida: IdPartida): EstadoPrestigio?

    /** Calculado sobre los animales persistidos; se usa dentro del recálculo, tras guardar la satisfacción. */
    fun censo(idPartida: IdPartida): CensoGlobal

    /** Número del último ciclo confirmado (`0` si no hay ninguno). */
    fun ultimoCiclo(idPartida: IdPartida): Int

    fun guardar(idPartida: IdPartida, estado: EstadoPrestigio)

    /** Códigos de catálogo de las especies ya desbloqueadas (con fila de desbloqueo). */
    fun especiesDesbloqueadas(idPartida: IdPartida): Set<String>

    /** Códigos de catálogo de los hábitats ya desbloqueados. */
    fun habitatsDesbloqueados(idPartida: IdPartida): Set<String>

    /** Inserta el desbloqueo con origen `PRESTIGIO` (permanente). Devuelve `false` si el código no existe en el catálogo. */
    fun desbloquearEspecie(idPartida: IdPartida, idEspecie: String, fecha: Long): Boolean

    /** Inserta el desbloqueo del hábitat y devuelve su identificador técnico, o `null` si no existe. */
    fun desbloquearHabitat(idPartida: IdPartida, codigo: String, fecha: Long): String?

    /** Aviso informativo no interactivo; no inserta nada si ya existe uno para esa entidad. */
    fun avisarInformativo(idPartida: IdPartida, codigo: String, entidad: String, idEntidad: String, numeroCiclo: Int, fecha: Long)
}

/** Lecturas del panel de prestigio (CU-19), todas sobre valores ya persistidos. */
interface PanelPrestigioRepositorio {
    fun existePartida(idPartida: IdPartida): Boolean

    fun estado(idPartida: IdPartida): EstadoPrestigio?

    /** Animales vivos alojados vigentes. */
    fun animalesVivosAlojados(idPartida: IdPartida): Int

    /** Métrica global de satisfacción persistida, o `null` si no existe. */
    fun satisfaccionGlobal(idPartida: IdPartida): Double?

    fun nombreEspecie(idEspecie: String): String?

    /** Nombre del hábitat de código [codigo], sin la palabra «Hábitat». */
    fun nombreHabitat(idPartida: IdPartida, codigo: String): String?
}
