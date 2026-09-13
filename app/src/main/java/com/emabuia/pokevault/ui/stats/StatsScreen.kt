package com.emabuia.pokevault.ui.stats

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.ui.components.EmptyStateView
import com.emabuia.pokevault.ui.components.ErrorStateView
import com.emabuia.pokevault.ui.components.OfflineBanner
import com.emabuia.pokevault.ui.components.StatsSkeleton
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.SetCompletion
import com.emabuia.pokevault.viewmodel.StatsViewModel

/**
 * Importo in euro con locale esplicito.
 *
 * Prima si usava "%.2f".format(valore), che adotta il locale di default del
 * dispositivo: il separatore decimale poteva non corrispondere al simbolo di
 * valuta mostrato accanto.
 */
private fun formatEuro(value: Double): String = "€%.2f".format(Locale.ITALY, value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = viewModel()
) {
    val state = viewModel.uiState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
    ) {
        TopAppBar(
            title = { Text(AppLocale.statistics, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = AppColors.textPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
        )

        OfflineBanner()

        // Crossfade e non un semplice if: la schermata vera deve prendere il
        // posto dello scheletro sfumando, altrimenti l'attesa finisce con uno
        // scatto e si perde il senso di aver aspettato quella forma li'.
        Crossfade(
            targetState = state.isLoading,
            animationSpec = tween(AppMotion.crossfade),
            label = "statsLoading"
        ) { isLoading ->
            if (isLoading) {
                StatsSkeleton(modifier = Modifier.padding(top = 4.dp))
            } else if (state.errorMessage != null) {
                ErrorStateView(message = state.errorMessage)
            } else if (state.cards.isEmpty()) {
                EmptyStateView(
                    title = AppLocale.emptyStatsTitle,
                    subtitle = AppLocale.emptyStatsSubtitle
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // ── Stat Cards 2x2 ──
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            label = AppLocale.totalCards,
                            value = "${state.stats.totalCards}",
                            color = AppColors.blue,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = AppLocale.uniqueCards,
                            value = "${state.stats.uniqueCards}",
                            color = AppColors.purple,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            label = AppLocale.totalValue,
                            value = formatEuro(state.stats.totalValue),
                            color = AppColors.green,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = AppLocale.averageValue,
                            value = formatEuro(state.averageValue),
                            color = AppColors.yellow,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // ── Carta più preziosa + Graduate ──
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            label = AppLocale.mostValuable,
                            value = state.stats.mostValuable,
                            color = AppColors.gold,
                            modifier = Modifier.weight(1f),
                            isTextValue = true
                        )
                        StatCard(
                            label = AppLocale.graded,
                            value = "${state.gradedCount}",
                            color = AppColors.red,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // ── Completamento Set ──
                    if (state.setCompletions.isNotEmpty()) {
                        CompletionSection(
                            title = AppLocale.setCompletion,
                            items = state.setCompletions.take(10)
                        )
                    }

                    // ── Distribuzione Per Set (Quantità) ──
                    if (state.cardsBySet.isNotEmpty()) {
                        DistributionSection(
                            title = AppLocale.bySet,
                            items = state.cardsBySet.take(10),
                            maxValue = state.cardsBySet.first().second,
                            color = AppColors.blue
                        )
                    }

                    // ── Per Rarità ──
                    if (state.cardsByRarity.isNotEmpty()) {
                        DistributionSection(
                            title = AppLocale.byRarity,
                            items = state.cardsByRarity,
                            maxValue = state.cardsByRarity.first().second,
                            color = AppColors.purple
                        )
                    }

                    // ── Per Tipo ──
                    if (state.cardsByType.isNotEmpty()) {
                        DistributionSection(
                            title = AppLocale.byType,
                            items = state.cardsByType,
                            maxValue = state.cardsByType.first().second,
                            color = AppColors.green
                        )
                    }

                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    // I valori testuali (il nome della carta piu' preziosa) hanno bisogno di
    // piu' spazio dei numeri: a 20.sp su mezza larghezza e una riga sola
    // venivano troncati a pochi caratteri.
    isTextValue: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .padding(16.dp)
    ) {
        Text(
            text = value,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = if (isTextValue) 14.sp else 20.sp,
            maxLines = if (isTextValue) 2 else 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = AppColors.textMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
fun CompletionSection(
    title: String,
    items: List<SetCompletion>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = AppColors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        items.forEachIndexed { index, completion ->
            CompletionBar(completion, index)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun CompletionBar(item: SetCompletion, index: Int = 0) {
    // Le barre partono da zero e si riempiono, sfalsate: il riempimento e' il
    // dato (quanto manca), e sfalsate si leggono una per volta invece che come
    // un blocco che compare.
    val fill by animateBarFill(item.percentage, index)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.setName,
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(item.percentage * 100).toInt()}% (${item.ownedUnique}/${item.totalCards})",
                color = AppColors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(AppColors.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            item.percentage >= 1f -> AppColors.green
                            item.percentage >= 0.5f -> AppColors.yellow
                            else -> AppColors.blue
                        }
                    )
            )
        }

        SetCompletedCelebration(completion = item)
    }
}

/**
 * Riempimento animato di una barra.
 *
 * Il valore parte da zero e ci arriva: `animateFloatAsState` da solo non
 * animerebbe la prima comparsa, perche' il suo stato iniziale *e'* gia' il
 * bersaglio. Il LaunchedEffect e' quello che gli da' qualcosa da percorrere.
 */
@Composable
private fun animateBarFill(target: Float, index: Int): State<Float> {
    val motion = AppMotion.current
    var launched by remember { mutableStateOf(false) }
    LaunchedEffect(target) { launched = true }

    return animateFloatAsState(
        targetValue = if (launched) target else 0f,
        animationSpec = tween(
            durationMillis = motion.bar,
            delayMillis = motion.barCascade(index),
            easing = AppMotion.standardEasing
        ),
        label = "barFill"
    )
}

@Composable
fun DistributionSection(
    title: String,
    items: List<Pair<String, Int>>,
    maxValue: Int,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = AppColors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        items.forEachIndexed { index, (name, count) ->
            DistributionBar(
                label = name,
                count = count,
                maxValue = maxValue,
                color = color,
                index = index
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun DistributionBar(
    label: String,
    count: Int,
    maxValue: Int,
    color: Color,
    index: Int = 0
) {
    val fraction = if (maxValue > 0) count.toFloat() / maxValue else 0f
    val fill by animateBarFill(fraction, index)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$count",
                color = AppColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppColors.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}
