package com.emabuia.pokevault.ui.graded

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.ui.components.ErrorStateView
import com.emabuia.pokevault.ui.components.GradedSkeleton
import com.emabuia.pokevault.ui.components.LabSearchField
import com.emabuia.pokevault.ui.components.StatTile
import com.emabuia.pokevault.ui.components.formatEurCompact
import com.emabuia.pokevault.ui.components.holoFoil
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.navigation.sharedCardImage
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.AppMotion
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.GradeBucket
import com.emabuia.pokevault.util.GradeTier
import com.emabuia.pokevault.util.GradedLab
import com.emabuia.pokevault.util.GradedSort
import com.emabuia.pokevault.util.ImageUrlUtils
import com.emabuia.pokevault.viewmodel.GradedCardsUiState
import com.emabuia.pokevault.viewmodel.GradedCardsViewModel

/**
 * Le carte gradate.
 *
 * Erano una griglia di miniature con un bollino dorato uguale per tutte: un PSA
 * 10 e un PSA 6 si leggevano allo stesso modo, il totale non diceva se era
 * completo, i filtri per ente uscivano dallo schermo e quello delle carte senza
 * ente non selezionava niente. Adesso ogni elemento e' la slab che rappresenta —
 * etichetta dell'ente in cima, voto grande, colore della fascia — e in testa
 * alla sezione ci sono i tre numeri che contano piu' la forma della collezione.
 *
 * I conti stanno in [GradedLab], che e' testato: qui c'e' solo come si vedono.
 */

/**
 * Quanto si aspetta prima di mostrare lo scheletro.
 *
 * Stessa cortesia della riga collezione in Home: sotto la soglia la schermata non
 * dichiara niente. La cache di Firestore risponde quasi sempre entro pochi
 * frame, e far lampeggiare i riquadri grigi per due frame e' peggio che
 * aspettarli.
 */
private const val SkeletonGraceMs = 220L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradedCardsScreen(
    onBack: () -> Unit,
    onCardClick: (String) -> Unit = {},
    viewModel: GradedCardsViewModel = viewModel()
) {
    val state = viewModel.uiState
    var sortMenuOpen by remember { mutableStateOf(false) }

    val showSkeleton by produceState(initialValue = false, state.isLoading) {
        value = false
        if (state.isLoading) {
            kotlinx.coroutines.delay(SkeletonGraceMs)
            value = true
        }
    }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = AppLocale.gradedTitle,
                            color = AppColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (!state.isEmpty) {
                            // Il valore si aggiunge solo se c'e': "3 slab · —"
                            // sembra un dato rotto, non un dato che manca.
                            val count = AppLocale.gradedSlabsCount(state.summary.slabs)
                            Text(
                                text = if (state.summary.totalValue > 0.0) {
                                    "$count · ${formatEurCompact(state.summary.totalValue)}"
                                } else {
                                    count
                                },
                                color = AppColors.textMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
                actions = {
                    if (!state.isEmpty) {
                        Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(
                                    Icons.Default.SwapVert,
                                    contentDescription = AppLocale.gradedSort,
                                    tint = AppColors.textPrimary
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuOpen,
                                onDismissRequest = { sortMenuOpen = false }
                            ) {
                                GradedSort.entries.forEach { option ->
                                    val selected = option == state.sort
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = sortLabel(option),
                                                color = if (selected) AppColors.gold else AppColors.textPrimary,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        },
                                        trailingIcon = {
                                            if (selected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = AppColors.gold,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        onClick = {
                                            sortMenuOpen = false
                                            viewModel.setSort(option)
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                // Lo scheletro vale solo alla prima apertura: uno snapshot che
                // arriva mentre si guarda la griglia non deve cancellarla.
                state.isLoading && state.isEmpty -> {
                    if (showSkeleton) {
                        GradedSkeleton(modifier = Modifier.padding(top = 8.dp))
                    }
                }

                // L'errore prima del vuoto: senza questo ramo un caricamento
                // fallito diceva "non hai carte gradate", che e' falso e manda a
                // cercare un problema nella collezione.
                state.errorMessage != null && state.isEmpty -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        ErrorStateView(message = AppLocale.gradedLoadError)
                        Button(
                            onClick = { viewModel.retry() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.gold)
                        ) {
                            Text(
                                text = AppLocale.retry,
                                color = onAccentColor(AppColors.gold),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                state.isEmpty -> GradedEmptyState()

                else -> GradedGrid(
                    state = state,
                    onCardClick = onCardClick,
                    onQueryChange = viewModel::updateSearch,
                    onCompanyClick = viewModel::filterByCompany,
                    onTierClick = viewModel::filterByTier,
                    onClearFilters = viewModel::clearFilters
                )
            }
        }
    }
}

@Composable
private fun GradedGrid(
    state: GradedCardsUiState,
    onCardClick: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onCompanyClick: (String?) -> Unit,
    onTierClick: (GradeTier?) -> Unit,
    onClearFilters: () -> Unit
) {
    val summary = state.summary

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = AppLocale.gradedStatSlabs,
                        value = summary.slabs.toString(),
                        icon = Icons.Default.WorkspacePremium,
                        accent = AppColors.gold,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = AppLocale.gradedStatAverage,
                        value = GradedLab.formatGrade(summary.averageGrade.takeIf { it > 0f }),
                        icon = Icons.Default.Star,
                        accent = AppColors.green,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = AppLocale.gradedStatValue,
                        value = formatEurCompact(summary.totalValue),
                        icon = Icons.Default.Savings,
                        accent = AppColors.blue,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Una fascia sola non e' una distribuzione: la barra comparirebbe
                // piena di un colore unico e non direbbe niente.
                if (state.spread.size > 1) {
                    GradeSpread(buckets = state.spread, gems = summary.gems)
                }

                LabSearchField(
                    value = state.searchQuery,
                    onValueChange = onQueryChange,
                    hint = AppLocale.gradedSearchHint,
                    accent = AppColors.gold
                )

                if (state.companies.size > 1) {
                    ChipRow {
                        GradedChip(
                            label = AppLocale.gradedAllCompanies,
                            selected = state.selectedCompany == null,
                            accent = AppColors.gold,
                            onClick = { onCompanyClick(null) }
                        )
                        state.companies.forEach { company ->
                            GradedChip(
                                label = "${companyLabel(company.key)} ${company.count}",
                                selected = state.selectedCompany == company.key,
                                accent = AppColors.gold,
                                onClick = { onCompanyClick(company.key) }
                            )
                        }
                    }
                }

                if (state.spread.size > 1) {
                    ChipRow {
                        GradedChip(
                            label = AppLocale.gradedAllGrades,
                            selected = state.selectedTier == null,
                            accent = AppColors.lavender,
                            onClick = { onTierClick(null) }
                        )
                        state.spread.forEach { bucket ->
                            GradedChip(
                                label = "${tierLabel(bucket.tier)} ${bucket.count}",
                                selected = state.selectedTier == bucket.tier,
                                accent = tierColor(bucket.tier),
                                onClick = { onTierClick(bucket.tier) }
                            )
                        }
                    }
                }

                // Il totale in cima e' una cifra su cui si decide se assicurare o
                // vendere: se non e' completa deve dirlo.
                val note = when {
                    summary.unpricedSlabs > 0 -> AppLocale.gradedUnpricedNote(summary.unpricedSlabs)
                    summary.ungraded > 0 -> AppLocale.gradedUngradedNote(summary.ungraded)
                    else -> null
                }
                if (note != null) {
                    Text(text = note, color = AppColors.textMuted, fontSize = 11.sp)
                }
            }
        }

        if (state.visibleCards.isEmpty()) {
            item(key = "no-results", span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = AppLocale.gradedNoResults,
                        color = AppColors.textMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    if (state.hasFilters) {
                        Spacer(modifier = Modifier.height(10.dp))
                        GradedChip(
                            label = AppLocale.resetFilters,
                            selected = false,
                            accent = AppColors.gold,
                            onClick = onClearFilters
                        )
                    }
                }
            }
        }

        items(state.visibleCards, key = { it.id }) { card ->
            SlabCard(
                card = card,
                onClick = { onCardClick(card.id) },
                // Il riordino non e' un taglio: le slab scivolano al loro nuovo
                // posto, cosi' si vede che sono le stesse carte messe in fila
                // in un altro modo.
                modifier = Modifier.animateItem()
            )
        }
    }
}

/**
 * La slab.
 *
 * L'etichetta in cima e' quella che l'ente stampa sul blocco: ente a sinistra,
 * fascia sotto, voto nel riquadro a destra. E' il pezzo che si guarda per primo
 * su una carta gradata — sul cartoncino vero e nella vetrina di un negozio — ed
 * e' il motivo per cui qui sta sopra l'immagine e non appiccicato sopra di essa.
 */
@Composable
private fun SlabCard(
    card: PokemonCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tier = GradedLab.tierOf(card.grade)
    val accent = tierColor(tier)
    val isGem = tier == GradeTier.GEM
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(AppColors.card)
            .border(1.dp, accent.copy(alpha = if (isGem) 0.55f else 0.22f), shape)
            .pressScale(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    // Il 10 e' l'unico che si prende un gradiente e la lamina:
                    // e' il traguardo, e nella sezione ce ne sono pochi.
                    if (isGem) {
                        Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.42f), accent.copy(alpha = 0.14f))
                        )
                    } else {
                        SolidColor(accent.copy(alpha = 0.16f))
                    }
                )
                .holoFoil(enabled = isGem)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = companyLabel(GradedLab.companyKey(card)),
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tierLabel(tier),
                    color = AppColors.textMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = GradedLab.formatGrade(card.grade),
                    color = onAccentColor(accent),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.72f)) {
            if (card.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageUrlUtils.safeProxiedImageUrl(card.imageUrl),
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedCardImage(card.id)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(accent.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = accent.copy(alpha = 0.5f),
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            if (card.quantity > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(AppColors.background.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "×${card.quantity}",
                        color = AppColors.textPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = card.name,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = AppLocale.displaySetName(card.set).ifBlank { AppLocale.noSet },
                    color = AppColors.textMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val value = GradedLab.slabValue(card)
                Text(
                    text = formatEurCompact(value),
                    color = if (value > 0.0) AppColors.green else AppColors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * La forma della collezione in una riga.
 *
 * Non e' un grafico: e' una barra divisa in fasce, larga in proporzione a quante
 * slab ci sono in ognuna. Dice in un colpo d'occhio la cosa che i tre numeri
 * sopra non dicono — se i 9 sono la norma o l'eccezione — e non e' toccabile di
 * proposito: i filtri sono i chip sotto, con le loro aree da dito.
 */
@Composable
private fun GradeSpread(buckets: List<GradeBucket>, gems: Int) {
    val total = buckets.sumOf { it.count }.coerceAtLeast(1)

    // Cresce da sinistra come le FillBar del resto dell'app, una volta sola.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = AppMotion.bar, easing = AppMotion.easing),
        label = "spread"
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .graphicsLayer {
                    scaleX = progress
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            buckets.forEach { bucket ->
                Box(
                    modifier = Modifier
                        .weight(bucket.count.toFloat() / total)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(tierColor(bucket.tier))
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = AppLocale.gradedSpread,
                color = AppColors.textMuted,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )
            if (gems > 0) {
                Icon(
                    Icons.Default.Diamond,
                    contentDescription = null,
                    tint = AppColors.gold,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = AppLocale.gradedGemCount(gems),
                    color = AppColors.gold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Fila di chip che scorre invece di andare a capo o uscire dallo schermo. */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

/**
 * Chip di filtro.
 *
 * Stessa forma di [com.emabuia.pokevault.ui.components.SortChipRow], ma qui ogni
 * chip ha un accento proprio — quello della sua fascia di voto — e quel
 * componente ne impone uno per tutta la fila.
 */
@Composable
private fun GradedChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    // Il passaggio fra spento e acceso e' interpolato: toccando un chip il
    // colore arriva invece di scattare, ed e' quello che fa sembrare la fila un
    // gruppo di interruttori e non quattro testi che cambiano.
    val spec = tween<Color>(AppMotion.state)
    val fill by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.22f) else AppColors.card,
        animationSpec = spec,
        label = "chipFill"
    )
    val stroke by animateColorAsState(
        targetValue = if (selected) accent else AppColors.textMuted.copy(alpha = 0.22f),
        animationSpec = spec,
        label = "chipStroke"
    )
    val text by animateColorAsState(
        targetValue = if (selected) accent else AppColors.textSecondary,
        animationSpec = spec,
        label = "chipText"
    )

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, stroke, CircleShape)
            .pressScale(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/** Il vuoto: dice dove si marca una carta come gradata, non solo che non ce ne sono. */
@Composable
private fun GradedEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.WorkspacePremium,
            contentDescription = null,
            tint = AppColors.gold.copy(alpha = 0.55f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = AppLocale.gradedEmptyTitle,
            color = AppColors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = AppLocale.gradedEmptySubtitle,
            color = AppColors.textMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ── Etichette e colori delle fasce ────────────────────────────────────────

/**
 * Il colore di una fascia.
 *
 * Oro solo per il 10, poi verde, blu, arancio e rosso: e' la stessa scala che
 * l'app usa per il completamento di un set, quindi si legge senza legenda.
 */
@Composable
private fun tierColor(tier: GradeTier): Color = when (tier) {
    GradeTier.GEM -> AppColors.gold
    GradeTier.MINT -> AppColors.green
    GradeTier.NEAR_MINT -> AppColors.blue
    GradeTier.EXCELLENT -> AppColors.orange
    GradeTier.PLAYED -> AppColors.red
    GradeTier.UNGRADED -> AppColors.textMuted
}

private fun tierLabel(tier: GradeTier): String = when (tier) {
    GradeTier.GEM -> AppLocale.gradedTierGem
    GradeTier.MINT -> AppLocale.gradedTierMint
    GradeTier.NEAR_MINT -> AppLocale.gradedTierNearMint
    GradeTier.EXCELLENT -> AppLocale.gradedTierExcellent
    GradeTier.PLAYED -> AppLocale.gradedTierPlayed
    GradeTier.UNGRADED -> AppLocale.gradedTierUngraded
}

private fun companyLabel(key: String): String =
    if (key == GradedLab.UNKNOWN_COMPANY) AppLocale.gradedNoCompany else key

private fun sortLabel(sort: GradedSort): String = when (sort) {
    GradedSort.GRADE_DESC -> AppLocale.gradedSortGrade
    GradedSort.VALUE_DESC -> AppLocale.gradedSortValue
    GradedSort.NAME -> AppLocale.gradedSortName
    GradedSort.RECENT -> AppLocale.gradedSortRecent
}

/**
 * Testo leggibile sopra un accento pieno.
 *
 * Deciso dalla luminanza e non scritto a mano: l'oro del tema scuro (0xFFFFD700)
 * vuole testo nero, quello del tema chiaro (0xFFB7950B) lo vuole bianco, e il
 * nero fisso di prima spariva su meta' degli accenti.
 */
private fun onAccentColor(accent: Color): Color =
    if (accent.luminance() > 0.45f) Color.Black else Color.White
