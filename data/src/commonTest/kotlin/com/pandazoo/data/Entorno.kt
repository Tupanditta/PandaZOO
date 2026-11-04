package com.pandazoo.data

import com.pandazoo.application.caso.AdquirirEspacio
import com.pandazoo.application.caso.AdquirirInsumos
import com.pandazoo.application.caso.ConfigurarEspacio
import com.pandazoo.application.caso.ConsultarInventarioFinanzas
import com.pandazoo.application.caso.ConsultarTienda
import com.pandazoo.application.caso.ConsultarZoo
import com.pandazoo.application.operacion.EjecutorOperacion
import com.pandazoo.data.config.CargadorConfiguracion
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.data.inicial.InicializadorPartida
import com.pandazoo.data.repositorio.AuditoriaSql
import com.pandazoo.data.repositorio.CatalogoSql
import com.pandazoo.data.repositorio.EconomiaSql
import com.pandazoo.data.repositorio.InventarioSql
import com.pandazoo.data.repositorio.ZooSql
import com.pandazoo.domain.config.ConfiguracionJuego
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.testing.AzarFalso
import com.pandazoo.testing.ConfiguracionDePrueba
import com.pandazoo.testing.GeneradorIdSecuencial
import com.pandazoo.testing.RelojFalso

/** Entorno de test: base de datos real en memoria, reloj y azar fijos, casos de uso cableados. */
class Entorno(val cfg: ConfiguracionJuego = configuracionBase) {
    val bd: BaseDatos = BaseDatos.enMemoria()
    val reloj = RelojFalso()
    val azar = AzarFalso(0.5)
    val ids = GeneradorIdSecuencial("id")
    val idPartida: String = InicializadorPartida(bd, reloj, GeneradorIdSecuencial("partida")).crearPartida(cfg)

    val economia = EconomiaSql(bd)
    val inventario = InventarioSql(bd)
    val catalogo = CatalogoSql(bd)
    val zoo = ZooSql(bd)
    val auditoria = AuditoriaSql(bd)
    val ejecutor = EjecutorOperacion(bd, auditoria, reloj)

    val adquirirInsumos = AdquirirInsumos(ejecutor, economia, inventario, catalogo, reloj)
    val adquirirEspacio = AdquirirEspacio(ejecutor, economia, zoo, catalogo, ids, reloj)
    val configurarEspacio = ConfigurarEspacio(ejecutor, zoo)
    val consultarInventario = ConsultarInventarioFinanzas(economia, inventario)
    val consultarTienda = ConsultarTienda(catalogo)
    val consultarZoo = ConsultarZoo(zoo)

    /** Id técnico de un hábitat por su código de catálogo. */
    fun habitat(codigo: String): String = "$idPartida/$codigo"

    fun fondos(): Dinero = economia.fondos(idPartida)

    fun contar(tabla: String, where: String = "1=1"): Int =
        bd.uno("SELECT COUNT(*) FROM $tabla WHERE $where") { it.entero(0) } ?: 0

    fun auditoriaDe(tipo: String): List<Pair<String, String?>> =
        bd.leer("SELECT resultado, clave_idempotencia FROM AuditoriaEvento WHERE tipo = ? ORDER BY id_evento", tipo) {
            it.texto(0) to it.textoONulo(1)
        }

    fun cerrar() = bd.cerrar()

    companion object {
        val configuracionBase: ConfiguracionJuego by lazy { CargadorConfiguracion.desdeTexto(ConfiguracionDePrueba.texto) }
    }
}
