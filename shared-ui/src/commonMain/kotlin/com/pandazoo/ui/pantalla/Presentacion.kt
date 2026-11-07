package com.pandazoo.ui.pantalla

import androidx.compose.runtime.compositionLocalOf
import kotlin.jvm.JvmInline
import com.pandazoo.ui.AppContainer
import com.pandazoo.application.caso.AnimalMonitor
import com.pandazoo.application.caso.EspacioMonitor
import com.pandazoo.application.caso.HabitatMonitor
import com.pandazoo.application.caso.MonitorSatisfaccion
import com.pandazoo.application.caso.PanelPrestigio
import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.application.caso.ConsultarBeneficios
import com.pandazoo.application.caso.RegistroCritico
import com.pandazoo.application.puerto.Aviso
import com.pandazoo.application.caso.MisionVista
import com.pandazoo.application.caso.OfertaAnimal
import com.pandazoo.application.caso.RecompensaReclamada
import com.pandazoo.application.caso.RecompensaVista
import com.pandazoo.application.puerto.TipoRecompensa
import com.pandazoo.application.puerto.AnimalFicha
import com.pandazoo.application.puerto.valorVenta
import com.pandazoo.application.puerto.AnimalResumen
import com.pandazoo.application.puerto.EspacioConHabitat
import com.pandazoo.application.puerto.EstadoTienda
import com.pandazoo.domain.animal.CatalogoFicha
import com.pandazoo.domain.animal.ReglasCuidado
import com.pandazoo.domain.animal.ReglasNivel
import com.pandazoo.domain.economia.Redondeo
import com.pandazoo.domain.modelo.Espacio
import com.pandazoo.domain.modelo.Existencia
import com.pandazoo.domain.modelo.Habitat
import com.pandazoo.domain.modelo.InventarioFinanzas
import com.pandazoo.domain.modelo.OfertaRecurso
import com.pandazoo.domain.modelo.Seccion
import com.pandazoo.domain.modelo.TipoRecurso
import com.pandazoo.domain.economia.Dinero

val LocalContenedor = compositionLocalOf<AppContainer> { error("Falta el contenedor de dependencias") }

/**
 * Referencia opaca para resolver acciones y navegación. Nunca se muestra (RES-06): su `toString` es
 * constante y los modelos de presentación no exponen ningún otro identificador.
 */
@JvmInline
value class Ref(val valor: String) {
    override fun toString(): String = "Ref"
}

data class LineaUi(val etiqueta: String, val valor: String)

/** `null` en una sección significa que el dato no está disponible; se informa, no se presenta como cero. */
data class InicioUi(
    val saldo: String?,
    val beneficiosPendientes: String?,
    val existencias: List<LineaUi>?,
    val bambu: String?,
    val insumosMedicos: String?,
    val avisos: List<String>,
)

/** Datos de la cabecera global: satisfacción del parque, saldo, bambú e insumos médicos. */
data class CabeceraUi(val satisfaccion: String, val saldo: String, val bambu: String, val insumos: String, val prestigio: String)

/** Panel de prestigio (CU-19). Un dato no disponible se muestra como tal, nunca como cero. */
data class PrestigioUi(
    val nivel: String,
    val maximoNivel: String,
    val maximoAnimales: String,
    val maximoSatisfaccion: String,
    val animales: String,
    val satisfaccion: String,
    /** Requisitos del nivel siguiente; `null` si ya se alcanzó el nivel máximo. */
    val siguiente: String?,
    val avisos: List<String>,
)

/** Aviso del historial (CU-21). `tono` reutiliza color, icono y texto explícito de [TipoAviso]. */
data class AvisoItemUi(
    val id: Long,
    val etiqueta: String,
    val texto: String,
    val estado: String,
    val fecha: String,
    val tono: TipoAviso,
    val critico: Boolean,
    val pendiente: Boolean,
    /** Aviso de reproducción aún aceptable desde la lista: abre el formulario de la cría. */
    val reproduccion: Boolean = false,
)

/** Fila del seguimiento crítico (CU-18). */
data class CriticoUi(val ref: Ref, val titulo: String, val causa: String, val detalle: String, val urgente: Boolean)

data class BeneficiosUi(val pendiente: String, val importe: Dinero, val saldo: String, val porCiclo: List<LineaUi>, val puedeReclamar: Boolean)

data class HabitatUi(val ref: Ref, val nombre: String, val disponible: Boolean)

data class EspacioUi(val ref: Ref, val nombre: String, val detalle: String, val activo: Boolean, val ocupacion: Int = 0)

data class OfertaUi(val ref: Ref, val etiqueta: String, val unidad: String, val precio: Dinero?, val insumo: Boolean = false)

data class CapacidadUi(val capacidad: Int, val coste: Dinero, val etiqueta: String)

/** Monitor de satisfacción (CU-17). `clave` solo identifica la fila abierta; se compone de nombres, no de claves internas. */
data class MonitorUi(val parque: String, val aviso: String?, val habitats: List<HabitatMonitorUi>, val avisoHabitats: String?)

data class HabitatMonitorUi(
    val clave: String,
    val nombre: String,
    val valor: String,
    val espacios: List<EspacioMonitorUi>,
    val aviso: String?,
)

data class EspacioMonitorUi(
    val clave: String,
    val nombre: String,
    val valor: String,
    val animales: List<AnimalMonitorUi>,
    val aviso: String?,
)

data class AnimalMonitorUi(val etiqueta: String, val valor: String)

/** Misión vigente con su progreso y recompensa ya formateados; `ref` es opaca y solo sirve para reclamar. */
data class MisionUi(
    val ref: Ref,
    val descripcion: String,
    val progreso: String,
    val recompensa: String,
    val cumplida: Boolean,
    val reclamable: Boolean,
)

/** Especie en la tienda de animales. `seleccionable` es falso si está bloqueada, es exclusiva o el saldo no alcanza. */
data class OfertaAnimalUi(
    val ref: Ref,
    val nombre: String,
    val rango: String,
    val coste: String,
    val estado: String,
    /** Línea bajo el nombre: «rango · estado», o solo el estado en las exclusivas, que ya nombran su rango. */
    val detalle: String,
    val seleccionable: Boolean,
    val bloqueada: Boolean,
)

/** `nombre` incluye el sufijo de desambiguación (edad u orden); la especie se muestra aparte, más pequeña y en cursiva. */
data class AnimalItemUi(
    val ref: Ref,
    val nombre: String,
    val especie: String,
    val rango: String,
    val detalle: String,
    /** Vivo y por debajo del 100 % de alimentación: entra en la acción grupal (CU-13). */
    val necesitaAlimento: Boolean = false,
)

data class CandidatoUi(val ref: Ref, val etiqueta: String)

data class DestinoUi(val ref: Ref, val etiqueta: String)

/** Ficha técnica legible; las referencias `id*` solo sirven para precargar el formulario de edición. */
data class FichaAnimalUi(
    val ref: Ref,
    val nombre: String,
    val vivo: Boolean,
    val lineas: List<LineaUi>,
    val habilidades: List<String>,
    val rasgos: List<String>,
    val idHabilidades: List<String>,
    val idRasgos: List<String>,
    val idPareja: String?,
    val idMejorAmigo: String?,
    val nombrePareja: String?,
    val nombreMejorAmigo: String?,
    val alimentacion: Int,
    val enfermo: Boolean,
    val rango: String,
    /** Importe que se acreditaría al vender ahora; se envía al confirmar para detectar un cambio de nivel (CU-06, 4.a). */
    val importeVenta: Dinero,
    val textoVenta: String,
)

object Textos {
    /** Los hábitats se muestran sin la palabra «Hábitat» (p. ej. «Bosque templado»). */
    fun nombreHabitat(nombre: String) = nombre.removePrefix("Hábitat ").trim()

    const val MONEDA = "🪙"

    fun dinero(valor: Dinero) = "$MONEDA ${valor}"

    fun inicio(v: InventarioFinanzas): InicioUi {
        val avisos = mutableListOf<String>()
        fun <T> valor(s: Seccion<T>): T? =
            when (s) {
                is Seccion.Disponible -> s.valor
                is Seccion.NoDisponible -> {
                    avisos += s.motivo
                    null
                }
            }
        val existencias = (v.existencias as? Seccion.Disponible)?.valor
        return InicioUi(
            saldo = valor(v.fondos)?.let(::dinero),
            beneficiosPendientes = valor(v.beneficiosPendientes)?.let(::dinero),
            existencias = valor(v.existencias)?.map { LineaUi(it.nombre, "${it.cantidad} ${it.unidad}") },
            bambu = existencias?.let { l -> "${l.filter { it.recurso.tipo == TipoRecurso.ALIMENTO }.sumOf { it.cantidad }} kg" },
            insumosMedicos = existencias?.let { l -> "${l.filter { it.recurso.tipo == TipoRecurso.INSUMO }.sumOf { it.cantidad }}" },
            avisos = avisos,
        )
    }

    fun habitat(h: Habitat) =
        HabitatUi(
            ref = Ref(h.id),
            nombre = nombreHabitat(h.nombre),
            disponible = h.utilizable,
        )

    fun espacio(e: Espacio) =
        EspacioUi(
            ref = Ref(e.id),
            nombre = e.nombre,
            detalle = "Ocupación ${e.ocupacion}/${e.capacidadMax}" +
                if (e.estado.name == "INACTIVO") " · De baja" else "",
            activo = e.estado.name == "ACTIVO",
            ocupacion = e.ocupacion,
        )

    fun oferta(o: OfertaRecurso) =
        OfertaUi(
            ref = Ref(o.recurso.codigo),
            etiqueta = o.nombre + (o.precio?.let { " — ${dinero(it)} por ${if (o.unidad == "kg") "kg" else "unidad"}" } ?: " — sin precio"),
            unidad = o.unidad,
            precio = o.precio,
            insumo = o.recurso.tipo == TipoRecurso.INSUMO,
        )

    /** Porcentaje entero con los medios hacia arriba (RF-15). */
    fun porcentaje(valor: Double) = "${Redondeo.porcentaje(valor)}%"

    /** Texto breve del indicador de la cabecera. */
    fun nivelPrestigio(p: PanelPrestigio): String =
        when (val a = p.actual) {
            is Seccion.Disponible -> "Nivel ${a.valor}"
            is Seccion.NoDisponible -> "Nivel N/D"
        }

    fun prestigio(p: PanelPrestigio): PrestigioUi {
        val sinMaximos = "Sin registrar"
        val maximos = (p.maximos as? Seccion.Disponible)?.valor
        val maximosDisponibles = p.maximos is Seccion.Disponible
        fun <T> dato(s: Seccion<T>, formato: (T) -> String) = when (s) {
            is Seccion.Disponible -> formato(s.valor)
            is Seccion.NoDisponible -> NO_DISPONIBLE
        }
        return PrestigioUi(
            nivel = nivelPrestigio(p),
            maximoNivel = if (!maximosDisponibles) NO_DISPONIBLE else maximos?.let { "Nivel ${it.nivel}" } ?: sinMaximos,
            maximoAnimales = if (!maximosDisponibles) NO_DISPONIBLE else maximos?.animalesVivos?.toString() ?: sinMaximos,
            maximoSatisfaccion = if (!maximosDisponibles) NO_DISPONIBLE else maximos?.let { porcentaje(it.satisfaccionGlobal) } ?: sinMaximos,
            animales = dato(p.animalesVivos) { it.toString() },
            satisfaccion = dato(p.satisfaccionGlobal, ::porcentaje),
            siguiente = p.siguiente?.let { "Nivel ${it.nivel}: ${it.animalesVivos} animales vivos y ${porcentaje(it.satisfaccionGlobal)} de satisfacción" },
            avisos = listOf(p.actual, p.maximos, p.animalesVivos, p.satisfaccionGlobal).mapNotNull(::aviso).distinct(),
        )
    }

    private const val SIN_ANIMALES = "Sin animales"
    private const val NO_DISPONIBLE = "No disponible"

    private fun valorNivel(s: Seccion<Double?>): String =
        when (s) {
            is Seccion.Disponible -> s.valor?.let(::porcentaje) ?: SIN_ANIMALES
            is Seccion.NoDisponible -> NO_DISPONIBLE
        }

    private fun aviso(s: Seccion<*>): String? = (s as? Seccion.NoDisponible)?.motivo

    fun monitor(m: MonitorSatisfaccion): MonitorUi =
        MonitorUi(
            parque = when (val p = m.parque) {
                is Seccion.Disponible -> porcentaje(p.valor)
                is Seccion.NoDisponible -> NO_DISPONIBLE
            },
            aviso = aviso(m.parque),
            habitats = (m.habitats as? Seccion.Disponible)?.valor.orEmpty().map(::habitatMonitor),
            avisoHabitats = aviso(m.habitats),
        )

    private fun habitatMonitor(h: HabitatMonitor) =
        HabitatMonitorUi(
            clave = h.nombre,
            nombre = nombreHabitat(h.nombre),
            valor = valorNivel(h.valor),
            espacios = (h.espacios as? Seccion.Disponible)?.valor.orEmpty().map { espacioMonitor(h.nombre, it) },
            aviso = aviso(h.valor) ?: aviso(h.espacios),
        )

    private fun espacioMonitor(habitat: String, e: EspacioMonitor) =
        EspacioMonitorUi(
            clave = "$habitat|${e.nombre}",
            nombre = e.nombre,
            valor = valorNivel(e.valor),
            animales = animalesMonitor((e.animales as? Seccion.Disponible)?.valor.orEmpty()),
            aviso = aviso(e.valor) ?: aviso(e.animales),
        )

    private fun animalesMonitor(l: List<AnimalMonitor>): List<AnimalMonitorUi> {
        val etiquetas = desambiguar(l.map { "${it.nombre} (${it.especie})" }, l.map { it.nivel })
        return l.mapIndexed { i, a -> AnimalMonitorUi(etiquetas[i], porcentaje(a.valor)) }
    }

    fun existencia(e: Existencia) = LineaUi(e.nombre, "${e.cantidad} ${e.unidad}")

    fun motivo(m: MotivoRechazo): String =
        when (m) {
            is MotivoRechazo.SaldoInsuficiente ->
                "Saldo insuficiente: cuesta ${dinero(m.coste)} y dispones de ${dinero(m.saldo)}."
            MotivoRechazo.CantidadInvalida -> "La cantidad debe ser un número entero positivo."
            MotivoRechazo.RecursoNoElegible -> "Ese recurso no está disponible en la tienda."
            MotivoRechazo.PrecioNoConfigurado -> "El recurso no tiene un precio configurado; no se puede comprar."
            MotivoRechazo.CapacidadNoPermitida -> "Esa capacidad no está disponible."
            MotivoRechazo.DenominacionInvalida -> "La denominación no puede estar vacía ni superar 40 caracteres."
            MotivoRechazo.DenominacionDuplicada -> "Ya existe un espacio con esa denominación en el hábitat."
            MotivoRechazo.HabitatNoDisponible -> "El hábitat no está disponible o está bloqueado."
            MotivoRechazo.EspacioInexistente -> "El espacio ya no existe."
            MotivoRechazo.EspacioInactivo -> "El espacio está dado de baja y no se puede configurar."
            MotivoRechazo.SinCambios -> "No hay cambios que aplicar."
            MotivoRechazo.EspecieInexistente -> "La especie ya no existe en el catálogo."
            MotivoRechazo.EspecieBloqueada -> "Esa especie aún está bloqueada: súbela de nivel para comprarla."
            MotivoRechazo.EspecieExclusiva -> "Las especies exclusivas solo se obtienen con misiones."
            MotivoRechazo.EspacioLleno -> "El espacio no tiene capacidad libre."
            MotivoRechazo.NombreInvalido -> "El nombre no puede estar vacío ni superar 40 caracteres."
            MotivoRechazo.FichaIncompleta -> "La ficha necesita exactamente dos habilidades sociales y dos rasgos de personalidad."
            MotivoRechazo.ParejaInexistente -> "La pareja elegida ya no está en el zoo."
            MotivoRechazo.ParejaOtraEspecie -> "La pareja debe ser de la misma especie."
            MotivoRechazo.ParejaOtroEspacio -> "La pareja debe estar en el mismo espacio."
            MotivoRechazo.ParejaNivelInsuficiente ->
                "Para ser pareja, los dos animales deben tener al menos el nivel ${ReglasNivel.NIVEL_MINIMO_PAREJA}."
            MotivoRechazo.ParejaOcupada -> "Alguno de los dos ya tiene otra pareja. Quítala antes de elegir una nueva."
            MotivoRechazo.AmigoInvalido -> "El mejor amigo debe ser otro animal del mismo espacio."
            MotivoRechazo.AutoReferencia -> "Un animal no puede ser su propia pareja ni su propio mejor amigo."
            MotivoRechazo.AnimalInexistente -> "El animal ya no existe."
            MotivoRechazo.AnimalNoVivo -> "El animal ya no está en el zoo."
            MotivoRechazo.AnimalEnfermo -> "Un animal enfermo no se puede vender."
            MotivoRechazo.SinAlimentacion -> "Un animal sin alimentación no se puede vender."
            is MotivoRechazo.ImporteVentaCambiado ->
                "El nivel del animal ha cambiado y ahora se vendería por ${dinero(m.vigente)}. Revisa el importe y confirma de nuevo."
            MotivoRechazo.DestinoIgualAOrigen -> "El animal ya está en ese espacio."
            MotivoRechazo.EspacioNoVacio -> "El espacio todavía tiene animales."
            MotivoRechazo.EspacioConDependencias -> "El espacio tiene operaciones pendientes y no se puede dar de baja."
            MotivoRechazo.AlimentacionCompleta -> "El animal ya tiene la alimentación al 100 %."
            MotivoRechazo.NadaQueAlimentar -> "No hay animales vivos por debajo del 100 % de alimentación en este espacio."
            MotivoRechazo.RacionNoConfigurada ->
                "No hay una cantidad de bambú configurada para alguna de las especies; no se puede alimentar."
            is MotivoRechazo.ConjuntoCambiado ->
                "Los animales del espacio han cambiado. El nuevo total es de ${m.requerido} kg de bambú; revísalo y confirma de nuevo."
            is MotivoRechazo.BambuInsuficiente ->
                "Bambú insuficiente: se necesitan ${m.requerido} kg, hay ${m.disponible} kg y faltan ${m.faltante} kg."
            MotivoRechazo.AnimalSano -> "El animal no está enfermo, no necesita tratamiento."
            MotivoRechazo.ConsumoMedicoNoConfigurado ->
                "No hay un consumo de insumos médicos configurado para este rango; no se puede tratar."
            is MotivoRechazo.InsumosInsuficientes ->
                "Insumos médicos insuficientes: " +
                    m.faltantes.joinToString("; ") { "${it.nombre} (necesitas ${it.necesario}, tienes ${it.disponible})" } + "."
            MotivoRechazo.SimulacionNoActiva -> "La simulación está en pausa."
            is MotivoRechazo.CicloFueraDeOrden -> "El ciclo solicitado no es el siguiente."
            MotivoRechazo.SinBeneficios -> "No hay beneficios pendientes de reclamar."
            is MotivoRechazo.BeneficiosCambiados ->
                "Los beneficios pendientes han cambiado: ahora son ${dinero(m.vigente)}. Revísalos y confirma de nuevo."
            MotivoRechazo.ConcepcionNoVigente -> "La cría ya no se puede registrar: la oferta caducó o ya se aceptó."
            MotivoRechazo.ProgenitoresNoValidos ->
                "Los progenitores ya no cumplen las condiciones: deben seguir vivos, ser pareja y estar en el mismo espacio."
            MotivoRechazo.AvisoInexistente -> "El aviso ya no existe."
            MotivoRechazo.MisionInexistente -> "Esa misión ya no está vigente: su recompensa ya se reclamó."
            MotivoRechazo.MisionNoCumplida -> "La misión aún no está cumplida."
            MotivoRechazo.RecompensaNoValida -> "No se puede determinar la recompensa de esta misión; no se ha acreditado nada."
            MotivoRechazo.ProgresionInvalida -> "No se puede calcular la siguiente misión; la recompensa sigue pendiente."
            MotivoRechazo.ConflictoIdempotencia -> "La solicitud ya se registró con otro contenido. Inténtalo de nuevo."
            MotivoRechazo.PartidaInexistente -> "No hay una partida disponible."
        }

    fun mision(m: MisionVista) = MisionUi(
        ref = Ref(m.idInstancia),
        descripcion = m.descripcion,
        progreso = m.progresoTexto,
        recompensa = recompensa(m.recompensa),
        cumplida = m.cumplida,
        reclamable = m.reclamable,
    )

    fun recompensa(r: RecompensaVista): String = when (r.tipo) {
        TipoRecompensa.FONDOS -> dinero(Dinero.desdeDecimal(r.cantidad))
        TipoRecompensa.ALIMENTO, TipoRecompensa.INSUMO -> "${r.cantidad.toLong()} × ${r.nombreItem ?: "recurso"}"
        TipoRecompensa.ESPECIE -> "Un animal exclusivo aleatorio, gratis"
    }

    fun recompensaReclamada(r: RecompensaReclamada): String {
        val premio = when (r.tipo) {
            TipoRecompensa.FONDOS -> "Has recibido ${dinero(Dinero.desdeDecimal(r.cantidad))}."
            TipoRecompensa.ALIMENTO, TipoRecompensa.INSUMO -> "Has recibido ${r.cantidad.toLong()} × ${r.nombreItem ?: "recurso"}."
            TipoRecompensa.ESPECIE -> "Te ha tocado ${r.nombreEspecie ?: "un animal"}: puedes comprarlo gratis en la tienda de animales."
        }
        return if (r.agotada) "$premio Esta misión ya no volverá a aparecer." else "$premio La misión se ha renovado con un nuevo objetivo."
    }

    fun ofertaAnimal(o: OfertaAnimal): OfertaAnimalUi {
        val e = o.especie
        if (o.gratuita) {
            return OfertaAnimalUi(
                ref = Ref(e.id), nombre = e.nombre, rango = e.rango, coste = "Gratis",
                estado = "Gratis · concedido por misión", detalle = "${e.rango} · Gratis · concedido por misión",
                seleccionable = true, bloqueada = false,
            )
        }
        val estado = when (e.estado) {
            EstadoTienda.COMERCIAL -> if (o.asequible) "Disponible" else "Saldo insuficiente"
            EstadoTienda.BLOQUEADA -> "Bloqueada"
            EstadoTienda.EXCLUSIVA -> "Exclusivo · solo por misión"
        }
        return OfertaAnimalUi(
            ref = Ref(e.id),
            nombre = e.nombre,
            rango = e.rango,
            coste = dinero(e.coste),
            estado = estado,
            detalle = if (e.estado == EstadoTienda.EXCLUSIVA) estado else "${e.rango} · $estado",
            seleccionable = o.asequible,
            bloqueada = e.estado != EstadoTienda.COMERCIAL,
        )
    }

    /**
     * Los nombres no son únicos (RF-32): si dos etiquetas de un mismo listado coinciden se añade el nivel y, si aún
     * coinciden, el número de orden de alta. Nunca se usa un identificador.
     */
    fun desambiguar(bases: List<String>, niveles: List<Int>): List<String> {
        val conNivel = bases.indices.map { i ->
            if (bases.count { it == bases[i] } > 1) "${bases[i]} · nivel ${niveles[i]}" else bases[i]
        }
        val orden = HashMap<String, Int>()
        return conNivel.map { l ->
            if (conNivel.count { it == l } > 1) {
                val k = (orden[l] ?: 0) + 1
                orden[l] = k
                "$l · n.º $k"
            } else {
                l
            }
        }
    }

    fun animalItems(l: List<AnimalResumen>): List<AnimalItemUi> {
        val bases = l.map { "${it.nombre} (${it.especie})" }
        val titulos = desambiguar(bases, l.map { it.nivel })
        return l.mapIndexed { i, a ->
            AnimalItemUi(
                ref = Ref(a.id),
                nombre = a.nombre + titulos[i].removePrefix(bases[i]),
                especie = a.especie,
                rango = a.rango,
                detalle = "Nivel ${a.nivel} · Alimentación ${a.alimentacion}% · ${if (a.enfermo) "Enfermo" else "Sano"} · Satisfacción ${porcentaje(a.satisfaccion)}",
                necesitaAlimento = a.vivo && a.alimentacion < ReglasCuidado.ALIMENTACION_MAXIMA,
            )
        }
    }

    fun candidatos(l: List<AnimalResumen>): List<CandidatoUi> {
        val etiquetas = desambiguar(l.map { "${it.nombre} (${it.especie})" }, l.map { it.nivel })
        return l.mapIndexed { i, a -> CandidatoUi(Ref(a.id), etiquetas[i]) }
    }

    /** «Nivel n de máx.» y el avance hacia el siguiente nivel (RF-51). Nunca se muestra una edad. */
    private fun lineasNivel(f: AnimalFicha, ciclosPorNivel: Int): List<LineaUi> {
        val tope = f.parametros.nivelMaximo
        val progreso = if (f.nivel >= tope) {
            "Nivel máximo alcanzado"
        } else {
            "${f.ciclosNivel} de $ciclosPorNivel ciclos sanos para el nivel ${f.nivel + 1}"
        }
        return listOf(LineaUi("Nivel", "${f.nivel} de $tope"), LineaUi("Progreso de nivel", progreso))
    }

    fun ficha(f: AnimalFicha, catalogo: CatalogoFicha, ciclosPorNivel: Int = ReglasNivel.CICLOS_POR_NIVEL): FichaAnimalUi {
        val importe = f.valorVenta()
        val equilibrio = ReglasNivel.nivelEquilibrio(f.parametros.nivelMaximo)
        return fichaUi(f, catalogo, ciclosPorNivel, importe, equilibrio)
    }

    private fun fichaUi(f: AnimalFicha, catalogo: CatalogoFicha, ciclosPorNivel: Int, importe: Dinero, equilibrio: Int) =
        FichaAnimalUi(
            ref = Ref(f.id),
            nombre = f.nombre,
            vivo = f.estadoVital == "VIVO",
            lineas = listOf(
                LineaUi("Especie", f.especie),
                LineaUi("Hábitat", f.nombreHabitat?.let(::nombreHabitat) ?: "Sin hábitat"),
                LineaUi("Espacio", f.nombreEspacio ?: "Sin espacio"),
                LineaUi("Rango", f.rango),
            ) + lineasNivel(f, ciclosPorNivel) + listOf(
                LineaUi("Valor de venta", dinero(importe)),
                LineaUi("Alimentación", "${f.alimentacion}%"),
                LineaUi("Salud", if (f.enfermo) "⚠ Enfermo" else "Sano"),
                LineaUi("Satisfacción", porcentaje(f.satisfaccion)),
                LineaUi("Pareja", f.nombrePareja ?: "Sin pareja"),
                LineaUi("Mejor amigo", f.nombreMejorAmigo ?: "Sin mejor amigo"),
            ),
            habilidades = f.habilidades.map(catalogo::nombreHabilidad),
            rasgos = f.rasgos.map(catalogo::nombreRasgo),
            idHabilidades = f.habilidades,
            idRasgos = f.rasgos,
            idPareja = f.idPareja,
            idMejorAmigo = f.idMejorAmigo,
            nombrePareja = f.nombrePareja,
            nombreMejorAmigo = f.nombreMejorAmigo,
            alimentacion = f.alimentacion,
            enfermo = f.enfermo,
            rango = f.rango,
            importeVenta = importe,
            textoVenta = "Vas a vender a ${f.nombre} (nivel ${f.nivel}) por ${dinero(importe)}. " +
                "En el nivel $equilibrio se recupera el coste de compra; por debajo se pierde dinero y por encima se gana. " +
                "La venta no se puede deshacer.",
        )

    fun destino(d: EspacioConHabitat) =
        DestinoUi(Ref(d.espacio.id), "${nombreHabitat(d.nombreHabitat)} · ${d.espacio.nombre} (${d.espacio.ocupacion}/${d.espacio.capacidadMax})")

    fun beneficios(v: ConsultarBeneficios.Vista) =
        BeneficiosUi(
            pendiente = dinero(v.pendiente),
            importe = v.pendiente,
            saldo = dinero(v.saldo),
            porCiclo = v.porCiclo.map { LineaUi("Ciclo ${it.numeroCiclo}", "${dinero(it.importe)} · ${if (it.reclamado) "Reclamado" else "Pendiente"}") },
            puedeReclamar = v.pendiente > Dinero.CERO,
        )

    /** Fecha UTC legible a partir de segundos Unix (calendario gregoriano, sin dependencias de plataforma). */
    fun fecha(segundos: Long): String {
        val dias = segundos.floorDiv(86_400L)
        val resto = segundos - dias * 86_400L
        val z = dias + 719_468
        val era = z.floorDiv(146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val dia = doy - (153 * mp + 2) / 5 + 1
        val mes = if (mp < 10) mp + 3 else mp - 9
        val anio = yoe + era * 400 + if (mes <= 2) 1 else 0
        fun dos(n: Long) = n.toString().padStart(2, '0')
        return "${dos(dia)}/${dos(mes)}/$anio ${dos(resto / 3_600)}:${dos((resto % 3_600) / 60)} UTC"
    }

    fun aviso(a: Aviso): AvisoItemUi {
        val (etiqueta, tono) = when (a.tipo.name) {
            "CRITICA" -> "Crítica · Alta" to TipoAviso.ERROR
            "OPERATIVA" -> "Operativa · Media" to TipoAviso.ADVERTENCIA
            else -> "Informativa · Baja" to TipoAviso.INFO
        }
        val estado = when (a.estado) {
            "PENDIENTE" -> "Pendiente"
            "VISTA" -> "Vista"
            "ACEPTADA" -> "Aceptada"
            "RESUELTA" -> "Resuelta"
            "DESCARTADA" -> "Descartada"
            else -> "Estado no disponible"
        }
        return AvisoItemUi(
            id = a.id,
            etiqueta = etiqueta,
            texto = a.texto,
            estado = estado,
            fecha = fecha(a.fecha),
            tono = tono,
            critico = a.tipo.name == "CRITICA",
            pendiente = a.estado == "PENDIENTE",
            reproduccion = a.codigo == "REPRODUCCION_DISPONIBLE" && (a.estado == "PENDIENTE" || a.estado == "VISTA"),
        )
    }

    fun criticos(l: List<RegistroCritico>): List<CriticoUi> {
        val etiquetas = desambiguar(l.map { "${it.animal.nombre} (${it.animal.especie})" }, l.map { it.animal.nivel })
        return l.mapIndexed { i, r ->
            val causas = buildList {
                if (r.animal.sinAlimento) add("Desnutrido")
                if (r.animal.enfermo) add("Enfermo")
            }
            fun ciclos(n: Int) = if (n == 1) "1 ciclo" else "$n ciclos"
            val contadores = buildList {
                if (r.animal.sinAlimento) add("Desnutrición: ${ciclos(r.ciclosDesnutricion)}")
                if (r.animal.enfermo) add("Enfermedad: ${ciclos(r.ciclosEnfermedad)}")
            }
            CriticoUi(
                ref = Ref(r.animal.id),
                titulo = etiquetas[i],
                causa = causas.joinToString(" y "),
                detalle = contadores.joinToString(" · ") + " · Quedan ${ciclos(r.ciclosRestantes)}",
                urgente = r.ciclosRestantes <= 1,
            )
        }
    }

    const val ERROR_TECNICO = "No se pudo completar la operación. No se ha realizado ningún cambio."
}
