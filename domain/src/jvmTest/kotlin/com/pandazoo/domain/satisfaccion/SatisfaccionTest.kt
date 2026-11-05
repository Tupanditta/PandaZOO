package com.pandazoo.domain.satisfaccion

import com.pandazoo.domain.config.CompatibilidadCfg
import com.pandazoo.domain.config.ConfiguracionJuego
import com.pandazoo.domain.config.McritEspecieCfg
import com.pandazoo.domain.config.MatrizAfinidadCfg
import com.pandazoo.domain.config.ParAfinidadCfg
import com.pandazoo.domain.config.PesosBioSocCfg
import com.pandazoo.domain.config.PesosSocialesCfg
import com.pandazoo.domain.config.SatisfaccionSocialCfg
import com.pandazoo.domain.economia.Redondeo
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.booleans.shouldBeTrue
import java.io.File
import kotlin.random.Random
import kotlin.test.Test

/**
 * T2.1 a T2.6: fórmula de satisfacción con casos calculados a mano sobre una configuración controlada, y
 * propiedades sobre la configuración real.
 *
 * Configuración controlada: `leon` (bio 0.5 / soc 0.5) y `cebra` (bio 0.4 / soc 0.6), pesos sociales
 * pareja .10, amigo .10, padre .05, madre .05, hermanos .10, afinidad .60; tope de hermanos 4 (`leon`) y 2 (`cebra`).
 * Afinidades: leon-leon .8, cebra-leon .2, cebra-cebra .5. Compatibilidad: leon-b1 .8, leon-b2 1.0, cebra-b1 .5.
 * `M_alimentación(n) = 0.3 + 0.7·n/100`; `M_enfermedad`: sano 1.0, enfermo 0.6.
 */
class SatisfaccionTest {
    private val real: ConfiguracionJuego by lazy {
        ConfiguracionJuego.desdeJson(File("../IngenieriaSoftware/Otros/valores_y_pesos.json").readText(Charsets.UTF_8))
    }

    private val pesosSociales = PesosSocialesCfg(pareja = 0.10, amigo = 0.10, padre = 0.05, madre = 0.05, hermanos = 0.10, afinidad = 0.60)

    private val cfg: ConfiguracionJuego by lazy {
        val leon = real.especies.first { it.id == "leon" }
        val curva = (0..100 step 10).associate { it.toString() to 0.3 + 0.7 * it / 100 }
        val enfermedad = mapOf("SANO" to 1.0, "ENFERMO" to 0.6)
        real.copy(
            especies = listOf(
                leon.copy(id = "leon", pesos = PesosBioSocCfg(0.5, 0.5), social = SatisfaccionSocialCfg(pesosSociales, 4)),
                leon.copy(id = "cebra", pesos = PesosBioSocCfg(0.4, 0.6), social = SatisfaccionSocialCfg(pesosSociales, 2)),
            ),
            matrizAfinidad = MatrizAfinidadCfg(
                listOf(ParAfinidadCfg("leon", "leon", 0.8), ParAfinidadCfg("cebra", "leon", 0.2), ParAfinidadCfg("cebra", "cebra", 0.5)),
            ),
            tablas = real.tablas.copy(
                compatibilidad = listOf(
                    CompatibilidadCfg("leon", "b1", 0.8),
                    CompatibilidadCfg("leon", "b2", 1.0),
                    CompatibilidadCfg("cebra", "b1", 0.5),
                ),
                factoresMcrit = listOf(McritEspecieCfg("leon", curva, enfermedad), McritEspecieCfg("cebra", curva, enfermedad)),
            ),
        )
    }

    private val regla by lazy { ReglaSatisfaccion(cfg) }

    private fun animal(
        id: String,
        especie: String = "leon",
        espacio: String = "e1",
        alimentacion: Int = 100,
        enfermo: Boolean = false,
        pareja: String? = null,
        amigo: String? = null,
        padre: String? = null,
        madre: String? = null,
    ) = AnimalVivo(id, especie, espacio, alimentacion, enfermo, pareja, amigo, padre, madre)

    private fun s(a: AnimalVivo, bioma: String, todos: List<AnimalVivo>): Double =
        regla.calcular(a, bioma, todos.filter { it.idEspacio == a.idEspacio }, IndiceVivos(todos)).valor

    private val tol = 1e-12

    // ---- T2.1 ----

    @Test
    fun `T2_1 alimentacion 100 y sano da M_crit 1 y la parte biologica completa`() {
        val a = animal("a")
        val d = regla.calcular(a, "b2", listOf(a), IndiceVivos(listOf(a)))
        d.mCrit shouldBe (1.0 plusOrMinus tol)
        d.sBio shouldBe 1.0
        d.sSoc shouldBe 0.0
        d.valor shouldBe (0.5 plusOrMinus tol) // (0.5·1 + 0.5·0)·1·1
    }

    @Test
    fun `T2_1 alimentacion 0 reduce S_i`() {
        val a = animal("a", pareja = "p")
        val p = animal("p", pareja = "a")
        val cebra = animal("c", especie = "cebra")
        val todos = listOf(a, p, cebra)
        // S_soc = 0.10 (pareja) + 0.60·0.2 (cebra, única no familiar) = 0.22
        s(a, "b2", todos) shouldBe (0.61 plusOrMinus tol) // (0.5·1 + 0.5·0.22)·1·1
        val hambriento = a.copy(nivelAlimentacion = 0)
        s(hambriento, "b2", listOf(hambriento, p, cebra)) shouldBe (0.033 plusOrMinus tol) // (0 + 0.11)·1·0.3
    }

    @Test
    fun `T2_1 animal enfermo reduce S_i`() {
        val a = animal("a", pareja = "p")
        val p = animal("p", pareja = "a")
        val cebra = animal("c", especie = "cebra")
        val enfermo = a.copy(enfermo = true)
        s(enfermo, "b2", listOf(enfermo, p, cebra)) shouldBe (0.366 plusOrMinus tol) // (0.5 + 0.11)·1·0.6
    }

    @Test
    fun `T2_1 P_hab multiplica sin truncar`() {
        val a = animal("a", pareja = "p")
        val p = animal("p", pareja = "a")
        val cebra = animal("c", especie = "cebra")
        s(a, "b1", listOf(a, p, cebra)) shouldBe (0.488 plusOrMinus tol) // 0.61·0.8
    }

    // ---- T2.2 ----

    @Test
    fun `T2_2 una relacion inexistente aporta cero sin penalizacion adicional`() {
        val a = animal("a")
        val d = regla.calcular(a, "b2", listOf(a), IndiceVivos(listOf(a)))
        d.sSoc shouldBe 0.0
        d.valor shouldBe (0.5 plusOrMinus tol)
    }

    @Test
    fun `T2_2 amigo padre madre hermanos y afinidad usan los pesos de la especie`() {
        val padre = animal("pa", espacio = "e2")
        val madre = animal("ma", espacio = "e3")
        val yo = animal("yo", amigo = "am", padre = "pa", madre = "ma")
        val amigo = animal("am", amigo = "yo")
        val h1 = animal("h1", espacio = "e4", padre = "pa", madre = "ma")
        val h2 = animal("h2", espacio = "e5", padre = "pa")
        val todos = listOf(padre, madre, yo, amigo, h1, h2)
        // amigo .10 + padre .05 + madre .05 + hermanos .10·min(1, 2/4) + afinidad .60·(leon-leon .8, el amigo no es familiar)
        val d = regla.calcular(yo, "b2", todos.filter { it.idEspacio == "e1" }, IndiceVivos(todos))
        d.sSoc shouldBe (0.73 plusOrMinus tol)
    }

    @Test
    fun `T2_2 una pareja muerta no cuenta aunque el vinculo exista en la ficha`() {
        val a = animal("a", pareja = "muerto")
        val d = regla.calcular(a, "b2", listOf(a), IndiceVivos(listOf(a)))
        d.sSoc shouldBe 0.0
    }

    @Test
    fun `T2_2 los hermanos se limitan al tope y la afinidad excluye a la familia pero no al amigo`() {
        val padre = animal("pa", espacio = "e2")
        val yo = animal("yo", especie = "cebra", padre = "pa") // tope 2
        val hermanos = (1..5).map { animal("h$it", especie = "cebra", padre = "pa") }
        val todos = listOf(padre, yo) + hermanos
        val d = regla.calcular(yo, "b1", todos.filter { it.idEspacio == "e1" }, IndiceVivos(todos))
        // padre .05 + hermanos .10·min(1, 5/2) = .10; los cinco hermanos son familia: sin no familiares C_afinidad = 0
        d.sSoc shouldBe (0.15 plusOrMinus tol)
    }

    @Test
    fun `T2_2 la afinidad es la media con los no familiares de otra y de la misma especie`() {
        val yo = animal("yo")
        val leon = animal("l")
        val cebra = animal("c", especie = "cebra")
        val d = regla.calcular(yo, "b2", listOf(yo, leon, cebra), IndiceVivos(listOf(yo, leon, cebra)))
        d.sSoc shouldBe (0.6 * (0.8 + 0.2) / 2 plusOrMinus tol) // 0.30
    }

    @Test
    fun `T2_2 la afinidad es simetrica`() {
        regla.afinidad("cebra", "leon") shouldBe regla.afinidad("leon", "cebra")
    }

    @Test
    fun `T2_2 los hijos vivos cuentan como familia y no como afinidad`() {
        val yo = animal("yo")
        val hijo = animal("hijo", padre = "yo")
        val d = regla.calcular(yo, "b2", listOf(yo, hijo), IndiceVivos(listOf(yo, hijo)))
        d.sSoc shouldBe 0.0
    }

    // ---- T2.3 ----

    @Test
    fun `T2_3 S_i esta siempre en 0 a 1 para entradas extremas con valores sinteticos`() {
        val azar = Random(42)
        val bioma = listOf("b1", "b2")
        repeat(5_000) {
            val n = 1 + azar.nextInt(8)
            val ids = (0 until n).map { "a$it" }
            val animales = ids.map { id ->
                animal(
                    id,
                    especie = if (azar.nextBoolean()) "leon" else "cebra",
                    alimentacion = azar.nextInt(0, 11) * 10,
                    enfermo = azar.nextBoolean(),
                    pareja = ids.random(azar).takeIf { azar.nextBoolean() && it != id },
                    amigo = ids.random(azar).takeIf { azar.nextBoolean() && it != id },
                    padre = ids.random(azar).takeIf { azar.nextBoolean() && it != id },
                    madre = ids.random(azar).takeIf { azar.nextBoolean() && it != id },
                )
            }
            val indice = IndiceVivos(animales)
            animales.forEach {
                val b = if (it.idEspecie == "cebra") "b1" else bioma.random(azar)
                val v = regla.calcular(it, b, animales, indice).valor
                (v in 0.0..1.0).shouldBeTrue()
            }
        }
    }

    @Test
    fun `T2_3 S_i esta en 0 a 1 para todas las especies biomas niveles y salud de la configuracion real`() {
        val reglaReal = ReglaSatisfaccion(real)
        val biomas = real.catalogos.biomas.map { it.id }
        val fuera = mutableListOf<String>()
        for (e in real.especies) for (b in biomas) for (nivel in 0..100 step 10) for (enfermo in listOf(false, true)) {
            // Entorno social máximo: pareja, amigo, padre, madre y 20 hermanos vivos, todos de la misma especie.
            val padre = AnimalVivo("pa", e.id, "x", 100, false)
            val madre = AnimalVivo("ma", e.id, "x", 100, false)
            val yo = AnimalVivo("yo", e.id, "e", nivel, enfermo, "pareja", "amigo", "pa", "ma")
            val resto = listOf(
                AnimalVivo("pareja", e.id, "e", 100, false, "yo"),
                AnimalVivo("amigo", e.id, "e", 100, false, "yo"),
            ) + (1..20).map { AnimalVivo("h$it", e.id, "x", 100, false, idPadre = "pa", idMadre = "ma") }
            val todos = listOf(padre, madre, yo) + resto
            val v = reglaReal.calcular(yo, b, todos.filter { it.idEspacio == "e" }, IndiceVivos(todos)).valor
            if (v !in 0.0..1.0) fuera += "${e.id}/$b/$nivel/$enfermo=$v"
        }
        fuera.shouldBeEmpty()
    }

    // ---- T2.5 ----

    @Test
    fun `T2_5 el porcentaje mostrado redondea los medios hacia arriba`() {
        Redondeo.porcentaje(0.495) shouldBe 50
        Redondeo.porcentaje(0.494) shouldBe 49
        Redondeo.porcentaje(0.0) shouldBe 0
        Redondeo.porcentaje(1.0) shouldBe 100
        Redondeo.porcentaje(0.005) shouldBe 1
        Redondeo.porcentaje(0.004) shouldBe 0
    }

    @Test
    fun `T2_5 el valor interno no se redondea`() {
        val a = animal("a", pareja = "p")
        val p = animal("p", pareja = "a")
        val cebra = animal("c", especie = "cebra")
        val v = s(a, "b1", listOf(a, p, cebra))
        v shouldBe (0.488 plusOrMinus tol)
        (v == 0.49 || v == 0.5).shouldBe(false)
        val tres = animal("t", alimentacion = 30)
        regla.calcular(tres, "b2", listOf(tres), IndiceVivos(listOf(tres))).sBio shouldBe 0.3
    }

    // ---- T2.6 ----

    @Test
    fun `T2_6 espacio vacio sin metrica habitat con un solo espacio con animales usa solo ese`() {
        val medias = Medias.calcular(
            listOf(ValorAlojado("e1", "h1", 0.4), ValorAlojado("e1", "h1", 0.6)),
        )
        medias.espacios.keys shouldBe setOf("e1") // e2, sin animales, no aparece
        medias.habitats shouldBe mapOf("h1" to (0.5))
        medias.parque shouldBe 0.5
    }

    @Test
    fun `T2_6 las medias son directas sobre los animales de cada nivel`() {
        val medias = Medias.calcular(
            listOf(
                ValorAlojado("e1", "h1", 1.0),
                ValorAlojado("e2", "h1", 0.0),
                ValorAlojado("e2", "h1", 0.5),
                ValorAlojado("e3", "h2", 0.3),
            ),
        )
        medias.espacios["e1"] shouldBe 1.0
        medias.espacios["e2"] shouldBe 0.25
        medias.habitats["h1"]!! shouldBe (0.5 plusOrMinus tol) // (1 + 0 + .5) / 3, no la media de medias (.625)
        medias.habitats["h2"] shouldBe 0.3
        medias.parque shouldBe (1.8 / 4 plusOrMinus tol)
    }

    @Test
    fun `T2_6 el parque sin animales vale cero y no hay metricas de espacio ni de habitat`() {
        val medias = Medias.calcular(emptyList())
        medias.parque shouldBe 0.0
        medias.espacios.isEmpty().shouldBeTrue()
        medias.habitats.isEmpty().shouldBeTrue()
    }

    @Test
    fun `un factor ausente en la configuracion es un error y no se sustituye por un valor`() {
        val sinCompat = ReglaSatisfaccion(cfg.copy(tablas = cfg.tablas.copy(compatibilidad = emptyList())))
        val a = animal("a")
        val fallo = runCatching { sinCompat.calcular(a, "b2", listOf(a), IndiceVivos(listOf(a))) }
        fallo.isFailure.shouldBeTrue()
    }
}
