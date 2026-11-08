package com.pandazoo.ui.pantalla

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.NavigationBar
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import com.pandazoo.application.caso.AdquirirEspacio
import com.pandazoo.domain.economia.Dinero
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.pandazoo.application.puerto.SeveridadAviso
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** Destinos de navegación que necesita el marco común (cabecera y barra inferior). */
class Atajos(
    val alTienda: () -> Unit,
    val alMonitor: () -> Unit,
    val alPrestigio: () -> Unit,
    val alBeneficios: () -> Unit,
    val alGuia: () -> Unit,
    val alHabitats: () -> Unit,
    val alMisiones: () -> Unit,
    val alNotificaciones: () -> Unit,
)

val LocalAtajos = compositionLocalOf<Atajos> { error("Faltan los atajos de navegación") }

enum class Seccion3 { HABITATS, MISIONES, NOTIFICACIONES }

/** Sección de la barra inferior a la que pertenece la pantalla actual; `null` en las que no cuelgan de ninguna. */
val LocalSeccion = compositionLocalOf<Seccion3?> { null }

@Composable
internal fun Dato(icono: ImageVector?, valor: String, descripcion: String, alPulsar: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClickLabel = descripcion, onClick = alPulsar)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icono != null) Icon(icono, contentDescription = descripcion, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
        Text(valor, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(start = if (icono == null) 0.dp else 6.dp), maxLines = 1)
    }
}

/** Cabecera global: título, satisfacción global, icono de tienda y fila de saldo, bambú e medicinas. */
@Composable
private fun Cabecera(titulo: String, alVolver: (() -> Unit)?) {
    val c = LocalContenedor.current
    val atajos = LocalAtajos.current
    val vm: CabeceraViewModel = viewModel(factory = fabrica { CabeceraViewModel(c) })
    val e by vm.estado.collectAsState()
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (alVolver != null) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Volver", onClick = alVolver),
                    contentAlignment = Alignment.Center,
                ) { Icon(Iconos.Volver, contentDescription = "Volver", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
            Text(titulo, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f).padding(start = if (alVolver == null) 16.dp else 0.dp, end = 8.dp), maxLines = 1)
            Dato(Iconos.Satisfaccion, e?.satisfaccion ?: "…", "Abrir monitor de satisfacción", atajos.alMonitor)
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Abrir guía", onClick = atajos.alGuia),
                contentAlignment = Alignment.Center,
            ) { Icon(Iconos.Guia, contentDescription = "Abrir guía", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Abrir tienda", onClick = atajos.alTienda),
                contentAlignment = Alignment.Center,
            ) { Icon(Iconos.Tienda, contentDescription = "Abrir tienda", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dato(Iconos.Saldo, e?.saldo ?: "…", "Ver beneficios", atajos.alBeneficios, Modifier.weight(1f))
            Dato(Iconos.Bambu, e?.bambu ?: "…", "Comprar bambú", atajos.alTienda, Modifier.weight(1f))
            Dato(Iconos.InsumosMedicos, e?.insumos ?: "…", "Comprar medicinas", atajos.alTienda, Modifier.weight(1f))
            Dato(Iconos.Prestigio, e?.prestigio ?: "…", "Abrir panel de prestigio", atajos.alPrestigio, Modifier.weight(1.3f))
        }
    }
}

@Composable
private fun BarraInferior() {
    val actual = LocalSeccion.current
    val a = LocalAtajos.current
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        listOf(
            Triple(Seccion3.HABITATS, Iconos.Habitats to "Hábitats", a.alHabitats),
            Triple(Seccion3.MISIONES, Iconos.Misiones to "Misiones", a.alMisiones),
            Triple(Seccion3.NOTIFICACIONES, Iconos.Avisos to "Avisos", a.alNotificaciones),
        ).forEach { (sec, par, accion) ->
            NavigationBarItem(
                selected = sec == actual,
                onClick = accion,
                icon = { Icon(par.first, contentDescription = null) },
                label = { Text(par.second) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
            )
        }
    }
}

@Composable
internal fun Marco(titulo: String, alVolver: (() -> Unit)?, contenido: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                Cabecera(titulo, alVolver)
                Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.secondary))
            }
        },
        bottomBar = { BarraInferior() },
        content = contenido,
    )
}

@Composable
internal fun MensajeUi(m: Mensaje?) {
    if (m != null) AvisoUi(m.texto, if (m.exito) TipoAviso.EXITO else TipoAviso.ERROR, Modifier.padding(vertical = 8.dp))
}

@Composable
internal fun Fila(etiqueta: String, valor: String) = FilaDato(etiqueta, valor)

@Composable
internal fun FichaSaldo(saldo: String) {
    Ficha(filete = MaterialTheme.colorScheme.primary) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Saldo disponible", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false))
            Text(saldo, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
internal fun TituloFicha(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
internal fun BotonPrincipal(texto: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(texto) }
}

@Composable
internal fun Opcion(seleccionada: Boolean, texto: String, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    FilterChip(
        selected = seleccionada,
        onClick = onClick,
        label = { Text(texto) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = seleccionada,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/** Recurso de la tienda: insignia con el icono del recurso y una sola línea con nombre y precio. */
@Composable
private fun OpcionRecurso(seleccionada: Boolean, o: OfertaUi, onClick: () -> Unit) {
    val e = MaterialTheme.colorScheme
    val forma = MaterialTheme.shapes.medium
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(forma)
            .background(if (seleccionada) e.primaryContainer else e.surface)
            .border(if (seleccionada) 2.dp else 1.dp, if (seleccionada) e.primary else e.outlineVariant, forma)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(e.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(if (o.insumo) Iconos.InsumosMedicos else Iconos.Bambu, contentDescription = null, modifier = Modifier.size(24.dp), tint = e.primary)
        }
        Text(o.etiqueta, style = MaterialTheme.typography.bodyLarge, color = e.onSurface, modifier = Modifier.weight(1f))
    }
}

/** CU-16 y CU-08: beneficios pendientes y por ciclo, y reclamación con confirmación. */
@Composable
fun PantallaBeneficios(alVolver: () -> Unit) {
    val c = LocalContenedor.current
    val vm: BeneficiosViewModel = viewModel(factory = fabrica { BeneficiosViewModel(c) })
    val vista by vm.vista.collectAsState()
    val confirmando by vm.confirmando.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    val desglose by vm.desglose.collectAsState()
    var abiertos by remember { mutableStateOf(emptySet<String>()) }
    Marco("Beneficios", alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val v = vista
            if (v == null) {
                Text("Cargando…", color = MaterialTheme.colorScheme.onBackground)
            } else {
                Ficha(filete = MaterialTheme.colorScheme.primary) {
                    TituloFicha("Beneficios pendientes")
                    Fila("Pendientes de cobro", v.pendiente)
                    Fila("Saldo actual", v.saldo)
                }
                BotonPrincipal("Reclamar beneficios", vm::pedirReclamar, enabled = v.puedeReclamar)
                MensajeUi(mensaje)
                // Como el monitor de satisfacción: hábitat, espacio y animal desplegables, con lo que genera cada uno por ciclo.
                desglose.forEach { h ->
                    val abierto = h.clave in abiertos
                    Ficha(Modifier.clickable { abiertos = if (abierto) abiertos - h.clave else abiertos + h.clave }, filete = MaterialTheme.colorScheme.primary) {
                        FilaNodo(abierto, h.titulo, h.valor, h.hijos.isNotEmpty())
                    }
                    if (abierto) h.hijos.forEach { e ->
                        val abiertoE = e.clave in abiertos
                        Ficha(Modifier.padding(start = 16.dp).clickable { abiertos = if (abiertoE) abiertos - e.clave else abiertos + e.clave }, filete = MaterialTheme.colorScheme.tertiary) {
                            FilaNodo(abiertoE, e.titulo, e.valor, e.hijos.isNotEmpty())
                        }
                        if (abiertoE) e.hijos.forEach { a ->
                            Ficha(Modifier.padding(start = 32.dp), filete = MaterialTheme.colorScheme.secondary) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(a.titulo, modifier = Modifier.weight(1f))
                                    Text(a.valor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    confirmando?.let {
        VentanaFlotante(
            VentanaUi(
                icono = Iconos.Saldo,
                titulo = "Reclamar beneficios",
                datos = listOf(
                    DatoVentana("+${Textos.dinero(it)}", "recibes"),
                    DatoVentana(vista?.saldo ?: "", "saldo actual"),
                ),
                confirmar = "Confirmar reclamación",
            ),
            alConfirmar = vm::reclamar,
            alCancelar = vm::cancelar,
        )
    }
}

@Composable
internal fun PantallaVacia(texto: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** CU-20: una misión vigente por tipo; la cumplida muestra la acción explícita de reclamar (CU-15). */
@Composable
fun PantallaMisiones() {
    val c = LocalContenedor.current
    val vm: MisionesViewModel = viewModel(factory = fabrica { MisionesViewModel(c) })
    val misiones by vm.misiones.collectAsState()
    val confirmando by vm.confirmando.collectAsState()
    val premio by vm.premio.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    Marco("Misiones", null) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MensajeUi(mensaje)
            if (misiones.isEmpty()) {
                Text("No hay misiones que mostrar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            misiones.forEach { m ->
                val color = when {
                    m.exclusiva -> Color(0xFFB59044)
                    m.cumplida -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.secondary
                }
                Ficha(filete = color, rango = if (m.exclusiva) "Exclusivo" else null) {
                    Text(m.descripcion, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(m.fraccion.coerceIn(0f, 1f)).fillMaxHeight().background(color))
                    }
                    Fila("Progreso", m.progreso)
                    if (m.exclusiva) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Recompensa", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            NombreRango(m.recompensa, "Exclusivo", modifier = Modifier.weight(1f), estilo = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End))
                        }
                    } else {
                        Fila("Recompensa", m.recompensa)
                    }
                    if (m.reclamable) {
                        Spacer(Modifier.height(8.dp))
                        BotonPrincipal("Reclamar recompensa", { vm.pedirReclamar(m) })
                    }
                }
            }
        }
    }
    confirmando?.let {
        VentanaFlotante(
            VentanaUi(
                icono = Iconos.Prestigio,
                titulo = "Reclamar recompensa",
                datos = listOf(DatoVentana(it.recompensa, "recompensa")),
                confirmar = "Confirmar reclamación",
            ),
            alConfirmar = vm::reclamar,
            alCancelar = vm::cancelar,
        )
    }
    premio?.let { VentanaPremio(it, vm::cerrarPremio) }
}

/** Una fila de aviso que se descarta deslizándola hacia cualquier lado (solo severidad baja y media). */
@Composable
private fun AvisoDeslizable(a: AvisoItemUi, alDescartar: () -> Unit, alPulsar: (() -> Unit)?) {
    val contenido: @Composable () -> Unit = {
        AvisoUi(a.texto, a.tono, if (alPulsar != null) Modifier.clickable(role = Role.Button, onClick = alPulsar) else Modifier)
    }
    if (!a.descartable) {
        contenido()
        return
    }
    val estado = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it != SwipeToDismissBoxValue.Settled) alDescartar()
            it != SwipeToDismissBoxValue.Settled
        },
    )
    SwipeToDismissBox(
        state = estado,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 20.dp),
                contentAlignment = if (estado.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) { Icon(Iconos.Cerrar, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        content = { contenido() },
    )
}

/** CU-21: avisos abiertos. El color indica la severidad; los de baja y media se deslizan para descartarlos. */
@Composable
fun PantallaNotificaciones(alSeguimiento: () -> Unit = {}, alRegistrarCria: (Ref) -> Unit = {}) {
    val c = LocalContenedor.current
    val vm: AvisosViewModel = viewModel(factory = fabrica { AvisosViewModel(c) })
    val avisos by vm.avisos.collectAsState()
    val filtro by vm.filtro.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    Marco("Avisos", null) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonPrincipal("Seguimiento crítico", alSeguimiento)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Opcion(filtro == null, "Todos", Modifier.weight(1f)) { vm.filtrar(null) }
                Opcion(filtro == SeveridadAviso.ALTA, "Alta", Modifier.weight(1f)) { vm.filtrar(SeveridadAviso.ALTA) }
                Opcion(filtro == SeveridadAviso.MEDIA, "Media", Modifier.weight(1f)) { vm.filtrar(SeveridadAviso.MEDIA) }
                Opcion(filtro == SeveridadAviso.BAJA, "Baja", Modifier.weight(1f)) { vm.filtrar(SeveridadAviso.BAJA) }
            }
            MensajeUi(mensaje)
            if (avisos.isEmpty()) {
                Text("No hay avisos por ahora.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            avisos.forEach { a ->
                key(a.id) {
                    AvisoDeslizable(
                        a,
                        alDescartar = { vm.descartar(a) },
                        alPulsar = when {
                            a.critico -> ({ vm.abrir(a, alSeguimiento) })
                            a.reproduccion -> ({ vm.abrir(a) { alRegistrarCria(Ref(a.id.toString())) } })
                            else -> null
                        },
                    )
                }
            }
        }
    }
}

/** CU-18: animales desnutridos o enfermos, con ciclos acumulados y restantes y acceso a la ficha. */
@Composable
fun PantallaSeguimientoCritico(alVolver: () -> Unit, alFicha: (Ref) -> Unit) {
    val c = LocalContenedor.current
    val vm: SeguimientoViewModel = viewModel(factory = fabrica { SeguimientoViewModel(c) })
    val registros by vm.registros.collectAsState()
    Marco("Seguimiento crítico", alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (registros.isEmpty()) {
                Text("No hay animales que requieran seguimiento.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            registros.forEach { r ->
                AvisoUi(
                    "${r.titulo}\n${r.causa}\n${r.detalle}",
                    if (r.urgente) TipoAviso.ERROR else TipoAviso.ADVERTENCIA,
                    Modifier.clickable { alFicha(r.ref) },
                )
            }
        }
    }
}

/** CU-07: comprar bambú o medicinas. */
@Composable
fun PantallaTienda(alVolver: () -> Unit, alAnimales: () -> Unit) {
    val c = LocalContenedor.current
    val vm: TiendaViewModel = viewModel(factory = fabrica { TiendaViewModel(c) })
    val ofertas by vm.ofertas.collectAsState()
    val saldo by vm.saldo.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    var seleccion by rememberSaveable { mutableStateOf<Int?>(null) }
    var cantidadTexto by rememberSaveable { mutableStateOf("") }
    val cantidad = cantidadTexto.toIntOrNull()
    val oferta = seleccion?.let { ofertas.getOrNull(it) }
    Marco("Tienda", alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FichaSaldo(saldo)
            OutlinedButton(onClick = alAnimales, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Comprar animales") }
            if (ofertas.isEmpty()) Text("No hay recursos disponibles para comprar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            ofertas.forEachIndexed { i, o ->
                OpcionRecurso(seleccion == i, o) {
                    seleccion = i
                    vm.descartarMensaje()
                }
            }
            if (oferta != null) {
                Selector(
                    texto = cantidadTexto,
                    etiqueta = "Cantidad (${oferta.unidad})",
                    alCambiar = { cantidadTexto = it.filter(Char::isDigit).take(9) },
                )
                val coste = if (cantidad != null && cantidad > 0 && oferta.precio != null) {
                    com.pandazoo.domain.economia.Dinero.coste(cantidad, oferta.precio)
                } else {
                    null
                }
                Ficha(filete = MaterialTheme.colorScheme.secondary) {
                    Text(
                        if (coste != null) "Coste total: ${Textos.dinero(coste)}" else "Indica una cantidad entera positiva.",
                        style = if (coste != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    )
                }
                BotonPrincipal("Confirmar compra", { vm.comprar(oferta, cantidad ?: 0) }, enabled = coste != null)
            }
            MensajeUi(mensaje)
        }
    }
}

/** Selector de cantidad: botones −/+ grandes, saltos rápidos y edición directa con el teclado. */
@Composable
private fun Selector(texto: String, etiqueta: String, alCambiar: (String) -> Unit) {
    val n = texto.toIntOrNull() ?: 0
    fun fijar(v: Int) = alCambiar(v.coerceIn(0, 999_999_999).toString())
    Ficha(filete = MaterialTheme.colorScheme.primary) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalIconButton(onClick = { fijar(n - 1) }, enabled = n > 1, modifier = Modifier.size(64.dp)) {
                    Icon(Iconos.Menos, contentDescription = "Reducir cantidad", modifier = Modifier.size(32.dp))
                }
                OutlinedTextField(
                    value = texto,
                    onValueChange = alCambiar,
                    label = { Text(etiqueta) },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(onClick = { fijar(n + 1) }, modifier = Modifier.size(64.dp)) {
                    Icon(Iconos.Mas, contentDescription = "Aumentar cantidad", modifier = Modifier.size(32.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 50, 100).forEach { paso ->
                    AssistChip(onClick = { fijar(n + paso) }, label = { Text("+$paso") })
                }
            }
        }
    }
}

/** RF-08: primer nivel de la jerarquía. */
@Composable
fun PantallaHabitats(alHabitat: (Ref) -> Unit) {
    val c = LocalContenedor.current
    val vm: HabitatsViewModel = viewModel(factory = fabrica { HabitatsViewModel(c) })
    val habitats by vm.habitats.collectAsState()
    Marco("Hábitats", null) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(habitats) { h ->
                Ficha(
                    Modifier.clickable(enabled = h.disponible) { alHabitat(h.ref) },
                    filete = if (h.disponible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    atenuada = !h.disponible,
                ) {
                    Text(h.nombre, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** RF-08: segundo nivel, espacios de un hábitat. Al abrir un espacio se llega al tercer nivel, sus animales. */
@Composable
fun PantallaEspacios(habitat: Ref, alVolver: () -> Unit, alComprar: () -> Unit, alAnimales: (Ref) -> Unit) {
    val c = LocalContenedor.current
    val vm: EspaciosViewModel = viewModel(key = "espacios-${habitat.valor}", factory = fabrica { EspaciosViewModel(c, habitat) })
    val espacios by vm.espacios.collectAsState()
    val nombre by vm.nombreHabitat.collectAsState()
    val hayHambre by vm.hayHambre.collectAsState()
    val grupal by vm.confirmandoGrupal.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    Marco(nombre.ifEmpty { "Espacios" }, alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonPrincipal("Comprar espacio", alComprar)
            if (espacios.isNotEmpty()) BotonPrincipal("Alimentar a todos", vm::pedirAlimentarGrupo, enabled = hayHambre)
            MensajeUi(mensaje)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(espacios) { e ->
                    Ficha(
                        Modifier.clickable(enabled = e.activo) { alAnimales(e.ref) },
                        filete = if (e.activo) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                        atenuada = !e.activo,
                    ) {
                        Text(e.nombre, style = MaterialTheme.typography.titleMedium)
                        OcupacionEspacio(e)
                    }
                }
            }
        }
    }
    grupal?.let {
        VentanaFlotante(it.ventana, alConfirmar = vm::confirmarGrupal, alCancelar = vm::cancelarGrupal)
    }
}

/** Capacidad de un espacio: insignia con el número de plazas, el nombre y el coste en una pastilla. */
@Composable
private fun OpcionCapacidad(o: CapacidadUi, onClick: () -> Unit) {
    val e = MaterialTheme.colorScheme
    val forma = MaterialTheme.shapes.large
    val gratis = o.coste == Dinero.CERO
    Row(
        Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(forma)
            .background(e.surface)
            .border(1.dp, e.outlineVariant, forma)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.medium).background(e.primaryContainer), contentAlignment = Alignment.Center) {
            Text("${o.capacidad}", style = MaterialTheme.typography.headlineSmall, color = e.onPrimaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${o.capacidad} animales", style = MaterialTheme.typography.titleMedium, color = e.onSurface)
            Text("Plazas del espacio", style = MaterialTheme.typography.labelMedium, color = e.onSurfaceVariant)
        }
        Text(
            if (gratis) "Gratis" else Textos.dinero(o.coste),
            style = MaterialTheme.typography.titleSmall,
            color = if (gratis) e.onSecondaryContainer else e.onPrimaryContainer,
            modifier = Modifier.clip(CircleShape)
                .background(if (gratis) e.secondaryContainer else e.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** CU-03: comprar un espacio. */
@Composable
fun PantallaComprarEspacio(habitat: Ref, alVolver: () -> Unit) {
    val c = LocalContenedor.current
    val vm: ComprarEspacioViewModel = viewModel(key = "comprar-${habitat.valor}", factory = fabrica { ComprarEspacioViewModel(c, habitat) })
    val capacidades by vm.capacidades.collectAsState()
    val saldo by vm.saldo.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    val creado by vm.creado.collectAsState()
    var nombre by rememberSaveable { mutableStateOf("") }
    var capacidad by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(creado) { if (creado) alVolver() }
    Marco("Comprar espacio", alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FichaSaldo(saldo)
            Text("Elige la capacidad del espacio", style = MaterialTheme.typography.titleMedium)
            capacidades.forEach { o ->
                OpcionCapacidad(o) {
                    vm.descartarMensaje()
                    capacidad = o.capacidad
                }
            }
        }
    }
    // La denominación se pide solo cuando ya se ha elegido la capacidad, en una ventana flotante.
    capacidades.firstOrNull { it.capacidad == capacidad }?.let { elegida ->
        VentanaDenominacion(
            capacidad = elegida.capacidad,
            coste = if (elegida.coste == Dinero.CERO) "Gratis" else Textos.dinero(elegida.coste),
            nombre = nombre,
            alCambiarNombre = { nombre = it },
            mensaje = mensaje,
            alComprar = { vm.comprar(nombre, elegida.capacidad) },
            alCerrar = {
                capacidad = null
                nombre = ""
            },
        )
    }
}

/** Ventana flotante que pide la denominación; el botón de comprar, con el coste, aparece cuando el nombre es válido. */
@Composable
private fun VentanaDenominacion(
    capacidad: Int,
    coste: String,
    nombre: String,
    alCambiarNombre: (String) -> Unit,
    mensaje: Mensaje?,
    alComprar: () -> Unit,
    alCerrar: () -> Unit,
) {
    val valido = nombre.trim().isNotEmpty() && nombre.trim().length <= AdquirirEspacio.MAX_DENOMINACION
    Dialog(onDismissRequest = alCerrar) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Espacio de $capacidad animales", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = nombre,
                    onValueChange = alCambiarNombre,
                    label = { Text("Denominación del espacio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (valido) BotonPrincipal("Comprar — $coste", alComprar)
                MensajeUi(mensaje)
            }
        }
    }
}

/** CU-02: cambiar la denominación de un espacio. Ni la capacidad ni el hábitat se pueden modificar. */
@Composable
fun PantallaConfigurarEspacio(espacio: Ref, alVolver: () -> Unit) {
    val c = LocalContenedor.current
    val vm: ConfigurarEspacioViewModel = viewModel(key = "config-${espacio.valor}", factory = fabrica { ConfigurarEspacioViewModel(c, espacio) })
    val actual by vm.actual.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    val guardado by vm.guardado.collectAsState()
    val ampliaciones by vm.ampliaciones.collectAsState()
    val ampliacion by vm.confirmandoAmpliacion.collectAsState()
    var nombre by rememberSaveable(actual?.nombre) { mutableStateOf(actual?.nombre ?: "") }
    LaunchedEffect(guardado) { if (guardado) alVolver() }
    Marco("Configurar espacio", alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            actual?.let { Ficha(filete = MaterialTheme.colorScheme.tertiary) { OcupacionEspacio(it) } }
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Denominación") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            BotonPrincipal("Guardar", { vm.guardar(nombre) }, enabled = nombre.isNotBlank())
            Text("Ampliar capacidad", style = MaterialTheme.typography.titleMedium)
            if (ampliaciones.isEmpty()) {
                Text("Este espacio ya tiene la capacidad máxima.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ampliaciones.forEach { o ->
                    OpcionCapacidad(o) { vm.elegirAmpliacion(o) }
                }
            }
            MensajeUi(mensaje)
        }
    }
    ampliacion?.let {
        VentanaFlotante(
            VentanaUi(
                icono = Iconos.Saldo,
                titulo = "Ampliar a ${it.capacidad} animales",
                datos = listOf(DatoVentana(Textos.dinero(it.coste), "pagas", Iconos.Saldo)),
                confirmar = "Confirmar ampliación",
            ),
            alConfirmar = vm::confirmarAmpliacion,
            alCancelar = vm::cancelarAmpliacion,
        )
    }
}

/** CU-19: nivel de prestigio actual e histórico con las estadísticas que lo determinan (RF-36, RF-37). Solo lectura. */
@Composable
fun PantallaPrestigio(alVolver: () -> Unit) {
    val c = LocalContenedor.current
    val vm: PrestigioViewModel = viewModel(factory = fabrica { PrestigioViewModel(c) })
    val estado by vm.estado.collectAsState()
    Marco("Prestigio", alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val p = estado
            if (p == null) {
                Text("Cargando…", color = MaterialTheme.colorScheme.onBackground)
            } else {
                Ficha(filete = MaterialTheme.colorScheme.primary) {
                    TituloFicha("Prestigio actual")
                    Text(p.nivel, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                p.avisos.forEach { AvisoUi(it, TipoAviso.ADVERTENCIA) }
                Ficha(filete = MaterialTheme.colorScheme.tertiary) {
                    val s = p.siguiente
                    if (s == null) {
                        TituloFicha("Siguiente prestigio")
                        Text("Prestigio máximo alcanzado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        TituloFicha("Siguiente prestigio · ${s.nivel}")
                        Fila("Animales vivos", s.animales)
                        Fila("Satisfacción global", s.satisfaccion)
                        if (s.especies.isNotEmpty()) Fila("Desbloquea especies", s.especies.joinToString(", "))
                        if (s.habitats.isNotEmpty()) Fila("Desbloquea hábitats", s.habitats.joinToString(", "))
                    }
                }
                Ficha(filete = MaterialTheme.colorScheme.secondary) {
                    TituloFicha("Estadísticas del parque")
                    Fila("Animales vivos alojados", p.animales)
                    Fila("Satisfacción global", p.satisfaccion)
                }
                Ficha(filete = MaterialTheme.colorScheme.tertiary) {
                    TituloFicha("Máximos históricos")
                    Fila("Prestigio", p.maximoNivel)
                    Fila("Animales vivos", p.maximoAnimales)
                    Fila("Satisfacción global", p.maximoSatisfaccion)
                }
            }
        }
    }
}

/** CU-17: satisfacción del parque con desglose desplegable hábitat, espacio y animal (RF-27). Solo lectura. */
@Composable
fun PantallaMonitor(alVolver: () -> Unit) {
    val c = LocalContenedor.current
    val vm: MonitorViewModel = viewModel(factory = fabrica { MonitorViewModel(c) })
    val estado by vm.estado.collectAsState()
    var abiertos by remember { mutableStateOf(emptySet<String>()) }
    fun alternar(clave: String) {
        abiertos = if (clave in abiertos) abiertos - clave else abiertos + clave
    }
    Marco("Satisfacción", alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val m = estado
            if (m == null) {
                Text("Cargando…", color = MaterialTheme.colorScheme.onBackground)
            } else {
                Ficha(filete = MaterialTheme.colorScheme.primary) {
                    TituloFicha("Parque")
                    Text(m.parque, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                m.aviso?.let { AvisoUi(it, TipoAviso.ADVERTENCIA) }
                m.avisoHabitats?.let { AvisoUi(it, TipoAviso.ADVERTENCIA) }
                m.habitats.forEach { h ->
                    val abierto = h.clave in abiertos
                    Ficha(Modifier.clickable { alternar(h.clave) }, filete = MaterialTheme.colorScheme.primary) {
                        FilaNodo(abierto, h.nombre, h.valor, h.espacios.isNotEmpty())
                        h.aviso?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    if (abierto) {
                        h.espacios.forEach { e ->
                            val abiertoE = e.clave in abiertos
                            Ficha(Modifier.padding(start = 16.dp).clickable { alternar(e.clave) }, filete = MaterialTheme.colorScheme.tertiary) {
                                FilaNodo(abiertoE, e.nombre, e.valor, e.animales.isNotEmpty())
                                e.aviso?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            }
                            if (abiertoE) {
                                e.animales.forEach { a ->
                                    Ficha(Modifier.padding(start = 32.dp), filete = MaterialTheme.colorScheme.secondary) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(a.etiqueta, modifier = Modifier.weight(1f))
                                            Text(a.valor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FilaNodo(abierto: Boolean, nombre: String, valor: String, desplegable: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            (if (!desplegable) "" else if (abierto) "▾ " else "▸ ") + nombre,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(valor, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
