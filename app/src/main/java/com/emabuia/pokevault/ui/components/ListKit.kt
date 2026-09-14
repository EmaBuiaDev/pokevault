package com.emabuia.pokevault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.AppMotion
import java.util.Locale

/**
 * Gli attrezzi delle liste: prezzi, barre, riquadri di statistica, ricerca e
 * chip di ordinamento.
 *
 * Stavano dentro `ui/album/CollectorLabComponents.kt` perche' li' sono nati,
 * ma non hanno niente di specifico del Collector Lab: sono il modo in cui
 * questa app scrive un prezzo, disegna un avanzamento e offre un ordinamento.
 * La Wishlist ha esattamente le stesse tre cose da dire, e l'alternativa era
 * ridisegnarle uguali una seconda volta.
 */

internal fun formatEur(value: Double): String =
    "€ " + String.format(Locale.ITALY, "%.2f", value)

/** Prezzo compatto per le card: 12,40 € diventa "€ 12" sopra i cento euro. */
internal fun formatEurCompact(value: Double): String = when {
    value <= 0.0 -> "—"
    value >= 100.0 -> "€ " + String.format(Locale.ITALY, "%.0f", value)
    else -> "€ " + String.format(Locale.ITALY, "%.2f", value)
}

/** Barra di riempimento sottile, per le righe di lista. */
@Composable
internal fun FillBar(
    percent: Float,
    modifier: Modifier = Modifier,
    accent: Color = AppColors.orange
) {
    val animated by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(AppMotion.bar),
        label = "fill"
    )
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier
            .height(5.dp)
            .clip(CircleShape),
        color = if (percent >= 100f) AppColors.gold else accent,
        trackColor = AppColors.textMuted.copy(alpha = 0.18f),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

/** Un numero con la sua etichetta. L'unita' di misura del riassunto in cima. */
@Composable
internal fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.card)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
        }
        Text(
            text = value,
            color = AppColors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            color = AppColors.textMuted,
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Campo di ricerca della sezione, uguale in tutte le liste. */
@Composable
internal fun CollectorSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(hint, color = AppColors.textMuted, fontSize = 13.sp) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.orange,
            unfocusedBorderColor = AppColors.textMuted.copy(alpha = 0.35f),
            cursorColor = AppColors.orange,
            focusedTextColor = AppColors.textPrimary,
            unfocusedTextColor = AppColors.textPrimary,
            focusedLeadingIconColor = AppColors.orange,
            unfocusedLeadingIconColor = AppColors.textMuted,
            focusedContainerColor = AppColors.card,
            unfocusedContainerColor = AppColors.card
        )
    )
}

/**
 * Chip di ordinamento in fila scorrevole.
 *
 * Non usa FilterChip di Material: qui i colori vengono dalla palette dell'app e
 * la fila deve poter scorrere senza andare a capo.
 */
@Composable
internal fun SortChipRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AppColors.orange
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) accent.copy(alpha = 0.22f) else AppColors.card)
                    .border(
                        1.dp,
                        if (selected) accent else AppColors.textMuted.copy(alpha = 0.22f),
                        CircleShape
                    )
                    .pressScale { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = label,
                    color = if (selected) accent else AppColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
