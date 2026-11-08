package com.pandazoo.ui

import com.pandazoo.application.caso.AlimentarAnimal
import com.pandazoo.application.caso.AlimentarEspacio
import com.pandazoo.application.caso.AlimentarHabitat
import com.pandazoo.application.caso.ConsultarCuidado
import com.pandazoo.application.caso.TratarAnimal
import com.pandazoo.application.caso.ConcederRegalosSemanales
import com.pandazoo.application.caso.RegaloSemanalConcedido
import com.pandazoo.data.repositorio.CuidadoSql
import com.pandazoo.data.repositorio.RegalosSemanalesSql
import com.pandazoo.application.caso.ActualizarMisiones
import com.pandazoo.application.caso.ConsultarMisiones
import com.pandazoo.application.caso.EtapaMisiones
import com.pandazoo.application.caso.ReclamarRecompensa
import com.pandazoo.data.repositorio.MisionSql
import com.pandazoo.domain.mision.EspecieElegible
import com.pandazoo.domain.mision.SorteoAnimales
import com.pandazoo.application.caso.AbrirAviso
import com.pandazoo.application.caso.DescartarAviso
import com.pandazoo.application.ciclo.MotorCiclos
import com.pandazoo.application.error.Resultado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pandazoo.application.caso.ProcesarDiferido
import com.pandazoo.application.caso.ResumenDiferido
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import com.pandazoo.application.caso.AceptarReproduccion
import com.pandazoo.application.caso.ConsultarReproduccion
import com.pandazoo.application.caso.EtapaReproduccion
import com.pandazoo.data.repositorio.ReproduccionSql
import com.pandazoo.application.caso.AdquirirAnimal
import com.pandazoo.application.caso.AdquirirEspacio
import com.pandazoo.application.caso.AmpliarEspacio
import com.pandazoo.application.caso.ConsultarAnimales
import com.pandazoo.application.caso.ConsultarEstadisticasAnimal
import com.pandazoo.application.caso.ConsultarGuiaConvivencia
import com.pandazoo.application.caso.ConsultarTiendaAnimales
import com.pandazoo.application.caso.DarDeBajaEspacio
import com.pandazoo.application.caso.EditarFicha
import com.pandazoo.application.caso.ReubicarAnimal
import com.pandazoo.application.caso.VenderAnimal
import com.pandazoo.application.puerto.Azar
import com.pandazoo.application.puerto.NuevoAnimal
import com.pandazoo.domain.animal.ReglasAnimal
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
import com.pandazoo.data.inicial.InicializadorPartida
import com.pandazoo.data.inicial.RegaloBienvenida
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
    /** Regalo de una partida recién creada, pendiente de mostrarse; `null` si la partida ya existía. */
    val regaloBienvenida: RegaloBienvenida? = null,
) {
    private val economia = EconomiaSql(bd)
    private val inventario = InventarioSql(bd)
    private val catalogo = CatalogoSql(bd)
    private val zoo = ZooSql(bd)
    private val ejecutor = EjecutorOperacion(bd, AuditoriaSql(bd), reloj)

    val consultarInventario = ConsultarInventarioFinanzas(economia, inventario)
    val consultarTienda = ConsultarTienda(catalogo, zoo)
    val consultarZoo = ConsultarZoo(zoo)
    val adquirirInsumos = AdquirirInsumos(ejecutor, economia, inventario, catalogo, reloj)
    val adquirirEspacio = AdquirirEspacio(ejecutor, economia, zoo, catalogo, ids, reloj)

    /** Orquestador de satisfacción: solo lo invocan las mutaciones y los ciclos, nunca la navegación (RF-44). */
    private val reglasPrestigio = ReglasPrestigio(cfg.tablas.nivelesPrestigio, cfg.tablas.desbloqueos)
    val recalcularPrestigio = RecalcularPrestigio(PrestigioSql(bd), reglasPrestigio, AuditoriaSql(bd), bd, reloj)
    private val misionesRepo = MisionSql(bd)
    private val seguimientoMisiones = ActualizarMisiones(misionesRepo)
    val recalcularSatisfaccion = RecalcularSatisfaccion(SatisfaccionSql(bd), ReglaSatisfaccion(cfg), bd, reloj, recalcularPrestigio, seguimientoMisiones)
    val consultarGuiaConvivencia = ConsultarGuiaConvivencia(cfg, ReglaSatisfaccion(cfg))
    val consultarPrestigio = ConsultarPrestigio(PanelPrestigioSql(bd), reglasPrestigio)
    val configurarEspacio = ConfigurarEspacio(ejecutor, zoo, recalcularSatisfaccion)

    // Sprint 3: gestión de especímenes.
    private val animales = AnimalSql(bd)
    val catalogoFicha: CatalogoFicha = cfg.catalogoFicha()
    val adquirirAnimal = AdquirirAnimal(ejecutor, economia, zoo, animales, catalogoFicha, ids, reloj, recalcularSatisfaccion, misionesRepo, seguimientoMisiones)
    val editarFicha = EditarFicha(ejecutor, animales, catalogoFicha, recalcularSatisfaccion)
    val reubicarAnimal = ReubicarAnimal(ejecutor, zoo, animales, recalcularSatisfaccion)
    val venderAnimal = VenderAnimal(ejecutor, economia, zoo, animales, reloj, recalcularSatisfaccion, seguimientoMisiones)
    val darDeBajaEspacio = DarDeBajaEspacio(ejecutor, zoo, economia, reloj)
    val ampliarEspacio = AmpliarEspacio(ejecutor, economia, zoo, catalogo, reloj)
    val consultarTiendaAnimales = ConsultarTiendaAnimales(animales, economia, misionesRepo)
    val consultarAnimales = ConsultarAnimales(animales, zoo)
    val consultarEstadisticasAnimal = ConsultarEstadisticasAnimal(SatisfaccionSql(bd), animales, ReglaSatisfaccion(cfg))

    // Sprint 4: cuidado animal.
    private val cuidado = CuidadoSql(bd)
    val alimentarAnimal = AlimentarAnimal(ejecutor, animales, cuidado, inventario, recalcularSatisfaccion)
    val alimentarEspacio = AlimentarEspacio(ejecutor, zoo, animales, cuidado, inventario, recalcularSatisfaccion)
    val alimentarHabitat = AlimentarHabitat(ejecutor, zoo, animales, cuidado, inventario, recalcularSatisfaccion)
    val divorciarAnimal = com.pandazoo.application.caso.DivorciarAnimal(ejecutor, animales, recalcularSatisfaccion)
    val tratarAnimal = TratarAnimal(ejecutor, animales, cuidado, inventario, recalcularSatisfaccion)
    val consultarCuidado = ConsultarCuidado(animales, cuidado, inventario, zoo)
    val consultarMonitor = ConsultarMonitorSatisfaccion(MonitorSql(bd))

    // Sprint 5: ciclo de simulación, beneficios y avisos.
    private val ciclos = CicloSql(bd)
    private val beneficios = BeneficioSql(bd)
    private val auditoria = AuditoriaSql(bd)
    private val reproduccion = ReproduccionSql(bd)
    val ejecutarCiclo = EjecutarCiclo(
        ejecutor, ciclos, animales, auditoria, recalcularSatisfaccion, azar, reloj,
        reproduccion = EtapaReproduccion(reproduccion, auditoria, azar, reloj),
        misiones = EtapaMisiones(seguimientoMisiones),
        ciclosPorNivel = cfg.reglas.nivel.ciclosPorNivel,
    )

    /** Ciclos sanos que sube un nivel (`reglas_de_calculo.nivel.ciclos_por_nivel`), para mostrar el progreso. */
    val ciclosPorNivel: Int = cfg.reglas.nivel.ciclosPorNivel
    val reclamarBeneficios = ReclamarBeneficios(ejecutor, economia, beneficios, ciclos, reloj)
    val consultarBeneficios = ConsultarBeneficios(beneficios, economia)
    val consultarDesgloseBeneficios = com.pandazoo.application.caso.ConsultarDesgloseBeneficios(zoo, animales)
    val consultarSeguimiento = ConsultarSeguimientoCritico(SeguimientoSql(bd))
    private val avisosRepo = AvisoSql(bd)
    val consultarAvisos = ConsultarHistorialAvisos(avisosRepo)
    val abrirAviso = AbrirAviso(ejecutor, avisosRepo, ciclos)
    val descartarAviso = DescartarAviso(ejecutor, avisosRepo, ciclos)

    // Sprint 9: misiones y recompensas.
    val reclamarRecompensa = ReclamarRecompensa(
        ejecutor, misionesRepo, economia, inventario, ciclos, seguimientoMisiones, azar, reloj,
        cfg.recompensasAnimales.especies.map { EspecieElegible(it.idEspecie, it.probabilidad) },
        SorteoAnimales.desde(cfg),
    )
    val consultarMisiones = ConsultarMisiones(misionesRepo, SorteoAnimales.desde(cfg))

    // Sprint 7: reproducción.
    val aceptarReproduccion = AceptarReproduccion(ejecutor, reproduccion, ciclos, animales, zoo, catalogoFicha, ids, recalcularSatisfaccion)
    val consultarReproduccion = ConsultarReproduccion(reproduccion, ciclos, animales, zoo)
    private val purgarRetencion = PurgarRetencion(bd, RetencionSql(bd), ciclos, cfg.retencion)

    // Regalos semanales (lunes y viernes): una unidad gratuita por regalo y día, con identificador propio.
    private val concederRegalosSemanales = ConcederRegalosSemanales(
        ejecutor, RegalosSemanalesSql(bd), SorteoAnimales.desde(cfg), azar, reloj, cfg.regalosSemanales,
        cfg.especies.associate { it.id to it.nombre },
    )
    private val _regalosSemanales = MutableStateFlow<List<RegaloSemanalConcedido>>(emptyList())

    /** Regalos semanales recién concedidos, pendientes de mostrarse de uno en uno. */
    val regalosSemanales: StateFlow<List<RegaloSemanalConcedido>> get() = _regalosSemanales

    /** Concede los regalos de hoy que falten (si es lunes o viernes) y los deja pendientes de mostrar. */
    fun comprobarRegalosSemanales() {
        try {
            val nuevos = concederRegalosSemanales.ejecutar(idPartida)
            if (nuevos.isNotEmpty()) _regalosSemanales.value = _regalosSemanales.value + nuevos
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Un fallo técnico revierte el regalo; se reintentará en el siguiente paso a primer plano.
        }
    }

    fun descartarRegaloSemanal() {
        _regalosSemanales.value = _regalosSemanales.value.drop(1)
    }

    private val marcaMonotonica = TimeSource.Monotonic.markNow()

    /** Temporizador del ciclo (RF-24). Se inicia al pasar a primer plano y se detiene al pasar a segundo plano. */
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val motor = MotorCiclos(
        scope = ambito,
        idPartida = idPartida,
        ciclos = ciclos,
        ejecutarCiclo = ejecutarCiclo,
        purga = purgarRetencion,
        monotonico = { marcaMonotonica.elapsedNow().inWholeMilliseconds },
        dispatcher = Dispatchers.Default,
        reloj = reloj,
    )

    // Sprint 8: simulación en diferido (CU-12).
    val procesarDiferido = ProcesarDiferido(bd, ciclos, ejecutarCiclo, auditoria, purgarRetencion, reloj, cfg.retencion.maxCiclosOffline)

    private val _ultimoDiferido = MutableStateFlow<ResumenDiferido?>(null)

    /** Resultado de la última recuperación diferida que procesó ciclos; la interfaz puede mostrarlo una vez. */
    val ultimoDiferido: StateFlow<ResumenDiferido?> get() = _ultimoDiferido

    private var reanudacion: Job? = null

    /**
     * Paso a primer plano (arranque en frío o regreso con el proceso vivo): primero resuelve el tiempo ausente con
     * CU-12 y solo después arranca el temporizador, para que ambos no ejecuten el mismo ciclo (RNF-12).
     */
    fun alPrimerPlano() {
        if (reanudacion?.isActive == true) return
        reanudacion = ambito.launch {
            withContext(Dispatchers.Default) { comprobarRegalosSemanales() }
            try {
                val r = withContext(Dispatchers.Default) { procesarDiferido.ejecutar(idPartida, cancelado = { !isActive }) }
                if (r is Resultado.Exito && r.valor.ciclosProcesados > 0) _ultimoDiferido.value = r.valor
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Recuperación revertida: queda el último estado consistente y el temporizador sigue con su remanente.
            }
            ensureActive()
            motor.iniciar()
        }
    }

    /** Paso a segundo plano: detiene la recuperación en curso y guarda remanente y marca de actividad. */
    fun alSegundoPlano() {
        reanudacion?.cancel()
        reanudacion = null
        motor.detener()
    }

    fun descartarResumenDiferido() {
        _ultimoDiferido.value = null
    }

    companion object {
        /**
         * Crea el contenedor sobre una base de datos ya abierta por el contenedor de plataforma. La configuración
         * se valida siempre (la regla de satisfacción la necesita) y, si no hay partida activa, crea una nueva.
         */
        fun crear(bd: BaseDatos, conBienvenida: Boolean = true, textoConfiguracion: () -> String): AppContainer {
            val reloj = RelojSistema()
            val ids = GeneradorUuid()
            val cfg = CargadorConfiguracion.desdeTexto(textoConfiguracion())
            val azar = AzarSistema()
            val existente = bd.partidaActiva()
            val inicializador = InicializadorPartida(bd, reloj, ids, azar)
            val nueva = if (existente == null && conBienvenida) inicializador.crearPartidaConBienvenida(cfg) else null
            val idPartida = existente ?: nueva?.idPartida ?: inicializador.crearPartida(cfg)
            return AppContainer(bd, idPartida, reloj, ids, azar, cfg, nueva?.regalo)
        }
    }
}
