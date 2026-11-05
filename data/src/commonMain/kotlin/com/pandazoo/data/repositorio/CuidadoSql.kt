package com.pandazoo.data.repositorio

import com.pandazoo.application.puerto.ConsumoMedico
import com.pandazoo.application.puerto.CuidadoRepositorio
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.domain.modelo.IdPartida

class CuidadoSql(private val bd: BaseDatos) : CuidadoRepositorio {
    override fun kilosPorIncremento(idEspecie: String): Int? =
        bd.uno("SELECT kg_bambu_por_incremento FROM Especie WHERE id_especie = ?", idEspecie) { it.entero(0) }

    override fun consumoMedico(idPartida: IdPartida, idAnimal: String): List<ConsumoMedico> =
        bd.leer(
            "SELECT c.id_insumo, i.nombre, c.cantidad_unidades FROM Animal a " +
                "JOIN Especie s ON s.id_especie = a.id_especie " +
                "JOIN ConsumoMedicoRango c ON c.id_rango = s.id_rango " +
                "JOIN InsumoMedico i ON i.id_insumo = c.id_insumo " +
                "WHERE a.id_partida = ? AND a.id_animal = ? ORDER BY c.id_insumo",
            idPartida, idAnimal,
        ) { ConsumoMedico(it.texto(0), it.texto(1), it.entero(2)) }

    override fun fijarAlimentacion(idPartida: IdPartida, idAnimal: String, nivel: Int) {
        require(nivel in 0..100 && nivel % 10 == 0) { "La alimentación debe ser 0 o múltiplo de 10" }
        // Con alimentación mayor que 0 el contador de desnutrición vuelve a cero.
        bd.escribir(
            "UPDATE Animal SET nivel_alimentacion = ?, ciclos_desnutricion = CASE WHEN ? > 0 THEN 0 ELSE ciclos_desnutricion END " +
                "WHERE id_partida = ? AND id_animal = ?",
            nivel, nivel, idPartida, idAnimal,
        )
    }

    override fun curar(idPartida: IdPartida, idAnimal: String) {
        bd.escribir(
            "UPDATE Animal SET estado_salud = 'SANO', ciclos_enfermedad = 0 WHERE id_partida = ? AND id_animal = ?",
            idPartida, idAnimal,
        )
    }

    override fun resolverAlertaSalud(idPartida: IdPartida, idAnimal: String) {
        bd.escribir(
            "UPDATE Notificacion SET estado = 'RESUELTA', ciclo_cierre = numero_ciclo " +
                "WHERE id_partida = ? AND codigo = 'ALERTA_SALUD' AND entidad = 'ANIMAL' AND id_entidad = ? " +
                "AND estado IN ('PENDIENTE', 'VISTA') " +
                "AND EXISTS (SELECT 1 FROM Animal a WHERE a.id_partida = ? AND a.id_animal = ? " +
                "AND a.ciclos_desnutricion = 0 AND a.ciclos_enfermedad = 0)",
            idPartida, idAnimal, idPartida, idAnimal,
        )
    }
}
