package com.pandazoo.domain.ciclo

import com.pandazoo.domain.animal.Edad
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.economia.Redondeo

/** Causa de una defunción automática (RF-14, RF-19). */
enum class CausaMuerte { LONGEVIDAD, CICLOS_CRITICOS }

/**
 * Reglas puras de un ciclo de simulación (RF-14, `EspecificacionBBDD.md` 1.4 y 2.3.1). Los valores son los de
 * `reglas_de_calculo` de `valores_y_pesos.json`; no dependen de reloj, azar ni base de datos.
 */
object ReglasCiclo {
    const val DURACION_CICLO_SEGUNDOS = 600
    const val DIAS_POR_CICLO = 6
    const val DESCENSO_ALIMENTACION = 20
    const val LIMITE_CICLOS_CRITICOS = 3

    /** Edad tras un ciclo, en días totales del calendario 30×12. */
    fun edadTrasCiclo(edadDias: Int): Int = edadDias + DIAS_POR_CICLO

    fun edadTrasCiclo(edad: Edad): Edad = Edad.deDias(edadTrasCiclo(edad.totalDias))

    /** Alimentación tras un ciclo ordinario: baja 20 puntos sin bajar de 0. */
    fun alimentacionTrasCiclo(nivel: Int): Int = (nivel - DESCENSO_ALIMENTACION).coerceAtLeast(0)

    /** Enferma si `aleatorio < probabilidad · (1 − satisfacción)`; la igualdad no enferma. */
    fun enferma(aleatorio: Double, probabilidadRango: Double, satisfaccion: Double): Boolean =
        aleatorio < probabilidadRango * (1.0 - satisfaccion)

    /** Un ciclo que termina con alimentación 0 suma 1; en otro caso el contador vuelve a 0. */
    fun contadorDesnutricion(anterior: Int, alimentacion: Int): Int = if (alimentacion == 0) anterior + 1 else 0

    /** Un ciclo que termina enfermo suma 1; en otro caso el contador vuelve a 0. */
    fun contadorEnfermedad(anterior: Int, enfermo: Boolean): Int = if (enfermo) anterior + 1 else 0

    /** Con ambas condiciones se usa el mayor contador (RF-14). */
    fun contadorCritico(desnutricion: Int, enfermedad: Int): Int = maxOf(desnutricion, enfermedad)

    /** Ciclos que quedan antes de la defunción: `3 − max(contadores)`, nunca negativo. */
    fun ciclosRestantes(desnutricion: Int, enfermedad: Int): Int =
        (LIMITE_CICLOS_CRITICOS - contadorCritico(desnutricion, enfermedad)).coerceAtLeast(0)

    /** La longevidad se comprueba con la edad ya incrementada; `>=` la edad máxima total en días. */
    fun causaDeMuerte(edadDias: Int, edadMaximaDias: Int, desnutricion: Int, enfermedad: Int): CausaMuerte? =
        when {
            edadDias >= edadMaximaDias -> CausaMuerte.LONGEVIDAD
            contadorCritico(desnutricion, enfermedad) >= LIMITE_CICLOS_CRITICOS -> CausaMuerte.CICLOS_CRITICOS
            else -> null
        }

    /**
     * `B = redondear(S_global · Σ beneficio_por_animal(rango))`, al centésimo con los medios hacia arriba.
     * Sin animales vivos alojados el beneficio es 0.
     */
    fun beneficio(satisfaccionGlobal: Double, importesPorAnimal: List<Dinero>): Dinero {
        if (importesPorAnimal.isEmpty()) return Dinero.CERO
        val suma = importesPorAnimal.sumOf { it.centimos } / 100.0
        return Redondeo.dinero(satisfaccionGlobal * suma)
    }
}
