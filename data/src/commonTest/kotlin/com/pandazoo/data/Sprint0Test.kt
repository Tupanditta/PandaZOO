package com.pandazoo.data

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import com.pandazoo.data.db.ErrorSql
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.operacion.Operacion
import com.pandazoo.application.puerto.ZooRepositorio
import com.pandazoo.data.inicial.InicializadorPartida
import com.pandazoo.domain.config.ConfiguracionInvalidaException
import com.pandazoo.testing.GeneradorIdSecuencial
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import com.pandazoo.domain.economia.Dinero

class Sprint0Test {
    private fun d(texto: String): Dinero = Dinero.desdeDecimal(texto.toDouble())

    private lateinit var e: Entorno

    @BeforeTest
    fun preparar() {
        e = Entorno()
    }

    @AfterTest
    fun cerrar() = e.cerrar()

    private fun rechaza(sql: String, vararg args: Any?) {
        shouldThrow<ErrorSql> { e.bd.escribir(sql, *args) }
    }

    private fun acepta(sql: String, vararg args: Any?) = e.bd.escribir(sql, *args)

    // ---- T0.4 ----
    @Test
    fun `T0_4 partida nueva tiene fondos prestigio y habitats precargados`() {
        val db = e.bd
        e.fondos() shouldBe d("2000.00")
        db.uno("SELECT prestigio_actual, prestigio_maximo_nivel, animales_vivos_maximos, satisfaccion_global_maxima, estado FROM Partida") {
            it.entero(0) shouldBe 0
            it.esNulo(1) shouldBe true
            it.esNulo(2) shouldBe true
            it.esNulo(3) shouldBe true
            it.texto(4) shouldBe "ACTIVA"
        }
        e.contar("Habitat", "id_partida = '${e.idPartida}' AND estado = 'ACTIVO'") shouldBe 5
        e.contar("DesbloqueoHabitat") shouldBe 2
        e.contar("DesbloqueoEspecie", "origen = 'INICIAL'") shouldBe 6
        e.contar("Espacio") shouldBe 0
        e.contar("Animal") shouldBe 0
        e.contar("Notificacion") shouldBe 0
        e.inventario.existencias(e.idPartida).map { it.nombre to it.cantidad }
            .shouldContainExactly("Bambú" to 30, "Medicina" to 3)
        db.uno("SELECT remanente_temporizador, estado, numero_ciclo_ultimo FROM EstadoSimulacion") {
            it.entero(0) shouldBe 600
            it.texto(1) shouldBe "ACTIVO"
            it.esNulo(2) shouldBe true
        }
        e.contar("MetricaSatisfaccion", "nivel = 'PARTIDA' AND valor = 0") shouldBe 1
        e.contar("ProgresoMision", "estado = 'PENDIENTE' AND progreso = 0") shouldBe 12
        e.contar("ParametroProgresoMision") shouldBe 21
    }

    @Test
    fun `T0_4 el catalogo se carga completo y una sola vez`() {
        e.contar("Especie") shouldBe 47
        e.contar("Especie", "estado_catalogo = 'EXCLUSIVA' AND nivel_desbloqueo IS NULL") shouldBe 9
        e.contar("Bioma") shouldBe 5
        e.contar("CompatibilidadBioma") shouldBe 235
        e.contar("AfinidadEspecies") shouldBe 1128
        e.contar("RangoEspecie") shouldBe 5
        e.contar("CosteEspacio") shouldBe 7
        e.contar("Mision") shouldBe 12
        // Una segunda partida reutiliza el catálogo global.
        val otra = InicializadorPartida(e.bd, e.reloj, GeneradorIdSecuencial("otra")).crearPartida(e.cfg)
        e.contar("Especie") shouldBe 47
        e.contar("Habitat", "id_partida = '$otra'") shouldBe 5
    }

    @Test
    fun `una configuracion invalida impide crear la partida y no deja filas`() {
        val rota = e.cfg.copy(tablas = e.cfg.tablas.copy(costesAlimentos = emptyList()))
        shouldThrow<ConfiguracionInvalidaException> {
            InicializadorPartida(e.bd, e.reloj, GeneradorIdSecuencial("x")).crearPartida(rota)
        }
        e.contar("Partida") shouldBe 1
    }

    // ---- T0.3 ----
    @Test
    fun `T0_3 claves foraneas activadas`() {
        e.bd.uno("PRAGMA foreign_keys") { it.entero(0) } shouldBe 1
    }

    private fun insertarAnimal(columna: String? = null, valor: Any? = null, id: String = "a1", nombre: String = "Rex") {
        val base = linkedMapOf<String, Any?>(
            "id_animal" to id, "id_partida" to e.idPartida, "id_especie" to "leon", "nombre" to nombre,
            "satisfaccion_actual" to 0.5,
        )
        if (columna != null) base[columna] = valor
        acepta("INSERT INTO Animal (${base.keys.joinToString()}) VALUES (${base.keys.joinToString { "?" }})", *base.values.toTypedArray())
    }

    @Test
    fun `T0_3 animal valido se acepta y los invalidos se rechazan`() {
        insertarAnimal()
        e.contar("Animal") shouldBe 1
        shouldThrow<ErrorSql> { insertarAnimal("nivel_alimentacion", 55, "a2", "B") } // no múltiplo de 10
        shouldThrow<ErrorSql> { insertarAnimal("nivel_alimentacion", 110, "a3", "C") }
        shouldThrow<ErrorSql> { insertarAnimal("estado_salud", "ROTO", "a4", "D") }
        shouldThrow<ErrorSql> { insertarAnimal("nivel", 0, "a5", "E") }
        shouldThrow<ErrorSql> { insertarAnimal("ciclos_nivel", -1, "a6", "F") }
        shouldThrow<ErrorSql> { insertarAnimal("satisfaccion_actual", 1.5, "a7", "G") }
        shouldThrow<ErrorSql> { insertarAnimal("ciclos_desnutricion", 4, "a8", "H") }
        shouldThrow<ErrorSql> { insertarAnimal("id_pareja", "a9", "a9", "I") } // autorreferencia
        shouldThrow<ErrorSql> { insertarAnimal("id_especie", "no_existe", "a10", "J") }
        insertarAnimal(null, null, "a11", "Rex") // el nombre no es único (RF-32)
        e.contar("Animal") shouldBe 2
    }

    @Test
    fun `T0_3 un animal no vivo no puede seguir alojado`() {
        acepta(
            "INSERT INTO Espacio (id_espacio, id_partida, id_habitat, nombre, capacidad_max) VALUES ('s1', ?, ?, 'Pradera', 4)",
            e.idPartida, e.habitat("bosque_templado"),
        )
        insertarAnimal("id_espacio", "s1")
        shouldThrow<ErrorSql> {
            e.bd.escribir("UPDATE Animal SET estado_vital = 'VENDIDO' WHERE id_animal = 'a1'")
        }
    }

    @Test
    fun `T0_3 espacios con capacidad invalida y referencias a otra partida se rechazan`() {
        val otra = InicializadorPartida(e.bd, e.reloj, GeneradorIdSecuencial("otra")).crearPartida(e.cfg)
        rechaza(
            "INSERT INTO Espacio (id_espacio, id_partida, id_habitat, nombre, capacidad_max) VALUES ('s1', ?, ?, 'X', 0)",
            e.idPartida, e.habitat("bosque_templado"),
        )
        // Hábitat de otra partida referenciado desde esta.
        rechaza(
            "INSERT INTO Espacio (id_espacio, id_partida, id_habitat, nombre, capacidad_max) VALUES ('s2', ?, ?, 'X', 4)",
            e.idPartida, "$otra/bosque_templado",
        )
        acepta(
            "INSERT INTO Espacio (id_espacio, id_partida, id_habitat, nombre, capacidad_max) VALUES ('s3', ?, ?, 'X', 4)",
            e.idPartida, e.habitat("bosque_templado"),
        )
        // Nombre repetido en el mismo hábitat.
        rechaza(
            "INSERT INTO Espacio (id_espacio, id_partida, id_habitat, nombre, capacidad_max) VALUES ('s4', ?, ?, 'X', 6)",
            e.idPartida, e.habitat("bosque_templado"),
        )
        // Un animal de esta partida no puede alojarse en un espacio de otra.
        acepta(
            "INSERT INTO Espacio (id_espacio, id_partida, id_habitat, nombre, capacidad_max) VALUES ('s5', ?, ?, 'X', 4)",
            otra, "$otra/bosque_templado",
        )
        shouldThrow<ErrorSql> { insertarAnimal("id_espacio", "s5") }
    }

    @Test
    fun `T0_3 economia e inventarios no admiten valores invalidos`() {
        rechaza("UPDATE Partida SET fondos = -1")
        rechaza("UPDATE Partida SET fondos = 10.005")
        rechaza("UPDATE InventarioAlimento SET cantidad = -1")
        rechaza("UPDATE InventarioMedico SET cantidad = -1")
        rechaza("UPDATE EstadoSimulacion SET remanente_temporizador = 601")
        rechaza("UPDATE Partida SET estado = 'OTRA'")
        rechaza(
            "INSERT INTO MovimientoEconomico (id_partida, tipo, importe, origen, fecha) VALUES (?, 'COMPRA', 1.234, 'x', 0)",
            e.idPartida,
        )
        rechaza(
            "INSERT INTO AuditoriaEvento (id_partida, tipo, entidad, origen, resultado, fecha, detalle) VALUES (?, 't', 'NADA', 'o', 'CONFIRMADO', 0, '')",
            e.idPartida,
        )
        e.fondos() shouldBe d("2000.00")
    }

    @Test
    fun `T0_3 catalogo respeta sus restricciones`() {
        rechaza("INSERT INTO AfinidadEspecies (id_especie_a, id_especie_b, coeficiente) VALUES ('leon', 'alce', 0.5)") // a > b
        rechaza("INSERT INTO AfinidadEspecies (id_especie_a, id_especie_b, coeficiente) VALUES ('alce', 'leon', 1.5)")
        rechaza("INSERT INTO CompatibilidadBioma (id_especie, id_bioma, factor_bioma) VALUES ('leon', 'no_existe', 0.5)")
        rechaza("UPDATE Especie SET peso_bio = 0.7 WHERE id_especie = 'leon'") // bio + soc != 1
        rechaza("UPDATE Especie SET peso_afinidad = 0.1 WHERE id_especie = 'leon'")
        rechaza("UPDATE Especie SET nivel_desbloqueo = 3 WHERE id_especie = 'panda'") // exclusiva sin nivel
        rechaza("UPDATE CosteEspacio SET importe = 10.005 WHERE capacidad_max = 6")
    }

    @Test
    fun `T0_3 notificaciones respetan el contrato de tipos y unicidad`() {
        val sql = "INSERT INTO Notificacion (id_partida, tipo, severidad, codigo, entidad, id_entidad, estado, fecha, numero_ciclo) " +
            "VALUES (?, ?, ?, ?, 'ANIMAL', ?, ?, 0, 0)"
        rechaza(sql, e.idPartida, "CRITICA", "BAJA", "ALERTA_SALUD", "a1", "PENDIENTE")
        rechaza(sql, e.idPartida, "INFORMATIVA", "BAJA", "PRESTIGIO_ASCENSO", "1", "ACEPTADA")
        acepta(sql, e.idPartida, "CRITICA", "ALTA", "ALERTA_SALUD", "a1", "PENDIENTE")
        rechaza(sql, e.idPartida, "CRITICA", "ALTA", "ALERTA_SALUD", "a1", "VISTA") // una crítica activa por animal
    }

    @Test
    fun `T0_3 el esquema contiene todas las tablas e indices parciales`() {
        e.bd.leer("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'") {
            it.texto(0)
        }.size shouldBe 36
        e.bd.uno("SELECT sql FROM sqlite_master WHERE name = 'idx_animal_localizacion'") {
            it.texto(0).contains("WHERE estado_vital = 'VIVO'") shouldBe true
        }
        e.bd.uno("SELECT sql FROM sqlite_master WHERE name = 'InventarioAlimento'") {
            it.texto(0).contains("WITHOUT ROWID") shouldBe true
        }
    }

    @Test
    fun `T0_3 una base de datos en fichero usa auto_vacuum incremental WAL y claves foraneas`() {
        val ruta = rutaTemporal("prueba-esquema.db")
        val archivo = com.pandazoo.data.db.BaseDatos.enArchivo(ruta)
        try {
            archivo.uno("PRAGMA auto_vacuum") { it.entero(0) } shouldBe 2
            archivo.uno("PRAGMA journal_mode") { it.texto(0) } shouldBe modoDiarioEsperado
            archivo.uno("PRAGMA foreign_keys") { it.entero(0) } shouldBe 1
            archivo.uno("PRAGMA user_version") { it.entero(0) } shouldBe com.pandazoo.data.db.BaseDatos.VERSION
        } finally {
            archivo.cerrar()
            borrarArchivoDeBd(ruta)
        }
    }

    @Test
    fun `T0_3 reabrir una base de datos en fichero conserva los datos y no recrea el esquema`() {
        val ruta = rutaTemporal("prueba-reabrir.db")
        try {
            val primera = com.pandazoo.data.db.BaseDatos.enArchivo(ruta)
            primera.escribir("INSERT INTO Bioma (id_bioma, nombre) VALUES ('x', 'Prueba')")
            primera.cerrar()
            val segunda = com.pandazoo.data.db.BaseDatos.enArchivo(ruta)
            segunda.uno("SELECT nombre FROM Bioma WHERE id_bioma = 'x'") { it.texto(0) } shouldBe "Prueba"
            segunda.cerrar()
        } finally {
            borrarArchivoDeBd(ruta)
        }
    }

    // ---- T0.5 ----
    @Test
    fun `T0_5 si una escritura falla a mitad no quedan cambios parciales`() {
        shouldThrow<IllegalStateException> {
            e.bd.ejecutar {
                e.economia.actualizarFondos(e.idPartida, d("1.00"))
                e.inventario.incrementar(e.idPartida, com.pandazoo.application.caso.AdquirirInsumos.bambu(), 5)
                error("fallo simulado")
            }
        }
        e.fondos() shouldBe d("2000.00")
        e.inventario.cantidad(e.idPartida, com.pandazoo.application.caso.AdquirirInsumos.bambu()) shouldBe 30
    }

    @Test
    fun `T0_5 el ejecutor revierte un fallo tecnico y audita el intento`() {
        val op = operacionPrueba("s-fallo")
        shouldThrow<IllegalStateException> {
            e.ejecutor.ejecutar(op) {
                e.economia.actualizarFondos(e.idPartida, d("5.00"))
                error("boom")
            }
        }
        e.fondos() shouldBe d("2000.00")
        e.auditoriaDe("PRUEBA").map { it.first } shouldContainExactly listOf("REVERTIDO")
    }

    // ---- T0.6 ----
    @Test
    fun `T0_6 repetir la misma clave devuelve el resultado original sin duplicar efectos`() {
        var ejecuciones = 0
        val op = operacionPrueba("s-1")
        val bloque = {
            ejecuciones++
            e.economia.actualizarFondos(e.idPartida, e.fondos() - d("10.00"))
            Resultado.Exito("resultado-$ejecuciones")
        }
        val primera = e.ejecutor.ejecutar(op, bloque)
        val segunda = e.ejecutor.ejecutar(op, bloque)
        ejecuciones shouldBe 1
        e.fondos() shouldBe d("1990.00")
        (primera as Resultado.Exito).repetido shouldBe false
        (segunda as Resultado.Exito).repetido shouldBe true
        segunda.valor shouldBe primera.valor
        e.auditoriaDe("PRUEBA").size shouldBe 1
    }

    @Test
    fun `T0_6 la misma clave con otro contenido es un conflicto auditado como rechazado`() {
        e.ejecutor.ejecutar(operacionPrueba("s-2", huella = "a")) { Resultado.Exito("x") }
        val conflicto = e.ejecutor.ejecutar(operacionPrueba("s-2", huella = "b")) { Resultado.Exito("y") }
        (conflicto as Resultado.Rechazado).motivo.codigo shouldBe "CONFLICTO_IDEMPOTENCIA"
        e.auditoriaDe("PRUEBA").map { it.first } shouldContainExactly listOf("CONFIRMADO", "RECHAZADO")
    }

    private fun operacionPrueba(solicitud: String, huella: String = "h"): Operacion<String> =
        Operacion(
            idPartida = e.idPartida,
            tipo = "PRUEBA",
            entidad = "PARTIDA",
            origen = "TEST",
            idSolicitud = solicitud,
            huella = huella,
            codificar = { it },
            decodificar = { it },
            idEntidad = { null },
        )
}
