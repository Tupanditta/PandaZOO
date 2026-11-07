package com.pandazoo.domain.mision

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** T9.2, T9.4 y T9.7 (parte de dominio): objetivos, progresión de parámetros y sorteo ponderado. */
class MisionTest {
    private val sumar1 = Progresion("SUMAR", 1.0, 1.0, 50.0)

    @Test
    fun `T9_2 el objetivo sale del parametro de cada tipo y las ventas valen uno`() {
        ReglasMision.objetivo("comprar_especie", mapOf("cantidad_requerida" to 2.0)) shouldBe 2.0
        ReglasMision.objetivo("crecer_especie", mapOf("cantidad_animales" to 3.0, "edad_requerida" to 24.0)) shouldBe 3.0
        ReglasMision.objetivo("espacios_ocupados_habitat", mapOf("numero_espacios_requeridos" to 2.0)) shouldBe 2.0
        ReglasMision.objetivo("vender_edad", mapOf("edad_requerida" to 22.0)) shouldBe 1.0
        ReglasMision.objetivo("satisfaccion_minima", mapOf("satisfaccion_minima" to 0.6)) shouldBe 0.6
        ReglasMision.objetivo("inventada", emptyMap()) shouldBe null
        ReglasMision.objetivo("familia", emptyMap()) shouldBe null
    }

    @Test
    fun `T9_2 cumplimiento exacto en el limite`() {
        ReglasMision.cumplida(1.0, 2.0) shouldBe false
        ReglasMision.cumplida(2.0, 2.0) shouldBe true
        ReglasMision.cumplida(0.6, 0.6) shouldBe true
        ReglasMision.cumplida(0.59, 0.6) shouldBe false
    }

    @Test
    fun `T9_2 cruce de edad solo cuenta al pasar de menos a igual o mas`() {
        ReglasMision.cruzaEdad(23, 24, 24.0) shouldBe true
        ReglasMision.cruzaEdad(23, 23, 24.0) shouldBe false
        ReglasMision.cruzaEdad(24, 24, 24.0) shouldBe false
        ReglasMision.cruzaEdad(24, 25, 24.0) shouldBe false
        ReglasMision.cruzaEdad(22, 25, 24.0) shouldBe true
    }

    @Test
    fun `T9_2 una venta cumple con edad mayor o igual`() {
        ReglasMision.ventaCumple(22, 22.0) shouldBe true
        ReglasMision.ventaCumple(21, 22.0) shouldBe false
        ReglasMision.ventaCumple(30, 22.0) shouldBe true
    }

    @Test
    fun `T9_4 cada operacion de progresion`() {
        fun p(op: String, k: Double) = Progresion(op, k, 1.0, 100.0)
        ReglasMision.progresar("cantidad", 10.0, p("SUMAR", 3.0)) shouldBe ResultadoProgresion.Valor(13.0)
        ReglasMision.progresar("cantidad", 10.0, p("RESTAR", 3.0)) shouldBe ResultadoProgresion.Valor(7.0)
        ReglasMision.progresar("cantidad", 10.0, p("MULTIPLICAR", 2.0)) shouldBe ResultadoProgresion.Valor(20.0)
        ReglasMision.progresar("cantidad", 10.0, p("DIVIDIR", 2.0)) shouldBe ResultadoProgresion.Valor(5.0)
    }

    @Test
    fun `T9_4 los enteros se redondean al mas cercano con medios hacia arriba`() {
        fun p(op: String, k: Double) = Progresion(op, k, 1.0, 100.0)
        ReglasMision.progresar("cantidad", 5.0, p("DIVIDIR", 2.0)) shouldBe ResultadoProgresion.Valor(3.0) // 2.5
        ReglasMision.progresar("cantidad", 7.0, p("MULTIPLICAR", 1.2)) shouldBe ResultadoProgresion.Valor(8.0) // 8.4
        ReglasMision.progresar("cantidad", 5.0, p("MULTIPLICAR", 1.1)) shouldBe ResultadoProgresion.Valor(6.0) // 5.5
        ReglasMision.progresar("cantidad", 7.0, p("DIVIDIR", 2.0)) shouldBe ResultadoProgresion.Valor(4.0) // 3.5
    }

    @Test
    fun `T9_4 la satisfaccion se redondea al centesimo y la suma es exacta`() {
        val p = Progresion("SUMAR", 0.05, 0.05, 0.95)
        ReglasMision.progresar("satisfaccion_minima", 0.6, p) shouldBe ResultadoProgresion.Valor(0.65)
        // 0.1 + 0.2 no es 0.30000000000000004 en aritmética decimal exacta.
        ReglasMision.progresar("satisfaccion_minima", 0.1, Progresion("SUMAR", 0.2, 0.05, 0.95)) shouldBe ResultadoProgresion.Valor(0.3)
        ReglasMision.progresar("satisfaccion_minima", 0.333, Progresion("MULTIPLICAR", 1.0, 0.05, 0.95)) shouldBe ResultadoProgresion.Valor(0.33)
        ReglasMision.progresar("satisfaccion_minima", 0.005, Progresion("MULTIPLICAR", 3.0, 0.01, 0.95)) shouldBe ResultadoProgresion.Valor(0.02) // 0.015
    }

    @Test
    fun `T9_4 los limites minimo y maximo son inclusivos y fuera de rango agota el tipo`() {
        ReglasMision.progresar("cantidad", 49.0, sumar1) shouldBe ResultadoProgresion.Valor(50.0)
        ReglasMision.progresar("cantidad", 50.0, sumar1) shouldBe ResultadoProgresion.Agotada
        ReglasMision.progresar("cantidad", 2.0, Progresion("RESTAR", 1.0, 1.0, 50.0)) shouldBe ResultadoProgresion.Valor(1.0)
        ReglasMision.progresar("cantidad", 1.0, Progresion("RESTAR", 1.0, 1.0, 50.0)) shouldBe ResultadoProgresion.Agotada
        ReglasMision.progresar("satisfaccion_minima", 0.9, Progresion("SUMAR", 0.05, 0.05, 0.95)) shouldBe ResultadoProgresion.Valor(0.95)
        ReglasMision.progresar("satisfaccion_minima", 0.95, Progresion("SUMAR", 0.05, 0.05, 0.95)) shouldBe ResultadoProgresion.Agotada
    }

    @Test
    fun `T9_4 una progresion invalida no se inventa`() {
        ReglasMision.progresar("cantidad", 10.0, Progresion("DIVIDIR", 0.0, 1.0, 50.0)) shouldBe ResultadoProgresion.Invalida
        ReglasMision.progresar("cantidad", 10.0, Progresion("DIVIDIR", -1.0, 1.0, 50.0)) shouldBe ResultadoProgresion.Invalida
        ReglasMision.progresar("cantidad", 10.0, Progresion("ELEVAR", 2.0, 1.0, 50.0)) shouldBe ResultadoProgresion.Invalida
    }

    @Test
    fun `T9_4 la instancia siguiente incrementa el ultimo segmento`() {
        ReglasMision.siguienteInstancia("p1/comprar_especie/1") shouldBe "p1/comprar_especie/2"
        ReglasMision.siguienteInstancia("p1/comprar_especie/9") shouldBe "p1/comprar_especie/10"
    }

    private val lista = listOf(
        EspecieElegible("panda", 10), EspecieElegible("leopardo", 12), EspecieElegible("titi", 14), EspecieElegible("tigre", 12),
        EspecieElegible("alce", 14), EspecieElegible("jaguar", 12), EspecieElegible("ballena", 6), EspecieElegible("pangolin", 12),
        EspecieElegible("rinoceronte", 8),
    )

    @Test
    fun `T9_7 el sorteo ponderado respeta los tramos de la lista`() {
        ReglasMision.sortearEspecie(0.0, lista) shouldBe "panda"
        ReglasMision.sortearEspecie(0.0999, lista) shouldBe "panda"
        ReglasMision.sortearEspecie(0.10, lista) shouldBe "leopardo"
        ReglasMision.sortearEspecie(0.2199, lista) shouldBe "leopardo"
        ReglasMision.sortearEspecie(0.22, lista) shouldBe "titi"
        ReglasMision.sortearEspecie(0.50, lista) shouldBe "alce"
        ReglasMision.sortearEspecie(0.76, lista) shouldBe "ballena"
        ReglasMision.sortearEspecie(0.80, lista) shouldBe "pangolin"
        ReglasMision.sortearEspecie(0.9199, lista) shouldBe "pangolin"
        ReglasMision.sortearEspecie(0.92, lista) shouldBe "rinoceronte"
        ReglasMision.sortearEspecie(0.9999, lista) shouldBe "rinoceronte"
    }

    @Test
    fun `T9_7 una lista que no suma 100 o vacia no sortea`() {
        ReglasMision.sortearEspecie(0.5, emptyList()) shouldBe null
        ReglasMision.sortearEspecie(0.5, listOf(EspecieElegible("panda", 90))) shouldBe null
    }

    @Test
    fun `T9_7 la distribucion de un barrido uniforme coincide con las probabilidades`() {
        val cuenta = HashMap<String, Int>()
        for (i in 0 until 1000) {
            val e = ReglasMision.sortearEspecie(i / 1000.0, lista)!!
            cuenta[e] = (cuenta[e] ?: 0) + 1
        }
        lista.forEach { cuenta[it.idEspecie] shouldBe it.probabilidad * 10 }
    }
}
