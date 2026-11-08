package com.pandazoo.data

import com.pandazoo.data.db.BaseDatos
import com.pandazoo.data.db.abrirConexion
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * TN.11: migración de la base de datos de la versión 2 a la 3 (Sprint N). El esquema de partida es el del Sprint 9
 * (`esquema_v2.sql`) y los animales cubren los casos calculados a mano de `sprints.md`.
 */
class MigracionV3Test {
    private lateinit var archivo: File

    @BeforeTest
    fun preparar() {
        archivo = File.createTempFile("pandazoo-v2", ".db")
        archivo.delete()
        crearBaseV2(archivo.absolutePath)
    }

    @AfterTest
    fun limpiar() {
        archivo.delete()
        File(archivo.absolutePath + "-wal").delete()
        File(archivo.absolutePath + "-shm").delete()
    }

    private fun crearBaseV2(ruta: String) {
        val ddl = MigracionV3Test::class.java.getResource("/esquema_v2.sql")!!.readText(Charsets.UTF_8)
        val sentencias = ddl.split(Regex("(?m)^;;\\s*$"))
            .map { b -> b.lines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n").trim() }
            .filter { it.isNotEmpty() }
        val c = abrirConexion(ruta)
        c.ejecutar("PRAGMA foreign_keys = ON")
        sentencias.forEach { c.ejecutar(it) }
        DATOS_V2.flatMap { it.split(SEPARADOR) }.forEach { c.ejecutar(it) }
        c.ejecutar("PRAGMA user_version = 2")
        c.cerrar()
    }

    private fun abrir(): BaseDatos = BaseDatos.enArchivoMigrandoHasta(archivo.absolutePath, 8)

    private fun BaseDatos.nivelDe(id: String) =
        uno("SELECT nivel, ciclos_nivel FROM Animal WHERE id_animal = ?", id) { it.entero(0) to it.entero(1) }

    @Test
    fun `TN_11 la migracion deja la version 5 sin referencias rotas`() {
        val bd = abrir()
        try {
            bd.uno("PRAGMA user_version") { it.entero(0) } shouldBe 8
            bd.leer("PRAGMA foreign_key_check") { it.texto(0) } shouldBe emptyList()
            bd.uno("PRAGMA foreign_keys") { it.entero(0) } shouldBe 1
            val columnas = bd.leer("PRAGMA table_info(Animal)") { it.texto(1) }
            columnas.any { it.startsWith("edad") } shouldBe false
            columnas.containsAll(listOf("nivel", "ciclos_nivel")) shouldBe true
            bd.leer("PRAGMA table_info(Especie)") { it.texto(1) }.contains("edad_media_anios") shouldBe false
            bd.leer("PRAGMA table_info(RangoEspecie)") { it.texto(1) }.let {
                it.contains("beneficio_por_animal_ciclo") shouldBe false
                it.containsAll(
                    listOf("beneficio_base_animal_ciclo", "nivel_maximo", "multiplicador_nivel_maximo", "valor_venta_base", "factor_venta_nivel_maximo"),
                ) shouldBe true
            }
        } finally {
            bd.cerrar()
        }
    }

    @Test
    fun `TN_11 el nivel migrado conserva el progreso de los animales comprados`() {
        val bd = abrir()
        try {
            bd.nivelDe("a1") shouldBe (1 to 0) // comprado, 20 años exactos
            bd.nivelDe("a2") shouldBe (2 to 0) // 20 años y 72 ciclos
            bd.nivelDe("a3") shouldBe (1 to 60) // 21 años: 360 días = 60 ciclos
            bd.nivelDe("a4") shouldBe (1 to 0) // cría de 3 años
            bd.nivelDe("a5") shouldBe (15 to 0) // justo en el tope del rango 1 (1008 ciclos)
            bd.nivelDe("a6") shouldBe (15 to 0) // por encima: se limita al tope
            bd.nivelDe("a7") shouldBe (2 to 28) // 100 ciclos: nivel 2 y resto 28
            bd.nivelDe("a9") shouldBe (4 to 0) // rango 3 (tope 41): 20 años + 216 ciclos = 1 + 3
        } finally {
            bd.cerrar()
        }
    }

    @Test
    fun `TN_11 los vinculos genealogia ficha y bajas se conservan`() {
        val bd = abrir()
        try {
            bd.uno("SELECT id_pareja FROM Animal WHERE id_animal = 'a1'") { it.texto(0) } shouldBe "a3"
            bd.uno("SELECT id_padre || '/' || id_madre FROM Animal WHERE id_animal = 'a4'") { it.texto(0) } shouldBe "a1/a3"
            bd.uno("SELECT COUNT(*) FROM RasgoPersonalidad WHERE id_animal = 'a1'") { it.entero(0) } shouldBe 1
            bd.uno("SELECT estado_vital FROM Animal WHERE id_animal = 'a8'") { it.texto(0) } shouldBe "VENDIDO"
            bd.uno("SELECT COUNT(*) FROM Animal") { it.entero(0) } shouldBe 9
            // El orden de inserción (rowid) se mantiene: las consultas desempatan por él.
            bd.leer("SELECT id_animal FROM Animal ORDER BY rowid") { it.texto(0) } shouldBe
                listOf("a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9")
        } finally {
            bd.cerrar()
        }
    }

    @Test
    fun `TN_11 los rangos reciben los valores de la version 1_1 con el beneficio a la mitad`() {
        val bd = abrir()
        try {
            val filas = bd.leer(
                "SELECT id_rango, beneficio_base_animal_ciclo, nivel_maximo, multiplicador_nivel_maximo, valor_venta_base, factor_venta_nivel_maximo " +
                    "FROM RangoEspecie ORDER BY id_rango",
            ) { listOf(it.entero(0).toDouble(), it.decimal(1), it.entero(2).toDouble(), it.decimal(3), it.decimal(4), it.decimal(5)) }
            filas shouldBe listOf(
                listOf(1.0, 6.0, 15.0, 3.0, 200.0, 1.5),
                listOf(2.0, 12.0, 29.0, 3.5, 450.0, 1.75),
                listOf(3.0, 20.0, 41.0, 4.0, 900.0, 2.0),
                listOf(4.0, 32.0, 51.0, 4.5, 1800.0, 2.25),
                listOf(5.0, 50.0, 61.0, 5.0, 4000.0, 2.5),
            )
        } finally {
            bd.cerrar()
        }
    }

    @Test
    fun `TN_11 las misiones de edad se renombran y conservan estado y recompensa`() {
        val bd = abrir()
        try {
            bd.leer("SELECT id_mision FROM Mision ORDER BY id_mision") { it.texto(0) } shouldBe
                listOf("comprar_especie", "crecer_especie", "vender_especie_nivel", "vender_nivel")
            bd.leer("SELECT DISTINCT id_mision FROM ProgresoMision ORDER BY id_mision") { it.texto(0) } shouldBe
                listOf("comprar_especie", "crecer_especie", "vender_especie_nivel", "vender_nivel")
            bd.uno("SELECT tipo_objetivo FROM Mision WHERE id_mision = 'vender_nivel'") { it.texto(0) } shouldBe "Vender un animal de un nivel concreto"

            fun parametros(instancia: String) =
                bd.leer(
                    "SELECT nombre_parametro, COALESCE(valor_numero, valor_texto) FROM ParametroProgresoMision WHERE id_instancia_mision = ? " +
                        "ORDER BY nombre_parametro",
                    instancia,
                ) { it.texto(0) to it.texto(1) }

            // PENDIENTE: vuelve a los valores base y progreso 0.
            bd.uno("SELECT progreso || '/' || estado FROM ProgresoMision WHERE id_instancia_mision = 'p1/crecer_especie/2'") { it.texto(0) } shouldBe "0.0/PENDIENTE"
            parametros("p1/crecer_especie/2") shouldBe listOf("cantidad_animales" to "1.0", "id_especie" to "nutria", "nivel_requerido" to "3.0")
            bd.uno("SELECT progreso || '/' || estado FROM ProgresoMision WHERE id_instancia_mision = 'p1/vender_nivel/3'") { it.texto(0) } shouldBe "0.0/PENDIENTE"
            parametros("p1/vender_nivel/3") shouldBe listOf("nivel_requerido" to "4.0")

            // CUMPLIDA: conserva estado, progreso y recompensa reclamable; solo cambia el parámetro de edad por el de nivel.
            bd.uno("SELECT progreso || '/' || estado FROM ProgresoMision WHERE id_instancia_mision = 'p1/vender_especie_nivel/2'") { it.texto(0) } shouldBe "1.0/CUMPLIDA"
            parametros("p1/vender_especie_nivel/2") shouldBe listOf("id_especie" to "erizo", "nivel_requerido" to "4.0")
            bd.uno("SELECT estado || '/' || id_mision FROM RecompensaMision WHERE id_instancia_mision = 'p1/vender_especie_nivel/2'") { it.texto(0) } shouldBe
                "PENDIENTE/vender_especie_nivel"

            // La misión que no era de edad no cambia.
            parametros("p1/comprar_especie/1") shouldBe listOf("cantidad_requerida" to "2.0", "id_especie" to "erizo")
            bd.leer("SELECT nombre_parametro FROM ParametroMision WHERE nombre_parametro = 'edad_requerida'") { it.texto(0) } shouldBe emptyList()
            bd.uno(
                "SELECT valor_base_numero || '/' || operacion_progresion || '/' || constante_progresion || '/' || valor_minimo || '/' || valor_maximo " +
                    "FROM ParametroMision WHERE id_mision = 'crecer_especie' AND nombre_parametro = 'nivel_requerido'",
            ) { it.texto(0) } shouldBe "3.0/SUMAR/1.0/2.0/15.0"
        } finally {
            bd.cerrar()
        }
    }

    @Test
    fun `TN_11 repetir la apertura no tiene efecto`() {
        abrir().cerrar()
        val bd = abrir()
        try {
            bd.uno("PRAGMA user_version") { it.entero(0) } shouldBe 8
            bd.nivelDe("a7") shouldBe (2 to 28)
            bd.uno("SELECT COUNT(*) FROM Animal") { it.entero(0) } shouldBe 9
        } finally {
            bd.cerrar()
        }
    }

    private companion object {
        val DATOS_V2 = listOf(
            "INSERT INTO Bioma (id_bioma, nombre) VALUES ('bosque', 'Bosque')",
            "INSERT INTO RangoEspecie VALUES (1, 'Común', 0.6, 0.04, 0.01, 0.01, 4.0)",
            "INSERT INTO RangoEspecie VALUES (2, 'Poco común', 0.65, 0.03, 0.008, 0.01, 8.0)",
            "INSERT INTO RangoEspecie VALUES (3, 'Raro', 0.7, 0.02, 0.006, 0.015, 14.0)",
            "INSERT INTO RangoEspecie VALUES (4, 'Épico', 0.75, 0.015, 0.005, 0.015, 22.0)",
            "INSERT INTO RangoEspecie VALUES (5, 'Exclusivo', 0.8, 0.01, 0.004, 0.02, 35.0)",
            "INSERT INTO Especie (id_especie, nombre_comun, id_rango, tope_hermanos, peso_bio, peso_soc, estado_catalogo, nivel_desbloqueo, " +
                "peso_pareja, peso_amigo, peso_padre, peso_madre, peso_hermanos, peso_afinidad, edad_media_anios, kg_bambu_por_incremento) " +
                "VALUES ('erizo', 'Erizo', 1, 3, 0.5, 0.5, 'COMERCIAL', 0, 0.1, 0.1, 0.05, 0.05, 0.1, 0.6, 40, 1)",
            "INSERT INTO Especie (id_especie, nombre_comun, id_rango, tope_hermanos, peso_bio, peso_soc, estado_catalogo, nivel_desbloqueo, " +
                "peso_pareja, peso_amigo, peso_padre, peso_madre, peso_hermanos, peso_afinidad, edad_media_anios, kg_bambu_por_incremento) " +
                "VALUES ('leon', 'León', 3, 3, 0.5, 0.5, 'COMERCIAL', 2, 0.1, 0.1, 0.05, 0.05, 0.1, 0.6, 50, 2)",
            "INSERT INTO Especie (id_especie, nombre_comun, id_rango, tope_hermanos, peso_bio, peso_soc, estado_catalogo, nivel_desbloqueo, " +
                "peso_pareja, peso_amigo, peso_padre, peso_madre, peso_hermanos, peso_afinidad, edad_media_anios, kg_bambu_por_incremento) " +
                "VALUES ('nutria', 'Nutria', 1, 3, 0.5, 0.5, 'COMERCIAL', 0, 0.1, 0.1, 0.05, 0.05, 0.1, 0.6, 40, 1)",
            "INSERT INTO Partida (id_partida, fondos) VALUES ('p1', 100.0)",
            "INSERT INTO Habitat (id_habitat, id_partida, codigo, id_bioma, nombre) VALUES ('p1/bosque', 'p1', 'bosque', 'bosque', 'Bosque')",
            "INSERT INTO Espacio (id_espacio, id_partida, id_habitat, nombre, capacidad_max) VALUES ('s1', 'p1', 'p1/bosque', 'Madriguera', 20)",
            // Animales: edad total en días (años·360 + meses·30 + días) y edad máxima irrelevante para la migración.
            animal("a1", "erizo", 20, 0, 0),
            animal("a2", "erizo", 21, 2, 12), // 7200 + 432
            animal("a3", "erizo", 21, 0, 0),
            animal("a4", "erizo", 3, 0, 0),
            animal("a5", "erizo", 36, 9, 18), // 7200 + 1008 · 6 = 13248
            animal("a6", "erizo", 40, 0, 0), // 14400: 1200 ciclos
            animal("a7", "erizo", 21, 8, 0), // 7200 + 600
            animal("a8", "erizo", 20, 0, 0, vivo = false),
            animal("a9", "leon", 23, 7, 6), // 7200 + 1296 = 8496 días → 216 ciclos → nivel 4
            "UPDATE Animal SET id_pareja = 'a3' WHERE id_animal = 'a1'",
            "UPDATE Animal SET id_pareja = 'a1' WHERE id_animal = 'a3'",
            "UPDATE Animal SET id_padre = 'a1', id_madre = 'a3' WHERE id_animal = 'a4'",
            "INSERT INTO RasgoPersonalidad (id_animal, rasgo) VALUES ('a1', 'curioso')",
            // Misiones del catálogo.
            mision("comprar_especie", "Comprar animales de una especie"),
            mision("crecer_especie", "Hacer crecer animales de una especie"),
            mision("vender_especie_edad", "Vender un animal de una especie y edad concretas"),
            mision("vender_edad", "Vender un animal de una edad concreta"),
            texto("comprar_especie", "id_especie", "erizo"),
            numero("comprar_especie", "cantidad_requerida", 2.0, "SUMAR", 1.0, 1.0, 20.0),
            texto("crecer_especie", "id_especie", "nutria"),
            numero("crecer_especie", "cantidad_animales", 1.0, "SUMAR", 1.0, 1.0, 20.0),
            numero("crecer_especie", "edad_requerida", 24.0, "SUMAR", 4.0, 1.0, 36.0),
            texto("vender_especie_edad", "id_especie", "erizo"),
            numero("vender_especie_edad", "edad_requerida", 22.0, "SUMAR", 2.0, 1.0, 36.0),
            numero("vender_edad", "edad_requerida", 22.0, "SUMAR", 2.0, 1.0, 36.0),
            // Instancias de la partida.
            instancia("comprar_especie", 1, "PENDIENTE", 1.0, "id_especie" to "erizo", "cantidad_requerida" to 2.0),
            instancia("crecer_especie", 2, "PENDIENTE", 1.0, "id_especie" to "nutria", "cantidad_animales" to 3.0, "edad_requerida" to 28.0),
            instancia("vender_especie_edad", 2, "CUMPLIDA", 1.0, "id_especie" to "erizo", "edad_requerida" to 24.0),
            instancia("vender_edad", 3, "PENDIENTE", 0.0, "edad_requerida" to 30.0),
            "INSERT INTO RecompensaMision (id_partida, id_mision, id_instancia_mision, tipo, cantidad, estado, numero_ciclo, fecha) " +
                "VALUES ('p1', 'vender_especie_edad', 'p1/vender_especie_edad/2', 'FONDOS', 50.0, 'PENDIENTE', 0, 1)",
        )

        fun animal(id: String, especie: String, anios: Int, meses: Int, dias: Int, vivo: Boolean = true) =
            "INSERT INTO Animal (id_animal, id_partida, id_especie, id_espacio, nombre, edad_anios, edad_meses, edad_dias, " +
                "edad_maxima_anios, edad_maxima_meses, edad_maxima_dias, estado_vital, satisfaccion_actual) " +
                "VALUES ('$id', 'p1', '$especie', ${if (vivo) "'s1'" else "NULL"}, 'Animal $id', $anios, $meses, $dias, 60, 0, 0, " +
                "'${if (vivo) "VIVO" else "VENDIDO"}', 0.5)"

        fun mision(id: String, tipo: String) =
            "INSERT INTO Mision (id_mision, tipo_objetivo, estado, naturaleza_progreso, tipo_recompensa, cantidad_recompensa) " +
                "VALUES ('$id', '$tipo', 'ACTIVA', 'ACUMULATIVO', 'FONDOS', 50.0)"

        fun texto(mision: String, nombre: String, valor: String) =
            "INSERT INTO ParametroMision (id_mision, nombre_parametro, tipo_valor, valor_base_texto) VALUES ('$mision', '$nombre', 'CATEGORICO', '$valor')"

        fun numero(mision: String, nombre: String, base: Double, op: String, constante: Double, min: Double, max: Double) =
            "INSERT INTO ParametroMision (id_mision, nombre_parametro, tipo_valor, valor_base_numero, operacion_progresion, constante_progresion, " +
                "valor_minimo, valor_maximo) VALUES ('$mision', '$nombre', 'NUMERICO', $base, '$op', $constante, $min, $max)"

        /** Una instancia vigente con sus parámetros; los pares con `String` son categóricos y con `Double`, numéricos. */
        fun instancia(mision: String, n: Int, estado: String, progreso: Double, vararg parametros: Pair<String, Any>): String {
            // Una sola sentencia no puede insertar en dos tablas: se encadenan con un separador que `datos` parte después.
            val id = "p1/$mision/$n"
            return "INSERT INTO ProgresoMision (id_partida, id_instancia_mision, id_mision, progreso, estado) VALUES ('p1', '$id', '$mision', $progreso, '$estado')" +
                parametros.joinToString("") { (nombre, valor) -> SEPARADOR + parametro(id, mision, nombre, valor) }
        }

        const val SEPARADOR = "\u0000"

        fun parametro(id: String, mision: String, nombre: String, valor: Any) =
            "INSERT INTO ParametroProgresoMision (id_partida, id_instancia_mision, id_mision, nombre_parametro, valor_numero, valor_texto) VALUES " +
                "('p1', '$id', '$mision', '$nombre', ${if (valor is Double) valor.toString() else "NULL"}, ${if (valor is String) "'$valor'" else "NULL"})"
    }
}
