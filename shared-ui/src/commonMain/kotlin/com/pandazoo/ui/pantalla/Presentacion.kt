package com.pandazoo.ui.pantalla

import androidx.compose.runtime.compositionLocalOf
import kotlin.jvm.JvmInline
import com.pandazoo.ui.AppContainer
import com.pandazoo.application.caso.AnimalMonitor
import com.pandazoo.application.caso.EstadisticasAnimal
import com.pandazoo.application.caso.HabitatBeneficio
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
import com.pandazoo.data.inicial.RegaloBienvenida
import com.pandazoo.application.puerto.TipoRecompensa
import com.pandazoo.application.puerto.SeveridadAviso
import androidx.compose.ui.graphics.vector.ImageVector
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

/** Estadísticas de un animal ya calculadas y listas para mostrar: sin fórmulas, solo datos. */
data class EstadisticasUi(val satisfaccion: List<LineaUi>)

/** `null` en una sección significa que el dato no está disponible; se informa, no se presenta como cero. */
data class InicioUi(
    val saldo: String?,
    val beneficiosPendientes: String?,
    val existencias: List<LineaUi>?,
    val bambu: String?,
    val insumosMedicos: String?,
    val avisos: List<String>,
)

/** Datos de la cabecera global: satisfacción del parque, saldo, bambú e medicinas. */
data class CabeceraUi(val satisfaccion: String, val saldo: String, val bambu: String, val insumos: String, val prestigio: String)

/** Panel de prestigio (CU-19). Un dato no disponible se muestra como tal, nunca como cero. */
data class PrestigioUi(
    val nivel: String,
    val maximoNivel: String,
    val maximoAnimales: String,
    val maximoSatisfaccion: String,
    val animales: String,
    val satisfaccion: String,
    /** Nivel siguiente y lo que exige; `null` si ya se alcanzó el nivel máximo. */
    val siguiente: SiguienteNivelUi?,
    val avisos: List<String>,
)

/** Bloque del nivel siguiente: requisitos y lo que desbloquea (nombres de especies y hábitats). */
data class SiguienteNivelUi(
    val nivel: String,
    val animales: String,
    val satisfaccion: String,
    val especies: List<String>,
    val habitats: List<String>,
)

/**
 * Aviso abierto (CU-21). El color de `tono` ya comunica la severidad: no se escribe. `ids` tiene más de uno cuando se agrupan
 * varias alertas de salud en una sola fila.
 */
data class AvisoItemUi(
    val id: Long,
    val texto: String,
    val tono: TipoAviso,
    val critico: Boolean,
    /** Los de severidad baja y media se descartan deslizando; los de severidad alta se cierran al resolverse su causa. */
    val descartable: Boolean,
    /** Aviso de reproducción aún aceptable desde la lista: abre el formulario de la cría. */
    val reproduccion: Boolean = false,
    val ids: List<Long> = listOf(id),
)

/** Fila del seguimiento crítico (CU-18). */
data class CriticoUi(val ref: Ref, val titulo: String, val causa: String, val detalle: String, val urgente: Boolean)

data class BeneficiosUi(val pendiente: String, val importe: Dinero, val saldo: String, val puedeReclamar: Boolean)

/** Nodo del desglose de beneficios (hábitat, espacio o animal); `clave` solo identifica la fila desplegada. */
data class NodoBeneficioUi(val clave: String, val titulo: String, val valor: String, val hijos: List<NodoBeneficioUi> = emptyList())

data class HabitatUi(val ref: Ref, val nombre: String, val disponible: Boolean)

data class EspacioUi(val ref: Ref, val nombre: String, val detalle: String, val activo: Boolean, val ocupacion: Int = 0, val conCupo: Boolean = false, val capacidad: Int = 0)

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
    /** Avance hacia el objetivo, entre 0 y 1. */
    val fraccion: Float = 0f,
    /** La recompensa es un animal exclusivo: el bloque se resalta en dorado. */
    val exclusiva: Boolean = false,
)

/** Lo que se ha conseguido al reclamar una recompensa (ventana flotante pequeña). */
data class PremioUi(
    val icono: ImageVector,
    val valor: String,
    val exclusivo: Boolean = false,
    /** Rango del animal (Común, Poco común, Raro, Exclusivo); `null` si el premio no es un animal. */
    val rango: String? = null,
    val titulo: String = "Has conseguido",
    /** Especie con toques de diamante (el panda): sigue siendo exclusiva. */
    val diamante: Boolean = false,
    /**
     * Probabilidad de cada rango del sorteo (nombre del rango y porcentaje). Si no está vacía, la ventana muestra primero las
     * probabilidades con el botón «¡SUERTE!» y solo después el animal.
     */
    val probabilidades: List<Pair<String, Int>> = emptyList(),
)

/** Especie en la tienda de animales. `seleccionable` es falso si está bloqueada, es exclusiva o el saldo no alcanza. */
data class OfertaAnimalUi(
    val ref: Ref,
    val nombre: String,
    val rango: String,
    val coste: String,
    val estado: String,
    /** Línea bajo el nombre; vacía si la especie está disponible (el precio y el color ya lo dicen). */
    val detalle: String,
    val seleccionable: Boolean,
    /** Solo se obtiene con misiones: no muestra precio. */
    val exclusiva: Boolean,
    val diamante: Boolean = false,
    /** Animales vivos de la especie alojados ahora en el zoo. */
    val alojados: Int = 0,
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
    val diamante: Boolean = false,
)

data class CandidatoUi(val ref: Ref, val etiqueta: String)

data class DestinoUi(val ref: Ref, val habitat: String, val espacio: String, val ocupacion: Int, val capacidad: Int)

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
    val nivel: Int,
    /** Importe que se acreditaría al vender ahora; se envía al confirmar para detectar un cambio de nivel (CU-06, 4.a). */
    val importeVenta: Dinero,
    val ventanaVenta: VentanaUi,
    val diamante: Boolean = false,
)

object Textos {
    /** La especie que luce toques de diamante además de ser exclusiva. */
    const val ESPECIE_DIAMANTE = "panda"

    fun esDiamante(idEspecie: String?) = idEspecie == ESPECIE_DIAMANTE

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
            conCupo = e.ocupacion < e.capacidadMax,
            capacidad = e.capacidadMax,
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
            is Seccion.Disponible -> "Prestigio ${a.valor}"
            is Seccion.NoDisponible -> "Prestigio N/D"
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
            maximoNivel = if (!maximosDisponibles) NO_DISPONIBLE else maximos?.let { "Prestigio ${it.nivel}" } ?: sinMaximos,
            maximoAnimales = if (!maximosDisponibles) NO_DISPONIBLE else maximos?.animalesVivos?.toString() ?: sinMaximos,
            maximoSatisfaccion = if (!maximosDisponibles) NO_DISPONIBLE else maximos?.let { porcentaje(it.satisfaccionGlobal) } ?: sinMaximos,
            animales = dato(p.animalesVivos) { it.toString() },
            satisfaccion = dato(p.satisfaccionGlobal, ::porcentaje),
            siguiente = p.siguiente?.let {
                SiguienteNivelUi(
                    nivel = "Prestigio ${it.nivel}",
                    animales = it.animalesVivos.toString(),
                    satisfaccion = porcentaje(it.satisfaccionGlobal),
                    especies = p.desbloqueoSiguiente?.especies.orEmpty(),
                    habitats = p.desbloqueoSiguiente?.habitats.orEmpty(),
                )
            },
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
            MotivoRechazo.CapacidadNoAmpliable -> "Un espacio solo puede ampliarse a una capacidad mayor."
            MotivoRechazo.DenominacionInvalida -> "La denominación no puede estar vacía ni superar 40 caracteres."
            MotivoRechazo.DenominacionDuplicada -> "Ya existe un espacio con esa denominación en el hábitat."
            MotivoRechazo.HabitatNoDisponible -> "El hábitat no está disponible o está bloqueado."
            MotivoRechazo.EspacioInexistente -> "El espacio ya no existe."
            MotivoRechazo.EspacioInactivo -> "El espacio está dado de baja y no se puede configurar."
            MotivoRechazo.SinCambios -> "No hay cambios que aplicar."
            MotivoRechazo.EspecieInexistente -> "La especie ya no existe en el catálogo."
            MotivoRechazo.EspecieBloqueada -> "Esa especie aún está bloqueada: sube tu prestigio para comprarla."
            MotivoRechazo.EspecieExclusiva -> "Las especies exclusivas solo se obtienen con misiones."
            MotivoRechazo.EspacioLleno -> "El espacio no tiene capacidad libre."
            MotivoRechazo.NombreInvalido -> "El nombre no puede estar vacío ni superar 40 caracteres."
            MotivoRechazo.FichaIncompleta -> "La ficha necesita exactamente dos habilidades sociales y dos rasgos de personalidad."
            MotivoRechazo.ParejaInexistente -> "La pareja elegida ya no está en el zoo."
            MotivoRechazo.ParejaOtraEspecie -> "La pareja debe ser de la misma especie."
            MotivoRechazo.ParejaOtroEspacio -> "La pareja debe estar en el mismo espacio."
            MotivoRechazo.ParejaNivelInsuficiente ->
                "Para ser pareja, los dos animales deben ser de la misma especie y estar en el mismo espacio."
            MotivoRechazo.ParejaOcupada -> "Alguno de los dos ya tiene otra pareja. Quítala antes de elegir una nueva."
            MotivoRechazo.AmigoInvalido -> "El mejor amigo debe ser otro animal del mismo espacio."
            MotivoRechazo.DivorcioRequerido -> "Para quitar o cambiar la pareja hay que divorciarse desde la ficha del animal."
            MotivoRechazo.SinPareja -> "El animal no tiene pareja."
            MotivoRechazo.AmigoFamiliar -> "Un familiar (pareja, padres, hijos o hermanos) no puede ser mejor amigo."
            MotivoRechazo.AmigoOcupado -> "Ambos animales deben estar libres: quita antes el mejor amigo actual."
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
            MotivoRechazo.RegaloYaConcedido -> "Ese regalo ya se recibió."
            MotivoRechazo.RegaloNoDisponible -> "No hay ningún animal disponible para este regalo."
            MotivoRechazo.AnimalSano -> "El animal no está enfermo, no necesita tratamiento."
            MotivoRechazo.ConsumoMedicoNoConfigurado ->
                "No hay un consumo de medicinas configurado para este rango; no se puede tratar."
            is MotivoRechazo.InsumosInsuficientes ->
                "Medicinas insuficientes: " +
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
            MotivoRechazo.AvisoNoDescartable -> "Los avisos de severidad alta se cierran al resolver su causa."
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
        fraccion = m.fraccion.toFloat(),
        // Dorado solo cuando el animal es seguro exclusivo (prestigio múltiplo de 10).
        exclusiva = m.recompensa.tipo == TipoRecompensa.ESPECIE &&
            (m.recompensa.probabilidades.isEmpty() || m.recompensa.probabilidades.all { it.idRango == 5 }),
    )

    fun recompensa(r: RecompensaVista): String = when (r.tipo) {
        TipoRecompensa.FONDOS -> dinero(Dinero.desdeDecimal(r.cantidad))
        TipoRecompensa.ALIMENTO, TipoRecompensa.INSUMO -> "${r.cantidad.toLong()} × ${r.nombreItem ?: "recurso"}"
        TipoRecompensa.ESPECIE ->
            // Las probabilidades solo se muestran en la ventana de suerte; aquí solo se dice si el exclusivo es seguro.
            if (r.probabilidades.isNotEmpty() && r.probabilidades.all { it.idRango == 5 }) {
                "Un animal exclusivo, gratis"
            } else {
                "Un animal aleatorio, gratis"
            }
    }

    /** Una ventana por recurso del regalo de bienvenida: dinero, medicinas y cada animal. */
    fun bienvenida(r: RegaloBienvenida): List<PremioUi> {
        val titulo = "Regalo de bienvenida"
        val medicinas = if (r.medicinas == 1) "1 medicina" else "${r.medicinas} medicinas"
        return listOf(
            PremioUi(Iconos.Saldo, dinero(Dinero.desdeDecimal(r.fondos)), titulo = titulo),
            PremioUi(Iconos.Bambu, "${r.bambu} kg de bambú", titulo = titulo),
            PremioUi(Iconos.InsumosMedicos, medicinas, titulo = titulo),
        ) + r.animales.map {
            val rango = nombreRango(it.idRango)
            PremioUi(
                Iconos.Pata, it.nombre, exclusivo = rango == "Exclusivo", rango = rango, titulo = titulo,
                probabilidades = sorteoVisible(it.probabilidades), diamante = esDiamante(it.idEspecie),
            )
        }
    }

    /** Ventana del regalo semanal: el animal sorteado, con la tabla de probabilidades de su rango. */
    fun regaloSemanal(r: com.pandazoo.application.caso.RegaloSemanalConcedido): PremioUi {
        val rango = nombreRango(r.idRango)
        return PremioUi(
            Iconos.Pata, r.nombreEspecie, exclusivo = rango == "Exclusivo", rango = rango, titulo = r.nombreRegalo,
            probabilidades = sorteoVisible(r.probabilidades), diamante = esDiamante(r.idEspecie),
        )
    }

    /** Solo hay ventana de suerte si el rango depende de probabilidades; con un único rango posible el animal se entrega directo. */
    private fun sorteoVisible(t: List<com.pandazoo.domain.config.ProbabilidadRango>): List<Pair<String, Int>> =
        if (t.size > 1) t.map { nombreRango(it.idRango) to it.porcentaje } else emptyList()

    fun nombreRango(idRango: Int): String = when (idRango) {
        1 -> "Común"
        2 -> "Poco común"
        3 -> "Raro"
        4 -> "Épico"
        else -> "Exclusivo"
    }

    fun premio(r: RecompensaReclamada): PremioUi = when (r.tipo) {
        TipoRecompensa.FONDOS -> PremioUi(Iconos.Saldo, dinero(Dinero.desdeDecimal(r.cantidad)))
        TipoRecompensa.ALIMENTO -> PremioUi(Iconos.Bambu, "${r.cantidad.toLong()} × ${r.nombreItem ?: "recurso"}")
        TipoRecompensa.INSUMO -> PremioUi(Iconos.InsumosMedicos, "${r.cantidad.toLong()} × ${r.nombreItem ?: "recurso"}")
        TipoRecompensa.ESPECIE -> {
            val rango = r.idRango?.let(::nombreRango)
            PremioUi(
                Iconos.Pata, r.nombreEspecie ?: "Animal aleatorio", exclusivo = rango == "Exclusivo", rango = rango,
                probabilidades = sorteoVisible(r.probabilidades), diamante = esDiamante(r.idEspecie),
            )
        }
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
                estado = "Gratis · concedido por misión", detalle = "Gratis · concedido por misión",
                seleccionable = true, exclusiva = false, diamante = esDiamante(e.id), alojados = o.alojados,
            )
        }
        val estado = when (e.estado) {
            EstadoTienda.COMERCIAL -> if (o.asequible) "Disponible" else "Saldo insuficiente"
            EstadoTienda.BLOQUEADA -> "Bloqueada"
            EstadoTienda.EXCLUSIVA -> "Exclusivo · solo por misión"
        }
        // El rango se comunica con el color y la disponibilidad con la opacidad: solo se escribe lo que no se ve.
        val detalle = when (e.estado) {
            EstadoTienda.COMERCIAL -> if (o.asequible) "" else estado
            EstadoTienda.BLOQUEADA -> e.nivelDesbloqueo?.let { "Prestigio $it" } ?: estado
            EstadoTienda.EXCLUSIVA -> estado
        }
        return OfertaAnimalUi(
            ref = Ref(e.id),
            nombre = e.nombre,
            rango = e.rango,
            coste = dinero(e.coste),
            estado = estado,
            detalle = detalle,
            seleccionable = o.asequible,
            exclusiva = e.estado == EstadoTienda.EXCLUSIVA,
            diamante = esDiamante(e.id),
            alojados = o.alojados,
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
                // [simplificar-3] Solo se muestran las excepciones (enfermo, hambre) junto al nivel y la satisfacción.
                detalle = listOfNotNull(
                    "Nivel ${a.nivel}",
                    if (a.enfermo) "Enfermo" else null,
                    if (a.alimentacion < ReglasCuidado.ALIMENTACION_MAXIMA) "Alimentación ${a.alimentacion}%" else null,
                    "Satisfacción ${porcentaje(a.satisfaccion)}",
                ).joinToString(" · "),
                necesitaAlimento = a.vivo && a.alimentacion < ReglasCuidado.ALIMENTACION_MAXIMA,
                diamante = esDiamante(a.idEspecie),
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
        return listOf(LineaUi("Progreso de nivel", progreso))
    }

    fun estadisticas(e: EstadisticasAnimal): EstadisticasUi {
        val s = e.satisfaccion
        fun pct(v: Double) = porcentaje(v)
        // Solo las afinidades: con las especies de su espacio y con su hábitat.
        return EstadisticasUi(
            satisfaccion = s.social.aportaciones.filter { it.nombre == "Afinidad con el espacio" }
                .map { LineaUi("Afinidad con las especies del espacio", pct(it.grado)) } +
                LineaUi("Afinidad con el hábitat", pct(s.desglose.pHab)),
        )
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
                // [simplificar-1] Hábitat y espacio en una sola línea.
                LineaUi(
                    "Ubicación",
                    if (f.nombreEspacio == null) "Sin espacio"
                    else listOfNotNull(f.nombreHabitat?.let(::nombreHabitat), f.nombreEspacio).joinToString(" · "),
                ),
            ) + lineasNivel(f, ciclosPorNivel) + listOfNotNull(
                LineaUi("Valor de venta", dinero(importe)),
                LineaUi("Alimentación", "${f.alimentacion}%"),
                // [simplificar-2] Solo se muestra lo que no es el valor por defecto.
                if (f.enfermo) LineaUi("Salud", "⚠ Enfermo") else null,
                LineaUi("Satisfacción", porcentaje(f.satisfaccion)),
                f.nombrePareja?.let { LineaUi("Pareja", it) },
                f.nombreMejorAmigo?.let { LineaUi("Mejor amigo", it) },
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
            nivel = f.nivel,
            importeVenta = importe,
            diamante = esDiamante(f.idEspecie),
            ventanaVenta = VentanaUi(
                icono = Iconos.Saldo,
                titulo = "Vender a ${f.nombre}",
                datos = listOf(
                    DatoVentana("${f.nivel}", "nivel", Iconos.Pata),
                    DatoVentana(dinero(importe), "recibes", alerta = f.nivel < equilibrio),
                    DatoVentana("$equilibrio", "nivel de equilibrio"),
                ),
                confirmar = "Confirmar venta",
                peligro = true,
            ),
        )

    fun destino(d: EspacioConHabitat) =
        DestinoUi(Ref(d.espacio.id), nombreHabitat(d.nombreHabitat), d.espacio.nombre, d.espacio.ocupacion, d.espacio.capacidadMax)

    fun beneficios(v: ConsultarBeneficios.Vista) =
        BeneficiosUi(
            pendiente = dinero(v.pendiente),
            importe = v.pendiente,
            saldo = dinero(v.saldo),
            puedeReclamar = v.pendiente > Dinero.CERO,
        )

    fun desgloseBeneficios(l: List<HabitatBeneficio>): List<NodoBeneficioUi> =
        l.map { h ->
            NodoBeneficioUi(
                clave = h.nombre,
                titulo = nombreHabitat(h.nombre),
                valor = dinero(h.importe),
                hijos = h.espacios.map { e ->
                    val etiquetas = desambiguar(e.animales.map { "${it.nombre} (${it.especie})" }, e.animales.map { it.nivel })
                    NodoBeneficioUi(
                        clave = "${h.nombre}|${e.nombre}",
                        titulo = e.nombre,
                        valor = dinero(e.importe),
                        hijos = e.animales.mapIndexed { i, a -> NodoBeneficioUi("${h.nombre}|${e.nombre}|$i", etiquetas[i], dinero(a.importe)) },
                    )
                },
            )
        }

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

    fun aviso(a: Aviso): AvisoItemUi =
        AvisoItemUi(
            id = a.id,
            texto = a.texto,
            tono = when (a.tipo.name) {
                "CRITICA" -> TipoAviso.ERROR
                "OPERATIVA" -> TipoAviso.ADVERTENCIA
                else -> TipoAviso.INFO
            },
            critico = a.tipo.name == "CRITICA",
            descartable = a.severidad != SeveridadAviso.ALTA,
            reproduccion = a.codigo == "REPRODUCCION_DISPONIBLE" && !a.cerrado,
        )

    /** Varias alertas de salud abiertas se muestran como una sola fila que lleva al seguimiento crítico. */
    fun avisos(abiertos: List<Aviso>): List<AvisoItemUi> {
        val salud = abiertos.filter { it.codigo == "ALERTA_SALUD" }
        if (salud.size < 2) return abiertos.map(::aviso)
        var agrupado = false
        return abiertos.mapNotNull { a ->
            when {
                a.codigo != "ALERTA_SALUD" -> aviso(a)
                agrupado -> null
                else -> {
                    agrupado = true
                    aviso(a).copy(texto = "${salud.size} animales en estado crítico.", ids = salud.map { it.id })
                }
            }
        }
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
