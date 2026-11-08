package com.pandazoo.data.inicial

import com.pandazoo.application.puerto.Azar
import com.pandazoo.application.puerto.GeneradorId
import com.pandazoo.application.puerto.Reloj
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.data.tiempo.AzarSistema
import com.pandazoo.domain.config.ConfiguracionJuego
import com.pandazoo.domain.config.EspecieCfg
import com.pandazoo.domain.config.MisionCfg
import com.pandazoo.domain.config.ValidadorConfiguracion
import com.pandazoo.domain.economia.Redondeo
import com.pandazoo.domain.config.ProbabilidadRango
import com.pandazoo.domain.mision.SorteoAnimales
import com.pandazoo.domain.modelo.IdPartida
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Un animal de regalo: su unidad gratuita se compra en la tienda de animales. */
/** @property probabilidades tabla de rango con la que se sorteó (vacía si el rango estaba fijado de antemano). */
data class AnimalDeBienvenida(
    val idEspecie: String,
    val nombre: String,
    val idRango: Int,
    val probabilidades: List<ProbabilidadRango> = emptyList(),
)

/** Lo que recibe quien empieza una partida nueva, para mostrarlo en ventanas flotantes. */
data class RegaloBienvenida(val fondos: Double, val bambu: Int, val medicinas: Int, val animales: List<AnimalDeBienvenida>)

data class PartidaCreada(val idPartida: IdPartida, val regalo: RegaloBienvenida)

/**
 * Carga del catálogo global y creación de una partida nueva en una única transacción
 * (`EspecificacionBBDD.md`, sección 2.4). Es la única vía autorizada para crear hábitats (RES-05).
 */
class InicializadorPartida(
    private val bd: BaseDatos,
    private val reloj: Reloj,
    private val ids: GeneradorId,
    private val azar: Azar = AzarSistema(),
) {
    /** Valida la configuración completa y crea la partida. Una configuración inválida lanza excepción. */
    fun crearPartida(cfg: ConfiguracionJuego): IdPartida {
        ValidadorConfiguracion.validarOExcepcion(cfg)
        return bd.ejecutar {
            if (!catalogoCargado()) cargarCatalogo(cfg)
            insertarPartida(cfg)
        }
    }

    /**
     * Crea la partida y, en la misma transacción, concede el regalo de bienvenida: animales gratuitos de especies
     * sorteadas sin tener en cuenta el prestigio. Los fondos y las medicinas ya son los de `partida_inicial`.
     */
    fun crearPartidaConBienvenida(cfg: ConfiguracionJuego): PartidaCreada {
        ValidadorConfiguracion.validarOExcepcion(cfg)
        return bd.ejecutar {
            if (!catalogoCargado()) cargarCatalogo(cfg)
            val id = insertarPartida(cfg)
            PartidaCreada(
                id,
                RegaloBienvenida(
                    cfg.partidaInicial.fondos,
                    cfg.partidaInicial.inventarioAlimento.values.sum(),
                    cfg.partidaInicial.inventarioMedico.values.sum(),
                    concederAnimales(id, cfg),
                ),
            )
        }
    }

    private fun concederAnimales(idPartida: IdPartida, cfg: ConfiguracionJuego): List<AnimalDeBienvenida> {
        val b = cfg.partidaInicial.bienvenida
        val comerciales = cfg.especies.filter { it.categoria == "COMERCIAL" }
        val porId = cfg.especies.associateBy { it.id }
        val elegidas = ArrayList<AnimalDeBienvenida>()
        fun fijas(rango: Int, n: Int) = sortearDistintas(comerciales.filter { it.idRango == rango }, n)
            .forEach { elegidas += AnimalDeBienvenida(it.id, it.nombre, rango) }
        fijas(1, b.comunes)
        fijas(2, b.pocoComunes)
        val sorteo = SorteoAnimales.desde(cfg)
        for (tabla in b.sorteos) {
            val id = sorteo.sortear(tabla, azar.siguienteDouble(), azar.siguienteDouble(), elegidas.map { it.idEspecie }.toSet()) ?: continue
            val especie = porId.getValue(id)
            elegidas += AnimalDeBienvenida(id, especie.nombre, especie.idRango, tabla)
        }
        val ahora = reloj.ahoraSegundos()
        elegidas.forEach {
            // Solo se concede la unidad: una especie bloqueada sigue bloqueada tras reclamarla.
            bd.escribir(
                "INSERT INTO UnidadGratuita (id_partida, id_especie, origen, unidad_pendiente, fecha) VALUES (?, ?, 'BIENVENIDA', 1, ?)",
                idPartida, it.idEspecie, ahora,
            )
        }
        return elegidas
    }

    /** Hasta [n] especies distintas elegidas al azar de [lista]. */
    private fun sortearDistintas(lista: List<EspecieCfg>, n: Int): List<EspecieCfg> {
        val restantes = lista.toMutableList()
        val resultado = ArrayList<EspecieCfg>()
        while (resultado.size < n && restantes.isNotEmpty()) {
            resultado += restantes.removeAt((azar.siguienteDouble() * restantes.size).toInt().coerceIn(0, restantes.size - 1))
        }
        return resultado
    }

    private fun catalogoCargado() = bd.uno("SELECT 1 FROM Bioma LIMIT 1") { true } ?: false

    private fun cargarCatalogo(cfg: ConfiguracionJuego) {
        val t = cfg.tablas
        cfg.catalogos.biomas.forEach { bd.escribir("INSERT INTO Bioma (id_bioma, nombre) VALUES (?, ?)", it.id, it.nombre) }
        t.rangos.forEach {
            bd.escribir(
                "INSERT INTO RangoEspecie (id_rango, nombre, umbral_reproduccion, probabilidad_reproduccion, " +
                    "penalizacion_hijo_reproduccion, probabilidad_enfermedad, beneficio_base_animal_ciclo, nivel_maximo, " +
                    "multiplicador_nivel_maximo, valor_venta_base, factor_venta_nivel_maximo, factores_hijos) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                it.id, it.nombre, it.umbralReproduccion, it.probabilidadReproduccion, it.penalizacionHijo,
                it.probabilidadEnfermedad, it.beneficioBase, it.nivelMaximo, it.multiplicadorNivelMaximo, it.valorVentaBase,
                it.factorVentaNivelMaximo, it.factoresHijos.joinToString(";"),
            )
        }
        val nivelPorEspecie = t.desbloqueos.flatMap { n -> n.especies.map { it to n.nivel } }.toMap()
        cfg.especies.forEach { s ->
            val p = s.social.pesos
            bd.escribir(
                "INSERT INTO Especie (id_especie, nombre_comun, id_rango, tope_hermanos, peso_bio, peso_soc, estado_catalogo, " +
                    "nivel_desbloqueo, peso_pareja, peso_amigo, peso_padre, peso_madre, peso_hermanos, peso_afinidad, " +
                    "kg_bambu_por_incremento) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                s.id, s.nombre, s.idRango, s.social.topeHermanos, s.pesos.bio, s.pesos.soc, s.categoria,
                nivelPorEspecie[s.id], p.pareja, p.amigo, p.padre, p.madre, p.hermanos, p.afinidad,
                t.alimentosPorEspecie.first { it.idEspecie == s.id }.kilogramos,
            )
        }
        t.compatibilidad.forEach {
            bd.escribir(
                "INSERT INTO CompatibilidadBioma (id_especie, id_bioma, factor_bioma) VALUES (?, ?, ?)",
                it.idEspecie, it.idBioma, it.factor,
            )
        }
        cfg.matrizAfinidad.pares.forEach {
            bd.escribir(
                "INSERT INTO AfinidadEspecies (id_especie_a, id_especie_b, coeficiente) VALUES (?, ?, ?)",
                it.a, it.b, it.valor,
            )
        }
        cfg.catalogos.alimentos.forEach { bd.escribir("INSERT INTO Alimento (id_alimento, nombre) VALUES (?, ?)", it.id, it.nombre) }
        t.costesAlimentos.forEach {
            bd.escribir(
                "INSERT INTO CosteAlimento (id_alimento, importe_por_kilogramo) VALUES (?, ?)",
                it.idAlimento, Redondeo.dinero(it.precio).paraBd(),
            )
        }
        cfg.catalogos.insumos.forEach { bd.escribir("INSERT INTO InsumoMedico (id_insumo, nombre) VALUES (?, ?)", it.id, it.nombre) }
        t.costesInsumos.forEach {
            bd.escribir(
                "INSERT INTO CosteInsumo (id_insumo, importe_por_unidad) VALUES (?, ?)",
                it.idInsumo, Redondeo.dinero(it.precio).paraBd(),
            )
        }
        t.consumoMedico.forEach {
            bd.escribir(
                "INSERT INTO ConsumoMedicoRango (id_rango, id_insumo, cantidad_unidades) VALUES (?, ?, ?)",
                it.idRango, it.idInsumo, it.cantidad,
            )
        }
        t.costesEspacios.forEach {
            bd.escribir(
                "INSERT INTO CosteEspacio (capacidad_max, importe) VALUES (?, ?)",
                it.capacidad, Redondeo.dinero(it.importe).paraBd(),
            )
        }
        t.costesRangos.forEach {
            bd.escribir(
                "INSERT INTO CosteRango (id_rango, importe) VALUES (?, ?)",
                it.idRango, Redondeo.dinero(it.importe).paraBd(),
            )
        }
        cfg.catalogos.misiones.forEach { cargarMision(it) }
    }

    private fun cargarMision(m: MisionCfg) {
        val contenido = m.recompensa.contenido
        val (item, cantidad) = when (m.recompensa.tipo) {
            "FONDOS" -> null to numero(contenido.getValue("cantidad_fondos"))
            "ALIMENTO", "INSUMO" ->
                contenido.getValue("tipo_alimento_o_insumo").jsonPrimitive.content to
                    numero(contenido.getValue("cantidad_entregada"))
            else -> null to numero(contenido.getValue("cantidad_animales")) // ESPECIE: animal aleatorio
        }
        bd.escribir(
            "INSERT INTO Mision (id_mision, tipo_objetivo, estado, naturaleza_progreso, tipo_recompensa, id_item_recompensa, " +
                "cantidad_recompensa) VALUES (?, ?, ?, ?, ?, ?, ?)",
            m.id, m.tipoObjetivo, m.estado, m.naturaleza, m.recompensa.tipo, item, cantidad,
        )
        m.parametrosBase.forEach { (nombre, valor) ->
            val primitivo = valor.jsonPrimitive
            val prog = m.progresion[nombre]
            if (primitivo.isString) {
                bd.escribir(
                    "INSERT INTO ParametroMision (id_mision, nombre_parametro, tipo_valor, valor_base_texto) VALUES (?, ?, 'CATEGORICO', ?)",
                    m.id, nombre, primitivo.content,
                )
            } else {
                bd.escribir(
                    "INSERT INTO ParametroMision (id_mision, nombre_parametro, tipo_valor, valor_base_numero, operacion_progresion, " +
                        "constante_progresion, valor_minimo, valor_maximo) VALUES (?, ?, 'NUMERICO', ?, ?, ?, ?, ?)",
                    m.id, nombre, numero(valor), prog?.operacion, prog?.constante, prog?.minimo, prog?.maximo,
                )
            }
        }
    }

    private fun numero(e: JsonElement): Double =
        (e as JsonPrimitive).doubleOrNull ?: error("Valor numérico esperado en la configuración de misiones")

    private fun insertarPartida(cfg: ConfiguracionJuego): IdPartida {
        val inicial = cfg.partidaInicial
        val idPartida = ids.nuevo()
        val ahora = reloj.ahoraSegundos()
        bd.escribir(
            "INSERT INTO Partida (id_partida, fondos, prestigio_actual, prestigio_maximo_nivel, animales_vivos_maximos, " +
                "satisfaccion_global_maxima, estado) VALUES (?, ?, 0, NULL, NULL, NULL, ?)",
            idPartida, Redondeo.dinero(inicial.fondos).paraBd(), inicial.estadoPartida,
        )
        cfg.catalogos.alimentos.forEach {
            bd.escribir(
                "INSERT INTO InventarioAlimento (id_partida, id_alimento, cantidad) VALUES (?, ?, ?)",
                idPartida, it.id, inicial.inventarioAlimento[it.id] ?: 0,
            )
        }
        cfg.catalogos.insumos.forEach {
            bd.escribir(
                "INSERT INTO InventarioMedico (id_partida, id_insumo, cantidad) VALUES (?, ?, ?)",
                idPartida, it.id, inicial.inventarioMedico[it.id] ?: 0,
            )
        }
        val idHabitat = { codigo: String -> "$idPartida/$codigo" }
        cfg.catalogos.habitats.forEach {
            bd.escribir(
                "INSERT INTO Habitat (id_habitat, id_partida, codigo, id_bioma, nombre, estado) VALUES (?, ?, ?, ?, ?, 'ACTIVO')",
                idHabitat(it.codigo), idPartida, it.codigo, it.idBioma, it.nombre,
            )
        }
        val nivel0 = cfg.tablas.desbloqueos.first { it.nivel == 0 }
        nivel0.especies.forEach {
            bd.escribir(
                "INSERT INTO DesbloqueoEspecie (id_partida, id_especie, origen, fecha) VALUES (?, ?, 'INICIAL', ?)",
                idPartida, it, ahora,
            )
        }
        nivel0.habitats.forEach {
            bd.escribir(
                "INSERT INTO DesbloqueoHabitat (id_partida, id_habitat, fecha) VALUES (?, ?, ?)",
                idPartida, idHabitat(it), ahora,
            )
        }
        bd.escribir(
            "INSERT INTO EstadoSimulacion (id_partida, numero_ciclo_ultimo, instante_ultimo_ciclo, remanente_temporizador, estado) " +
                "VALUES (?, NULL, NULL, ?, ?)",
            idPartida, inicial.remanente, inicial.estadoSimulacion,
        )
        cfg.catalogos.misiones.filter { it.estado == "ACTIVA" }.forEach { m ->
            val instancia = "$idPartida/${m.id}/1"
            bd.escribir(
                "INSERT INTO ProgresoMision (id_partida, id_instancia_mision, id_mision, progreso, estado) VALUES (?, ?, ?, 0, 'PENDIENTE')",
                idPartida, instancia, m.id,
            )
            m.parametrosBase.forEach { (nombre, valor) ->
                val p = valor.jsonPrimitive
                bd.escribir(
                    "INSERT INTO ParametroProgresoMision (id_partida, id_instancia_mision, id_mision, nombre_parametro, " +
                        "valor_numero, valor_texto) VALUES (?, ?, ?, ?, ?, ?)",
                    idPartida, instancia, m.id, nombre, if (p.isString) null else numero(valor), if (p.isString) p.content else null,
                )
            }
        }
        bd.escribir(
            "INSERT INTO MetricaSatisfaccion (id_partida, nivel, valor, fecha) VALUES (?, 'PARTIDA', 0, ?)",
            idPartida, ahora,
        )
        bd.escribir(
            "INSERT INTO AuditoriaEvento (id_partida, tipo, entidad, id_entidad, origen, resultado, numero_ciclo, " +
                "clave_idempotencia, fecha, detalle) VALUES (?, 'CREACION_PARTIDA', 'PARTIDA', ?, 'SISTEMA', 'CONFIRMADO', 0, NULL, ?, 'partida nueva')",
            idPartida, idPartida, ahora,
        )
        return idPartida
    }
}
