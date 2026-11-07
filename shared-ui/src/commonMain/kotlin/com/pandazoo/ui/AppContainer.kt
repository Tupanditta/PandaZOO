package com.pandazoo.ui

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
import com.pandazoo.application.caso.ConsultarAnimales
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
import com.pandazoo.data.depuracion.DatosDePrueba
import com.pandazoo.data.depuracion.ResumenCarga
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
    private val misionesRepo = MisionSql(bd)
    private val seguimientoMisiones = ActualizarMisiones(misionesRepo)
    val recalcularSatisfaccion = RecalcularSatisfaccion(SatisfaccionSql(bd), ReglaSatisfaccion(cfg), bd, reloj, recalcularPrestigio, seguimientoMisiones)
    val consultarPrestigio = ConsultarPrestigio(PanelPrestigioSql(bd), reglasPrestigio)
    val configurarEspacio = ConfigurarEspacio(ejecutor, zoo, recalcularSatisfaccion)

    // Sprint 3: gestión de especímenes.
    private val animales = AnimalSql(bd)
    val catalogoFicha: CatalogoFicha = cfg.catalogoFicha()
    val adquirirAnimal = AdquirirAnimal(ejecutor, economia, zoo, animales, catalogoFicha, azar, ids, reloj, recalcularSatisfaccion, misionesRepo, seguimientoMisiones)
    val editarFicha = EditarFicha(ejecutor, animales, catalogoFicha, recalcularSatisfaccion)
    val reubicarAnimal = ReubicarAnimal(ejecutor, zoo, animales, recalcularSatisfaccion)
    val venderAnimal = VenderAnimal(ejecutor, economia, zoo, animales, reloj, recalcularSatisfaccion, seguimientoMisiones)
    val darDeBajaEspacio = DarDeBajaEspacio(ejecutor, zoo)
    val consultarTiendaAnimales = ConsultarTiendaAnimales(animales, economia, misionesRepo)
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
    private val reproduccion = ReproduccionSql(bd)
    val ejecutarCiclo = EjecutarCiclo(
        ejecutor, ciclos, animales, auditoria, recalcularSatisfaccion, azar, reloj,
        reproduccion = EtapaReproduccion(reproduccion, auditoria, azar, reloj),
        misiones = EtapaMisiones(seguimientoMisiones),
    )
    val reclamarBeneficios = ReclamarBeneficios(ejecutor, economia, beneficios, ciclos, reloj)
    val consultarBeneficios = ConsultarBeneficios(beneficios, economia)
    val consultarSeguimiento = ConsultarSeguimientoCritico(SeguimientoSql(bd))
    private val avisosRepo = AvisoSql(bd)
    val consultarAvisos = ConsultarHistorialAvisos(avisosRepo)
    val abrirAviso = AbrirAviso(ejecutor, avisosRepo, ciclos)

    // Sprint 9: misiones y recompensas.
    val reclamarRecompensa = ReclamarRecompensa(
        ejecutor, misionesRepo, economia, inventario, ciclos, seguimientoMisiones, azar, reloj,
        cfg.recompensasAnimales.especies.map { EspecieElegible(it.idEspecie, it.probabilidad) },
    )
    val consultarMisiones = ConsultarMisiones(misionesRepo)

    // Sprint 7: reproducción.
    val aceptarReproduccion = AceptarReproduccion(ejecutor, reproduccion, ciclos, animales, zoo, catalogoFicha, azar, ids, recalcularSatisfaccion)
    val consultarReproduccion = ConsultarReproduccion(reproduccion, ciclos, animales, zoo)
    private val purgarRetencion = PurgarRetencion(bd, RetencionSql(bd), ciclos, cfg.retencion)

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

    /**
     * Solo en modo de depuración: añade un panda gratis a la partida activa, en el primer espacio con cupo.
     * Devuelve `false` si no hay espacio con cupo o la especie no existe.
     */
    fun anadirPanda(): Boolean {
        check(depuracion) { "Añadir un panda directo solo existe en modo de depuración" }
        return bd.ejecutar {
            val especie = animales.especieTienda(idPartida, "panda") ?: return@ejecutar false
            val espacio = zoo.espaciosConCupo(idPartida).firstOrNull()?.espacio ?: return@ejecutar false
            animales.crear(
                idPartida,
                NuevoAnimal(
                    id = ids.nuevo(),
                    idEspecie = "panda",
                    idEspacio = espacio.id,
                    nombre = "Panda",
                    edadMaxima = ReglasAnimal.edadMaxima(especie.edadMediaAnios, 1.0),
                    habilidades = catalogoFicha.habilidades.take(ReglasAnimal.HABILIDADES_POR_FICHA).map { it.id },
                    rasgos = catalogoFicha.rasgos.take(ReglasAnimal.RASGOS_POR_FICHA).map { it.id },
                    idPareja = null,
                    idMejorAmigo = null,
                ),
            )
            recalcularSatisfaccion.alConsolidar(idPartida, AlcanceRecalculo.Espacios(setOf(espacio.id)))
            true
        }
    }

    /** Solo en modo de depuración: borra la carga de referencia (no toca animales ni espacios del usuario). */
    fun eliminarCargaDePrueba(): ResumenCarga {
        check(depuracion) { "El generador de datos de prueba solo existe en modo de depuración" }
        return bd.ejecutar {
            val resumen = datosDePrueba.eliminarCargaReferencia(idPartida)
            recalcularSatisfaccion.alConsolidar(idPartida, AlcanceRecalculo.Parque)
            resumen
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
