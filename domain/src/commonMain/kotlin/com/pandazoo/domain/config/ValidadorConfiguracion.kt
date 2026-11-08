package com.pandazoo.domain.config

import com.pandazoo.domain.economia.Decimales
import com.pandazoo.domain.economia.Redondeo
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** La configuración no es completa o coherente; impide crear una partida. */
class ConfiguracionInvalidaException(val errores: List<String>) :
    RuntimeException("Configuración inválida: " + errores.joinToString("; "))

/**
 * Valida de forma exhaustiva `valores_y_pesos.json` antes de crear una partida
 * (`EspecificacionBBDD.md` 2.4, `design.md` 4.2). Devuelve la lista de errores; vacía si es válida.
 */
object ValidadorConfiguracion {
    private val CAPACIDADES_ESPACIO = setOf(6, 8, 10, 15, 20, 25, 30)
    private val NIVELES_ALIMENTACION = (0..100 step 10).map { it.toString() }.toSet()
    private val OPERACIONES = setOf("SUMAR", "RESTAR", "MULTIPLICAR", "DIVIDIR")
    private val TIPOS_RECOMPENSA = setOf("FONDOS", "ALIMENTO", "INSUMO", "ESPECIE")
    const val ALIMENTO_BAMBU = "bambu"

    /** Tope aplicable a un parámetro de nivel cuando la misión no está ligada a una especie. */
    private const val NIVEL_MAXIMO_SIN_ESPECIE = 60

    fun validarOExcepcion(cfg: ConfiguracionJuego) {
        val errores = validar(cfg)
        if (errores.isNotEmpty()) throw ConfiguracionInvalidaException(errores)
    }

    fun validar(cfg: ConfiguracionJuego): List<String> {
        val e = mutableListOf<String>()
        validarCatalogos(cfg, e)
        validarRangosYCostes(cfg, e)
        validarEspecies(cfg, e)
        validarAfinidad(cfg, e)
        validarCompatibilidad(cfg, e)
        validarMcrit(cfg, e)
        validarDesbloqueos(cfg, e)
        validarPrestigio(cfg, e)
        validarRecompensasAnimales(cfg, e)
        validarMisiones(cfg, e)
        validarRetencion(cfg, e)
        validarPartidaInicial(cfg, e)
        return e
    }

    private const val ESCALA_SUMA = 9

    /** Suma exacta de decimales, redondeada a centésimas con los medios hacia arriba; `100` equivale a `1.00`. */
    private fun sumaCentesimas(vararg valores: Double): Long {
        val total = valores.sumOf { Decimales.redondearEscalado(it, ESCALA_SUMA) }
        val unidadCentesima = Decimales.potencia10(ESCALA_SUMA - 2)
        return (total + unidadCentesima / 2) / unidadCentesima
    }

    private fun unico(ids: List<String>, nombre: String, e: MutableList<String>) {
        val repetidos = ids.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        if (repetidos.isNotEmpty()) e += "$nombre repetidos: $repetidos"
    }

    private fun enUnidad(v: Double) = v in 0.0..1.0

    private fun importeValido(v: Double) = v >= 0.0 && Decimales.decimales(v) <= 2

    private fun validarCatalogos(cfg: ConfiguracionJuego, e: MutableList<String>) {
        val c = cfg.catalogos
        unico(c.biomas.map { it.id }, "biomas", e)
        unico(c.habitats.map { it.codigo }, "hábitats", e)
        unico(c.habitats.map { it.nombre }, "nombres de hábitat", e)
        unico(c.alimentos.map { it.id }, "alimentos", e)
        unico(c.insumos.map { it.id }, "insumos", e)
        unico(c.rasgos.map { it.id }, "rasgos", e)
        unico(c.habilidades.map { it.id }, "habilidades", e)
        val biomas = c.biomas.map { it.id }.toSet()
        if (biomas.isEmpty()) e += "no hay biomas"
        c.habitats.filter { it.idBioma !in biomas }.forEach { e += "hábitat ${it.codigo}: bioma inexistente ${it.idBioma}" }
        if (c.habitats.map { it.idBioma }.toSet() != biomas) e += "debe existir exactamente un hábitat por bioma"
        if (c.alimentos.none { it.id == ALIMENTO_BAMBU }) e += "falta el alimento bambú"
        if (c.insumos.isEmpty()) e += "falta la medicina"
        if (c.rasgos.size < 2) e += "se necesitan al menos 2 rasgos de personalidad"
        if (c.habilidades.size < 2) e += "se necesitan al menos 2 habilidades sociales"
    }

    private fun validarRangosYCostes(cfg: ConfiguracionJuego, e: MutableList<String>) {
        val t = cfg.tablas
        val idsRango = t.rangos.map { it.id }
        unico(idsRango.map { it.toString() }, "rangos", e)
        if (idsRango.sorted() != (1..5).toList()) e += "los rangos deben ser exactamente 1..5"
        t.rangos.forEach { r ->
            val campos = mapOf(
                "umbral_reproduccion" to r.umbralReproduccion,
                "probabilidad_reproduccion" to r.probabilidadReproduccion,
                "penalizacion_hijo_reproduccion" to r.penalizacionHijo,
                "probabilidad_enfermedad" to r.probabilidadEnfermedad,
            )
            campos.filterValues { !enUnidad(it) }.keys.forEach { e += "rango ${r.id}: $it fuera de 0..1" }
            if (!importeValido(r.beneficioBase)) e += "rango ${r.id}: beneficio base por animal inválido"
            if (r.nivelMaximo < 2) e += "rango ${r.id}: nivel_maximo debe ser al menos 2"
            if (r.multiplicadorNivelMaximo < 1.0) e += "rango ${r.id}: multiplicador_nivel_maximo debe ser al menos 1"
            if (!importeValido(r.valorVentaBase)) e += "rango ${r.id}: valor_venta_base inválido"
            if (r.factorVentaNivelMaximo <= 1.0) e += "rango ${r.id}: factor_venta_nivel_maximo debe ser mayor que 1"
        }
        validarNivelYVenta(cfg, e)
        idsRango.forEach { id ->
            if (t.costesRangos.none { it.idRango == id }) e += "falta coste de rango $id"
            if (t.consumoMedico.none { it.idRango == id }) e += "falta consumo médico del rango $id"
        }
        t.costesRangos.filter { !importeValido(it.importe) }.forEach { e += "coste de rango ${it.idRango} inválido" }
        t.consumoMedico.filter { it.cantidad <= 0 }.forEach { e += "consumo médico rango ${it.idRango}: cantidad no positiva" }
        val insumos = cfg.catalogos.insumos.map { it.id }.toSet()
        t.consumoMedico.filter { it.idInsumo !in insumos }.forEach { e += "consumo médico: insumo inexistente ${it.idInsumo}" }
        if (t.costesEspacios.map { it.capacidad }.toSet() != CAPACIDADES_ESPACIO ||
            t.costesEspacios.size != CAPACIDADES_ESPACIO.size
        ) {
            e += "costes de espacios: las capacidades deben ser $CAPACIDADES_ESPACIO"
        }
        t.costesEspacios.filter { !importeValido(it.importe) }.forEach { e += "coste de espacio ${it.capacidad} inválido" }
        val precioBambu = t.costesAlimentos.firstOrNull { it.idAlimento == ALIMENTO_BAMBU }
        if (precioBambu == null) e += "falta el precio del bambú" else if (!importeValido(precioBambu.precio)) e += "precio del bambú inválido"
        cfg.catalogos.alimentos.filter { a -> t.costesAlimentos.none { it.idAlimento == a.id } }
            .forEach { e += "falta precio del alimento ${it.id}" }
        cfg.catalogos.insumos.filter { i -> t.costesInsumos.none { it.idInsumo == i.id } }
            .forEach { e += "falta precio del insumo ${it.id}" }
        t.costesInsumos.filter { !importeValido(it.precio) }.forEach { e += "precio de insumo ${it.idInsumo} inválido" }
    }

    /** Reglas de nivel (RF-51) y de venta (RF-52) y su coherencia con los rangos. */
    private fun validarNivelYVenta(cfg: ConfiguracionJuego, e: MutableList<String>) {
        val t = cfg.tablas
        val n = cfg.reglas.nivel
        val v = cfg.reglas.venta
        if (n.nivelInicial < 1) e += "nivel: nivel_inicial debe ser al menos 1"
        if (n.ciclosPorNivel < 1) e += "nivel: ciclos_por_nivel debe ser al menos 1"
        val menorTope = t.rangos.minOfOrNull { it.nivelMaximo }
        if (n.nivelMinimoPareja < 1 || (menorTope != null && n.nivelMinimoPareja > menorTope)) {
            e += "nivel: nivel_minimo_pareja debe estar entre 1 y el menor nivel_maximo"
        }
        if (v.factorNivel1 <= 0.0 || v.factorNivel1 >= 1.0) e += "venta: factor_venta_nivel_1 fuera de (0,1)"
        if (v.fraccionEquilibrio <= 0.5 || v.fraccionEquilibrio >= 1.0) e += "venta: fraccion_equilibrio fuera de (0.5,1)"
        t.rangos.forEach { r ->
            if (r.nivelMaximo >= 2) {
                val eq = Redondeo.entero(v.fraccionEquilibrio * r.nivelMaximo)
                if (eq < 2 || eq >= r.nivelMaximo) e += "rango ${r.id}: nivel de equilibrio $eq fuera de [2, nivel_maximo)"
            }
            val coste = t.costesRangos.firstOrNull { it.idRango == r.id }?.importe
            if (coste != null && coste > 0.0 && coste != r.valorVentaBase) {
                e += "rango ${r.id}: valor_venta_base debe ser igual al coste de compra"
            }
        }
        t.rangos.sortedBy { it.id }.zipWithNext().forEach { (a, b) ->
            if (b.valorVentaBase < a.valorVentaBase || b.factorVentaNivelMaximo < a.factorVentaNivelMaximo) {
                e += "venta: valor_venta_base y factor_venta_nivel_maximo no pueden decrecer con el rango (${b.id})"
            }
            val ventaA = a.valorVentaBase * a.factorVentaNivelMaximo
            val ventaB = b.valorVentaBase * b.factorVentaNivelMaximo
            if (ventaB < ventaA) e += "venta: el valor de venta en el tope decrece con el rango (${b.id})"
        }
    }

    private fun validarEspecies(cfg: ConfiguracionJuego, e: MutableList<String>) {
        val t = cfg.tablas
        unico(cfg.especies.map { it.id }, "especies", e)
        unico(cfg.especies.map { it.nombre }, "nombres de especie", e)
        val rangos = t.rangos.map { it.id }.toSet()
        cfg.especies.forEach { s ->
            val p = s.social.pesos
            if (sumaCentesimas(s.pesos.bio, s.pesos.soc) != 100L) e += "${s.id}: peso_bio + peso_soc distinto de 1.00"
            if (!enUnidad(s.pesos.bio) || !enUnidad(s.pesos.soc)) e += "${s.id}: pesos bio/soc fuera de 0..1"
            if (sumaCentesimas(p.pareja, p.amigo, p.padre, p.madre, p.hermanos, p.afinidad) != 100L) {
                e += "${s.id}: pesos sociales distintos de 1.00"
            }
            if (listOf(p.pareja, p.amigo, p.padre, p.madre, p.hermanos, p.afinidad).any { it < 0 }) e += "${s.id}: peso social negativo"
            if (p.afinidad <= p.pareja + p.amigo + p.padre + p.madre + p.hermanos) {
                e += "${s.id}: el peso de afinidad no supera la suma de los pesos familiares"
            }
            if (s.social.topeHermanos <= 0) e += "${s.id}: tope de hermanos no positivo"
            if (s.idRango !in rangos) e += "${s.id}: rango inexistente ${s.idRango}"
            if (s.categoria != "COMERCIAL" && s.categoria != "EXCLUSIVA") e += "${s.id}: categoría inválida ${s.categoria}"
            if (s.categoria == "EXCLUSIVA" && s.idRango != 5) e += "${s.id}: las exclusivas deben ser de rango 5"
            val alim = t.alimentosPorEspecie.filter { it.idEspecie == s.id }
            if (alim.size != 1 || alim[0].kilogramos <= 0) e += "${s.id}: falta cantidad de bambú por incremento"
        }
        val ids = cfg.especies.map { it.id }.toSet()
        t.alimentosPorEspecie.filter { it.idEspecie !in ids }.forEach { e += "alimento de especie inexistente ${it.idEspecie}" }
        t.alimentosPorEspecie.filter { it.idAlimento != ALIMENTO_BAMBU }.forEach { e += "${it.idEspecie}: solo se admite bambú" }
    }

    private fun validarAfinidad(cfg: ConfiguracionJuego, e: MutableList<String>) {
        val ids = cfg.especies.map { it.id }.sorted()
        val pares = cfg.matrizAfinidad.pares
        val claves = pares.map { it.a to it.b }
        if (claves.any { it.first > it.second }) e += "afinidad: pares no normalizados (a <= b)"
        if (claves.size != claves.toSet().size) e += "afinidad: pares duplicados"
        val esperados = ids.indices.flatMap { i -> (i..ids.lastIndex).map { j -> ids[i] to ids[j] } }.toSet()
        val faltan = esperados - claves.toSet()
        if (faltan.isNotEmpty()) e += "afinidad: faltan ${faltan.size} pares (p. ej. ${faltan.first()})"
        if ((claves.toSet() - esperados).isNotEmpty()) e += "afinidad: pares con especies inexistentes"
        pares.filter { !enUnidad(it.valor) }.forEach { e += "afinidad ${it.a}-${it.b} fuera de 0..1" }
    }

    private fun validarCompatibilidad(cfg: ConfiguracionJuego, e: MutableList<String>) {
        val c = cfg.tablas.compatibilidad
        val claves = c.map { it.idEspecie to it.idBioma }
        if (claves.size != claves.toSet().size) e += "compatibilidad: combinaciones duplicadas"
        val esperadas = cfg.especies.flatMap { s -> cfg.catalogos.biomas.map { s.id to it.id } }.toSet()
        val faltan = esperadas - claves.toSet()
        if (faltan.isNotEmpty()) e += "compatibilidad: faltan ${faltan.size} combinaciones (p. ej. ${faltan.first()})"
        if ((claves.toSet() - esperadas).isNotEmpty()) e += "compatibilidad: combinaciones con entidades inexistentes"
        c.filter { !enUnidad(it.factor) }.forEach { e += "compatibilidad ${it.idEspecie}-${it.idBioma} fuera de 0..1" }
    }

    private fun validarMcrit(cfg: ConfiguracionJuego, e: MutableList<String>) {
        val m = cfg.tablas.factoresMcrit
        cfg.especies.forEach { s ->
            val f = m.filter { it.idEspecie == s.id }
            if (f.size != 1) {
                e += "${s.id}: falta M_crit (factores de alimentación y enfermedad)"
                return@forEach
            }
            val a = f[0].alimentacion
            if (a.keys != NIVELES_ALIMENTACION) e += "${s.id}: M_alimentación debe cubrir 0,10..100"
            if (a.values.any { !enUnidad(it) }) e += "${s.id}: M_alimentación fuera de 0..1"
            if (a["100"] != 1.0) e += "${s.id}: M_alimentación(100) debe ser 1"
            val en = f[0].enfermedad
            if (en.keys != setOf("SANO", "ENFERMO")) e += "${s.id}: M_enfermedad debe tener SANO y ENFERMO"
            if (en["SANO"] != 1.0) e += "${s.id}: M_enfermedad(SANO) debe ser 1"
            if (en.values.any { !enUnidad(it) }) e += "${s.id}: M_enfermedad fuera de 0..1"
        }
    }

    private fun validarDesbloqueos(cfg: ConfiguracionJuego, e: MutableList<String>) {
        val d = cfg.tablas.desbloqueos
        val niveles = d.map { it.nivel }
        if (niveles.sorted() != (0..cfg.reglas.prestigio.nivelMaximo).toList()) e += "desbloqueos: deben existir los niveles 0..nivel_maximo"
        val especies = d.flatMap { it.especies }
        val habitats = d.flatMap { it.habitats }
        unico(especies, "especies en desbloqueos", e)
        unico(habitats, "hábitats en desbloqueos", e)
        val comerciales = cfg.especies.filter { it.categoria == "COMERCIAL" }.map { it.id }.toSet()
        if (especies.toSet() != comerciales) e += "desbloqueos: toda especie comercial (y solo ellas) debe tener un nivel"
        if (habitats.toSet() != cfg.catalogos.habitats.map { it.codigo }.toSet()) e += "desbloqueos: todo hábitat debe tener un nivel"
        if (d.none { it.nivel == 0 && it.especies.isNotEmpty() }) e += "desbloqueos: el nivel 0 debe tener especies iniciales"
    }

    private fun validarPrestigio(cfg: ConfiguracionJuego, e: MutableList<String>) {
        val p = cfg.reglas.prestigio
        if (p.umbralesNivel1.animalesVivos < 1) e += "prestigio: animales base debe ser >= 1"
        if (p.umbralesNivel1.satisfaccionGlobal <= 0.0 || p.umbralesNivel1.satisfaccionGlobal > 1.0) e += "prestigio: satisfacción base fuera de (0,1]"
        listOf(p.multiplicadores.animalesVivos, p.multiplicadores.satisfaccionGlobal).forEach {
            if (it <= 1.0 || it > 3.0) e += "prestigio: multiplicador $it fuera de (1,3]"
        }
        val n = cfg.tablas.nivelesPrestigio
        if (n.map { it.nivel } != (1..p.nivelMaximo).toList()) e += "prestigio: la tabla de niveles debe ser 1..${p.nivelMaximo}"
        n.filter { it.umbralSatisfaccion <= 0.0 || it.umbralSatisfaccion > 1.0 }
            .forEach { e += "prestigio: umbral de satisfacción del nivel ${it.nivel} fuera de (0,1]" }
        n.filter { it.umbralAnimalesComparacion != Redondeo.entero(it.umbralAnimalesSinRedondear) }
            .forEach { e += "prestigio: umbral de animales del nivel ${it.nivel} mal redondeado" }
        n.zipWithNext().filter { (a, b) -> b.umbralSatisfaccion < a.umbralSatisfaccion || b.umbralAnimalesSinRedondear < a.umbralAnimalesSinRedondear }
            .forEach { (_, b) -> e += "prestigio: los umbrales no crecen en el nivel ${b.nivel}" }
    }

    private fun validarRecompensasAnimales(cfg: ConfiguracionJuego, e: MutableList<String>) {
        val r = cfg.recompensasAnimales.especies
        if (r.sumOf { it.probabilidad } != 100) e += "recompensas de animales: las probabilidades no suman 100"
        if (r.any { it.probabilidad <= 0 }) e += "recompensas de animales: probabilidad no positiva"
        unico(r.map { it.idEspecie }, "especies de recompensa", e)
        val exclusivas = cfg.especies.filter { it.categoria == "EXCLUSIVA" }.map { it.id }.toSet()
        if (r.map { it.idEspecie }.toSet() != exclusivas) e += "recompensas de animales: debe listar exactamente las especies exclusivas"
    }

    private fun validarMisiones(cfg: ConfiguracionJuego, e: MutableList<String>) {
        val m = cfg.catalogos.misiones
        unico(m.map { it.id }, "misiones", e)
        unico(m.map { it.tipoObjetivo }, "tipos de misión", e)
        m.forEach { s ->
            if (s.estado != "ACTIVA" && s.estado != "INACTIVA") e += "misión ${s.id}: estado inválido"
            if (s.naturaleza != "ACUMULATIVO" && s.naturaleza != "DINAMICO") e += "misión ${s.id}: naturaleza inválida"
            if (s.recompensa.tipo !in TIPOS_RECOMPENSA) e += "misión ${s.id}: tipo de recompensa inválido"
            s.progresion.forEach { (nombre, p) ->
                if (nombre !in s.parametrosBase) e += "misión ${s.id}: progresión de parámetro inexistente $nombre"
                if (p.operacion !in OPERACIONES) e += "misión ${s.id}.$nombre: operación inválida"
                if (p.operacion == "DIVIDIR" && p.constante <= 0) e += "misión ${s.id}.$nombre: divisor no positivo"
                if (p.minimo > p.maximo) e += "misión ${s.id}.$nombre: mínimo mayor que máximo"
            }
            validarMisionDeNivel(cfg, s, e)
        }
    }

    /** Un parámetro `nivel_requerido` no puede superar el tope de nivel de su especie (o del mayor rango si no tiene especie). */
    private fun validarMisionDeNivel(cfg: ConfiguracionJuego, s: MisionCfg, e: MutableList<String>) {
        val prog = s.progresion["nivel_requerido"] ?: return
        val idEspecie = (s.parametrosBase["id_especie"] as? JsonPrimitive)?.contentOrNull
        val tope = if (idEspecie != null) {
            val rango = cfg.especies.firstOrNull { it.id == idEspecie }?.idRango
            cfg.tablas.rangos.firstOrNull { it.id == rango }?.nivelMaximo ?: return
        } else {
            NIVEL_MAXIMO_SIN_ESPECIE
        }
        val menorTope = cfg.tablas.rangos.minOfOrNull { it.nivelMaximo } ?: tope
        if (prog.maximo > tope) e += "misión ${s.id}: nivel_requerido máximo ${prog.maximo} supera el nivel_maximo $tope"
        if (s.id == "crecer_especie" && prog.minimo < 2) e += "misión ${s.id}: nivel_requerido mínimo debe ser al menos 2"
        if (s.id == "crecer_especie" && prog.maximo > menorTope) e += "misión ${s.id}: nivel_requerido máximo supera el menor tope de rango ($menorTope)"
    }

    private fun validarRetencion(cfg: ConfiguracionJuego, e: MutableList<String>) {
        val r = cfg.retencion
        listOf(
            "auditoria_evento" to r.auditoriaEvento,
            "movimiento_economico" to r.movimientoEconomico,
            "ventana_ciclos" to r.notificacionCerrada.ventanaCiclos,
            "maximo_filas_por_partida" to r.notificacionCerrada.maximoFilas,
            "beneficio_cerrado" to r.beneficioCerrado,
            "reproduccion_finalizada" to r.reproduccionFinalizada,
            "recompensa_mision_cerrada" to r.recompensaMisionCerrada,
            "idempotencia" to r.idempotencia,
            "ciclo_simulacion" to r.cicloSimulacion,
            "max_ciclos_offline" to r.maxCiclosOffline,
        ).filter { it.second <= 0 }.forEach { e += "retención: ${it.first} debe ser positivo" }
    }

    private fun validarTablaRango(tabla: List<ProbabilidadRango>, nombre: String, e: MutableList<String>) {
        if (tabla.sumOf { it.porcentaje } != 100 || tabla.any { it.idRango !in 2..5 || it.porcentaje <= 0 }) {
            e += "sorteo de rango $nombre: debe sumar 100 con rangos 2 a 5"
        }
    }

    private fun validarPartidaInicial(cfg: ConfiguracionJuego, e: MutableList<String>) {
        val p = cfg.partidaInicial
        if (!importeValido(p.fondos)) e += "partida inicial: fondos inválidos"
        if (p.prestigioActual != 0) e += "partida inicial: el prestigio debe ser 0"
        if (p.remanente != 600) e += "partida inicial: el remanente debe ser 600 s"
        if (p.estadoPartida != "ACTIVA") e += "partida inicial: estado de partida debe ser ACTIVA"
        if (p.estadoSimulacion != "ACTIVO") e += "partida inicial: estado de simulación debe ser ACTIVO"
        if (p.espacioGratuito.capacidad !in CAPACIDADES_ESPACIO) e += "partida inicial: capacidad del espacio gratuito inválida"
        val b = p.bienvenida
        if (b.comunes < 0 || b.pocoComunes < 0) e += "partida inicial: bienvenida inválida"
        b.sorteos.forEach { validarTablaRango(it, "bienvenida", e) }
        val sp = cfg.recompensasAnimales.sorteoPrestigio
        validarTablaRango(sp.base, "prestigio base", e)
        validarTablaRango(sp.multiplo5, "prestigio múltiplo de 5", e)
        validarTablaRango(sp.multiplo10, "prestigio múltiplo de 10", e)
        val alimentos = cfg.catalogos.alimentos.map { it.id }.toSet()
        val insumos = cfg.catalogos.insumos.map { it.id }.toSet()
        p.inventarioAlimento.filter { it.key !in alimentos || it.value < 0 }.forEach { e += "partida inicial: alimento inválido ${it.key}" }
        p.inventarioMedico.filter { it.key !in insumos || it.value < 0 }.forEach { e += "partida inicial: insumo inválido ${it.key}" }
    }
}
