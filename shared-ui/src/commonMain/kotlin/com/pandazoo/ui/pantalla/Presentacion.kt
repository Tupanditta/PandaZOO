package com.pandazoo.ui.pantalla

import androidx.compose.runtime.compositionLocalOf
import kotlin.jvm.JvmInline
import com.pandazoo.ui.AppContainer
import com.pandazoo.application.error.MotivoRechazo
import com.pandazoo.domain.modelo.Espacio
import com.pandazoo.domain.modelo.Existencia
import com.pandazoo.domain.modelo.Habitat
import com.pandazoo.domain.modelo.InventarioFinanzas
import com.pandazoo.domain.modelo.OfertaRecurso
import com.pandazoo.domain.modelo.Seccion
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
    val avisos: List<String>,
)

data class HabitatUi(val ref: Ref, val nombre: String, val estado: String, val disponible: Boolean)

data class EspacioUi(val ref: Ref, val nombre: String, val detalle: String, val activo: Boolean)

data class OfertaUi(val ref: Ref, val etiqueta: String, val unidad: String, val precio: Dinero?)

data class CapacidadUi(val capacidad: Int, val coste: Dinero, val etiqueta: String)

object Textos {
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
        return InicioUi(
            saldo = valor(v.fondos)?.let(::dinero),
            beneficiosPendientes = valor(v.beneficiosPendientes)?.let(::dinero),
            existencias = valor(v.existencias)?.map { LineaUi(it.nombre, "${it.cantidad} ${it.unidad}") },
            avisos = avisos,
        )
    }

    fun habitat(h: Habitat) =
        HabitatUi(
            ref = Ref(h.id),
            nombre = h.nombre,
            estado = if (h.utilizable) "Disponible" else "Bloqueado",
            disponible = h.utilizable,
        )

    fun espacio(e: Espacio) =
        EspacioUi(
            ref = Ref(e.id),
            nombre = e.nombre,
            detalle = "Capacidad ${e.capacidadMax} · Ocupación ${e.ocupacion}/${e.capacidadMax}" +
                if (e.estado.name == "INACTIVO") " · De baja" else "",
            activo = e.estado.name == "ACTIVO",
        )

    fun oferta(o: OfertaRecurso) =
        OfertaUi(
            ref = Ref(o.recurso.codigo),
            etiqueta = o.nombre + (o.precio?.let { " — ${dinero(it)} por ${if (o.unidad == "kg") "kg" else "unidad"}" } ?: " — sin precio"),
            unidad = o.unidad,
            precio = o.precio,
        )

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
            MotivoRechazo.ConflictoIdempotencia -> "La solicitud ya se registró con otro contenido. Inténtalo de nuevo."
            MotivoRechazo.PartidaInexistente -> "No hay una partida disponible."
        }

    const val ERROR_TECNICO = "No se pudo completar la operación. No se ha realizado ningún cambio."
}
