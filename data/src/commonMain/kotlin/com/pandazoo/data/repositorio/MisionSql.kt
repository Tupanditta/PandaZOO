package com.pandazoo.data.repositorio

import com.pandazoo.application.puerto.EstadoMision
import com.pandazoo.application.puerto.InstanciaMision
import com.pandazoo.application.puerto.MisionRepositorio
import com.pandazoo.application.puerto.RecompensaConfigurada
import com.pandazoo.application.puerto.RecompensaRegistrada
import com.pandazoo.application.puerto.TipoRecompensa
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.domain.mision.Progresion
import com.pandazoo.domain.modelo.IdPartida

/**
 * Misiones, progreso y recompensas (`Mision`, `ParametroMision`, `ProgresoMision`, `ParametroProgresoMision`,
 * `RecompensaMision`). Todas las escrituras ocurren dentro de la transacción del caso de uso que las invoca.
 */
class MisionSql(private val bd: BaseDatos) : MisionRepositorio {
    override fun instancias(idPartida: IdPartida): List<InstanciaMision> = cargar(idPartida, null)

    override fun instancia(idPartida: IdPartida, idInstancia: String): InstanciaMision? = cargar(idPartida, idInstancia).firstOrNull()

    private fun cargar(idPartida: IdPartida, idInstancia: String?): List<InstanciaMision> {
        val filtro = if (idInstancia == null) "" else " AND p.id_instancia_mision = ?"
        val args = if (idInstancia == null) arrayOf<Any?>(idPartida) else arrayOf<Any?>(idPartida, idInstancia)
        val numericos = HashMap<String, MutableMap<String, Double>>()
        val textos = HashMap<String, MutableMap<String, String>>()
        bd.leer(
            "SELECT q.id_instancia_mision, q.nombre_parametro, q.valor_numero, q.valor_texto FROM ParametroProgresoMision q " +
                "JOIN ProgresoMision p ON p.id_partida = q.id_partida AND p.id_instancia_mision = q.id_instancia_mision " +
                "WHERE q.id_partida = ?$filtro",
            *args,
        ) { Triple(it.texto(0), it.texto(1), it.decimalONulo(2) to it.textoONulo(3)) }.forEach { (instancia, nombre, valor) ->
            valor.first?.let { numericos.getOrPut(instancia) { LinkedHashMap() }[nombre] = it }
            valor.second?.let { textos.getOrPut(instancia) { LinkedHashMap() }[nombre] = it }
        }
        return bd.leer(
            "SELECT p.id_instancia_mision, p.id_mision, m.tipo_objetivo, m.naturaleza_progreso, p.progreso, p.estado, " +
                "m.tipo_recompensa, m.id_item_recompensa, m.cantidad_recompensa FROM ProgresoMision p " +
                "JOIN Mision m ON m.id_mision = p.id_mision WHERE p.id_partida = ?$filtro ORDER BY p.id_mision",
            *args,
        ) {
            val id = it.texto(0)
            InstanciaMision(
                idInstancia = id,
                idMision = it.texto(1),
                tipoObjetivo = it.texto(2),
                dinamica = it.texto(3) == "DINAMICO",
                progreso = it.decimal(4),
                estado = EstadoMision.valueOf(it.texto(5)),
                numericos = numericos[id].orEmpty(),
                textos = textos[id].orEmpty(),
                recompensa = RecompensaConfigurada(TipoRecompensa.valueOf(it.texto(6)), it.textoONulo(7), it.decimal(8)),
            )
        }
    }

    override fun progresiones(idMision: String): Map<String, Progresion> =
        bd.leer(
            "SELECT nombre_parametro, operacion_progresion, constante_progresion, valor_minimo, valor_maximo FROM ParametroMision " +
                "WHERE id_mision = ? AND tipo_valor = 'NUMERICO' AND operacion_progresion IS NOT NULL",
            idMision,
        ) { it.texto(0) to Progresion(it.texto(1), it.decimal(2), it.decimal(3), it.decimal(4)) }.toMap()

    override fun fijarProgreso(idPartida: IdPartida, idInstancia: String, progreso: Double, estado: EstadoMision) {
        bd.escribir(
            "UPDATE ProgresoMision SET progreso = ?, estado = ? WHERE id_partida = ? AND id_instancia_mision = ?",
            progreso, estado.name, idPartida, idInstancia,
        )
    }

    override fun sustituirInstancia(idPartida: IdPartida, anterior: InstanciaMision, nuevaId: String, numericos: Map<String, Double>) {
        // Los parámetros de la instancia retirada caen en cascada; el tipo es único por partida, así que se borra antes de insertar.
        bd.escribir("DELETE FROM ProgresoMision WHERE id_partida = ? AND id_instancia_mision = ?", idPartida, anterior.idInstancia)
        bd.escribir(
            "INSERT INTO ProgresoMision (id_partida, id_instancia_mision, id_mision, progreso, estado) VALUES (?, ?, ?, 0, 'PENDIENTE')",
            idPartida, nuevaId, anterior.idMision,
        )
        numericos.forEach { (nombre, valor) ->
            bd.escribir(
                "INSERT INTO ParametroProgresoMision (id_partida, id_instancia_mision, id_mision, nombre_parametro, valor_numero, valor_texto) " +
                    "VALUES (?, ?, ?, ?, ?, NULL)",
                idPartida, nuevaId, anterior.idMision, nombre, valor,
            )
        }
        anterior.textos.forEach { (nombre, valor) ->
            bd.escribir(
                "INSERT INTO ParametroProgresoMision (id_partida, id_instancia_mision, id_mision, nombre_parametro, valor_numero, valor_texto) " +
                    "VALUES (?, ?, ?, ?, NULL, ?)",
                idPartida, nuevaId, anterior.idMision, nombre, valor,
            )
        }
    }

    override fun marcarAgotada(idPartida: IdPartida, idInstancia: String) {
        bd.escribir(
            "UPDATE ProgresoMision SET estado = 'AGOTADA' WHERE id_partida = ? AND id_instancia_mision = ?",
            idPartida, idInstancia,
        )
    }

    override fun valorDinamico(idPartida: IdPartida, instancia: InstanciaMision): Double {
        val vivos = "a.id_partida = ? AND a.estado_vital = 'VIVO' AND a.id_espacio IS NOT NULL"
        val especie = instancia.textos["id_especie"]
        val habitat = instancia.textos["id_habitat"]
        return when (instancia.idMision) {
            "total_animales" -> contar("SELECT COUNT(*) FROM Animal a WHERE $vivos", idPartida)
            "animales_habitat" ->
                contar(
                    "SELECT COUNT(*) FROM Animal a JOIN Espacio e ON e.id_partida = a.id_partida AND e.id_espacio = a.id_espacio " +
                        "JOIN Habitat h ON h.id_partida = e.id_partida AND h.id_habitat = e.id_habitat " +
                        "WHERE $vivos AND e.estado = 'ACTIVO' AND h.codigo = ?",
                    idPartida, habitat,
                )
            "espacios_ocupados_habitat" ->
                contar(
                    "SELECT COUNT(*) FROM (SELECT e.id_espacio FROM Animal a " +
                        "JOIN Espacio e ON e.id_partida = a.id_partida AND e.id_espacio = a.id_espacio " +
                        "JOIN Habitat h ON h.id_partida = e.id_partida AND h.id_habitat = e.id_habitat " +
                        "WHERE $vivos AND e.estado = 'ACTIVO' AND h.codigo = ? GROUP BY e.id_espacio HAVING COUNT(*) >= ?)",
                    idPartida, habitat, instancia.numericos["numero_minimo_animales_por_espacio"] ?: Double.MAX_VALUE,
                )
            "subir_prestigio" -> contar("SELECT prestigio_actual FROM Partida WHERE id_partida = ?", idPartida)
            "familia", "familia_especie" -> hermanos(idPartida, if (instancia.idMision == "familia_especie") especie else null)
            "satisfaccion_minima" ->
                decimal("SELECT COALESCE(MAX(a.satisfaccion_actual), 0) FROM Animal a WHERE $vivos", idPartida)
            "satisfaccion_minima_especie" ->
                decimal("SELECT COALESCE(MAX(a.satisfaccion_actual), 0) FROM Animal a WHERE $vivos AND a.id_especie = ?", idPartida, especie)
            else -> instancia.progreso
        }
    }

    /**
     * Mayor número de hermanos vivos (animales con el mismo padre o la misma madre) entre los animales vivos alojados
     * cuyos padre y madre están vivos y alojados.
     */
    private fun hermanos(idPartida: IdPartida, especie: String?): Double =
        decimal(
            "SELECT COALESCE(MAX((SELECT COUNT(*) FROM Animal b WHERE b.id_partida = a.id_partida AND b.estado_vital = 'VIVO' " +
                "AND b.id_animal <> a.id_animal AND (b.id_padre = a.id_padre OR b.id_madre = a.id_madre))), 0) " +
                "FROM Animal a " +
                "JOIN Animal p ON p.id_partida = a.id_partida AND p.id_animal = a.id_padre AND p.estado_vital = 'VIVO' AND p.id_espacio IS NOT NULL " +
                "JOIN Animal m ON m.id_partida = a.id_partida AND m.id_animal = a.id_madre AND m.estado_vital = 'VIVO' AND m.id_espacio IS NOT NULL " +
                "WHERE a.id_partida = ? AND a.estado_vital = 'VIVO' AND a.id_espacio IS NOT NULL" +
                (if (especie != null) " AND a.id_especie = ?" else ""),
            *(if (especie != null) arrayOf<Any?>(idPartida, especie) else arrayOf<Any?>(idPartida)),
        )

    private fun contar(sql: String, vararg args: Any?): Double = decimal(sql, *args)

    private fun decimal(sql: String, vararg args: Any?): Double = bd.uno(sql, *args) { it.decimal(0) } ?: 0.0

    override fun recompensa(idPartida: IdPartida, idInstancia: String): RecompensaRegistrada? =
        bd.uno(
            "SELECT tipo, id_item, cantidad, id_especie_seleccionada, unidad_pendiente FROM RecompensaMision " +
                "WHERE id_partida = ? AND id_instancia_mision = ?",
            idPartida, idInstancia,
        ) {
            RecompensaRegistrada(
                idInstancia, TipoRecompensa.valueOf(it.texto(0)), it.textoONulo(1), it.decimal(2), it.textoONulo(3), it.entero(4) == 1,
            )
        }

    override fun registrarRecompensa(
        idPartida: IdPartida,
        idMision: String,
        idInstancia: String,
        recompensa: RecompensaConfigurada,
        idEspecie: String?,
        numeroCiclo: Int,
        fecha: Long,
    ) {
        bd.escribir(
            "INSERT INTO RecompensaMision (id_partida, id_mision, id_instancia_mision, tipo, id_item, cantidad, " +
                "id_especie_seleccionada, unidad_pendiente, estado, numero_ciclo, fecha) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'RECLAMADA', ?, ?)",
            idPartida, idMision, idInstancia, recompensa.tipo.name, recompensa.idItem, recompensa.cantidad, idEspecie,
            if (recompensa.tipo == TipoRecompensa.ESPECIE) 1 else 0, numeroCiclo, fecha,
        )
    }

    override fun desbloquearEspecie(idPartida: IdPartida, idEspecie: String, numeroCiclo: Int, fecha: Long) {
        bd.escribir(
            "INSERT OR IGNORE INTO DesbloqueoEspecie (id_partida, id_especie, origen, fecha) VALUES (?, ?, 'RECOMPENSA', ?)",
            idPartida, idEspecie, fecha,
        )
        bd.escribir(
            "INSERT INTO Notificacion (id_partida, tipo, severidad, codigo, entidad, id_entidad, numero_ciclo, estado, fecha) " +
                "SELECT ?, 'INFORMATIVA', 'BAJA', 'ESPECIE_DESBLOQUEADA', 'ESPECIE', ?, ?, 'PENDIENTE', ? WHERE NOT EXISTS (" +
                "SELECT 1 FROM Notificacion WHERE id_partida = ? AND tipo = 'INFORMATIVA' AND codigo = 'ESPECIE_DESBLOQUEADA' AND id_entidad = ?)",
            idPartida, idEspecie, numeroCiclo, fecha, idPartida, idEspecie,
        )
    }

    override fun especiesConUnidad(idPartida: IdPartida): Set<String> =
        bd.leer(
            "SELECT DISTINCT id_especie_seleccionada FROM RecompensaMision WHERE id_partida = ? AND unidad_pendiente = 1",
            idPartida,
        ) { it.texto(0) }.toSet()

    override fun consumirUnidad(idPartida: IdPartida, idEspecie: String): Boolean {
        bd.escribir(
            "UPDATE RecompensaMision SET unidad_pendiente = 0 WHERE id_recompensa = (SELECT MIN(id_recompensa) FROM RecompensaMision " +
                "WHERE id_partida = ? AND unidad_pendiente = 1 AND id_especie_seleccionada = ?)",
            idPartida, idEspecie,
        )
        return (bd.uno("SELECT changes()") { it.entero(0) } ?: 0) > 0
    }

    override fun nombreEspecie(idEspecie: String): String? =
        bd.uno("SELECT nombre_comun FROM Especie WHERE id_especie = ?", idEspecie) { it.texto(0) }

    override fun nombreHabitat(idPartida: IdPartida, codigo: String): String? =
        bd.uno("SELECT nombre FROM Habitat WHERE id_partida = ? AND codigo = ?", idPartida, codigo) { it.texto(0).removePrefix("Hábitat ").trim() }

    override fun nombreRecurso(idItem: String): String? =
        bd.uno("SELECT nombre FROM Alimento WHERE id_alimento = ?", idItem) { it.texto(0) }
            ?: bd.uno("SELECT nombre FROM InsumoMedico WHERE id_insumo = ?", idItem) { it.texto(0) }
}
