package com.pandazoo.domain.prestigio

import com.pandazoo.domain.config.DesbloqueoNivelCfg
import com.pandazoo.domain.config.NivelPrestigioCfg
import com.pandazoo.domain.config.PrestigioReglasCfg
import com.pandazoo.domain.economia.Redondeo

/** Terna de máximos históricos independientes (RF-36): cada componente puede haberse logrado en un momento distinto. */
data class MaximosHistoricos(val nivel: Int, val animalesVivos: Int, val satisfaccionGlobal: Double)

/** Estado de prestigio persistido: nivel actual y máximos; `maximos == null` en una partida sin máximos registrados. */
data class EstadoPrestigio(val actual: Int, val maximos: MaximosHistoricos?)

/** Qué especies y hábitats (por código de catálogo) abre un nivel. */
data class DesbloqueoNivel(val nivel: Int, val especies: List<String>, val habitats: List<String>)

/**
 * Reglas puras de prestigio (RF-35, RF-36, RF-43; `EspecificacionBBDD.md` 4.4). Usan la tabla de umbrales ya
 * expandida de `valores_y_pesos.json`; [generarTabla] reproduce esa expansión para verificarla.
 */
class ReglasPrestigio(
    niveles: List<NivelPrestigioCfg>,
    desbloqueos: List<DesbloqueoNivelCfg>,
) {
    private val niveles: List<NivelPrestigioCfg> = niveles.sortedBy { it.nivel }
    private val desbloqueos: List<DesbloqueoNivel> = desbloqueos.sortedBy { it.nivel }.map { DesbloqueoNivel(it.nivel, it.especies, it.habitats) }

    /** Mayor nivel cuyos dos umbrales se cumplen a la vez; `0` si ninguno se cumple. */
    fun nivelActual(animalesVivos: Int, satisfaccionGlobal: Double): Int =
        niveles.lastOrNull { animalesVivos >= it.umbralAnimalesComparacion && satisfaccionGlobal >= it.umbralSatisfaccion }?.nivel ?: 0

    /**
     * Cada máximo sube cuando el valor actual lo supera. La terna se inicializa la primera vez que algún valor es
     * positivo; una partida sin animales ni nivel no tiene máximos que registrar.
     */
    fun actualizarMaximos(previos: MaximosHistoricos?, nivel: Int, animalesVivos: Int, satisfaccionGlobal: Double): MaximosHistoricos? {
        if (previos == null) {
            return if (nivel > 0 || animalesVivos > 0 || satisfaccionGlobal > 0.0) MaximosHistoricos(nivel, animalesVivos, satisfaccionGlobal) else null
        }
        return MaximosHistoricos(
            nivel = maxOf(previos.nivel, nivel),
            animalesVivos = maxOf(previos.animalesVivos, animalesVivos),
            satisfaccionGlobal = maxOf(previos.satisfaccionGlobal, satisfaccionGlobal),
        )
    }

    /** Umbrales del nivel siguiente al [actual], o `null` si ya es el máximo configurado. */
    fun siguiente(actual: Int): NivelPrestigioCfg? = niveles.firstOrNull { it.nivel == actual + 1 }

    /** Se avisa del ascenso solo si el nivel supera el máximo histórico previo (RF-43). */
    fun avisaAscenso(maximoPrevio: Int?, nuevoNivel: Int): Boolean = nuevoNivel > 0 && nuevoNivel > (maximoPrevio ?: 0)

    /** Lo que abre exactamente el nivel [nivel], o `null` si no abre nada. */
    fun desbloqueoDe(nivel: Int): DesbloqueoNivel? = desbloqueos.firstOrNull { it.nivel == nivel }

    /** Desbloqueos de todos los niveles menores o iguales que [nivel], también tras un salto de varios niveles. */
    fun desbloqueosHasta(nivel: Int): List<DesbloqueoNivel> = desbloqueos.filter { it.nivel <= nivel }

    companion object {
        /** Expansión de la fórmula: cada umbral sin redondear es el anterior por su multiplicador (RF-35). */
        fun generarTabla(reglas: PrestigioReglasCfg): List<NivelPrestigioCfg> {
            var animales = reglas.umbralesNivel1.animalesVivos.toDouble()
            var satisfaccion = reglas.umbralesNivel1.satisfaccionGlobal
            return (1..reglas.nivelMaximo).map { n ->
                if (n > 1) {
                    animales *= reglas.multiplicadores.animalesVivos
                    satisfaccion *= reglas.multiplicadores.satisfaccionGlobal
                }
                NivelPrestigioCfg(n, animales, Redondeo.entero(animales), satisfaccion)
            }
        }
    }
}
