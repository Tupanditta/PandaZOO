package com.pandazoo.data.repositorio

import com.pandazoo.application.puerto.AuditoriaRepositorio
import com.pandazoo.application.puerto.CatalogoRepositorio
import com.pandazoo.application.puerto.AnimalActivo
import com.pandazoo.application.puerto.EconomiaRepositorio
import com.pandazoo.application.puerto.EspacioBasico
import com.pandazoo.application.puerto.EspacioConHabitat
import com.pandazoo.application.puerto.EspacioSatisfaccion
import com.pandazoo.application.puerto.HabitatBasico
import com.pandazoo.application.puerto.MonitorRepositorio
import com.pandazoo.application.puerto.SatisfaccionRepositorio
import com.pandazoo.application.puerto.InventarioRepositorio
import com.pandazoo.application.puerto.RegistroAuditoria
import com.pandazoo.application.puerto.ResultadoAuditoria
import com.pandazoo.application.puerto.TipoMovimiento
import com.pandazoo.application.puerto.ZooRepositorio
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.data.db.Fila
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.modelo.Espacio
import com.pandazoo.domain.modelo.EstadoEntidad
import com.pandazoo.domain.modelo.Existencia
import com.pandazoo.domain.modelo.Habitat
import com.pandazoo.domain.modelo.IdPartida
import com.pandazoo.domain.modelo.OfertaRecurso
import com.pandazoo.domain.modelo.OpcionCapacidad
import com.pandazoo.domain.modelo.Recurso
import com.pandazoo.domain.modelo.TipoRecurso
import com.pandazoo.domain.satisfaccion.AnimalVivo
import com.pandazoo.domain.satisfaccion.MediasSatisfaccion

class EconomiaSql(private val bd: BaseDatos) : EconomiaRepositorio {
    override fun existePartida(idPartida: IdPartida): Boolean =
        bd.uno("SELECT 1 FROM Partida WHERE id_partida = ?", idPartida) { true } ?: false

    override fun fondos(idPartida: IdPartida): Dinero =
        bd.uno("SELECT fondos FROM Partida WHERE id_partida = ?", idPartida) { Dinero.desdeBd(it.decimal(0)) }
            ?: error("La partida no existe")

    override fun actualizarFondos(idPartida: IdPartida, nuevo: Dinero) =
        bd.escribir("UPDATE Partida SET fondos = ? WHERE id_partida = ?", nuevo.paraBd(), idPartida)

    override fun registrarMovimiento(
        idPartida: IdPartida,
        tipo: TipoMovimiento,
        importe: Dinero,
        origen: String,
        numeroCiclo: Int,
        fecha: Long,
    ) = bd.escribir(
        "INSERT INTO MovimientoEconomico (id_partida, tipo, importe, origen, numero_ciclo, fecha) VALUES (?, ?, ?, ?, ?, ?)",
        idPartida, tipo.name, importe.paraBd(), origen, numeroCiclo, fecha,
    )

    override fun beneficiosPendientes(idPartida: IdPartida): Dinero =
        bd.uno(
            "SELECT COALESCE(SUM(importe), 0) FROM BeneficioPendiente WHERE id_partida = ? AND estado = 'PENDIENTE'",
            idPartida,
        ) { Dinero.desdeBd(it.decimal(0)) } ?: Dinero.CERO
}

class InventarioSql(private val bd: BaseDatos) : InventarioRepositorio {
    override fun existencias(idPartida: IdPartida): List<Existencia> {
        val alimentos = bd.leer(
            "SELECT a.id_alimento, a.nombre, i.cantidad FROM InventarioAlimento i " +
                "JOIN Alimento a ON a.id_alimento = i.id_alimento WHERE i.id_partida = ? ORDER BY a.nombre",
            idPartida,
        ) { Existencia(Recurso(TipoRecurso.ALIMENTO, it.texto(0)), it.texto(1), it.entero(2)) }
        val insumos = bd.leer(
            "SELECT m.id_insumo, m.nombre, i.cantidad FROM InventarioMedico i " +
                "JOIN InsumoMedico m ON m.id_insumo = i.id_insumo WHERE i.id_partida = ? ORDER BY m.nombre",
            idPartida,
        ) { Existencia(Recurso(TipoRecurso.INSUMO, it.texto(0)), it.texto(1), it.entero(2)) }
        return alimentos + insumos
    }

    override fun cantidad(idPartida: IdPartida, recurso: Recurso): Int? {
        val (tabla, columna) = tablaDe(recurso)
        return bd.uno("SELECT cantidad FROM $tabla WHERE id_partida = ? AND $columna = ?", idPartida, recurso.codigo) {
            it.entero(0)
        }
    }

    override fun incrementar(idPartida: IdPartida, recurso: Recurso, cantidad: Int) {
        val (tabla, columna) = tablaDe(recurso)
        val actual = cantidad(idPartida, recurso)
        val nuevo = (actual ?: 0).toLong() + cantidad
        require(nuevo >= 0) { "El inventario no puede quedar negativo" }
        if (actual == null) {
            bd.escribir("INSERT INTO $tabla (id_partida, $columna, cantidad) VALUES (?, ?, ?)", idPartida, recurso.codigo, nuevo)
        } else {
            bd.escribir("UPDATE $tabla SET cantidad = ? WHERE id_partida = ? AND $columna = ?", nuevo, idPartida, recurso.codigo)
        }
    }

    private fun tablaDe(r: Recurso) =
        when (r.tipo) {
            TipoRecurso.ALIMENTO -> "InventarioAlimento" to "id_alimento"
            TipoRecurso.INSUMO -> "InventarioMedico" to "id_insumo"
        }
}

class CatalogoSql(private val bd: BaseDatos) : CatalogoRepositorio {
    override fun recursosElegibles(): List<OfertaRecurso> {
        // El bambú es elegible si alguna especie del catálogo lo demanda; el insumo médico es universal.
        val alimentos = bd.leer(
            "SELECT a.id_alimento, a.nombre, c.importe_por_kilogramo FROM Alimento a " +
                "LEFT JOIN CosteAlimento c ON c.id_alimento = a.id_alimento " +
                "WHERE EXISTS (SELECT 1 FROM Especie WHERE kg_bambu_por_incremento > 0) ORDER BY a.nombre",
        ) { OfertaRecurso(Recurso(TipoRecurso.ALIMENTO, it.texto(0)), it.texto(1), precio(it, 2)) }
        val insumos = bd.leer(
            "SELECT m.id_insumo, m.nombre, c.importe_por_unidad FROM InsumoMedico m " +
                "LEFT JOIN CosteInsumo c ON c.id_insumo = m.id_insumo ORDER BY m.nombre",
        ) { OfertaRecurso(Recurso(TipoRecurso.INSUMO, it.texto(0)), it.texto(1), precio(it, 2)) }
        return alimentos + insumos
    }

    override fun oferta(recurso: Recurso): OfertaRecurso? = recursosElegibles().firstOrNull { it.recurso == recurso }

    override fun opcionesCapacidad(): List<OpcionCapacidad> =
        bd.leer("SELECT capacidad_max, importe FROM CosteEspacio ORDER BY capacidad_max") {
            OpcionCapacidad(it.entero(0), Dinero.desdeBd(it.decimal(1)))
        }

    private fun precio(f: Fila, col: Int): Dinero? = if (f.esNulo(col)) null else Dinero.desdeBd(f.decimal(col))
}

class ZooSql(private val bd: BaseDatos) : ZooRepositorio {
    override fun habitats(idPartida: IdPartida): List<Habitat> =
        bd.leer("$SELECT_HABITAT WHERE h.id_partida = ? ORDER BY h.nombre", idPartida) { habitat(it) }

    override fun habitat(idPartida: IdPartida, idHabitat: String): Habitat? =
        bd.uno("$SELECT_HABITAT WHERE h.id_partida = ? AND h.id_habitat = ?", idPartida, idHabitat) { habitat(it) }

    override fun espacios(idPartida: IdPartida, idHabitat: String): List<Espacio> =
        bd.leer("$SELECT_ESPACIO WHERE e.id_partida = ? AND e.id_habitat = ? ORDER BY e.nombre", idPartida, idHabitat) {
            espacio(it)
        }

    override fun espacio(idPartida: IdPartida, idEspacio: String): Espacio? =
        bd.uno("$SELECT_ESPACIO WHERE e.id_partida = ? AND e.id_espacio = ?", idPartida, idEspacio) { espacio(it) }

    override fun crearEspacio(idPartida: IdPartida, idEspacio: String, idHabitat: String, nombre: String, capacidadMax: Int) =
        bd.escribir(
            "INSERT INTO Espacio (id_espacio, id_partida, id_habitat, nombre, capacidad_max, estado) VALUES (?, ?, ?, ?, ?, 'ACTIVO')",
            idEspacio, idPartida, idHabitat, nombre, capacidadMax,
        )

    override fun reconfigurarEspacio(idPartida: IdPartida, idEspacio: String, nombre: String, idHabitat: String) =
        bd.escribir(
            "UPDATE Espacio SET nombre = ?, id_habitat = ? WHERE id_partida = ? AND id_espacio = ?",
            nombre, idHabitat, idPartida, idEspacio,
        )

    override fun espaciosConCupo(idPartida: IdPartida): List<EspacioConHabitat> =
        bd.leer(
            "SELECT e.id_espacio, e.id_habitat, e.nombre, e.capacidad_max, e.estado, " +
                "(SELECT COUNT(*) FROM Animal a WHERE a.id_partida = e.id_partida AND a.id_espacio = e.id_espacio " +
                "AND a.estado_vital = 'VIVO') AS ocupacion, h.nombre " +
                "FROM Espacio e JOIN Habitat h ON h.id_partida = e.id_partida AND h.id_habitat = e.id_habitat " +
                "WHERE e.id_partida = ? AND e.estado = 'ACTIVO' AND h.estado = 'ACTIVO' " +
                "AND EXISTS (SELECT 1 FROM DesbloqueoHabitat d WHERE d.id_partida = h.id_partida AND d.id_habitat = h.id_habitat) " +
                "AND ocupacion < e.capacidad_max ORDER BY h.nombre, e.nombre",
            idPartida,
        ) { EspacioConHabitat(espacio(it), it.texto(6)) }

    override fun tieneDependencias(idPartida: IdPartida, idEspacio: String): Boolean {
        val aviso = bd.uno(
            "SELECT 1 FROM Notificacion WHERE id_partida = ? AND entidad = 'ESPACIO' AND id_entidad = ? " +
                "AND estado IN ('PENDIENTE', 'VISTA') LIMIT 1",
            idPartida, idEspacio,
        ) { true } ?: false
        val concepcion = bd.uno(
            "SELECT 1 FROM ReproduccionCiclo r JOIN Animal a ON a.id_partida = r.id_partida " +
                "AND (a.id_animal = r.id_animal_a OR a.id_animal = r.id_animal_b) " +
                "WHERE r.id_partida = ? AND r.estado = 'CONCEBIDA' AND a.id_espacio = ? AND a.estado_vital = 'VIVO' LIMIT 1",
            idPartida, idEspacio,
        ) { true } ?: false
        return aviso || concepcion
    }

    override fun darDeBaja(idPartida: IdPartida, idEspacio: String) =
        bd.escribir("UPDATE Espacio SET estado = 'INACTIVO' WHERE id_partida = ? AND id_espacio = ?", idPartida, idEspacio)

    private fun habitat(f: Fila) =
        Habitat(f.texto(0), f.texto(1), f.texto(2), EstadoEntidad.valueOf(f.texto(3)), f.entero(4) == 1)

    private fun espacio(f: Fila) =
        Espacio(f.texto(0), f.texto(1), f.texto(2), f.entero(3), EstadoEntidad.valueOf(f.texto(4)), f.entero(5))

    private companion object {
        const val SELECT_HABITAT =
            "SELECT h.id_habitat, h.codigo, h.nombre, h.estado, " +
                "EXISTS (SELECT 1 FROM DesbloqueoHabitat d WHERE d.id_partida = h.id_partida AND d.id_habitat = h.id_habitat) " +
                "FROM Habitat h"

        const val SELECT_ESPACIO =
            "SELECT e.id_espacio, e.id_habitat, e.nombre, e.capacidad_max, e.estado, " +
                "(SELECT COUNT(*) FROM Animal a WHERE a.id_partida = e.id_partida AND a.id_espacio = e.id_espacio " +
                "AND a.estado_vital = 'VIVO') FROM Espacio e"
    }
}

class AuditoriaSql(private val bd: BaseDatos) : AuditoriaRepositorio {
    override fun existePartida(idPartida: IdPartida): Boolean =
        bd.uno("SELECT 1 FROM Partida WHERE id_partida = ?", idPartida) { true } ?: false

    override fun confirmadoPorClave(idPartida: IdPartida, clave: String): RegistroAuditoria? =
        bd.uno(
            "SELECT tipo, entidad, id_entidad, origen, resultado, numero_ciclo, clave_idempotencia, fecha, detalle " +
                "FROM AuditoriaEvento WHERE id_partida = ? AND clave_idempotencia = ? AND resultado = 'CONFIRMADO'",
            idPartida, clave,
        ) {
            RegistroAuditoria(
                idPartida, it.texto(0), it.texto(1), it.textoONulo(2), it.texto(3),
                ResultadoAuditoria.valueOf(it.texto(4)), it.entero(5), it.textoONulo(6), it.largo(7), it.texto(8),
            )
        }

    override fun registrar(registro: RegistroAuditoria) =
        bd.escribir(
            "INSERT INTO AuditoriaEvento (id_partida, tipo, entidad, id_entidad, origen, resultado, numero_ciclo, " +
                "clave_idempotencia, fecha, detalle) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            registro.idPartida, registro.tipo, registro.entidad, registro.idEntidad, registro.origen,
            registro.resultado.name, registro.numeroCiclo, registro.claveIdempotencia, registro.fecha, registro.detalle,
        )
}

class SatisfaccionSql(private val bd: BaseDatos) : SatisfaccionRepositorio {
    override fun espacios(idPartida: IdPartida): List<EspacioSatisfaccion> =
        bd.leer(
            "SELECT e.id_espacio, e.id_habitat, h.id_bioma FROM Espacio e " +
                "JOIN Habitat h ON h.id_partida = e.id_partida AND h.id_habitat = e.id_habitat WHERE e.id_partida = ?",
            idPartida,
        ) { EspacioSatisfaccion(it.texto(0), it.texto(1), it.texto(2)) }

    override fun animalesVivosAlojados(idPartida: IdPartida): List<AnimalVivo> =
        bd.leer(
            "SELECT id_animal, id_especie, id_espacio, nivel_alimentacion, estado_salud, id_pareja, id_mejor_amigo, " +
                "id_padre, id_madre FROM Animal WHERE id_partida = ? AND estado_vital = 'VIVO' AND id_espacio IS NOT NULL",
            idPartida,
        ) {
            AnimalVivo(
                id = it.texto(0),
                idEspecie = it.texto(1),
                idEspacio = it.texto(2),
                nivelAlimentacion = it.entero(3),
                enfermo = it.texto(4) == "ENFERMO",
                idPareja = it.textoONulo(5),
                idMejorAmigo = it.textoONulo(6),
                idPadre = it.textoONulo(7),
                idMadre = it.textoONulo(8),
            )
        }

    override fun satisfaccionGuardada(idPartida: IdPartida): Map<String, Double> =
        bd.leer(
            "SELECT id_animal, satisfaccion_actual FROM Animal WHERE id_partida = ? AND estado_vital = 'VIVO' AND id_espacio IS NOT NULL",
            idPartida,
        ) { it.texto(0) to it.decimal(1) }.toMap()

    override fun guardarSatisfaccionAnimales(idPartida: IdPartida, valores: Map<String, Double>) {
        valores.forEach { (id, valor) ->
            bd.escribir("UPDATE Animal SET satisfaccion_actual = ? WHERE id_partida = ? AND id_animal = ?", valor, idPartida, id)
        }
    }

    override fun reemplazarMetricas(idPartida: IdPartida, medias: MediasSatisfaccion, fecha: Long) {
        bd.escribir("DELETE FROM MetricaSatisfaccion WHERE id_partida = ? AND nivel IN ('ESPACIO', 'HABITAT')", idPartida)
        medias.espacios.forEach { (id, valor) ->
            bd.escribir(
                "INSERT INTO MetricaSatisfaccion (id_partida, nivel, id_espacio, valor, fecha) VALUES (?, 'ESPACIO', ?, ?, ?)",
                idPartida, id, valor, fecha,
            )
        }
        medias.habitats.forEach { (id, valor) ->
            bd.escribir(
                "INSERT INTO MetricaSatisfaccion (id_partida, nivel, id_habitat, valor, fecha) VALUES (?, 'HABITAT', ?, ?, ?)",
                idPartida, id, valor, fecha,
            )
        }
        bd.escribir(
            "INSERT INTO MetricaSatisfaccion (id_partida, nivel, valor, fecha) VALUES (?, 'PARTIDA', ?, ?) " +
                "ON CONFLICT(id_partida) WHERE nivel = 'PARTIDA' DO UPDATE SET valor = excluded.valor, fecha = excluded.fecha",
            idPartida, medias.parque, fecha,
        )
    }
}

class MonitorSql(private val bd: BaseDatos) : MonitorRepositorio {
    override fun existePartida(idPartida: IdPartida): Boolean =
        bd.uno("SELECT 1 FROM Partida WHERE id_partida = ?", idPartida) { true } ?: false

    override fun satisfaccionParque(idPartida: IdPartida): Double? =
        bd.uno("SELECT valor FROM MetricaSatisfaccion WHERE id_partida = ? AND nivel = 'PARTIDA'", idPartida) { it.decimal(0) }

    override fun satisfaccionHabitats(idPartida: IdPartida): Map<String, Double> =
        bd.leer("SELECT id_habitat, valor FROM MetricaSatisfaccion WHERE id_partida = ? AND nivel = 'HABITAT'", idPartida) {
            it.texto(0) to it.decimal(1)
        }.toMap()

    override fun satisfaccionEspacios(idPartida: IdPartida): Map<String, Double> =
        bd.leer("SELECT id_espacio, valor FROM MetricaSatisfaccion WHERE id_partida = ? AND nivel = 'ESPACIO'", idPartida) {
            it.texto(0) to it.decimal(1)
        }.toMap()

    override fun habitats(idPartida: IdPartida): List<HabitatBasico> =
        bd.leer("SELECT id_habitat, nombre FROM Habitat WHERE id_partida = ? ORDER BY nombre", idPartida) {
            HabitatBasico(it.texto(0), it.texto(1))
        }

    override fun espacios(idPartida: IdPartida): List<EspacioBasico> =
        bd.leer(
            "SELECT id_espacio, id_habitat, nombre FROM Espacio WHERE id_partida = ? AND estado = 'ACTIVO' ORDER BY nombre",
            idPartida,
        ) { EspacioBasico(it.texto(0), it.texto(1), it.texto(2)) }

    override fun animales(idPartida: IdPartida): List<AnimalActivo> =
        bd.leer(
            "SELECT a.id_espacio, a.nombre, s.nombre_comun, a.satisfaccion_actual, a.edad_anios FROM Animal a " +
                "JOIN Especie s ON s.id_especie = a.id_especie " +
                "WHERE a.id_partida = ? AND a.estado_vital = 'VIVO' AND a.id_espacio IS NOT NULL ORDER BY a.nombre, a.rowid",
            idPartida,
        ) { AnimalActivo(it.texto(0), it.texto(1), it.texto(2), it.decimal(3), it.entero(4)) }
}
