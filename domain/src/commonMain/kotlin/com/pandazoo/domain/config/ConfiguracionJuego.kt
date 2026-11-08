package com.pandazoo.domain.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Modelo tipado de `valores_y_pesos.json`, la fuente única de valores del juego.
 * Solo se declaran los campos que consume la aplicación; el resto se ignora al leer.
 */
@Serializable
data class ConfiguracionJuego(
    val especies: List<EspecieCfg>,
    @SerialName("matriz_afinidad") val matrizAfinidad: MatrizAfinidadCfg,
    @SerialName("reglas_de_calculo") val reglas: ReglasCfg,
    @SerialName("recompensas_animales_aleatorios") val recompensasAnimales: RecompensasAnimalesCfg,
    val retencion: RetencionCfg,
    val catalogos: CatalogosCfg,
    val tablas: TablasCfg,
    @SerialName("partida_inicial") val partidaInicial: PartidaInicialCfg,
    @SerialName("regalos_semanales") val regalosSemanales: List<RegaloSemanalCfg> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun desdeJson(texto: String): ConfiguracionJuego = json.decodeFromString(serializer(), texto)
    }
}

@Serializable
data class EspecieCfg(
    @SerialName("id_especie") val id: String,
    val nombre: String,
    @SerialName("categoria_adquisicion") val categoria: String,
    @SerialName("id_rango") val idRango: Int,
    @SerialName("pesos_satisfaccion") val pesos: PesosBioSocCfg,
    @SerialName("satisfaccion_social") val social: SatisfaccionSocialCfg,
)

@Serializable
data class PesosBioSocCfg(
    @SerialName("peso_bio") val bio: Double,
    @SerialName("peso_soc") val soc: Double,
)

@Serializable
data class SatisfaccionSocialCfg(
    val pesos: PesosSocialesCfg,
    @SerialName("tope_hermanos") val topeHermanos: Int,
)

@Serializable
data class PesosSocialesCfg(
    val pareja: Double,
    val amigo: Double,
    val padre: Double,
    val madre: Double,
    val hermanos: Double,
    val afinidad: Double,
)

@Serializable
data class MatrizAfinidadCfg(
    val pares: List<ParAfinidadCfg>,
    /** Afinidad entre familiares (pareja, hijos, padres y hermanos), que sustituye a la de la matriz entre esos dos animales. */
    @SerialName("afinidad_familiar") val afinidadFamiliar: Double = 0.8,
)

@Serializable
data class ParAfinidadCfg(
    @SerialName("id_especie_a") val a: String,
    @SerialName("id_especie_b") val b: String,
    val valor: Double,
)

@Serializable
data class ReglasCfg(
    val prestigio: PrestigioReglasCfg,
    val nivel: NivelReglasCfg,
    val venta: VentaReglasCfg,
)

@Serializable
data class NivelReglasCfg(
    @SerialName("nivel_inicial") val nivelInicial: Int,
    @SerialName("ciclos_por_nivel") val ciclosPorNivel: Int,
    @SerialName("nivel_minimo_pareja") val nivelMinimoPareja: Int,
)

@Serializable
data class VentaReglasCfg(
    @SerialName("fraccion_equilibrio") val fraccionEquilibrio: Double,
    @SerialName("factor_venta_nivel_1") val factorNivel1: Double,
)

@Serializable
data class PrestigioReglasCfg(
    @SerialName("umbrales_nivel_1") val umbralesNivel1: UmbralesCfg,
    @SerialName("multiplicadores_nivel_siguiente") val multiplicadores: MultiplicadoresCfg,
    @SerialName("nivel_maximo") val nivelMaximo: Int,
)

@Serializable
data class UmbralesCfg(
    @SerialName("animales_vivos") val animalesVivos: Int,
    @SerialName("satisfaccion_global") val satisfaccionGlobal: Double,
)

@Serializable
data class MultiplicadoresCfg(
    @SerialName("animales_vivos") val animalesVivos: Double,
    @SerialName("satisfaccion_global") val satisfaccionGlobal: Double,
)

@Serializable
data class RecompensasAnimalesCfg(
    val especies: List<RecompensaEspecieCfg>,
    @SerialName("sorteo_prestigio") val sorteoPrestigio: SorteoPrestigioCfg = SorteoPrestigioCfg(),
)

@Serializable
data class RecompensaEspecieCfg(
    @SerialName("id_especie") val idEspecie: String,
    @SerialName("probabilidad_porcentual") val probabilidad: Int,
)

@Serializable
data class RetencionCfg(
    @SerialName("auditoria_evento") val auditoriaEvento: Int,
    @SerialName("movimiento_economico") val movimientoEconomico: Int,
    @SerialName("notificacion_cerrada") val notificacionCerrada: NotificacionRetencionCfg,
    @SerialName("beneficio_cerrado") val beneficioCerrado: Int,
    @SerialName("reproduccion_finalizada") val reproduccionFinalizada: Int,
    @SerialName("recompensa_mision_cerrada") val recompensaMisionCerrada: Int,
    val idempotencia: Int,
    @SerialName("ciclo_simulacion") val cicloSimulacion: Int,
    @SerialName("max_ciclos_offline") val maxCiclosOffline: Int,
)

@Serializable
data class NotificacionRetencionCfg(
    @SerialName("ventana_ciclos") val ventanaCiclos: Int,
    @SerialName("maximo_filas_por_partida") val maximoFilas: Int,
)

@Serializable
data class CatalogosCfg(
    val biomas: List<BiomaCfg>,
    val alimentos: List<AlimentoCfg>,
    @SerialName("insumos_medicos") val insumos: List<InsumoCfg>,
    val misiones: List<MisionCfg>,
    val habitats: List<HabitatCfg>,
    @SerialName("rasgos_personalidad") val rasgos: List<RasgoCfg>,
    @SerialName("habilidades_sociales") val habilidades: List<HabilidadCfg>,
)

@Serializable
data class BiomaCfg(@SerialName("id_bioma") val id: String, val nombre: String)

@Serializable
data class AlimentoCfg(@SerialName("id_alimento") val id: String, val nombre: String)

@Serializable
data class InsumoCfg(@SerialName("id_insumo") val id: String, val nombre: String)

@Serializable
data class RasgoCfg(@SerialName("id_rasgo") val id: String, val nombre: String)

@Serializable
data class HabilidadCfg(@SerialName("id_habilidad") val id: String, val nombre: String)

@Serializable
data class HabitatCfg(
    @SerialName("id_habitat") val codigo: String,
    val nombre: String,
    @SerialName("id_bioma") val idBioma: String,
)

@Serializable
data class MisionCfg(
    @SerialName("id_mision") val id: String,
    @SerialName("estado_mision") val estado: String,
    @SerialName("tipo_objetivo") val tipoObjetivo: String,
    @SerialName("naturaleza_progreso") val naturaleza: String,
    @SerialName("parametros_base") val parametrosBase: Map<String, JsonElement>,
    @SerialName("progresion_parametros") val progresion: Map<String, ProgresionCfg> = emptyMap(),
    @SerialName("recompensa_asociada") val recompensa: RecompensaMisionCfg,
)

@Serializable
data class ProgresionCfg(
    val operacion: String,
    val constante: Double,
    val minimo: Double,
    val maximo: Double,
)

@Serializable
data class RecompensaMisionCfg(
    val tipo: String,
    val contenido: Map<String, JsonElement>,
)

@Serializable
data class TablasCfg(
    @SerialName("niveles_prestigio") val nivelesPrestigio: List<NivelPrestigioCfg>,
    @SerialName("desbloqueos_prestigio") val desbloqueos: List<DesbloqueoNivelCfg>,
    @SerialName("rangos_especie") val rangos: List<RangoCfg>,
    @SerialName("consumo_medico_rango") val consumoMedico: List<ConsumoMedicoCfg>,
    @SerialName("costes_espacios") val costesEspacios: List<CosteEspacioCfg>,
    @SerialName("costes_alimentos") val costesAlimentos: List<CosteAlimentoCfg>,
    @SerialName("costes_rangos") val costesRangos: List<CosteRangoCfg>,
    @SerialName("costes_insumos") val costesInsumos: List<CosteInsumoCfg>,
    @SerialName("compatibilidad_especie_bioma") val compatibilidad: List<CompatibilidadCfg>,
    @SerialName("alimentos_por_especie") val alimentosPorEspecie: List<AlimentoEspecieCfg>,
    @SerialName("factores_mcrit_especie") val factoresMcrit: List<McritEspecieCfg>,
)

@Serializable
data class NivelPrestigioCfg(
    val nivel: Int,
    @SerialName("umbral_animales_sin_redondear") val umbralAnimalesSinRedondear: Double,
    @SerialName("umbral_animales_comparacion") val umbralAnimalesComparacion: Int,
    @SerialName("umbral_satisfaccion") val umbralSatisfaccion: Double,
)

@Serializable
data class DesbloqueoNivelCfg(
    val nivel: Int,
    val especies: List<String>,
    val habitats: List<String>,
)

@Serializable
data class RangoCfg(
    @SerialName("id_rango") val id: Int,
    val nombre: String,
    @SerialName("umbral_reproduccion") val umbralReproduccion: Double,
    @SerialName("probabilidad_reproduccion") val probabilidadReproduccion: Double,
    /** En desuso: la penalización lineal la sustituye [factoresHijos]. Se conserva en el esquema con valor 0. */
    @SerialName("penalizacion_hijo_reproduccion") val penalizacionHijo: Double = 0.0,
    /** Factor de `probabilidad_reproduccion` según los hijos vivos comunes (0 a 5); el último es 0: tope de cinco hijos. */
    @SerialName("factores_hijos") val factoresHijos: List<Double>,
    @SerialName("probabilidad_enfermedad") val probabilidadEnfermedad: Double,
    @SerialName("beneficio_base_animal_ciclo") val beneficioBase: Double,
    @SerialName("nivel_maximo") val nivelMaximo: Int,
    @SerialName("multiplicador_nivel_maximo") val multiplicadorNivelMaximo: Double,
    @SerialName("valor_venta_base") val valorVentaBase: Double,
    @SerialName("factor_venta_nivel_maximo") val factorVentaNivelMaximo: Double,
)

@Serializable
data class ConsumoMedicoCfg(
    @SerialName("id_rango") val idRango: Int,
    @SerialName("id_insumo") val idInsumo: String,
    @SerialName("cantidad_unidades") val cantidad: Int,
)

@Serializable
data class CosteEspacioCfg(
    @SerialName("capacidad_max") val capacidad: Int,
    val importe: Double,
)

@Serializable
data class CosteAlimentoCfg(
    @SerialName("id_alimento") val idAlimento: String,
    @SerialName("precio_por_kilogramo") val precio: Double,
)

@Serializable
data class CosteRangoCfg(
    @SerialName("id_rango") val idRango: Int,
    val importe: Double,
)

@Serializable
data class CosteInsumoCfg(
    @SerialName("id_insumo") val idInsumo: String,
    @SerialName("precio_por_unidad") val precio: Double,
)

@Serializable
data class CompatibilidadCfg(
    @SerialName("id_especie") val idEspecie: String,
    @SerialName("id_bioma") val idBioma: String,
    @SerialName("factor_bioma") val factor: Double,
)

@Serializable
data class AlimentoEspecieCfg(
    @SerialName("id_especie") val idEspecie: String,
    @SerialName("id_alimento") val idAlimento: String,
    @SerialName("cantidad_kilogramos_por_incremento_10_puntos") val kilogramos: Int,
)

@Serializable
data class McritEspecieCfg(
    @SerialName("id_especie") val idEspecie: String,
    @SerialName("m_alimentacion") val alimentacion: Map<String, Double>,
    @SerialName("m_enfermedad") val enfermedad: Map<String, Double>,
)

@Serializable
data class PartidaInicialCfg(
    val fondos: Double,
    @SerialName("inventario_alimento") val inventarioAlimento: Map<String, Int>,
    @SerialName("inventario_medico") val inventarioMedico: Map<String, Int>,
    @SerialName("prestigio_actual") val prestigioActual: Int,
    @SerialName("estado_partida") val estadoPartida: String,
    @SerialName("remanente_temporizador_segundos") val remanente: Int,
    @SerialName("estado_simulacion") val estadoSimulacion: String,
    @SerialName("espacio_gratuito") val espacioGratuito: EspacioGratuitoCfg = EspacioGratuitoCfg(),
    val bienvenida: BienvenidaCfg = BienvenidaCfg(),
)

/** Capacidad del primer espacio que se adquiere sin coste. */
@Serializable
data class EspacioGratuitoCfg(val capacidad: Int = 6)

/** Animales de regalo de la partida nueva: especies distintas, sorteadas sin tener en cuenta el prestigio. */
@Serializable
data class BienvenidaCfg(
    @SerialName("animales_comunes") val comunes: Int = 3,
    @SerialName("animales_poco_comunes") val pocoComunes: Int = 1,
    /** Un animal por tabla: rango según las probabilidades y después especie al azar dentro del rango. */
    val sorteos: List<List<ProbabilidadRango>> = listOf(
        listOf(ProbabilidadRango(3, 40), ProbabilidadRango(2, 60)),
        listOf(ProbabilidadRango(2, 50), ProbabilidadRango(4, 30), ProbabilidadRango(5, 20)),
    ),
)

/**
 * Regalo de un día de la semana: un animal gratuito (unidad de la tienda) cuyo rango se sortea con [sorteo] y cuya especie
 * se elige después dentro del rango. `diaSemana` es ISO: 1 = lunes … 7 = domingo.
 */
@Serializable
data class RegaloSemanalCfg(
    @SerialName("id_regalo") val id: String,
    val nombre: String,
    @SerialName("dia_semana") val diaSemana: Int,
    val sorteo: List<ProbabilidadRango>,
)

/** Probabilidad porcentual de que un animal sorteado sea de un rango. */
@Serializable
data class ProbabilidadRango(
    @SerialName("id_rango") val idRango: Int,
    @SerialName("probabilidad_porcentual") val porcentaje: Int,
)

/** Tablas de rango de la recompensa de prestigio. */
@Serializable
data class SorteoPrestigioCfg(
    val base: List<ProbabilidadRango> =
        listOf(ProbabilidadRango(2, 50), ProbabilidadRango(3, 25), ProbabilidadRango(4, 15), ProbabilidadRango(5, 10)),
    @SerialName("multiplo_5") val multiplo5: List<ProbabilidadRango> =
        listOf(ProbabilidadRango(2, 30), ProbabilidadRango(3, 40), ProbabilidadRango(4, 20), ProbabilidadRango(5, 10)),
    @SerialName("multiplo_10") val multiplo10: List<ProbabilidadRango> = listOf(ProbabilidadRango(5, 100)),
)
