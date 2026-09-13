package com.emabuia.pokevault.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.emabuia.pokevault.ui.theme.AppMotion

/**
 * Riflesso holo che attraversa l'immagine di una carta rara.
 *
 * E' una banda di luce in diagonale disegnata in ColorDodge sopra il contenuto:
 * il ColorDodge schiarisce quello che c'e' sotto invece di coprirlo, quindi la
 * luce prende i colori della carta come farebbe su una carta vera, e sulle zone
 * scure quasi non si vede — che e' esattamente come si comporta una foil.
 *
 * @param enabled false per le rarita' che non sono lucide. Da decidere con
 *   [com.emabuia.pokevault.util.RarityUtils.hasFoilFinish]: con la foil su tutte
 *   le carte una griglia da sessanta diventa sessanta animazioni continue.
 */
@Composable
fun Modifier.holoFoil(enabled: Boolean = true): Modifier {
    val motion = AppMotion.current
    if (!enabled || !motion.enabled) return this

    val transition = rememberInfiniteTransition(label = "holoFoil")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(motion.foil, easing = AppMotion.linearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "holoFoilProgress"
    )

    // Bianco esplicito e non un token: questa e' luce riflessa sopra
    // l'immagine della carta, non una superficie dell'interfaccia, e non deve
    // cambiare fra tema chiaro e scuro.
    val highlight = Color.White.copy(alpha = 0.5f)

    return this.drawWithContent {
        drawContent()

        val band = size.width * 0.5f
        val travel = size.width + band * 2f
        val start = -band + travel * progress

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(start, 0f),
                end = Offset(start + band, size.height)
            ),
            blendMode = BlendMode.ColorDodge
        )
    }
}
