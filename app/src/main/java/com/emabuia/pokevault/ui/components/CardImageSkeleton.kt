package com.emabuia.pokevault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.ui.theme.AppColors

/**
 * Quello che si vede al posto della carta finche' l'immagine non arriva.
 *
 * Prima era un rettangolo pieno del colore di sfondo -- in tema scuro un
 * quadrato quasi nero, che dava la sensazione che l'app fosse ferma. Un
 * gradiente tenue col numero della carta in filigrana dice invece che quel
 * posto e' gia' assegnato a qualcosa che sta arrivando.
 *
 * Volutamente **statico**: un'animazione per cella vorrebbe dire sessanta
 * animazioni infinite sulla stessa schermata, che e' l'opposto della
 * fluidita' cercata. Il movimento lo mette la dissolvenza dell'immagine che
 * si sovrappone (crossfade dell'ImageLoader).
 */
@Composable
fun CardImageSkeleton(
    number: String = "",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        AppColors.surface,
                        AppColors.card
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (number.isNotBlank()) {
            Text(
                text = number,
                color = AppColors.textMuted.copy(alpha = 0.35f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
