package com.pandazoo.application.caso

import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.operacion.EjecutorOperacion
import com.pandazoo.application.operacion.Operacion
import com.pandazoo.application.puerto.AnimalRepositorio
import com.pandazoo.application.puerto.AuditoriaRepositorio
import com.pandazoo.application.puerto.Azar
import com.pandazoo.application.puerto.CicloRepositorio
import com.pandazoo.application.puerto.Concepcion
import com.pandazoo.application.puerto.NuevoAnimal
import com.pandazoo.application.puerto.RegistroAuditoria
import com.pandazoo.application.puerto.Reloj
import com.pandazoo.application.puerto.ReproduccionRepositorio
import com.pandazoo.application.puerto.ResultadoAuditoria
import com.pandazoo.application.puerto.GeneradorId
import com.pandazoo.application.puerto.ZooRepositorio
import com.pandazoo.domain.animal.CatalogoFicha
import com.pandazoo.domain.modelo.EstadoEntidad
import com.pandazoo.domain.modelo.IdPartida
import com.pandazoo.domain.reproduccion.ReglasReproduccion

// ---------------------------------------------------------------------------------------------------- Etapa del ciclo

/**
 * Etapa de reproducción del ciclo (CU-11, RF-16). Primero expira las concepciones vencidas y después evalúa, en orden de
 * identificadores, cada pareja válida: ambos progenitores con la satisfacción vigente al inicio del ciclo (sin redondear)
 * en el umbral del rango y un sorteo con probabilidad `max(0, p − penalización × hijos vivos comunes)`.
 * Una pareja concibe como máximo una vez por ciclo y no se evalúa mientras tenga una concepción abierta.
 *
 * En diferido (CU-12) nadie puede alimentar a los animales, así que el umbral se mide con la satisfacción de cada
 * animal al empezar la sesión, igual que los límites de RF-22 y RF-23; y la ventana de aceptación de las concepciones
 * de la sesión cuenta desde su último ciclo, para que no caduquen antes de que el jugador vuelva: se registran como
 * concebidas en ese último ciclo (así `ciclo_disponible` y `ciclo_expiracion` siguen la regla de siempre).
 */
class EtapaReproduccion(
    private val reproduccion: ReproduccionRepositorio,
    private val auditoria: AuditoriaRepositorio,
    private val azar: Azar,
    private val reloj: Reloj,
) : EtapaCiclo {
    override fun ejecutar(idPartida: IdPartida, numeroCiclo: Int, vivos: List<AnimalEnCiclo>) = ejecutar(idPartida, numeroCiclo, vivos, null)

    override fun ejecutar(idPartida: IdPartida, numeroCiclo: Int, vivos: List<AnimalEnCiclo>, diferido: ContextoDiferido?) {
        reproduccion.expirarVencidas(idPartida, numeroCiclo)
        val satisfaccion = vivos.associate { it.id to if (diferido == null) it.satisfaccionInicial else it.inicioSesion.satisfaccion }
        val cicloRegistro = diferido?.ultimoCiclo ?: numeroCiclo
        val ahora = reloj.ahoraSegundos()
        for (p in reproduccion.parejasReproductoras(idPartida)) {
            if (p.concepcionAbierta) continue
            val sa = satisfaccion[p.idA] ?: continue
            val sb = satisfaccion[p.idB] ?: continue
            if (!ReglasReproduccion.cumpleUmbral(sa, sb, p.umbral)) continue
            val probabilidad = ReglasReproduccion.probabilidad(p.probabilidad, p.factoresPorHijo, p.hijosVivos)
            if (!ReglasReproduccion.concibe(azar.siguienteDouble(), probabilidad)) continue
            reproduccion.registrarConcepcion(idPartida, cicloRegistro, p.idA, p.idB, ahora)
            auditoria.registrar(
                RegistroAuditoria(
                    idPartida = idPartida,
                    tipo = "CONCEPCION",
                    entidad = "REPRODUCCION",
                    idEntidad = "$cicloRegistro:${p.idA}:${p.idB}",
                    origen = "CICLO_PERIODICO",
                    resultado = ResultadoAuditoria.CONFIRMADO,
                    numeroCiclo = numeroCiclo,
                    claveIdempotencia = "$idPartida:$numeroCiclo:CONCEPCION:${p.idA}:${p.idB}",
                    fecha = ahora,
                    detalle = "CONCEBIDA",
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------- Validación común

/** Comprobaciones de vigencia, progenitores y aforo compartidas por la consulta y por la confirmación (regla 5.3). */
internal class ValidadorConcepcion(
    private val reproduccion: ReproduccionRepositorio,
    private val ciclos: CicloRepositorio,
    private val animales: AnimalRepositorio,
    private val zoo: ZooRepositorio,
) {
    fun concepcion(idPartida: IdPartida, idNotificacion: Long): Concepcion? = reproduccion.concepcionDeAviso(idPartida, idNotificacion)

    /** Primer motivo por el que la concepción no puede aceptarse ahora, o `null` si puede. */
    fun motivo(idPartida: IdPartida, c: Concepcion): MotivoRechazo? {
        val ultimo = ciclos.estadoMotor(idPartida)?.numeroUltimo ?: 0
        if (c.estado != "CONCEBIDA" || c.estadoAviso !in ABIERTOS || !ReglasReproduccion.vigente(ultimo, c.cicloExpiracion)) {
            return MotivoRechazo.ConcepcionNoVigente
        }
        val a = animales.animal(idPartida, c.idA)
        val b = animales.animal(idPartida, c.idB)
        if (a == null || b == null || !a.vivo || !b.vivo) return MotivoRechazo.ProgenitoresNoValidos
        val espacio = a.idEspacio
        if (espacio == null || espacio != b.idEspacio || a.idPareja != b.id || b.idPareja != a.id) {
            return MotivoRechazo.ProgenitoresNoValidos
        }
        val e = zoo.espacio(idPartida, espacio) ?: return MotivoRechazo.EspacioInexistente
        if (e.estado != EstadoEntidad.ACTIVO) return MotivoRechazo.EspacioInactivo
        if (e.ocupacion >= e.capacidadMax) return MotivoRechazo.EspacioLleno
        return null
    }

    private companion object {
        val ABIERTOS = setOf("PENDIENTE", "VISTA")
    }
}

// ---------------------------------------------------------------------------------------------------- CU-10

data class ComandoAceptarReproduccion(
    val idPartida: IdPartida,
    val idNotificacion: Long,
    /** Nombre, dos habilidades, dos rasgos y mejor amigo opcional; la pareja de una cría no se indica. */
    val ficha: FichaNueva,
    val idSolicitud: String,
)

data class CriaRegistrada(val idAnimal: String, val idEspacio: String)

/**
 * CU-10 Aceptar reproducción (RF-18, RF-51, RES-08). Alta atómica de la cría de nivel 1, alimentación 100 %, sana, progenitores
 * por identificador (menor padre, mayor madre) y satisfacción calculada tras
 * completar la ficha. La concepción pasa a `CONFIRMADA` y el aviso a `ACEPTADA`. Es idempotente y definitiva.
 */
class AceptarReproduccion(
    private val ejecutor: EjecutorOperacion,
    private val reproduccion: ReproduccionRepositorio,
    private val ciclos: CicloRepositorio,
    private val animales: AnimalRepositorio,
    private val zoo: ZooRepositorio,
    private val catalogo: CatalogoFicha,
    private val ids: GeneradorId,
    private val recalculo: RecalcularSatisfaccion,
) {
    private val validador = ValidadorConcepcion(reproduccion, ciclos, animales, zoo)

    fun ejecutar(c: ComandoAceptarReproduccion): Resultado<CriaRegistrada> {
        val f = c.ficha
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "REPRODUCCION_CONFIRMADA",
            entidad = "REPRODUCCION",
            origen = "CU-10",
            idSolicitud = c.idSolicitud,
            huella = "aviso=${c.idNotificacion};nombre=${f.nombre.trim()};hab=${f.habilidades.sorted()};" +
                "ras=${f.rasgos.sorted()};amigo=${f.idMejorAmigo}",
            codificar = { "${it.idAnimal}|${it.idEspacio}" },
            decodificar = {
                val (animal, espacio) = it.split("|")
                CriaRegistrada(animal, espacio)
            },
            idEntidad = { it.idAnimal },
        )
        return ejecutor.ejecutar(op) { aceptar(c) }
    }

    private fun aceptar(c: ComandoAceptarReproduccion): Resultado<CriaRegistrada> {
        val concepcion = validador.concepcion(c.idPartida, c.idNotificacion) ?: return Resultado.Rechazado(MotivoRechazo.AvisoInexistente)
        validador.motivo(c.idPartida, concepcion)?.let { return Resultado.Rechazado(it) }

        val nombre = c.ficha.nombre.trim()
        Vinculos.validarFicha(nombre, c.ficha.habilidades, c.ficha.rasgos, catalogo)?.let { return Resultado.Rechazado(it) }
        val padre = requireNotNull(animales.animal(c.idPartida, concepcion.idA))
        val espacio = requireNotNull(padre.idEspacio)
        val (idPadre, idMadre) = ReglasReproduccion.progenitores(concepcion.idA, concepcion.idB)
        c.ficha.idMejorAmigo?.let { id ->
            val familiares = animales.familiaresDeCria(c.idPartida, idPadre, idMadre)
            Vinculos.validarAmigo(animales.animal(c.idPartida, id), null, espacio, familiares)?.let { return Resultado.Rechazado(it) }
        }
        val idCria = ids.nuevo()
        animales.crear(
            c.idPartida,
            NuevoAnimal(
                id = idCria,
                idEspecie = padre.idEspecie,
                idEspacio = espacio,
                nombre = nombre,
                habilidades = c.ficha.habilidades,
                rasgos = c.ficha.rasgos,
                idPareja = null,
                idMejorAmigo = c.ficha.idMejorAmigo,
                idPadre = idPadre,
                idMadre = idMadre,
            ),
        )
        reproduccion.confirmar(c.idPartida, concepcion, ciclos.estadoMotor(c.idPartida)?.numeroUltimo ?: 0)
        // Los hermanos alojados en otros espacios también cambian su `S_soc` al nacer la cría.
        val afectados = HashSet<String>().apply {
            add(espacio)
            addAll(animales.espaciosRelacionados(c.idPartida, idCria))
        }
        recalculo.alConsolidar(c.idPartida, AlcanceRecalculo.Espacios(afectados))
        return Resultado.Exito(CriaRegistrada(idCria, espacio))
    }
}

// ---------------------------------------------------------------------------------------------------- Consulta

/** Información descriptiva de una concepción y estado de su acción de aceptar (CU-10, pasos 1 y 2). */
data class DetalleReproduccion(
    val idNotificacion: Long,
    val especie: String,
    val nombreA: String,
    val nombreB: String,
    val idA: String,
    val idB: String,
    val idEspacio: String?,
    val nombreEspacio: String?,
    val habilitada: Boolean,
    val motivoDeshabilitada: MotivoRechazo?,
    val ultimoCicloAceptable: Int,
)

/** Solo lectura: no cambia ningún estado ni dispara recálculo. */
class ConsultarReproduccion(
    private val reproduccion: ReproduccionRepositorio,
    private val ciclos: CicloRepositorio,
    private val animales: AnimalRepositorio,
    private val zoo: ZooRepositorio,
) {
    private val validador = ValidadorConcepcion(reproduccion, ciclos, animales, zoo)

    fun ejecutar(idPartida: IdPartida, idNotificacion: Long): DetalleReproduccion? {
        val c = validador.concepcion(idPartida, idNotificacion) ?: return null
        val a = animales.animal(idPartida, c.idA)
        val b = animales.animal(idPartida, c.idB)
        val motivo = validador.motivo(idPartida, c)
        val nombreEspacio = a?.idEspacio?.let { zoo.espacio(idPartida, it)?.nombre }
        return DetalleReproduccion(
            idNotificacion = idNotificacion,
            especie = a?.especie ?: b?.especie ?: "",
            nombreA = a?.nombre ?: "",
            nombreB = b?.nombre ?: "",
            idA = c.idA,
            idB = c.idB,
            idEspacio = a?.idEspacio,
            nombreEspacio = nombreEspacio,
            habilitada = motivo == null,
            motivoDeshabilitada = motivo,
            ultimoCicloAceptable = c.cicloExpiracion,
        )
    }
}
