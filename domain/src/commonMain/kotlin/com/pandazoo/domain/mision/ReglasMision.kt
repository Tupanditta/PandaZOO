package com.pandazoo.domain.mision

import com.pandazoo.domain.economia.Decimales

/** Operación y constante de progresión de un parámetro numérico, con su rango admitido (`EspecificacionMisiones.md`, 4.1). */
data class Progresion(val operacion: String, val constante: Double, val minimo: Double, val maximo: Double)

/** Resultado de progresar un parámetro al reclamar una misión. */
sealed interface ResultadoProgresion {
    data class Valor(val valor: Double) : ResultadoProgresion

    /** El resultado queda fuera de `[minimo, maximo]`: el tipo se agota y no vuelve a generarse (4.1, «Agotamiento»). */
    data object Agotada : ResultadoProgresion

    /** Falta la operación, la constante no es válida o `DIVIDIR` con constante no positiva (CU-15, 4.c). */
    data object Invalida : ResultadoProgresion
}

/** Especie elegible de la recompensa de animal aleatorio, con su probabilidad porcentual. */
data class EspecieElegible(val idEspecie: String, val probabilidad: Int)

/**
 * Reglas puras de misiones y recompensas (RF-39, RF-40, RF-38). No dependen de reloj, azar ni base de datos.
 */
object ReglasMision {
    /** Parámetro de satisfacción: se redondea al centésimo; los demás numéricos son enteros. */
    const val PARAMETRO_SATISFACCION = "satisfaccion_minima"

    private const val ESCALA = 6

    /**
     * Nombre del parámetro que fija el objetivo de cada tipo (`condicion_cumplimiento`: `progreso >= objetivo`).
     * `null` significa un objetivo fijo de `1` (las misiones de venta).
     */
    private val OBJETIVO: Map<String, String?> = mapOf(
        "comprar_especie" to "cantidad_requerida",
        "crecer_especie" to "cantidad_animales",
        "total_animales" to "numero_animales",
        "animales_habitat" to "numero_animales",
        "espacios_ocupados_habitat" to "numero_espacios_requeridos",
        "subir_prestigio" to "nivel_prestigio_requerido",
        "vender_especie_edad" to null,
        "vender_edad" to null,
        "familia" to "numero_minimo_hermanos",
        "familia_especie" to "numero_minimo_hermanos",
        "satisfaccion_minima" to "satisfaccion_minima",
        "satisfaccion_minima_especie" to "satisfaccion_minima",
    )

    /** Tipos de misión conocidos. */
    val tipos: Set<String> get() = OBJETIVO.keys

    /** Objetivo numérico de la instancia, o `null` si el tipo no se conoce o falta el parámetro. */
    fun objetivo(idMision: String, numericos: Map<String, Double>): Double? {
        if (idMision !in OBJETIVO) return null
        val nombre = OBJETIVO.getValue(idMision) ?: return 1.0
        return numericos[nombre]
    }

    fun cumplida(progreso: Double, objetivo: Double): Boolean = progreso >= objetivo

    /**
     * Aplica la operación y la constante en aritmética decimal exacta (`SUMAR` y `RESTAR` a seis decimales), redondea al
     * entero más cercano (satisfacción: al centésimo) con los medios hacia arriba y comprueba el rango.
     */
    fun progresar(nombre: String, valor: Double, p: Progresion): ResultadoProgresion {
        val a = Decimales.redondearEscalado(valor, ESCALA)
        val c = Decimales.redondearEscalado(p.constante, ESCALA)
        val bruto = when (p.operacion) {
            "SUMAR" -> (a + c) / 1_000_000.0
            "RESTAR" -> (a - c) / 1_000_000.0
            "MULTIPLICAR" -> valor * p.constante
            "DIVIDIR" -> if (p.constante > 0.0) valor / p.constante else return ResultadoProgresion.Invalida
            else -> return ResultadoProgresion.Invalida
        }
        if (!bruto.isFinite()) return ResultadoProgresion.Invalida
        val redondeado = if (nombre == PARAMETRO_SATISFACCION) {
            Decimales.redondearEscalado(bruto, 2) / 100.0
        } else {
            Decimales.redondearEscalado(bruto, 0).toDouble()
        }
        return if (redondeado < p.minimo || redondeado > p.maximo) ResultadoProgresion.Agotada else ResultadoProgresion.Valor(redondeado)
    }

    /**
     * Sorteo ponderado de la especie de la recompensa. `aleatorio` está en `[0,1)`; cada especie ocupa un tramo de
     * `probabilidad %`. Devuelve `null` si la lista no suma exactamente 100 o está vacía.
     */
    fun sortearEspecie(aleatorio: Double, lista: List<EspecieElegible>): String? {
        if (lista.isEmpty() || lista.sumOf { it.probabilidad } != 100) return null
        val punto = aleatorio * 100.0
        var acumulado = 0
        for (e in lista) {
            acumulado += e.probabilidad
            if (punto < acumulado) return e.idEspecie
        }
        return lista.last().idEspecie
    }

    /** `…/N` pasa a `…/N+1`; las instancias se numeran por tipo dentro de la partida. */
    fun siguienteInstancia(idInstancia: String): String {
        val corte = idInstancia.lastIndexOf('/')
        val n = idInstancia.substring(corte + 1).toIntOrNull() ?: 0
        return idInstancia.substring(0, corte + 1) + (n + 1)
    }

    /** ¿La misión de crecimiento cuenta a un animal que pasó de `antes` a `despues` años completos? */
    fun cruzaEdad(antesAnios: Int, despuesAnios: Int, requerida: Double): Boolean =
        antesAnios < requerida && despuesAnios >= requerida

    /** ¿La venta de un animal de `edadAnios` cumple la edad de una misión de venta? */
    fun ventaCumple(edadAnios: Int, requerida: Double): Boolean = edadAnios >= requerida
}
