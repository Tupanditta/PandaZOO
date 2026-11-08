package com.pandazoo.application.caso

import com.pandazoo.domain.config.ConfiguracionJuego
import com.pandazoo.domain.satisfaccion.ReglaSatisfaccion

/** Cómo encajan dos especies, o una especie y un hábitat, en tres niveles; la guía nunca expone el valor numérico. */
enum class NivelEncaje { BIEN, REGULAR, MAL }

data class EspecieGuia(val id: String, val nombre: String, val idRango: Int)

data class HabitatGuia(val idBioma: String, val nombre: String)

data class RelacionGuia(val especie: EspecieGuia, val nivel: NivelEncaje)

/**
 * Guía de convivencia: consulta de catálogo, independiente de la partida. Reutiliza la afinidad entre especies y la
 * compatibilidad especie-hábitat de [ReglaSatisfaccion] y las traduce a [NivelEncaje]. Solo lectura.
 */
class ConsultarGuiaConvivencia(cfg: ConfiguracionJuego, private val regla: ReglaSatisfaccion) {
    /** Todas las especies, también las aún bloqueadas, por rango y nombre. */
    val especies: List<EspecieGuia> =
        cfg.especies.map { EspecieGuia(it.id, it.nombre, it.idRango) }.sortedWith(compareBy({ it.idRango }, { it.nombre }))

    val habitats: List<HabitatGuia> = cfg.catalogos.biomas.map { HabitatGuia(it.id, it.nombre) }

    /** Encaje de la especie en cada hábitat, en el orden de [habitats]. */
    fun encajesHabitat(idEspecie: String): List<NivelEncaje> =
        habitats.map { nivelHabitat(regla.pHab(idEspecie, it.idBioma)) }

    /** Relación de la especie con todas las demás (no consigo misma), en el orden de [especies]. */
    fun relaciones(idEspecie: String): List<RelacionGuia> =
        especies.filter { it.id != idEspecie }.map { RelacionGuia(it, nivelAfinidad(regla.afinidad(idEspecie, it.id))) }

    companion object {
        const val UMBRAL_AFINIDAD_BIEN = 0.6
        const val UMBRAL_AFINIDAD_REGULAR = 0.35
        const val UMBRAL_HABITAT_BIEN = 0.75
        const val UMBRAL_HABITAT_REGULAR = 0.4

        fun nivelAfinidad(valor: Double): NivelEncaje = nivel(valor, UMBRAL_AFINIDAD_BIEN, UMBRAL_AFINIDAD_REGULAR)

        fun nivelHabitat(valor: Double): NivelEncaje = nivel(valor, UMBRAL_HABITAT_BIEN, UMBRAL_HABITAT_REGULAR)

        private fun nivel(valor: Double, bien: Double, regular: Double) =
            when {
                valor >= bien -> NivelEncaje.BIEN
                valor >= regular -> NivelEncaje.REGULAR
                else -> NivelEncaje.MAL
            }
    }
}
