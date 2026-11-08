package com.pandazoo.data.repositorio

import com.pandazoo.application.caso.RegaloSemanalRepositorio
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.domain.modelo.IdPartida

class RegalosSemanalesSql(private val bd: BaseDatos) : RegaloSemanalRepositorio {
    override fun existe(idPartida: IdPartida, idRegalo: String): Boolean =
        bd.uno("SELECT 1 FROM RegaloSemanal WHERE id_partida = ? AND id_regalo = ?", idPartida, idRegalo) { true } ?: false

    override fun registrar(idPartida: IdPartida, idRegalo: String, idEspecie: String, fecha: Long) {
        // La clave (id_partida, id_regalo) impide por sí sola conceder dos veces el mismo regalo.
        bd.escribir(
            "INSERT INTO RegaloSemanal (id_partida, id_regalo, id_especie, fecha) VALUES (?, ?, ?, ?)",
            idPartida, idRegalo, idEspecie, fecha,
        )
        bd.escribir(
            "INSERT INTO UnidadGratuita (id_partida, id_especie, origen, unidad_pendiente, fecha) VALUES (?, ?, 'SEMANAL', 1, ?)",
            idPartida, idEspecie, fecha,
        )
    }
}
