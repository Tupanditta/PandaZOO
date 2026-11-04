package com.pandazoo.application.caso

import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.operacion.EjecutorOperacion
import com.pandazoo.application.operacion.Operacion
import com.pandazoo.application.puerto.ZooRepositorio
import com.pandazoo.domain.modelo.EstadoEntidad
import com.pandazoo.domain.modelo.IdPartida

data class ComandoConfigurarEspacio(
    val idPartida: IdPartida,
    val idEspacio: String,
    /** `null` conserva la denominación actual. */
    val nuevoNombre: String?,
    /** `null` conserva el hábitat actual. */
    val nuevoIdHabitat: String?,
    val idSolicitud: String,
)

data class EspacioConfigurado(val idEspacio: String, val nombre: String, val idHabitat: String)

/**
 * CU-02 Configurar espacio: cambia denominación y/o hábitat, nunca la capacidad (RF-04, RES-04).
 *
 * Los hábitats no tienen una capacidad propia: la capacidad de un espacio es inalterable, así que el
 * requisito de «capacidad suficiente» del hábitat de destino se traduce en que el hábitat exista, esté
 * activo y desbloqueado (CU-02, 3.c).
 */
class ConfigurarEspacio(
    private val ejecutor: EjecutorOperacion,
    private val zoo: ZooRepositorio,
) {
    fun ejecutar(c: ComandoConfigurarEspacio): Resultado<EspacioConfigurado> {
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "CONFIGURACION_ESPACIO",
            entidad = "ESPACIO",
            origen = "CU-02",
            idSolicitud = c.idSolicitud,
            huella = "espacio=${c.idEspacio};nombre=${c.nuevoNombre?.trim()};habitat=${c.nuevoIdHabitat}",
            codificar = { "${it.idEspacio}|${it.idHabitat}|${it.nombre}" },
            decodificar = {
                val partes = it.split("|", limit = 3)
                EspacioConfigurado(partes[0], partes[2], partes[1])
            },
            idEntidad = { it.idEspacio },
        )
        return ejecutor.ejecutar(op) { configurar(c) }
    }

    private fun configurar(c: ComandoConfigurarEspacio): Resultado<EspacioConfigurado> {
        val actual = zoo.espacio(c.idPartida, c.idEspacio) ?: return rechazo(MotivoRechazo.EspacioInexistente)
        if (actual.estado != EstadoEntidad.ACTIVO) return rechazo(MotivoRechazo.EspacioInactivo)

        val idHabitatFinal = c.nuevoIdHabitat ?: actual.idHabitat
        val nombreFinal = c.nuevoNombre?.trim() ?: actual.nombre
        if (nombreFinal.isEmpty() || nombreFinal.length > AdquirirEspacio.MAX_DENOMINACION) {
            return rechazo(MotivoRechazo.DenominacionInvalida)
        }
        val cambiaHabitat = idHabitatFinal != actual.idHabitat
        if (!cambiaHabitat && nombreFinal == actual.nombre) return rechazo(MotivoRechazo.SinCambios)

        if (cambiaHabitat) {
            val destino = zoo.habitat(c.idPartida, idHabitatFinal)
            if (destino == null || !destino.utilizable) return rechazo(MotivoRechazo.HabitatNoDisponible)
        }
        val choca = zoo.espacios(c.idPartida, idHabitatFinal)
            .any { it.id != actual.id && it.nombre.equals(nombreFinal, ignoreCase = true) }
        if (choca) return rechazo(MotivoRechazo.DenominacionDuplicada)

        zoo.reconfigurarEspacio(c.idPartida, actual.id, nombreFinal, idHabitatFinal)
        return Resultado.Exito(EspacioConfigurado(actual.id, nombreFinal, idHabitatFinal))
    }

    private fun rechazo(m: MotivoRechazo) = Resultado.Rechazado(m)
}
