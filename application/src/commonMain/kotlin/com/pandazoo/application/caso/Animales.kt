package com.pandazoo.application.caso

import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.error.Resultado
import com.pandazoo.application.operacion.EjecutorOperacion
import com.pandazoo.application.operacion.Operacion
import com.pandazoo.application.puerto.AnimalFicha
import com.pandazoo.application.puerto.AnimalRepositorio
import com.pandazoo.application.puerto.AnimalResumen
import com.pandazoo.application.puerto.EconomiaRepositorio
import com.pandazoo.application.puerto.EspacioConHabitat
import com.pandazoo.application.puerto.EspecieTienda
import com.pandazoo.application.puerto.EstadoTienda
import com.pandazoo.application.puerto.GeneradorId
import com.pandazoo.application.puerto.NuevoAnimal
import com.pandazoo.application.puerto.Reloj
import com.pandazoo.application.puerto.TipoMovimiento
import com.pandazoo.application.puerto.UnidadesGratuitas
import com.pandazoo.application.puerto.ZooRepositorio
import com.pandazoo.application.puerto.valorVenta
import com.pandazoo.domain.animal.CatalogoFicha
import com.pandazoo.domain.animal.ReglasAnimal
import com.pandazoo.domain.animal.ReglasNivel
import com.pandazoo.domain.economia.Dinero
import com.pandazoo.domain.modelo.EstadoEntidad
import com.pandazoo.domain.modelo.Habitat
import com.pandazoo.domain.modelo.IdPartida

/** Reglas de vínculos sociales compartidas por CU-01 y CU-05 (RF-34, RF-48). */
internal object Vinculos {
    /**
     * @param propio animal que recibe la pareja; `idEspecie`, `idEspacio` y `nivel` son los suyos. El nivel mínimo solo se
     * exige al crear el vínculo (RF-34).
     */
    fun validarPareja(
        candidata: AnimalResumen?,
        propio: AnimalResumen?,
        idEspecie: String,
        idEspacio: String,
        nivel: Int,
        nivelMinimo: Int = ReglasNivel.NIVEL_MINIMO_PAREJA,
    ): MotivoRechazo? {
        if (candidata == null || !candidata.vivo) return MotivoRechazo.ParejaInexistente
        if (candidata.id == propio?.id) return MotivoRechazo.AutoReferencia
        if (candidata.idEspecie != idEspecie) return MotivoRechazo.ParejaOtraEspecie
        if (candidata.idEspacio != idEspacio) return MotivoRechazo.ParejaOtroEspacio
        if (nivel < nivelMinimo || candidata.nivel < nivelMinimo) return MotivoRechazo.ParejaNivelInsuficiente
        if (candidata.idPareja != null && candidata.idPareja != propio?.id) return MotivoRechazo.ParejaOcupada
        if (propio?.idPareja != null && propio.idPareja != candidata.id) return MotivoRechazo.ParejaOcupada
        return null
    }

    fun validarAmigo(candidato: AnimalResumen?, propio: AnimalResumen?, idEspacio: String): MotivoRechazo? {
        if (candidato == null || !candidato.vivo) return MotivoRechazo.AmigoInvalido
        if (candidato.id == propio?.id) return MotivoRechazo.AutoReferencia
        if (candidato.idEspacio != idEspacio) return MotivoRechazo.AmigoInvalido
        return null
    }

    fun validarFicha(nombre: String, habilidades: List<String>, rasgos: List<String>, catalogo: CatalogoFicha): MotivoRechazo? {
        if (nombre.isEmpty() || nombre.length > ReglasAnimal.MAX_NOMBRE) return MotivoRechazo.NombreInvalido
        if (!catalogo.habilidadesValidas(habilidades) || !catalogo.rasgosValidos(rasgos)) return MotivoRechazo.FichaIncompleta
        return null
    }
}

private fun rechazo(m: MotivoRechazo) = Resultado.Rechazado(m)

// ---------------------------------------------------------------------------------------------------- CU-01

data class FichaNueva(
    val nombre: String,
    val habilidades: List<String>,
    val rasgos: List<String>,
    val idMejorAmigo: String? = null,
)

data class ComandoAdquirirAnimal(
    val idPartida: IdPartida,
    val idEspecie: String,
    val idEspacio: String,
    val ficha: FichaNueva,
    val idSolicitud: String,
)

data class AnimalAdquirido(val idAnimal: String, val coste: Dinero, val saldo: Dinero)

/**
 * CU-01 Adquirir espécimen (RF-01, RF-29, RF-32, RF-33, RF-51, RES-08, RES-09). Alta atómica: cargo, animal de nivel 1,
 * alimentación 100 % y sano, ficha, mejor amigo y satisfacción. La pareja se asigna después con CU-05, cuando el
 * animal alcance el nivel mínimo.
 * La compatibilidad con el hábitat no impide la ubicación: solo afecta a la satisfacción.
 */
class AdquirirAnimal(
    private val ejecutor: EjecutorOperacion,
    private val economia: EconomiaRepositorio,
    private val zoo: ZooRepositorio,
    private val animales: AnimalRepositorio,
    private val catalogo: CatalogoFicha,
    private val ids: GeneradorId,
    private val reloj: Reloj,
    private val recalculo: RecalcularSatisfaccion,
    /** Unidades de coste cero concedidas por recompensas (RF-38): si hay una de la especie, se compra gratis y se consume. */
    private val unidades: UnidadesGratuitas? = null,
    private val misiones: ActualizarMisiones? = null,
) {
    fun ejecutar(c: ComandoAdquirirAnimal): Resultado<AnimalAdquirido> {
        val f = c.ficha
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "COMPRA_ANIMAL",
            entidad = "ANIMAL",
            origen = "CU-01",
            idSolicitud = c.idSolicitud,
            huella = "especie=${c.idEspecie};espacio=${c.idEspacio};nombre=${f.nombre.trim()};hab=${f.habilidades.sorted()};" +
                "ras=${f.rasgos.sorted()};amigo=${f.idMejorAmigo}",
            codificar = { "${it.idAnimal}|${it.coste.centimos}|${it.saldo.centimos}" },
            decodificar = {
                val (id, coste, saldo) = it.split("|")
                AnimalAdquirido(id, Dinero.deCentimos(coste.toLong()), Dinero.deCentimos(saldo.toLong()))
            },
            idEntidad = { it.idAnimal },
        )
        return ejecutor.ejecutar(op) { comprar(c) }
    }

    private fun comprar(c: ComandoAdquirirAnimal): Resultado<AnimalAdquirido> {
        val especie = animales.especieTienda(c.idPartida, c.idEspecie) ?: return rechazo(MotivoRechazo.EspecieInexistente)
        // La unidad concedida por una recompensa tiene prioridad sobre el estado de catálogo: coste cero y una sola compra.
        val gratuita = unidades?.especiesConUnidad(c.idPartida)?.contains(c.idEspecie) == true
        if (!gratuita) {
            when (especie.estado) {
                EstadoTienda.BLOQUEADA -> return rechazo(MotivoRechazo.EspecieBloqueada)
                EstadoTienda.EXCLUSIVA -> return rechazo(MotivoRechazo.EspecieExclusiva)
                EstadoTienda.COMERCIAL -> Unit
            }
        }
        val espacio = zoo.espacio(c.idPartida, c.idEspacio) ?: return rechazo(MotivoRechazo.EspacioInexistente)
        if (espacio.estado != EstadoEntidad.ACTIVO) return rechazo(MotivoRechazo.EspacioInactivo)
        val habitat = zoo.habitat(c.idPartida, espacio.idHabitat)
        if (habitat == null || !habitat.utilizable) return rechazo(MotivoRechazo.HabitatNoDisponible)
        if (espacio.ocupacion >= espacio.capacidadMax) return rechazo(MotivoRechazo.EspacioLleno)

        val nombre = c.ficha.nombre.trim()
        Vinculos.validarFicha(nombre, c.ficha.habilidades, c.ficha.rasgos, catalogo)?.let { return rechazo(it) }
        c.ficha.idMejorAmigo?.let { id ->
            Vinculos.validarAmigo(animales.animal(c.idPartida, id), null, c.idEspacio)?.let { return rechazo(it) }
        }

        val saldo = economia.fondos(c.idPartida)
        val coste = if (gratuita) Dinero.CERO else especie.coste
        if (saldo < coste) return rechazo(MotivoRechazo.SaldoInsuficiente(coste, saldo))

        val idAnimal = ids.nuevo()
        economia.actualizarFondos(c.idPartida, saldo - coste)
        animales.crear(
            c.idPartida,
            NuevoAnimal(
                id = idAnimal,
                idEspecie = c.idEspecie,
                idEspacio = c.idEspacio,
                nombre = nombre,
                habilidades = c.ficha.habilidades,
                rasgos = c.ficha.rasgos,
                idPareja = null,
                idMejorAmigo = c.ficha.idMejorAmigo,
            ),
        )
        economia.registrarMovimiento(c.idPartida, TipoMovimiento.COMPRA, coste, "CU-01", 0, reloj.ahoraSegundos())
        if (gratuita) check(unidades?.consumirUnidad(c.idPartida, c.idEspecie) == true) { "La unidad gratuita ya no está disponible" }
        misiones?.alComprar(c.idPartida, c.idEspecie)
        // La satisfacción se calcula con la ficha ya completa (RF-01), dentro de la misma transacción.
        recalculo.alConsolidar(c.idPartida, AlcanceRecalculo.Espacios(setOf(c.idEspacio)))
        return Resultado.Exito(AnimalAdquirido(idAnimal, coste, saldo - coste))
    }
}

// ---------------------------------------------------------------------------------------------------- CU-05

data class ComandoEditarFicha(
    val idPartida: IdPartida,
    val idAnimal: String,
    val nombre: String,
    val habilidades: List<String>,
    val rasgos: List<String>,
    /** Pareja resultante; `null` la elimina. */
    val idPareja: String?,
    /** Mejor amigo resultante; `null` lo elimina. */
    val idMejorAmigo: String?,
    val idSolicitud: String,
)

data class FichaEditada(val idAnimal: String)

/**
 * CU-05 Editar ficha: nombre, dos habilidades, dos rasgos, pareja y mejor amigo (RF-03, RF-34, RF-48, RES-03).
 * Solo existen estos campos en el comando: la especie, el nivel, la alimentación, la salud y la satisfacción no son editables.
 */
class EditarFicha(
    private val ejecutor: EjecutorOperacion,
    private val animales: AnimalRepositorio,
    private val catalogo: CatalogoFicha,
    private val recalculo: RecalcularSatisfaccion,
) {
    fun ejecutar(c: ComandoEditarFicha): Resultado<FichaEditada> {
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "EDICION_FICHA",
            entidad = "ANIMAL",
            origen = "CU-05",
            idSolicitud = c.idSolicitud,
            huella = "animal=${c.idAnimal};nombre=${c.nombre.trim()};hab=${c.habilidades.sorted()};ras=${c.rasgos.sorted()};" +
                "pareja=${c.idPareja};amigo=${c.idMejorAmigo}",
            codificar = { it.idAnimal },
            decodificar = { FichaEditada(it) },
            idEntidad = { it.idAnimal },
        )
        return ejecutor.ejecutar(op) { editar(c) }
    }

    private fun editar(c: ComandoEditarFicha): Resultado<FichaEditada> {
        val actual = animales.animal(c.idPartida, c.idAnimal) ?: return rechazo(MotivoRechazo.AnimalInexistente)
        val espacio = actual.idEspacio
        if (!actual.vivo || espacio == null) return rechazo(MotivoRechazo.AnimalNoVivo)

        val nombre = c.nombre.trim()
        Vinculos.validarFicha(nombre, c.habilidades, c.rasgos, catalogo)?.let { return rechazo(it) }
        val fichaActual = animales.ficha(c.idPartida, c.idAnimal) ?: return rechazo(MotivoRechazo.AnimalInexistente)

        // Un vínculo sin cambios no se vuelve a validar: la pareja puede haberse trasladado sin romper la relación.
        val cambiaPareja = c.idPareja != actual.idPareja
        val cambiaAmigo = c.idMejorAmigo != actual.idMejorAmigo
        if (cambiaPareja && c.idPareja != null) {
            Vinculos.validarPareja(animales.animal(c.idPartida, c.idPareja), actual, actual.idEspecie, espacio, actual.nivel)
                ?.let { return rechazo(it) }
        }
        if (cambiaAmigo && c.idMejorAmigo != null) {
            Vinculos.validarAmigo(animales.animal(c.idPartida, c.idMejorAmigo), actual, espacio)?.let { return rechazo(it) }
        }

        val cambiaFicha = nombre != actual.nombre ||
            c.habilidades.toSet() != fichaActual.habilidades.toSet() ||
            c.rasgos.toSet() != fichaActual.rasgos.toSet()
        if (!cambiaFicha && !cambiaPareja && !cambiaAmigo) return rechazo(MotivoRechazo.SinCambios)

        val afectados = HashSet<String>().apply {
            add(espacio)
            addAll(animales.espaciosRelacionados(c.idPartida, c.idAnimal))
        }
        if (cambiaFicha) animales.actualizarFicha(c.idPartida, c.idAnimal, nombre, c.habilidades, c.rasgos)
        if (cambiaPareja) animales.fijarPareja(c.idPartida, c.idAnimal, c.idPareja)
        if (cambiaAmigo) animales.fijarMejorAmigo(c.idPartida, c.idAnimal, c.idMejorAmigo)
        afectados += animales.espaciosRelacionados(c.idPartida, c.idAnimal)
        recalculo.alConsolidar(c.idPartida, AlcanceRecalculo.Espacios(afectados))
        return Resultado.Exito(FichaEditada(c.idAnimal))
    }
}

// ---------------------------------------------------------------------------------------------------- CU-04

data class ComandoReubicarAnimal(
    val idPartida: IdPartida,
    val idAnimal: String,
    val idEspacioDestino: String,
    val idSolicitud: String,
)

/** `origenVacio` indica que debe ofrecerse la baja opcional del espacio de origen (CU-04, paso 6). */
data class AnimalReubicado(val idAnimal: String, val idEspacioOrigen: String, val origenVacio: Boolean)

/** CU-04 Reubicar espécimen (RF-05, RF-29). La compatibilidad con el hábitat de destino no bloquea el traslado. */
class ReubicarAnimal(
    private val ejecutor: EjecutorOperacion,
    private val zoo: ZooRepositorio,
    private val animales: AnimalRepositorio,
    private val recalculo: RecalcularSatisfaccion,
) {
    fun ejecutar(c: ComandoReubicarAnimal): Resultado<AnimalReubicado> {
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "TRASLADO_ANIMAL",
            entidad = "ANIMAL",
            origen = "CU-04",
            idSolicitud = c.idSolicitud,
            huella = "animal=${c.idAnimal};destino=${c.idEspacioDestino}",
            codificar = { "${it.idAnimal}|${it.idEspacioOrigen}|${if (it.origenVacio) 1 else 0}" },
            decodificar = {
                val (id, origen, vacio) = it.split("|")
                AnimalReubicado(id, origen, vacio == "1")
            },
            idEntidad = { it.idAnimal },
        )
        return ejecutor.ejecutar(op) { trasladar(c) }
    }

    private fun trasladar(c: ComandoReubicarAnimal): Resultado<AnimalReubicado> {
        val animal = animales.animal(c.idPartida, c.idAnimal) ?: return rechazo(MotivoRechazo.AnimalInexistente)
        val origen = animal.idEspacio
        if (!animal.vivo || origen == null) return rechazo(MotivoRechazo.AnimalNoVivo)
        if (origen == c.idEspacioDestino) return rechazo(MotivoRechazo.DestinoIgualAOrigen)
        val destino = zoo.espacio(c.idPartida, c.idEspacioDestino) ?: return rechazo(MotivoRechazo.EspacioInexistente)
        if (destino.estado != EstadoEntidad.ACTIVO) return rechazo(MotivoRechazo.EspacioInactivo)
        if (destino.ocupacion >= destino.capacidadMax) return rechazo(MotivoRechazo.EspacioLleno)

        animales.mover(c.idPartida, c.idAnimal, c.idEspacioDestino)
        recalculo.alConsolidar(c.idPartida, AlcanceRecalculo.Espacios(setOf(origen, c.idEspacioDestino)))
        val vacio = (zoo.espacio(c.idPartida, origen)?.ocupacion ?: 0) == 0
        return Resultado.Exito(AnimalReubicado(c.idAnimal, origen, vacio))
    }
}

// ---------------------------------------------------------------------------------------------------- CU-06

/**
 * @property importeConfirmado importe que el gestor vio al confirmar; si el nivel cambió y el importe ya no coincide,
 * la venta se rechaza (CU-06, 4.a). `null` vende por el importe vigente (uso interno y tests).
 */
data class ComandoVenderAnimal(
    val idPartida: IdPartida,
    val idAnimal: String,
    val idSolicitud: String,
    val importeConfirmado: Dinero? = null,
)

data class AnimalVendido(val idAnimal: String, val idEspacioOrigen: String, val origenVacio: Boolean, val importe: Dinero = Dinero.CERO)

/** CU-06 Vender espécimen (RF-06, RF-52, RES-11): abona el importe según nivel y rango, libera el aforo y conserva la genealogía. */
class VenderAnimal(
    private val ejecutor: EjecutorOperacion,
    private val economia: EconomiaRepositorio,
    private val zoo: ZooRepositorio,
    private val animales: AnimalRepositorio,
    private val reloj: Reloj,
    private val recalculo: RecalcularSatisfaccion,
    private val misiones: ActualizarMisiones? = null,
) {
    fun ejecutar(c: ComandoVenderAnimal): Resultado<AnimalVendido> {
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "VENTA_ANIMAL",
            entidad = "ANIMAL",
            origen = "CU-06",
            idSolicitud = c.idSolicitud,
            huella = "animal=${c.idAnimal}",
            codificar = { "${it.idAnimal}|${it.idEspacioOrigen}|${if (it.origenVacio) 1 else 0}|${it.importe.centimos}" },
            decodificar = {
                val (id, origen, vacio, importe) = it.split("|")
                AnimalVendido(id, origen, vacio == "1", Dinero.deCentimos(importe.toLong()))
            },
            idEntidad = { it.idAnimal },
        )
        return ejecutor.ejecutar(op) { vender(c) }
    }

    private fun vender(c: ComandoVenderAnimal): Resultado<AnimalVendido> {
        val animal = animales.animal(c.idPartida, c.idAnimal) ?: return rechazo(MotivoRechazo.AnimalInexistente)
        val origen = animal.idEspacio
        if (!animal.vivo || origen == null) return rechazo(MotivoRechazo.AnimalNoVivo)
        if (animal.enfermo) return rechazo(MotivoRechazo.AnimalEnfermo)
        if (animal.alimentacion <= 0) return rechazo(MotivoRechazo.SinAlimentacion)
        val importe = animal.valorVenta()
        if (c.importeConfirmado != null && c.importeConfirmado != importe) return rechazo(MotivoRechazo.ImporteVentaCambiado(importe))

        // Los afectados se calculan antes de retirar el animal: después ya no hay vínculos que seguir.
        val afectados = HashSet<String>().apply {
            add(origen)
            addAll(animales.espaciosRelacionados(c.idPartida, c.idAnimal))
        }
        animales.retirar(c.idPartida, c.idAnimal, ESTADO_VENDIDO)
        economia.actualizarFondos(c.idPartida, economia.fondos(c.idPartida) + importe)
        economia.registrarMovimiento(c.idPartida, TipoMovimiento.VENTA, importe, "CU-06", 0, reloj.ahoraSegundos())
        misiones?.alVender(c.idPartida, animal.idEspecie, animal.nivel)
        recalculo.alConsolidar(c.idPartida, AlcanceRecalculo.Espacios(afectados))
        val vacio = (zoo.espacio(c.idPartida, origen)?.ocupacion ?: 0) == 0
        return Resultado.Exito(AnimalVendido(c.idAnimal, origen, vacio, importe))
    }

    private companion object {
        const val ESTADO_VENDIDO = "VENDIDO"
    }
}

// ---------------------------------------------------------------------------------------------------- CU-14

data class ComandoDarDeBajaEspacio(val idPartida: IdPartida, val idEspacio: String, val idSolicitud: String)

data class EspacioDadoDeBaja(val idEspacio: String)

/** CU-14 Dar de baja espacio vacío (RF-07): baja lógica, sin borrar nada ni invalidar referencias. */
class DarDeBajaEspacio(
    private val ejecutor: EjecutorOperacion,
    private val zoo: ZooRepositorio,
) {
    fun ejecutar(c: ComandoDarDeBajaEspacio): Resultado<EspacioDadoDeBaja> {
        val op = Operacion(
            idPartida = c.idPartida,
            tipo = "BAJA_ESPACIO",
            entidad = "ESPACIO",
            origen = "CU-14",
            idSolicitud = c.idSolicitud,
            huella = "espacio=${c.idEspacio}",
            codificar = { it.idEspacio },
            decodificar = { EspacioDadoDeBaja(it) },
            idEntidad = { it.idEspacio },
        )
        return ejecutor.ejecutar(op) { baja(c) }
    }

    private fun baja(c: ComandoDarDeBajaEspacio): Resultado<EspacioDadoDeBaja> {
        val espacio = zoo.espacio(c.idPartida, c.idEspacio) ?: return rechazo(MotivoRechazo.EspacioInexistente)
        if (espacio.estado != EstadoEntidad.ACTIVO) return rechazo(MotivoRechazo.EspacioInactivo)
        if (espacio.ocupacion > 0) return rechazo(MotivoRechazo.EspacioNoVacio)
        if (zoo.tieneDependencias(c.idPartida, c.idEspacio)) return rechazo(MotivoRechazo.EspacioConDependencias)
        zoo.darDeBaja(c.idPartida, c.idEspacio)
        return Resultado.Exito(EspacioDadoDeBaja(c.idEspacio))
    }
}

// ---------------------------------------------------------------------------------------------------- Consultas

/** `gratuita`: una recompensa de misión concedió una unidad de coste cero de esta especie (RF-38). */
data class OfertaAnimal(val especie: EspecieTienda, val asequible: Boolean, val gratuita: Boolean = false)

/** Tienda de animales (CU-01, paso 2): todas las especies con su estado; solo las comerciales desbloqueadas se compran. */
class ConsultarTiendaAnimales(
    private val animales: AnimalRepositorio,
    private val economia: EconomiaRepositorio,
    private val unidades: UnidadesGratuitas? = null,
) {
    fun ejecutar(idPartida: IdPartida): List<OfertaAnimal> {
        val saldo = economia.fondos(idPartida)
        val gratuitas = unidades?.especiesConUnidad(idPartida).orEmpty()
        return animales.especiesTienda(idPartida).map {
            val gratis = it.id in gratuitas
            OfertaAnimal(it, gratis || (it.estado == EstadoTienda.COMERCIAL && saldo >= it.coste), gratis)
        }
    }
}

/** Navegación Hábitats a Espacios a Animales y selección de ubicación (RF-08, RF-25). Solo lectura. */
class ConsultarAnimales(
    private val animales: AnimalRepositorio,
    private val zoo: ZooRepositorio,
) {
    fun animalesDe(idPartida: IdPartida, idEspacio: String): List<AnimalResumen> = animales.animalesDeEspacio(idPartida, idEspacio)

    fun ficha(idPartida: IdPartida, idAnimal: String): AnimalFicha? = animales.ficha(idPartida, idAnimal)

    /** Hábitats utilizables con al menos un espacio activo con aforo libre (CU-01, paso 3). */
    fun habitatsConCupo(idPartida: IdPartida): List<Habitat> {
        val conCupo = zoo.espaciosConCupo(idPartida).map { it.espacio.idHabitat }.toSet()
        return zoo.habitats(idPartida).filter { it.utilizable && it.id in conCupo }
    }

    fun espaciosConCupo(idPartida: IdPartida, idHabitat: String) =
        zoo.espaciosConCupo(idPartida).filter { it.espacio.idHabitat == idHabitat }.map { it.espacio }

    /** Espacios de destino posibles para un traslado: activos, con aforo libre y distintos del actual (CU-04, paso 2). */
    fun destinosPosibles(idPartida: IdPartida, idEspacioActual: String?): List<EspacioConHabitat> =
        zoo.espaciosConCupo(idPartida).filter { it.espacio.id != idEspacioActual }
}
