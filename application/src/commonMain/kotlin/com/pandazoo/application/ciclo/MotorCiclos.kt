package com.pandazoo.application.ciclo

import com.pandazoo.application.caso.EjecutarCiclo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.caso.PurgarRetencion
import com.pandazoo.application.puerto.CicloRepositorio
import com.pandazoo.application.puerto.Reloj
import com.pandazoo.domain.ciclo.ReglasCiclo
import com.pandazoo.domain.modelo.IdPartida
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reloj monótono en milisegundos: no le afecta un cambio de la hora del dispositivo (`5.2`, punto 6). */
fun interface RelojMonotonico {
    fun ahoraMs(): Long
}

/**
 * Temporizador del ciclo (RF-24): una sola espera hasta el fin del ciclo, sin sondeo (RES-10). Con la app en
 * primer plano espera el remanente persistido, ejecuta el ciclo y vuelve a empezar con 600 s. Al pasar a segundo
 * plano ([detener]) guarda el remanente, para continuar donde corresponde en el siguiente [iniciar].
 *
 * No hay servicio en segundo plano (D7): el tiempo con la app cerrada lo resuelve CU-12 (`ProcesarDiferido`).
 */
class MotorCiclos(
    private val scope: CoroutineScope,
    private val idPartida: IdPartida,
    private val ciclos: CicloRepositorio,
    private val ejecutarCiclo: EjecutarCiclo,
    private val purga: PurgarRetencion,
    private val monotonico: RelojMonotonico,
    private val dispatcher: CoroutineDispatcher,
    private val reloj: Reloj,
    private val alTerminarCiclo: () -> Unit = {},
) {
    private var tarea: Job? = null

    /** Segundos de la espera en curso; 0 mientras se ejecuta el ciclo o si no hay espera. */
    @Volatile
    private var esperaSegundos: Int = 0

    @Volatile
    private var inicioEsperaMs: Long = 0

    val activo: Boolean get() = tarea?.isActive == true

    fun iniciar() {
        if (activo) return
        tarea = scope.launch { bucle() }
    }

    /** Cancela la espera y persiste el remanente si el ciclo no está ya en ejecución. */
    fun detener() {
        val t = tarea ?: return
        tarea = null
        t.cancel()
        val espera = esperaSegundos
        if (espera > 0) {
            val transcurridos = ((monotonico.ahoraMs() - inicioEsperaMs) / 1000).toInt()
            // Remanente y marca de actividad juntos: CU-12 mide la ausencia desde esa marca (regla 5.2).
            ciclos.guardarRemanente(idPartida, (espera - transcurridos).coerceIn(0, ReglasCiclo.DURACION_CICLO_SEGUNDOS), reloj.ahoraSegundos())
            esperaSegundos = 0
        }
    }

    private suspend fun bucle() {
        while (true) {
            val estado = withContext(dispatcher) { ciclos.estadoMotor(idPartida) } ?: return
            if (!estado.partidaActiva || !estado.simulacionActiva) return
            val espera = estado.remanenteSegundos.coerceIn(0, ReglasCiclo.DURACION_CICLO_SEGUNDOS)
            inicioEsperaMs = monotonico.ahoraMs()
            esperaSegundos = espera
            delay(espera * 1000L)
            esperaSegundos = 0
            try {
                withContext(dispatcher) {
                    val resultado = ejecutarCiclo.siguiente(idPartida)
                    // Un rechazo no reinicia el temporizador: se espera un ciclo completo antes de reintentar.
                    if (resultado is Resultado.Rechazado) ciclos.guardarRemanente(idPartida, ReglasCiclo.DURACION_CICLO_SEGUNDOS)
                    // La purga va en su propia transacción: si falla, el ciclo ya confirmado se conserva.
                    runCatching { purga.ejecutar(idPartida) }
                }
                alTerminarCiclo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ciclo revertido: se conserva el último estado consistente y se reintenta en un ciclo completo.
                runCatching { withContext(dispatcher) { ciclos.guardarRemanente(idPartida, ReglasCiclo.DURACION_CICLO_SEGUNDOS) } }
            }
        }
    }
}
