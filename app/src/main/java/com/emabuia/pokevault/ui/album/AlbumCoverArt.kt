package com.emabuia.pokevault.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.emabuia.pokevault.data.model.AlbumCover

/**
 * La copertina disegnata di un album: il colore dell'album, un motivo leggero
 * e, se scelto, un Pokemon al centro.
 *
 * Prima la copertina era una carta, e rimpicciolita in una riga di elenco
 * diventava un rettangolo colorato qualsiasi. Qui il motivo e' disegnato
 * (niente immagini da scaricare) e l'unica cosa che arriva dalla rete e'
 * l'artwork del Pokemon, che resta riconoscibile anche piccolo.
 *
 * Il ritaglio della forma lo decide chi la usa: la stessa copertina sta in un
 * riquadro arrotondato nell'elenco e nell'anteprima del modulo.
 */
@Composable
internal fun AlbumCoverArt(
    theme: String,
    pattern: String,
    pokemon: Int,
    modifier: Modifier = Modifier
) {
    val colors = getThemeColors(theme)
    val artwork = AlbumCover.artworkUrl(pokemon)
    Box(
        modifier = modifier
            .background(Brush.linearGradient(colors))
            .drawBehind { drawPattern(pattern) },
        contentAlignment = Alignment.Center
    ) {
        if (artwork != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artwork)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.8f)
            )
        } else {
            Icon(
                Icons.Default.AutoStories,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxSize(0.38f)
            )
        }
    }
}

/**
 * Il motivo, in proporzione al riquadro: la stessa copertina deve restare
 * ordinata a 60 dp nell'elenco e a 120 nell'anteprima.
 */
private fun DrawScope.drawPattern(pattern: String) {
    // Un riflesso in alto a sinistra, sempre: da' alla copertina un po' di volume.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
            center = Offset(size.width * 0.15f, size.height * 0.1f),
            radius = size.maxDimension * 0.75f
        ),
        radius = size.maxDimension * 0.75f,
        center = Offset(size.width * 0.15f, size.height * 0.1f)
    )
    val step = size.minDimension / 6f
    when (pattern) {
        AlbumCover.DOTS -> {
            val radius = step * 0.11f
            val color = Color.White.copy(alpha = 0.24f)
            var row = 0
            var y = step / 2f
            while (y < size.height + step) {
                // A righe sfalsate, come una carta da regalo.
                var x = if (row % 2 == 0) step / 2f else step
                while (x < size.width + step) {
                    drawCircle(color, radius, Offset(x, y))
                    x += step
                }
                y += step * 0.8f
                row++
            }
        }
        AlbumCover.STRIPES -> {
            val color = Color.White.copy(alpha = 0.16f)
            val width = step * 0.22f
            var start = -size.height
            while (start < size.width) {
                drawLine(
                    color = color,
                    start = Offset(start, size.height),
                    end = Offset(start + size.height, 0f),
                    strokeWidth = width
                )
                start += step * 0.7f
            }
        }
        else -> Unit
    }
}
