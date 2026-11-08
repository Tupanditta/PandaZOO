package com.pandazoo.domain.animal

import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.economia.Redondeo

/** Progreso de nivel de un animal: nivel actual y ciclos sanos acumulados hacia el siguiente (RF-51). */
data class ProgresoNivel(val nivel: Int, val ciclosNivel: Int)

/** Valores de un rango que intervienen en nivel, beneficio y venta (`tablas.rangos_especie`). */
data class ParametrosNivelRango(
    val beneficioBase: Double,
    val nivelMaximo: Int,
    val multiplicadorNivelMaximo: Double,
    val valorVentaBase: Dinero,
    val factorVentaNivelMaximo: Double,
)

/**
 * Reglas puras de nivel, beneficio por animal y venta (RF-15, RF-51, RF-52). Los valores son los de
 * `reglas_de_calculo.nivel` y `reglas_de_calculo.venta` y los de `tablas.rangos_especie`.
 */
object ReglasNivel {
    const val NIVEL_INICIAL = 1
    const val CICLOS_POR_NIVEL = 72
    /** Sin mínimo práctico: cualquier animal vivo puede tener pareja (el nivel inicial es 1). */
    const val NIVEL_MINIMO_PAREJA = 1
    const val FRACCION_EQUILIBRIO = 0.6667
    const val FACTOR_VENTA_NIVEL_1 = 0.05

    /**
     * Etapa de nivel de un animal que sigue vivo: un ciclo sano suma 1 a `ciclosNivel`; al llegar a `ciclosPorNivel` sube
     * un nivel y vuelve a 0, sin pasar del tope (donde `ciclosNivel` queda en 0). Un ciclo no sano no suma ni resta.
     */
    fun tras(actual: ProgresoNivel, sano: Boolean, nivelMaximo: Int, ciclosPorNivel: Int = CICLOS_POR_NIVEL): ProgresoNivel {
        if (actual.nivel >= nivelMaximo) return ProgresoNivel(nivelMaximo, 0)
        if (!sano) return actual
        val ciclos = actual.ciclosNivel + 1
        if (ciclos < ciclosPorNivel) return ProgresoNivel(actual.nivel, ciclos)
        val nivel = actual.nivel + 1
        return ProgresoNivel(nivel, 0)
    }

    /** Sano a efectos de nivel: alimentación mayor que 0 y ambos contadores críticos a 0. */
    fun cicloSano(alimentacion: Int, ciclosDesnutricion: Int, ciclosEnfermedad: Int): Boolean =
        alimentacion > 0 && ciclosDesnutricion == 0 && ciclosEnfermedad == 0

    /** `M_nivel(n) = 1 + (mult_max − 1) · (n − 1) / (nivel_max − 1)`. */
    fun multiplicador(nivel: Int, nivelMaximo: Int, multiplicadorMaximo: Double): Double =
        1.0 + (multiplicadorMaximo - 1.0) * (nivel - 1).toDouble() / (nivelMaximo - 1).toDouble()

    /** `b_i = beneficio_base · S_i · M_nivel(n)`, sin redondear. */
    fun beneficioAnimal(p: ParametrosNivelRango, satisfaccion: Double, nivel: Int): Double =
        p.beneficioBase * satisfaccion * multiplicador(nivel, p.nivelMaximo, p.multiplicadorNivelMaximo)

    /** `B_ciclo = redondear(Σ b_i)`, al centésimo con los medios hacia arriba. */
    fun beneficioCiclo(aportaciones: List<Double>): Dinero =
        if (aportaciones.isEmpty()) Dinero.CERO else Redondeo.dinero(aportaciones.sum())

    /** `n_eq = redondear_medio_arriba(fraccion_equilibrio · nivel_maximo)`. */
    fun nivelEquilibrio(nivelMaximo: Int, fraccion: Double = FRACCION_EQUILIBRIO): Int = Redondeo.entero(fraccion * nivelMaximo)

    /** `F_venta(n)`, lineal a tramos: `f1` en el nivel 1, `1` en `n_eq` y `factorMaximo` en el tope. */
    fun factorVenta(
        nivel: Int,
        nivelMaximo: Int,
        factorMaximo: Double,
        fraccion: Double = FRACCION_EQUILIBRIO,
        factorNivel1: Double = FACTOR_VENTA_NIVEL_1,
    ): Double {
        val eq = nivelEquilibrio(nivelMaximo, fraccion)
        val n = nivel.coerceIn(1, nivelMaximo)
        // Los tres puntos definidos se devuelven exactos, sin error de coma flotante.
        if (n == 1) return factorNivel1
        if (n == eq) return 1.0
        if (n == nivelMaximo) return factorMaximo
        return if (n <= eq) {
            factorNivel1 + (1.0 - factorNivel1) * (n - 1).toDouble() / (eq - 1).toDouble()
        } else {
            1.0 + (factorMaximo - 1.0) * (n - eq).toDouble() / (nivelMaximo - eq).toDouble()
        }
    }

    /** `V = redondear(valor_venta_base · F_venta(n))`. */
    fun valorVenta(
        p: ParametrosNivelRango,
        nivel: Int,
        fraccion: Double = FRACCION_EQUILIBRIO,
        factorNivel1: Double = FACTOR_VENTA_NIVEL_1,
    ): Dinero {
        val f = factorVenta(nivel, p.nivelMaximo, p.factorVentaNivelMaximo, fraccion, factorNivel1)
        return Redondeo.dinero(p.valorVentaBase.paraBd() * f)
    }
}
