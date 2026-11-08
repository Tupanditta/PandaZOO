package com.pandazoo.application.caso

import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.operacion.EjecutorOperacion
import com.pandazoo.application.operacion.Operacion
import com.pandazoo.application.puerto.Azar
import com.pandazoo.application.puerto.Reloj
import com.pandazoo.domain.config.ProbabilidadRango
import com.pandazoo.domain.config.RegaloSemanalCfg
import com.pandazoo.domain.mision.SorteoAnimales
import com.pandazoo.domain.modelo.IdPartida
import com.pandazoo.domain.regalo.ReglasRegaloSemanal

/** Regalo semanal concedido: una unidad gratuita de la especie sorteada, para comprarla en la tienda de animales. */
data class RegaloSemanalConcedido(
    val idRegalo: String,
    val nombreRegalo: String,
    val idEspecie: String,
    val nombreEspecie: String,
    val idRango: Int,
    val probabilidades: List<ProbabilidadRango>,
)

/** Persistencia de los regalos semanales. `idRegalo` identifica el regalo de un día concreto (p. ej. `lunes:2026-11-09`). */
interface RegaloSemanalRepositorio {
    fun existe(idPartida: IdPartida, idRegalo: String): Boolean

    /** Registra el regalo y crea su unidad gratuita (origen `SEMANAL`, pendiente) en la transacción en curso. */
    fun registrar(idPartida: IdPartida, idRegalo: String, idEspecie: String, fecha: Long)
}

/**
 * Concede los regalos semanales que corresponden al día de hoy (hora local del dispositivo). Cada regalo tiene un
 * identificador propio con su fecha y se concede como mucho una vez: se comprueba antes de abrir la transacción y otra vez dentro,
 * la clave única de \`RegaloSemanal\` lo impide a nivel de datos y el reintento con el mismo identificador devuelve lo ya confirmado.
 * Como la unidad es gratuita, no desbloquea la especie y no se pierde: queda pendiente hasta que el jugador la compra.
 */
class ConcederRegalosSemanales(
    private val ejecutor: EjecutorOperacion,
    private val repositorio: RegaloSemanalRepositorio,
    private val sorteo: SorteoAnimales,
    private val azar: Azar,
    private val reloj: Reloj,
    private val regalos: List<RegaloSemanalCfg>,
    private val nombresEspecies: Map<String, String>,
) {
    fun ejecutar(idPartida: IdPartida): List<RegaloSemanalConcedido> {
        val dias = ReglasRegaloSemanal.diasLocales(reloj.ahoraSegundos(), reloj.desplazamientoLocalSegundos())
        val hoy = ReglasRegaloSemanal.diaSemana(dias)
        val fecha = ReglasRegaloSemanal.fecha(dias)
        val concedidos = ArrayList<RegaloSemanalConcedido>()
        for (regalo in regalos.filter { it.diaSemana == hoy }) {
            val idRegalo = ReglasRegaloSemanal.idRegalo(regalo.id, fecha)
            if (repositorio.existe(idPartida, idRegalo)) continue
            val op = Operacion(
                idPartida = idPartida,
                tipo = "REGALO_SEMANAL",
                entidad = "ESPECIE",
                origen = "REGALO_SEMANAL",
                idSolicitud = idRegalo,
                huella = "regalo=$idRegalo",
                codificar = { "${it.idRegalo}|${it.idEspecie}|${it.idRango}" },
                decodificar = {
                    val (id, especie, rango) = it.split("|")
                    RegaloSemanalConcedido(id, regalo.nombre, especie, nombresEspecies[especie] ?: especie, rango.toInt(), regalo.sorteo)
                },
                idEntidad = { it.idEspecie },
            )
            val r = ejecutor.ejecutar(op) { conceder(idPartida, regalo, idRegalo) }
            if (r is Resultado.Exito && !r.repetido) concedidos += r.valor
        }
        return concedidos
    }

    private fun conceder(idPartida: IdPartida, regalo: RegaloSemanalCfg, idRegalo: String): Resultado<RegaloSemanalConcedido> {
        if (repositorio.existe(idPartida, idRegalo)) return Resultado.Rechazado(MotivoRechazo.RegaloYaConcedido)
        val idEspecie = sorteo.sortear(regalo.sorteo, azar.siguienteDouble(), azar.siguienteDouble())
            ?: return Resultado.Rechazado(MotivoRechazo.RegaloNoDisponible)
        repositorio.registrar(idPartida, idRegalo, idEspecie, reloj.ahoraSegundos())
        val rango = sorteo.rangoDe(idEspecie) ?: regalo.sorteo.last().idRango
        return Resultado.Exito(
            RegaloSemanalConcedido(idRegalo, regalo.nombre, idEspecie, nombresEspecies[idEspecie] ?: idEspecie, rango, regalo.sorteo),
        )
    }
}
