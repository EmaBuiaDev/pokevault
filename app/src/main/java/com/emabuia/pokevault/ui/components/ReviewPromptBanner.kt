package com.emabuia.pokevault.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import kotlinx.coroutines.delay

/** Quante stelle si accendono, una dopo l'altra. */
private const val STAR_COUNT = 5

/** Distanza fra l'accensione di una stella e la successiva. */
private const val STAR_STAGGER_MS = 90L

/**
 * Respiro fra il momento in cui il banner viene deciso e quello in cui entra.
 *
 * Senza, comparirebbe nello stesso fotogramma in cui la schermata nuova sta
 * ancora scivolando dentro: due movimenti sovrapposti che il pollice legge come
 * un inciampo.
 */
private const val ENTRANCE_DELAY_MS = 650L

/**
 * Richiesta di recensione, in fondo allo schermo.
 *
 * Prima era un AlertDialog: compariva di colpo in mezzo alla schermata,
 * oscurava tutto e costringeva a rispondere per tornare a fare quello che si
 * stava facendo. Chiedere un favore interrompendo non e' il momento migliore
 * per chiederlo.
 *
 * Qui e' una scheda che sale dal basso, non modale: l'app resta viva e
 * toccabile dietro di lei, si puo' continuare a scorrere e ignorarla. Entra con
 * un ritardo perche' non si accavalli alla transizione di schermata, e le
 * stelle si accendono in sequenza — l'unica animazione rimasta, e dura meno di
 * mezzo secondo.
 *
 * Le stelle sono decorative e non si toccano di proposito: un selettore che
 * manda allo store solo chi sceglie 4 o 5 e' *review gating*, vietato dalle
 * norme di Google Play. Qualunque strada da qui porta alla stessa pagina.
 */
@Composable
fun ReviewPromptBanner(
    visible: Boolean,
    onReview: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Ritardo gestito qui dentro e non dal chiamante: e' una proprieta' di come
    // il banner entra, non di quando l'app decide che va mostrato.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(ENTRANCE_DELAY_MS)
            entered = true
        } else {
            entered = false
        }
    }

    AnimatedVisibility(
        visible = visible && entered,
        enter = slideInVertically(
            // Spring invece di tween: arriva con un accenno di rimbalzo, come
            // se si appoggiasse, invece di fermarsi di netto.
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            initialOffsetY = { it }
        ) + fadeIn(animationSpec = tween(220)),
        exit = slideOutVertically(
            animationSpec = tween(180),
            targetOffsetY = { it }
        ) + fadeOut(animationSpec = tween(140)),
        modifier = modifier
    ) {
        BannerCard(onReview = onReview, onLater = onLater)
    }
}

@Composable
private fun BannerCard(
    onReview: () -> Unit,
    onLater: () -> Unit
) {
    var litStars by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(STAR_COUNT) {
            delay(STAR_STAGGER_MS)
            litStars += 1
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AppColors.card,
        // L'ombra e' quello che la stacca dal contenuto vivo dietro: senza,
        // sembrerebbe parte della schermata invece che una cosa appoggiata
        // sopra, che si puo' spazzare via.
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, AppColors.gold.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(AppColors.gold, AppColors.gold.copy(alpha = 0.55f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CatchingPokemon,
                        contentDescription = null,
                        tint = AppColors.background,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = AppLocale.ratingPromptTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = AppLocale.ratingPromptTagline,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.gold,
                        lineHeight = 17.sp
                    )
                }

                // Una via d'uscita che non costringe a leggere i bottoni.
                IconButton(
                    onClick = onLater,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = AppLocale.ratingPromptLaterCta,
                        tint = AppColors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(STAR_COUNT) { index ->
                    val lit = index < litStars
                    val scale by animateFloatAsState(
                        targetValue = if (lit) 1f else 0.6f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "reviewStarScale$index"
                    )
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = if (lit) AppColors.gold else AppColors.textMuted.copy(alpha = 0.3f),
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = AppLocale.ratingPromptBody,
                fontSize = 12.sp,
                color = AppColors.textSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = onLater,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = AppLocale.ratingPromptLaterCta,
                        color = AppColors.textMuted,
                        fontSize = 13.sp
                    )
                }
                Button(
                    onClick = onReview,
                    modifier = Modifier.weight(1.4f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.gold)
                ) {
                    Text(
                        text = AppLocale.ratingPromptReviewCta,
                        color = AppColors.background,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
