package com.emabuia.pokevault.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.model.MetaArchetype
import com.emabuia.pokevault.data.model.MetaDeck
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.MetaDeckViewModel
import kotlinx.coroutines.delay

private enum class ArchetypeSortMode { META_SHARE, WIN_RATE, BEST_PLACEMENT }

/** Ritorna 1..4 in base alla meta share dell'archetipo. */
private fun tierOf(metaShare: Double) = when {
    metaShare >= 15.0 -> 1
    metaShare >= 8.0  -> 2
    metaShare >= 3.0  -> 3
    else              -> 4
}

private fun tierColor(tier: Int) = when (tier) {
    1    -> Color(0xFFEF4444) // rosso – dominante
    2    -> Color(0xFFEAB308) // giallo – rilevante
    3    -> Color(0xFF22C55E) // verde – presente
    else -> Color(0xFF6B7280) // grigio – fringe
}

private fun tierLabel(tier: Int) = when (tier) {
    1    -> "Tier 1"
    2    -> "Tier 2"
    3    -> "Tier 3"
    else -> "Tier 4"
}

@Composable
fun MetaArchetypeSection(
    viewModel: MetaDeckViewModel,
    onImportDeck: ((MetaDeck) -> Unit)? = null,
    onCardClick: ((MetaDeck) -> Unit)? = null
) {
    var sortMode by remember { mutableStateOf(ArchetypeSortMode.META_SHARE) }
    var showTierInfo by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Torna sempre al primo elemento quando cambia l'ordinamento
    LaunchedEffect(sortMode) {
        if (listState.firstVisibleItemIndex != 0) {
            listState.animateScrollToItem(0)
        }
    }

    val sortedArchetypes = remember(viewModel.archetypes, sortMode) {
        when (sortMode) {
            ArchetypeSortMode.META_SHARE    -> viewModel.archetypes
            ArchetypeSortMode.WIN_RATE      -> viewModel.archetypes.sortedByDescending { it.avgWinrate }
            ArchetypeSortMode.BEST_PLACEMENT -> viewModel.archetypes.sortedBy { it.topPlacement }
        }
    }

    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); tick++ }
    }
    var rateLimitedMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(rateLimitedMessage) {
        if (rateLimitedMessage != null) { delay(3_000); rateLimitedMessage = null }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Info banner
        MetaInfoBanner(
            title = AppLocale.metaArchetypeInfoTitle,
            body = AppLocale.metaArchetypeInfoBody,
            lastUpdated = viewModel.lastUpdated,
            rateLimitMessage = rateLimitedMessage,
            tick = tick
        )

        // Format + Refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FormatChip(
                label = "Standard",
                selected = viewModel.selectedFormat == "standard",
                onClick = { viewModel.selectFormat("standard") }
            )
            FormatChip(
                label = "Expanded",
                selected = viewModel.selectedFormat == "expanded",
                onClick = { viewModel.selectFormat("expanded") }
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    val started = viewModel.refresh()
                    rateLimitedMessage = if (!started)
                        AppLocale.metaRefreshCooldown(viewModel.refreshCooldownSeconds)
                    else null
                },
                modifier = Modifier.size(32.dp).clip(CircleShape).background(DarkCard)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }

        // Sort selector + info tier
        if (viewModel.archetypes.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.Sort, null, tint = TextMuted, modifier = Modifier.size(15.dp))
                Text(
                    text = if (AppLocale.isItalian) "Ordina:" else "Sort:",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                SortChip(
                    label = AppLocale.metaShare,
                    selected = sortMode == ArchetypeSortMode.META_SHARE,
                    onClick = { sortMode = ArchetypeSortMode.META_SHARE }
                )
                SortChip(
                    label = "Win Rate",
                    selected = sortMode == ArchetypeSortMode.WIN_RATE,
                    onClick = { sortMode = ArchetypeSortMode.WIN_RATE }
                )
                SortChip(
                    label = if (AppLocale.isItalian) "Piazz." else "Place",
                    selected = sortMode == ArchetypeSortMode.BEST_PLACEMENT,
                    onClick = { sortMode = ArchetypeSortMode.BEST_PLACEMENT }
                )
                Spacer(modifier = Modifier.weight(1f))
                // Bottone info tier
                IconButton(
                    onClick = { showTierInfo = !showTierInfo },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = if (AppLocale.isItalian) "Cos'è un tier?" else "What is a tier?",
                        tint = if (showTierInfo) BlueCard else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Pannello tier collassabile
            AnimatedVisibility(
                visible = showTierInfo,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                TierInfoPanel()
            }
        }

        when {
            viewModel.isLoadingArchetypes -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = BlueCard, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (AppLocale.isItalian) "Caricamento archetipi..." else "Loading archetypes...",
                            color = TextMuted, fontSize = 13.sp
                        )
                    }
                }
            }

            viewModel.archetypeError != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
                        Icon(Icons.Default.CloudOff, null, tint = RedCard, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (AppLocale.isItalian) "Errore di connessione" else "Connection error",
                            color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                        Text(viewModel.archetypeError ?: "", color = TextMuted, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { viewModel.loadArchetypes() }) {
                            Text(AppLocale.retry, color = BlueCard)
                        }
                    }
                }
            }

            viewModel.archetypes.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
                        Icon(Icons.Default.SearchOff, null, tint = LavenderCard, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(AppLocale.metaNoArchetypes, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (AppLocale.isItalian) "Prova a cambiare formato." else "Try changing format.",
                            color = TextMuted, fontSize = 12.sp
                        )
                    }
                }
            }

            else -> {
                // Mostra tier legenda solo quando ordinato per meta share
                val showTierHeaders = sortMode == ArchetypeSortMode.META_SHARE

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    itemsIndexed(sortedArchetypes, key = { _, a -> a.name }) { index, archetype ->
                        val previousTier = if (index > 0 && showTierHeaders)
                            tierOf(sortedArchetypes[index - 1].metaShare) else -1
                        val currentTier = tierOf(archetype.metaShare)

                        // Header di separazione tra tier
                        if (showTierHeaders && currentTier != previousTier) {
                            TierHeader(tier = currentTier)
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        ArchetypeCard(
                            rank = index + 1,
                            archetype = archetype,
                            onImport = if (onImportDeck != null && archetype.sampleDeck != null)
                                { { onImportDeck(archetype.sampleDeck!!) } } else null,
                            onClick = if (onCardClick != null && archetype.sampleDeck != null)
                                { { onCardClick(archetype.sampleDeck!!) } } else null
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ── Tier info panel (collassabile) ─────────────────────────────────────────

@Composable
private fun TierInfoPanel() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = DarkCard,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = if (AppLocale.isItalian) "Cosa sono i Tier?" else "What are Tiers?",
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (AppLocale.isItalian)
                    "Il tier indica quanto un archetipo è diffuso nel meta competitivo, calcolato sul numero di deck giocati negli ultimi tornei rispetto al totale."
                else
                    "The tier indicates how widespread an archetype is in the competitive meta, calculated on the number of decks played in recent tournaments vs. the total.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            listOf(
                Triple(1, "Tier 1",
                    if (AppLocale.isItalian) "≥ 15% — Dominante, punta del meta" else "≥ 15% — Dominant, top of meta"),
                Triple(2, "Tier 2",
                    if (AppLocale.isItalian) "8–15% — Rilevante, ottima scelta" else "8–15% — Relevant, strong choice"),
                Triple(3, "Tier 3",
                    if (AppLocale.isItalian) "3–8% — Presente, situazionale" else "3–8% — Present, situational"),
                Triple(4, "Tier 4",
                    if (AppLocale.isItalian) "< 3% — Fringe, raramente giocato" else "< 3% — Fringe, rarely played")
            ).forEach { (tier, label, desc) ->
                val color = tierColor(tier)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        color = color.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 1.dp)
                    ) {
                        Text(
                            label,
                            color = color,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(desc, color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
    }
}

// ── Tier separator header ──────────────────────────────────────────────────

@Composable
private fun TierHeader(tier: Int) {
    val color = tierColor(tier)
    val label = tierLabel(tier)
    val subtitle = when (tier) {
        1    -> if (AppLocale.isItalian) "Dominante (≥15% meta share)" else "Dominant (≥15% meta share)"
        2    -> if (AppLocale.isItalian) "Rilevante (8–15%)" else "Relevant (8–15%)"
        3    -> if (AppLocale.isItalian) "Presente (3–8%)" else "Present (3–8%)"
        else -> if (AppLocale.isItalian) "Fringe (<3%)" else "Fringe (<3%)"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.width(8.dp))
        Text(subtitle, color = TextMuted, fontSize = 10.sp)
    }
}

// ── Archetype card ─────────────────────────────────────────────────────────

@Composable
private fun ArchetypeCard(
    rank: Int,
    archetype: MetaArchetype,
    onImport: (() -> Unit)?,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tier = tierOf(archetype.metaShare)
    val tColor = tierColor(tier)
    val tLabel = tierLabel(tier)

    val rankColor = when (rank) {
        1    -> Color(0xFFFFD700)
        2    -> Color(0xFFC0C0C0)
        3    -> Color(0xFFCD7F32)
        else -> TextMuted
    }

    val wrColor = when {
        archetype.avgWinrate >= 0.65 -> GreenCard
        archetype.avgWinrate >= 0.50 -> YellowCard
        else                          -> RedCard
    }

    // Trend: confronta i primi 2 piazzamenti recenti con gli ultimi 2
    val trendInfo: Pair<androidx.compose.ui.graphics.vector.ImageVector, Color>? =
        remember(archetype.recentResults) {
            val results = archetype.recentResults
            if (results.size >= 4) {
                val early = results.take(2).average()
                val late  = results.takeLast(2).average()
                when {
                    late < early - 4  -> Icons.AutoMirrored.Filled.TrendingUp   to GreenCard
                    late > early + 4  -> Icons.AutoMirrored.Filled.TrendingDown to RedCard
                    else              -> Icons.AutoMirrored.Filled.TrendingFlat to TextMuted
                }
            } else null
        }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {

            // ── Riga 1: Rank · Nome · Tier badge · Meta% ─────────────────
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // Rank circle
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(rankColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("#$rank", color = rankColor, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Nome + tier badge + info secondaria
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = archetype.name,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = tColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                tLabel,
                                color = tColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(AppLocale.deckCountLabel(archetype.count), color = TextMuted, fontSize = 11.sp)

                        if (archetype.topPlacement <= 3) {
                            val trophyColor = when (archetype.topPlacement) {
                                1    -> Color(0xFFFFD700)
                                2    -> Color(0xFFC0C0C0)
                                else -> Color(0xFFCD7F32)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EmojiEvents, null, tint = trophyColor, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    "Top ${archetype.topPlacement}",
                                    color = trophyColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Trend indicator
                        if (trendInfo != null) {
                            val (trendIcon, trendColor) = trendInfo
                            Icon(trendIcon, null, tint = trendColor, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Meta % badge grande
                Surface(
                    color = tColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            "${"%.1f".format(archetype.metaShare)}%",
                            color = tColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                        Text("meta", color = tColor.copy(alpha = 0.75f), fontSize = 9.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Barra meta share ─────────────────────────────────────────
            val barFraction = (archetype.metaShare / 25.0).coerceIn(0.0, 1.0).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(DarkBackground)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(barFraction)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(listOf(tColor.copy(alpha = 0.4f), tColor))
                        )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Stats row + import ────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArchetypeStat(
                    label = "Win Rate",
                    value = "${(archetype.avgWinrate * 100).toInt()}%",
                    color = wrColor,
                    modifier = Modifier.weight(1f)
                )
                ArchetypeStat(
                    label = if (AppLocale.isItalian) "Piazzamento" else "Best Place",
                    value = "#${archetype.topPlacement}",
                    color = BlueCard,
                    modifier = Modifier.weight(1f)
                )
                ArchetypeStat(
                    label = "Deck",
                    value = "${archetype.count}",
                    color = LavenderCard,
                    modifier = Modifier.weight(0.8f)
                )

                // Import button
                if (onImport != null) {
                    Surface(
                        modifier = Modifier
                            .weight(1.1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onImport),
                        color = PurpleCard.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FileDownload, null, tint = PurpleCard, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(AppLocale.import, color = PurpleCard, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ── Support composables ────────────────────────────────────────────────────

@Composable
private fun ArchetypeStat(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(42.dp),
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(text = label, color = color.copy(alpha = 0.7f), fontSize = 8.5.sp)
        }
    }
}

@Composable
private fun SortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) BlueCard.copy(alpha = 0.2f) else DarkCard,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) BlueCard else TextMuted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
        )
    }
}
