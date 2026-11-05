package com.pandazoo.data.repositorio

import com.pandazoo.application.puerto.AnimalCiclo
import com.pandazoo.application.puerto.AnimalCritico
import com.pandazoo.application.puerto.Aviso
import com.pandazoo.application.puerto.AvisoRepositorio
import com.pandazoo.application.puerto.BeneficioCiclo
import com.pandazoo.application.puerto.BeneficioRepositorio
import com.pandazoo.application.puerto.CicloRepositorio
import com.pandazoo.application.puerto.EstadoAnimalCiclo
import com.pandazoo.application.puerto.EstadoMotor
import com.pandazoo.application.puerto.RetencionRepositorio
import com.pandazoo.application.puerto.SeguimientoRepositorio
import com.pandazoo.application.puerto.SeveridadAviso
import com.pandazoo.application.puerto.TipoAviso
import com.pandazoo.application.puerto.VentanasRetencion
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.domain.ciclo.ReglasCiclo
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.modelo.IdPartida

class CicloSql(private val bd: BaseDatos) : CicloRepositorio {
    override fun estadoMotor(idPartida: IdPartida): EstadoMotor? =
        bd.uno(
            "SELECT e.numero_ciclo_ultimo, e.remanente_temporizador, e.estado, p.estado FROM EstadoSimulacion e " +
                "JOIN Partida p ON p.id_partida = e.id_partida WHERE e.id_partida = ?",
            idPartida,
        ) {
            EstadoMotor(
                numeroUltimo = if (it.esNulo(0)) null else it.entero(0),
                remanenteSegundos = it.entero(1),
                partidaActiva = it.texto(3) == "ACTIVA",
                simulacionActiva = it.texto(2) == "ACTIVO",
            )
        }

    override fun animalesVivosAlojados(idPartida: IdPartida): List<AnimalCiclo> =
        bd.leer(
            "SELECT a.id_animal, a.id_especie, a.edad_anios, a.edad_meses, a.edad_dias, a.edad_maxima_anios, a.edad_maxima_meses, " +
                "a.edad_maxima_dias, a.nivel_alimentacion, a.estado_salud, a.satisfaccion_actual, r.probabilidad_enfermedad, " +
                "r.beneficio_por_animal_ciclo, a.ciclos_desnutricion, a.ciclos_enfermedad " +
                "FROM Animal a JOIN Especie s ON s.id_especie = a.id_especie JOIN RangoEspecie r ON r.id_rango = s.id_rango " +
                "WHERE a.id_partida = ? AND a.estado_vital = 'VIVO' AND a.id_espacio IS NOT NULL ORDER BY a.id_animal",
            idPartida,
        ) {
            AnimalCiclo(
                id = it.texto(0),
                idEspecie = it.texto(1),
                edadDias = it.entero(2) * 360 + it.entero(3) * 30 + it.entero(4),
                edadMaximaDias = it.entero(5) * 360 + it.entero(6) * 30 + it.entero(7),
                alimentacion = it.entero(8),
                enfermo = it.texto(9) == "ENFERMO",
                satisfaccion = it.decimal(10),
                probabilidadEnfermedad = it.decimal(11),
                beneficioPorCiclo = Dinero.desdeBd(it.decimal(12)),
                ciclosDesnutricion = it.entero(13),
                ciclosEnfermedad = it.entero(14),
            )
        }

    override fun guardarEstados(idPartida: IdPartida, estados: List<EstadoAnimalCiclo>) {
        for (e in estados) {
            bd.escribir(
                "UPDATE Animal SET edad_anios = ?, edad_meses = ?, edad_dias = ?, nivel_alimentacion = ?, estado_salud = ?, " +
                    "ciclos_desnutricion = ?, ciclos_enfermedad = ? WHERE id_partida = ? AND id_animal = ?",
                e.edadDias / 360, (e.edadDias % 360) / 30, e.edadDias % 30, e.alimentacion,
                if (e.enfermo) "ENFERMO" else "SANO", e.ciclosDesnutricion, e.ciclosEnfermedad, idPartida, e.id,
            )
        }
    }

    override fun registrarCiclo(idPartida: IdPartida, numero: Int, inicio: Long, fin: Long) {
        bd.escribir(
            "INSERT INTO CicloSimulacion (id_partida, numero, inicio, fin, estado) VALUES (?, ?, ?, ?, 'CONFIRMADO')",
            idPartida, numero, inicio, fin,
        )
    }

    override fun actualizarEstadoMotor(idPartida: IdPartida, numero: Int, instante: Long, remanenteSegundos: Int) {
        bd.escribir(
            "UPDATE EstadoSimulacion SET numero_ciclo_ultimo = ?, instante_ultimo_ciclo = ?, remanente_temporizador = ? WHERE id_partida = ?",
            numero, instante, remanenteSegundos, idPartida,
        )
    }

    override fun guardarRemanente(idPartida: IdPartida, segundos: Int) {
        bd.escribir(
            "UPDATE EstadoSimulacion SET remanente_temporizador = ? WHERE id_partida = ?",
            segundos.coerceIn(0, ReglasCiclo.DURACION_CICLO_SEGUNDOS), idPartida,
        )
    }

    override fun registrarBeneficio(idPartida: IdPartida, numeroCiclo: Int, importe: Dinero, clave: String, fecha: Long) {
        bd.escribir(
            "INSERT INTO BeneficioPendiente (id_partida, importe, estado, origen, numero_ciclo, clave_idempotencia, fecha_creacion) " +
                "VALUES (?, ?, 'PENDIENTE', 'CICLO_PERIODICO', ?, ?, ?)",
            idPartida, importe.paraBd(), numeroCiclo, clave, fecha,
        )
    }

    override fun alertasCriticasAbiertas(idPartida: IdPartida): Set<String> =
        bd.leer(
            "SELECT id_entidad FROM Notificacion WHERE id_partida = ? AND tipo = 'CRITICA' AND codigo = 'ALERTA_SALUD' " +
                "AND estado IN ('PENDIENTE', 'VISTA')",
            idPartida,
        ) { it.texto(0) }.toSet()

    override fun abrirAlertasCriticas(idPartida: IdPartida, idsAnimal: Collection<String>, numeroCiclo: Int, fecha: Long) {
        for (id in idsAnimal) {
            bd.escribir(
                "INSERT INTO Notificacion (id_partida, tipo, severidad, codigo, entidad, id_entidad, numero_ciclo, estado, fecha) " +
                    "VALUES (?, 'CRITICA', 'ALTA', 'ALERTA_SALUD', 'ANIMAL', ?, ?, 'PENDIENTE', ?)",
                idPartida, id, numeroCiclo, fecha,
            )
        }
    }

    override fun resolverAlertasCriticas(idPartida: IdPartida, idsAnimal: Collection<String>, numeroCiclo: Int) {
        for (id in idsAnimal) {
            bd.escribir(
                "UPDATE Notificacion SET estado = 'RESUELTA', ciclo_cierre = MAX(numero_ciclo, ?) WHERE id_partida = ? " +
                    "AND tipo = 'CRITICA' AND codigo = 'ALERTA_SALUD' AND id_entidad = ? AND estado IN ('PENDIENTE', 'VISTA')",
                numeroCiclo, idPartida, id,
            )
        }
    }
}

class BeneficioSql(private val bd: BaseDatos) : BeneficioRepositorio {
    override fun pendientes(idPartida: IdPartida): Dinero =
        bd.uno(
            "SELECT COALESCE(SUM(importe), 0) FROM BeneficioPendiente WHERE id_partida = ? AND estado = 'PENDIENTE'",
            idPartida,
        ) { Dinero.desdeBd(it.decimal(0)) } ?: Dinero.CERO

    override fun reclamarTodos(idPartida: IdPartida, fecha: Long): Int {
        bd.escribir(
            "UPDATE BeneficioPendiente SET estado = 'RECLAMADO', fecha_reclamacion = ? WHERE id_partida = ? AND estado = 'PENDIENTE'",
            fecha, idPartida,
        )
        return bd.uno("SELECT changes()") { it.entero(0) } ?: 0
    }

    override fun recientes(idPartida: IdPartida, limite: Int): List<BeneficioCiclo> =
        bd.leer(
            "SELECT numero_ciclo, importe, estado FROM BeneficioPendiente WHERE id_partida = ? " +
                "ORDER BY numero_ciclo DESC, id_beneficio DESC LIMIT ?",
            idPartida, limite,
        ) { BeneficioCiclo(it.entero(0), Dinero.desdeBd(it.decimal(1)), it.texto(2) == "RECLAMADO") }
}

class SeguimientoSql(private val bd: BaseDatos) : SeguimientoRepositorio {
    override fun criticos(idPartida: IdPartida): List<AnimalCritico> =
        bd.leer(
            "SELECT a.id_animal, a.nombre, s.nombre_comun, a.edad_anios, a.nivel_alimentacion, a.estado_salud, " +
                "a.ciclos_desnutricion, a.ciclos_enfermedad FROM Animal a JOIN Especie s ON s.id_especie = a.id_especie " +
                "WHERE a.id_partida = ? AND a.estado_vital = 'VIVO' AND a.id_espacio IS NOT NULL " +
                "AND (a.nivel_alimentacion = 0 OR a.estado_salud = 'ENFERMO') ORDER BY a.nombre, a.id_animal",
            idPartida,
        ) {
            AnimalCritico(
                id = it.texto(0), nombre = it.texto(1), especie = it.texto(2), edadAnios = it.entero(3),
                sinAlimento = it.entero(4) == 0, enfermo = it.texto(5) == "ENFERMO",
                ciclosDesnutricion = it.entero(6), ciclosEnfermedad = it.entero(7),
            )
        }
}

class AvisoSql(private val bd: BaseDatos) : AvisoRepositorio {
    override fun historial(idPartida: IdPartida, severidad: SeveridadAviso?): List<Aviso> =
        if (severidad == null) {
            bd.leer("$SELECT WHERE n.id_partida = ? ORDER BY n.id_notificacion DESC", idPartida) { aviso(it) }
        } else {
            bd.leer("$SELECT WHERE n.id_partida = ? AND n.severidad = ? ORDER BY n.id_notificacion DESC", idPartida, severidad.name) { aviso(it) }
        }

    override fun aviso(idPartida: IdPartida, id: Long): Aviso? =
        bd.uno("$SELECT WHERE n.id_partida = ? AND n.id_notificacion = ?", idPartida, id) { aviso(it) }

    override fun marcarVista(idPartida: IdPartida, id: Long, numeroCiclo: Int): Boolean {
        bd.escribir(
            "UPDATE Notificacion SET estado = 'VISTA', ciclo_cierre = CASE WHEN tipo = 'INFORMATIVA' THEN MAX(numero_ciclo, ?) ELSE ciclo_cierre END " +
                "WHERE id_partida = ? AND id_notificacion = ? AND estado = 'PENDIENTE'",
            numeroCiclo, idPartida, id,
        )
        return (bd.uno("SELECT changes()") { it.entero(0) } ?: 0) > 0
    }

    private fun aviso(f: com.pandazoo.data.db.Fila): Aviso {
        val codigo = f.texto(4)
        val idEntidad = f.textoONulo(6)
        val texto = when (codigo) {
            "ALERTA_SALUD" -> textoAlerta(f)
            "PRESTIGIO_ASCENSO" -> "Has alcanzado el nivel de prestigio ${idEntidad ?: ""}.".replace(" .", ".")
            "ESPECIE_DESBLOQUEADA" -> "Nueva especie disponible: ${f.textoONulo(14) ?: "una especie"}."
            "HABITAT_DESBLOQUEADO" -> "Nuevo hábitat disponible: ${(f.textoONulo(15) ?: "un hábitat").removePrefix("Hábitat ").trim()}."
            "REPRODUCCION_DISPONIBLE" -> "Una pareja puede tener una cría."
            else -> codigo
        }
        return Aviso(
            id = f.largo(0),
            tipo = TipoAviso.valueOf(f.texto(1)),
            severidad = SeveridadAviso.valueOf(f.texto(2)),
            estado = f.texto(3),
            codigo = codigo,
            texto = texto,
            fecha = f.largo(7),
            numeroCiclo = f.entero(8),
            idAnimal = if (codigo == "ALERTA_SALUD") idEntidad else null,
        )
    }

    private fun textoAlerta(f: com.pandazoo.data.db.Fila): String {
        val nombre = f.textoONulo(9) ?: "Un animal"
        if (f.textoONulo(10) != "VIVO") return "$nombre ya no está en el zoo."
        val desnutricion = f.entero(11)
        val enfermedad = f.entero(12)
        val causas = buildList {
            if (desnutricion > 0) add("desnutrición ($desnutricion ${if (desnutricion == 1) "ciclo" else "ciclos"})")
            if (enfermedad > 0) add("enfermedad ($enfermedad ${if (enfermedad == 1) "ciclo" else "ciclos"})")
        }
        if (causas.isEmpty()) return "$nombre ya no tiene problemas de salud."
        val restantes = ReglasCiclo.ciclosRestantes(desnutricion, enfermedad)
        return "Alerta de salud: $nombre, ${causas.joinToString(" y ")}. Quedan $restantes ${if (restantes == 1) "ciclo" else "ciclos"}."
    }

    private companion object {
        const val SELECT =
            "SELECT n.id_notificacion, n.tipo, n.severidad, n.estado, n.codigo, n.entidad, n.id_entidad, n.fecha, n.numero_ciclo, " +
                "a.nombre, a.estado_vital, a.ciclos_desnutricion, a.ciclos_enfermedad, a.nivel_alimentacion, s.nombre_comun, h.nombre " +
                "FROM Notificacion n " +
                "LEFT JOIN Animal a ON n.codigo = 'ALERTA_SALUD' AND a.id_partida = n.id_partida AND a.id_animal = n.id_entidad " +
                "LEFT JOIN Especie s ON n.codigo = 'ESPECIE_DESBLOQUEADA' AND s.id_especie = n.id_entidad " +
                "LEFT JOIN Habitat h ON n.codigo = 'HABITAT_DESBLOQUEADO' AND h.id_partida = n.id_partida AND h.id_habitat = n.id_entidad"
    }
}

class RetencionSql(private val bd: BaseDatos) : RetencionRepositorio {
    override fun purgar(idPartida: IdPartida, numeroCiclo: Int, retencion: VentanasRetencion): Int {
        var total = 0

        fun borrar(sql: String, vararg args: Any?) {
            bd.escribir(sql, *args)
            total += bd.uno("SELECT changes()") { it.entero(0) } ?: 0
        }
        // La clave de idempotencia solo se conserva dentro de su ventana; el registro de auditoría, dentro de la suya.
        bd.escribir(
            "UPDATE AuditoriaEvento SET clave_idempotencia = NULL WHERE id_partida = ? AND numero_ciclo < ? AND clave_idempotencia IS NOT NULL",
            idPartida, numeroCiclo - retencion.idempotencia,
        )
        borrar("DELETE FROM AuditoriaEvento WHERE id_partida = ? AND numero_ciclo < ?", idPartida, numeroCiclo - retencion.auditoria)
        borrar("DELETE FROM MovimientoEconomico WHERE id_partida = ? AND numero_ciclo < ?", idPartida, numeroCiclo - retencion.movimientos)
        borrar(
            "DELETE FROM BeneficioPendiente WHERE id_partida = ? AND estado IN ('RECLAMADO', 'ANULADO') AND numero_ciclo < ?",
            idPartida, numeroCiclo - retencion.beneficiosCerrados,
        )
        borrar(
            "DELETE FROM CicloSimulacion WHERE id_partida = ? AND estado IN ('CONFIRMADO', 'REVERTIDO') AND numero < ?",
            idPartida, numeroCiclo - retencion.ciclos,
        )
        // Avisos: los PENDIENTE nunca se purgan; los cerrados salen por antigüedad de cierre o por exceso de filas.
        borrar(
            "DELETE FROM Notificacion WHERE id_partida = ? AND estado <> 'PENDIENTE' AND ciclo_cierre IS NOT NULL AND ciclo_cierre < ?",
            idPartida, numeroCiclo - retencion.avisosCerrados,
        )
        borrar(
            "DELETE FROM Notificacion WHERE id_partida = ? AND estado <> 'PENDIENTE' AND ciclo_cierre IS NOT NULL AND id_notificacion NOT IN (" +
                "SELECT id_notificacion FROM Notificacion WHERE id_partida = ? AND estado <> 'PENDIENTE' AND ciclo_cierre IS NOT NULL " +
                "ORDER BY id_notificacion DESC LIMIT ?)",
            idPartida, idPartida, retencion.maximoAvisosCerrados,
        )
        return total
    }
}
