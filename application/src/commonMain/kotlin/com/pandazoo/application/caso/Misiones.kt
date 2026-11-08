package com.pandazoo.application.caso

import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.operacion.EjecutorOperacion
import com.pandazoo.application.operacion.Operacion
import com.pandazoo.application.puerto.Azar
import com.pandazoo.application.puerto.CicloRepositorio
import com.pandazoo.application.puerto.EconomiaRepositorio
import com.pandazoo.application.puerto.EstadoMision
import com.pandazoo.application.puerto.InstanciaMision
import com.pandazoo.application.puerto.InventarioRepositorio
import com.pandazoo.application.puerto.MisionRepositorio
import com.pandazoo.application.puerto.Reloj
import com.pandazoo.application.puerto.TipoMovimiento
import com.pandazoo.application.puerto.RecompensaConfigurada
import com.pandazoo.application.puerto.TipoRecompensa
import com.pandazoo.domain.config.ProbabilidadRango
import com.pandazoo.domain.economia.Decimales
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.mision.EspecieElegible
import com.pandazoo.domain.mision.ReglasMision
import com.pandazoo.domain.mision.ResultadoProgresion
import com.pandazoo.domain.mision.SorteoAnimales
import com.pandazoo.domain.modelo.IdPartida
import com.pandazoo.domain.modelo.Recurso
import com.pandazoo.domain.modelo.TipoRecurso

// ---------------------------------------------------------------------------------------------------- Seguimiento

/** Un animal que pasó de un nivel a otro durante un ciclo. */
data class CruceNivel(val idEspecie: String, val antes: Int, val despues: Int)

/**
 * Actualiza el progreso de las instancias vigentes (RF-39, RF-44, `EspecificacionMisiones.md` 4 y 4.1). Solo lo invocan
 * los eventos que pueden afectar a un objetivo (compra, venta, ciclo, recálculo tras una mutación): nunca una consulta.
 * Escribe en la transacción de quien lo llama. Una instancia `CUMPLIDA` no vuelve a `PENDIENTE` aunque un valor
 * dinámico baje después.
 */
class ActualizarMisiones(private val misiones: MisionRepositorio) {
    private fun pendientes(idPartida: IdPartida) = misiones.instancias(idPartida).filter { it.estado == EstadoMision.PENDIENTE }

    /** Fija el progreso y, si alcanza el objetivo, la instancia pasa de inmediato a `CUMPLIDA`. */
    private fun fijar(idPartida: IdPartida, i: InstanciaMision, progreso: Double) {
        val objetivo = ReglasMision.objetivo(i.idMision, i.numericos) ?: return
        val estado = if (ReglasMision.cumplida(progreso, objetivo)) EstadoMision.CUMPLIDA else EstadoMision.PENDIENTE
        if (progreso != i.progreso || estado != i.estado) misiones.fijarProgreso(idPartida, i.idInstancia, progreso, estado)
    }

    /** Compra confirmada de una especie (incluida la unidad gratuita de una recompensa). Acumulativa. */
    fun alComprar(idPartida: IdPartida, idEspecie: String) {
        pendientes(idPartida)
            .filter { it.idMision == "comprar_especie" && it.textos["id_especie"] == idEspecie }
            .forEach { fijar(idPartida, it, it.progreso + 1) }
    }

    /** Venta confirmada: una sola venta de un animal con nivel mayor o igual que el requerido. Acumulativa. */
    fun alVender(idPartida: IdPartida, idEspecie: String, nivel: Int) {
        for (i in pendientes(idPartida)) {
            val requerido = i.numericos["nivel_requerido"] ?: continue
            val aplica = when (i.idMision) {
                "vender_especie_nivel" -> i.textos["id_especie"] == idEspecie
                "vender_nivel" -> true
                else -> false
            }
            if (aplica && ReglasMision.ventaCumple(nivel, requerido)) fijar(idPartida, i, 1.0)
        }
    }

    /** Animales que alcanzaron el nivel requerido durante un ciclo posterior a la creación de la instancia. Acumulativa. */
    fun alSubirNivel(idPartida: IdPartida, cruces: List<CruceNivel>) {
        if (cruces.isEmpty()) return
        for (i in pendientes(idPartida).filter { it.idMision == "crecer_especie" }) {
            val requerido = i.numericos["nivel_requerido"] ?: continue
            val especie = i.textos["id_especie"] ?: continue
            val cuenta = cruces.count { it.idEspecie == especie && ReglasMision.alcanzaNivel(it.antes, it.despues, requerido) }
            if (cuenta > 0) fijar(idPartida, i, i.progreso + cuenta)
        }
    }

    /** Recalcula las misiones dinámicas sobre lo persistido; se invoca tras cada recálculo de satisfacción. */
    fun evaluarDinamicas(idPartida: IdPartida) {
        for (i in pendientes(idPartida).filter { it.dinamica }) evaluar(idPartida, i)
    }

    /** Evalúa una instancia dinámica concreta (también la recién creada al reclamar). */
    fun evaluar(idPartida: IdPartida, i: InstanciaMision) {
        if (i.estado != EstadoMision.PENDIENTE || !i.dinamica) return
        fijar(idPartida, i, misiones.valorDinamico(idPartida, i))
    }
}

/** Etapa de misiones del ciclo (CU-11, 3.8): cuenta a los animales que subieron de nivel en este ciclo. */
class EtapaMisiones(private val seguimiento: ActualizarMisiones) : EtapaCiclo {
    override fun ejecutar(idPartida: IdPartida, numeroCiclo: Int, vivos: List<AnimalEnCiclo>) {
        val cruces = vivos.mapNotNull {
            if (it.nivel > it.nivelPrevio) CruceNivel(it.idEspecie, it.nivelPrevio, it.nivel) else null
        }
        seguimiento.alSubirNivel(idPartida, cruces)
    }
}

// ---------------------------------------------------------------------------------------------------- CU-15

data class ComandoReclamarRecompensa(val idPartida: IdPartida, val idInstancia: String)

/**
 * @property nombreItem alimento o insumo acreditado.
 * @property nombreEspecie especie sorteada, cuya unidad gratuita queda disponible en la tienda.
 * @property agotada el tipo agotó su progresión y no vuelve a aparecer.
 * @property idRango rango de la especie sorteada, si se conoce.
 * @property probabilidades tabla de rango con la que se sorteó el animal (vacía si no hubo sorteo por rango).
 */
data class RecompensaReclamada(
    val tipo: TipoRecompensa,
    val cantidad: Double,
    val nombreItem: String?,
    val nombreEspecie: String?,
    val agotada: Boolean,
    val idRango: Int? = null,
    val probabilidades: List<ProbabilidadRango> = emptyList(),
    val idEspecie: String? = null,
)

/**
 * CU-15 Reclamar recompensa (RF-40, RF-38, RES-09, RES-12, RNF-12). Una sola transacción: valida la instancia, el
 * contenido y la progresión; acredita fondos, inventario o la unidad gratuita; registra la recompensa; retira la
 * instancia cumplida y crea la siguiente con los parámetros progresados. La clave de idempotencia deriva de la instancia.
 */
class ReclamarRecompensa(
    private val ejecutor: EjecutorOperacion,
    private val misiones: MisionRepositorio,
    private val economia: EconomiaRepositorio,
    private val inventario: InventarioRepositorio,
    private val ciclos: CicloRepositorio,
    private val seguimiento: ActualizarMisiones,
    private val azar: Azar,
    private val reloj: Reloj,
    private val elegibles: List<EspecieElegible>,
    /** Sorteo por rango de la misión de prestigio; sin él, el animal sale de [elegibles]. */
    private val sorteo: SorteoAnimales? = null,
) {
    fun ejecutar(c: ComandoReclamarRecompensa): Resultado<RecompensaReclamada> {
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "RECOMPENSA_RECLAMADA",
            entidad = "RECOMPENSA",
            origen = "CU-15",
            idSolicitud = c.idInstancia,
            huella = "instancia=${c.idInstancia}",
            codificar = {
                "${it.tipo}|${it.cantidad}|${it.nombreItem.orEmpty()}|${it.nombreEspecie.orEmpty()}|${if (it.agotada) 1 else 0}|" +
                    "${it.idRango ?: ""}|${it.probabilidades.joinToString(",") { p -> "${p.idRango}:${p.porcentaje}" }}|${it.idEspecie.orEmpty()}"
            },
            decodificar = {
                val p = it.split("|")
                RecompensaReclamada(
                    TipoRecompensa.valueOf(p[0]), p[1].toDouble(), p[2].ifEmpty { null }, p[3].ifEmpty { null }, p[4] == "1",
                    p.getOrNull(5)?.toIntOrNull(),
                    p.getOrNull(6).orEmpty().split(",").filter { s -> s.isNotEmpty() }.map { s ->
                        val (rango, porcentaje) = s.split(":")
                        ProbabilidadRango(rango.toInt(), porcentaje.toInt())
                    },
                    p.getOrNull(7)?.ifEmpty { null },
                )
            },
            idEntidad = { c.idInstancia },
            numeroCiclo = ciclos.estadoMotor(c.idPartida)?.numeroUltimo ?: 0,
        )
        return ejecutor.ejecutar(op) { reclamar(c) }
    }

    private fun reclamar(c: ComandoReclamarRecompensa): Resultado<RecompensaReclamada> {
        val idPartida = c.idPartida
        val inst = misiones.instancia(idPartida, c.idInstancia) ?: return rechazo(MotivoRechazo.MisionInexistente)
        if (inst.estado != EstadoMision.CUMPLIDA) return rechazo(MotivoRechazo.MisionNoCumplida)

        // Se valida todo antes de escribir: un rechazo no cambia nada (CU-15, 4.b y 4.c).
        val r = recompensaVigente(inst)
        val enteraValida = r.cantidad == r.cantidad.toLong().toDouble()
        val contenidoValido = r.cantidad > 0 && when (r.tipo) {
            TipoRecompensa.FONDOS -> true
            TipoRecompensa.ALIMENTO, TipoRecompensa.INSUMO -> r.idItem != null && enteraValida
            TipoRecompensa.ESPECIE -> r.cantidad == 1.0
        }
        if (!contenidoValido) return rechazo(MotivoRechazo.RecompensaNoValida)

        val progresiones = misiones.progresiones(inst.idMision)
        val nuevos = LinkedHashMap<String, Double>()
        var agotada = false
        for ((nombre, valor) in inst.numericos) {
            val p = progresiones[nombre] ?: return rechazo(MotivoRechazo.ProgresionInvalida)
            when (val res = ReglasMision.progresar(nombre, valor, p)) {
                is ResultadoProgresion.Valor -> nuevos[nombre] = res.valor
                ResultadoProgresion.Agotada -> agotada = true
                ResultadoProgresion.Invalida -> return rechazo(MotivoRechazo.ProgresionInvalida)
            }
        }
        var tabla: List<ProbabilidadRango> = emptyList()
        val especie = if (r.tipo != TipoRecompensa.ESPECIE) {
            null
        } else if (sorteo != null && inst.idMision == ReglasMision.MISION_PRESTIGIO) {
            val requerido = inst.numericos[ReglasMision.PARAMETRO_PRESTIGIO] ?: return rechazo(MotivoRechazo.RecompensaNoValida)
            tabla = sorteo.tablaPrestigio(requerido.toInt())
            sorteo.sortear(tabla, azar.siguienteDouble(), azar.siguienteDouble()) ?: return rechazo(MotivoRechazo.RecompensaNoValida)
        } else {
            ReglasMision.sortearEspecie(azar.siguienteDouble(), elegibles) ?: return rechazo(MotivoRechazo.RecompensaNoValida)
        }

        val ahora = reloj.ahoraSegundos()
        val ciclo = ciclos.estadoMotor(idPartida)?.numeroUltimo ?: 0
        when (r.tipo) {
            TipoRecompensa.FONDOS -> {
                val importe = Dinero.desdeDecimal(r.cantidad)
                economia.actualizarFondos(idPartida, economia.fondos(idPartida) + importe)
                economia.registrarMovimiento(idPartida, TipoMovimiento.RECOMPENSA, importe, "CU-15", ciclo, ahora)
            }
            TipoRecompensa.ALIMENTO ->
                inventario.incrementar(idPartida, Recurso(TipoRecurso.ALIMENTO, requireNotNull(r.idItem)), r.cantidad.toInt())
            TipoRecompensa.INSUMO ->
                inventario.incrementar(idPartida, Recurso(TipoRecurso.INSUMO, requireNotNull(r.idItem)), r.cantidad.toInt())
            // Solo concede una unidad gratuita (registrarRecompensa): la especie no se desbloquea en la tienda.
            TipoRecompensa.ESPECIE -> requireNotNull(especie)
        }
        misiones.registrarRecompensa(idPartida, inst.idMision, inst.idInstancia, r, especie, ciclo, ahora)

        if (agotada) {
            misiones.marcarAgotada(idPartida, inst.idInstancia)
        } else {
            val nuevaId = ReglasMision.siguienteInstancia(inst.idInstancia)
            misiones.sustituirInstancia(idPartida, inst, nuevaId, nuevos)
            // Una instancia dinámica puede quedar cumplida al instante si el parque ya cumple los nuevos parámetros.
            misiones.instancia(idPartida, nuevaId)?.let { seguimiento.evaluar(idPartida, it) }
        }
        return Resultado.Exito(
            RecompensaReclamada(
                tipo = r.tipo,
                cantidad = r.cantidad,
                nombreItem = r.idItem?.let { misiones.nombreRecurso(it) ?: it },
                nombreEspecie = especie?.let { misiones.nombreEspecie(it) ?: it },
                agotada = agotada,
                idRango = especie?.let { sorteo?.rangoDe(it) },
                probabilidades = tabla,
                idEspecie = especie,
            ),
        )
    }

    private fun rechazo(m: MotivoRechazo) = Resultado.Rechazado(m)
}

/** Recompensa que se entrega por la instancia: la configurada (la misión de prestigio siempre concede un animal). */
internal fun recompensaVigente(i: InstanciaMision): RecompensaConfigurada = i.recompensa

// ---------------------------------------------------------------------------------------------------- CU-20

/** @property probabilidades tabla de rango del animal que se sortea al reclamar (vacía si no es un sorteo por rango). */
data class RecompensaVista(
    val tipo: TipoRecompensa,
    val cantidad: Double,
    val nombreItem: String?,
    val probabilidades: List<ProbabilidadRango> = emptyList(),
)

/**
 * @property progresoTexto progreso y objetivo ya formateados («1 / 2», «62 % / 65 %»).
 * @property reclamable la instancia está cumplida: se ofrece la acción explícita de reclamar.
 */
data class MisionVista(
    val idInstancia: String,
    val descripcion: String,
    val progreso: Double,
    val objetivo: Double,
    val progresoTexto: String,
    val cumplida: Boolean,
    val reclamable: Boolean,
    val recompensa: RecompensaVista,
    /** Orden en que se cumplió, para ordenar las pendientes de reclamar; `null` si no está cumplida. */
    val ordenCumplida: Int? = null,
) {
    /** Avance hacia el objetivo, entre 0 y 1. */
    val fraccion: Double get() = if (objetivo <= 0.0) 1.0 else (progreso / objetivo).coerceIn(0.0, 1.0)
}

/**
 * CU-20 Consultar misiones y progreso (RF-39). Lee lo persistido: no recalcula, no abre transacción y no audita (RF-47).
 * Muestra una instancia vigente por tipo; las agotadas no se muestran. Los nombres son siempre descriptivos (RES-06).
 */
class ConsultarMisiones(private val misiones: MisionRepositorio, private val sorteo: SorteoAnimales? = null) {
    /**
     * Primero las cumplidas pendientes de reclamar, en el orden en que se cumplieron; después el resto, de más a menos
     * avanzada y, a igual avance, la que menos le falta para completarse.
     */
    fun ejecutar(idPartida: IdPartida): List<MisionVista> =
        misiones.instancias(idPartida).filter { it.estado != EstadoMision.AGOTADA }.mapNotNull { vista(idPartida, it) }
            .sortedWith(
                compareByDescending<MisionVista> { it.reclamable }
                    .thenBy { if (it.reclamable) it.ordenCumplida ?: Int.MAX_VALUE else 0 }
                    .thenByDescending { it.fraccion }
                    .thenBy { it.objetivo - it.progreso }
                    .thenBy { it.idInstancia },
            )

    private fun vista(idPartida: IdPartida, i: InstanciaMision): MisionVista? {
        val objetivo = ReglasMision.objetivo(i.idMision, i.numericos) ?: return null
        val especie = i.textos["id_especie"]?.let { misiones.nombreEspecie(it) ?: it }
        val habitat = i.textos["id_habitat"]?.let { misiones.nombreHabitat(idPartida, it) ?: it }
        val r = recompensaVigente(i)
        return MisionVista(
            idInstancia = i.idInstancia,
            descripcion = descripcion(i, especie, habitat),
            progreso = i.progreso,
            objetivo = objetivo,
            progresoTexto = if (i.numericos.containsKey(ReglasMision.PARAMETRO_SATISFACCION)) {
                "${porcentaje(i.progreso)} % / ${porcentaje(objetivo)} %"
            } else {
                "${entero(minOf(i.progreso, objetivo))} / ${entero(objetivo)}"
            },
            cumplida = i.estado == EstadoMision.CUMPLIDA,
            reclamable = i.estado == EstadoMision.CUMPLIDA,
            recompensa = RecompensaVista(
                r.tipo, r.cantidad, r.idItem?.let { misiones.nombreRecurso(it) ?: it },
                if (r.tipo == TipoRecompensa.ESPECIE && i.idMision == ReglasMision.MISION_PRESTIGIO) {
                    sorteo?.tablaPrestigio((i.numericos[ReglasMision.PARAMETRO_PRESTIGIO] ?: 0.0).toInt()).orEmpty()
                } else {
                    emptyList()
                },
            ),
            ordenCumplida = i.ordenCumplida,
        )
    }

    private fun descripcion(i: InstanciaMision, especie: String?, habitat: String?): String {
        fun n(nombre: String) = entero(i.numericos[nombre] ?: 0.0)
        val sp = especie ?: "la especie indicada"
        val hb = habitat ?: "el hábitat indicado"
        return when (i.idMision) {
            "comprar_especie" -> "Compra ${n("cantidad_requerida")} ejemplares de $sp."
            "crecer_especie" -> "Haz que ${n("cantidad_animales")} ejemplares de $sp alcancen el nivel ${n("nivel_requerido")}."
            "total_animales" -> "Ten ${n("numero_animales")} animales en el zoo."
            "animales_habitat" -> "Ten ${n("numero_animales")} animales en el hábitat $hb."
            "espacios_ocupados_habitat" ->
                "Ten ${n("numero_espacios_requeridos")} espacios del hábitat $hb con al menos ${n("numero_minimo_animales_por_espacio")} animales."
            "subir_prestigio" -> "Alcanza el prestigio ${n("nivel_prestigio_requerido")}."
            "vender_especie_nivel" -> "Vende un ejemplar de $sp de nivel ${n("nivel_requerido")} o superior."
            "vender_nivel" -> "Vende un animal de nivel ${n("nivel_requerido")} o superior."
            "familia" -> "Ten una familia: un animal con padre, madre y al menos ${n("numero_minimo_hermanos")} hermanos vivos."
            "familia_especie" -> "Ten una familia de $sp: padre, madre y al menos ${n("numero_minimo_hermanos")} hermanos vivos."
            "satisfaccion_minima" ->
                "Consigue un animal con una satisfacción de al menos ${porcentaje(i.numericos["satisfaccion_minima"] ?: 0.0)} %."
            "satisfaccion_minima_especie" ->
                "Consigue un ejemplar de $sp con una satisfacción de al menos ${porcentaje(i.numericos["satisfaccion_minima"] ?: 0.0)} %."
            else -> i.tipoObjetivo
        }
    }

    private fun entero(v: Double) = Decimales.redondearEscalado(v, 0).toString()

    /** Porcentaje entero hacia abajo: no se muestra un 65 % que aún no se ha alcanzado. */
    private fun porcentaje(v: Double) = (Decimales.redondearEscalado(v * 100.0, 6) / 1_000_000L).toString()
}
