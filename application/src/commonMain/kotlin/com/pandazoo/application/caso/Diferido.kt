package com.pandazoo.application.caso

import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.puerto.AuditoriaRepositorio
import com.pandazoo.application.puerto.CicloRepositorio
import com.pandazoo.application.puerto.RegistroAuditoria
import com.pandazoo.application.puerto.Reloj
import com.pandazoo.application.puerto.ResultadoAuditoria
import com.pandazoo.application.puerto.Transaccion
import com.pandazoo.domain.ciclo.ReglasCiclo
import com.pandazoo.domain.ciclo.ReglasDiferido
import com.pandazoo.domain.modelo.IdPartida

/**
 * Resultado de una recuperación diferida.
 *
 * @property segundosAusente tiempo medido desde la marca de actividad (`E`), 0 si el reloj fue anómalo o no hay marca.
 * @property relojAnomalo la marca era posterior a `ahora` (salto del reloj hacia atrás, regla 5.2, punto 4).
 */
data class ResumenDiferido(
    val segundosAusente: Long,
    val ciclosProcesados: Int,
    val ciclosDescartados: Int,
    val remanenteSegundos: Int,
    val relojAnomalo: Boolean = false,
)

/**
 * CU-12 Procesar simulación en diferido (RF-21 a RF-24, RNF-09, RNF-12, regla de reloj 5.2). Se ejecuta en cada paso a
 * primer plano, no solo en el arranque en frío.
 *
 * Cada ciclo vencido es su propia transacción de CU-11 (identificador único, sin repetir uno ya confirmado) y deja
 * como marca de actividad el instante virtual en que terminó. Así, un cierre inesperado a mitad conserva el último
 * ciclo confirmado y la siguiente apertura reanuda exactamente desde ahí, sin duplicar ni regalar ciclos. Una última
 * transacción fija el remanente y la marca con la hora actual y deja un único registro de auditoría de la recuperación.
 */
class ProcesarDiferido(
    private val transaccion: Transaccion,
    private val ciclos: CicloRepositorio,
    private val ejecutarCiclo: EjecutarCiclo,
    private val auditoria: AuditoriaRepositorio,
    private val purga: PurgarRetencion,
    private val reloj: Reloj,
    private val maxCiclosOffline: Int,
) {
    /**
     * @param cancelado se consulta entre ciclos; si devuelve `true` se detiene la recuperación conservando lo ya confirmado.
     */
    fun ejecutar(idPartida: IdPartida, cancelado: () -> Boolean = { false }): Resultado<ResumenDiferido> {
        val estado = ciclos.estadoMotor(idPartida) ?: return Resultado.Rechazado(MotivoRechazo.PartidaInexistente)
        val ahora = reloj.ahoraSegundos()
        val remanente = estado.remanenteSegundos

        // Partida o simulación en pausa: el tiempo ausente no cuenta, solo se renueva la marca.
        if (!estado.partidaActiva || !estado.simulacionActiva) {
            transaccion.ejecutar { ciclos.guardarRemanente(idPartida, remanente, ahora) }
            return Resultado.Exito(ResumenDiferido(0, 0, 0, remanente))
        }

        // 2.b: sin marca no hay desfase que validar; no se procesan ciclos y se reinicializa la marca.
        val marca = estado.marcaActividad
        if (marca == null) {
            transaccion.ejecutar { ciclos.guardarRemanente(idPartida, remanente, ahora) }
            return Resultado.Exito(ResumenDiferido(0, 0, 0, remanente))
        }

        // 5.2.4: un salto hacia atrás es E = 0. R no cambia, la marca se reescribe y se audita el reloj anómalo.
        if (marca > ahora) {
            transaccion.ejecutar {
                ciclos.guardarRemanente(idPartida, remanente, ahora)
                auditoria.registrar(
                    RegistroAuditoria(
                        idPartida = idPartida,
                        tipo = "RELOJ_ANOMALO",
                        entidad = "CICLO",
                        idEntidad = null,
                        origen = "CU-12",
                        resultado = ResultadoAuditoria.CONFIRMADO,
                        numeroCiclo = estado.numeroUltimo ?: 0,
                        claveIdempotencia = null,
                        fecha = ahora,
                        detalle = "marca=$marca;ahora=$ahora",
                    ),
                )
            }
            return Resultado.Exito(ResumenDiferido(0, 0, 0, remanente, relojAnomalo = true))
        }

        val transcurridos = ahora - marca
        val plan = ReglasDiferido.planificar(transcurridos, remanente, maxCiclosOffline)

        // 2.a: no vence ningún ciclo; solo se actualiza el remanente (la marca avanza con él).
        if (plan.ciclos == 0) {
            transaccion.ejecutar { ciclos.guardarRemanente(idPartida, plan.remanenteSegundos, ahora) }
            return Resultado.Exito(ResumenDiferido(transcurridos, 0, 0, plan.remanenteSegundos))
        }

        // Las restricciones offline se miden contra el estado de cada animal al empezar la sesión.
        val inicioSesion = ciclos.animalesVivosAlojados(idPartida).associateBy { it.id }
        val primero = estado.siguiente
        val ultimo = primero + plan.ciclos - 1
        var procesados = 0
        for (k in 0 until plan.ciclos) {
            if (cancelado()) return Resultado.Exito(ResumenDiferido(transcurridos, procesados, 0, remanente))
            // Instante virtual en que termina el ciclo k: `marca + R + k · 600`, nunca posterior a `ahora`.
            val instante = marca + remanente + k.toLong() * ReglasCiclo.DURACION_CICLO_SEGUNDOS
            val r = ejecutarCiclo.ejecutar(ComandoEjecutarCiclo(idPartida, primero + k, ContextoDiferido(inicioSesion, instante, ultimo)))
            if (r is Resultado.Rechazado) return r
            procesados++
        }

        transaccion.ejecutar {
            ciclos.guardarRemanente(idPartida, plan.remanenteSegundos, ahora)
            auditoria.registrar(
                RegistroAuditoria(
                    idPartida = idPartida,
                    tipo = "RECUPERACION_DIFERIDA",
                    entidad = "CICLO",
                    idEntidad = ultimo.toString(),
                    origen = "CU-12",
                    resultado = ResultadoAuditoria.CONFIRMADO,
                    numeroCiclo = ultimo,
                    claveIdempotencia = "$idPartida:RECUPERACION_DIFERIDA:$ultimo",
                    fecha = ahora,
                    detalle = "ausente=$transcurridos;ciclos=${plan.ciclos};descartados=${plan.descartados};remanente=${plan.remanenteSegundos}",
                ),
            )
        }
        // La purga va en su propia transacción: si falla, la recuperación ya confirmada se conserva.
        runCatching { purga.ejecutar(idPartida) }
        return Resultado.Exito(ResumenDiferido(transcurridos, procesados, plan.descartados, plan.remanenteSegundos))
    }
}
