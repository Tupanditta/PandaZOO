package com.pandazoo.data.db

import com.pandazoo.application.puerto.NotificadorCambios
import com.pandazoo.application.puerto.Transaccion
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Lectura de una fila de resultados; los índices de columna empiezan en 0. */
class Fila internal constructor(private val s: SentenciaSql) {
    fun texto(i: Int): String = s.getText(i)

    fun textoONulo(i: Int): String? = if (s.isNull(i)) null else s.getText(i)

    fun entero(i: Int): Int = s.getLong(i).toInt()

    fun enteroONulo(i: Int): Int? = if (s.isNull(i)) null else s.getLong(i).toInt()

    fun largo(i: Int): Long = s.getLong(i)

    fun decimal(i: Int): Double = s.getDouble(i)

    fun decimalONulo(i: Int): Double? = if (s.isNull(i)) null else s.getDouble(i)

    fun esNulo(i: Int): Boolean = s.isNull(i)
}

/**
 * Base de datos SQLite de la aplicación, común a Android y a la web (PWA para iOS).
 *
 * El DDL de `EspecificacionBBDD.md` (sección 2) es la fuente de verdad del esquema y se aplica tal cual
 * (`schema/esquema.sql`): no hay una segunda definición que pueda divergir. Las plataformas usan el mismo
 * SQLite (el empaquetado en Android y JVM, sql.js en la web; RNF-16), así que `CHECK`, índices parciales y
 * `WITHOUT ROWID` se comportan igual.
 *
 * Una sola conexión protegida por un candado reentrante. Implementa [Transaccion] (una operación = una
 * transacción `BEGIN IMMEDIATE`, RF-45) y [NotificadorCambios] (emite tras cada confirmación para que la
 * interfaz se repinte sin sondeo, RES-10).
 */
class BaseDatos private constructor(private val conexion: ConexionSql) : Transaccion, NotificadorCambios {
    private val candado = Candado()
    private var profundidad = 0

    private val emisor = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val cambios: Flow<Unit> get() = emisor

    fun <T> leer(sql: String, vararg args: Any?, mapa: (Fila) -> T): List<T> =
        candado.conCandado {
            conexion.preparar(sql).usar { s ->
                enlazar(s, args)
                val filas = ArrayList<T>()
                val fila = Fila(s)
                while (s.step()) filas += mapa(fila)
                filas
            }
        }

    fun <T> uno(sql: String, vararg args: Any?, mapa: (Fila) -> T): T? =
        candado.conCandado {
            conexion.preparar(sql).usar { s ->
                enlazar(s, args)
                if (s.step()) mapa(Fila(s)) else null
            }
        }

    fun escribir(sql: String, vararg args: Any?) {
        val autoconfirmada = candado.conCandado {
            conexion.preparar(sql).usar { s ->
                enlazar(s, args)
                while (s.step()) {
                    // Las sentencias de escritura no devuelven filas.
                }
            }
            profundidad == 0
        }
        // Fuera de una transacción la sentencia se confirma sola; el motor de la web debe guardar esa escritura también.
        if (autoconfirmada) conexion.alConfirmar()
    }

    override fun <T> ejecutar(bloque: () -> T): T {
        var externa = false
        val resultado = candado.conCandado {
            if (profundidad > 0) return@conCandado bloque()
            externa = true
            conexion.ejecutar("BEGIN IMMEDIATE")
            profundidad++
            try {
                val r = bloque()
                conexion.ejecutar("COMMIT")
                r
            } catch (e: Throwable) {
                runCatching { conexion.ejecutar("ROLLBACK") }
                throw e
            } finally {
                profundidad--
            }
        }
        // Solo la transacción más externa notifica; una anidada se confirma con ella.
        if (externa) {
            conexion.alConfirmar()
            emisor.tryEmit(Unit)
        }
        return resultado
    }

    fun cerrar() = conexion.cerrar()

    /** Identificador de la partida activa, si existe alguna. Es un dato técnico, nunca para la interfaz. */
    fun partidaActiva(): String? =
        uno("SELECT id_partida FROM Partida WHERE estado = 'ACTIVA' ORDER BY id_partida LIMIT 1") { it.texto(0) }

    private fun enlazar(s: SentenciaSql, args: Array<out Any?>) {
        args.forEachIndexed { i, a ->
            val p = i + 1
            when (a) {
                null -> s.bindNull(p)
                is String -> s.bindText(p, a)
                is Int -> s.bindLong(p, a.toLong())
                is Long -> s.bindLong(p, a)
                is Double -> s.bindDouble(p, a)
                is Boolean -> s.bindLong(p, if (a) 1 else 0)
                else -> error("Tipo de parámetro no soportado: ${a::class.simpleName}")
            }
        }
    }

    companion object {
        /**
         * Versión 2: la partida nueva empieza con 2000 monedas. Las partidas creadas con la versión 1 reciben
         * 1000 monedas de compensación una sola vez, al migrar (`migrarDe1a2`). Versión 3 (Sprint N): niveles de animal en
         * lugar de edad, beneficio y venta según nivel (`migrarDe2a3`). Versión 4: orden de cumplimiento de las misiones
         * (`migrarDe3a4`). Versión 5: beneficio base por animal a la mitad (`migrarDe4a5`). Versión 6: desbloqueo de especies
         * por prestigio reajustado y misión de prestigio hasta el prestigio 100 (`migrarDe5a6`).
         * Versión 7: espacios más caros, algo más de probabilidad de enfermar y sin espacios dados de baja (`migrarDe6a7`).
         * Versión 8: desbloqueo de especies repartido entre rangos (`migrarDe7a8`).
         * Versión 9: reinicio total de los datos (regalo de bienvenida, primer espacio de 6 gratis, sin espacios de 2 ni 4)
         * (`migrarDe8a9`). Versión 10: mejor amigo recíproco (`migrarDe9a10`).
         * Versión 11: cada espacio guarda lo que costó, para devolverlo al venderlo (`migrarDe10a11`).
         * Versión 12: espacios de 25 y 30 animales (`migrarDe11a12`).
         */
        const val VERSION = 12

        /** Compensación única a las partidas creadas con la versión 1 (que empezaban con 1000 monedas). */
        const val COMPENSACION_V2 = 1000.0

        /** Base de datos en memoria; desaparece al cerrarla (tests). */
        fun enMemoria(): BaseDatos = abrir(":memory:", archivo = false)

        /** Base de datos en el fichero indicado por el contenedor de plataforma. */
        fun enArchivo(ruta: String): BaseDatos = abrir(ruta, archivo = true)

        /** Como [enArchivo], pero migra solo hasta [version] (tests de migraciones anteriores al reinicio de la versión 9). */
        internal fun enArchivoMigrandoHasta(ruta: String, version: Int): BaseDatos =
            sobre(abrirConexion(ruta), archivo = true, hasta = version)

        private fun abrir(nombre: String, archivo: Boolean): BaseDatos = sobre(abrirConexion(nombre), archivo)

        /** Base de datos sobre una conexión ya abierta (la web la abre tras cargar el motor y los datos guardados). */
        internal fun sobre(conexion: ConexionSql, archivo: Boolean, hasta: Int = VERSION): BaseDatos {
            val bd = BaseDatos(conexion)
            bd.configurar(archivo, hasta)
            return bd
        }
    }

    private fun configurar(archivo: Boolean, hasta: Int) {
        val nuevo = (uno("PRAGMA user_version") { it.entero(0) } ?: 0) == 0
        if (nuevo) {
            // Debe fijarse antes de crear la primera tabla y antes de activar el modo WAL.
            conexion.ejecutar("PRAGMA auto_vacuum = INCREMENTAL")
        }
        if (archivo && conexion.admiteWal) uno("PRAGMA journal_mode = WAL") { it.texto(0) }
        conexion.ejecutar("PRAGMA foreign_keys = ON")
        if (nuevo) {
            ejecutar {
                EsquemaGenerado.sentencias.forEach { conexion.ejecutar(it) }
                conexion.ejecutar("PRAGMA user_version = $VERSION")
            }
        } else {
            val actual = uno("PRAGMA user_version") { it.entero(0) }
            if (actual == 1) migrarDe1a2()
            if ((uno("PRAGMA user_version") { it.entero(0) }) == 2) migrarDe2a3()
            if ((uno("PRAGMA user_version") { it.entero(0) }) == 3) migrarDe3a4()
            if ((uno("PRAGMA user_version") { it.entero(0) }) == 4) migrarDe4a5()
            if ((uno("PRAGMA user_version") { it.entero(0) }) == 5) migrarDe5a6()
            if ((uno("PRAGMA user_version") { it.entero(0) }) == 6) migrarDe6a7()
            if ((uno("PRAGMA user_version") { it.entero(0) }) == 7) migrarDe7a8()
            if (hasta >= 9 && (uno("PRAGMA user_version") { it.entero(0) }) == 8) migrarDe8a9()
            if (hasta >= 10 && (uno("PRAGMA user_version") { it.entero(0) }) == 9) migrarDe9a10()
            if (hasta >= 11 && (uno("PRAGMA user_version") { it.entero(0) }) == 10) migrarDe10a11()
            if (hasta >= 12 && (uno("PRAGMA user_version") { it.entero(0) }) == 11) migrarDe11a12()
            check((uno("PRAGMA user_version") { it.entero(0) }) == hasta) { "Versión de base de datos no soportada" }
        }
    }

    /** Suma 1000 monedas a cada partida existente y lo registra como ingreso; atómico y de una sola vez. */
    internal fun migrarDe1a2() {
        ejecutar {
            escribir(
                "INSERT INTO MovimientoEconomico (id_partida, tipo, importe, origen, numero_ciclo, fecha) " +
                    "SELECT id_partida, 'INGRESO', ?, 'COMPENSACION_INICIAL', 0, CAST(strftime('%s', 'now') AS INTEGER) FROM Partida",
                COMPENSACION_V2,
            )
            escribir("UPDATE Partida SET fondos = ROUND(fondos + ?, 2)", COMPENSACION_V2)
            conexion.ejecutar("PRAGMA user_version = 2")
        }
    }

    /**
     * Versión 3 (Sprint N): sustituye la edad por el nivel (RF-51), reescribe el beneficio y añade el valor de venta (RF-52).
     * Una sola transacción. Las claves foráneas se desactivan durante la reconstrucción de las tablas (la orden no tiene
     * efecto dentro de una transacción, así que va fuera) y se comprueban con `foreign_key_check` antes de confirmar.
     */
    internal fun migrarDe2a3() {
        conexion.ejecutar("PRAGMA foreign_keys = OFF")
        try {
            ejecutar {
                // (4) Rangos: renombra el beneficio base y añade nivel máximo, multiplicador y valores de venta (versión 1.1).
                reconstruir(
                    "RangoEspecie",
                    "WITH v(id_rango, base, nmax, mult, venta, fmax) AS (VALUES (1, 12.0, 15, 3.0, 200.0, 1.5), " +
                        "(2, 24.0, 29, 3.5, 450.0, 1.75), (3, 40.0, 41, 4.0, 900.0, 2.0), (4, 64.0, 51, 4.5, 1800.0, 2.25), " +
                        "(5, 100.0, 61, 5.0, 4000.0, 2.5)) " +
                        "INSERT INTO RangoEspecie_nueva (rowid, id_rango, nombre, umbral_reproduccion, probabilidad_reproduccion, " +
                        "penalizacion_hijo_reproduccion, probabilidad_enfermedad, beneficio_base_animal_ciclo, nivel_maximo, " +
                        "multiplicador_nivel_maximo, valor_venta_base, factor_venta_nivel_maximo) " +
                        "SELECT r.rowid, r.id_rango, r.nombre, r.umbral_reproduccion, r.probabilidad_reproduccion, " +
                        "r.penalizacion_hijo_reproduccion, r.probabilidad_enfermedad, v.base, v.nmax, v.mult, v.venta, v.fmax " +
                        "FROM RangoEspecie r JOIN v ON v.id_rango = r.id_rango",
                )
                // (3) Especies: desaparece la edad media.
                reconstruir(
                    "Especie",
                    "INSERT INTO Especie_nueva (rowid, id_especie, nombre_comun, id_rango, tope_hermanos, peso_bio, peso_soc, " +
                        "estado_catalogo, nivel_desbloqueo, peso_pareja, peso_amigo, peso_padre, peso_madre, peso_hermanos, " +
                        "peso_afinidad, kg_bambu_por_incremento) " +
                        "SELECT rowid, id_especie, nombre_comun, id_rango, tope_hermanos, peso_bio, peso_soc, estado_catalogo, " +
                        "nivel_desbloqueo, peso_pareja, peso_amigo, peso_padre, peso_madre, peso_hermanos, peso_afinidad, " +
                        "kg_bambu_por_incremento FROM Especie",
                )
                // (1) y (2) Animales: sin edades y con nivel. `c` son los ciclos vividos desde el alta a los 20 años (7200 días).
                reconstruir(
                    "Animal",
                    "INSERT INTO Animal_nueva (rowid, id_animal, id_partida, id_especie, id_espacio, nombre, nivel, ciclos_nivel, " +
                        "nivel_alimentacion, estado_salud, estado_vital, ciclos_desnutricion, ciclos_enfermedad, id_pareja, " +
                        "id_mejor_amigo, id_padre, id_madre, satisfaccion_actual) " +
                        "SELECT a.rid, a.id_animal, a.id_partida, a.id_especie, a.id_espacio, a.nombre, " +
                        "MIN(r.nivel_maximo, 1 + a.c / 72), CASE WHEN 1 + a.c / 72 >= r.nivel_maximo THEN 0 ELSE a.c % 72 END, " +
                        "a.nivel_alimentacion, a.estado_salud, a.estado_vital, a.ciclos_desnutricion, a.ciclos_enfermedad, " +
                        "a.id_pareja, a.id_mejor_amigo, a.id_padre, a.id_madre, a.satisfaccion_actual " +
                        "FROM (SELECT rowid AS rid, *, MAX(0, edad_anios * 360 + edad_meses * 30 + edad_dias - 7200) / 6 AS c FROM Animal) a " +
                        "JOIN Especie s ON s.id_especie = a.id_especie JOIN RangoEspecie r ON r.id_rango = s.id_rango",
                )
                migrarMisionesDeNivel()
                // (6)
                val huerfanas = leer("PRAGMA foreign_key_check") { it.texto(0) }
                check(huerfanas.isEmpty()) { "La migración a la versión 3 dejó referencias rotas: $huerfanas" }
                conexion.ejecutar("PRAGMA user_version = 3")
            }
        } finally {
            conexion.ejecutar("PRAGMA foreign_keys = ON")
        }
    }

    /** Versión 4: las misiones cumplidas recuerdan el orden en que se cumplieron (las ya cumplidas, por identificador). */
    internal fun migrarDe3a4() {
        ejecutar {
            conexion.ejecutar("ALTER TABLE ProgresoMision ADD COLUMN orden_cumplida INTEGER")
            conexion.ejecutar(
                "UPDATE ProgresoMision SET orden_cumplida = (SELECT COUNT(*) FROM ProgresoMision q WHERE q.id_partida = ProgresoMision.id_partida " +
                    "AND q.estado = 'CUMPLIDA' AND q.id_instancia_mision <= ProgresoMision.id_instancia_mision) WHERE estado = 'CUMPLIDA'",
            )
            conexion.ejecutar("PRAGMA user_version = 4")
        }
    }

    /** Versión 5: el beneficio base por animal y ciclo de cada rango baja a la mitad (valores de `valores_y_pesos.json`). */
    internal fun migrarDe4a5() {
        ejecutar {
            conexion.ejecutar("UPDATE RangoEspecie SET beneficio_base_animal_ciclo = beneficio_base_animal_ciclo / 2.0")
            conexion.ejecutar("PRAGMA user_version = 5")
        }
    }

    /**
     * Versión 6: cada especie recupera el prestigio que la desbloquea según `desbloqueos_prestigio` de
     * `valores_y_pesos.json`; se retiran los desbloqueos por prestigio de especies que aún no tocan (las de inicio y
     * las ganadas por recompensa se conservan, igual que los animales ya comprados) y la misión de prestigio llega al 100.
     */
    internal fun migrarDe5a6() {
        ejecutar {
            conexion.ejecutar(
                "UPDATE Especie SET nivel_desbloqueo = CASE id_especie WHEN 'erizo' THEN 0 WHEN 'zarigueya' THEN 0 WHEN 'nutria' THEN 0 WHEN 'capibara' THEN 0 WHEN 'tortuga' THEN 0 WHEN 'cisne' THEN 0 WHEN 'suricata' THEN 2 WHEN 'flamenco' THEN 2 WHEN 'antilope' THEN 4 WHEN 'cebra' THEN 4 WHEN 'mono' THEN 7 WHEN 'lemur' THEN 7 WHEN 'nu' THEN 10 WHEN 'bufalo' THEN 10 WHEN 'delfin' THEN 14 WHEN 'tucan' THEN 18 WHEN 'pinguino' THEN 18 WHEN 'foca' THEN 22 WHEN 'canguro' THEN 22 WHEN 'lobo' THEN 26 WHEN 'koala' THEN 31 WHEN 'reno' THEN 31 WHEN 'oso_pardo' THEN 36 WHEN 'hipopotamo' THEN 41 WHEN 'alce' THEN 46 WHEN 'lince_iberico' THEN 46 WHEN 'pantera' THEN 51 WHEN 'chimpance' THEN 55 WHEN 'leon' THEN 59 WHEN 'elefante' THEN 63 WHEN 'guepardo' THEN 67 WHEN 'leopardo' THEN 71 WHEN 'rinoceronte' THEN 75 WHEN 'jirafa' THEN 79 WHEN 'cocodrilo' THEN 83 WHEN 'oso_polar' THEN 87 WHEN 'gorila' THEN 91 WHEN 'tigre' THEN 95 ELSE nivel_desbloqueo END WHERE estado_catalogo = 'COMERCIAL'",
            )
            conexion.ejecutar(
                "DELETE FROM DesbloqueoEspecie WHERE origen = 'PRESTIGIO' AND id_especie IN " +
                    "(SELECT s.id_especie FROM Especie s WHERE s.nivel_desbloqueo > " +
                    "(SELECT p.prestigio_actual FROM Partida p WHERE p.id_partida = DesbloqueoEspecie.id_partida))",
            )
            conexion.ejecutar(
                "UPDATE ParametroMision SET valor_maximo = 100 WHERE id_mision = 'subir_prestigio' " +
                    "AND nombre_parametro = 'nivel_prestigio_requerido'",
            )
            conexion.ejecutar("PRAGMA user_version = 6")
        }
    }

    /** Versión 7: nuevos costes de espacio y probabilidad de enfermedad por rango (valores de `valores_y_pesos.json`). */
    internal fun migrarDe6a7() {
        ejecutar {
            conexion.ejecutar(
                "UPDATE CosteEspacio SET importe = CASE capacidad_max WHEN 2 THEN 130.0 WHEN 4 THEN 235.0 WHEN 6 THEN 325.0 " +
                    "WHEN 8 THEN 415.0 WHEN 10 THEN 520.0 WHEN 15 THEN 715.0 WHEN 20 THEN 910.0 ELSE importe END",
            )
            conexion.ejecutar(
                "UPDATE RangoEspecie SET probabilidad_enfermedad = CASE id_rango WHEN 1 THEN 0.015 WHEN 2 THEN 0.015 " +
                    "WHEN 3 THEN 0.02 WHEN 4 THEN 0.02 WHEN 5 THEN 0.025 ELSE probabilidad_enfermedad END",
            )
            // Las bajas de espacio ahora borran la fila: se retiran las que quedaron como INACTIVO (sin animales).
            val sinAnimales = "estado = 'INACTIVO' AND NOT EXISTS (SELECT 1 FROM Animal a WHERE a.id_partida = Espacio.id_partida " +
                "AND a.id_espacio = Espacio.id_espacio)"
            conexion.ejecutar(
                "DELETE FROM MetricaSatisfaccion WHERE id_espacio IN (SELECT id_espacio FROM Espacio WHERE $sinAnimales)",
            )
            conexion.ejecutar(
                "DELETE FROM Notificacion WHERE entidad = 'ESPACIO' AND id_entidad IN (SELECT id_espacio FROM Espacio WHERE $sinAnimales)",
            )
            conexion.ejecutar("DELETE FROM Espacio WHERE $sinAnimales")
            conexion.ejecutar("PRAGMA user_version = 7")
        }
    }

    /**
     * Versión 8: el prestigio que desbloquea cada especie vuelve a repartirse mezclando rangos (`desbloqueos_prestigio` de
     * `valores_y_pesos.json`). Se retiran los desbloqueos por prestigio que aún no tocan; los siguientes ciclos añaden los que sí.
     */
    internal fun migrarDe7a8() {
        ejecutar {
            conexion.ejecutar("UPDATE Especie SET nivel_desbloqueo = CASE id_especie WHEN 'erizo' THEN 0 WHEN 'zarigueya' THEN 0 WHEN 'nutria' THEN 0 WHEN 'capibara' THEN 0 WHEN 'tortuga' THEN 0 WHEN 'cisne' THEN 0 WHEN 'suricata' THEN 1 WHEN 'cebra' THEN 2 WHEN 'mono' THEN 3 WHEN 'antilope' THEN 4 WHEN 'bufalo' THEN 5 WHEN 'delfin' THEN 7 WHEN 'lemur' THEN 10 WHEN 'nu' THEN 10 WHEN 'koala' THEN 13 WHEN 'lobo' THEN 16 WHEN 'flamenco' THEN 19 WHEN 'foca' THEN 19 WHEN 'hipopotamo' THEN 22 WHEN 'oso_pardo' THEN 25 WHEN 'canguro' THEN 28 WHEN 'tucan' THEN 31 WHEN 'reno' THEN 31 WHEN 'pantera' THEN 34 WHEN 'elefante' THEN 37 WHEN 'alce' THEN 40 WHEN 'pinguino' THEN 43 WHEN 'lince_iberico' THEN 43 WHEN 'leopardo' THEN 47 WHEN 'chimpance' THEN 51 WHEN 'guepardo' THEN 55 WHEN 'rinoceronte' THEN 59 WHEN 'leon' THEN 63 WHEN 'jirafa' THEN 67 WHEN 'oso_polar' THEN 71 WHEN 'cocodrilo' THEN 75 WHEN 'gorila' THEN 79 WHEN 'tigre' THEN 87 ELSE nivel_desbloqueo END WHERE estado_catalogo = 'COMERCIAL'")
            conexion.ejecutar(
                "DELETE FROM DesbloqueoEspecie WHERE origen = 'PRESTIGIO' AND id_especie IN " +
                    "(SELECT s.id_especie FROM Especie s WHERE s.nivel_desbloqueo > " +
                    "(SELECT p.prestigio_actual FROM Partida p WHERE p.id_partida = DesbloqueoEspecie.id_partida))",
            )
            conexion.ejecutar("PRAGMA user_version = 8")
        }
    }

    /**
     * Versión 9: borra todos los datos (partida, catálogo y avisos) y recrea el esquema vigente. La siguiente apertura de la
     * aplicación crea una partida nueva con su regalo de bienvenida.
     */
    internal fun migrarDe8a9() {
        conexion.ejecutar("PRAGMA foreign_keys = OFF")
        try {
            ejecutar {
                val objetos = leer("SELECT type, name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'") { it.texto(0) to it.texto(1) }
                objetos.filter { it.first == "view" }.forEach { conexion.ejecutar("DROP VIEW IF EXISTS \"${it.second}\"") }
                objetos.filter { it.first == "table" }.forEach { conexion.ejecutar("DROP TABLE IF EXISTS \"${it.second}\"") }
                EsquemaGenerado.sentencias.forEach { conexion.ejecutar(it) }
                conexion.ejecutar("PRAGMA user_version = 9")
            }
        } finally {
            conexion.ejecutar("PRAGMA foreign_keys = ON")
        }
    }

    /**
     * Versión 10: el mejor amigo es recíproco. Si A tiene a B de amigo y B no tiene ninguno, B pasa a tener a A (con varios
     * candidatos gana el de menor identificador); los vínculos que aun así queden sin reciprocidad se quitan.
     */
    /**
     * Los espacios existentes reciben como coste pagado el precio actual de su capacidad: no se conserva cuánto costó
     * cada uno (ni cuál fue el gratuito), así que es la mejor aproximación.
     */
    internal fun migrarDe10a11() {
        ejecutar {
            conexion.ejecutar(
                "ALTER TABLE Espacio ADD COLUMN coste_pagado REAL NOT NULL DEFAULT 0 " +
                    "CHECK (coste_pagado >= 0 AND coste_pagado = ROUND(coste_pagado, 2))",
            )
            conexion.ejecutar(
                "UPDATE Espacio SET coste_pagado = COALESCE((SELECT importe FROM CosteEspacio c WHERE c.capacidad_max = Espacio.capacidad_max), 0)",
            )
            conexion.ejecutar("PRAGMA user_version = 11")
        }
    }

    /** Versión 12: nuevas capacidades de espacio, 25 y 30 animales (valores de `valores_y_pesos.json`). */
    internal fun migrarDe11a12() {
        ejecutar {
            conexion.ejecutar("INSERT OR IGNORE INTO CosteEspacio (capacidad_max, importe) VALUES (25, 1105.0)")
            conexion.ejecutar("INSERT OR IGNORE INTO CosteEspacio (capacidad_max, importe) VALUES (30, 1300.0)")
            conexion.ejecutar("PRAGMA user_version = 12")
        }
    }

    internal fun migrarDe9a10() {
        ejecutar {
            conexion.ejecutar(
                "UPDATE Animal SET id_mejor_amigo = (SELECT MIN(a.id_animal) FROM Animal a WHERE a.id_partida = Animal.id_partida " +
                    "AND a.id_mejor_amigo = Animal.id_animal AND a.estado_vital = 'VIVO') " +
                    "WHERE id_mejor_amigo IS NULL AND estado_vital = 'VIVO' AND EXISTS (SELECT 1 FROM Animal a " +
                    "WHERE a.id_partida = Animal.id_partida AND a.id_mejor_amigo = Animal.id_animal AND a.estado_vital = 'VIVO')",
            )
            conexion.ejecutar(
                "UPDATE Animal SET id_mejor_amigo = NULL WHERE id_mejor_amigo IS NOT NULL AND NOT EXISTS (SELECT 1 FROM Animal b " +
                    "WHERE b.id_partida = Animal.id_partida AND b.id_animal = Animal.id_mejor_amigo AND b.id_mejor_amigo = Animal.id_animal)",
            )
            conexion.ejecutar("PRAGMA user_version = 10")
        }
    }

    /** Crea `<tabla>_nueva` con el DDL vigente, la rellena con [copia], sustituye a la original y recrea sus índices. */
    private fun reconstruir(tabla: String, copia: String) {
        val ddl = EsquemaGenerado.sentencias.first { it.startsWith("CREATE TABLE $tabla ") }
        conexion.ejecutar(ddl.replaceFirst("CREATE TABLE $tabla ", "CREATE TABLE ${tabla}_nueva "))
        conexion.ejecutar(copia)
        conexion.ejecutar("DROP TABLE $tabla")
        conexion.ejecutar("ALTER TABLE ${tabla}_nueva RENAME TO $tabla")
        EsquemaGenerado.sentencias
            .filter { Regex("^CREATE (UNIQUE )?INDEX \\w+ ON $tabla\\(").containsMatchIn(it) }
            .forEach { conexion.ejecutar(it) }
    }

    /**
     * (5) Misiones: `vender_especie_edad` y `vender_edad` pasan a `vender_especie_nivel` y `vender_nivel`, y `edad_requerida` a
     * `nivel_requerido` en las tres misiones de edad. Las instancias `PENDIENTE` vuelven a los valores base con progreso 0;
     * las `CUMPLIDA` (y el resto) conservan estado y recompensa y solo reemplazan sus parámetros por los valores base.
     */
    private fun migrarMisionesDeNivel() {
        val renombres = listOf(
            Triple("vender_especie_edad", "vender_especie_nivel", "Vender un animal de una especie y nivel concretos"),
            Triple("vender_edad", "vender_nivel", "Vender un animal de un nivel concreto"),
        )
        for ((viejo, nuevo, tipo) in renombres) {
            val reemplazo = "REPLACE(id_instancia_mision, '/$viejo/', '/$nuevo/')"
            escribir("UPDATE Mision SET id_mision = ?, tipo_objetivo = ? WHERE id_mision = ?", nuevo, tipo, viejo)
            escribir("UPDATE ParametroMision SET id_mision = ? WHERE id_mision = ?", nuevo, viejo)
            escribir("UPDATE ProgresoMision SET id_mision = ?, id_instancia_mision = $reemplazo WHERE id_mision = ?", nuevo, viejo)
            escribir("UPDATE ParametroProgresoMision SET id_mision = ?, id_instancia_mision = $reemplazo WHERE id_mision = ?", nuevo, viejo)
            escribir("UPDATE RecompensaMision SET id_mision = ?, id_instancia_mision = $reemplazo WHERE id_mision = ?", nuevo, viejo)
        }
        escribir(
            "UPDATE Mision SET tipo_objetivo = 'Hacer subir de nivel a animales de una especie' WHERE id_mision = 'crecer_especie'",
        )
        // Nuevo parámetro de nivel de cada misión: base, operación y límites de la versión 1.1.
        val niveles = listOf(Triple("crecer_especie", 3.0, 2.0), Triple("vender_especie_nivel", 4.0, 1.0), Triple("vender_nivel", 4.0, 1.0))
        for ((mision, base, minimo) in niveles) {
            escribir("DELETE FROM ParametroMision WHERE id_mision = ? AND nombre_parametro = 'edad_requerida'", mision)
            escribir(
                "INSERT INTO ParametroMision (id_mision, nombre_parametro, tipo_valor, valor_base_numero, operacion_progresion, " +
                    "constante_progresion, valor_minimo, valor_maximo) VALUES (?, 'nivel_requerido', 'NUMERICO', ?, 'SUMAR', 1, ?, 15)",
                mision, base, minimo,
            )
            // Todas las instancias pierden el parámetro de edad; las pendientes, además, vuelven a sus valores base.
            escribir("DELETE FROM ParametroProgresoMision WHERE id_mision = ? AND nombre_parametro = 'edad_requerida'", mision)
            escribir(
                "DELETE FROM ParametroProgresoMision WHERE id_mision = ? AND id_instancia_mision IN " +
                    "(SELECT id_instancia_mision FROM ProgresoMision WHERE id_mision = ? AND estado = 'PENDIENTE')",
                mision, mision,
            )
            escribir("UPDATE ProgresoMision SET progreso = 0 WHERE id_mision = ? AND estado = 'PENDIENTE'", mision)
            escribir(
                "INSERT INTO ParametroProgresoMision (id_partida, id_instancia_mision, id_mision, nombre_parametro, valor_numero, valor_texto) " +
                    "SELECT p.id_partida, p.id_instancia_mision, p.id_mision, m.nombre_parametro, m.valor_base_numero, m.valor_base_texto " +
                    "FROM ProgresoMision p JOIN ParametroMision m ON m.id_mision = p.id_mision " +
                    "WHERE p.id_mision = ? AND NOT EXISTS (SELECT 1 FROM ParametroProgresoMision q WHERE q.id_partida = p.id_partida " +
                    "AND q.id_instancia_mision = p.id_instancia_mision AND q.nombre_parametro = m.nombre_parametro)",
                mision,
            )
        }
    }
}
