package com.emabuia.pokevault.ui.whatsnew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.ThumbsUpDown
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.WhatsNew

/**
 * Le novita' della versione, una volta sola dopo l'aggiornamento (vedi
 * [WhatsNew]). Si chiude col bottone, col tasto indietro o toccando fuori:
 * e' un saluto, non un passaggio obbligato.
 */
@Composable
fun WhatsNewDialog(
    versionName: String,
    items: List<WhatsNew.Item>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.surface)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = WhatsNew.title(versionName),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = WhatsNew.subtitle,
                fontSize = 13.sp,
                color = AppColors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            items.forEachIndexed { index, item ->
                if (index > 0) Spacer(modifier = Modifier.height(16.dp))
                WhatsNewRow(item)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue)
            ) {
                Text(
                    text = WhatsNew.closeCta,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun WhatsNewRow(item: WhatsNew.Item) {
    val (icon, tint) = iconFor(item.icon)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // Accento traslucido e non AppColors.card: col tema chiaro card e
        // surface sono lo stesso bianco e il cerchio sparirebbe.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.body,
                fontSize = 13.sp,
                color = AppColors.textSecondary,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun iconFor(icon: WhatsNew.Icon): Pair<ImageVector, Color> = when (icon) {
    WhatsNew.Icon.NAME -> Icons.Filled.AutoAwesome to AppColors.purple
    WhatsNew.Icon.LANGUAGE -> Icons.Filled.Translate to AppColors.blue
    WhatsNew.Icon.SCANNER -> Icons.Filled.DocumentScanner to AppColors.green
    WhatsNew.Icon.PRICE -> Icons.Filled.Euro to AppColors.gold
    WhatsNew.Icon.ALBUM -> Icons.Filled.PhotoLibrary to AppColors.orange
    WhatsNew.Icon.HAND -> Icons.Filled.Style to AppColors.green
    WhatsNew.Icon.RATING -> Icons.Filled.ThumbsUpDown to AppColors.blue
    WhatsNew.Icon.META -> Icons.Filled.Leaderboard to AppColors.purple
}
