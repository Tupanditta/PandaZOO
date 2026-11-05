package com.pandazoo.domain.animal

import com.pandazoo.domain.config.ConfiguracionJuego
import com.pandazoo.domain.economia.Redondeo

/** Edad con el calendario fijo del juego: 30 días por mes y 12 meses por año (RF-14). */
data class Edad(val anios: Int, val meses: Int, val dias: Int) {
    val totalDias: Int get() = anios * DIAS_ANIO + meses * DIAS_MES + dias

    override fun toString(): String = "$anios años, $meses meses y $dias días"

    companion object {
        const val DIAS_MES = 30
        const val DIAS_ANIO = 360

        fun deDias(total: Int): Edad = Edad(total / DIAS_ANIO, (total % DIAS_ANIO) / DIAS_MES, total % DIAS_MES)
    }
}

/** Reglas de alta de un animal comprado (RF-01, RF-14, RF-34). */
object ReglasAnimal {
    const val EDAD_ALTA_ANIOS = 20
    const val EDAD_MINIMA_PAREJA_ANIOS = 16
    const val FACTOR_EDAD_MIN = 0.90
    const val FACTOR_EDAD_MAX = 1.10
    const val ALIMENTACION_INICIAL = 100
    const val HABILIDADES_POR_FICHA = 2
    const val RASGOS_POR_FICHA = 2
    const val MAX_NOMBRE = 40

    /** Factor de longevidad uniforme en `[0.90, 1.10]` a partir de un aleatorio uniforme en `[0,1)`. */
    fun factorEdad(aleatorio: Double): Double = FACTOR_EDAD_MIN + aleatorio * (FACTOR_EDAD_MAX - FACTOR_EDAD_MIN)

    /** `dias = redondear_medio_arriba(edad_media * 360 * factor)`, descompuesto con el calendario fijo. */
    fun edadMaxima(edadMediaAnios: Int, factor: Double): Edad =
        Edad.deDias(Redondeo.entero(edadMediaAnios * Edad.DIAS_ANIO * factor))
}

/** Opción de un catálogo cerrado (rasgos de personalidad o habilidades sociales). `id` es técnico; `nombre` se muestra. */
data class OpcionFicha(val id: String, val nombre: String)

fun ConfiguracionJuego.catalogoFicha(): CatalogoFicha =
    CatalogoFicha(
        habilidades = catalogos.habilidades.map { OpcionFicha(it.id, it.nombre) },
        rasgos = catalogos.rasgos.map { OpcionFicha(it.id, it.nombre) },
    )

/** Catálogos cerrados de los que se eligen exactamente dos habilidades y dos rasgos (RF-33). */
class CatalogoFicha(val habilidades: List<OpcionFicha>, val rasgos: List<OpcionFicha>) {
    fun nombreHabilidad(id: String): String = habilidades.firstOrNull { it.id == id }?.nombre ?: id

    fun nombreRasgo(id: String): String = rasgos.firstOrNull { it.id == id }?.nombre ?: id

    /** Dos elementos distintos y todos del catálogo. */
    fun habilidadesValidas(ids: List<String>): Boolean = validas(ids, habilidades, ReglasAnimal.HABILIDADES_POR_FICHA)

    fun rasgosValidos(ids: List<String>): Boolean = validas(ids, rasgos, ReglasAnimal.RASGOS_POR_FICHA)

    private fun validas(ids: List<String>, catalogo: List<OpcionFicha>, cuantas: Int) =
        ids.size == cuantas && ids.toSet().size == cuantas && ids.all { id -> catalogo.any { it.id == id } }
}

/**
 * Reglas puras del cuidado manual (RF-12, RF-13, RF-30). La alimentación siempre es `0` o múltiplo de `10`
 * y el consumo de bambú es un número entero de kilogramos por cada incremento de 10 puntos.
 */
object ReglasCuidado {
    const val INCREMENTO_PUNTOS = 10
    const val ALIMENTACION_MAXIMA = 100

    /** Incrementos de 10 puntos que faltan para llegar al 100 %. Nunca negativo. */
    fun incrementosHastaMaximo(nivel: Int): Int = ((ALIMENTACION_MAXIMA - nivel).coerceAtLeast(0)) / INCREMENTO_PUNTOS

    /** Nivel tras una ración individual: suma 10 puntos con tope en el 100 %. */
    fun nivelTrasRacion(nivel: Int): Int = (nivel + INCREMENTO_PUNTOS).coerceAtMost(ALIMENTACION_MAXIMA)

    /** Kilogramos para llevar un animal al 100 %. */
    fun kilosHastaMaximo(nivel: Int, kgPorIncremento: Int): Int = incrementosHastaMaximo(nivel) * kgPorIncremento
}
