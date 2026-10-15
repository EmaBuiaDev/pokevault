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
import com.emabuia.pokevault.ui.components.ArchetypeSpriteRow
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.MetaDeckViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
            ArchetypeSortMode.WIN_RATE      -> viewModel.archetypes.sortedByWinRate()
            // Senza piazzamento (0) in fondo, non in testa: vedi LimitlessPlacing.
            ArchetypeSortMode.BEST_PLACEMENT -> viewModel.archetypes.sortedWith(
                compareBy<MetaArchetype> { if (it.topPlacement > 0) 0 else 1 }.thenBy { it.topPlacement }
            )
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

    // Come per Win Tournament: i dati si chiedono all'apertura della tab, non
    // alla creazione del ViewModel.
    LaunchedEffect(Unit) { viewModel.ensureArchetypesLoaded() }

    Column(modifier = Modifier.fillMaxSize()) {
        MetaToolbar(
            infoBody = AppLocale.metaArchetypeInfoBody,
            selectedFormat = viewModel.selectedFormat,
            onFormatChange = { viewModel.selectFormat(it) },
            lastUpdated = viewModel.lastUpdated,
            rateLimitMessage = rateLimitedMessage,
            onRefresh = {
                rateLimitedMessage = if (!viewModel.refresh()) {
                    AppLocale.metaRefreshCooldown(viewModel.refreshCooldownSeconds)
                } else {
                    null
                }
            },
            tick = tick
        )

        // Sort selector + info tier
        if (viewModel.archetypes.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.Sort, null, tint = AppColors.textMuted, modifier = Modifier.size(15.dp))
                Text(
                    text = if (AppLocale.isItalian) "Ordina:" else "Sort:",
                    color = AppColors.textMuted,
                    fontSize = 11.sp
                )
                SortChip(
                    label = AppLocale.metaPresence,
                    selected = sortMode == ArchetypeSortMode.META_SHARE,
                    onClick = { sortMode = ArchetypeSortMode.META_SHARE }
                )
                SortChip(
                    label = AppLocale.metaWinRate,
                    selected = sortMode == ArchetypeSortMode.WIN_RATE,
                    onClick = { sortMode = ArchetypeSortMode.WIN_RATE }
                )
                SortChip(
                    label = AppLocale.metaSortPlacement,
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
                        tint = if (showTierInfo) AppColors.blue else AppColors.textMuted,
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

        val rateLimitWait = remember(viewModel.rateLimitedForSeconds, tick) {
            viewModel.currentRateLimitWait()
        }
        if (viewModel.rateLimitedForSeconds != null && viewModel.archetypes.isNotEmpty()) {
            MetaRateLimitNotice(secondsRemaining = rateLimitWait, hasData = true)
        }

        when {
            viewModel.rateLimitedForSeconds != null && viewModel.archetypes.isEmpty() -> {
                MetaRateLimitNotice(secondsRemaining = rateLimitWait, hasData = false)
            }

            viewModel.isLoadingArchetypes -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AppColors.blue, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (AppLocale.isItalian) "Caricamento archetipi..." else "Loading archetypes...",
                            color = AppColors.textMuted, fontSize = 13.sp
                        )
                    }
                }
            }

            viewModel.archetypeError != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
                        Icon(Icons.Default.CloudOff, null, tint = AppColors.red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (AppLocale.isItalian) "Errore di connessione" else "Connection error",
                            color = AppColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                        Text(viewModel.archetypeError ?: "", color = AppColors.textMuted, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { viewModel.loadArchetypes() }) {
                            Text(AppLocale.retry, color = AppColors.blue)
                        }
                    }
                }
            }

            viewModel.archetypes.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
                        Icon(Icons.Default.SearchOff, null, tint = AppColors.lavender, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(AppLocale.metaNoArchetypes, color = AppColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (AppLocale.isItalian) "Prova a cambiare formato." else "Try changing format.",
                            color = AppColors.textMuted, fontSize = 12.sp
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
                    item(key = "meta-summary") {
                        MetaSummaryCard(archetypes = viewModel.archetypes)
                    }

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
        color = AppColors.card,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = if (AppLocale.isItalian) "Cosa sono i Tier?" else "What are Tiers?",
                color = AppColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (AppLocale.isItalian)
                    "Il tier indica quanto un archetipo è diffuso fra i primi 32 classificati dei tornei recenti: la quota di quei deck che lo usa."
                else
                    "The tier shows how widespread an archetype is among the top 32 of recent tournaments: the share of those decks using it.",
                color = AppColors.textMuted,
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
                    Text(desc, color = AppColors.textMuted, fontSize = 11.sp, lineHeight = 15.sp)
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
        1    -> if (AppLocale.isItalian) "Dominante (≥15% nei top 32)" else "Dominant (≥15% in top 32)"
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
        Text(subtitle, color = AppColors.textMuted, fontSize = 10.sp)
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
        else -> AppColors.textMuted
    }

    val winRateLevel = archetype.winRateLevel()
    val wrColor = when (winRateLevel) {
        WinRateLevel.HIGH -> AppColors.green
        WinRateLevel.LOW -> AppColors.red
        WinRateLevel.AVERAGE -> AppColors.blue
        WinRateLevel.FEW_DATA -> AppColors.textMuted
    }

    // Trend: la quota nei tornei piu' recenti contro quella nei piu' vecchi.
    // Fino alla 3.1.8 confrontava i piazzamenti ordinati dal migliore al
    // peggiore, cioe' niente che avesse a che fare col tempo.
    val trendInfo: Triple<androidx.compose.ui.graphics.vector.ImageVector, Color, String>? =
        when (archetype.trendDirection()) {
            TrendDirection.UP -> Triple(Icons.AutoMirrored.Filled.TrendingUp, AppColors.green, AppLocale.metaTrendUp)
            TrendDirection.DOWN -> Triple(Icons.AutoMirrored.Filled.TrendingDown, AppColors.red, AppLocale.metaTrendDown)
            TrendDirection.FLAT -> Triple(Icons.AutoMirrored.Filled.TrendingFlat, AppColors.textMuted, AppLocale.metaTrendFlat)
            null -> null
        }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.card),
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

                Spacer(modifier = Modifier.width(8.dp))

                // I Pokemon che danno il nome all'archetipo: in una classifica
                // di nomi inglesi, la figura si riconosce prima della scritta.
                ArchetypeSpriteRow(
                    archetype = archetype.name,
                    size = 40.dp,
                    modifier = Modifier.padding(end = 6.dp)
                )

                // Nome + tier badge + info secondaria
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = archetype.name,
                            color = AppColors.textPrimary,
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
                        Text(AppLocale.deckCountLabel(archetype.count), color = AppColors.textMuted, fontSize = 11.sp)

                        if (archetype.topPlacement in 1..3) {
                            val trophyColor = when (archetype.topPlacement) {
                                1    -> Color(0xFFFFD700)
                                2    -> Color(0xFFC0C0C0)
                                else -> Color(0xFFCD7F32)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EmojiEvents, null, tint = trophyColor, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    placementWithPlayers(archetype),
                                    color = trophyColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Trend indicator
                        if (trendInfo != null) {
                            val (trendIcon, trendColor, trendLabel) = trendInfo
                            Icon(trendIcon, contentDescription = trendLabel, tint = trendColor, modifier = Modifier.size(14.dp))
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
                        Text(AppLocale.metaInTop32, color = tColor.copy(alpha = 0.75f), fontSize = 9.sp)
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
                    .background(AppColors.background)
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
                // Il win rate porta sempre con se' su quante partite e'
                // calcolato: sotto la soglia non si mostra il numero.
                ArchetypeStat(
                    label = if (winRateLevel == WinRateLevel.FEW_DATA) AppLocale.metaFewData
                        else AppLocale.metaGames(archetype.games),
                    value = if (winRateLevel == WinRateLevel.FEW_DATA) "—"
                        else "${(archetype.avgWinrate * 100).roundToInt()}%",
                    color = wrColor,
                    title = AppLocale.metaWinRate,
                    modifier = Modifier.weight(1.2f)
                )
                // "1° su 128": un primo posto a un torneo da 8 giocatori non
                // vale uno a un Regional, e "#1" li faceva sembrare uguali.
                ArchetypeStat(
                    label = if (archetype.topPlacementPlayers > 0) AppLocale.metaOfPlayers(archetype.topPlacementPlayers) else "",
                    value = if (archetype.topPlacement > 0) AppLocale.metaPlacementOf(archetype.topPlacement) else "—",
                    color = AppColors.blue,
                    title = AppLocale.metaBestPlacement,
                    modifier = Modifier.weight(1f)
                )
                ArchetypeStat(
                    label = AppLocale.metaTop8Times(archetype.top8Count),
                    value = "${archetype.top8Count}",
                    color = AppColors.lavender,
                    title = AppLocale.metaTop8,
                    modifier = Modifier.weight(0.9f)
                )

                // Import button
                if (onImport != null) {
                    Surface(
                        modifier = Modifier
                            .weight(1.1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onImport),
                        color = AppColors.purple.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FileDownload, null, tint = AppColors.purple, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(AppLocale.import, color = AppColors.purple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ── Support composables ────────────────────────────────────────────────────

/**
 * Un numero della card: cosa misura sopra, il valore, e sotto su cosa e'
 * calcolato ("su 140 partite", "su 128 giocatori").
 */
@Composable
private fun ArchetypeStat(
    label: String,
    value: String,
    color: Color,
    title: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(56.dp),
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = title, color = AppColors.textMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            if (label.isNotEmpty()) {
                Text(text = label, color = color.copy(alpha = 0.75f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun placementWithPlayers(archetype: MetaArchetype): String =
    if (archetype.topPlacementPlayers > 0) {
        "${AppLocale.metaPlacementOf(archetype.topPlacement)} ${AppLocale.metaOfPlayers(archetype.topPlacementPlayers)}"
    } else {
        AppLocale.metaPlacementOf(archetype.topPlacement)
    }

/**
 * In cima alla classifica: su cosa e' calcolata, e come si divide il meta fra
 * i primi otto archetipi. Prima la classifica partiva subito con le card, e
 * per capire quanto pesava il primo rispetto agli altri bisognava confrontare
 * a occhio i numeri di ognuna.
 */
@Composable
private fun MetaSummaryCard(archetypes: List<MetaArchetype>) {
    if (archetypes.isEmpty()) return
    val (top, others) = remember(archetypes) { archetypes.topShares() }
    val palette = listOf(
        AppColors.blue, AppColors.purple, AppColors.green, AppColors.orange,
        AppColors.red, AppColors.gold, AppColors.lavender, AppColors.yellow
    )
    val sample = archetypes.first()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.card)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = AppLocale.metaTopEightTitle,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            if (sample.sampleTournaments > 0) {
                Text(
                    text = AppLocale.metaSampleSummary(sample.sampleTournaments, sample.sampleDecks),
                    color = AppColors.textMuted,
                    fontSize = 11.sp
                )
            }

            // Una barra sola, divisa per quota: le proporzioni si vedono
            // senza leggere nessun numero.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
            ) {
                top.forEachIndexed { index, archetype ->
                    if (archetype.metaShare > 0.0) {
                        Box(
                            modifier = Modifier
                                .weight(archetype.metaShare.toFloat())
                                .fillMaxHeight()
                                .background(palette[index % palette.size])
                        )
                    }
                }
                if (others > 0.0) {
                    Box(
                        modifier = Modifier
                            .weight(others.toFloat())
                            .fillMaxHeight()
                            .background(AppColors.textMuted.copy(alpha = 0.25f))
                    )
                }
            }

            // Legenda in due colonne: nome e quota, nello stesso colore.
            top.chunked(2).forEachIndexed { rowIndex, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEachIndexed { columnIndex, archetype ->
                        val color = palette[(rowIndex * 2 + columnIndex) % palette.size]
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = archetype.name,
                                color = AppColors.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${"%.1f".format(archetype.metaShare)}%",
                                color = AppColors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
            if (others > 0.5) {
                Text(
                    text = "${AppLocale.metaOthers} ${"%.1f".format(others)}%",
                    color = AppColors.textMuted,
                    fontSize = 11.sp
                )
            }
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
        color = if (selected) AppColors.blue.copy(alpha = 0.2f) else AppColors.card,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) AppColors.blue else AppColors.textMuted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
        )
    }
}
