package com.pandazoo.data

import com.pandazoo.application.caso.AlimentarAnimal
import com.pandazoo.application.caso.AlimentarEspacio
import com.pandazoo.application.caso.ConsultarCuidado
import com.pandazoo.application.caso.TratarAnimal
import com.pandazoo.data.repositorio.CuidadoSql
import com.pandazoo.application.caso.ActualizarMisiones
import com.pandazoo.application.caso.ConsultarMisiones
import com.pandazoo.application.caso.EtapaMisiones
import com.pandazoo.application.caso.ReclamarRecompensa
import com.pandazoo.data.repositorio.MisionSql
import com.pandazoo.domain.mision.EspecieElegible
import com.pandazoo.application.caso.AbrirAviso
import com.pandazoo.application.caso.ConsultarBeneficios
import com.pandazoo.application.caso.ConsultarHistorialAvisos
import com.pandazoo.application.caso.ConsultarSeguimientoCritico
import com.pandazoo.application.caso.EjecutarCiclo
import com.pandazoo.application.caso.ProcesarDiferido
import com.pandazoo.application.caso.PurgarRetencion
import com.pandazoo.application.caso.ReclamarBeneficios
import com.pandazoo.data.repositorio.AvisoSql
import com.pandazoo.data.repositorio.BeneficioSql
import com.pandazoo.data.repositorio.CicloSql
import com.pandazoo.data.repositorio.RetencionSql
import com.pandazoo.data.repositorio.SeguimientoSql
import com.pandazoo.application.caso.AceptarReproduccion
import com.pandazoo.application.caso.ConsultarReproduccion
import com.pandazoo.application.caso.EtapaReproduccion
import com.pandazoo.data.repositorio.ReproduccionSql
import com.pandazoo.application.caso.AdquirirAnimal
import com.pandazoo.application.caso.AdquirirEspacio
import com.pandazoo.application.caso.ConsultarAnimales
import com.pandazoo.application.caso.ConsultarTiendaAnimales
import com.pandazoo.application.caso.DarDeBajaEspacio
import com.pandazoo.application.caso.EditarFicha
import com.pandazoo.application.caso.ReubicarAnimal
import com.pandazoo.application.caso.VenderAnimal
import com.pandazoo.data.repositorio.AnimalSql
import com.pandazoo.domain.animal.catalogoFicha
import com.pandazoo.application.caso.AdquirirInsumos
import com.pandazoo.application.caso.ConfigurarEspacio
import com.pandazoo.application.caso.ConsultarInventarioFinanzas
import com.pandazoo.application.caso.ConsultarMonitorSatisfaccion
import com.pandazoo.application.caso.RecalcularSatisfaccion
import com.pandazoo.application.caso.RecalcularPrestigio
import com.pandazoo.application.caso.ConsultarPrestigio
import com.pandazoo.data.repositorio.PrestigioSql
import com.pandazoo.data.repositorio.PanelPrestigioSql
import com.pandazoo.domain.prestigio.ReglasPrestigio
import com.pandazoo.application.caso.ConsultarTienda
import com.pandazoo.application.caso.ConsultarZoo
import com.pandazoo.application.operacion.EjecutorOperacion
import com.pandazoo.data.config.CargadorConfiguracion
import com.pandazoo.data.db.BaseDatos
import com.pandazoo.data.depuracion.DatosDePrueba
import com.pandazoo.data.inicial.InicializadorPartida
import com.pandazoo.data.repositorio.AuditoriaSql
import com.pandazoo.data.repositorio.CatalogoSql
import com.pandazoo.data.repositorio.EconomiaSql
import com.pandazoo.data.repositorio.InventarioSql
import com.pandazoo.data.repositorio.MonitorSql
import com.pandazoo.data.repositorio.SatisfaccionSql
import com.pandazoo.data.repositorio.ZooSql
import com.pandazoo.domain.config.ConfiguracionJuego
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.satisfaccion.ReglaSatisfaccion
import com.pandazoo.testing.AzarFalso
import com.pandazoo.testing.ConfiguracionDePrueba
import com.pandazoo.testing.GeneradorIdSecuencial
import com.pandazoo.testing.RelojFalso

/** Entorno de test: base de datos real en memoria, reloj y azar fijos, casos de uso cableados. */
class Entorno(val cfg: ConfiguracionJuego = configuracionBase, valorAzar: Double = 0.5) {
    val bd: BaseDatos = BaseDatos.enMemoria()
    val reloj = RelojFalso()
    val azar = AzarFalso(valorAzar)
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
    val consultarInventario = ConsultarInventarioFinanzas(economia, inventario)
    val consultarTienda = ConsultarTienda(catalogo)
    val consultarZoo = ConsultarZoo(zoo)

    val regla = ReglaSatisfaccion(cfg)
    val reglasPrestigio = ReglasPrestigio(cfg.tablas.nivelesPrestigio, cfg.tablas.desbloqueos)
    val recalcularPrestigio = RecalcularPrestigio(PrestigioSql(bd), reglasPrestigio, auditoria, bd, reloj)
    val misionesRepo = MisionSql(bd)
    val seguimientoMisiones = ActualizarMisiones(misionesRepo)
    val recalcular = RecalcularSatisfaccion(SatisfaccionSql(bd), regla, bd, reloj, recalcularPrestigio, seguimientoMisiones)
    val consultarPrestigio = ConsultarPrestigio(PanelPrestigioSql(bd), reglasPrestigio)
    val configurarEspacio = ConfigurarEspacio(ejecutor, zoo, recalcular)

    // Sprint 3: gestión de especímenes.
    val animales = AnimalSql(bd)
    val catalogoFicha = cfg.catalogoFicha()
    val adquirirAnimal = AdquirirAnimal(ejecutor, economia, zoo, animales, catalogoFicha, azar, ids, reloj, recalcular, misionesRepo, seguimientoMisiones)
    val editarFicha = EditarFicha(ejecutor, animales, catalogoFicha, recalcular)
    val reubicarAnimal = ReubicarAnimal(ejecutor, zoo, animales, recalcular)
    val venderAnimal = VenderAnimal(ejecutor, economia, zoo, animales, reloj, recalcular, seguimientoMisiones)
    val darDeBajaEspacio = DarDeBajaEspacio(ejecutor, zoo)
    val consultarTiendaAnimales = ConsultarTiendaAnimales(animales, economia, misionesRepo)
    val consultarAnimales = ConsultarAnimales(animales, zoo)

    // Sprint 4: cuidado animal.
    val cuidado = CuidadoSql(bd)
    val alimentarAnimal = AlimentarAnimal(ejecutor, animales, cuidado, inventario, recalcular)
    val alimentarEspacio = AlimentarEspacio(ejecutor, zoo, animales, cuidado, inventario, recalcular)
    val tratarAnimal = TratarAnimal(ejecutor, animales, cuidado, inventario, recalcular)
    val consultarCuidado = ConsultarCuidado(animales, cuidado, inventario)
    val monitor = MonitorSql(bd)
    val consultarMonitor = ConsultarMonitorSatisfaccion(monitor)
    val datos = DatosDePrueba(bd, GeneradorIdSecuencial("dp"))

    // Sprint 5: ciclo de simulación, beneficios y avisos.
    val ciclos = CicloSql(bd)
    val beneficios = BeneficioSql(bd)
    val reproduccion = ReproduccionSql(bd)
    val ejecutarCiclo = EjecutarCiclo(
        ejecutor, ciclos, animales, auditoria, recalcular, azar, reloj,
        reproduccion = EtapaReproduccion(reproduccion, auditoria, azar, reloj),
        misiones = EtapaMisiones(seguimientoMisiones),
    )
    val reclamarBeneficios = ReclamarBeneficios(ejecutor, economia, beneficios, ciclos, reloj)
    val consultarBeneficios = ConsultarBeneficios(beneficios, economia)
    val consultarSeguimiento = ConsultarSeguimientoCritico(SeguimientoSql(bd))
    val avisos = AvisoSql(bd)
    val consultarAvisos = ConsultarHistorialAvisos(avisos)
    val abrirAviso = AbrirAviso(ejecutor, avisos, ciclos)

    // Sprint 7: reproducción.
    val aceptarReproduccion = AceptarReproduccion(ejecutor, reproduccion, ciclos, animales, zoo, catalogoFicha, azar, ids, recalcular)
    val consultarReproduccion = ConsultarReproduccion(reproduccion, ciclos, animales, zoo)
    val purgarRetencion = PurgarRetencion(bd, RetencionSql(bd), ciclos, cfg.retencion)

    // Sprint 9: misiones y recompensas.
    val reclamarRecompensa = ReclamarRecompensa(
        ejecutor, misionesRepo, economia, inventario, ciclos, seguimientoMisiones, azar, reloj,
        cfg.recompensasAnimales.especies.map { EspecieElegible(it.idEspecie, it.probabilidad) },
    )
    val consultarMisiones = ConsultarMisiones(misionesRepo)

    // Sprint 8: simulación en diferido.
    fun procesarDiferido(maxCiclos: Int = cfg.retencion.maxCiclosOffline, ciclo: EjecutarCiclo = ejecutarCiclo) =
        ProcesarDiferido(bd, ciclos, ciclo, auditoria, purgarRetencion, reloj, maxCiclos)

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
