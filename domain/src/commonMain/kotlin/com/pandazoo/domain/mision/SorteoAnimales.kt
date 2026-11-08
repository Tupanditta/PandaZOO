package com.pandazoo.domain.mision

import com.pandazoo.domain.config.ConfiguracionJuego
import com.pandazoo.domain.config.ProbabilidadRango
import com.pandazoo.domain.config.SorteoPrestigioCfg

/**
 * Sorteo de animales por rango: primero el rango según una tabla de probabilidades y después la especie dentro del rango
 * (uniforme; el rango exclusivo usa los pesos de `recompensas_animales_aleatorios`). Reglas puras: el azar es un argumento.
 *
 * @property porRango especies elegibles de cada rango con su peso relativo.
 */
class SorteoAnimales(private val porRango: Map<Int, List<EspecieElegible>>, private val prestigio: SorteoPrestigioCfg) {
    private val rangos: Map<String, Int> = porRango.flatMap { (r, l) -> l.map { it.idEspecie to r } }.toMap()

    /** Rango de una especie sorteable, o `null` si no lo es. */
    fun rangoDe(idEspecie: String): Int? = rangos[idEspecie]

    /** Tabla de rango de la recompensa al alcanzar [prestigioRequerido]: múltiplos de 10, de 5 y resto. */
    fun tablaPrestigio(prestigioRequerido: Int): List<ProbabilidadRango> = when {
        prestigioRequerido > 0 && prestigioRequerido % 10 == 0 -> prestigio.multiplo10
        prestigioRequerido > 0 && prestigioRequerido % 5 == 0 -> prestigio.multiplo5
        else -> prestigio.base
    }

    /**
     * @param aleatorioRango en `[0,1)`: elige el rango. @param aleatorioEspecie en `[0,1)`: elige la especie del rango.
     * @param excluir especies que no pueden salir (si el rango se queda sin ninguna, se ignora la exclusión).
     * Devuelve `null` si la tabla no suma 100 o el rango no tiene especies.
     */
    fun sortear(
        tabla: List<ProbabilidadRango>,
        aleatorioRango: Double,
        aleatorioEspecie: Double,
        excluir: Set<String> = emptySet(),
    ): String? {
        if (tabla.sumOf { it.porcentaje } != 100) return null
        val punto = aleatorioRango * 100.0
        var acumulado = 0
        val rango = tabla.firstOrNull { acumulado += it.porcentaje; punto < acumulado }?.idRango ?: tabla.last().idRango
        val todas = porRango[rango].orEmpty()
        val candidatas = todas.filter { it.idEspecie !in excluir }.ifEmpty { todas }
        val total = candidatas.sumOf { it.probabilidad }
        if (total <= 0) return null
        val objetivo = aleatorioEspecie * total
        var suma = 0
        for (c in candidatas) {
            suma += c.probabilidad
            if (objetivo < suma) return c.idEspecie
        }
        return candidatas.last().idEspecie
    }

    companion object {
        /** Especies comerciales de los rangos 2 a 4 (peso uniforme) y exclusivas con su lista de recompensas. */
        fun desde(cfg: ConfiguracionJuego): SorteoAnimales {
            val mapa = HashMap<Int, List<EspecieElegible>>()
            for (r in 2..4) {
                mapa[r] = cfg.especies.filter { it.categoria == "COMERCIAL" && it.idRango == r }.map { EspecieElegible(it.id, 1) }
            }
            mapa[5] = cfg.recompensasAnimales.especies.map { EspecieElegible(it.idEspecie, it.probabilidad) }
            return SorteoAnimales(mapa, cfg.recompensasAnimales.sorteoPrestigio)
        }
    }
}
