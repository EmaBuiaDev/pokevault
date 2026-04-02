package com.example.pokevault.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pokevault.ui.theme.*
import com.example.pokevault.util.AppLocale
import com.example.pokevault.viewmodel.StatsViewModel

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
            .background(DarkBackground)
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = { Text(AppLocale.statistics, fontWeight = FontWeight.SemiBold, color = TextWhite) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, AppLocale.back, tint = TextWhite)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BlueCard)
            }
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
                        color = BlueCard,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = AppLocale.uniqueCards,
                        value = "${state.stats.uniqueCards}",
                        color = PurpleCard,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        label = AppLocale.totalValue,
                        value = "\u20AC${"%.2f".format(state.stats.totalValue)}",
                        color = GreenCard,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = AppLocale.averageValue,
                        value = "\u20AC${"%.2f".format(state.averageValue)}",
                        color = YellowCard,
                        modifier = Modifier.weight(1f)
                    )
                }

                // ── Carta più preziosa + Graduate ──
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        label = AppLocale.mostValuable,
                        value = state.stats.mostValuable,
                        color = StarGold,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = AppLocale.graded,
                        value = "${state.gradedCount}",
                        color = RedCard,
                        modifier = Modifier.weight(1f)
                    )
                }

                // ── Per Set ──
                if (state.cardsBySet.isNotEmpty()) {
                    DistributionSection(
                        title = AppLocale.bySet,
                        items = state.cardsBySet.take(10),
                        maxValue = state.cardsBySet.maxOf { it.second },
                        color = BlueCard
                    )
                }

                // ── Per Rarità ──
                if (state.cardsByRarity.isNotEmpty()) {
                    DistributionSection(
                        title = AppLocale.byRarity,
                        items = state.cardsByRarity,
                        maxValue = state.cardsByRarity.maxOf { it.second },
                        color = PurpleCard
                    )
                }

                // ── Per Tipo ──
                if (state.cardsByType.isNotEmpty()) {
                    DistributionSection(
                        title = AppLocale.byType,
                        items = state.cardsByType,
                        maxValue = state.cardsByType.maxOf { it.second },
                        color = GreenCard
                    )
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .padding(16.dp)
    ) {
        Text(
            text = value,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = TextMuted,
            fontSize = 12.sp
        )
    }
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
            .background(DarkCard)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = TextWhite,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        items.forEach { (name, count) ->
            DistributionBar(
                label = name,
                count = count,
                maxValue = maxValue,
                color = color
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
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = TextGray,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$count",
                color = TextWhite,
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
                .background(DarkSurface)
        ) {
            val fraction = if (maxValue > 0) count.toFloat() / maxValue else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}
