package com.pandazoo.application.caso

import com.pandazoo.application.puerto.MonitorRepositorio
import com.pandazoo.application.puerto.Reloj
import com.pandazoo.application.puerto.SatisfaccionRepositorio
import com.pandazoo.application.puerto.Transaccion
import com.pandazoo.domain.modelo.IdPartida
import com.pandazoo.domain.modelo.Seccion
import com.pandazoo.domain.satisfaccion.IndiceVivos
import com.pandazoo.domain.satisfaccion.Medias
import com.pandazoo.domain.satisfaccion.ReglaSatisfaccion
import com.pandazoo.domain.satisfaccion.ValorAlojado

/** Qué animales se recalculan. Las medias de todos los niveles se actualizan siempre a partir de lo persistido. */
sealed interface AlcanceRecalculo {
    /** Todos los animales vivos alojados: fin de ciclo periódico u offline, o carga inicial de datos. */
    data object Parque : AlcanceRecalculo

    /**
     * Los animales vivos de esos espacios: la mutación confirmada afectó a esos espacios. Quien muta debe incluir
     * todos los espacios afectados (por ejemplo origen y destino de un traslado, o el espacio de un familiar).
     */
    data class Espacios(val ids: Set<String>) : AlcanceRecalculo
}

/**
 * Orquestador de recálculo de satisfacción (RF-15, RF-44, RES-10). Solo lo invocan los casos de uso tras una
 * mutación o un ciclo: ninguna consulta, pantalla o temporizador lo llama. `invocaciones` permite a los tests
 * comprobar que la navegación y la lectura no lo ejecutan.
 *
 * Se ejecuta dentro de la transacción de quien lo llama (la transacción anidada se une a la externa), de modo
 * que la mutación, la satisfacción individual y las métricas se confirman o se revierten juntas.
 */
class RecalcularSatisfaccion(
    private val repositorio: SatisfaccionRepositorio,
    private val regla: ReglaSatisfaccion,
    private val transaccion: Transaccion,
    private val reloj: Reloj,
    /** Prestigio: se evalúa en el mismo evento y la misma transacción, justo después de persistir las métricas (RF-35). */
    private val prestigio: RecalcularPrestigio? = null,
) {
    var invocaciones: Int = 0
        private set

    /** [numeroCiclo] lo informa el ciclo en curso; las mutaciones lo dejan vacío. */
    fun alConsolidar(idPartida: IdPartida, alcance: AlcanceRecalculo = AlcanceRecalculo.Parque, numeroCiclo: Int? = null) {
        invocaciones++
        transaccion.ejecutar {
            val espacios = repositorio.espacios(idPartida).associateBy { it.id }
            val vivos = repositorio.animalesVivosAlojados(idPartida)
            val indice = IndiceVivos(vivos)
            val porEspacio = vivos.groupBy { it.idEspacio }

            val objetivos = when (alcance) {
                AlcanceRecalculo.Parque -> vivos
                is AlcanceRecalculo.Espacios -> vivos.filter { it.idEspacio in alcance.ids }
            }
            val nuevos = HashMap<String, Double>(objetivos.size)
            for (a in objetivos) {
                val espacio = espacios[a.idEspacio] ?: error("Animal alojado en un espacio inexistente")
                nuevos[a.id] = regla.calcular(a, espacio.idBioma, porEspacio.getValue(a.idEspacio), indice).valor
            }
            repositorio.guardarSatisfaccionAnimales(idPartida, nuevos)

            val guardados = if (nuevos.size == vivos.size) nuevos else repositorio.satisfaccionGuardada(idPartida) + nuevos
            val valores = vivos.map { a ->
                ValorAlojado(a.idEspacio, espacios.getValue(a.idEspacio).idHabitat, guardados.getValue(a.id))
            }
            val medias = Medias.calcular(valores)
            repositorio.reemplazarMetricas(idPartida, medias, reloj.ahoraSegundos())
            prestigio?.alConsolidar(idPartida, numeroCiclo)
        }
    }
}

/** Satisfacción individual de un animal vivo en el monitor. */
data class AnimalMonitor(val nombre: String, val especie: String, val valor: Double, val edadAnios: Int = 0)

data class EspacioMonitor(
    val nombre: String,
    /** `Disponible(null)` significa sin animales vivos (sin métrica); `NoDisponible` es un fallo de lectura. */
    val valor: Seccion<Double?>,
    val animales: Seccion<List<AnimalMonitor>>,
)

data class HabitatMonitor(
    val nombre: String,
    val valor: Seccion<Double?>,
    val espacios: Seccion<List<EspacioMonitor>>,
)

data class MonitorSatisfaccion(
    /** `NoDisponible` si no hay métrica persistida; nunca se presenta como cero (CU-17, 2.a). */
    val parque: Seccion<Double>,
    val habitats: Seccion<List<HabitatMonitor>>,
)

/**
 * CU-17 Consultar monitor de satisfacción (RF-27). Lee valores ya persistidos: no recalcula, no abre
 * transacción y no audita (RF-47). Un fallo en un nivel se aísla y el resto se conserva (CU-17, 2.b).
 */
class ConsultarMonitorSatisfaccion(private val repositorio: MonitorRepositorio) {
    fun ejecutar(idPartida: IdPartida): MonitorSatisfaccion {
        if (!seguro { repositorio.existePartida(idPartida) }) {
            val sin = Seccion.NoDisponible("No hay una partida de la que consultar la satisfacción.")
            return MonitorSatisfaccion(sin, sin)
        }
        val parque: Seccion<Double> = try {
            repositorio.satisfaccionParque(idPartida)?.let { Seccion.Disponible(it) }
                ?: Seccion.NoDisponible("La satisfacción global no está disponible.")
        } catch (e: Exception) {
            Seccion.NoDisponible("No se pudo consultar la satisfacción global.")
        }

        val habitatsMetricas = lectura { repositorio.satisfaccionHabitats(idPartida) }
        val espaciosMetricas = lectura { repositorio.satisfaccionEspacios(idPartida) }
        val animales = lectura { repositorio.animales(idPartida).groupBy { it.idEspacio } }
        val espaciosPorHabitat = lectura { repositorio.espacios(idPartida).groupBy { it.idHabitat } }

        val habitats: Seccion<List<HabitatMonitor>> = try {
            Seccion.Disponible(
                repositorio.habitats(idPartida).map { h ->
                    HabitatMonitor(
                        nombre = h.nombre,
                        valor = valorDe(habitatsMetricas, h.id, "esta satisfacción de hábitat"),
                        espacios = when (espaciosPorHabitat) {
                            is Resultado.Ok -> Seccion.Disponible(
                                espaciosPorHabitat.valor[h.id].orEmpty().map { e ->
                                    EspacioMonitor(
                                        nombre = e.nombre,
                                        valor = valorDe(espaciosMetricas, e.id, "esta satisfacción de espacio"),
                                        animales = when (animales) {
                                            is Resultado.Ok -> Seccion.Disponible(
                                                animales.valor[e.id].orEmpty()
                                                    .map { AnimalMonitor(it.nombre, it.especie, it.valor, it.edadAnios) },
                                            )
                                            is Resultado.Fallo -> Seccion.NoDisponible("No se pudieron consultar los animales.")
                                        },
                                    )
                                },
                            )
                            is Resultado.Fallo -> Seccion.NoDisponible("No se pudieron consultar los espacios.")
                        },
                    )
                },
            )
        } catch (e: Exception) {
            Seccion.NoDisponible("No se pudieron consultar los hábitats.")
        }
        return MonitorSatisfaccion(parque, habitats)
    }

    private sealed interface Resultado<out T> {
        data class Ok<T>(val valor: T) : Resultado<T>

        data object Fallo : Resultado<Nothing>
    }

    private fun <T> lectura(bloque: () -> T): Resultado<T> =
        try {
            Resultado.Ok(bloque())
        } catch (e: Exception) {
            Resultado.Fallo
        }

    private fun valorDe(metricas: Resultado<Map<String, Double>>, id: String, que: String): Seccion<Double?> =
        when (metricas) {
            is Resultado.Ok -> Seccion.Disponible(metricas.valor[id])
            is Resultado.Fallo -> Seccion.NoDisponible("No se pudo consultar $que.")
        }

    private fun seguro(bloque: () -> Boolean): Boolean =
        try {
            bloque()
        } catch (e: Exception) {
            false
        }
}
