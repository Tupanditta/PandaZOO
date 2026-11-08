package com.pandazoo.application.caso

import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.operacion.EjecutorOperacion
import com.pandazoo.application.operacion.Operacion
import com.pandazoo.application.puerto.AnimalCritico
import com.pandazoo.application.puerto.AnimalRepositorio
import com.pandazoo.application.puerto.ZooRepositorio
import com.pandazoo.domain.animal.ReglasNivel
import com.pandazoo.domain.economia.Redondeo
import com.pandazoo.application.puerto.Aviso
import com.pandazoo.application.puerto.AvisoRepositorio
import com.pandazoo.application.puerto.BeneficioCiclo
import com.pandazoo.application.puerto.BeneficioRepositorio
import com.pandazoo.application.puerto.CicloRepositorio
import com.pandazoo.application.puerto.EconomiaRepositorio
import com.pandazoo.application.puerto.Reloj
import com.pandazoo.application.puerto.RetencionRepositorio
import com.pandazoo.application.puerto.SeguimientoRepositorio
import com.pandazoo.application.puerto.SeveridadAviso
import com.pandazoo.application.puerto.TipoMovimiento
import com.pandazoo.application.puerto.Transaccion
import com.pandazoo.application.puerto.VentanasRetencion
import com.pandazoo.domain.ciclo.ReglasCiclo
import com.pandazoo.domain.config.RetencionCfg
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.modelo.IdPartida

// ---------------------------------------------------------------------------------------------------- CU-08

/**
 * @property importeEsperado importe mostrado al usuario antes de confirmar; si al validar el pendiente es otro, se
 * rechaza (CU-08, 4.a). `null` no comprueba el importe mostrado.
 */
data class ComandoReclamarBeneficios(val idPartida: IdPartida, val importeEsperado: Dinero?, val idSolicitud: String)

data class BeneficiosReclamados(val importe: Dinero, val saldo: Dinero)

/** CU-08 Reclamar beneficios (RF-11, RES-12): acredita una sola vez el total pendiente y lo deja en 0. */
class ReclamarBeneficios(
    private val ejecutor: EjecutorOperacion,
    private val economia: EconomiaRepositorio,
    private val beneficios: BeneficioRepositorio,
    private val ciclos: CicloRepositorio,
    private val reloj: Reloj,
) {
    fun ejecutar(c: ComandoReclamarBeneficios): Resultado<BeneficiosReclamados> {
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "BENEFICIO_RECLAMADO",
            entidad = "BENEFICIO",
            origen = "CU-08",
            idSolicitud = c.idSolicitud,
            huella = "importe=${c.importeEsperado?.centimos}",
            codificar = { "${it.importe.centimos}|${it.saldo.centimos}" },
            decodificar = {
                val (importe, saldo) = it.split("|")
                BeneficiosReclamados(Dinero.deCentimos(importe.toLong()), Dinero.deCentimos(saldo.toLong()))
            },
        )
        return ejecutor.ejecutar(op) { reclamar(c) }
    }

    private fun reclamar(c: ComandoReclamarBeneficios): Resultado<BeneficiosReclamados> {
        val pendiente = beneficios.pendientes(c.idPartida)
        if (pendiente <= Dinero.CERO) return Resultado.Rechazado(MotivoRechazo.SinBeneficios)
        if (c.importeEsperado != null && c.importeEsperado != pendiente) {
            return Resultado.Rechazado(MotivoRechazo.BeneficiosCambiados(pendiente))
        }
        val ahora = reloj.ahoraSegundos()
        val saldo = economia.fondos(c.idPartida) + pendiente
        economia.actualizarFondos(c.idPartida, saldo)
        beneficios.reclamarTodos(c.idPartida, ahora)
        val ciclo = ciclos.estadoMotor(c.idPartida)?.numeroUltimo ?: 0
        economia.registrarMovimiento(c.idPartida, TipoMovimiento.INGRESO, pendiente, "CU-08", ciclo, ahora)
        return Resultado.Exito(BeneficiosReclamados(pendiente, saldo))
    }
}

/** Beneficios pendientes y los últimos por ciclo (CU-08 paso 2, CU-16). Solo lectura. */
class ConsultarBeneficios(
    private val beneficios: BeneficioRepositorio,
    private val economia: EconomiaRepositorio,
) {
    data class Vista(val pendiente: Dinero, val saldo: Dinero, val porCiclo: List<BeneficioCiclo>)

    fun ejecutar(idPartida: IdPartida): Vista =
        Vista(beneficios.pendientes(idPartida), economia.fondos(idPartida), beneficios.recientes(idPartida, LIMITE))

    private companion object {
        const val LIMITE = 20
    }
}

/** Beneficio que genera un animal en un ciclo con su nivel y satisfacción actuales. */
data class AnimalBeneficio(val nombre: String, val especie: String, val nivel: Int, val importe: Dinero)

data class EspacioBeneficio(val nombre: String, val importe: Dinero, val animales: List<AnimalBeneficio>)

data class HabitatBeneficio(val nombre: String, val importe: Dinero, val espacios: List<EspacioBeneficio>)

/**
 * Desglose del beneficio por ciclo por hábitat, espacio y animal. Solo lectura: aplica la misma fórmula que el ciclo
 * (`b_i = base · S_i · M_nivel`) a lo persistido. Cada total se redondea desde la suma sin redondear de sus animales.
 */
class ConsultarDesgloseBeneficios(private val zoo: ZooRepositorio, private val animales: AnimalRepositorio) {
    fun ejecutar(idPartida: IdPartida): List<HabitatBeneficio> =
        zoo.habitats(idPartida).mapNotNull { h ->
            // Cada espacio con su suma sin redondear, para que el total del hábitat no acumule redondeos.
            val espacios = zoo.espacios(idPartida, h.id).mapNotNull { e ->
                val filas = animales.animalesDeEspacio(idPartida, e.id).filter { it.vivo }
                    .map { it to ReglasNivel.beneficioAnimal(it.parametros, it.satisfaccion, it.nivel) }
                if (filas.isEmpty()) {
                    null
                } else {
                    val total = filas.sumOf { it.second }
                    total to EspacioBeneficio(
                        e.nombre,
                        Redondeo.dinero(total),
                        filas.map { (a, b) -> AnimalBeneficio(a.nombre, a.especie, a.nivel, Redondeo.dinero(b)) },
                    )
                }
            }
            if (espacios.isEmpty()) null else HabitatBeneficio(h.nombre, Redondeo.dinero(espacios.sumOf { it.first }), espacios.map { it.second })
        }
}

// ---------------------------------------------------------------------------------------------------- CU-18

data class RegistroCritico(
    val animal: AnimalCritico,
    val ciclosDesnutricion: Int,
    val ciclosEnfermedad: Int,
    val ciclosRestantes: Int,
)

/** CU-18 Seguimiento crítico (RF-20, RF-41, RF-42): contadores persistidos y `3 − max(contadores)`. Solo lectura. */
class ConsultarSeguimientoCritico(private val repositorio: SeguimientoRepositorio) {
    /** Los más urgentes (menos ciclos restantes) primero. */
    fun ejecutar(idPartida: IdPartida): List<RegistroCritico> =
        repositorio.criticos(idPartida).map {
            val desnutricion = if (it.sinAlimento) it.ciclosDesnutricion else 0
            val enfermedad = if (it.enfermo) it.ciclosEnfermedad else 0
            RegistroCritico(it, desnutricion, enfermedad, ReglasCiclo.ciclosRestantes(desnutricion, enfermedad))
        }.sortedWith(compareBy({ it.ciclosRestantes }, { it.animal.nombre }))
}

// ---------------------------------------------------------------------------------------------------- CU-21

/** CU-21 Historial de avisos: lectura sin efectos; el más reciente primero. Listar no cambia ningún estado. */
class ConsultarHistorialAvisos(private val avisos: AvisoRepositorio) {
    fun ejecutar(idPartida: IdPartida, severidad: SeveridadAviso? = null): List<Aviso> = avisos.historial(idPartida, severidad)
}

data class ComandoAbrirAviso(val idPartida: IdPartida, val idAviso: Long, val idSolicitud: String)

data class AvisoAbierto(val aviso: Aviso)

/** Abrir el detalle de un aviso `PENDIENTE` lo marca `VISTA`; no lo acepta, descarta ni resuelve (CU-21, paso 5). */
class AbrirAviso(
    private val ejecutor: EjecutorOperacion,
    private val avisos: AvisoRepositorio,
    private val ciclos: CicloRepositorio,
) {
    fun ejecutar(c: ComandoAbrirAviso): Resultado<AvisoAbierto> {
        val op = Operacion<AvisoAbierto>(
            idPartida = c.idPartida,
            tipo = "AVISO_VISTO",
            entidad = "NOTIFICACION",
            origen = "CU-21",
            idSolicitud = c.idSolicitud,
            huella = "aviso=${c.idAviso}",
            codificar = { it.aviso.id.toString() },
            decodificar = { AvisoAbierto(requireNotNull(avisos.aviso(c.idPartida, it.toLong()))) },
            idEntidad = { it.aviso.id.toString() },
        )
        return ejecutor.ejecutar(op) {
            val actual = avisos.aviso(c.idPartida, c.idAviso) ?: return@ejecutar Resultado.Rechazado(MotivoRechazo.AvisoInexistente)
            if (actual.estado == "PENDIENTE") {
                avisos.marcarVista(c.idPartida, c.idAviso, ciclos.estadoMotor(c.idPartida)?.numeroUltimo ?: 0)
            }
            Resultado.Exito(AvisoAbierto(requireNotNull(avisos.aviso(c.idPartida, c.idAviso))))
        }
    }
}

data class ComandoDescartarAviso(val idPartida: IdPartida, val idAviso: Long, val idSolicitud: String)

/**
 * Descarta un aviso de severidad baja o media (deslizándolo). Los de severidad alta se cierran solos al resolverse su
 * causa. Los de severidad baja caducan al pasar un ciclo y los de severidad media a los tres (`RetencionSql`).
 */
class DescartarAviso(
    private val ejecutor: EjecutorOperacion,
    private val avisos: AvisoRepositorio,
    private val ciclos: CicloRepositorio,
) {
    fun ejecutar(c: ComandoDescartarAviso): Resultado<Unit> {
        val op = Operacion<Unit>(
            idPartida = c.idPartida,
            tipo = "AVISO_DESCARTADO",
            entidad = "NOTIFICACION",
            origen = "CU-21",
            idSolicitud = c.idSolicitud,
            huella = "aviso=${c.idAviso}",
            codificar = { "" },
            decodificar = { },
            idEntidad = { c.idAviso.toString() },
        )
        return ejecutor.ejecutar(op) {
            val actual = avisos.aviso(c.idPartida, c.idAviso) ?: return@ejecutar Resultado.Rechazado(MotivoRechazo.AvisoInexistente)
            if (actual.severidad == SeveridadAviso.ALTA) return@ejecutar Resultado.Rechazado(MotivoRechazo.AvisoNoDescartable)
            avisos.descartar(c.idPartida, c.idAviso, ciclos.estadoMotor(c.idPartida)?.numeroUltimo ?: 0)
            Resultado.Exito(Unit)
        }
    }
}

// ---------------------------------------------------------------------------------------------------- Retención

/** Purga acotada tras confirmar cada ciclo, en su propia transacción; si falla, se reintenta el ciclo siguiente. */
class PurgarRetencion(
    private val transaccion: Transaccion,
    private val repositorio: RetencionRepositorio,
    private val ciclos: CicloRepositorio,
    private val cfg: RetencionCfg,
) {
    fun ejecutar(idPartida: IdPartida): Int {
        val numero = ciclos.estadoMotor(idPartida)?.numeroUltimo ?: return 0
        val ventanas = VentanasRetencion(
            auditoria = cfg.auditoriaEvento,
            movimientos = cfg.movimientoEconomico,
            avisosCerrados = cfg.notificacionCerrada.ventanaCiclos,
            maximoAvisosCerrados = cfg.notificacionCerrada.maximoFilas,
            beneficiosCerrados = cfg.beneficioCerrado,
            ciclos = cfg.cicloSimulacion,
            idempotencia = cfg.idempotencia,
            recompensasCerradas = cfg.recompensaMisionCerrada,
        )
        return transaccion.ejecutar { repositorio.purgar(idPartida, numero, ventanas) }
    }
}
