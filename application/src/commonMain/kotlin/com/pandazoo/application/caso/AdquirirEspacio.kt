package com.pandazoo.application.caso

import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.operacion.EjecutorOperacion
import com.pandazoo.application.operacion.Operacion
import com.pandazoo.application.puerto.CatalogoRepositorio
import com.pandazoo.application.puerto.EconomiaRepositorio
import com.pandazoo.application.puerto.GeneradorId
import com.pandazoo.application.puerto.Reloj
import com.pandazoo.application.puerto.TipoMovimiento
import com.pandazoo.application.puerto.ZooRepositorio
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.modelo.IdPartida

data class ComandoAdquirirEspacio(
    val idPartida: IdPartida,
    val idHabitat: String,
    val nombre: String,
    val capacidad: Int,
    val idSolicitud: String,
)

data class EspacioAdquirido(val idEspacio: String, val coste: Dinero, val saldo: Dinero)

/** CU-03 Adquirir espacios (RF-02, RF-31, RES-04). */
class AdquirirEspacio(
    private val ejecutor: EjecutorOperacion,
    private val economia: EconomiaRepositorio,
    private val zoo: ZooRepositorio,
    private val catalogo: CatalogoRepositorio,
    private val ids: GeneradorId,
    private val reloj: Reloj,
) {
    fun ejecutar(c: ComandoAdquirirEspacio): Resultado<EspacioAdquirido> {
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "COMPRA_ESPACIO",
            entidad = "ESPACIO",
            origen = "CU-03",
            idSolicitud = c.idSolicitud,
            huella = "habitat=${c.idHabitat};nombre=${c.nombre.trim()};capacidad=${c.capacidad}",
            codificar = { "${it.idEspacio}|${it.coste.centimos}|${it.saldo.centimos}" },
            decodificar = {
                val (id, coste, saldo) = it.split("|")
                EspacioAdquirido(id, Dinero.deCentimos(coste.toLong()), Dinero.deCentimos(saldo.toLong()))
            },
            idEntidad = { it.idEspacio },
        )
        return ejecutor.ejecutar(op) { comprar(c) }
    }

    private fun comprar(c: ComandoAdquirirEspacio): Resultado<EspacioAdquirido> {
        val habitat = zoo.habitat(c.idPartida, c.idHabitat)
        if (habitat == null || !habitat.utilizable) return rechazo(MotivoRechazo.HabitatNoDisponible)
        val opcion = catalogo.opcionesCapacidad().firstOrNull { it.capacidad == c.capacidad }
            ?: return rechazo(MotivoRechazo.CapacidadNoPermitida)
        val nombre = c.nombre.trim()
        if (nombre.isEmpty() || nombre.length > MAX_DENOMINACION) return rechazo(MotivoRechazo.DenominacionInvalida)
        if (zoo.espacios(c.idPartida, c.idHabitat).any { it.nombre.equals(nombre, ignoreCase = true) }) {
            return rechazo(MotivoRechazo.DenominacionDuplicada)
        }
        val saldo = economia.fondos(c.idPartida)
        if (saldo < opcion.coste) return rechazo(MotivoRechazo.SaldoInsuficiente(opcion.coste, saldo))

        val idEspacio = ids.nuevo()
        val nuevoSaldo = saldo - opcion.coste
        economia.actualizarFondos(c.idPartida, nuevoSaldo)
        zoo.crearEspacio(c.idPartida, idEspacio, c.idHabitat, nombre, c.capacidad)
        economia.registrarMovimiento(c.idPartida, TipoMovimiento.COMPRA, opcion.coste, "CU-03", 0, reloj.ahoraSegundos())
        return Resultado.Exito(EspacioAdquirido(idEspacio, opcion.coste, nuevoSaldo))
    }

    private fun rechazo(m: MotivoRechazo) = Resultado.Rechazado(m)

    companion object {
        const val MAX_DENOMINACION = 40
    }
}
