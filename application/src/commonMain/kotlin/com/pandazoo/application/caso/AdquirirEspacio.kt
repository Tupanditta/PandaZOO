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
import com.pandazoo.domain.modelo.EstadoEntidad
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
        val gratuito = c.capacidad == CAPACIDAD_GRATUITA && zoo.espacioGratuitoDisponible(c.idPartida)
        val coste = if (gratuito) Dinero.CERO else opcion.coste
        val saldo = economia.fondos(c.idPartida)
        if (saldo < coste) return rechazo(MotivoRechazo.SaldoInsuficiente(coste, saldo))

        val idEspacio = ids.nuevo()
        val nuevoSaldo = saldo - coste
        if (gratuito) {
            zoo.consumirEspacioGratuito(c.idPartida)
        } else {
            economia.actualizarFondos(c.idPartida, nuevoSaldo)
            economia.registrarMovimiento(c.idPartida, TipoMovimiento.COMPRA, coste, "CU-03", 0, reloj.ahoraSegundos())
        }
        zoo.crearEspacio(c.idPartida, idEspacio, c.idHabitat, nombre, c.capacidad, coste)
        return Resultado.Exito(EspacioAdquirido(idEspacio, coste, nuevoSaldo))
    }

    private fun rechazo(m: MotivoRechazo) = Resultado.Rechazado(m)

    companion object {
        const val MAX_DENOMINACION = 40

        /** El primer espacio de esta capacidad no cuesta nada. */
        const val CAPACIDAD_GRATUITA = 6
    }
}

data class ComandoAmpliarEspacio(
    val idPartida: IdPartida,
    val idEspacio: String,
    val nuevaCapacidad: Int,
    val idSolicitud: String,
)

data class EspacioAmpliado(val idEspacio: String, val capacidad: Int, val coste: Dinero, val saldo: Dinero)

/**
 * Ampliar un espacio existente: se paga la diferencia de precio con la capacidad actual más un 25 % de recargo. Nunca se reduce la capacidad
 * (solo se puede ampliar o vender el espacio vacío) y no se supera la mayor capacidad del catálogo.
 */
class AmpliarEspacio(
    private val ejecutor: EjecutorOperacion,
    private val economia: EconomiaRepositorio,
    private val zoo: ZooRepositorio,
    private val catalogo: CatalogoRepositorio,
    private val reloj: Reloj,
) {
    fun ejecutar(c: ComandoAmpliarEspacio): Resultado<EspacioAmpliado> {
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "AMPLIACION_ESPACIO",
            entidad = "ESPACIO",
            origen = "CU-03",
            idSolicitud = c.idSolicitud,
            huella = "espacio=${c.idEspacio};capacidad=${c.nuevaCapacidad}",
            codificar = { "${it.idEspacio}|${it.capacidad}|${it.coste.centimos}|${it.saldo.centimos}" },
            decodificar = {
                val (id, capacidad, coste, saldo) = it.split("|")
                EspacioAmpliado(id, capacidad.toInt(), Dinero.deCentimos(coste.toLong()), Dinero.deCentimos(saldo.toLong()))
            },
            idEntidad = { it.idEspacio },
        )
        return ejecutor.ejecutar(op) { ampliar(c) }
    }

    private fun ampliar(c: ComandoAmpliarEspacio): Resultado<EspacioAmpliado> {
        val espacio = zoo.espacio(c.idPartida, c.idEspacio) ?: return Resultado.Rechazado(MotivoRechazo.EspacioInexistente)
        if (espacio.estado != EstadoEntidad.ACTIVO) return Resultado.Rechazado(MotivoRechazo.EspacioInactivo)
        val opcion = catalogo.opcionesCapacidad().firstOrNull { it.capacidad == c.nuevaCapacidad }
            ?: return Resultado.Rechazado(MotivoRechazo.CapacidadNoPermitida)
        if (c.nuevaCapacidad <= espacio.capacidadMax) return Resultado.Rechazado(MotivoRechazo.CapacidadNoAmpliable)
        val actual = catalogo.opcionesCapacidad().firstOrNull { it.capacidad == espacio.capacidadMax }?.coste ?: Dinero.CERO
        val coste = costeAmpliacion(actual, opcion.coste)
        val saldo = economia.fondos(c.idPartida)
        if (saldo < coste) return Resultado.Rechazado(MotivoRechazo.SaldoInsuficiente(coste, saldo))

        val nuevoSaldo = saldo - coste
        economia.actualizarFondos(c.idPartida, nuevoSaldo)
        economia.registrarMovimiento(c.idPartida, TipoMovimiento.COMPRA, coste, "CU-03", 0, reloj.ahoraSegundos())
        zoo.ampliarEspacio(c.idPartida, c.idEspacio, c.nuevaCapacidad, coste)
        return Resultado.Exito(EspacioAmpliado(c.idEspacio, c.nuevaCapacidad, coste, nuevoSaldo))
    }

    companion object {
        /** Diferencia de precio entre la capacidad nueva y la actual, más un cuarto de recargo (medios hacia arriba). */
        fun costeAmpliacion(actual: Dinero, nuevo: Dinero): Dinero {
            val diferencia = (nuevo.centimos - actual.centimos).coerceAtLeast(0L)
            return Dinero.deCentimos((diferencia * 5 + 2) / 4)
        }
    }
}
