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
import androidx.compose.material3.AlertDialog
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
    val alHabitats: () -> Unit,
    val alMisiones: () -> Unit,
    val alNotificaciones: () -> Unit,
)

val LocalAtajos = compositionLocalOf<Atajos> { error("Faltan los atajos de navegación") }

enum class Seccion3 { HABITATS, MISIONES, NOTIFICACIONES }

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

/** Cabecera global: título, satisfacción global, icono de tienda y fila de saldo, bambú e insumos médicos. */
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
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Abrir tienda", onClick = atajos.alTienda),
                contentAlignment = Alignment.Center,
            ) { Icon(Iconos.Tienda, contentDescription = "Abrir tienda", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dato(Iconos.Saldo, e?.saldo ?: "…", "Ver beneficios", atajos.alBeneficios, Modifier.weight(1f))
            Dato(Iconos.Bambu, e?.bambu ?: "…", "Comprar bambú", atajos.alTienda, Modifier.weight(1f))
            Dato(Iconos.InsumosMedicos, e?.insumos ?: "…", "Comprar insumos médicos", atajos.alTienda, Modifier.weight(1f))
            Dato(Iconos.Prestigio, e?.prestigio ?: "…", "Abrir panel de prestigio", atajos.alPrestigio, Modifier.weight(1.3f))
        }
    }
}

@Composable
private fun BarraInferior(actual: Seccion3) {
    val a = LocalAtajos.current
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        listOf(
            Triple(Seccion3.HABITATS, Iconos.Habitats to "Hábitats", a.alHabitats),
            Triple(Seccion3.MISIONES, Iconos.Misiones to "Misiones", a.alMisiones),
            Triple(Seccion3.NOTIFICACIONES, Iconos.Avisos to "Avisos", a.alNotificaciones),
        ).forEach { (sec, par, accion) ->
            NavigationBarItem(
                selected = sec == actual,
                onClick = { if (sec != actual) accion() },
                icon = { Icon(par.first, contentDescription = null) },
                label = { Text(par.second) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
            )
        }
    }
}

@Composable
internal fun Marco(titulo: String, alVolver: (() -> Unit)?, seccion: Seccion3? = null, contenido: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                Cabecera(titulo, alVolver)
                Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.secondary))
            }
        },
        bottomBar = { if (seccion != null) BarraInferior(seccion) },
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
            Text("Saldo disponible", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(saldo, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
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
                if (!v.puedeReclamar) Text("No hay beneficios pendientes de reclamar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                MensajeUi(mensaje)
                Ficha(filete = MaterialTheme.colorScheme.secondary) {
                    TituloFicha("Beneficios por ciclo")
                    if (v.porCiclo.isEmpty()) {
                        Text("Aún no hay ciclos confirmados.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        v.porCiclo.forEach { Fila(it.etiqueta, it.valor) }
                    }
                }
            }
        }
    }
    confirmando?.let {
        AlertDialog(
            onDismissRequest = vm::cancelar,
            title = { Text("Reclamar beneficios") },
            text = { Text("Se acreditarán ${Textos.dinero(it)} a tu saldo. La reclamación no se puede deshacer.") },
            confirmButton = { TextButton(onClick = vm::reclamar) { Text("Confirmar reclamación") } },
            dismissButton = { TextButton(onClick = vm::cancelar) { Text("Cancelar") } },
        )
    }
}

@Composable
internal fun PantallaVacia(texto: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PantallaMisiones() {
    Marco("Misiones", null, Seccion3.MISIONES) { pad -> Box(Modifier.padding(pad)) { PantallaVacia("Las misiones llegarán en un próximo sprint.") } }
}

/** CU-21: historial de avisos con severidad, estado y fecha; abrir uno pendiente lo marca como visto. */
@Composable
fun PantallaNotificaciones(alSeguimiento: () -> Unit = {}, alRegistrarCria: (Ref) -> Unit = {}) {
    val c = LocalContenedor.current
    val vm: AvisosViewModel = viewModel(factory = fabrica { AvisosViewModel(c) })
    val avisos by vm.avisos.collectAsState()
    val filtro by vm.filtro.collectAsState()
    val detalle by vm.detalle.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    Marco("Avisos", null, Seccion3.NOTIFICACIONES) { pad ->
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
                AvisoUi(
                    "${a.etiqueta} · ${a.estado} · ${a.fecha}\n${a.texto}",
                    a.tono,
                    Modifier.clickable { vm.abrir(a) },
                )
            }
        }
    }
    detalle?.let { a ->
        AlertDialog(
            onDismissRequest = vm::cerrarDetalle,
            title = { Text(a.etiqueta) },
            text = { Text("${a.texto}\n\n${a.estado} · ${a.fecha}") },
            confirmButton = {
                if (a.critico) {
                    TextButton(onClick = { vm.cerrarDetalle(); alSeguimiento() }) { Text("Ver seguimiento") }
                } else if (a.reproduccion) {
                    TextButton(onClick = { vm.cerrarDetalle(); alRegistrarCria(Ref(a.id.toString())) }) { Text("Registrar cría") }
                } else {
                    TextButton(onClick = vm::cerrarDetalle) { Text("Cerrar") }
                }
            },
            dismissButton = { if (a.critico || a.reproduccion) TextButton(onClick = vm::cerrarDetalle) { Text("Cerrar") } },
        )
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

/** CU-07: comprar bambú o insumos médicos. */
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
            if (ofertas.isEmpty()) Text("No hay recursos disponibles para comprar.")
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
    val mensaje by vm.mensaje.collectAsState()
    Marco("Hábitats", null, Seccion3.HABITATS) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (c.depuracion) {
                item {
                    OutlinedButton(onClick = vm::generarDatosDePrueba, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Generar datos de prueba (depuración)")
                    }
                    OutlinedButton(onClick = vm::eliminarDatosDePrueba, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Eliminar datos de prueba (depuración)")
                    }
                    OutlinedButton(onClick = vm::anadirPanda, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Añadir panda (depuración)")
                    }
                    OutlinedButton(onClick = vm::adelantarCiclo, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Adelantar ciclo (depuración)")
                    }
                    MensajeUi(mensaje)
                }
            }
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
    Marco(nombre.ifEmpty { "Espacios" }, alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonPrincipal("Comprar espacio", alComprar)
            if (espacios.isEmpty()) Text("Este hábitat aún no tiene espacios.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(espacios) { e ->
                    Ficha(
                        Modifier.clickable(enabled = e.activo) { alAnimales(e.ref) },
                        filete = if (e.activo) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                        atenuada = !e.activo,
                    ) {
                        Text(e.nombre, style = MaterialTheme.typography.titleMedium)
                        Text(e.detalle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
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
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Denominación del espacio") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Capacidad", style = MaterialTheme.typography.titleMedium)
            capacidades.forEach { o ->
                Opcion(capacidad == o.capacidad, o.etiqueta) { capacidad = o.capacidad }
            }
            BotonPrincipal("Confirmar compra", { capacidad?.let { vm.comprar(nombre, it) } }, enabled = capacidad != null && nombre.isNotBlank())
            MensajeUi(mensaje)
        }
    }
}

/** CU-02: cambiar denominación y/o hábitat de un espacio. La capacidad no se puede modificar. */
@Composable
fun PantallaConfigurarEspacio(espacio: Ref, alVolver: () -> Unit) {
    val c = LocalContenedor.current
    val vm: ConfigurarEspacioViewModel = viewModel(key = "config-${espacio.valor}", factory = fabrica { ConfigurarEspacioViewModel(c, espacio) })
    val actual by vm.actual.collectAsState()
    val habitatActual by vm.habitatActual.collectAsState()
    val habitats by vm.habitats.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    val guardado by vm.guardado.collectAsState()
    var nombre by rememberSaveable(actual?.nombre) { mutableStateOf(actual?.nombre ?: "") }
    var destino by remember(habitatActual) { mutableStateOf(habitatActual) }
    LaunchedEffect(guardado) { if (guardado) alVolver() }
    Marco("Configurar espacio", alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            actual?.let { Ficha(filete = MaterialTheme.colorScheme.tertiary) { Text(it.detalle) } }
            Text("La capacidad no se puede modificar.", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Denominación") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Hábitat", style = MaterialTheme.typography.titleMedium)
            habitats.forEach { h ->
                Opcion(destino == h.ref, h.nombre) { destino = h.ref }
            }
            BotonPrincipal("Guardar", { vm.guardar(nombre, destino) }, enabled = nombre.isNotBlank())
            MensajeUi(mensaje)
        }
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
                    p.siguiente?.let { Text("Para el siguiente: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                p.avisos.forEach { AvisoUi(it, TipoAviso.ADVERTENCIA) }
                Ficha(filete = MaterialTheme.colorScheme.secondary) {
                    TituloFicha("Estadísticas del parque")
                    Fila("Animales vivos alojados", p.animales)
                    Fila("Satisfacción global", p.satisfaccion)
                }
                Ficha(filete = MaterialTheme.colorScheme.tertiary) {
                    TituloFicha("Máximos históricos")
                    Fila("Nivel de prestigio", p.maximoNivel)
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
    Marco("Monitor de satisfacción", alVolver) { pad ->
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
                                            Text(a.valor, style = MaterialTheme.typography.bodyLarge)
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
private fun FilaNodo(abierto: Boolean, nombre: String, valor: String, desplegable: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            (if (!desplegable) "" else if (abierto) "▾ " else "▸ ") + nombre,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(valor, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
