package com.pandazoo.data.repositorio

import com.pandazoo.application.puerto.CensoGlobal
import com.pandazoo.application.puerto.PanelPrestigioRepositorio
import com.pandazoo.application.puerto.PrestigioRepositorio
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.domain.modelo.IdPartida
import com.pandazoo.domain.prestigio.EstadoPrestigio
import com.pandazoo.domain.prestigio.MaximosHistoricos

private fun BaseDatos.estadoPrestigio(idPartida: IdPartida): EstadoPrestigio? =
    uno(
        "SELECT prestigio_actual, prestigio_maximo_nivel, animales_vivos_maximos, satisfaccion_global_maxima FROM Partida WHERE id_partida = ?",
        idPartida,
    ) {
        EstadoPrestigio(
            actual = it.entero(0),
            maximos = if (it.esNulo(1)) null else MaximosHistoricos(it.entero(1), it.entero(2), it.decimal(3)),
        )
    }

private fun BaseDatos.vivosAlojados(idPartida: IdPartida): Int =
    uno("SELECT COUNT(*) FROM Animal WHERE id_partida = ? AND estado_vital = 'VIVO' AND id_espacio IS NOT NULL", idPartida) { it.entero(0) } ?: 0

private fun BaseDatos.satisfaccionDelParque(idPartida: IdPartida): Double? =
    uno("SELECT valor FROM MetricaSatisfaccion WHERE id_partida = ? AND nivel = 'PARTIDA'", idPartida) { it.decimal(0) }

class PrestigioSql(private val bd: BaseDatos) : PrestigioRepositorio {
    override fun estado(idPartida: IdPartida): EstadoPrestigio? = bd.estadoPrestigio(idPartida)

    override fun censo(idPartida: IdPartida): CensoGlobal {
        val vivos = bd.vivosAlojados(idPartida)
        return CensoGlobal(vivos, if (vivos == 0) 0.0 else bd.satisfaccionDelParque(idPartida) ?: 0.0)
    }

    override fun ultimoCiclo(idPartida: IdPartida): Int =
        bd.uno("SELECT numero_ciclo_ultimo FROM EstadoSimulacion WHERE id_partida = ?", idPartida) { if (it.esNulo(0)) 0 else it.entero(0) } ?: 0

    override fun guardar(idPartida: IdPartida, estado: EstadoPrestigio) {
        val m = estado.maximos
        bd.escribir(
            "UPDATE Partida SET prestigio_actual = ?, prestigio_maximo_nivel = ?, animales_vivos_maximos = ?, " +
                "satisfaccion_global_maxima = ? WHERE id_partida = ?",
            estado.actual, m?.nivel, m?.animalesVivos, m?.satisfaccionGlobal, idPartida,
        )
    }

    override fun especiesDesbloqueadas(idPartida: IdPartida): Set<String> =
        bd.leer("SELECT id_especie FROM DesbloqueoEspecie WHERE id_partida = ?", idPartida) { it.texto(0) }.toSet()

    override fun habitatsDesbloqueados(idPartida: IdPartida): Set<String> =
        bd.leer(
            "SELECT h.codigo FROM DesbloqueoHabitat d JOIN Habitat h ON h.id_partida = d.id_partida AND h.id_habitat = d.id_habitat " +
                "WHERE d.id_partida = ?",
            idPartida,
        ) { it.texto(0) }.toSet()

    override fun desbloquearEspecie(idPartida: IdPartida, idEspecie: String, fecha: Long): Boolean {
        if (bd.uno("SELECT 1 FROM Especie WHERE id_especie = ?", idEspecie) { true } != true) return false
        bd.escribir(
            "INSERT OR IGNORE INTO DesbloqueoEspecie (id_partida, id_especie, origen, fecha) VALUES (?, ?, 'PRESTIGIO', ?)",
            idPartida, idEspecie, fecha,
        )
        return true
    }

    override fun desbloquearHabitat(idPartida: IdPartida, codigo: String, fecha: Long): String? {
        val id = bd.uno("SELECT id_habitat FROM Habitat WHERE id_partida = ? AND codigo = ?", idPartida, codigo) { it.texto(0) } ?: return null
        bd.escribir("INSERT OR IGNORE INTO DesbloqueoHabitat (id_partida, id_habitat, fecha) VALUES (?, ?, ?)", idPartida, id, fecha)
        return id
    }

    override fun avisarInformativo(idPartida: IdPartida, codigo: String, entidad: String, idEntidad: String, numeroCiclo: Int, fecha: Long) {
        bd.escribir(
            "INSERT INTO Notificacion (id_partida, tipo, severidad, codigo, entidad, id_entidad, numero_ciclo, estado, fecha) " +
                "SELECT ?, 'INFORMATIVA', 'BAJA', ?, ?, ?, ?, 'PENDIENTE', ? WHERE NOT EXISTS (" +
                "SELECT 1 FROM Notificacion WHERE id_partida = ? AND tipo = 'INFORMATIVA' AND codigo = ? AND id_entidad = ?)",
            idPartida, codigo, entidad, idEntidad, numeroCiclo, fecha, idPartida, codigo, idEntidad,
        )
    }
}

class PanelPrestigioSql(private val bd: BaseDatos) : PanelPrestigioRepositorio {
    override fun existePartida(idPartida: IdPartida): Boolean =
        bd.uno("SELECT 1 FROM Partida WHERE id_partida = ?", idPartida) { true } ?: false

    override fun estado(idPartida: IdPartida): EstadoPrestigio? = bd.estadoPrestigio(idPartida)

    override fun animalesVivosAlojados(idPartida: IdPartida): Int = bd.vivosAlojados(idPartida)

    override fun satisfaccionGlobal(idPartida: IdPartida): Double? = bd.satisfaccionDelParque(idPartida)

    override fun nombreEspecie(idEspecie: String): String? =
        bd.uno("SELECT nombre_comun FROM Especie WHERE id_especie = ?", idEspecie) { it.texto(0) }

    override fun nombreHabitat(idPartida: IdPartida, codigo: String): String? =
        bd.uno("SELECT nombre FROM Habitat WHERE id_partida = ? AND codigo = ?", idPartida, codigo) { it.texto(0).removePrefix("Hábitat ").trim() }
}
