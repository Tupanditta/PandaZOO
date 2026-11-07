package com.pandazoo.data.repositorio

import com.pandazoo.application.puerto.AnimalFicha
import com.pandazoo.application.puerto.AnimalRepositorio
import com.pandazoo.application.puerto.AnimalResumen
import com.pandazoo.application.puerto.EspecieTienda
import com.pandazoo.application.puerto.EstadoTienda
import com.pandazoo.application.puerto.NuevoAnimal
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.data.db.Fila
import com.pandazoo.domain.animal.Edad
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.modelo.IdPartida

class AnimalSql(private val bd: BaseDatos) : AnimalRepositorio {
    override fun especiesTienda(idPartida: IdPartida): List<EspecieTienda> =
        bd.leer("$SELECT_ESPECIE ORDER BY s.id_rango, s.nombre_comun", idPartida) { especie(it) }

    override fun especieTienda(idPartida: IdPartida, idEspecie: String): EspecieTienda? =
        bd.uno("$SELECT_ESPECIE AND s.id_especie = ?", idPartida, idEspecie) { especie(it) }

    override fun animal(idPartida: IdPartida, idAnimal: String): AnimalResumen? =
        bd.uno("$SELECT_RESUMEN WHERE a.id_partida = ? AND a.id_animal = ?", idPartida, idAnimal) { resumen(it) }

    override fun animalesDeEspacio(idPartida: IdPartida, idEspacio: String): List<AnimalResumen> =
        bd.leer(
            "$SELECT_RESUMEN WHERE a.id_partida = ? AND a.id_espacio = ? AND a.estado_vital = 'VIVO' ORDER BY a.nombre, a.rowid",
            idPartida, idEspacio,
        ) { resumen(it) }

    override fun ficha(idPartida: IdPartida, idAnimal: String): AnimalFicha? {
        val base = bd.uno(
            "SELECT a.id_animal, a.nombre, s.nombre_comun, r.nombre, a.estado_vital, a.id_espacio, e.nombre, h.nombre, " +
                "a.edad_anios, a.edad_meses, a.edad_dias, a.edad_maxima_anios, a.edad_maxima_meses, a.edad_maxima_dias, " +
                "a.nivel_alimentacion, a.estado_salud, a.satisfaccion_actual, a.id_pareja, p.nombre, a.id_mejor_amigo, m.nombre " +
                "FROM Animal a " +
                "JOIN Especie s ON s.id_especie = a.id_especie " +
                "JOIN RangoEspecie r ON r.id_rango = s.id_rango " +
                "LEFT JOIN Espacio e ON e.id_partida = a.id_partida AND e.id_espacio = a.id_espacio " +
                "LEFT JOIN Habitat h ON h.id_partida = e.id_partida AND h.id_habitat = e.id_habitat " +
                "LEFT JOIN Animal p ON p.id_partida = a.id_partida AND p.id_animal = a.id_pareja " +
                "LEFT JOIN Animal m ON m.id_partida = a.id_partida AND m.id_animal = a.id_mejor_amigo " +
                "WHERE a.id_partida = ? AND a.id_animal = ?",
            idPartida, idAnimal,
        ) { f ->
            AnimalFicha(
                id = f.texto(0), nombre = f.texto(1), especie = f.texto(2), rango = f.texto(3), estadoVital = f.texto(4),
                idEspacio = f.textoONulo(5), nombreEspacio = f.textoONulo(6), nombreHabitat = f.textoONulo(7),
                edad = Edad(f.entero(8), f.entero(9), f.entero(10)),
                edadMaxima = Edad(f.entero(11), f.entero(12), f.entero(13)),
                alimentacion = f.entero(14), enfermo = f.texto(15) == "ENFERMO", satisfaccion = f.decimal(16),
                idPareja = f.textoONulo(17), nombrePareja = f.textoONulo(18),
                idMejorAmigo = f.textoONulo(19), nombreMejorAmigo = f.textoONulo(20),
                habilidades = emptyList(), rasgos = emptyList(),
            )
        } ?: return null
        val habilidades = bd.leer("SELECT habilidad FROM HabilidadSocial WHERE id_animal = ? ORDER BY habilidad", idAnimal) { it.texto(0) }
        val rasgos = bd.leer("SELECT rasgo FROM RasgoPersonalidad WHERE id_animal = ? ORDER BY rasgo", idAnimal) { it.texto(0) }
        return base.copy(habilidades = habilidades, rasgos = rasgos)
    }

    override fun crear(idPartida: IdPartida, nuevo: NuevoAnimal) {
        val m = nuevo.edadMaxima
        bd.escribir(
            "INSERT INTO Animal (id_animal, id_partida, id_especie, id_espacio, nombre, edad_anios, edad_meses, edad_dias, " +
                "edad_maxima_anios, edad_maxima_meses, edad_maxima_dias, nivel_alimentacion, estado_salud, estado_vital, " +
                "id_pareja, id_mejor_amigo, id_padre, id_madre, satisfaccion_actual) " +
                "VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, 100, 'SANO', 'VIVO', ?, ?, ?, ?, 0)",
            nuevo.id, idPartida, nuevo.idEspecie, nuevo.idEspacio, nuevo.nombre, nuevo.edadInicialAnios, m.anios, m.meses, m.dias,
            nuevo.idPareja, nuevo.idMejorAmigo, nuevo.idPadre, nuevo.idMadre,
        )
        guardarFicha(nuevo.id, nuevo.habilidades, nuevo.rasgos)
        if (nuevo.idPareja != null) {
            bd.escribir("UPDATE Animal SET id_pareja = ? WHERE id_partida = ? AND id_animal = ?", nuevo.id, idPartida, nuevo.idPareja)
        }
    }

    override fun actualizarFicha(idPartida: IdPartida, idAnimal: String, nombre: String, habilidades: List<String>, rasgos: List<String>) {
        bd.escribir("UPDATE Animal SET nombre = ? WHERE id_partida = ? AND id_animal = ?", nombre, idPartida, idAnimal)
        bd.escribir("DELETE FROM HabilidadSocial WHERE id_animal = ?", idAnimal)
        bd.escribir("DELETE FROM RasgoPersonalidad WHERE id_animal = ?", idAnimal)
        guardarFicha(idAnimal, habilidades, rasgos)
    }

    override fun fijarPareja(idPartida: IdPartida, idAnimal: String, idPareja: String?) {
        // Libera a quien tuviera a este animal como pareja (la pareja anterior) y, si procede, enlaza a la nueva.
        bd.escribir("UPDATE Animal SET id_pareja = NULL WHERE id_partida = ? AND id_pareja = ?", idPartida, idAnimal)
        if (idPareja == null) {
            bd.escribir("UPDATE Animal SET id_pareja = NULL WHERE id_partida = ? AND id_animal = ?", idPartida, idAnimal)
            return
        }
        bd.escribir("UPDATE Animal SET id_pareja = ? WHERE id_partida = ? AND id_animal = ?", idPareja, idPartida, idAnimal)
        bd.escribir("UPDATE Animal SET id_pareja = ? WHERE id_partida = ? AND id_animal = ?", idAnimal, idPartida, idPareja)
    }

    override fun fijarMejorAmigo(idPartida: IdPartida, idAnimal: String, idAmigo: String?) {
        bd.escribir("UPDATE Animal SET id_mejor_amigo = ? WHERE id_partida = ? AND id_animal = ?", idAmigo, idPartida, idAnimal)
    }

    override fun mover(idPartida: IdPartida, idAnimal: String, idEspacio: String) {
        bd.escribir("UPDATE Animal SET id_espacio = ? WHERE id_partida = ? AND id_animal = ?", idEspacio, idPartida, idAnimal)
    }

    override fun retirar(idPartida: IdPartida, idAnimal: String, estadoVital: String) {
        require(estadoVital == "VENDIDO" || estadoVital == "MUERTO") { "Estado vital de baja no válido" }
        bd.escribir("UPDATE Animal SET id_pareja = NULL WHERE id_partida = ? AND id_pareja = ?", idPartida, idAnimal)
        bd.escribir("UPDATE Animal SET id_mejor_amigo = NULL WHERE id_partida = ? AND id_mejor_amigo = ?", idPartida, idAnimal)
        bd.escribir(
            "UPDATE Animal SET estado_vital = ?, id_espacio = NULL, id_pareja = NULL, id_mejor_amigo = NULL " +
                "WHERE id_partida = ? AND id_animal = ?",
            estadoVital, idPartida, idAnimal,
        )
        bd.escribir("DELETE FROM HabilidadSocial WHERE id_animal = ?", idAnimal)
        bd.escribir("DELETE FROM RasgoPersonalidad WHERE id_animal = ?", idAnimal)
    }

    override fun espaciosRelacionados(idPartida: IdPartida, idAnimal: String): Set<String> {
        val v = bd.uno(
            "SELECT id_pareja, id_mejor_amigo, id_padre, id_madre FROM Animal WHERE id_partida = ? AND id_animal = ?",
            idPartida, idAnimal,
        ) { listOf(it.textoONulo(0), it.textoONulo(1), it.textoONulo(2), it.textoONulo(3)) } ?: return emptySet()
        return bd.leer(
            "SELECT DISTINCT id_espacio FROM Animal WHERE id_partida = ? AND estado_vital = 'VIVO' AND id_espacio IS NOT NULL " +
                "AND id_animal <> ? AND (id_animal IN (?, ?, ?, ?) OR id_pareja = ? OR id_mejor_amigo = ? " +
                "OR id_padre = ? OR id_madre = ? OR id_padre = ? OR id_madre = ?)",
            idPartida, idAnimal, v[0], v[1], v[2], v[3], idAnimal, idAnimal, idAnimal, idAnimal, v[2], v[3],
        ) { it.texto(0) }.toSet()
    }

    private fun guardarFicha(idAnimal: String, habilidades: List<String>, rasgos: List<String>) {
        habilidades.forEach { bd.escribir("INSERT INTO HabilidadSocial (id_animal, habilidad) VALUES (?, ?)", idAnimal, it) }
        rasgos.forEach { bd.escribir("INSERT INTO RasgoPersonalidad (id_animal, rasgo) VALUES (?, ?)", idAnimal, it) }
    }

    private fun especie(f: Fila): EspecieTienda {
        val estado = when {
            f.texto(4) == "EXCLUSIVA" -> EstadoTienda.EXCLUSIVA
            f.entero(7) == 1 -> EstadoTienda.COMERCIAL
            else -> EstadoTienda.BLOQUEADA
        }
        return EspecieTienda(f.texto(0), f.texto(1), f.texto(3), estado, Dinero.desdeBd(f.decimal(6)), f.entero(5))
    }

    private fun resumen(f: Fila) =
        AnimalResumen(
            id = f.texto(0), idEspecie = f.texto(1), especie = f.texto(2), nombre = f.texto(3), idEspacio = f.textoONulo(4),
            edadAnios = f.entero(5), alimentacion = f.entero(6), enfermo = f.texto(7) == "ENFERMO", vivo = f.texto(8) == "VIVO",
            idPareja = f.textoONulo(9), idMejorAmigo = f.textoONulo(10), satisfaccion = f.decimal(11),
            rango = f.texto(12),
        )

    private companion object {
        const val SELECT_ESPECIE =
            "SELECT s.id_especie, s.nombre_comun, s.id_rango, r.nombre, s.estado_catalogo, s.edad_media_anios, c.importe, " +
                "EXISTS (SELECT 1 FROM DesbloqueoEspecie d WHERE d.id_partida = ? AND d.id_especie = s.id_especie) " +
                "FROM Especie s JOIN RangoEspecie r ON r.id_rango = s.id_rango JOIN CosteRango c ON c.id_rango = s.id_rango " +
                "WHERE 1 = 1"

        const val SELECT_RESUMEN =
            "SELECT a.id_animal, a.id_especie, s.nombre_comun, a.nombre, a.id_espacio, a.edad_anios, a.nivel_alimentacion, " +
                "a.estado_salud, a.estado_vital, a.id_pareja, a.id_mejor_amigo, a.satisfaccion_actual, r.nombre " +
                "FROM Animal a JOIN Especie s ON s.id_especie = a.id_especie " +
                "JOIN RangoEspecie r ON r.id_rango = s.id_rango"
    }
}
