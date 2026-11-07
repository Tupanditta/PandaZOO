package com.pandazoo.application.caso

import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.operacion.EjecutorOperacion
import com.pandazoo.application.operacion.Operacion
import com.pandazoo.application.puerto.AnimalCiclo
import com.pandazoo.application.puerto.AnimalRepositorio
import com.pandazoo.application.puerto.AuditoriaRepositorio
import com.pandazoo.application.puerto.Azar
import com.pandazoo.application.puerto.CicloRepositorio
import com.pandazoo.application.puerto.EstadoAnimalCiclo
import com.pandazoo.application.puerto.RegistroAuditoria
import com.pandazoo.application.puerto.Reloj
import com.pandazoo.application.puerto.ResultadoAuditoria
import com.pandazoo.domain.ciclo.CausaMuerte
import com.pandazoo.domain.ciclo.ReglasCiclo
import com.pandazoo.domain.ciclo.ReglasDiferido
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.modelo.IdPartida

/** Estado mutable de un animal mientras se procesan las etapas del ciclo; las etapas se ven unas a otras (RF-14). */
class AnimalEnCiclo(origen: AnimalCiclo, /** Estado al empezar la sesión diferida; en un ciclo en vivo es el propio origen. */ val inicioSesion: AnimalCiclo = origen) {
    val id: String = origen.id
    val idEspecie: String = origen.idEspecie
    val edadMaximaDias: Int = origen.edadMaximaDias
    val satisfaccionInicial: Double = origen.satisfaccion
    val probabilidadEnfermedad: Double = origen.probabilidadEnfermedad
    val beneficioPorCiclo: Dinero = origen.beneficioPorCiclo

    var edadDias: Int = origen.edadDias
    var alimentacion: Int = origen.alimentacion
    var enfermo: Boolean = origen.enfermo
    var ciclosDesnutricion: Int = origen.ciclosDesnutricion
    var ciclosEnfermedad: Int = origen.ciclosEnfermedad
    var causaMuerte: CausaMuerte? = null

    fun estado() = EstadoAnimalCiclo(id, edadDias, alimentacion, enfermo, ciclosDesnutricion, ciclosEnfermedad)
}

/** Observador de etapas: los tests lo usan para comprobar el orden y que cada etapa ve el resultado de la anterior. */
fun interface TrazaCiclo {
    fun etapa(nombre: String, animales: List<AnimalEnCiclo>)
}

/** Punto de extensión de las etapas de reproducción (Sprint 7) y misiones (Sprint 9). */
fun interface EtapaCiclo {
    fun ejecutar(idPartida: IdPartida, numeroCiclo: Int, vivos: List<AnimalEnCiclo>)
}

object Etapas {
    const val EDAD = "EDAD"
    const val ALIMENTACION = "ALIMENTACION"
    const val SALUD = "SALUD"
    const val CICLOS_CRITICOS = "CICLOS_CRITICOS"
    const val DEFUNCIONES = "DEFUNCIONES"
    const val REPRODUCCION = "REPRODUCCION"
    const val BENEFICIOS = "BENEFICIOS"
    const val MISIONES = "MISIONES"
    const val METRICAS = "METRICAS"
    const val PERSISTENCIA = "PERSISTENCIA"
}

/**
 * Contexto de un ciclo procesado en diferido (CU-12). Las restricciones de RF-22 y RF-23 se miden contra el estado de
 * cada animal al empezar la sesión.
 *
 * @property inicioSesion estado de los animales vivos y alojados antes del primer ciclo diferido, por identificador.
 * @property instante instante virtual en que termina este ciclo (segundos Unix): es la marca de actividad que queda
 * persistida, de modo que una interrupción reanuda desde ese punto sin perder ni regalar tiempo.
 */
class ContextoDiferido(val inicioSesion: Map<String, AnimalCiclo>, val instante: Long)

data class ComandoEjecutarCiclo(val idPartida: IdPartida, val numero: Int, val diferido: ContextoDiferido? = null)

data class CicloEjecutado(
    val numero: Int,
    val vivos: Int,
    val muertos: Int,
    val nuevosEnfermos: Int,
    val beneficio: Dinero,
)

/**
 * CU-11 Ejecutar ciclo (RF-14, RF-24, RNF-12). Una única transacción procesa edad, alimentación, salud, ciclos
 * críticos, defunciones, reproducción, beneficios, misiones, métricas y persistencia, en ese orden. El número de
 * ciclo es la clave de idempotencia: repetirlo devuelve el resultado persistido sin repetir efectos.
 *
 * Decisión sobre el beneficio: se calcula tras las defunciones con la satisfacción persistida al inicio del ciclo
 * (la «vigente al inicio de la etapa») de los animales que siguen vivos; la satisfacción nueva se calcula después, en
 * la etapa de métricas, y alimenta el beneficio del ciclo siguiente.
 */
class EjecutarCiclo(
    private val ejecutor: EjecutorOperacion,
    private val ciclos: CicloRepositorio,
    private val animales: AnimalRepositorio,
    private val auditoria: AuditoriaRepositorio,
    private val recalculo: RecalcularSatisfaccion,
    private val azar: Azar,
    private val reloj: Reloj,
    private val traza: TrazaCiclo? = null,
    private val reproduccion: EtapaCiclo? = null,
    private val misiones: EtapaCiclo? = null,
) {
    /** Ejecuta el ciclo siguiente al último confirmado. */
    fun siguiente(idPartida: IdPartida): Resultado<CicloEjecutado> {
        val estado = ciclos.estadoMotor(idPartida) ?: return Resultado.Rechazado(MotivoRechazo.PartidaInexistente)
        return ejecutar(ComandoEjecutarCiclo(idPartida, estado.siguiente))
    }

    fun ejecutar(c: ComandoEjecutarCiclo): Resultado<CicloEjecutado> {
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "CICLO_CONFIRMADO",
            entidad = "CICLO",
            origen = "CICLO_PERIODICO",
            idSolicitud = "ciclo-${c.numero}",
            huella = "ciclo=${c.numero}",
            codificar = { "${it.numero}|${it.vivos}|${it.muertos}|${it.nuevosEnfermos}|${it.beneficio.centimos}" },
            decodificar = {
                val (n, vivos, muertos, enfermos, beneficio) = it.split("|")
                CicloEjecutado(n.toInt(), vivos.toInt(), muertos.toInt(), enfermos.toInt(), Dinero.deCentimos(beneficio.toLong()))
            },
            idEntidad = { it.numero.toString() },
            numeroCiclo = c.numero,
        )
        return ejecutor.ejecutar(op) { procesar(c) }
    }

    private fun procesar(c: ComandoEjecutarCiclo): Resultado<CicloEjecutado> {
        val idPartida = c.idPartida
        val estado = ciclos.estadoMotor(idPartida) ?: return Resultado.Rechazado(MotivoRechazo.PartidaInexistente)
        if (!estado.partidaActiva || !estado.simulacionActiva) return Resultado.Rechazado(MotivoRechazo.SimulacionNoActiva)
        if (c.numero != estado.siguiente) return Resultado.Rechazado(MotivoRechazo.CicloFueraDeOrden(estado.siguiente))
        val ahora = reloj.ahoraSegundos()

        val diferido = c.diferido
        val vivos = ciclos.animalesVivosAlojados(idPartida).map { AnimalEnCiclo(it, diferido?.inicioSesion?.get(it.id) ?: it) }

        // 3.1 Edad.
        vivos.forEach { it.edadDias = ReglasCiclo.edadTrasCiclo(it.edadDias) }
        traza?.etapa(Etapas.EDAD, vivos)

        // 3.2 Alimentación.
        vivos.forEach {
            it.alimentacion = if (diferido == null) {
                ReglasCiclo.alimentacionTrasCiclo(it.alimentacion)
            } else {
                ReglasDiferido.alimentacionTrasCiclo(it.alimentacion, it.inicioSesion.alimentacion) // RF-22
            }
        }
        traza?.etapa(Etapas.ALIMENTACION, vivos)

        // 3.3 Salud: solo se sortean los sanos, con la satisfacción persistida al inicio del ciclo.
        val nuevosEnfermos = ArrayList<AnimalEnCiclo>()
        for (a in vivos) {
            if (a.enfermo) continue
            if (ReglasCiclo.enferma(azar.siguienteDouble(), a.probabilidadEnfermedad, a.satisfaccionInicial)) {
                a.enfermo = true
                nuevosEnfermos += a
            }
        }
        traza?.etapa(Etapas.SALUD, vivos)

        // 3.4 Contadores críticos, independientes entre sí.
        vivos.forEach {
            if (diferido == null) {
                it.ciclosDesnutricion = ReglasCiclo.contadorDesnutricion(it.ciclosDesnutricion, it.alimentacion)
                it.ciclosEnfermedad = ReglasCiclo.contadorEnfermedad(it.ciclosEnfermedad, it.enfermo)
            } else {
                val ini = it.inicioSesion
                it.ciclosDesnutricion = ReglasDiferido.contadorDesnutricion(it.ciclosDesnutricion, ini.ciclosDesnutricion, it.alimentacion)
                it.ciclosEnfermedad = ReglasDiferido.contadorEnfermedad(it.ciclosEnfermedad, ini.ciclosEnfermedad, it.enfermo, ini.enfermo) // RF-23
            }
        }
        traza?.etapa(Etapas.CICLOS_CRITICOS, vivos)

        // 3.5 Defunciones.
        vivos.forEach { it.causaMuerte = ReglasCiclo.causaDeMuerte(it.edadDias, it.edadMaximaDias, it.ciclosDesnutricion, it.ciclosEnfermedad) }
        val muertos = vivos.filter { it.causaMuerte != null }
        val supervivientes = vivos.filter { it.causaMuerte == null }
        ciclos.guardarEstados(idPartida, supervivientes.map { it.estado() })
        for (m in muertos) {
            animales.retirar(idPartida, m.id, "MUERTO")
            auditar(idPartida, c.numero, "DEFUNCION", "ANIMAL", m.id, "${m.id}:DEFUNCION", requireNotNull(m.causaMuerte).name, ahora)
        }
        nuevosEnfermos.filter { it.causaMuerte == null }.forEach {
            auditar(idPartida, c.numero, "CAMBIO_ESTADO_SALUD", "ANIMAL", it.id, "${it.id}:ENFERMEDAD", "SANO>ENFERMO", ahora)
        }
        traza?.etapa(Etapas.DEFUNCIONES, supervivientes)

        // 3.6 Reproducción: punto de extensión del Sprint 7.
        reproduccion?.ejecutar(idPartida, c.numero, supervivientes)
        traza?.etapa(Etapas.REPRODUCCION, supervivientes)

        // 3.7 Beneficios: tras las defunciones, con la satisfacción vigente al inicio de la etapa.
        val beneficio = if (supervivientes.isEmpty()) {
            Dinero.CERO
        } else {
            ReglasCiclo.beneficio(supervivientes.sumOf { it.satisfaccionInicial } / supervivientes.size, supervivientes.map { it.beneficioPorCiclo })
        }
        if (beneficio > Dinero.CERO) {
            val clave = "$idPartida:BENEFICIO:${c.numero}"
            ciclos.registrarBeneficio(idPartida, c.numero, beneficio, clave, ahora)
            auditar(idPartida, c.numero, "BENEFICIO_GENERADO", "BENEFICIO", null, "BENEFICIO:${c.numero}", beneficio.toString(), ahora)
        }
        traza?.etapa(Etapas.BENEFICIOS, supervivientes)

        // 3.8 Misiones: punto de extensión del Sprint 9.
        misiones?.ejecutar(idPartida, c.numero, supervivientes)
        traza?.etapa(Etapas.MISIONES, supervivientes)

        // 3.9 Métricas de satisfacción y avisos críticos.
        recalculo.alConsolidar(idPartida, AlcanceRecalculo.Parque, c.numero)
        actualizarAlertas(idPartida, c.numero, supervivientes, ahora)
        traza?.etapa(Etapas.METRICAS, supervivientes)

        // 3.10 Persistencia del ciclo y del temporizador.
        val instante = diferido?.instante ?: ahora
        ciclos.registrarCiclo(idPartida, c.numero, instante, instante)
        ciclos.actualizarEstadoMotor(idPartida, c.numero, instante, ReglasCiclo.DURACION_CICLO_SEGUNDOS)
        traza?.etapa(Etapas.PERSISTENCIA, supervivientes)

        return Resultado.Exito(CicloEjecutado(c.numero, supervivientes.size, muertos.size, nuevosEnfermos.count { it.causaMuerte == null }, beneficio))
    }

    /**
     * Un único aviso crítico abierto por animal: se abre al entrar en estado crítico, se conserva mientras persista
     * alguna causa y se resuelve cuando ya no hay ninguna o el animal deja de estar vivo.
     */
    private fun actualizarAlertas(idPartida: IdPartida, numero: Int, supervivientes: List<AnimalEnCiclo>, ahora: Long) {
        val criticos = supervivientes.filter { it.ciclosDesnutricion > 0 || it.ciclosEnfermedad > 0 }.map { it.id }.toSet()
        val abiertas = ciclos.alertasCriticasAbiertas(idPartida)
        val nuevas = criticos - abiertas
        val cerradas = abiertas - criticos
        if (nuevas.isNotEmpty()) ciclos.abrirAlertasCriticas(idPartida, nuevas.sorted(), numero, ahora)
        if (cerradas.isNotEmpty()) ciclos.resolverAlertasCriticas(idPartida, cerradas.sorted(), numero)
    }

    private fun auditar(
        idPartida: IdPartida,
        numero: Int,
        tipo: String,
        entidad: String,
        idEntidad: String?,
        claveSufijo: String,
        detalle: String,
        fecha: Long,
    ) {
        auditoria.registrar(
            RegistroAuditoria(
                idPartida = idPartida,
                tipo = tipo,
                entidad = entidad,
                idEntidad = idEntidad,
                origen = "CICLO_PERIODICO",
                resultado = ResultadoAuditoria.CONFIRMADO,
                numeroCiclo = numero,
                claveIdempotencia = "$idPartida:$numero:$claveSufijo",
                fecha = fecha,
                detalle = detalle,
            ),
        )
    }
}
