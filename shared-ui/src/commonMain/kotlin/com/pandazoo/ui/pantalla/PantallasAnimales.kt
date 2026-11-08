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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.pandazoo.domain.animal.ReglasNivel

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

/** Ventana de confirmación sin cifras: solo icono, título y los dos botones. */
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
                val tono = tonoRango(o.rango)
                Ficha(
                    Modifier.clickable(enabled = o.seleccionable) { alElegir(o.ref) },
                    filete = tono.filete,
                    fondo = tono.contenedor,
                    atenuada = !o.seleccionable,
                    rango = o.rango,
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        NombreRango(o.nombre, o.rango, modifier = Modifier.weight(1f))
                        Text(if (o.exclusiva) "—" else o.coste, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
                    }
                    if (o.detalle.isNotEmpty()) Text(o.detalle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** CU-01, pasos 3 a 7: hábitat, espacio con cupo, ficha técnica y confirmación. */
@Composable
fun PantallaComprarAnimal(especie: Ref, espacioFijo: Ref? = null, alVolver: () -> Unit) {
    val c = LocalContenedor.current
    val vm: ComprarAnimalViewModel = viewModel(key = "comprar-animal-${especie.valor}-${espacioFijo?.valor}", factory = fabrica { ComprarAnimalViewModel(c, especie, espacioFijo) })
    val ubicacionFija by vm.ubicacionFija.collectAsState()
    val oferta by vm.oferta.collectAsState()
    val saldo by vm.saldo.collectAsState()
    val habitats by vm.habitats.collectAsState()
    val espacios by vm.espacios.collectAsState()
    val habitatElegido by vm.habitatElegido.collectAsState()
    val espacioElegido by vm.espacioElegido.collectAsState()
    val companeros by vm.companeros.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    val comprado by vm.comprado.collectAsState()
    var nombre by rememberSaveable { mutableStateOf("") }
    var habilidades by remember { mutableStateOf(emptySet<String>()) }
    var rasgos by remember { mutableStateOf(emptySet<String>()) }
    var amigo by remember(espacioElegido) { mutableStateOf<Ref?>(null) }
    LaunchedEffect(comprado) { if (comprado) alVolver() }

    fun alternar(set: Set<String>, v: String) = if (v in set) set - v else set + v

    Marco(oferta?.let { "Comprar ${it.nombre}" } ?: "Comprar animal", alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FichaSaldo(saldo)
            oferta?.let { o ->
                val tono = tonoRango(o.rango)
                Ficha(filete = tono.filete, fondo = tono.contenedor, rango = o.rango) {
                    Fila("Especie", o.nombre)
                    Fila("Rango", o.rango)
                    Fila("Coste", o.coste)
                }
                if (!o.seleccionable) AvisoUi("Esta especie no se puede comprar ahora: ${o.estado.lowercase()}.", TipoAviso.ADVERTENCIA)
            }

            if (espacioFijo != null) {
                // Compra desde un espacio: hábitat y espacio ya vienen escritos y no se pueden cambiar.
                ubicacionFija?.let { u ->
                    Ficha(filete = MaterialTheme.colorScheme.tertiary) {
                        Fila("Hábitat", u.habitat)
                        Fila("Espacio", u.espacio)
                    }
                }
            } else {
                Text("Hábitat", style = MaterialTheme.typography.titleMedium)
                if (habitats.isEmpty()) {
                    AvisoUi("No hay ningún espacio con capacidad libre. Compra un espacio antes de adquirir animales.", TipoAviso.ADVERTENCIA)
                }
                habitats.forEach { h -> Opcion(habitatElegido == h.ref, h.nombre) { vm.elegirHabitat(h.ref) } }

                if (habitatElegido != null) {
                    Text("Espacio", style = MaterialTheme.typography.titleMedium)
                    espacios.forEach { e -> Opcion(espacioElegido == e.ref, "${e.nombre} — ${e.detalle}") { vm.elegirEspacio(e.ref) } }
                }
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
                Elegir("Mejor amigo", "Sin mejor amigo", companeros, amigo) { amigo = it }
                Text(
                    "La pareja se podrá asignar más adelante, cuando el animal alcance el nivel ${ReglasNivel.NIVEL_MINIMO_PAREJA}.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BotonPrincipal(
                    "Confirmar compra",
                    { vm.comprar(nombre, habilidades, rasgos, amigo) },
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
fun PantallaAnimales(espacio: Ref, alVolver: () -> Unit, alAnimal: (Ref) -> Unit, alConfigurar: () -> Unit, alComprar: () -> Unit) {
    val c = LocalContenedor.current
    val vm: AnimalesViewModel = viewModel(key = "animales-${espacio.valor}", factory = fabrica { AnimalesViewModel(c, espacio) })
    val actual by vm.actual.collectAsState()
    val animales by vm.animales.collectAsState()
    val ofreciendoBaja by vm.ofreciendoBaja.collectAsState()
    val reembolso by vm.reembolso.collectAsState()
    val dadoDeBaja by vm.dadoDeBaja.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    val grupal by vm.confirmandoGrupal.collectAsState()
    LaunchedEffect(dadoDeBaja) { if (dadoDeBaja) alVolver() }
    Marco(actual?.nombre ?: "Espacio", alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actual?.let { Ficha(filete = MaterialTheme.colorScheme.tertiary) { OcupacionEspacio(it) } }
            if (actual?.activo == true) {
                OutlinedButton(onClick = alConfigurar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Configurar espacio") }
            }
            if (actual?.activo == true && actual?.conCupo == true) {
                BotonPrincipal("Comprar un animal para este espacio", alComprar)
            }
            if (animales.isNotEmpty()) {
                val hayHambre = animales.any { it.necesitaAlimento }
                BotonPrincipal("Alimentar a todos", vm::pedirAlimentarGrupo, enabled = hayHambre)
            }
            animales.forEach { a ->
                val tono = tonoRango(a.rango)
                Ficha(Modifier.clickable { alAnimal(a.ref) }, filete = tono.filete, fondo = tono.contenedor, rango = a.rango) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NombreRango(a.nombre, a.rango)
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
                BotonPrincipal("Vender espacio", vm::pedirBaja)
            }
            MensajeUi(mensaje)
        }
    }
    grupal?.let {
        VentanaFlotante(it.ventana, alConfirmar = vm::confirmarGrupal, alCancelar = vm::cancelarGrupal)
    }
    if (ofreciendoBaja) {
        VentanaFlotante(
            VentanaUi(
                icono = Iconos.Papelera,
                titulo = "Vender espacio",
                datos = listOf(DatoVentana(Textos.dinero(reembolso), "recuperas", Iconos.Saldo)),
                confirmar = "Confirmar venta del espacio",
                peligro = true,
            ),
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
    val terminado by vm.terminado.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    val estadisticas by vm.estadisticas.collectAsState()
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
                Ficha(filete = tono.filete, fondo = tono.contenedor, rango = f.rango) {
                    NombreRango("${f.nombre} · Nivel ${f.nivel}", f.rango, MaterialTheme.typography.headlineSmall)
                    f.lineas.forEach { Fila(it.etiqueta, it.valor) }
                }
                // [simplificar-2] Sin habilidades ni rasgos no se muestra la tarjeta ni sus títulos.
                if (f.habilidades.isNotEmpty() || f.rasgos.isNotEmpty()) {
                    Ficha(filete = MaterialTheme.colorScheme.secondary) {
                        if (f.habilidades.isNotEmpty()) {
                            TituloFicha("Habilidades sociales")
                            Text(f.habilidades.joinToString(", "))
                        }
                        if (f.rasgos.isNotEmpty()) {
                            TituloFicha("Rasgos de personalidad")
                            Text(f.rasgos.joinToString(", "))
                        }
                    }
                }
                if (f.vivo) {
                    // [simplificar-6] Las afinidades se muestran directamente, sin botón desplegable.
                    estadisticas?.let { est ->
                        Ficha(filete = MaterialTheme.colorScheme.tertiary) {
                            est.satisfaccion.forEach { Fila(it.etiqueta, it.valor) }
                        }
                    }
                    BotonPrincipal("Alimentar", vm::pedirAlimentar, enabled = f.alimentacion < ReglasCuidado.ALIMENTACION_MAXIMA)
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
        VentanaFlotante(it, alConfirmar = vm::alimentar, alCancelar = vm::cancelarAlimentar)
    }
    tratando?.let {
        VentanaFlotante(it, alConfirmar = vm::tratar, alCancelar = vm::cancelarTratamiento)
    }
    if (confirmando) {
        ficha?.let { VentanaFlotante(it.ventanaVenta, alConfirmar = vm::vender, alCancelar = vm::cancelarVenta) }
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

    Text("Especie, nivel, alimentación, salud y satisfacción no se pueden editar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val terminado by vm.terminado.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    var destino by remember { mutableStateOf<Ref?>(null) }
    var abiertos by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(terminado) { if (terminado) alVolver() }
    Marco("Trasladar a $nombre".trim(), alVolver) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Espacio de destino", style = MaterialTheme.typography.titleMedium)
            if (destinos.isEmpty()) AvisoUi("No hay otro espacio activo con capacidad libre.", TipoAviso.ADVERTENCIA)
            // Como el monitor de satisfacción: hábitat desplegable y, dentro, sus espacios con la capacidad.
            destinos.groupBy { it.habitat }.forEach { (habitat, espacios) ->
                val abierto = habitat in abiertos
                Ficha(Modifier.clickable { abiertos = if (abierto) abiertos - habitat else abiertos + habitat }, filete = MaterialTheme.colorScheme.primary) {
                    FilaNodo(abierto, habitat, "${espacios.sumOf { it.capacidad - it.ocupacion }} plazas libres", true)
                }
                if (abierto) espacios.forEach { d ->
                    val elegido = destino == d.ref
                    Ficha(
                        Modifier.padding(start = 16.dp).clickable { destino = d.ref },
                        filete = if (elegido) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                        fondo = if (elegido) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    ) {
                        FilaNodo(false, d.espacio, "${d.ocupacion}/${d.capacidad}", false)
                    }
                }
            }
            BotonPrincipal("Confirmar traslado", { destino?.let(vm::reubicar) }, enabled = destino != null)
            MensajeUi(mensaje)
        }
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
