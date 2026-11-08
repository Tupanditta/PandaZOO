package com.pandazoo.application.puerto

import com.pandazoo.domain.modelo.IdPartida

/** Pareja recíproca, viva, de la misma especie y alojada en el mismo espacio, con los valores de su rango. */
data class ParejaReproductora(
    val idA: String,
    val idB: String,
    val umbral: Double,
    val probabilidad: Double,
    /** Factor de la probabilidad según los hijos vivos comunes (índice = número de hijos). */
    val factoresPorHijo: List<Double>,
    /** Hijos comunes con estado vital `VIVO`, estén alojados o no. */
    val hijosVivos: Int,
    /** Ya tiene una concepción abierta (`CONCEBIDA`): no se evalúa otra hasta resolverla. */
    val concepcionAbierta: Boolean,
)

/** Concepción notificada, tal y como se persiste. `idA < idB`. */
data class Concepcion(
    val idNotificacion: Long,
    val numeroCiclo: Int,
    val idA: String,
    val idB: String,
    val estado: String,
    val cicloExpiracion: Int,
    val estadoAviso: String,
)

/** Persistencia de la reproducción (CU-10, etapa de reproducción del CU-11). */
interface ReproduccionRepositorio {
    /** Parejas que pueden evaluarse en el ciclo, ordenadas por identificadores. */
    fun parejasReproductoras(idPartida: IdPartida): List<ParejaReproductora>

    /** Inserta la concepción `CONCEBIDA` y su aviso operativo `PENDIENTE` en la misma escritura. */
    fun registrarConcepcion(idPartida: IdPartida, numeroCiclo: Int, idA: String, idB: String, fecha: Long)

    /** Pasa a `EXPIRADA` las concepciones abiertas cuya ventana terminó y a `DESCARTADA` su aviso. Devuelve cuántas. */
    fun expirarVencidas(idPartida: IdPartida, numeroCiclo: Int): Int

    fun concepcionDeAviso(idPartida: IdPartida, idNotificacion: Long): Concepcion?

    /** Concepción `CONFIRMADA` y aviso `ACEPTADA`. */
    fun confirmar(idPartida: IdPartida, concepcion: Concepcion, numeroCiclo: Int)
}
