package com.pandazoo.data.repositorio

import com.pandazoo.application.puerto.Concepcion
import com.pandazoo.application.puerto.ParejaReproductora
import com.pandazoo.application.puerto.ReproduccionRepositorio
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.domain.modelo.IdPartida
import com.pandazoo.domain.reproduccion.ReglasReproduccion

class ReproduccionSql(private val bd: BaseDatos) : ReproduccionRepositorio {
    override fun parejasReproductoras(idPartida: IdPartida): List<ParejaReproductora> =
        bd.leer(
            "SELECT a.id_animal, b.id_animal, r.umbral_reproduccion, r.probabilidad_reproduccion, r.penalizacion_hijo_reproduccion, " +
                "(SELECT COUNT(*) FROM Animal h WHERE h.id_partida = a.id_partida AND h.estado_vital = 'VIVO' " +
                "AND ((h.id_padre = a.id_animal AND h.id_madre = b.id_animal) OR (h.id_padre = b.id_animal AND h.id_madre = a.id_animal))), " +
                "EXISTS (SELECT 1 FROM ReproduccionCiclo c WHERE c.id_partida = a.id_partida AND c.id_animal_a = a.id_animal " +
                "AND c.id_animal_b = b.id_animal AND c.estado = 'CONCEBIDA') " +
                "FROM Animal a JOIN Animal b ON b.id_partida = a.id_partida AND b.id_animal = a.id_pareja AND b.id_pareja = a.id_animal " +
                "JOIN Especie s ON s.id_especie = a.id_especie JOIN RangoEspecie r ON r.id_rango = s.id_rango " +
                "WHERE a.id_partida = ? AND a.id_animal < b.id_animal AND a.estado_vital = 'VIVO' AND b.estado_vital = 'VIVO' " +
                "AND a.id_espacio IS NOT NULL AND a.id_espacio = b.id_espacio AND a.id_especie = b.id_especie " +
                "ORDER BY a.id_animal, b.id_animal",
            idPartida,
        ) {
            ParejaReproductora(
                idA = it.texto(0), idB = it.texto(1), umbral = it.decimal(2), probabilidad = it.decimal(3),
                penalizacionPorHijo = it.decimal(4), hijosVivos = it.entero(5), concepcionAbierta = it.entero(6) == 1,
            )
        }

    override fun registrarConcepcion(idPartida: IdPartida, numeroCiclo: Int, idA: String, idB: String, fecha: Long) {
        bd.escribir(
            "INSERT INTO ReproduccionCiclo (id_partida, numero_ciclo, id_animal_a, id_animal_b, estado, ciclo_disponible, ciclo_expiracion) " +
                "VALUES (?, ?, ?, ?, 'CONCEBIDA', ?, ?)",
            idPartida, numeroCiclo, idA, idB, ReglasReproduccion.cicloDisponible(numeroCiclo), ReglasReproduccion.cicloExpiracion(numeroCiclo),
        )
        bd.escribir(
            "INSERT INTO Notificacion (id_partida, tipo, severidad, codigo, entidad, id_entidad, numero_ciclo, estado, fecha) " +
                "VALUES (?, 'OPERATIVA', 'MEDIA', 'REPRODUCCION_DISPONIBLE', 'REPRODUCCION', ?, ?, 'PENDIENTE', ?)",
            idPartida, "$numeroCiclo:$idA:$idB", numeroCiclo, fecha,
        )
    }

    override fun expirarVencidas(idPartida: IdPartida, numeroCiclo: Int): Int {
        val vencidas = "SELECT numero_ciclo || ':' || id_animal_a || ':' || id_animal_b FROM ReproduccionCiclo " +
            "WHERE id_partida = ? AND estado = 'CONCEBIDA' AND ciclo_expiracion < ?"
        bd.escribir(
            "UPDATE Notificacion SET estado = 'DESCARTADA', ciclo_cierre = MAX(numero_ciclo, ?) WHERE id_partida = ? " +
                "AND tipo = 'OPERATIVA' AND codigo = 'REPRODUCCION_DISPONIBLE' AND estado IN ('PENDIENTE', 'VISTA') " +
                "AND id_entidad IN ($vencidas)",
            numeroCiclo, idPartida, idPartida, numeroCiclo,
        )
        bd.escribir(
            "UPDATE ReproduccionCiclo SET estado = 'EXPIRADA' WHERE id_partida = ? AND estado = 'CONCEBIDA' AND ciclo_expiracion < ?",
            idPartida, numeroCiclo,
        )
        return bd.uno("SELECT changes()") { it.entero(0) } ?: 0
    }

    override fun concepcionDeAviso(idPartida: IdPartida, idNotificacion: Long): Concepcion? =
        bd.uno(
            "SELECT n.id_notificacion, r.numero_ciclo, r.id_animal_a, r.id_animal_b, r.estado, r.ciclo_expiracion, n.estado " +
                "FROM Notificacion n JOIN ReproduccionCiclo r ON r.id_partida = n.id_partida " +
                "AND n.id_entidad = r.numero_ciclo || ':' || r.id_animal_a || ':' || r.id_animal_b " +
                "WHERE n.id_partida = ? AND n.id_notificacion = ? AND n.codigo = 'REPRODUCCION_DISPONIBLE'",
            idPartida, idNotificacion,
        ) {
            Concepcion(it.largo(0), it.entero(1), it.texto(2), it.texto(3), it.texto(4), it.entero(5), it.texto(6))
        }

    override fun confirmar(idPartida: IdPartida, concepcion: Concepcion, numeroCiclo: Int) {
        bd.escribir(
            "UPDATE ReproduccionCiclo SET estado = 'CONFIRMADA' WHERE id_partida = ? AND numero_ciclo = ? AND id_animal_a = ? AND id_animal_b = ?",
            idPartida, concepcion.numeroCiclo, concepcion.idA, concepcion.idB,
        )
        bd.escribir(
            "UPDATE Notificacion SET estado = 'ACEPTADA', ciclo_cierre = MAX(numero_ciclo, ?) WHERE id_partida = ? AND id_notificacion = ?",
            numeroCiclo, idPartida, concepcion.idNotificacion,
        )
    }
}
