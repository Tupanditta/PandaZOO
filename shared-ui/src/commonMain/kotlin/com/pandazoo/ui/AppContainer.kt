package com.pandazoo.ui

import com.pandazoo.application.caso.AlimentarAnimal
import com.pandazoo.application.caso.AlimentarEspacio
import com.pandazoo.application.caso.ConsultarCuidado
import com.pandazoo.application.caso.TratarAnimal
import com.pandazoo.data.repositorio.CuidadoSql
import com.pandazoo.application.caso.AbrirAviso
import com.pandazoo.application.ciclo.MotorCiclos
import com.pandazoo.application.error.Resultado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.TimeSource
import com.pandazoo.application.caso.ConsultarBeneficios
import com.pandazoo.application.caso.ConsultarHistorialAvisos
import com.pandazoo.application.caso.ConsultarSeguimientoCritico
import com.pandazoo.application.caso.EjecutarCiclo
import com.pandazoo.application.caso.PurgarRetencion
import com.pandazoo.application.caso.ReclamarBeneficios
import com.pandazoo.data.repositorio.AvisoSql
import com.pandazoo.data.repositorio.BeneficioSql
import com.pandazoo.data.repositorio.CicloSql
import com.pandazoo.data.repositorio.RetencionSql
import com.pandazoo.data.repositorio.SeguimientoSql
import com.pandazoo.application.caso.AdquirirAnimal
import com.pandazoo.application.caso.AdquirirEspacio
import com.pandazoo.application.caso.ConsultarAnimales
import com.pandazoo.application.caso.ConsultarTiendaAnimales
import com.pandazoo.application.caso.DarDeBajaEspacio
import com.pandazoo.application.caso.EditarFicha
import com.pandazoo.application.caso.ReubicarAnimal
import com.pandazoo.application.caso.VenderAnimal
import com.pandazoo.application.puerto.Azar
import com.pandazoo.data.repositorio.AnimalSql
import com.pandazoo.data.tiempo.AzarSistema
import com.pandazoo.domain.animal.CatalogoFicha
import com.pandazoo.domain.animal.catalogoFicha
import com.pandazoo.application.caso.AdquirirInsumos
import com.pandazoo.application.caso.ConfigurarEspacio
import com.pandazoo.application.caso.AlcanceRecalculo
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
import com.pandazoo.application.puerto.GeneradorId
import com.pandazoo.application.puerto.Reloj
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
import com.pandazoo.data.tiempo.GeneradorUuid
import com.pandazoo.data.tiempo.RelojSistema
import com.pandazoo.domain.config.ConfiguracionJuego
import com.pandazoo.domain.satisfaccion.ReglaSatisfaccion

/** Cableado manual de dependencias: los módulos de dominio y aplicación no conocen ningún framework. */
class AppContainer(
    val bd: BaseDatos,
    val idPartida: String,
    reloj: Reloj,
    val ids: GeneradorId,
    azar: Azar,
    cfg: ConfiguracionJuego,
    /** Modo de depuración: habilita el generador de datos de prueba. Siempre falso en una versión de usuario. */
    val depuracion: Boolean = false,
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

    /** Orquestador de satisfacción: solo lo invocan las mutaciones y los ciclos, nunca la navegación (RF-44). */
    private val reglasPrestigio = ReglasPrestigio(cfg.tablas.nivelesPrestigio, cfg.tablas.desbloqueos)
    val recalcularPrestigio = RecalcularPrestigio(PrestigioSql(bd), reglasPrestigio, AuditoriaSql(bd), bd, reloj)
    val recalcularSatisfaccion = RecalcularSatisfaccion(SatisfaccionSql(bd), ReglaSatisfaccion(cfg), bd, reloj, recalcularPrestigio)
    val consultarPrestigio = ConsultarPrestigio(PanelPrestigioSql(bd), reglasPrestigio)
    val configurarEspacio = ConfigurarEspacio(ejecutor, zoo, recalcularSatisfaccion)

    // Sprint 3: gestión de especímenes.
    private val animales = AnimalSql(bd)
    val catalogoFicha: CatalogoFicha = cfg.catalogoFicha()
    val adquirirAnimal = AdquirirAnimal(ejecutor, economia, zoo, animales, catalogoFicha, azar, ids, reloj, recalcularSatisfaccion)
    val editarFicha = EditarFicha(ejecutor, animales, catalogoFicha, recalcularSatisfaccion)
    val reubicarAnimal = ReubicarAnimal(ejecutor, zoo, animales, recalcularSatisfaccion)
    val venderAnimal = VenderAnimal(ejecutor, economia, zoo, animales, reloj, recalcularSatisfaccion)
    val darDeBajaEspacio = DarDeBajaEspacio(ejecutor, zoo)
    val consultarTiendaAnimales = ConsultarTiendaAnimales(animales, economia)
    val consultarAnimales = ConsultarAnimales(animales, zoo)

    // Sprint 4: cuidado animal.
    private val cuidado = CuidadoSql(bd)
    val alimentarAnimal = AlimentarAnimal(ejecutor, animales, cuidado, inventario, recalcularSatisfaccion)
    val alimentarEspacio = AlimentarEspacio(ejecutor, zoo, animales, cuidado, inventario, recalcularSatisfaccion)
    val tratarAnimal = TratarAnimal(ejecutor, animales, cuidado, inventario, recalcularSatisfaccion)
    val consultarCuidado = ConsultarCuidado(animales, cuidado, inventario)
    val consultarMonitor = ConsultarMonitorSatisfaccion(MonitorSql(bd))

    // Sprint 5: ciclo de simulación, beneficios y avisos.
    private val ciclos = CicloSql(bd)
    private val beneficios = BeneficioSql(bd)
    private val auditoria = AuditoriaSql(bd)
    val ejecutarCiclo = EjecutarCiclo(ejecutor, ciclos, animales, auditoria, recalcularSatisfaccion, azar, reloj)
    val reclamarBeneficios = ReclamarBeneficios(ejecutor, economia, beneficios, ciclos, reloj)
    val consultarBeneficios = ConsultarBeneficios(beneficios, economia)
    val consultarSeguimiento = ConsultarSeguimientoCritico(SeguimientoSql(bd))
    private val avisosRepo = AvisoSql(bd)
    val consultarAvisos = ConsultarHistorialAvisos(avisosRepo)
    val abrirAviso = AbrirAviso(ejecutor, avisosRepo, ciclos)
    private val purgarRetencion = PurgarRetencion(bd, RetencionSql(bd), ciclos, cfg.retencion)

    private val marcaMonotonica = TimeSource.Monotonic.markNow()

    /** Temporizador del ciclo (RF-24). Se inicia al pasar a primer plano y se detiene al pasar a segundo plano. */
    val motor = MotorCiclos(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        idPartida = idPartida,
        ciclos = ciclos,
        ejecutarCiclo = ejecutarCiclo,
        purga = purgarRetencion,
        monotonico = { marcaMonotonica.elapsedNow().inWholeMilliseconds },
        dispatcher = Dispatchers.Default,
    )

    /** Solo en modo de depuración: ejecuta el siguiente ciclo sin esperar (adelanta el temporizador). */
    fun adelantarCiclo(): Boolean {
        check(depuracion) { "Adelantar ciclos solo existe en modo de depuración" }
        return ejecutarCiclo.siguiente(idPartida) is Resultado.Exito
    }
    private val datosDePrueba = DatosDePrueba(bd, ids)

    /** Solo en modo de depuración: carga de referencia y recálculo. Devuelve `false` si ya había animales. */
    fun generarCargaDePrueba(): Boolean {
        check(depuracion) { "El generador de datos de prueba solo existe en modo de depuración" }
        return bd.ejecutar {
            val resumen = datosDePrueba.cargaReferencia(idPartida)
            if (resumen != null) recalcularSatisfaccion.alConsolidar(idPartida, AlcanceRecalculo.Parque)
            resumen != null
        }
    }

    companion object {
        /**
         * Crea el contenedor sobre una base de datos ya abierta por el contenedor de plataforma. La configuración
         * se valida siempre (la regla de satisfacción la necesita) y, si no hay partida activa, crea una nueva.
         */
        fun crear(bd: BaseDatos, depuracion: Boolean = false, textoConfiguracion: () -> String): AppContainer {
            val reloj = RelojSistema()
            val ids = GeneradorUuid()
            val cfg = CargadorConfiguracion.desdeTexto(textoConfiguracion())
            val idPartida = bd.partidaActiva() ?: InicializadorPartida(bd, reloj, ids).crearPartida(cfg)
            return AppContainer(bd, idPartida, reloj, ids, AzarSistema(), cfg, depuracion)
        }
    }
}
