package com.emabuia.pokevault.ui.album

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.emabuia.pokevault.ui.components.holoFoil
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.AppMotion
import com.emabuia.pokevault.util.ImageUrlUtils

/**
 * Pezzi comuni del Collector Lab.
 *
 * Album e Chase mostrano le stesse tre cose — quanto sei avanti, quanto vale,
 * che carte ci sono dentro — e prima ognuno le disegnava a modo suo: due anelli
 * di progresso diversi, tre modi di scrivere un prezzo, nessuna ricerca.
 *
 * Quelli che non parlano di album — prezzo, barra, riquadro di statistica,
 * ricerca, chip di ordinamento — sono passati in `ui/components/ListKit.kt`
 * quando la Wishlist ha avuto le stesse cose da mostrare: qui restano solo i
 * pezzi che sanno cos'e' un album.
 */


/**
 * Anello di avanzamento.
 *
 * Il traguardo ha un colore proprio (oro) perche' il 100% e' un evento, non
 * l'ultimo di tanti valori possibili.
 */
@Composable
internal fun ProgressRing(
    percent: Float,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    stroke: Dp = 4.dp,
    accent: Color = AppColors.orange,
    label: String? = null,
    labelSize: Int = 11
) {
    val complete = percent >= 100f
    val color = if (complete) AppColors.gold else accent
    val animated by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(AppMotion.bar),
        label = "ring"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.size(size),
            color = AppColors.textMuted.copy(alpha = 0.18f),
            strokeWidth = stroke,
            strokeCap = StrokeCap.Round,
            trackColor = Color.Transparent
        )
        CircularProgressIndicator(
            progress = { animated },
            modifier = Modifier.size(size),
            color = color,
            strokeWidth = stroke,
            strokeCap = StrokeCap.Round,
            trackColor = Color.Transparent
        )
        Text(
            text = label ?: "${percent.toInt()}%",
            color = if (complete) AppColors.gold else AppColors.textPrimary,
            fontSize = labelSize.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


/**
 * Le prime carte dell'album, sfalsate come in mano.
 *
 * Una copertina sola non dice se dentro c'e' una carta o trenta; tre carte
 * accennate lo dicono a colpo d'occhio.
 */
@Composable
internal fun CoverCollage(
    urls: List<String>,
    modifier: Modifier = Modifier,
    gradient: List<Color>,
    slotSize: Dp = 64.dp
) {
    Box(modifier = modifier.height(slotSize).width(slotSize + 22.dp)) {
        if (urls.isEmpty()) {
            Box(
                modifier = Modifier
                    .size(slotSize)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(gradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PhotoAlbum,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            return@Box
        }

        // Disegnate al contrario: la prima carta deve restare sopra le altre.
        urls.take(3).reversed().forEachIndexed { reverseIndex, url ->
            val index = urls.take(3).lastIndex - reverseIndex
            Box(
                modifier = Modifier
                    .padding(start = (index * 11).dp)
                    .size(slotSize)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.background)
                    .border(1.dp, AppColors.background, RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(ImageUrlUtils.safeProxiedImageUrl(url))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}


/** Titolo di sezione con la sua scorciatoia a destra. */
@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = AppColors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(actionLabel, color = AppColors.orange, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Il traguardo, quando arriva.
 *
 * Usa la lamina olografica dell'app: e' il linguaggio che il resto di PokeVault
 * usa per dire "questa e' speciale", e un chase chiuso lo e'.
 */
@Composable
internal fun CompletionBanner(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(AppColors.gold.copy(alpha = 0.30f), AppColors.gold.copy(alpha = 0.10f))
                )
            )
            .holoFoil()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.WorkspacePremium,
            contentDescription = null,
            tint = AppColors.gold,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, color = AppColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = AppColors.textSecondary, fontSize = 12.sp)
        }
    }
}
