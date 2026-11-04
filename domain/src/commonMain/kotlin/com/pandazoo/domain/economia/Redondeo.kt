package com.pandazoo.domain.economia

/** Reglas de redondeo del juego: al más cercano con los medios hacia arriba (RF-10, RF-15). */
object Redondeo {
    /** Redondea un importe de configuración al centésimo, medios hacia arriba. */
    fun dinero(valor: Double): Dinero = Dinero.desdeDecimal(valor)

    /** Porcentaje entero mostrado para un valor `0..1`, medios hacia arriba (0.495 da 50). */
    fun porcentaje(valor: Double): Int = Decimales.redondearEscalado(valor, 2).toInt()

    /** Entero más cercano con los medios hacia arriba; se usa para el umbral de animales de prestigio. */
    fun entero(valor: Double): Int = Decimales.redondearEscalado(valor, 0).toInt()
}
