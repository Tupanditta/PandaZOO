package com.pandazoo.ui

import com.pandazoo.application.caso.AdquirirEspacio
import com.pandazoo.application.caso.AdquirirInsumos
import com.pandazoo.application.caso.ConfigurarEspacio
import com.pandazoo.application.caso.ConsultarInventarioFinanzas
import com.pandazoo.application.caso.ConsultarTienda
import com.pandazoo.application.caso.ConsultarZoo
import com.pandazoo.application.operacion.EjecutorOperacion
import com.pandazoo.application.puerto.GeneradorId
import com.pandazoo.application.puerto.Reloj
import com.pandazoo.data.config.CargadorConfiguracion
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.data.inicial.InicializadorPartida
import com.pandazoo.data.repositorio.AuditoriaSql
import com.pandazoo.data.repositorio.CatalogoSql
import com.pandazoo.data.repositorio.EconomiaSql
import com.pandazoo.data.repositorio.InventarioSql
import com.pandazoo.data.repositorio.ZooSql
import com.pandazoo.data.tiempo.GeneradorUuid
import com.pandazoo.data.tiempo.RelojSistema

/** Cableado manual de dependencias: los módulos de dominio y aplicación no conocen ningún framework. */
class AppContainer(
    val bd: BaseDatos,
    val idPartida: String,
    reloj: Reloj,
    val ids: GeneradorId,
) {
    private val economia = EconomiaSql(bd)
    private val inventario = InventarioSql(bd)
    private val catalogo = CatalogoSql(bd)
    private val zoo = ZooSql(bd)
    private val ejecutor = EjecutorOperacion(bd, AuditoriaSql(bd), reloj)

    val consultarInventario = ConsultarInventarioFinanzas(economia, inventario)
    val consultarTienda = ConsultarTienda(catalogo)
    val consultarZoo = ConsultarZoo(zoo)
    val adquirirInsumos = AdquirirInsumos(ejecutor, economia, inventario, catalogo, reloj)
    val adquirirEspacio = AdquirirEspacio(ejecutor, economia, zoo, catalogo, ids, reloj)
    val configurarEspacio = ConfigurarEspacio(ejecutor, zoo)

    companion object {
        /**
         * Crea el contenedor sobre una base de datos ya abierta por el contenedor de plataforma. Si no hay
         * partida activa, crea una nueva con la configuración indicada (que se valida antes).
         */
        fun crear(bd: BaseDatos, textoConfiguracion: () -> String): AppContainer {
            val reloj = RelojSistema()
            val ids = GeneradorUuid()
            val idPartida = bd.partidaActiva()
                ?: InicializadorPartida(bd, reloj, ids).crearPartida(CargadorConfiguracion.desdeTexto(textoConfiguracion()))
            return AppContainer(bd, idPartida, reloj, ids)
        }
    }
}
