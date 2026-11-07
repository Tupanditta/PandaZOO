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
         * lugar de edad, beneficio y venta según nivel (`migrarDe2a3`).
         */
        const val VERSION = 3

        /** Compensación única a las partidas creadas con la versión 1 (que empezaban con 1000 monedas). */
        const val COMPENSACION_V2 = 1000.0

        /** Base de datos en memoria; desaparece al cerrarla (tests). */
        fun enMemoria(): BaseDatos = abrir(":memory:", archivo = false)

        /** Base de datos en el fichero indicado por el contenedor de plataforma. */
        fun enArchivo(ruta: String): BaseDatos = abrir(ruta, archivo = true)

        private fun abrir(nombre: String, archivo: Boolean): BaseDatos = sobre(abrirConexion(nombre), archivo)

        /** Base de datos sobre una conexión ya abierta (la web la abre tras cargar el motor y los datos guardados). */
        internal fun sobre(conexion: ConexionSql, archivo: Boolean): BaseDatos {
            val bd = BaseDatos(conexion)
            bd.configurar(archivo)
            return bd
        }
    }

    private fun configurar(archivo: Boolean) {
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
            check((uno("PRAGMA user_version") { it.entero(0) }) == VERSION) { "Versión de base de datos no soportada" }
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
