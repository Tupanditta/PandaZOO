package com.pandazoo.ui.pantalla

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PandaZooTema(contenido: @Composable () -> Unit) {
    MaterialTheme(content = contenido)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Marco(titulo: String, alVolver: (() -> Unit)?, contenido: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulo) },
                navigationIcon = { if (alVolver != null) OutlinedButton(onClick = alVolver) { Text("Volver") } },
            )
        },
        content = contenido,
    )
}

@Composable
private fun MensajeUi(m: Mensaje?) {
    if (m != null) {
        Text(
            text = m.texto,
            color = if (m.exito) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun Fila(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta)
        Text(valor)
    }
}

/** CU-16: balance, existencias y beneficios pendientes, solo lectura. */
@Composable
fun PantallaInicio(alTienda: () -> Unit, alZoo: () -> Unit) {
    val c = LocalContenedor.current
    val vm: InicioViewModel = viewModel(factory = fabrica { InicioViewModel(c) })
    val estado by vm.estado.collectAsState()
    Marco("PandaZOO", null) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val e = estado
            if (e == null) {
                Text("Cargando…")
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Finanzas", style = MaterialTheme.typography.titleMedium)
                        Fila("Saldo", e.saldo ?: "No disponible")
                        Fila("Beneficios pendientes", e.beneficiosPendientes ?: "No disponible")
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Inventario", style = MaterialTheme.typography.titleMedium)
                        if (e.existencias == null) Text("No disponible") else e.existencias.forEach { Fila(it.etiqueta, it.valor) }
                    }
                }
                e.avisos.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Button(onClick = alTienda, modifier = Modifier.fillMaxWidth()) { Text("Tienda de recursos") }
            Button(onClick = alZoo, modifier = Modifier.fillMaxWidth()) { Text("Hábitats y espacios") }
        }
    }
}

/** CU-07: comprar bambú o insumos médicos. */
@Composable
fun PantallaTienda(alVolver: () -> Unit) {
    val c = LocalContenedor.current
    val vm: TiendaViewModel = viewModel(factory = fabrica { TiendaViewModel(c) })
    val ofertas by vm.ofertas.collectAsState()
    val saldo by vm.saldo.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    var seleccion by rememberSaveable { mutableStateOf<Int?>(null) }
    var cantidadTexto by rememberSaveable { mutableStateOf("") }
    val cantidad = cantidadTexto.toIntOrNull()
    val oferta = seleccion?.let { ofertas.getOrNull(it) }
    Marco("Tienda de recursos", alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Fila("Saldo disponible", saldo)
            if (ofertas.isEmpty()) Text("No hay recursos disponibles para comprar.")
            ofertas.forEachIndexed { i, o ->
                FilterChip(
                    selected = seleccion == i,
                    onClick = {
                        seleccion = i
                        vm.descartarMensaje()
                    },
                    label = { Text(o.etiqueta) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (oferta != null) {
                OutlinedTextField(
                    value = cantidadTexto,
                    onValueChange = { cantidadTexto = it.filter(Char::isDigit).take(9) },
                    label = { Text("Cantidad (${oferta.unidad})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val coste = if (cantidad != null && cantidad > 0 && oferta.precio != null) {
                    com.pandazoo.domain.economia.Dinero.coste(cantidad, oferta.precio)
                } else {
                    null
                }
                Text(if (coste != null) "Coste total: ${Textos.dinero(coste)}" else "Indica una cantidad entera positiva.")
                Button(
                    onClick = { vm.comprar(oferta, cantidad ?: 0) },
                    enabled = coste != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Confirmar compra") }
            }
            MensajeUi(mensaje)
        }
    }
}

/** RF-08: primer nivel de la jerarquía. */
@Composable
fun PantallaHabitats(alVolver: () -> Unit, alHabitat: (Ref) -> Unit) {
    val c = LocalContenedor.current
    val vm: HabitatsViewModel = viewModel(factory = fabrica { HabitatsViewModel(c) })
    val habitats by vm.habitats.collectAsState()
    Marco("Hábitats", alVolver) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(habitats) { h ->
                Card(Modifier.fillMaxWidth().clickable(enabled = h.disponible) { alHabitat(h.ref) }) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(h.nombre)
                        Text(h.estado)
                    }
                }
            }
        }
    }
}

/** RF-08: segundo nivel, espacios de un hábitat. El tercer nivel (animales) llega en el Sprint 3. */
@Composable
fun PantallaEspacios(habitat: Ref, alVolver: () -> Unit, alComprar: () -> Unit, alConfigurar: (Ref) -> Unit) {
    val c = LocalContenedor.current
    val vm: EspaciosViewModel = viewModel(key = "espacios-${habitat.valor}", factory = fabrica { EspaciosViewModel(c, habitat) })
    val espacios by vm.espacios.collectAsState()
    val nombre by vm.nombreHabitat.collectAsState()
    Marco(nombre.ifEmpty { "Espacios" }, alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = alComprar, modifier = Modifier.fillMaxWidth()) { Text("Comprar espacio") }
            if (espacios.isEmpty()) Text("Este hábitat aún no tiene espacios.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(espacios) { e ->
                    Card(Modifier.fillMaxWidth().clickable(enabled = e.activo) { alConfigurar(e.ref) }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(e.nombre, style = MaterialTheme.typography.titleMedium)
                            Text(e.detalle)
                        }
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
            Fila("Saldo disponible", saldo)
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Denominación del espacio") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Capacidad", style = MaterialTheme.typography.titleMedium)
            capacidades.forEach { o ->
                FilterChip(selected = capacidad == o.capacidad, onClick = { capacidad = o.capacidad }, label = { Text(o.etiqueta) }, modifier = Modifier.fillMaxWidth())
            }
            Button(
                onClick = { capacidad?.let { vm.comprar(nombre, it) } },
                enabled = capacidad != null && nombre.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Confirmar compra") }
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
            actual?.let { Text(it.detalle) }
            Text("La capacidad no se puede modificar.")
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Denominación") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Hábitat", style = MaterialTheme.typography.titleMedium)
            habitats.forEach { h ->
                FilterChip(selected = destino == h.ref, onClick = { destino = h.ref }, label = { Text(h.nombre) }, modifier = Modifier.fillMaxWidth())
            }
            Button(
                onClick = { vm.guardar(nombre, destino) },
                enabled = nombre.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar") }
            MensajeUi(mensaje)
        }
    }
}
