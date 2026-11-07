package com.pandazoo.domain.ciclo

/** Ciclos vencidos tras una ausencia y remanente con el que se reanuda el temporizador. */
data class PlanDiferido(val ciclos: Int, val descartados: Int, val remanenteSegundos: Int)

/**
 * Reglas puras de la simulación en diferido (CU-12, RF-21 a RF-24). Son las del ciclo ordinario con los límites
 * de una sesión diferida; «inicial» es el estado del animal al empezar la sesión (antes del primer ciclo diferido).
 */
object ReglasDiferido {
    /** Alimentación más baja que puede alcanzar un animal que empieza la sesión con 60 % o más (RF-22). */
    const val PISO_DESDE_TRAMO_ALTO = 40

    /** Ciclos de enfermedad adicionales que acumula en una sesión un animal que ya estaba enfermo (RF-23). */
    const val ENFERMEDAD_ADICIONAL_PREVIA = 2

    /** Ciclos de enfermedad que computa en una sesión un animal que enferma durante ella (RF-23). */
    const val ENFERMEDAD_ADICIONAL_NUEVA = 1

    /**
     * Cálculo de CU-12, paso 2. `transcurridos` son los segundos ausentes (`E >= 0`) y `remanente` el `R` persistido.
     * Si `E < R` no vence ningún ciclo. Si no, vencen `1 + floor((E − R) / 600)`, limitados a `maxCiclos`, y el
     * nuevo remanente es `600 − ((E − R) mod 600)` (un resto 0 vale 600).
     */
    fun planificar(transcurridos: Long, remanente: Int, maxCiclos: Int): PlanDiferido {
        require(transcurridos >= 0) { "El tiempo ausente no puede ser negativo" }
        require(maxCiclos >= 0) { "El máximo de ciclos no puede ser negativo" }
        if (transcurridos < remanente) return PlanDiferido(0, 0, (remanente - transcurridos).toInt())
        val exceso = transcurridos - remanente
        val duracion = ReglasCiclo.DURACION_CICLO_SEGUNDOS
        val vencidos = 1 + exceso / duracion
        val procesados = minOf(vencidos, maxCiclos.toLong())
        return PlanDiferido(procesados.toInt(), (vencidos - procesados).toInt(), (duracion - exceso % duracion).toInt())
    }

    /** Suelo de alimentación de la sesión: `[100, 80, 60] → 40` y `[40, 20, 0] → 0`. */
    fun pisoAlimentacion(inicial: Int): Int = if (inicial >= 60) PISO_DESDE_TRAMO_ALTO else 0

    /** Alimentación tras un ciclo diferido: baja 20 puntos sin pasar del suelo de la sesión. */
    fun alimentacionTrasCiclo(nivel: Int, inicial: Int): Int =
        maxOf(ReglasCiclo.alimentacionTrasCiclo(nivel), pisoAlimentacion(inicial))

    /**
     * Contador de desnutrición: en una sesión se computa a lo sumo un ciclo mortal. Si ya subió respecto al inicial
     * no vuelve a subir; un nivel distinto de 0 lo reinicia.
     */
    fun contadorDesnutricion(actual: Int, inicial: Int, alimentacion: Int): Int = when {
        alimentacion != 0 -> 0
        actual > inicial -> actual
        else -> actual + 1
    }

    /** Contador de enfermedad: un ciclo si enferma en la sesión, como máximo dos adicionales si ya estaba enfermo. */
    fun contadorEnfermedad(actual: Int, inicial: Int, enfermo: Boolean, enfermoInicial: Boolean): Int {
        if (!enfermo) return 0
        val tope = if (enfermoInicial) ENFERMEDAD_ADICIONAL_PREVIA else ENFERMEDAD_ADICIONAL_NUEVA
        return if (actual - inicial < tope) actual + 1 else actual
    }
}
