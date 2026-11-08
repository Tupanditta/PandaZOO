package com.pandazoo.application.caso

import com.pandazoo.application.puerto.AuditoriaRepositorio
import com.pandazoo.application.puerto.PanelPrestigioRepositorio
import com.pandazoo.application.puerto.PrestigioRepositorio
import com.pandazoo.application.puerto.RegistroAuditoria
import com.pandazoo.application.puerto.Reloj
import com.pandazoo.application.puerto.ResultadoAuditoria
import com.pandazoo.application.puerto.Transaccion
import com.pandazoo.domain.modelo.IdPartida
import com.pandazoo.domain.modelo.Seccion
import com.pandazoo.domain.prestigio.EstadoPrestigio
import com.pandazoo.domain.prestigio.MaximosHistoricos
import com.pandazoo.domain.prestigio.ReglasPrestigio

/**
 * Resultado de evaluar el prestigio tras un evento. `ascenso` es el nivel avisado (un único aviso por el más alto)
 * y las listas son los desbloqueos nuevos de esa evaluación.
 */
data class EvaluacionPrestigio(
    val nivel: Int,
    val ascenso: Int?,
    val especiesDesbloqueadas: List<String>,
    val habitatsDesbloqueados: List<String>,
)

/**
 * Recálculo de prestigio (RF-35, RF-36, RF-43, RES-07). Solo lo invoca [RecalcularSatisfaccion] al final de su
 * propio recálculo, es decir, tras una mutación confirmada o al cerrar un ciclo; ninguna consulta ni pantalla lo
 * llama. `invocaciones` permite a los tests comprobar que la navegación no lo ejecuta (RES-10).
 *
 * Se ejecuta dentro de la transacción del llamante: nivel, máximos, desbloqueos, avisos y auditoría se confirman
 * o se revierten junto con la mutación que los provocó.
 */
class RecalcularPrestigio(
    private val repositorio: PrestigioRepositorio,
    private val reglas: ReglasPrestigio,
    private val auditoria: AuditoriaRepositorio,
    private val transaccion: Transaccion,
    private val reloj: Reloj,
) {
    var invocaciones: Int = 0
        private set

    /** [numeroCiclo] es el ciclo en curso si lo evalúa un ciclo; en otro caso se usa el último confirmado. */
    fun alConsolidar(idPartida: IdPartida, numeroCiclo: Int? = null): EvaluacionPrestigio? {
        invocaciones++
        return transaccion.ejecutar {
            val previo = repositorio.estado(idPartida) ?: return@ejecutar null
            val ciclo = numeroCiclo ?: repositorio.ultimoCiclo(idPartida)
            val censo = repositorio.censo(idPartida)
            val ahora = reloj.ahoraSegundos()

            // El nivel de prestigio no baja: si las estadísticas son menores, simplemente se conserva.
            val nivel = maxOf(previo.actual, reglas.nivelActual(censo.animalesVivos, censo.satisfaccionGlobal))
            val maximos = reglas.actualizarMaximos(previo.maximos, nivel, censo.animalesVivos, censo.satisfaccionGlobal)
            val nuevo = EstadoPrestigio(nivel, maximos)
            if (nuevo != previo) repositorio.guardar(idPartida, nuevo)

            val ascenso = nivel.takeIf { reglas.avisaAscenso(previo.maximos?.nivel, it) }
            if (ascenso != null) {
                repositorio.avisarInformativo(idPartida, "PRESTIGIO_ASCENSO", "PARTIDA", ascenso.toString(), ciclo, ahora)
                registrar(idPartida, "PRESTIGIO_ASCENSO", "PARTIDA", ascenso.toString(), ciclo, ahora, "nivel=$ascenso")
            }

            val especies = ArrayList<String>()
            val habitats = ArrayList<String>()
            val yaEspecies = repositorio.especiesDesbloqueadas(idPartida)
            val yaHabitats = repositorio.habitatsDesbloqueados(idPartida)
            for (d in reglas.desbloqueosHasta(nivel)) {
                for (id in d.especies.filter { it !in yaEspecies }) {
                    if (!repositorio.desbloquearEspecie(idPartida, id, ahora)) continue
                    repositorio.avisarInformativo(idPartida, "ESPECIE_DESBLOQUEADA", "ESPECIE", id, ciclo, ahora)
                    registrar(idPartida, "ESPECIE_DESBLOQUEADA", "ESPECIE", id, ciclo, ahora, "nivel=${d.nivel}")
                    especies += id
                }
                for (codigo in d.habitats.filter { it !in yaHabitats }) {
                    val idHabitat = repositorio.desbloquearHabitat(idPartida, codigo, ahora) ?: continue
                    repositorio.avisarInformativo(idPartida, "HABITAT_DESBLOQUEADO", "HABITAT", idHabitat, ciclo, ahora)
                    registrar(idPartida, "HABITAT_DESBLOQUEADO", "HABITAT", idHabitat, ciclo, ahora, "nivel=${d.nivel}")
                    habitats += codigo
                }
            }
            EvaluacionPrestigio(nivel, ascenso, especies, habitats)
        }
    }

    private fun registrar(idPartida: IdPartida, tipo: String, entidad: String, idEntidad: String, ciclo: Int, fecha: Long, detalle: String) {
        auditoria.registrar(
            RegistroAuditoria(
                idPartida = idPartida,
                tipo = tipo,
                entidad = entidad,
                idEntidad = idEntidad,
                origen = "SISTEMA",
                resultado = ResultadoAuditoria.CONFIRMADO,
                numeroCiclo = ciclo,
                claveIdempotencia = "$idPartida:$tipo:$idEntidad",
                fecha = fecha,
                detalle = detalle,
            ),
        )
    }
}

/** Umbrales que debe cumplir el parque para alcanzar el nivel siguiente. */
data class UmbralesSiguiente(val nivel: Int, val animalesVivos: Int, val satisfaccionGlobal: Double)

/**
 * Panel de prestigio (CU-19, RF-37). Cada dato falla por separado: uno no disponible no se sustituye por cero ni
 * por otro valor.
 */
data class PanelPrestigio(
    val actual: Seccion<Int>,
    /** `Disponible(null)` es una partida que todavía no registra máximos. */
    val maximos: Seccion<MaximosHistoricos?>,
    val animalesVivos: Seccion<Int>,
    val satisfaccionGlobal: Seccion<Double>,
    /** `null` si el nivel actual ya es el máximo o el nivel actual no está disponible. */
    val siguiente: UmbralesSiguiente?,
    /** Lo que abre el nivel siguiente, con nombres legibles; `null` si no hay nivel siguiente. */
    val desbloqueoSiguiente: DesbloqueoLegible? = null,
)

/** Especies y hábitats que desbloquea un nivel, por nombre. */
data class DesbloqueoLegible(val especies: List<String>, val habitats: List<String>)

/** CU-19 Consultar prestigio: lee valores persistidos, no recalcula, no abre transacción y no audita (RF-47). */
class ConsultarPrestigio(
    private val repositorio: PanelPrestigioRepositorio,
    private val reglas: ReglasPrestigio,
) {
    fun ejecutar(idPartida: IdPartida): PanelPrestigio {
        val existe = try {
            repositorio.existePartida(idPartida)
        } catch (e: Exception) {
            false
        }
        if (!existe) {
            val sin = Seccion.NoDisponible("No hay una partida de la que consultar el prestigio.")
            return PanelPrestigio(sin, sin, sin, sin, null)
        }
        val estado: EstadoPrestigio? = try {
            repositorio.estado(idPartida)
        } catch (e: Exception) {
            null
        }
        val actual: Seccion<Int> =
            estado?.let { Seccion.Disponible(it.actual) } ?: Seccion.NoDisponible("El prestigio actual no está disponible.")
        val maximos: Seccion<MaximosHistoricos?> =
            estado?.let { Seccion.Disponible(it.maximos) } ?: Seccion.NoDisponible("El máximo histórico no está disponible.")
        val animales: Seccion<Int> = try {
            Seccion.Disponible(repositorio.animalesVivosAlojados(idPartida))
        } catch (e: Exception) {
            Seccion.NoDisponible("No se pudo determinar la cantidad de animales vivos.")
        }
        val satisfaccion: Seccion<Double> = try {
            repositorio.satisfaccionGlobal(idPartida)?.let { Seccion.Disponible(it) }
                ?: Seccion.NoDisponible("La satisfacción global no está disponible.")
        } catch (e: Exception) {
            Seccion.NoDisponible("No se pudo consultar la satisfacción global.")
        }
        val siguiente = estado?.let { reglas.siguiente(it.actual) }
            ?.let { UmbralesSiguiente(it.nivel, it.umbralAnimalesComparacion, it.umbralSatisfaccion) }
        val desbloqueo = estado?.let { reglas.desbloqueoDe(it.actual + 1) }?.let { d ->
            DesbloqueoLegible(
                especies = d.especies.map { repositorio.nombreEspecie(it) ?: it },
                habitats = d.habitats.map { repositorio.nombreHabitat(idPartida, it) ?: it },
            )
        }
        return PanelPrestigio(actual, maximos, animales, satisfaccion, siguiente, desbloqueo)
    }
}
