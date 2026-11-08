package com.pandazoo.domain.satisfaccion

import com.pandazoo.domain.config.ConfiguracionJuego
import com.pandazoo.domain.config.EspecieCfg
import com.pandazoo.domain.config.McritEspecieCfg

/**
 * Entrada del cálculo: estado confirmado de un animal vivo alojado en un espacio (`EspecificacionBBDD.md`, 4.2).
 * Los vínculos son los identificadores guardados en la ficha; solo cuentan si el animal vinculado sigue vivo.
 */
data class AnimalVivo(
    val id: String,
    val idEspecie: String,
    val idEspacio: String,
    val nivelAlimentacion: Int,
    val enfermo: Boolean,
    val idPareja: String? = null,
    val idMejorAmigo: String? = null,
    val idPadre: String? = null,
    val idMadre: String? = null,
)

/** Componentes de `S_i`, sin redondear. `valor` es el resultado de la ecuación. */
data class DesgloseSatisfaccion(
    val sBio: Double,
    val sSoc: Double,
    val pHab: Double,
    val mCrit: Double,
    val valor: Double,
)

/** Una parte de `S_soc`: peso del vínculo en la especie y grado (0..1) con que se cumple. */
data class AportacionSocial(val nombre: String, val peso: Double, val grado: Double)

data class DetalleSocial(val aportaciones: List<AportacionSocial>, val valor: Double)

/** [desglose] con los pesos de `S_bio` y `S_soc` y las partes de `S_soc` y `M_crit`, para explicar la satisfacción. */
data class DetalleSatisfaccion(
    val desglose: DesgloseSatisfaccion,
    val pesoBio: Double,
    val pesoSoc: Double,
    val social: DetalleSocial,
    val mAlimentacion: Double,
    val mEnfermedad: Double,
)

/** Índice de los animales vivos alojados de la partida; resuelve vínculos y parentesco sin recorrer listas. */
class IndiceVivos(animales: List<AnimalVivo>) {
    private val porId: Map<String, AnimalVivo> = animales.associateBy { it.id }
    private val hijosDePadre: Map<String, List<AnimalVivo>> = animales.filter { it.idPadre != null }.groupBy { it.idPadre!! }
    private val hijosDeMadre: Map<String, List<AnimalVivo>> = animales.filter { it.idMadre != null }.groupBy { it.idMadre!! }

    fun vivo(id: String?): Boolean = id != null && id in porId

    /** Vivos que comparten padre o madre con `a` (`num_hermanos` cuenta solo animales vivos). */
    fun hermanos(a: AnimalVivo): Set<String> {
        val ids = HashSet<String>()
        a.idPadre?.let { p -> hijosDePadre[p]?.forEach { ids += it.id } }
        a.idMadre?.let { m -> hijosDeMadre[m]?.forEach { ids += it.id } }
        ids -= a.id
        return ids
    }

    fun hijos(a: AnimalVivo): Set<String> {
        val ids = HashSet<String>()
        hijosDePadre[a.id]?.forEach { ids += it.id }
        hijosDeMadre[a.id]?.forEach { ids += it.id }
        return ids
    }
}

/**
 * Regla formal de satisfacción (`EspecificacionBBDD.md`, sección 4.2):
 * `S_i = (w_bio·S_bio + w_soc·S_soc) · P_hab · M_crit`, todo en `0..1` y sin redondeo interno.
 *
 * Es dominio puro: no lee ni escribe nada y no decide cuándo se ejecuta. Quien la invoca es el orquestador de
 * recálculo, y solo ante un evento de dominio (RF-44, RES-10). Los valores salen de la configuración ya
 * validada: la ausencia de un factor es un error de configuración y no se sustituye por un valor supuesto.
 */
class ReglaSatisfaccion(cfg: ConfiguracionJuego) {
    private val especies: Map<String, EspecieCfg> = cfg.especies.associateBy { it.id }
    private val afinidades: Map<Pair<String, String>, Double> =
        cfg.matrizAfinidad.pares.associate { clave(it.a, it.b) to it.valor }
    private val compatibilidad: Map<Pair<String, String>, Double> =
        cfg.tablas.compatibilidad.associate { (it.idEspecie to it.idBioma) to it.factor }
    private val factoresCriticos: Map<String, McritEspecieCfg> = cfg.tablas.factoresMcrit.associateBy { it.idEspecie }

    /** `S_bio = alimentación / 100`. */
    fun sBio(nivelAlimentacion: Int): Double = nivelAlimentacion / 100.0

    /** Afinidad simétrica entre dos especies; el par es normalizado (`a <= b`) y la diagonal es la propia especie. */
    fun afinidad(a: String, b: String): Double =
        afinidades[clave(a, b)] ?: error("Falta la afinidad entre dos especies en la configuración")

    /** `P_hab` de la combinación especie-bioma. Modifica la satisfacción, nunca impide ubicar animales. */
    fun pHab(idEspecie: String, idBioma: String): Double =
        compatibilidad[idEspecie to idBioma] ?: error("Falta la compatibilidad especie-bioma en la configuración")

    /** `M_crit = M_alimentación · M_enfermedad`. */
    fun mCrit(idEspecie: String, nivelAlimentacion: Int, enfermo: Boolean): Double {
        val f = factoresCriticos[idEspecie] ?: error("Faltan los factores M_crit de una especie")
        val alimentacion = f.alimentacion[nivelAlimentacion.toString()] ?: error("Falta un nivel de M_alimentación")
        val enfermedad = f.enfermedad[if (enfermo) "ENFERMO" else "SANO"] ?: error("Falta un factor M_enfermedad")
        return alimentacion * enfermedad
    }

    /**
     * Satisfacción de `animal` alojado en un espacio de bioma `idBioma`. `delEspacio` son los animales vivos de
     * ese espacio (puede incluir al propio animal) e `indice` resuelve vínculos y parentesco de toda la partida.
     */
    fun calcular(animal: AnimalVivo, idBioma: String, delEspacio: List<AnimalVivo>, indice: IndiceVivos): DesgloseSatisfaccion {
        val especie = especies[animal.idEspecie] ?: error("Especie no configurada")
        val sBio = sBio(animal.nivelAlimentacion)
        val sSoc = sSoc(animal, especie, delEspacio, indice)
        val pHab = pHab(animal.idEspecie, idBioma)
        val mCrit = mCrit(animal.idEspecie, animal.nivelAlimentacion, animal.enfermo)
        val bruto = (especie.pesos.bio * sBio + especie.pesos.soc * sSoc) * pHab * mCrit
        // Guarda numérica: con pesos que suman 1.00 el resultado ya está en 0..1; solo corrige el error de coma
        // flotante de la suma (por ejemplo 1.0000000000000002), no aplica ninguna saturación de negocio.
        return DesgloseSatisfaccion(sBio, sSoc, pHab, mCrit, bruto.coerceIn(0.0, 1.0))
    }

    private fun sSoc(animal: AnimalVivo, especie: EspecieCfg, delEspacio: List<AnimalVivo>, indice: IndiceVivos): Double =
        detalleSocial(animal, especie, delEspacio, indice).valor

    private fun detalleSocial(animal: AnimalVivo, especie: EspecieCfg, delEspacio: List<AnimalVivo>, indice: IndiceVivos): DetalleSocial {
        val w = especie.social.pesos
        val cPareja = if (indice.vivo(animal.idPareja)) 1.0 else 0.0
        val cAmigo = if (indice.vivo(animal.idMejorAmigo)) 1.0 else 0.0
        val cPadre = if (indice.vivo(animal.idPadre)) 1.0 else 0.0
        val cMadre = if (indice.vivo(animal.idMadre)) 1.0 else 0.0
        val hermanos = indice.hermanos(animal)
        val cHermanos = minOf(1.0, hermanos.size.toDouble() / especie.social.topeHermanos)

        // Familia: pareja, padre, madre, hermanos e hijos vivos. El mejor amigo no es familiar.
        val familia = HashSet<String>(hermanos)
        familia += indice.hijos(animal)
        animal.idPareja?.let { familia += it }
        animal.idPadre?.let { familia += it }
        animal.idMadre?.let { familia += it }
        val noFamiliares = delEspacio.filter { it.id != animal.id && it.id !in familia }
        val cAfinidad =
            if (noFamiliares.isEmpty()) 0.0 else noFamiliares.sumOf { afinidad(animal.idEspecie, it.idEspecie) } / noFamiliares.size

        val bruto = w.pareja * cPareja + w.amigo * cAmigo + w.padre * cPadre + w.madre * cMadre +
            w.hermanos * cHermanos + w.afinidad * cAfinidad
        return DetalleSocial(
            aportaciones = listOf(
                AportacionSocial("Pareja", w.pareja, cPareja),
                AportacionSocial("Mejor amigo", w.amigo, cAmigo),
                AportacionSocial("Padre", w.padre, cPadre),
                AportacionSocial("Madre", w.madre, cMadre),
                AportacionSocial("Hermanos", w.hermanos, cHermanos),
                AportacionSocial("Afinidad con el espacio", w.afinidad, cAfinidad),
            ),
            valor = bruto.coerceIn(0.0, 1.0),
        )
    }

    /** Desglose completo para mostrarlo: los mismos valores que [calcular] más los pesos y las partes de `S_soc` y `M_crit`. */
    fun detalle(animal: AnimalVivo, idBioma: String, delEspacio: List<AnimalVivo>, indice: IndiceVivos): DetalleSatisfaccion {
        val especie = especies[animal.idEspecie] ?: error("Especie no configurada")
        val f = factoresCriticos[animal.idEspecie] ?: error("Faltan los factores M_crit de una especie")
        return DetalleSatisfaccion(
            desglose = calcular(animal, idBioma, delEspacio, indice),
            pesoBio = especie.pesos.bio,
            pesoSoc = especie.pesos.soc,
            social = detalleSocial(animal, especie, delEspacio, indice),
            mAlimentacion = f.alimentacion[animal.nivelAlimentacion.toString()] ?: error("Falta un nivel de M_alimentación"),
            mEnfermedad = f.enfermedad[if (animal.enfermo) "ENFERMO" else "SANO"] ?: error("Falta un factor M_enfermedad"),
        )
    }

    private fun clave(a: String, b: String) = if (a <= b) a to b else b to a
}

/** Satisfacción individual de un animal vivo y su ubicación, entrada de las medias. */
data class ValorAlojado(val idEspacio: String, val idHabitat: String, val valor: Double)

/** Medias de los tres niveles. Un espacio u hábitat sin animales vivos no aparece (queda sin métrica). */
data class MediasSatisfaccion(
    val espacios: Map<String, Double>,
    val habitats: Map<String, Double>,
    val parque: Double,
)

object Medias {
    /**
     * Cada nivel es la media directa de los animales vivos que contiene (`EspecificacionBBDD.md`, 4.3). Un nivel
     * sin animales no tiene valor ni entra en las medias superiores; el parque sin animales vale `0`.
     */
    fun calcular(valores: List<ValorAlojado>): MediasSatisfaccion {
        fun media(v: List<ValorAlojado>) = (v.sumOf { it.valor } / v.size).coerceIn(0.0, 1.0)
        return MediasSatisfaccion(
            espacios = valores.groupBy { it.idEspacio }.mapValues { media(it.value) },
            habitats = valores.groupBy { it.idHabitat }.mapValues { media(it.value) },
            parque = if (valores.isEmpty()) 0.0 else media(valores),
        )
    }
}
