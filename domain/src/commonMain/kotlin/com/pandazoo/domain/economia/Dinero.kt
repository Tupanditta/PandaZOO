package com.pandazoo.domain.economia

import kotlin.jvm.JvmInline
import kotlin.math.abs
import kotlin.math.round

/**
 * Importe monetario como entero de céntimos. Sustituye a `BigDecimal` con escala 2: el resultado es exacto y
 * idéntico en todas las plataformas (RES-14, RNF-15). La unidad monetaria del juego es una moneda dorada.
 */
@JvmInline
value class Dinero private constructor(val centimos: Long) : Comparable<Dinero> {
    operator fun plus(otro: Dinero) = Dinero(centimos + otro.centimos)

    operator fun minus(otro: Dinero) = Dinero(centimos - otro.centimos)

    /** `cantidad * este importe`, exacto en céntimos. */
    operator fun times(cantidad: Int) = Dinero(centimos * cantidad)

    override fun compareTo(other: Dinero): Int = centimos.compareTo(other.centimos)

    /** Valor `REAL` con dos decimales para persistir; exacto al leerlo de vuelta con [desdeBd]. */
    fun paraBd(): Double = centimos / 100.0

    /** Texto con exactamente dos decimales, por ejemplo `1000.00`. */
    override fun toString(): String {
        val absoluto = abs(centimos)
        val signo = if (centimos < 0) "-" else ""
        return "$signo${absoluto / 100}.${(absoluto % 100).toString().padStart(2, '0')}"
    }

    companion object {
        val CERO = Dinero(0)

        fun deCentimos(centimos: Long) = Dinero(centimos)

        /** Importe de un valor decimal de configuración: redondeo al centésimo, medios hacia arriba (RF-10). */
        fun desdeDecimal(valor: Double) = Dinero(Decimales.redondearEscalado(valor, 2))

        /** Lee un `REAL` ya guardado con dos decimales. */
        fun desdeBd(valor: Double) = Dinero(round(valor * 100).toLong())

        /** `cantidad * precioUnitario` (RF-10). */
        fun coste(cantidad: Int, precioUnitario: Dinero): Dinero = precioUnitario * cantidad
    }
}
