package com.pandazoo.domain.animal

import com.pandazoo.domain.config.ConfiguracionJuego

/** Reglas de alta de un animal (RF-01, RF-34, RF-51). Todo animal nace o se compra en el nivel inicial. */
object ReglasAnimal {
    const val ALIMENTACION_INICIAL = 100
    const val HABILIDADES_POR_FICHA = 2
    const val RASGOS_POR_FICHA = 2
    const val MAX_NOMBRE = 40
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
