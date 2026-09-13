package com.emabuia.pokevault.ui.competitive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.ui.components.pressSlide
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.CompetitiveLogViewModel
import kotlin.math.roundToInt

/**
 * Hub della sezione competitive.
 *
 * Le tre card mostrano il dato che sta dietro alla schermata a cui portano —
 * quanti mazzi, che record, se c'e' con cosa simulare — invece del solo
 * sottotitolo fisso: senza quello erano tre pulsanti colorati, e per sapere
 * come stavano i tornei bisognava entrarci.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitiveHubScreen(
    onBack: () -> Unit,
    onNavigateToDeckLab: () -> Unit,
    onNavigateToMatchLog: () -> Unit,
    onNavigateToHandSimulator: () -> Unit,
    logViewModel: CompetitiveLogViewModel = viewModel()
) {
    // I mazzi arrivano da CompetitiveLogViewModel e non da DeckLabViewModel:
    // il secondo carica anche l'intera collezione di carte, che qui non serve
    // a disegnare nulla.
    val deckCount = logViewModel.userDecks.size
    val playedMatches = logViewModel.allMatches.size

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        AppLocale.competitiveTitle,
                        color = AppColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppLocale.back,
                            tint = AppColors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            CompetitiveSectionCard(
                title = AppLocale.competitiveDeckLabTab,
                subtitle = AppLocale.deckLabSubtitle,
                badge = if (deckCount == 0) {
                    AppLocale.competitiveHubNoDecks
                } else {
                    AppLocale.competitiveHubDeckCount(deckCount)
                },
                icon = Icons.Default.Layers,
                gradientColors = listOf(AppColors.lavender, AppColors.lavender.copy(alpha = 0.6f)),
                onClick = onNavigateToDeckLab
            )

            CompetitiveSectionCard(
                title = AppLocale.competitiveLogTab,
                subtitle = if (playedMatches == 0) {
                    AppLocale.competitiveHubNoMatches
                } else {
                    AppLocale.competitiveHubWinRate(logViewModel.globalWinRate.roundToInt())
                },
                badge = if (playedMatches == 0) {
                    null
                } else {
                    AppLocale.competitiveHubRecord(
                        logViewModel.globalWins,
                        logViewModel.globalLosses,
                        logViewModel.globalTies
                    )
                },
                icon = Icons.Default.EmojiEvents,
                gradientColors = listOf(Color(0xFFE87A35), Color(0xFFD4631E)),
                onClick = onNavigateToMatchLog
            )

            CompetitiveSectionCard(
                title = AppLocale.competitiveHandSimulatorTab,
                subtitle = if (deckCount == 0) {
                    AppLocale.competitiveHubNeedsDeck
                } else {
                    AppLocale.competitiveHubReadyToDraw
                },
                badge = null,
                icon = Icons.Default.Shuffle,
                gradientColors = listOf(AppColors.blue, AppColors.blue.copy(alpha = 0.65f)),
                onClick = onNavigateToHandSimulator
            )
        }
    }
}

@Composable
private fun CompetitiveSectionCard(
    title: String,
    subtitle: String,
    badge: String?,
    icon: ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(brush = Brush.linearGradient(gradientColors))
            .pressSlide(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.08f),
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 20.dp, y = 10.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(7.dp)
                        ) {
                            Text(
                                text = badge,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
