package com.pandazoo.application.operacion

import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.puerto.AuditoriaRepositorio
import com.pandazoo.application.puerto.RegistroAuditoria
import com.pandazoo.application.puerto.Reloj
import com.pandazoo.application.puerto.ResultadoAuditoria
import com.pandazoo.application.puerto.Transaccion
import com.pandazoo.domain.modelo.IdPartida

/**
 * Descripción de una operación mutante: qué se audita y cómo se reconoce un reintento (RNF-12).
 *
 * @property huella contenido de la solicitud; una clave ya confirmada con otra huella es un conflicto.
 * @property codificar serializa el resultado confirmado para devolverlo tal cual en un reintento.
 */
class Operacion<T>(
    val idPartida: IdPartida,
    val tipo: String,
    val entidad: String,
    val origen: String,
    val idSolicitud: String,
    val huella: String,
    val codificar: (T) -> String,
    val decodificar: (String) -> T,
    val idEntidad: (T) -> String? = { null },
    val numeroCiclo: Int = 0,
) {
    /** `id_partida + tipo_operacion + id_solicitud` (`EspecificacionBBDD.md`, sección 3). */
    val clave: String get() = "$idPartida:$tipo:$idSolicitud"
}

/**
 * Plantilla común de un caso de uso mutante (design.md 2.4): una sola transacción que comprueba la
 * idempotencia, ejecuta la operación y deja la auditoría. Un rechazo de negocio no cambia el dominio y
 * se audita; un fallo técnico revierte todo y se audita aparte como `REVERTIDO`.
 */
class EjecutorOperacion(
    private val transaccion: Transaccion,
    private val auditoria: AuditoriaRepositorio,
    private val reloj: Reloj,
) {
    fun <T> ejecutar(op: Operacion<T>, bloque: () -> Resultado<T>): Resultado<T> =
        try {
            transaccion.ejecutar { dentroDeTransaccion(op, bloque) }
        } catch (e: Exception) {
            auditarReversion(op, e)
            throw e
        }

    private fun <T> dentroDeTransaccion(op: Operacion<T>, bloque: () -> Resultado<T>): Resultado<T> {
        if (!auditoria.existePartida(op.idPartida)) return Resultado.Rechazado(MotivoRechazo.PartidaInexistente)
        val previo = auditoria.confirmadoPorClave(op.idPartida, op.clave)
        if (previo != null) {
            return if (huellaDe(previo.detalle) == op.huella) {
                Resultado.Exito(op.decodificar(resultadoDe(previo.detalle)), repetido = true)
            } else {
                registrar(op, ResultadoAuditoria.RECHAZADO, null, MotivoRechazo.ConflictoIdempotencia.codigo, null)
                Resultado.Rechazado(MotivoRechazo.ConflictoIdempotencia)
            }
        }
        return when (val r = bloque()) {
            is Resultado.Exito -> {
                val detalle = op.huella + SEPARADOR + op.codificar(r.valor)
                registrar(op, ResultadoAuditoria.CONFIRMADO, op.idEntidad(r.valor), detalle, op.clave)
                r
            }
            is Resultado.Rechazado -> {
                registrar(op, ResultadoAuditoria.RECHAZADO, null, r.motivo.codigo, null)
                r
            }
        }
    }

    private fun registrar(op: Operacion<*>, resultado: ResultadoAuditoria, idEntidad: String?, detalle: String, clave: String?) {
        auditoria.registrar(
            RegistroAuditoria(
                idPartida = op.idPartida,
                tipo = op.tipo,
                entidad = op.entidad,
                idEntidad = idEntidad,
                origen = op.origen,
                resultado = resultado,
                numeroCiclo = op.numeroCiclo,
                claveIdempotencia = clave,
                fecha = reloj.ahoraSegundos(),
                detalle = detalle,
            ),
        )
    }

    /** Auditoría del intento revertido, en su propia transacción porque la original ya se deshizo. */
    private fun auditarReversion(op: Operacion<*>, causa: Exception) {
        runCatching {
            transaccion.ejecutar {
                registrar(op, ResultadoAuditoria.REVERTIDO, null, causa::class.simpleName ?: "Exception", null)
            }
        }
    }

    private fun huellaDe(detalle: String) = detalle.substringBefore(SEPARADOR)

    private fun resultadoDe(detalle: String) = detalle.substringAfter(SEPARADOR, "")

    private companion object {
        const val SEPARADOR = "\n=>\n"
    }
}
