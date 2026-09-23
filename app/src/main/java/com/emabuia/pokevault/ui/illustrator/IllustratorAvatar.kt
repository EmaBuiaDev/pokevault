package com.emabuia.pokevault.ui.illustrator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.ui.theme.AppColors

/**
 * Il segno di un illustratore: iniziali su un fondo sfumato.
 *
 * Gli illustratori non hanno un'immagine -- il catalogo ha solo il nome -- e
 * una fila di quattrocento righe con la stessa icona generica non si scorre:
 * non si distingue una voce dall'altra mentre il dito scende. Il colore nasce
 * dal nome, quindi lo stesso artista ha sempre il suo, e riconoscerlo diventa
 * possibile anche senza leggere.
 *
 * I colori vengono dalla palette dell'app e non da un HSL calcolato al volo:
 * una tinta qualsiasi passa il tema chiaro e affonda in quello scuro, o
 * viceversa.
 */
@Composable
fun IllustratorAvatar(
    key: String,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val palette = listOf(
        AppColors.purple,
        AppColors.blue,
        AppColors.orange,
        AppColors.green,
        AppColors.red,
        AppColors.lavender,
        AppColors.gold
    )
    // Un hash stabile: hashCode() di String lo e' per contratto, e la stessa
    // chiave deve dare lo stesso colore su ogni dispositivo e a ogni avvio.
    val accent = palette[(key.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }) % palette.size]

    Box(
        modifier = modifier
            .size(size)
            .background(
                Brush.linearGradient(listOf(accent.copy(alpha = 0.32f), accent.copy(alpha = 0.14f))),
                RoundedCornerShape(size / 3.4f)
            )
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(size / 3.4f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initialsOf(displayName),
            color = accent,
            fontSize = (size.value * 0.34f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Una o due iniziali. "5ban Graphics" da' "5G", "Mitsuhiro Arita" da' "MA":
 * anche i nomi che cominciano per cifra devono uscirne con qualcosa addosso.
 */
private fun initialsOf(displayName: String): String {
    val words = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    val first = words.first().firstOrNull()?.uppercaseChar() ?: '?'
    if (words.size == 1) return first.toString()
    val second = words[1].firstOrNull()?.uppercaseChar()
    return if (second != null) "$first$second" else first.toString()
}

/** Il colore d'accento della sezione: viola, l'unico libero fra Album e Chase. */
val illustratorAccent: Color
    @Composable get() = AppColors.purple

