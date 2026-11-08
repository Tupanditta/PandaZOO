package com.pandazoo.ui.pantalla

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Una cifra de la ventana: valor grande y etiqueta pequeña; `alerta` la tiñe de error (p. ej. bambú insuficiente). */
data class DatoVentana(val valor: String, val etiqueta: String, val icono: ImageVector? = null, val alerta: Boolean = false)

/**
 * Contenido de una ventana de confirmación. Los botones no llevan texto visible: `confirmar` y `cancelar` son su texto
 * semántico (lectores de pantalla y pruebas). `peligro` tiñe la ventana de error (acciones irreversibles).
 */
data class VentanaUi(
    val icono: ImageVector,
    val titulo: String,
    val datos: List<DatoVentana> = emptyList(),
    val nota: String? = null,
    val confirmar: String,
    val cancelar: String = "Cancelar",
    val peligro: Boolean = false,
)

@Composable
private fun FichaDato(d: DatoVentana, modifier: Modifier) {
    val e = MaterialTheme.colorScheme
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(if (d.alerta) e.errorContainer else e.surfaceVariant.copy(alpha = 0.6f)).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (d.icono != null) Icon(d.icono, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (d.alerta) e.error else e.primary)
        Text(
            d.valor,
            style = MaterialTheme.typography.titleMedium,
            color = if (d.alerta) e.onErrorContainer else e.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(d.etiqueta, style = MaterialTheme.typography.labelMedium, color = if (d.alerta) e.onErrorContainer else e.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/**
 * Ventana flotante de confirmación: insignia con el icono de la acción sobre una cabecera de color, cifras clave en
 * fichas y dos botones de icono: cancelar (círculo) y confirmar (píldora ancha).
 */
@Composable
internal fun VentanaFlotante(v: VentanaUi, alConfirmar: () -> Unit, alCancelar: () -> Unit) {
    val e = MaterialTheme.colorScheme
    val acento = if (v.peligro) e.error else e.primary
    val cabecera = if (v.peligro) e.errorContainer else e.primaryContainer
    Dialog(onDismissRequest = alCancelar) {
        Surface(shape = RoundedCornerShape(24.dp), color = e.surface, border = BorderStroke(1.dp, e.outlineVariant), tonalElevation = 6.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().background(cabecera).padding(top = 28.dp, bottom = 20.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(e.surface).border(3.dp, acento, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(v.icono, contentDescription = null, modifier = Modifier.size(36.dp), tint = acento)
                    }
                }
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(v.titulo, style = MaterialTheme.typography.titleLarge, color = e.onSurface, textAlign = TextAlign.Center)
                    if (v.datos.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            v.datos.forEach { FichaDato(it, Modifier.weight(1f)) }
                        }
                    }
                    v.nota?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = e.onSurfaceVariant, textAlign = TextAlign.Center) }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).border(1.5.dp, e.outline, CircleShape)
                                .clickable(role = Role.Button, onClickLabel = v.cancelar, onClick = alCancelar)
                                .semantics(mergeDescendants = true) { text = AnnotatedString(v.cancelar) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Iconos.Cerrar, contentDescription = null, tint = e.onSurfaceVariant) }
                        Box(
                            Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(acento)
                                .clickable(role = Role.Button, onClickLabel = v.confirmar, onClick = alConfirmar)
                                .semantics(mergeDescendants = true) { text = AnnotatedString(v.confirmar) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Iconos.Check, contentDescription = null, modifier = Modifier.size(28.dp), tint = if (v.peligro) e.onError else e.onPrimary) }
                    }
                }
            }
        }
    }
}

/**
 * Ventana pequeña con lo conseguido; se cierra pulsando fuera de ella. Si el premio es un sorteo, primero muestra las
 * probabilidades con el botón «¡SUERTE!» (no se puede cerrar sin pulsarlo) y después el animal sobre el color de su rango.
 */
@Composable
internal fun VentanaPremio(p: PremioUi, alCerrar: () -> Unit) {
    val e = MaterialTheme.colorScheme
    var revelado by remember(p) { mutableStateOf(p.probabilidades.isEmpty()) }
    val diamante = revelado && p.diamante
    val tono = when {
        !revelado -> TonoRango(e.primary, e.primaryContainer)
        diamante -> tonoRango("Exclusivo", diamante = true)
        p.exclusivo -> tonoRango("Exclusivo")
        p.rango != null -> tonoRango(p.rango)
        else -> TonoRango(e.primary, e.primaryContainer)
    }
    val brillo = revelado && p.exclusivo
    Dialog(onDismissRequest = { if (revelado) alCerrar() }) {
        val forma = RoundedCornerShape(22.dp)
        // Un animal exclusivo luce como en la tienda y las listas: contorno brillante, resplandor y reflejos.
        Surface(
            modifier = if (brillo) Modifier.shadow(9.dp, forma, ambientColor = tono.filete, spotColor = tono.filete) else Modifier,
            shape = forma,
            color = tono.contenedor,
            border = if (diamante) {
                BorderStroke(2.5.dp, BrilloDiamante)
            } else if (brillo) {
                BorderStroke(2.5.dp, BrilloExclusivo)
            } else {
                BorderStroke(2.dp, tono.filete)
            },
        ) {
            Column(
                Modifier.fillMaxWidth().reflejos(brillo && !diamante).destellosDiamante(diamante).padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(e.surface).border(3.dp, tono.filete, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(p.icono, contentDescription = null, modifier = Modifier.size(32.dp), tint = tono.filete)
                }
                Text(p.titulo, style = MaterialTheme.typography.labelMedium, color = e.onSurfaceVariant)
                if (!revelado) {
                    Text("Animal aleatorio", style = MaterialTheme.typography.headlineSmall, color = e.onSurface)
                    p.probabilidades.forEach { (rango, porcentaje) ->
                        val t = tonoRango(rango)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(rango, style = MaterialTheme.typography.bodyLarge, color = t.filete)
                            Text("$porcentaje %", style = MaterialTheme.typography.bodyLarge, color = e.onSurface)
                        }
                    }
                    BotonPrincipal("¡SUERTE!", { revelado = true })
                } else {
                    NombreRango(p.valor, if (p.exclusivo) "Exclusivo" else null, MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center), diamante = diamante)
                    if (p.exclusivo) {
                        Text(if (diamante) "Animal exclusivo · diamante" else "Animal exclusivo", style = MaterialTheme.typography.labelMedium, color = tono.filete)
                    } else if (p.rango != null) {
                        Text("Animal ${p.rango.lowercase()}", style = MaterialTheme.typography.labelMedium, color = tono.filete)
                    }
                }
            }
        }
    }
}

/**
 * Ventana de confirmación del divorcio: explica las consecuencias y solo con «Acepto» se realiza la operación. Cerrarla
 * (botón «Cancelar» o tocar fuera) no hace nada.
 */
@Composable
internal fun VentanaDivorcio(nombre: String, nombrePareja: String, alAceptar: () -> Unit, alCancelar: () -> Unit) {
    val e = MaterialTheme.colorScheme
    Dialog(onDismissRequest = alCancelar) {
        Surface(shape = RoundedCornerShape(24.dp), color = e.surface, border = BorderStroke(1.dp, e.error), tonalElevation = 6.dp) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Divorcio", style = MaterialTheme.typography.titleLarge, color = e.error)
                Text(
                    "$nombre y $nombrePareja dejarán de ser pareja.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = e.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Si tienen hijos en común, dejarán de ser sus hijos: quedarán como individuos sin padres ni hermanos. " +
                        "No se borra ningún animal. Después, cada uno podrá elegir otra pareja o ser mejor amigo del otro. " +
                        "No se puede deshacer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = e.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                BotonPrincipal("Acepto", alAceptar)
                BotonPrincipal("Cancelar", alCancelar)
            }
        }
    }
}

/** Ventana de ánimo tras un divorcio: se cierra tocando fuera de ella. */
@Composable
internal fun VentanaAnimo(alCerrar: () -> Unit) {
    val e = MaterialTheme.colorScheme
    Dialog(onDismissRequest = alCerrar) {
        Surface(shape = RoundedCornerShape(22.dp), color = e.primaryContainer, border = BorderStroke(2.dp, e.primary)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 30.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Ánimo My Sheila ❤",
                    style = MaterialTheme.typography.headlineSmall,
                    color = e.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
