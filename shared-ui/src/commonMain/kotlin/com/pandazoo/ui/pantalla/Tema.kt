package com.pandazoo.ui.pantalla

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import com.pandazoo.ui.generated.resources.Res
import com.pandazoo.ui.generated.resources.fraunces
import com.pandazoo.ui.generated.resources.nunito
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Identidad visual: IngenieriaSoftware/Interfaz/EspecificacionInterfaz.md. Único lugar con colores literales.

private val EsquemaClaro = lightColorScheme(
    primary = Color(0xFF5E9068),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDEDD8),
    onPrimaryContainer = Color(0xFF1E3D27),
    secondary = Color(0xFF7A5C3E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEBDCC6),
    onSecondaryContainer = Color(0xFF2E200F),
    tertiary = Color(0xFF2F6F7E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCDE7EB),
    onTertiaryContainer = Color(0xFF0B2E36),
    background = Color(0xFFF6F2E7),
    onBackground = Color(0xFF1F2A20),
    surface = Color(0xFFFBF8EF),
    onSurface = Color(0xFF1F2A20),
    surfaceVariant = Color(0xFFE7E2D0),
    onSurfaceVariant = Color(0xFF4A5043),
    outline = Color(0xFF8A8468),
    outlineVariant = Color(0xFFCFC8AC),
    error = Color(0xFFA63A2B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF6D8D0),
    onErrorContainer = Color(0xFF3F0F08),
)

private val EsquemaOscuro = darkColorScheme(
    primary = Color(0xFFA9D0A1),
    onPrimary = Color(0xFF0C2A14),
    primaryContainer = Color(0xFF2F5238),
    onPrimaryContainer = Color(0xFFDDEDD8),
    secondary = Color(0xFFD9BC96),
    onSecondary = Color(0xFF3A280F),
    secondaryContainer = Color(0xFF54402A),
    onSecondaryContainer = Color(0xFFEBDCC6),
    tertiary = Color(0xFF8CC7D3),
    onTertiary = Color(0xFF06242B),
    tertiaryContainer = Color(0xFF1D515D),
    onTertiaryContainer = Color(0xFFCDE7EB),
    background = Color(0xFF111812),
    onBackground = Color(0xFFE4E6DA),
    surface = Color(0xFF18211A),
    onSurface = Color(0xFFE4E6DA),
    surfaceVariant = Color(0xFF2B342B),
    onSurfaceVariant = Color(0xFFC2C7B4),
    outline = Color(0xFF8D9380),
    outlineVariant = Color(0xFF454D40),
    error = Color(0xFFF0A090),
    onError = Color(0xFF4A130B),
    errorContainer = Color(0xFF7A281C),
    onErrorContainer = Color(0xFFF6D8D0),
)

/** Colores semánticos que Material 3 no define (spec. sección 2.3). */
@Immutable
class ColoresSemanticos(
    val exito: Color,
    val exitoContenedor: Color,
    val advertencia: Color,
    val advertenciaContenedor: Color,
    val info: Color,
    val infoContenedor: Color,
)

private val SemanticosClaro = ColoresSemanticos(
    exito = Color(0xFF4A7F55),
    exitoContenedor = Color(0xFFE0F0DC),
    advertencia = Color(0xFF8A5A00),
    advertenciaContenedor = Color(0xFFF8E3B5),
    info = Color(0xFF2F6F7E),
    infoContenedor = Color(0xFFCDE7EB),
)

private val SemanticosOscuro = ColoresSemanticos(
    exito = Color(0xFF9CD39A),
    exitoContenedor = Color(0xFF24472C),
    advertencia = Color(0xFFE6BE6A),
    advertenciaContenedor = Color(0xFF4D3A10),
    info = Color(0xFF8CC7D3),
    infoContenedor = Color(0xFF1D515D),
)

private val LocalSemanticos = staticCompositionLocalOf { SemanticosClaro }

val MaterialTheme.semanticos: ColoresSemanticos
    @Composable @ReadOnlyComposable get() = LocalSemanticos.current

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

/** Familias propias: Fraunces (serif con carácter) para títulos y Nunito (redondeada) para el resto. */
@Composable
private fun tipografia(): Typography {
    @Composable fun familia(r: FontResource, pesos: List<Int>) = FontFamily(
        pesos.map { w -> Font(r, FontWeight(w), FontStyle.Normal, FontVariation.Settings(FontVariation.weight(w))) },
    )
    val titulos = familia(Res.font.fraunces, listOf(400, 600, 700))
    val cuerpo = familia(Res.font.nunito, listOf(400, 500, 600, 700))
    val base = Typography()
    fun TextStyle.con(f: FontFamily) = copy(fontFamily = f)
    return base.copy(
        displayLarge = base.displayLarge.con(titulos),
        displayMedium = base.displayMedium.con(titulos),
        displaySmall = base.displaySmall.con(titulos),
        headlineLarge = base.headlineLarge.con(titulos).copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.con(titulos).copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.con(titulos).copy(fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontFamily = titulos, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = titulos, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
        titleSmall = base.titleSmall.con(titulos),
        bodyLarge = base.bodyLarge.con(cuerpo).copy(fontSize = 15.sp),
        bodyMedium = base.bodyMedium.con(cuerpo).copy(fontSize = 15.sp),
        bodySmall = base.bodySmall.con(cuerpo),
        labelLarge = base.labelLarge.con(cuerpo).copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        labelMedium = base.labelMedium.con(cuerpo).copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
        labelSmall = base.labelSmall.con(cuerpo),
    )
}

@Composable
fun PandaZooTema(contenido: @Composable () -> Unit) {
    val oscuro = isSystemInDarkTheme()
    CompositionLocalProvider(LocalSemanticos provides if (oscuro) SemanticosOscuro else SemanticosClaro) {
        MaterialTheme(
            colorScheme = if (oscuro) EsquemaOscuro else EsquemaClaro,
            typography = tipografia(),
            shapes = Formas,
            content = contenido,
        )
    }
}

/** Tipo de aviso (spec. sección 5.9): cada uno lleva color, icono y texto (RNF-07). */
enum class TipoAviso(val icono: String) {
    EXITO("✔"), // U+2714 existe en la fuente de emoji de reserva de la web; "✓" (U+2713) no
    ERROR("⚠"),
    ADVERTENCIA("⚠"),
    INFO("i"),
}

@Composable
internal fun colorAviso(tipo: TipoAviso): Pair<Color, Color> {
    val s = MaterialTheme.semanticos
    val e = MaterialTheme.colorScheme
    return when (tipo) {
        TipoAviso.EXITO -> s.exito to s.exitoContenedor
        TipoAviso.ERROR -> e.error to e.errorContainer
        TipoAviso.ADVERTENCIA -> s.advertencia to s.advertenciaContenedor
        TipoAviso.INFO -> s.info to s.infoContenedor
    }
}

/** Tono de un rango de especie: filete y fondo suave. El rango no se escribe; se comunica con el color. */
@Immutable
internal class TonoRango(val filete: Color, val contenedor: Color)

private val TonosClaro = mapOf(
    "Común" to TonoRango(Color(0xFF8A9A86), Color(0xFFEDF0E8)),
    "Poco común" to TonoRango(Color(0xFF4F9A5E), Color(0xFFE0F0DC)),
    "Raro" to TonoRango(Color(0xFF3C7FB8), Color(0xFFDCEAF6)),
    "Épico" to TonoRango(Color(0xFF8456B5), Color(0xFFEADFF4)),
    "Exclusivo" to TonoRango(Color(0xFFB59044), Color(0xFFF1E8CF)),
)

private val TonosOscuro = mapOf(
    "Común" to TonoRango(Color(0xFFA7B3A2), Color(0xFF242C25)),
    "Poco común" to TonoRango(Color(0xFF7CC48A), Color(0xFF1F3A25)),
    "Raro" to TonoRango(Color(0xFF7FB4E3), Color(0xFF1D3547)),
    "Épico" to TonoRango(Color(0xFFBE9AE6), Color(0xFF33244A)),
    "Exclusivo" to TonoRango(Color(0xFFD2B574), Color(0xFF3A3220)),
)

/** Tonos del diamante: azul hielo, que se distingue del dorado de los demás exclusivos. */
private val TonoDiamanteClaro = TonoRango(Color(0xFF1F84AE), Color(0xFFD8EEF7))
private val TonoDiamanteOscuro = TonoRango(Color(0xFF5FC1E4), Color(0xFF112B35))

/** [diamante]: la especie con toques de diamante (sigue siendo exclusiva). */
@Composable
internal fun tonoRango(rango: String, diamante: Boolean = false): TonoRango {
    val oscuro = MaterialTheme.colorScheme.background.luminance() < 0.5f
    if (diamante) return if (oscuro) TonoDiamanteOscuro else TonoDiamanteClaro
    val tonos = if (oscuro) TonosOscuro else TonosClaro
    return tonos[rango] ?: TonoRango(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.surface)
}

@Composable
private fun esOscuro() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Degradado del contorno exclusivo: dorado apagado con un destello claro, para no desentonar con el resto de la interfaz. */
internal val BrilloExclusivo = Brush.linearGradient(listOf(Color(0xFFE3CE95), Color(0xFFB59044), Color(0xFFF1E3B8), Color(0xFFB59044), Color(0xFFE3CE95)))

/** Degradado del contorno de diamante: hielo, blanco y un toque lila, con destellos intercalados. */
internal val BrilloDiamante = Brush.linearGradient(
    listOf(Color(0xFFA6DCF2), Color(0xFF369FCB), Color(0xFFFFFFFF), Color(0xFFB7A6F2), Color(0xFF80C6E8), Color(0xFFFFFFFF), Color(0xFF369FCB)),
)

/** Contorno de una ficha según su rango: degradado brillante en épico y exclusivo; el resto, el borde fino de siempre. */
@Composable
private fun bordeRango(rango: String?, filete: Color, diamante: Boolean = false): BorderStroke {
    val base = MaterialTheme.colorScheme.outlineVariant
    if (diamante) return BorderStroke(2.5.dp, BrilloDiamante)
    return when (rango) {
        "Exclusivo" -> BorderStroke(2.dp, BrilloExclusivo)
        "Épico" -> BorderStroke(2.dp, Brush.linearGradient(listOf(filete, Color(0xFFD9BFFF), filete, Color(0xFFB98CF0), filete)))
        else -> BorderStroke(1.dp, base)
    }
}

/** Reflejo diagonal sobre la ficha exclusiva (estático: una animación infinita impide que la interfaz quede en reposo). */
internal fun Modifier.reflejos(activo: Boolean): Modifier =
    if (!activo) this else drawWithContent {
        drawContent()
        val w = size.width
        val h = size.height
        drawRect(
            Brush.linearGradient(
                0.28f to Color.Transparent, 0.38f to Color.White.copy(alpha = 0.20f), 0.46f to Color.Transparent,
                0.58f to Color.Transparent, 0.62f to Color.White.copy(alpha = 0.14f), 0.68f to Color.Transparent,
                start = Offset(0f, 0f), end = Offset(w, h),
            ),
        )
    }

/** Destellos de diamante: pequeñas estrellas de cuatro puntas y una faceta clara (estático, como los reflejos). */
internal fun Modifier.destellosDiamante(activo: Boolean): Modifier =
    if (!activo) this else drawWithContent {
        drawContent()
        val w = size.width
        val h = size.height
        // Facetas: dos bandas diagonales claras con un matiz azulado.
        drawRect(
            Brush.linearGradient(
                0.18f to Color.Transparent, 0.26f to Color(0xFFA6DCF2).copy(alpha = 0.30f), 0.34f to Color.Transparent,
                0.52f to Color.Transparent, 0.58f to Color.White.copy(alpha = 0.26f), 0.64f to Color.Transparent,
                0.78f to Color.Transparent, 0.82f to Color(0xFFC9B8FF).copy(alpha = 0.20f), 0.88f to Color.Transparent,
                start = Offset(0f, 0f), end = Offset(w, h),
            ),
        )
        // Estrellas de cuatro puntas en posiciones fijas.
        fun estrella(cx: Float, cy: Float, radio: Float, alfa: Float) {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx, cy - radio)
                quadraticTo(cx, cy, cx + radio, cy)
                quadraticTo(cx, cy, cx, cy + radio)
                quadraticTo(cx, cy, cx - radio, cy)
                quadraticTo(cx, cy, cx, cy - radio)
                close()
            }
            drawPath(p, Color.White.copy(alpha = alfa))
        }
        val base = minOf(w, h)
        estrella(w * 0.10f, h * 0.22f, base * 0.10f, 0.85f)
        estrella(w * 0.88f, h * 0.30f, base * 0.07f, 0.70f)
        estrella(w * 0.72f, h * 0.80f, base * 0.09f, 0.80f)
        estrella(w * 0.30f, h * 0.78f, base * 0.05f, 0.60f)
    }

/**
 * Ficha: unidad visual con filete izquierdo de color y borde fino (spec. sección 5.3). Con [rango] añade el brillo
 * propio del rango: contorno brillante y resplandor en los épicos; además reflejos en los exclusivos.
 */
@Composable
internal fun Ficha(
    modifier: Modifier = Modifier,
    filete: Color = MaterialTheme.colorScheme.primary,
    atenuada: Boolean = false,
    fondo: Color = MaterialTheme.colorScheme.surface,
    rango: String? = null,
    diamante: Boolean = false,
    contenido: @Composable () -> Unit,
) {
    val brillante = rango == "Épico" || rango == "Exclusivo" || diamante
    val forma = MaterialTheme.shapes.medium
    Card(
        modifier = modifier.fillMaxWidth().alpha(if (atenuada) 0.6f else 1f)
            .then(if (brillante) Modifier.shadow(8.dp, forma, ambientColor = filete, spotColor = filete) else Modifier),
        shape = forma,
        colors = CardDefaults.cardColors(containerColor = fondo),
        border = bordeRango(rango, filete, diamante),
    ) {
        Row(Modifier.height(IntrinsicSize.Min).reflejos(rango == "Exclusivo" && !diamante).destellosDiamante(diamante)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(filete))
            Column(Modifier.padding(16.dp).weight(1f)) { contenido() }
        }
    }
}

/**
 * Nombre de un animal o especie. En el rango exclusivo es más luminoso: degradado dorado con resplandor.
 */
@Composable
internal fun NombreRango(
    texto: String,
    rango: String?,
    estilo: TextStyle = MaterialTheme.typography.titleMedium,
    modifier: Modifier = Modifier,
    diamante: Boolean = false,
) {
    if (diamante) {
        val oscuro = esOscuro()
        val colores = if (oscuro) listOf(Color(0xFFD8F2FC), Color(0xFF5FC1E4), Color(0xFFFFFFFF), Color(0xFFB7A6F2))
        else listOf(Color(0xFF0B5F82), Color(0xFF1F84AE), Color(0xFF3069B5), Color(0xFF1F84AE))
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                texto,
                style = estilo.copy(
                    brush = Brush.linearGradient(colores),
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(color = if (oscuro) Color(0xFF5FC1E4) else Color(0xFFA6DCF2), offset = Offset(0f, 0f), blurRadius = if (oscuro) 11f else 8f),
                ),
            )
            // Toque de diamante junto al nombre.
            Text("◆", style = estilo.copy(fontSize = estilo.fontSize * 0.8f), color = if (oscuro) Color(0xFF80D0EC) else Color(0xFF1F84AE))
        }
    } else if (rango == "Exclusivo") {
        val oscuro = esOscuro()
        val colores = if (oscuro) listOf(Color(0xFFEFE0B4), Color(0xFFD2B574), Color(0xFFF4E9C8), Color(0xFFD2B574))
        else listOf(Color(0xFF86672A), Color(0xFFB08A35), Color(0xFF94722C), Color(0xFFB08A35))
        Text(
            texto,
            modifier = modifier,
            style = estilo.copy(
                brush = Brush.linearGradient(colores),
                fontWeight = FontWeight.Bold,
                shadow = Shadow(color = if (oscuro) Color(0xFFD2B574) else Color(0xFFE9D9A8), offset = Offset(0f, 0f), blurRadius = if (oscuro) 9f else 6f),
            ),
        )
    } else {
        Text(texto, modifier = modifier, style = estilo, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Fila de dato: etiqueta a la izquierda, valor a la derecha, separador inferior (spec. sección 5.4). */
@Composable
internal fun FilaDato(etiqueta: String, valor: String) {
    Column(Modifier.fillMaxWidth()) {
        // Ambos textos pueden ocupar varias líneas: cada uno recibe un reparto del ancho en lugar de empujar al otro.
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false))
            Text(
                valor,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Mensaje o aviso con contenedor semántico, filete, icono y texto (spec. sección 5.9). */
@Composable
internal fun AvisoUi(texto: String, tipo: TipoAviso, modifier: Modifier = Modifier) {
    val (color, contenedor) = colorAviso(tipo)
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).background(contenedor, MaterialTheme.shapes.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(color))
        Text(tipo.icono, color = color, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
        Text(texto, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(12.dp).weight(1f))
    }
}

/** Ocupación de un espacio: etiqueta y cifra como el resto de datos, con una barra fina que se llena con los animales alojados. */
@Composable
internal fun OcupacionEspacio(e: EspacioUi) {
    val lleno = e.capacidad > 0 && e.ocupacion >= e.capacidad
    val color = if (!e.activo) MaterialTheme.colorScheme.outline else if (lleno) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val fraccion = if (e.capacidad > 0) (e.ocupacion.toFloat() / e.capacidad).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Ocupación", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (e.activo) "${e.ocupacion}/${e.capacidad}" else "${e.ocupacion}/${e.capacidad} · De baja",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(fraccion).fillMaxHeight().background(color, RoundedCornerShape(3.dp)))
        }
    }
}
