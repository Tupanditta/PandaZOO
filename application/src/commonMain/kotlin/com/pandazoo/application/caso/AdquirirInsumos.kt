package com.pandazoo.application.caso

import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.operacion.EjecutorOperacion
import com.pandazoo.application.operacion.Operacion
import com.pandazoo.application.puerto.CatalogoRepositorio
import com.pandazoo.application.puerto.EconomiaRepositorio
import com.pandazoo.application.puerto.InventarioRepositorio
import com.pandazoo.application.puerto.Reloj
import com.pandazoo.application.puerto.TipoMovimiento
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.modelo.IdPartida
import com.pandazoo.domain.modelo.Recurso
import com.pandazoo.domain.modelo.TipoRecurso

data class ComandoAdquirirInsumos(
    val idPartida: IdPartida,
    val recurso: Recurso,
    val cantidad: Int,
    /** Se genera una sola vez al iniciar la acción en la interfaz y se reutiliza en sus reintentos. */
    val idSolicitud: String,
)

data class CompraRealizada(val coste: Dinero, val saldo: Dinero, val existenciasTotales: Int)

/** CU-07 Adquirir insumos: bambú o insumos médicos (RF-10, RF-31, RF-50, RES-12). */
class AdquirirInsumos(
    private val ejecutor: EjecutorOperacion,
    private val economia: EconomiaRepositorio,
    private val inventario: InventarioRepositorio,
    private val catalogo: CatalogoRepositorio,
    private val reloj: Reloj,
) {
    fun ejecutar(c: ComandoAdquirirInsumos): Resultado<CompraRealizada> {
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "COMPRA_RECURSO",
            entidad = "INVENTARIO",
            origen = "CU-07",
            idSolicitud = c.idSolicitud,
            huella = "recurso=${c.recurso.tipo}:${c.recurso.codigo};cantidad=${c.cantidad}",
            codificar = { "${it.coste.centimos}|${it.saldo.centimos}|${it.existenciasTotales}" },
            decodificar = {
                val (coste, saldo, total) = it.split("|")
                CompraRealizada(Dinero.deCentimos(coste.toLong()), Dinero.deCentimos(saldo.toLong()), total.toInt())
            },
            idEntidad = { c.recurso.codigo },
        )
        return ejecutor.ejecutar(op) { comprar(c) }
    }

    private fun comprar(c: ComandoAdquirirInsumos): Resultado<CompraRealizada> {
        if (c.cantidad <= 0) return rechazo(MotivoRechazo.CantidadInvalida)
        val oferta = catalogo.oferta(c.recurso) ?: return rechazo(MotivoRechazo.RecursoNoElegible)
        val precio = oferta.precio ?: return rechazo(MotivoRechazo.PrecioNoConfigurado)
        val actual = inventario.cantidad(c.idPartida, c.recurso) ?: 0
        if (actual.toLong() + c.cantidad > Int.MAX_VALUE) return rechazo(MotivoRechazo.CantidadInvalida)

        val coste = Dinero.coste(c.cantidad, precio)
        val saldo = economia.fondos(c.idPartida)
        if (saldo < coste) return rechazo(MotivoRechazo.SaldoInsuficiente(coste, saldo))

        val nuevoSaldo = saldo - coste
        economia.actualizarFondos(c.idPartida, nuevoSaldo)
        inventario.incrementar(c.idPartida, c.recurso, c.cantidad)
        economia.registrarMovimiento(c.idPartida, TipoMovimiento.COMPRA, coste, "CU-07", 0, reloj.ahoraSegundos())
        return Resultado.Exito(CompraRealizada(coste, nuevoSaldo, actual + c.cantidad))
    }

    private fun rechazo(m: MotivoRechazo) = Resultado.Rechazado(m)

    companion object {
        fun bambu(): Recurso = Recurso(TipoRecurso.ALIMENTO, "bambu")
    }
}
