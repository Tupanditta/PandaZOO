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
import com.pandazoo.application.puerto.TipoRecompensa
import com.pandazoo.domain.economia.Decimales
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.mision.EspecieElegible
import com.pandazoo.domain.mision.ReglasMision
import com.pandazoo.domain.mision.ResultadoProgresion
import com.pandazoo.domain.modelo.IdPartida
import com.pandazoo.domain.modelo.Recurso
import com.pandazoo.domain.modelo.TipoRecurso

// ---------------------------------------------------------------------------------------------------- Seguimiento

/** Un animal que cambió de años completos durante un ciclo. */
data class CruceEdad(val idEspecie: String, val antesAnios: Int, val despuesAnios: Int)

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

    /** Venta confirmada: una sola venta con edad en años completos mayor o igual que la requerida. Acumulativa. */
    fun alVender(idPartida: IdPartida, idEspecie: String, edadAnios: Int) {
        for (i in pendientes(idPartida)) {
            val edad = i.numericos["edad_requerida"] ?: continue
            val aplica = when (i.idMision) {
                "vender_especie_edad" -> i.textos["id_especie"] == idEspecie
                "vender_edad" -> true
                else -> false
            }
            if (aplica && ReglasMision.ventaCumple(edadAnios, edad)) fijar(idPartida, i, 1.0)
        }
    }

    /** Animales que cruzaron la edad requerida durante un ciclo posterior a la creación de la instancia. Acumulativa. */
    fun alCrecer(idPartida: IdPartida, cruces: List<CruceEdad>) {
        if (cruces.isEmpty()) return
        for (i in pendientes(idPartida).filter { it.idMision == "crecer_especie" }) {
            val edad = i.numericos["edad_requerida"] ?: continue
            val especie = i.textos["id_especie"] ?: continue
            val cuenta = cruces.count { it.idEspecie == especie && ReglasMision.cruzaEdad(it.antesAnios, it.despuesAnios, edad) }
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

/** Etapa de misiones del ciclo (CU-11, 3.8): cuenta a los animales que cruzaron la edad requerida en este ciclo. */
class EtapaMisiones(private val seguimiento: ActualizarMisiones) : EtapaCiclo {
    override fun ejecutar(idPartida: IdPartida, numeroCiclo: Int, vivos: List<AnimalEnCiclo>) {
        val cruces = vivos.mapNotNull {
            val antes = it.edadPreviaDias / DIAS_ANIO
            val despues = it.edadDias / DIAS_ANIO
            if (despues > antes) CruceEdad(it.idEspecie, antes, despues) else null
        }
        seguimiento.alCrecer(idPartida, cruces)
    }

    private companion object {
        const val DIAS_ANIO = 360
    }
}

// ---------------------------------------------------------------------------------------------------- CU-15

data class ComandoReclamarRecompensa(val idPartida: IdPartida, val idInstancia: String)

/**
 * @property nombreItem alimento o insumo acreditado.
 * @property nombreEspecie especie sorteada, cuya unidad gratuita queda disponible en la tienda.
 * @property agotada el tipo agotó su progresión y no vuelve a aparecer.
 */
data class RecompensaReclamada(
    val tipo: TipoRecompensa,
    val cantidad: Double,
    val nombreItem: String?,
    val nombreEspecie: String?,
    val agotada: Boolean,
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
) {
    fun ejecutar(c: ComandoReclamarRecompensa): Resultado<RecompensaReclamada> {
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "RECOMPENSA_RECLAMADA",
            entidad = "RECOMPENSA",
            origen = "CU-15",
            idSolicitud = c.idInstancia,
            huella = "instancia=${c.idInstancia}",
            codificar = { "${it.tipo}|${it.cantidad}|${it.nombreItem.orEmpty()}|${it.nombreEspecie.orEmpty()}|${if (it.agotada) 1 else 0}" },
            decodificar = {
                val p = it.split("|")
                RecompensaReclamada(
                    TipoRecompensa.valueOf(p[0]), p[1].toDouble(), p[2].ifEmpty { null }, p[3].ifEmpty { null }, p[4] == "1",
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
        val r = inst.recompensa
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
        val especie = if (r.tipo == TipoRecompensa.ESPECIE) {
            ReglasMision.sortearEspecie(azar.siguienteDouble(), elegibles) ?: return rechazo(MotivoRechazo.RecompensaNoValida)
        } else {
            null
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
            TipoRecompensa.ESPECIE -> misiones.desbloquearEspecie(idPartida, requireNotNull(especie), ciclo, ahora)
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
            ),
        )
    }

    private fun rechazo(m: MotivoRechazo) = Resultado.Rechazado(m)
}

// ---------------------------------------------------------------------------------------------------- CU-20

data class RecompensaVista(val tipo: TipoRecompensa, val cantidad: Double, val nombreItem: String?)

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
)

/**
 * CU-20 Consultar misiones y progreso (RF-39). Lee lo persistido: no recalcula, no abre transacción y no audita (RF-47).
 * Muestra una instancia vigente por tipo; las agotadas no se muestran. Los nombres son siempre descriptivos (RES-06).
 */
class ConsultarMisiones(private val misiones: MisionRepositorio) {
    fun ejecutar(idPartida: IdPartida): List<MisionVista> =
        misiones.instancias(idPartida).filter { it.estado != EstadoMision.AGOTADA }.mapNotNull { vista(idPartida, it) }

    private fun vista(idPartida: IdPartida, i: InstanciaMision): MisionVista? {
        val objetivo = ReglasMision.objetivo(i.idMision, i.numericos) ?: return null
        val especie = i.textos["id_especie"]?.let { misiones.nombreEspecie(it) ?: it }
        val habitat = i.textos["id_habitat"]?.let { misiones.nombreHabitat(idPartida, it) ?: it }
        val r = i.recompensa
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
            recompensa = RecompensaVista(r.tipo, r.cantidad, r.idItem?.let { misiones.nombreRecurso(it) ?: it }),
        )
    }

    private fun descripcion(i: InstanciaMision, especie: String?, habitat: String?): String {
        fun n(nombre: String) = entero(i.numericos[nombre] ?: 0.0)
        val sp = especie ?: "la especie indicada"
        val hb = habitat ?: "el hábitat indicado"
        return when (i.idMision) {
            "comprar_especie" -> "Compra ${n("cantidad_requerida")} ejemplares de $sp."
            "crecer_especie" -> "Haz que ${n("cantidad_animales")} ejemplares de $sp lleguen a ${n("edad_requerida")} años."
            "total_animales" -> "Ten ${n("numero_animales")} animales en el zoo."
            "animales_habitat" -> "Ten ${n("numero_animales")} animales en el hábitat $hb."
            "espacios_ocupados_habitat" ->
                "Ten ${n("numero_espacios_requeridos")} espacios del hábitat $hb con al menos ${n("numero_minimo_animales_por_espacio")} animales."
            "subir_prestigio" -> "Alcanza el nivel de prestigio ${n("nivel_prestigio_requerido")}."
            "vender_especie_edad" -> "Vende un ejemplar de $sp de ${n("edad_requerida")} años o más."
            "vender_edad" -> "Vende un animal de ${n("edad_requerida")} años o más."
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
