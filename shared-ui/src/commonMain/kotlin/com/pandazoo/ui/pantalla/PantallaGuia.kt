package com.pandazoo.ui.pantalla

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandazoo.application.caso.ConsultarGuiaConvivencia
import com.pandazoo.application.caso.EspecieGuia
import com.pandazoo.application.caso.HabitatGuia
import com.pandazoo.application.caso.NivelEncaje

private const val APARTADO_CONVIVENCIA = "convivencia"
private const val PESTANA_HABITATS = "habitats"
private const val PESTANA_ANIMALES = "animales"

/** Texto corto de un hábitat para la cabecera de la tabla: primera y última palabra significativas. */
internal fun nombreCortoHabitat(nombre: String): String {
    val palabras = nombre.split(" ").filter { it.lowercase() !in setOf("de", "del", "la", "el") }
    return if (palabras.size <= 2) palabras.joinToString(" ") else "${palabras.first()} ${palabras.last()}"
}

private fun carita(n: NivelEncaje) = when (n) {
    NivelEncaje.BIEN -> "😊"
    NivelEncaje.REGULAR -> "😐"
    NivelEncaje.MAL -> "☹"
}

private fun colorEncaje(n: NivelEncaje) = when (n) {
    NivelEncaje.BIEN -> Color(0xFF2E7D32)
    NivelEncaje.REGULAR -> Color(0xFFEF9A00)
    NivelEncaje.MAL -> Color(0xFFC62828)
}

private fun descripcion(n: NivelEncaje) = when (n) {
    NivelEncaje.BIEN -> "Bien"
    NivelEncaje.REGULAR -> "Regular"
    NivelEncaje.MAL -> "Mal"
}

/** Guía: consulta general del juego. Por ahora solo tiene el apartado de convivencia. */
@Composable
fun PantallaGuia(alVolver: () -> Unit) {
    val c = LocalContenedor.current
    var apartado by rememberSaveable { mutableStateOf<String?>(null) }
    Marco(if (apartado == null) "Guía" else "Convivencia", if (apartado == null) alVolver else ({ apartado = null })) { pad ->
        if (apartado == null) {
            Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Ficha(Modifier.clickable(role = Role.Button) { apartado = APARTADO_CONVIVENCIA }, filete = MaterialTheme.colorScheme.primary) {
                    Text("Convivencia", style = MaterialTheme.typography.titleMedium)
                    Text("Cómo se llevan los animales entre sí y con cada hábitat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Convivencia(c.consultarGuiaConvivencia, Modifier.padding(pad))
        }
    }
}

@Composable
private fun Convivencia(guia: ConsultarGuiaConvivencia, modifier: Modifier) {
    var pestana by rememberSaveable { mutableStateOf(PESTANA_HABITATS) }
    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Opcion(pestana == PESTANA_HABITATS, "Hábitats", Modifier.weight(1f)) { pestana = PESTANA_HABITATS }
            Opcion(pestana == PESTANA_ANIMALES, "Animales", Modifier.weight(1f)) { pestana = PESTANA_ANIMALES }
        }
        if (pestana == PESTANA_HABITATS) TablaHabitats(guia) else RelacionesAnimales(guia)
        Leyenda()
    }
}

@Composable
private fun Leyenda() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
        NivelEncaje.entries.forEach {
            Text("${carita(it)} ${descripcion(it).lowercase()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Celda(n: NivelEncaje, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = 40.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(34.dp).background(colorEncaje(n).copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(carita(n)) }
    }
}

/** Filas: especies; columnas: hábitats. La cabecera se queda fija y solo se desplazan las filas. */
@Composable
private fun TablaHabitats(guia: ConsultarGuiaConvivencia) {
    val habitats: List<HabitatGuia> = guia.habitats
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1.4f))
            habitats.forEach {
                Text(
                    nombreCortoHabitat(it.nombre),
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                )
            }
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            guia.especies.forEach { e ->
                val encajes = guia.encajesHabitat(e.id)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.nombre, modifier = Modifier.weight(1.4f).padding(end = 4.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    encajes.forEach { Celda(it, Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Se elige una especie y se ve cómo se lleva con todas las demás, agrupadas en tres bloques plegables. */
@Composable
private fun RelacionesAnimales(guia: ConsultarGuiaConvivencia) {
    var elegida by rememberSaveable { mutableStateOf<String?>(null) }
    var abierto by remember { mutableStateOf(false) }
    var plegados by rememberSaveable { mutableStateOf(setOf(NivelEncaje.REGULAR.name)) }
    val nombre = guia.especies.firstOrNull { it.id == elegida }?.nombre
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box {
            OutlinedButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(nombre ?: "Elige un animal")
            }
            DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                guia.especies.forEach { e: EspecieGuia ->
                    DropdownMenuItem(text = { Text(e.nombre) }, onClick = { elegida = e.id; abierto = false })
                }
            }
        }
        val id = elegida
        if (id != null) {
            val relaciones = guia.relaciones(id)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(NivelEncaje.BIEN to "Se llevan bien", NivelEncaje.REGULAR to "Indiferentes", NivelEncaje.MAL to "Se llevan mal").forEach { (nivel, titulo) ->
                    val del = relaciones.filter { it.nivel == nivel }
                    if (del.isEmpty()) return@forEach
                    val cerrado = nivel.name in plegados
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { plegados = if (cerrado) plegados - nivel.name else plegados + nivel.name },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Celda(nivel)
                        Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(if (cerrado) "▸" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!cerrado) del.forEach {
                        Text(it.especie.nombre, modifier = Modifier.padding(start = 48.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
