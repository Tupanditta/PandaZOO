package com.pandazoo.data.repositorio

import com.pandazoo.application.puerto.AnimalFicha
import com.pandazoo.application.puerto.AnimalRepositorio
import com.pandazoo.application.puerto.AnimalResumen
import com.pandazoo.application.puerto.EspecieTienda
import com.pandazoo.application.puerto.EstadoTienda
import com.pandazoo.application.puerto.NuevoAnimal
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.data.db.Fila
import com.pandazoo.domain.animal.ParametrosNivelRango
import com.pandazoo.domain.animal.ReglasNivel
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

    override fun familiares(idPartida: IdPartida, idAnimal: String): Set<String> =
        bd.leer(
            "SELECT y.id_animal FROM Animal x JOIN Animal y ON y.id_partida = x.id_partida AND y.id_animal <> x.id_animal " +
                "WHERE x.id_partida = ? AND x.id_animal = ? AND (" + CONDICION_FAMILIA + ")",
            idPartida, idAnimal,
        ) { it.texto(0) }.toSet()

    override fun familiaresDeCria(idPartida: IdPartida, idPadre: String, idMadre: String): Set<String> =
        (
            listOf(idPadre, idMadre) + bd.leer(
                "SELECT id_animal FROM Animal WHERE id_partida = ? AND (id_padre IN (?, ?) OR id_madre IN (?, ?))",
                idPartida, idPadre, idMadre, idPadre, idMadre,
            ) { it.texto(0) }
            ).toSet()

    override fun ficha(idPartida: IdPartida, idAnimal: String): AnimalFicha? {
        val base = bd.uno(
            "SELECT a.id_animal, a.nombre, s.nombre_comun, r.nombre, a.estado_vital, a.id_espacio, e.nombre, h.nombre, " +
                "a.nivel, a.ciclos_nivel, a.nivel_alimentacion, a.estado_salud, a.satisfaccion_actual, a.id_pareja, p.nombre, " +
                "a.id_mejor_amigo, m.nombre, $COLUMNAS_PARAMETROS, a.id_especie " +
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
                nivel = f.entero(8), ciclosNivel = f.entero(9), parametros = parametros(f, 17),
                alimentacion = f.entero(10), enfermo = f.texto(11) == "ENFERMO", satisfaccion = f.decimal(12),
                idPareja = f.textoONulo(13), nombrePareja = f.textoONulo(14),
                idMejorAmigo = f.textoONulo(15), nombreMejorAmigo = f.textoONulo(16),
                habilidades = emptyList(), rasgos = emptyList(), idEspecie = f.texto(22),
            )
        } ?: return null
        val habilidades = bd.leer("SELECT habilidad FROM HabilidadSocial WHERE id_animal = ? ORDER BY habilidad", idAnimal) { it.texto(0) }
        val rasgos = bd.leer("SELECT rasgo FROM RasgoPersonalidad WHERE id_animal = ? ORDER BY rasgo", idAnimal) { it.texto(0) }
        return base.copy(habilidades = habilidades, rasgos = rasgos)
    }

    override fun crear(idPartida: IdPartida, nuevo: NuevoAnimal) {
        bd.escribir(
            "INSERT INTO Animal (id_animal, id_partida, id_especie, id_espacio, nombre, nivel, ciclos_nivel, nivel_alimentacion, " +
                "estado_salud, estado_vital, id_pareja, id_mejor_amigo, id_padre, id_madre, satisfaccion_actual) " +
                "VALUES (?, ?, ?, ?, ?, ?, 0, 100, 'SANO', 'VIVO', ?, ?, ?, ?, 0)",
            nuevo.id, idPartida, nuevo.idEspecie, nuevo.idEspacio, nuevo.nombre, ReglasNivel.NIVEL_INICIAL,
            nuevo.idPareja, nuevo.idMejorAmigo, nuevo.idPadre, nuevo.idMadre,
        )
        guardarFicha(nuevo.id, nuevo.habilidades, nuevo.rasgos)
        if (nuevo.idPareja != null) {
            bd.escribir("UPDATE Animal SET id_pareja = ? WHERE id_partida = ? AND id_animal = ?", nuevo.id, idPartida, nuevo.idPareja)
        }
        if (nuevo.idMejorAmigo != null) {
            bd.escribir("UPDATE Animal SET id_mejor_amigo = ? WHERE id_partida = ? AND id_animal = ?", nuevo.id, idPartida, nuevo.idMejorAmigo)
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

    override fun divorciar(idPartida: IdPartida, idAnimal: String, idPareja: String) {
        fijarPareja(idPartida, idAnimal, null)
        // Los hijos comunes (de cualquier estado vital) pasan a ser individuos sin padres.
        bd.escribir(
            "UPDATE Animal SET id_padre = NULL, id_madre = NULL WHERE id_partida = ? AND " +
                "((id_padre = ? AND id_madre = ?) OR (id_padre = ? AND id_madre = ?))",
            idPartida, idAnimal, idPareja, idPareja, idAnimal,
        )
    }

    override fun fijarMejorAmigo(idPartida: IdPartida, idAnimal: String, idAmigo: String?) {
        // Recíproco: libera a quien tuviera a este animal como amigo y, si procede, enlaza a ambos.
        bd.escribir("UPDATE Animal SET id_mejor_amigo = NULL WHERE id_partida = ? AND id_mejor_amigo = ?", idPartida, idAnimal)
        bd.escribir("UPDATE Animal SET id_mejor_amigo = ? WHERE id_partida = ? AND id_animal = ?", idAmigo, idPartida, idAnimal)
        if (idAmigo != null) {
            bd.escribir("UPDATE Animal SET id_mejor_amigo = ? WHERE id_partida = ? AND id_animal = ?", idAnimal, idPartida, idAmigo)
        }
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
            f.entero(6) == 1 -> EstadoTienda.COMERCIAL
            else -> EstadoTienda.BLOQUEADA
        }
        return EspecieTienda(f.texto(0), f.texto(1), f.texto(3), estado, Dinero.desdeBd(f.decimal(5)), f.enteroONulo(7))
    }

    private fun resumen(f: Fila) =
        AnimalResumen(
            id = f.texto(0), idEspecie = f.texto(1), especie = f.texto(2), nombre = f.texto(3), idEspacio = f.textoONulo(4),
            nivel = f.entero(5), ciclosNivel = f.entero(6), parametros = parametros(f, 14),
            alimentacion = f.entero(7), enfermo = f.texto(8) == "ENFERMO", vivo = f.texto(9) == "VIVO",
            idPareja = f.textoONulo(10), idMejorAmigo = f.textoONulo(11), satisfaccion = f.decimal(12),
            rango = f.texto(13),
        )

    private fun parametros(f: Fila, desde: Int) =
        ParametrosNivelRango(
            beneficioBase = f.decimal(desde),
            nivelMaximo = f.entero(desde + 1),
            multiplicadorNivelMaximo = f.decimal(desde + 2),
            valorVentaBase = Dinero.desdeBd(f.decimal(desde + 3)),
            factorVentaNivelMaximo = f.decimal(desde + 4),
        )

    private companion object {
        /** x e y son familiares: pareja, padre o madre de uno, o comparten padre o madre (en cualquiera de las dos posiciones). */
        const val CONDICION_FAMILIA =
            "x.id_pareja = y.id_animal OR y.id_pareja = x.id_animal OR x.id_padre = y.id_animal OR x.id_madre = y.id_animal OR " +
                "y.id_padre = x.id_animal OR y.id_madre = x.id_animal OR " +
                "(x.id_padre IS NOT NULL AND (x.id_padre = y.id_padre OR x.id_padre = y.id_madre)) OR " +
                "(x.id_madre IS NOT NULL AND (x.id_madre = y.id_padre OR x.id_madre = y.id_madre))"

        const val COLUMNAS_PARAMETROS =
            "r.beneficio_base_animal_ciclo, r.nivel_maximo, r.multiplicador_nivel_maximo, r.valor_venta_base, r.factor_venta_nivel_maximo"

        const val SELECT_ESPECIE =
            "SELECT s.id_especie, s.nombre_comun, s.id_rango, r.nombre, s.estado_catalogo, c.importe, " +
                "EXISTS (SELECT 1 FROM DesbloqueoEspecie d WHERE d.id_partida = ? AND d.id_especie = s.id_especie), " +
                "s.nivel_desbloqueo " +
                "FROM Especie s JOIN RangoEspecie r ON r.id_rango = s.id_rango JOIN CosteRango c ON c.id_rango = s.id_rango " +
                "WHERE 1 = 1"

        const val SELECT_RESUMEN =
            "SELECT a.id_animal, a.id_especie, s.nombre_comun, a.nombre, a.id_espacio, a.nivel, a.ciclos_nivel, a.nivel_alimentacion, " +
                "a.estado_salud, a.estado_vital, a.id_pareja, a.id_mejor_amigo, a.satisfaccion_actual, r.nombre, $COLUMNAS_PARAMETROS " +
                "FROM Animal a JOIN Especie s ON s.id_especie = a.id_especie " +
                "JOIN RangoEspecie r ON r.id_rango = s.id_rango"
    }
}
