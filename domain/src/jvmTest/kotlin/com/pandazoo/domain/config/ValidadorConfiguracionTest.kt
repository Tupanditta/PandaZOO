package com.pandazoo.domain.config

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import com.pandazoo.domain.animal.ReglasNivel
import kotlin.test.Test
import java.io.File

/** T0.2 El validador acepta el JSON actual y rechaza variantes rotas. */
class ValidadorConfiguracionTest {
    private val base: ConfiguracionJuego by lazy {
        ConfiguracionJuego.desdeJson(File("../IngenieriaSoftware/Otros/valores_y_pesos.json").readText(Charsets.UTF_8))
    }

    private fun errores(cfg: ConfiguracionJuego) = ValidadorConfiguracion.validar(cfg)

    private fun List<String>.algunoContiene(texto: String) = any { it.contains(texto) }

    @Test
    fun `T0_2 acepta el JSON actual`() {
        errores(base).shouldBeEmpty()
    }

    @Test
    fun `T0_2 rechaza pesos bio y soc que no suman 1`() {
        val e = base.especies.toMutableList()
        e[0] = e[0].copy(pesos = PesosBioSocCfg(0.6, 0.6))
        errores(base.copy(especies = e)).algunoContiene("peso_bio + peso_soc").shouldBeTrue()
    }

    @Test
    fun `T0_2 rechaza pesos sociales que no suman 1`() {
        val e = base.especies.toMutableList()
        val s = e[0]
        e[0] = s.copy(social = s.social.copy(pesos = s.social.pesos.copy(pareja = s.social.pesos.pareja + 0.05)))
        errores(base.copy(especies = e)).algunoContiene("pesos sociales").shouldBeTrue()
    }

    @Test
    fun `T0_2 rechaza peso de afinidad no mayor que la suma familiar`() {
        val e = base.especies.toMutableList()
        e[0] = e[0].copy(social = e[0].social.copy(pesos = PesosSocialesCfg(0.2, 0.2, 0.1, 0.1, 0.1, 0.3)))
        errores(base.copy(especies = e)).algunoContiene("no supera la suma de los pesos familiares").shouldBeTrue()
    }

    @Test
    fun `T0_2 rechaza probabilidades de recompensa que no suman 100`() {
        val r = base.recompensasAnimales.especies.toMutableList()
        r[0] = r[0].copy(probabilidad = r[0].probabilidad + 1)
        errores(base.copy(recompensasAnimales = RecompensasAnimalesCfg(r))).algunoContiene("no suman 100").shouldBeTrue()
    }

    @Test
    fun `T0_2 rechaza falta de precio de bambu`() {
        val t = base.tablas.copy(costesAlimentos = emptyList())
        errores(base.copy(tablas = t)).algunoContiene("precio del bambú").shouldBeTrue()
    }

    @Test
    fun `T0_2 rechaza falta de M_crit`() {
        val t = base.tablas.copy(factoresMcrit = base.tablas.factoresMcrit.drop(1))
        errores(base.copy(tablas = t)).algunoContiene("M_crit").shouldBeTrue()
    }

    @Test
    fun `rechaza compatibilidad ausente sin asignar valor por defecto`() {
        val t = base.tablas.copy(compatibilidad = base.tablas.compatibilidad.drop(1))
        errores(base.copy(tablas = t)).algunoContiene("compatibilidad: faltan").shouldBeTrue()
    }

    @Test
    fun `rechaza afinidad fuera de rango y pares ausentes`() {
        val p = base.matrizAfinidad.pares.toMutableList()
        p[0] = p[0].copy(valor = 1.5)
        errores(base.copy(matrizAfinidad = MatrizAfinidadCfg(p))).algunoContiene("fuera de 0..1").shouldBeTrue()
        errores(base.copy(matrizAfinidad = MatrizAfinidadCfg(p.drop(1)))).algunoContiene("faltan").shouldBeTrue()
    }

    @Test
    fun `rechaza capacidad de espacio fuera de tabla`() {
        val t = base.tablas.copy(costesEspacios = base.tablas.costesEspacios.dropLast(1))
        errores(base.copy(tablas = t)).shouldNotBeEmpty()
    }

    @Test
    fun `rechaza umbral de prestigio mayor que 1`() {
        val n = base.tablas.nivelesPrestigio.toMutableList()
        n[n.lastIndex] = n.last().copy(umbralSatisfaccion = 1.2)
        errores(base.copy(tablas = base.tablas.copy(nivelesPrestigio = n))).algunoContiene("fuera de (0,1]").shouldBeTrue()
    }

    @Test
    fun `rechaza json incompleto con excepcion de decodificacion`() {
        val roto = File("../IngenieriaSoftware/Otros/valores_y_pesos.json").readText(Charsets.UTF_8)
            .replace("\"partida_inicial\"", "\"otra_cosa\"")
        val ex = runCatching { ConfiguracionJuego.desdeJson(roto) }.exceptionOrNull()
        (ex != null).shouldBeTrue()
    }

    @Test
    fun `validarOExcepcion lanza con los errores`() {
        val t = base.tablas.copy(costesAlimentos = emptyList())
        val ex = runCatching { ValidadorConfiguracion.validarOExcepcion(base.copy(tablas = t)) }.exceptionOrNull()
        ex!!.message!! shouldContain "bambú"
    }

    // ---- TN.1 Reglas de nivel, beneficio y venta (Sprint N) ----

    private fun conRango(indice: Int, cambio: (RangoCfg) -> RangoCfg): ConfiguracionJuego {
        val rangos = base.tablas.rangos.toMutableList()
        rangos[indice] = cambio(rangos[indice])
        return base.copy(tablas = base.tablas.copy(rangos = rangos))
    }

    @Test
    fun `TN_1 la configuracion 1_1 declara los valores de nivel esperados`() {
        base.reglas.nivel.nivelInicial shouldBe 1
        base.reglas.nivel.ciclosPorNivel shouldBe 72
        base.reglas.nivel.nivelMinimoPareja shouldBe 4
        base.reglas.venta.factorNivel1 shouldBe 0.05
        base.reglas.venta.fraccionEquilibrio shouldBe 0.6667
        base.tablas.rangos.map { it.nivelMaximo } shouldBe listOf(15, 29, 41, 51, 61)
    }

    @Test
    fun `TN_1 las constantes del dominio coinciden con el JSON`() {
        ReglasNivel.NIVEL_INICIAL shouldBe base.reglas.nivel.nivelInicial
        ReglasNivel.CICLOS_POR_NIVEL shouldBe base.reglas.nivel.ciclosPorNivel
        ReglasNivel.NIVEL_MINIMO_PAREJA shouldBe base.reglas.nivel.nivelMinimoPareja
        ReglasNivel.FACTOR_VENTA_NIVEL_1 shouldBe base.reglas.venta.factorNivel1
        ReglasNivel.FRACCION_EQUILIBRIO shouldBe base.reglas.venta.fraccionEquilibrio
    }

    @Test
    fun `TN_1 rechaza nivel_maximo menor que 2 y multiplicador menor que 1`() {
        errores(conRango(0) { it.copy(nivelMaximo = 1) }).algunoContiene("nivel_maximo").shouldBeTrue()
        errores(conRango(0) { it.copy(multiplicadorNivelMaximo = 0.9) }).algunoContiene("multiplicador_nivel_maximo").shouldBeTrue()
    }

    @Test
    fun `TN_1 rechaza ciclos_por_nivel menor que 1 y pareja por encima del menor tope`() {
        val n = base.reglas.nivel
        errores(base.copy(reglas = base.reglas.copy(nivel = n.copy(ciclosPorNivel = 0)))).algunoContiene("ciclos_por_nivel").shouldBeTrue()
        errores(base.copy(reglas = base.reglas.copy(nivel = n.copy(nivelMinimoPareja = 16)))).algunoContiene("nivel_minimo_pareja").shouldBeTrue()
    }

    @Test
    fun `TN_1 rechaza factores de venta fuera de rango y nivel de equilibrio invalido`() {
        val v = base.reglas.venta
        errores(base.copy(reglas = base.reglas.copy(venta = v.copy(factorNivel1 = 1.0)))).algunoContiene("factor_venta_nivel_1").shouldBeTrue()
        errores(base.copy(reglas = base.reglas.copy(venta = v.copy(factorNivel1 = 0.0)))).algunoContiene("factor_venta_nivel_1").shouldBeTrue()
        errores(conRango(0) { it.copy(factorVentaNivelMaximo = 1.0) }).algunoContiene("factor_venta_nivel_maximo").shouldBeTrue()
        // Con nivel_maximo = 2 el equilibrio (redondeo de 2 · 0.6667 = 1) queda fuera de [2, nivel_maximo).
        errores(conRango(0) { it.copy(nivelMaximo = 2) }).algunoContiene("nivel de equilibrio").shouldBeTrue()
    }

    @Test
    fun `TN_1 rechaza valor_venta_base distinto del coste y ventas decrecientes con el rango`() {
        errores(conRango(0) { it.copy(valorVentaBase = 199.0) }).algunoContiene("valor_venta_base debe ser igual al coste").shouldBeTrue()
        // Rango 5 vale menos que el 4: el valor de venta decrece con el rango.
        errores(conRango(4) { it.copy(valorVentaBase = 1000.0) }).algunoContiene("decrece").shouldBeTrue()
    }

    @Test
    fun `TN_1 rechaza un parametro de mision de nivel por encima del tope o un minimo de crecimiento menor que 2`() {
        fun conProgresion(id: String, cambio: (ProgresionCfg) -> ProgresionCfg): ConfiguracionJuego {
            val misiones = base.catalogos.misiones.map { m ->
                if (m.id == id) m.copy(progresion = m.progresion + ("nivel_requerido" to cambio(m.progresion.getValue("nivel_requerido")))) else m
            }
            return base.copy(catalogos = base.catalogos.copy(misiones = misiones))
        }
        errores(conProgresion("vender_nivel") { it.copy(maximo = 61.0) }).algunoContiene("supera el nivel_maximo").shouldBeTrue()
        errores(conProgresion("crecer_especie") { it.copy(maximo = 16.0) }).algunoContiene("supera").shouldBeTrue()
        errores(conProgresion("crecer_especie") { it.copy(minimo = 1.0) }).algunoContiene("al menos 2").shouldBeTrue()
    }

    @Test
    fun `TN_1 la copia de recursos de la interfaz es identica a la original`() {
        val original = File("../IngenieriaSoftware/Otros/valores_y_pesos.json").readText(Charsets.UTF_8)
        val copia = File("../shared-ui/src/commonMain/composeResources/files/valores_y_pesos.json").readText(Charsets.UTF_8)
        (original == copia).shouldBeTrue()
    }
}
