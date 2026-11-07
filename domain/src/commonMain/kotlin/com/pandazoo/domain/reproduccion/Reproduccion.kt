package com.pandazoo.domain.reproduccion

/**
 * Reglas puras de la reproducción (RF-16, RF-17, RF-18; `EspecificacionBBDD.md` 2.3.2 y regla 5.3 de `sprints.md`).
 * No dependen de reloj, azar ni base de datos.
 */
object ReglasReproduccion {
    /** Ciclos durante los que una concepción puede aceptarse a partir del ciclo siguiente al de la concepción. */
    const val VENTANA_CICLOS = 3

    /** Ambos progenitores deben llegar al umbral del rango con la satisfacción sin redondear (`>=`). */
    fun cumpleUmbral(satisfaccionA: Double, satisfaccionB: Double, umbral: Double): Boolean =
        satisfaccionA >= umbral && satisfaccionB >= umbral

    /** `max(0, p_inicial − penalización × hijos vivos comunes)`. */
    fun probabilidad(base: Double, penalizacionPorHijo: Double, hijosVivos: Int): Double =
        maxOf(0.0, base - penalizacionPorHijo * hijosVivos)

    /** Se concibe si `aleatorio < probabilidad`: con `0` nunca y con `1` siempre. */
    fun concibe(aleatorio: Double, probabilidad: Double): Boolean = aleatorio < probabilidad

    /** Ciclo en el que la concepción pasa a estar disponible (el siguiente al de la concepción). */
    fun cicloDisponible(cicloConcepcion: Int): Int = cicloConcepcion + 1

    /** Último ciclo de la ventana: disponible durante tres ciclos. */
    fun cicloExpiracion(cicloConcepcion: Int): Int = cicloDisponible(cicloConcepcion) + VENTANA_CICLOS - 1

    /**
     * Una concepción sigue vigente mientras el último ciclo confirmado no supere su `ciclo_expiracion`
     * (expira al confirmarse un ciclo con número mayor).
     */
    fun vigente(ultimoCicloConfirmado: Int, cicloExpiracion: Int): Boolean = ultimoCicloConfirmado <= cicloExpiracion

    /** Padre y madre se asignan por identificador: el menor es `id_padre` y el mayor `id_madre` (CU-10). */
    fun progenitores(idA: String, idB: String): Pair<String, String> = if (idA <= idB) idA to idB else idB to idA
}
