package com.emabuia.pokevault.ui.stats

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.AppMotion
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.SetCompletion
import kotlin.math.cos
import kotlin.math.sin

private const val CELEBRATED_SETS_PREFS = "set_completion_celebration"
private const val CELEBRATED_SETS_KEY = "celebrated_set_ids"

/**
 * Set gia' festeggiati.
 *
 * Persistiti e non tenuti in memoria: la percentuale di un set completato resta
 * a 1f per sempre, quindi senza memoria i coriandoli ripartirebbero a ogni
 * apertura delle statistiche — e una festa che si ripete ogni volta smette di
 * essere una festa dopo la seconda.
 */
private fun celebratedSetIds(context: Context): Set<String> =
    context.getSharedPreferences(CELEBRATED_SETS_PREFS, Context.MODE_PRIVATE)
        .getStringSet(CELEBRATED_SETS_KEY, emptySet())
        .orEmpty()

private fun markSetCelebrated(context: Context, setName: String) {
    val prefs = context.getSharedPreferences(CELEBRATED_SETS_PREFS, Context.MODE_PRIVATE)
    val updated = prefs.getStringSet(CELEBRATED_SETS_KEY, emptySet()).orEmpty() + setName
    prefs.edit().putStringSet(CELEBRATED_SETS_KEY, updated).apply()
}

/**
 * Badge e coriandoli quando un set arriva al 100%.
 *
 * Compare solo la prima volta che quel set risulta completo: dopo, la barra
 * verde al massimo dice gia' tutto quello che c'e' da dire.
 */
@Composable
fun SetCompletedCelebration(completion: SetCompletion) {
    if (completion.percentage < 1f) return

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val motion = AppMotion.current

    // Letto una volta all'ingresso: se il set era gia' nella lista prima di
    // questa apertura, la festa non parte proprio.
    val alreadyCelebrated = remember(completion.setName) {
        completion.setName in celebratedSetIds(context)
    }
    var celebrating by remember(completion.setName) { mutableStateOf(false) }

    LaunchedEffect(completion.setName, completion.percentage) {
        if (alreadyCelebrated) return@LaunchedEffect
        markSetCelebrated(context, completion.setName)
        celebrating = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Il badge resta anche a festa finita, i coriandoli no: il primo e' una
    // targa, i secondi un momento.
    Spacer(modifier = Modifier.height(10.dp))

    Box(contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = alreadyCelebrated || celebrating,
            enter = fadeIn(tween(motion.celebration)) +
                scaleIn(tween(motion.celebration), initialScale = 0.9f)
        ) {
            CompletedBadge(setName = completion.setName)
        }

        if (celebrating && motion.enabled) {
            Confetti()
        }
    }
}

@Composable
private fun CompletedBadge(setName: String) {
    val motion = AppMotion.current
    val green = AppColors.green

    // Alone che respira: tiene il badge vivo senza muoverlo, cosi' l'occhio ci
    // torna sopra anche dopo che l'animazione d'ingresso e' finita.
    val glowAlpha = if (motion.enabled) {
        val transition = rememberInfiniteTransition(label = "badgeGlow")
        val animated by transition.animateFloat(
            initialValue = 0.06f,
            targetValue = 0.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(motion.glow, easing = AppMotion.standardEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "badgeGlowAlpha"
        )
        animated
    } else {
        0.12f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(green.copy(alpha = glowAlpha))
            .border(1.dp, green.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(green),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = AppColors.onAccent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(
                text = AppLocale.setCompletedTitle(setName),
                color = AppColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = AppLocale.setCompletedSubtitle,
                color = AppColors.textMuted,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Dodici particelle che partono dal badge a ventaglio.
 *
 * Un Animatable solo e dodici particelle disegnate da lui, invece di dodici
 * animazioni: la posizione di ognuna e' una funzione del progresso comune piu'
 * il suo angolo, quindi non c'e' niente da sincronizzare.
 */
@Composable
private fun Confetti() {
    val motion = AppMotion.current
    val progress = remember { Animatable(0f) }
    val colors = listOf(
        AppColors.gold,
        AppColors.blue,
        AppColors.green,
        AppColors.purple,
        AppColors.orange
    )

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(motion.confetti, easing = AppMotion.standardEasing))
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val particles = 12
        val originX = size.width / 2f
        val originY = size.height * 0.4f
        val particleSize = 7.dp.toPx()
        val rise = 104.dp.toPx()

        repeat(particles) { index ->
            // Ogni particella parte con un ritardo suo, quindi ha un progresso
            // suo: la coerenza sta nella formula, non in dodici animazioni.
            val delayFraction = (index % 4) * 0.07f
            val local = ((progress.value - delayFraction) / (1f - delayFraction))
                .coerceIn(0f, 1f)
            if (local <= 0f) return@repeat

            // Da -75° a +75°, con lo zero verso l'alto.
            val angle = Math.toRadians((-75.0 + 150.0 * index / (particles - 1)))
            val distance = rise * local

            val x = originX + sin(angle).toFloat() * distance * 0.6f
            val y = originY - cos(angle).toFloat() * distance

            drawCircle(
                color = colors[index % colors.size].copy(alpha = 1f - local),
                radius = particleSize / 2f * (1f - local * 0.6f),
                center = Offset(x, y)
            )
        }
    }
}
