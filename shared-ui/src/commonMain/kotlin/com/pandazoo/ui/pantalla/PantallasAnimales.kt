package com.pandazoo.ui.pantalla

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pandazoo.domain.animal.ReglasAnimal
import com.pandazoo.domain.animal.ReglasCuidado

// ---------------------------------------------------------------------------------------------------- Componentes

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Etiquetas(opciones: List<OpcionUi>, seleccion: Set<String>, maximo: Int, alAlternar: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        opciones.forEach { o ->
            val elegida = o.ref in seleccion
            FilterChip(
                selected = elegida,
                onClick = { alAlternar(o.ref) },
                enabled = elegida || seleccion.size < maximo,
                label = { Text(o.nombre) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

/** Elección única entre un hueco explícito («Sin pareja») y los candidatos. */
@Composable
private fun Elegir(titulo: String, sin: String, candidatos: List<CandidatoUi>, elegido: Ref?, alElegir: (Ref?) -> Unit) {
    Text(titulo, style = MaterialTheme.typography.titleMedium)
    Opcion(elegido == null, sin) { alElegir(null) }
    candidatos.forEach { cand -> Opcion(elegido == cand.ref, cand.etiqueta) { alElegir(cand.ref) } }
}

@Composable
private fun Confirmacion(
    titulo: String,
    texto: String,
    confirmar: String,
    cancelar: String,
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = alConfirmar) { Text(confirmar) } },
        dismissButton = { TextButton(onClick = alCancelar) { Text(cancelar) } },
    )
}

// ---------------------------------------------------------------------------------------------------- CU-01

/** CU-01, paso 2: escoger la especie entre las disponibles en la tienda de animales. */
@Composable
fun PantallaTiendaAnimales(alVolver: () -> Unit, alElegir: (Ref) -> Unit) {
    val c = LocalContenedor.current
    val vm: TiendaAnimalesViewModel = viewModel(factory = fabrica { TiendaAnimalesViewModel(c) })
    val ofertas by vm.ofertas.collectAsState()
    val saldo by vm.saldo.collectAsState()
    Marco("Tienda de animales", alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FichaSaldo(saldo)
            if (ofertas.none { it.seleccionable }) AvisoUi("No hay animales que puedas comprar ahora.", TipoAviso.ADVERTENCIA)
            ofertas.forEach { o ->
                Ficha(
                    Modifier.clickable(enabled = o.seleccionable) { alElegir(o.ref) },
                    filete = if (o.seleccionable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    atenuada = !o.seleccionable,
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(o.nombre, style = MaterialTheme.typography.titleMedium)
                        Text(if (o.bloqueada) "—" else o.coste, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(o.detalle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** CU-01, pasos 3 a 7: hábitat, espacio con cupo, ficha técnica y confirmación. */
@Composable
fun PantallaComprarAnimal(especie: Ref, alVolver: () -> Unit) {
    val c = LocalContenedor.current
    val vm: ComprarAnimalViewModel = viewModel(key = "comprar-animal-${especie.valor}", factory = fabrica { ComprarAnimalViewModel(c, especie) })
    val oferta by vm.oferta.collectAsState()
    val saldo by vm.saldo.collectAsState()
    val habitats by vm.habitats.collectAsState()
    val espacios by vm.espacios.collectAsState()
    val habitatElegido by vm.habitatElegido.collectAsState()
    val espacioElegido by vm.espacioElegido.collectAsState()
    val parejas by vm.parejasPosibles.collectAsState()
    val companeros by vm.companeros.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    val comprado by vm.comprado.collectAsState()
    var nombre by rememberSaveable { mutableStateOf("") }
    var habilidades by remember { mutableStateOf(emptySet<String>()) }
    var rasgos by remember { mutableStateOf(emptySet<String>()) }
    var pareja by remember(espacioElegido) { mutableStateOf<Ref?>(null) }
    var amigo by remember(espacioElegido) { mutableStateOf<Ref?>(null) }
    LaunchedEffect(comprado) { if (comprado) alVolver() }

    fun alternar(set: Set<String>, v: String) = if (v in set) set - v else set + v

    Marco(oferta?.let { "Comprar ${it.nombre}" } ?: "Comprar animal", alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FichaSaldo(saldo)
            oferta?.let { o ->
                Ficha(filete = MaterialTheme.colorScheme.secondary) {
                    Fila("Especie", o.nombre)
                    Fila("Rango", o.rango)
                    Fila("Coste", o.coste)
                }
                if (!o.seleccionable) AvisoUi("Esta especie no se puede comprar ahora: ${o.estado.lowercase()}.", TipoAviso.ADVERTENCIA)
            }

            Text("Hábitat", style = MaterialTheme.typography.titleMedium)
            if (habitats.isEmpty()) {
                AvisoUi("No hay ningún espacio con capacidad libre. Compra un espacio antes de adquirir animales.", TipoAviso.ADVERTENCIA)
            }
            habitats.forEach { h -> Opcion(habitatElegido == h.ref, h.nombre) { vm.elegirHabitat(h.ref) } }

            if (habitatElegido != null) {
                Text("Espacio", style = MaterialTheme.typography.titleMedium)
                espacios.forEach { e -> Opcion(espacioElegido == e.ref, "${e.nombre} — ${e.detalle}") { vm.elegirEspacio(e.ref) } }
            }

            if (espacioElegido != null) {
                Text("Ficha técnica", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre del animal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Habilidades sociales (elige 2)", style = MaterialTheme.typography.labelLarge)
                Etiquetas(vm.habilidades, habilidades, ReglasAnimal.HABILIDADES_POR_FICHA) { habilidades = alternar(habilidades, it) }
                Text("Rasgos de personalidad (elige 2)", style = MaterialTheme.typography.labelLarge)
                Etiquetas(vm.rasgos, rasgos, ReglasAnimal.RASGOS_POR_FICHA) { rasgos = alternar(rasgos, it) }
                Elegir("Pareja", "Sin pareja", parejas, pareja) { pareja = it }
                Elegir("Mejor amigo", "Sin mejor amigo", companeros, amigo) { amigo = it }
                BotonPrincipal(
                    "Confirmar compra",
                    { vm.comprar(nombre, habilidades, rasgos, pareja, amigo) },
                    enabled = oferta?.seleccionable == true && nombre.isNotBlank() &&
                        habilidades.size == ReglasAnimal.HABILIDADES_POR_FICHA && rasgos.size == ReglasAnimal.RASGOS_POR_FICHA,
                )
            }
            MensajeUi(mensaje)
        }
    }
}

// ---------------------------------------------------------------------------------------------------- Espacio y animales

/** RF-08, tercer nivel: animales de un espacio, con acceso a su configuración y a la baja del espacio vacío. */
@Composable
fun PantallaAnimales(espacio: Ref, alVolver: () -> Unit, alAnimal: (Ref) -> Unit, alConfigurar: () -> Unit) {
    val c = LocalContenedor.current
    val vm: AnimalesViewModel = viewModel(key = "animales-${espacio.valor}", factory = fabrica { AnimalesViewModel(c, espacio) })
    val actual by vm.actual.collectAsState()
    val animales by vm.animales.collectAsState()
    val ofreciendoBaja by vm.ofreciendoBaja.collectAsState()
    val dadoDeBaja by vm.dadoDeBaja.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    val grupal by vm.confirmandoGrupal.collectAsState()
    LaunchedEffect(dadoDeBaja) { if (dadoDeBaja) alVolver() }
    Marco(actual?.nombre ?: "Espacio", alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actual?.let { Ficha(filete = MaterialTheme.colorScheme.tertiary) { Text(it.detalle) } }
            if (actual?.activo == true) {
                OutlinedButton(onClick = alConfigurar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Configurar espacio") }
            }
            if (animales.isEmpty()) Text("Este espacio no tiene animales.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (animales.isNotEmpty()) {
                val hayHambre = animales.any { it.necesitaAlimento }
                BotonPrincipal("Alimentar a todos", vm::pedirAlimentarGrupo, enabled = hayHambre)
                if (!hayHambre) Text("Todos los animales están al 100 % de alimentación.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            animales.forEach { a ->
                val tono = tonoRango(a.rango)
                Ficha(Modifier.clickable { alAnimal(a.ref) }, filete = tono.filete, fondo = tono.contenedor) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(a.nombre, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            a.especie,
                            style = MaterialTheme.typography.labelMedium,
                            fontStyle = FontStyle.Italic,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    Text(a.detalle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (actual?.activo == true && actual?.ocupacion == 0) {
                OutlinedButton(onClick = vm::pedirBaja, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Dar de baja el espacio") }
            }
            MensajeUi(mensaje)
        }
    }
    grupal?.let {
        Confirmacion(
            titulo = "Alimentar a todos",
            texto = it.texto,
            confirmar = "Confirmar alimentación",
            cancelar = "Cancelar",
            alConfirmar = vm::confirmarGrupal,
            alCancelar = vm::cancelarGrupal,
        )
    }
    if (ofreciendoBaja) {
        Confirmacion(
            titulo = "Dar de baja el espacio",
            texto = "La baja es lógica: el espacio deja de ofrecerse y se conserva su histórico. ¿Confirmas la baja?",
            confirmar = "Confirmar baja",
            cancelar = "Cancelar",
            alConfirmar = vm::confirmarBaja,
            alCancelar = vm::cancelarBaja,
        )
    }
}

// ---------------------------------------------------------------------------------------------------- Ficha y CU-06

/** Ficha técnica (RF-25) sin campos editables y con las acciones de editar, trasladar y vender. */
@Composable
fun PantallaFichaAnimal(animal: Ref, alVolver: () -> Unit, alEditar: () -> Unit, alReubicar: () -> Unit) {
    val c = LocalContenedor.current
    val vm: FichaAnimalViewModel = viewModel(key = "ficha-${animal.valor}", factory = fabrica { FichaAnimalViewModel(c, animal) })
    val ficha by vm.ficha.collectAsState()
    val confirmando by vm.confirmandoVenta.collectAsState()
    val ofertaBaja by vm.ofertaBaja.collectAsState()
    val terminado by vm.terminado.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    val alimentando by vm.confirmandoAlimentar.collectAsState()
    val tratando by vm.confirmandoTratar.collectAsState()
    LaunchedEffect(terminado) { if (terminado) alVolver() }
    Marco(ficha?.nombre ?: "Ficha del animal", alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val f = ficha
            if (f == null) {
                Text("El animal ya no está en el zoo.", color = MaterialTheme.colorScheme.onBackground)
            } else {
                val tono = tonoRango(f.rango)
                Ficha(filete = tono.filete, fondo = tono.contenedor) { f.lineas.forEach { Fila(it.etiqueta, it.valor) } }
                Ficha(filete = MaterialTheme.colorScheme.secondary) {
                    TituloFicha("Habilidades sociales")
                    Text(f.habilidades.joinToString(", ").ifEmpty { "Sin habilidades" })
                    TituloFicha("Rasgos de personalidad")
                    Text(f.rasgos.joinToString(", ").ifEmpty { "Sin rasgos" })
                }
                if (f.vivo) {
                    BotonPrincipal("Alimentar", vm::pedirAlimentar, enabled = f.alimentacion < ReglasCuidado.ALIMENTACION_MAXIMA)
                    if (f.alimentacion >= ReglasCuidado.ALIMENTACION_MAXIMA) {
                        Text("La alimentación ya está al 100 %.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (f.enfermo) BotonPrincipal("Aplicar tratamiento", vm::pedirTratamiento)
                    BotonPrincipal("Editar ficha", alEditar)
                    BotonPrincipal("Trasladar", alReubicar)
                    val puedeVender = !f.enfermo && f.alimentacion > 0
                    BotonPrincipal("Vender", vm::pedirVenta, enabled = puedeVender)
                    if (f.enfermo) Text("Un animal enfermo no se puede vender.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (f.alimentacion == 0) Text("Un animal sin alimentación no se puede vender.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            MensajeUi(mensaje)
        }
    }
    alimentando?.let {
        Confirmacion(
            titulo = "Alimentar",
            texto = it,
            confirmar = "Confirmar alimentación",
            cancelar = "Cancelar",
            alConfirmar = vm::alimentar,
            alCancelar = vm::cancelarAlimentar,
        )
    }
    tratando?.let {
        Confirmacion(
            titulo = "Aplicar tratamiento",
            texto = it,
            confirmar = "Confirmar tratamiento",
            cancelar = "Cancelar",
            alConfirmar = vm::tratar,
            alCancelar = vm::cancelarTratamiento,
        )
    }
    if (confirmando) {
        Confirmacion(
            titulo = "Vender animal",
            texto = "Vas a vender a ${ficha?.nombre ?: "este animal"}. La venta no acredita fondos y no se puede deshacer.",
            confirmar = "Confirmar venta",
            cancelar = "Cancelar",
            alConfirmar = vm::vender,
            alCancelar = vm::cancelarVenta,
        )
    }
    ofertaBaja?.let { origen ->
        Confirmacion(
            titulo = "Espacio vacío",
            texto = "El espacio ha quedado sin animales. ¿Quieres darlo de baja?",
            confirmar = "Dar de baja",
            cancelar = "Mantener",
            alConfirmar = { vm.darDeBaja(origen) },
            alCancelar = vm::mantenerEspacio,
        )
    }
}

// ---------------------------------------------------------------------------------------------------- CU-05

/** CU-05: solo se editan el nombre, las habilidades, los rasgos, la pareja y el mejor amigo. */
@Composable
fun PantallaEditarFicha(animal: Ref, alVolver: () -> Unit) {
    val c = LocalContenedor.current
    val vm: EditarFichaViewModel = viewModel(key = "editar-${animal.valor}", factory = fabrica { EditarFichaViewModel(c, animal) })
    val ficha by vm.ficha.collectAsState()
    val candidatos by vm.candidatos.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    val guardado by vm.guardado.collectAsState()
    LaunchedEffect(guardado) { if (guardado) alVolver() }
    Marco("Editar ficha", alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val f = ficha
            if (f == null) {
                Text("Cargando…", color = MaterialTheme.colorScheme.onBackground)
            } else {
                FormularioFicha(vm, f, candidatos, mensaje)
            }
        }
    }
}

@Composable
private fun FormularioFicha(vm: EditarFichaViewModel, f: FichaAnimalUi, candidatos: CandidatosUi, mensaje: Mensaje?) {
    var nombre by rememberSaveable { mutableStateOf(f.nombre) }
    var habilidades by remember { mutableStateOf(f.idHabilidades.toSet()) }
    var rasgos by remember { mutableStateOf(f.idRasgos.toSet()) }
    var pareja by remember { mutableStateOf(f.idPareja?.let(::Ref)) }
    var amigo by remember { mutableStateOf(f.idMejorAmigo?.let(::Ref)) }

    fun alternar(set: Set<String>, v: String) = if (v in set) set - v else set + v

    // La pareja o el amigo actual puede estar en otro espacio tras un traslado: se mantiene como opción.
    val parejas = candidatos.parejas.withActual(f.idPareja, f.nombrePareja)
    val amigos = candidatos.amigos.withActual(f.idMejorAmigo, f.nombreMejorAmigo)

    Text("Especie, edad, alimentación, salud y satisfacción no se pueden editar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = nombre,
        onValueChange = { nombre = it },
        label = { Text("Nombre") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Habilidades sociales (elige 2)", style = MaterialTheme.typography.labelLarge)
    Etiquetas(vm.habilidades, habilidades, ReglasAnimal.HABILIDADES_POR_FICHA) { habilidades = alternar(habilidades, it) }
    Text("Rasgos de personalidad (elige 2)", style = MaterialTheme.typography.labelLarge)
    Etiquetas(vm.rasgos, rasgos, ReglasAnimal.RASGOS_POR_FICHA) { rasgos = alternar(rasgos, it) }
    Elegir("Pareja", "Sin pareja", parejas, pareja) { pareja = it }
    Elegir("Mejor amigo", "Sin mejor amigo", amigos, amigo) { amigo = it }
    BotonPrincipal(
        "Guardar",
        { vm.guardar(nombre, habilidades, rasgos, pareja, amigo) },
        enabled = nombre.isNotBlank() &&
            habilidades.size == ReglasAnimal.HABILIDADES_POR_FICHA && rasgos.size == ReglasAnimal.RASGOS_POR_FICHA,
    )
    MensajeUi(mensaje)
}

private fun List<CandidatoUi>.withActual(id: String?, nombre: String?): List<CandidatoUi> =
    if (id == null || nombre == null || any { it.ref.valor == id }) this else this + CandidatoUi(Ref(id), "$nombre (actual)")

// ---------------------------------------------------------------------------------------------------- CU-04

/** CU-04: elegir un espacio de destino activo con capacidad libre. */
@Composable
fun PantallaReubicar(animal: Ref, alVolver: () -> Unit) {
    val c = LocalContenedor.current
    val vm: ReubicarViewModel = viewModel(key = "reubicar-${animal.valor}", factory = fabrica { ReubicarViewModel(c, animal) })
    val nombre by vm.nombre.collectAsState()
    val destinos by vm.destinos.collectAsState()
    val ofertaBaja by vm.ofertaBaja.collectAsState()
    val terminado by vm.terminado.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    var destino by remember { mutableStateOf<Ref?>(null) }
    LaunchedEffect(terminado) { if (terminado) alVolver() }
    Marco("Trasladar a $nombre".trim(), alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Espacio de destino", style = MaterialTheme.typography.titleMedium)
            if (destinos.isEmpty()) AvisoUi("No hay otro espacio activo con capacidad libre.", TipoAviso.ADVERTENCIA)
            destinos.forEach { d -> Opcion(destino == d.ref, d.etiqueta) { destino = d.ref } }
            BotonPrincipal("Confirmar traslado", { destino?.let(vm::reubicar) }, enabled = destino != null)
            MensajeUi(mensaje)
        }
    }
    ofertaBaja?.let { origen ->
        Confirmacion(
            titulo = "Espacio vacío",
            texto = "El espacio de origen ha quedado sin animales. ¿Quieres darlo de baja?",
            confirmar = "Dar de baja",
            cancelar = "Mantener",
            alConfirmar = { vm.darDeBaja(origen) },
            alCancelar = vm::mantenerEspacio,
        )
    }
}

/** CU-10: concepción notificada y ficha de la cría. La acción se deshabilita, con su motivo, si ya no puede aceptarse. */
@Composable
fun PantallaRegistrarCria(aviso: Ref, alVolver: () -> Unit) {
    val c = LocalContenedor.current
    val vm: CriaViewModel = viewModel(key = "cria-${aviso.valor}", factory = fabrica { CriaViewModel(c, aviso) })
    val detalle by vm.detalle.collectAsState()
    val companeros by vm.companeros.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    val registrada by vm.registrada.collectAsState()
    var nombre by rememberSaveable { mutableStateOf("") }
    var habilidades by remember { mutableStateOf(emptySet<String>()) }
    var rasgos by remember { mutableStateOf(emptySet<String>()) }
    var amigo by remember { mutableStateOf<Ref?>(null) }
    LaunchedEffect(registrada) { if (registrada) alVolver() }

    fun alternar(set: Set<String>, v: String) = if (v in set) set - v else set + v

    Marco("Registrar cría", alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val d = detalle
            if (d == null) {
                AvisoUi("Esta cría ya no está disponible.", TipoAviso.ADVERTENCIA)
                return@Column
            }
            Ficha(filete = MaterialTheme.colorScheme.secondary) {
                Fila("Especie", d.especie)
                Fila("Progenitores", "${d.nombreA} y ${d.nombreB}")
                Fila("Espacio", d.nombreEspacio ?: "Sin espacio")
            }
            d.motivoDeshabilitada?.let { AvisoUi(Textos.motivo(it), TipoAviso.ADVERTENCIA) }
            Text("Ficha técnica de la cría", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre de la cría") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Habilidades sociales (elige 2)", style = MaterialTheme.typography.labelLarge)
            Etiquetas(vm.habilidades, habilidades, ReglasAnimal.HABILIDADES_POR_FICHA) { habilidades = alternar(habilidades, it) }
            Text("Rasgos de personalidad (elige 2)", style = MaterialTheme.typography.labelLarge)
            Etiquetas(vm.rasgos, rasgos, ReglasAnimal.RASGOS_POR_FICHA) { rasgos = alternar(rasgos, it) }
            Elegir("Mejor amigo", "Sin mejor amigo", companeros, amigo) { amigo = it }
            BotonPrincipal(
                "Confirmar registro",
                { vm.registrar(nombre, habilidades, rasgos, amigo) },
                enabled = d.habilitada && nombre.isNotBlank() &&
                    habilidades.size == ReglasAnimal.HABILIDADES_POR_FICHA && rasgos.size == ReglasAnimal.RASGOS_POR_FICHA,
            )
            MensajeUi(mensaje)
        }
    }
}
