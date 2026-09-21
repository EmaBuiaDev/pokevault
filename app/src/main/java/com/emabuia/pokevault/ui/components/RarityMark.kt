package com.emabuia.pokevault.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.RarityInfo
import com.emabuia.pokevault.util.RarityShape
import com.emabuia.pokevault.util.RaritySymbol
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Il segno di rarita', disegnato invece che scritto.
 *
 * Una forma ripetuta una, due o tre volte, piena o contornata, del colore
 * della rarita': e' il sistema che le carte usano davvero (una stella nera
 * Rara, due nere Doppia Rara, due argento contornate Ultra Rara, tre oro Iper
 * Rara) e regge tutte le rarita' del catalogo senza doverne scrivere nessuna.
 *
 * Il Canvas e' alto quanto [size] e largo quanto serve alle ripetizioni: le
 * forme restano tutte della stessa misura, cosa che i glifi unicode di prima
 * ("●", "◆", "★★★") non garantivano -- cambiavano larghezza col font di
 * sistema e facevano ballare la riga del riepilogo.
 */
@Composable
fun RaritySymbolIcon(info: RarityInfo, size: Dp = 12.dp, tintOverride: Color? = null) {
    val tint = tintOverride ?: if (info.adaptive) AppColors.textPrimary else info.color
    val symbol = info.symbol
    val gap = size * 0.12f
    val width = size * symbol.count + gap * (symbol.count - 1)

    Canvas(
        modifier = Modifier
            .width(width)
            .height(size)
    ) {
        val step = this.size.height + (gap.toPx())
        repeat(symbol.count) { i ->
            val center = Offset(
                x = this.size.height / 2f + i * step,
                y = this.size.height / 2f
            )
            drawRarityShape(symbol, center, this.size.height, tint)
        }
    }
}

private fun DrawScope.drawRarityShape(
    symbol: RaritySymbol,
    center: Offset,
    side: Float,
    tint: Color
) {
    // Il contorno e' sottile ma non sotto il pixel: a 10dp una linea piu'
    // fine di cosi' sparisce sui display a densita' bassa.
    val strokeWidth = (side * 0.14f).coerceAtLeast(1f)

    when (symbol.shape) {
        RarityShape.CIRCLE -> {
            val radius = side * 0.30f
            if (symbol.filled) {
                drawCircle(tint, radius, center)
            } else {
                drawCircle(tint, radius, center, style = Stroke(strokeWidth))
            }
        }

        RarityShape.DIAMOND -> {
            val r = side * 0.38f
            val path = Path().apply {
                moveTo(center.x, center.y - r)
                lineTo(center.x + r * 0.78f, center.y)
                lineTo(center.x, center.y + r)
                lineTo(center.x - r * 0.78f, center.y)
                close()
            }
            drawPath(path, tint, style = if (symbol.filled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(strokeWidth))
        }

        // Stella a cinque punte: dieci vertici alternati fra raggio esterno e
        // interno, partendo da -90° per avere la punta in alto e non a destra.
        RarityShape.STAR -> {
            val outer = side * 0.44f
            val path = starPath(center, outer, outer * 0.45f, points = 5, startAngle = -PI / 2)
            drawPath(path, tint, style = if (symbol.filled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(strokeWidth))
        }

        // Quattro punte lunghe e sottili: e' il luccichio delle shiny, e a
        // colpo d'occhio non si confonde con la stella a cinque.
        RarityShape.SPARKLE -> {
            val outer = side * 0.48f
            val path = starPath(center, outer, outer * 0.24f, points = 4, startAngle = -PI / 2)
            drawPath(path, tint, style = if (symbol.filled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(strokeWidth))
        }

        RarityShape.DASH -> {
            val half = side * 0.30f
            drawLine(
                color = tint,
                start = Offset(center.x - half, center.y),
                end = Offset(center.x + half, center.y),
                strokeWidth = strokeWidth
            )
        }
    }
}

private fun starPath(center: Offset, outer: Float, inner: Float, points: Int, startAngle: Double): Path {
    val path = Path()
    val steps = points * 2
    for (i in 0 until steps) {
        val radius = if (i % 2 == 0) outer else inner
        val angle = (startAngle + i * PI / points).toFloat()
        val x = center.x + radius * cos(angle)
        val y = center.y + radius * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * Il badge che sta sopra la miniatura di una carta.
 *
 * Fondo scuro fisso, non colorato: sopra un'illustrazione qualunque serve un
 * contrasto certo, e il colore della rarita' su un'immagine a caso non lo da'.
 * Prima il fondo era il colore della rarita' e il glifo restava nero di
 * default, per cui Doppia Rara e Rara -- nere su fondo nero -- sparivano.
 */
@Composable
fun RarityOverlayBadge(info: RarityInfo, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        RaritySymbolIcon(
            info = info,
            size = 9.dp,
            tintOverride = if (info.adaptive) Color.White else info.color
        )
    }
}

/**
 * Segno + nome, per le righe dove c'e' spazio (liste, risultati di ricerca).
 */
@Composable
fun RarityMarkWithLabel(
    info: RarityInfo,
    modifier: Modifier = Modifier,
    suffix: String = "",
    fontSize: Int = 11,
    labelColor: Color? = null
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RaritySymbolIcon(info, (fontSize + 1).dp)
        Text(
            text = if (suffix.isEmpty()) info.label else "${info.label} $suffix",
            color = labelColor ?: AppColors.textMuted,
            fontSize = fontSize.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
