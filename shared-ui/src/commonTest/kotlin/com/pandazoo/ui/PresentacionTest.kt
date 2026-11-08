package com.pandazoo.ui

import com.pandazoo.application.caso.OfertaAnimal
import com.pandazoo.application.caso.RecompensaVista
import com.pandazoo.application.puerto.Aviso
import com.pandazoo.application.puerto.EspecieTienda
import com.pandazoo.application.puerto.EstadoTienda
import com.pandazoo.application.puerto.SeveridadAviso
import com.pandazoo.application.puerto.TipoAviso as TipoAvisoDominio
import com.pandazoo.application.puerto.TipoRecompensa
import com.pandazoo.data.inicial.AnimalDeBienvenida
import com.pandazoo.data.inicial.RegaloBienvenida
import com.pandazoo.domain.config.ProbabilidadRango
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.ui.pantalla.Textos
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Textos y modelos de presentación puros: no necesitan interfaz ni base de datos. */
class PresentacionTest {
    private fun aviso(id: Long, tipo: TipoAvisoDominio, sev: SeveridadAviso, codigo: String, texto: String = "t$id", cerrado: Boolean = false) =
        Aviso(id, tipo, sev, "PENDIENTE", codigo, texto, 0, 0, if (codigo == "ALERTA_SALUD") "a$id" else null, cerrado)

    @Test
    fun `los habitats se muestran sin la palabra Habitat`() {
        Textos.nombreHabitat("Hábitat Bosque templado") shouldBe "Bosque templado"
        Textos.nombreHabitat("Bosque templado") shouldBe "Bosque templado"
    }

    @Test
    fun `los rangos tienen nombre por numero`() {
        listOf(1, 2, 3, 4, 5).map(Textos::nombreRango) shouldContainExactly listOf("Común", "Poco común", "Raro", "Épico", "Exclusivo")
    }

    @Test
    fun `una sola alerta de salud se muestra tal cual y varias se agrupan en una fila`() {
        val una = Textos.avisos(listOf(aviso(1, TipoAvisoDominio.CRITICA, SeveridadAviso.ALTA, "ALERTA_SALUD")))
        una.size shouldBe 1
        una[0].texto shouldBe "t1"

        val varias = Textos.avisos(
            listOf(
                aviso(1, TipoAvisoDominio.CRITICA, SeveridadAviso.ALTA, "ALERTA_SALUD"),
                aviso(2, TipoAvisoDominio.INFORMATIVA, SeveridadAviso.BAJA, "PRESTIGIO_ASCENSO"),
                aviso(3, TipoAvisoDominio.CRITICA, SeveridadAviso.ALTA, "ALERTA_SALUD"),
            ),
        )
        varias.size shouldBe 2
        varias[0].texto shouldBe "2 animales en estado crítico."
        varias[0].ids shouldContainExactly listOf(1L, 3L)
        varias[0].critico shouldBe true
        varias[1].texto shouldBe "t2"
    }

    @Test
    fun `solo los avisos de severidad baja y media se pueden descartar`() {
        Textos.aviso(aviso(1, TipoAvisoDominio.INFORMATIVA, SeveridadAviso.BAJA, "X")).descartable shouldBe true
        Textos.aviso(aviso(2, TipoAvisoDominio.OPERATIVA, SeveridadAviso.MEDIA, "X")).descartable shouldBe true
        Textos.aviso(aviso(3, TipoAvisoDominio.CRITICA, SeveridadAviso.ALTA, "ALERTA_SALUD")).descartable shouldBe false
    }

    @Test
    fun `la oferta de cria solo es accionable mientras sigue abierta`() {
        Textos.aviso(aviso(1, TipoAvisoDominio.OPERATIVA, SeveridadAviso.MEDIA, "REPRODUCCION_DISPONIBLE")).reproduccion shouldBe true
        Textos.aviso(aviso(2, TipoAvisoDominio.OPERATIVA, SeveridadAviso.MEDIA, "REPRODUCCION_DISPONIBLE", cerrado = true)).reproduccion shouldBe false
    }

    @Test
    fun `la recompensa de animal anuncia exclusivo solo si es seguro`() {
        fun r(vararg p: Pair<Int, Int>) = RecompensaVista(TipoRecompensa.ESPECIE, 1.0, null, p.map { ProbabilidadRango(it.first, it.second) })
        Textos.recompensa(r(5 to 100)) shouldBe "Un animal exclusivo, gratis"
        Textos.recompensa(r(2 to 50, 5 to 50)) shouldBe "Un animal aleatorio, gratis"
        Textos.recompensa(r()) shouldBe "Un animal aleatorio, gratis"
    }

    @Test
    fun `las demas recompensas muestran cantidad y nombre`() {
        Textos.recompensa(RecompensaVista(TipoRecompensa.FONDOS, 250.0, null)) shouldBe "${Textos.MONEDA} 250.00"
        Textos.recompensa(RecompensaVista(TipoRecompensa.ALIMENTO, 30.0, "Bambú")) shouldBe "30 × Bambú"
        Textos.recompensa(RecompensaVista(TipoRecompensa.INSUMO, 5.0, "Medicina")) shouldBe "5 × Medicina"
    }

    private fun especie(estado: EstadoTienda, nivel: Int? = null) =
        EspecieTienda("id", "Suricata", "Poco común", estado, Dinero.desdeDecimal(160.0), nivel)

    @Test
    fun `la tienda de animales indica el prestigio que desbloquea una especie bloqueada`() {
        val o = Textos.ofertaAnimal(OfertaAnimal(especie(EstadoTienda.BLOQUEADA, 7), asequible = false))
        o.estado shouldBe "Bloqueada"
        o.detalle shouldBe "Prestigio 7"
        o.seleccionable shouldBe false
    }

    @Test
    fun `la tienda de animales distingue asequible, sin saldo, exclusiva y gratuita`() {
        Textos.ofertaAnimal(OfertaAnimal(especie(EstadoTienda.COMERCIAL), asequible = true)).seleccionable shouldBe true
        Textos.ofertaAnimal(OfertaAnimal(especie(EstadoTienda.COMERCIAL), asequible = true)).detalle shouldBe ""
        Textos.ofertaAnimal(OfertaAnimal(especie(EstadoTienda.COMERCIAL), asequible = false)).detalle shouldBe "Saldo insuficiente"
        val exclusiva = Textos.ofertaAnimal(OfertaAnimal(especie(EstadoTienda.EXCLUSIVA), asequible = false))
        exclusiva.exclusiva shouldBe true
        exclusiva.seleccionable shouldBe false
        val gratis = Textos.ofertaAnimal(OfertaAnimal(especie(EstadoTienda.BLOQUEADA, 9), asequible = true, gratuita = true))
        gratis.coste shouldBe "Gratis"
        gratis.seleccionable shouldBe true
    }

    @Test
    fun `el regalo de bienvenida son tres ventanas de recursos mas una por animal`() {
        val regalo = RegaloBienvenida(
            fondos = 2000.0, bambu = 30, medicinas = 3,
            animales = listOf(
                AnimalDeBienvenida("erizo", "Erizo", 1),
                AnimalDeBienvenida("lince", "Lince", 4, listOf(ProbabilidadRango(2, 50), ProbabilidadRango(4, 50))),
                AnimalDeBienvenida("panda", "Panda", 5, listOf(ProbabilidadRango(5, 100))),
            ),
        )
        val v = Textos.bienvenida(regalo)
        v.size shouldBe 6
        v.take(3).map { it.titulo }.distinct() shouldContainExactly listOf("Regalo de bienvenida")
        v[1].valor shouldBe "30 kg de bambú"
        v[2].valor shouldBe "3 medicinas"
        v[3].probabilidades shouldBe emptyList()
        v[4].probabilidades shouldContainExactly listOf("Poco común" to 50, "Épico" to 50)
        // Con un único rango posible no hay ventana de suerte.
        v[5].probabilidades shouldBe emptyList()
        v[5].exclusivo shouldBe true
        Textos.bienvenida(regalo.copy(medicinas = 1))[2].valor shouldBe "1 medicina"
    }

    @Test
    fun `los homonimos se distinguen por nivel y luego por numero de orden`() {
        Textos.desambiguar(listOf("A", "B"), listOf(1, 1)) shouldContainExactly listOf("A", "B")
        Textos.desambiguar(listOf("A", "A"), listOf(1, 2)) shouldContainExactly listOf("A · nivel 1", "A · nivel 2")
        Textos.desambiguar(listOf("A", "A"), listOf(3, 3)) shouldContainExactly listOf("A · nivel 3 · n.º 1", "A · nivel 3 · n.º 2")
    }

    @Test
    fun `el porcentaje redondea los medios hacia arriba`() {
        Textos.porcentaje(0.125) shouldBe "13%"
        Textos.porcentaje(0.5) shouldBe "50%"
    }
}
