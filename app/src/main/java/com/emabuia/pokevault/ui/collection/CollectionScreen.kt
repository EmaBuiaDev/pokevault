package com.emabuia.pokevault.ui.collection

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.ui.components.CardImageSkeleton
import com.emabuia.pokevault.ui.components.CardVariants
import com.emabuia.pokevault.ui.components.CollectionSkeleton
import com.emabuia.pokevault.ui.components.LabSearchField
import com.emabuia.pokevault.ui.components.OwnedVariantBadges
import com.emabuia.pokevault.ui.components.RaritySymbolIcon
import com.emabuia.pokevault.ui.components.formatEur
import com.emabuia.pokevault.ui.components.holoFoil
import com.emabuia.pokevault.ui.home.components.SearchBar
import com.emabuia.pokevault.ui.navigation.sharedCardImage
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.CardCategory
import com.emabuia.pokevault.util.CardGroup
import com.emabuia.pokevault.util.CollectionFilter
import com.emabuia.pokevault.util.CollectionLayout
import com.emabuia.pokevault.util.CollectionSort
import com.emabuia.pokevault.util.ExpansionGroupSection
import com.emabuia.pokevault.util.ExpansionOrder
import com.emabuia.pokevault.util.FacetCount
import com.emabuia.pokevault.util.ImageUrlUtils
import com.emabuia.pokevault.util.RarityUtils
import com.emabuia.pokevault.util.ValueBucket
import com.emabuia.pokevault.viewmodel.CollectionUiState
import com.emabuia.pokevault.viewmodel.CollectionViewModel

private const val PREFS_NAME = "collection_view_prefs"

/** Quante espansioni mostra il filtro prima di "Mostra tutte". */
private const val EXPANSIONS_PREVIEW = 12

// ── Etichette ─────────────────────────────────────────────────────────────────

private fun CollectionSort.label(): String = when (this) {
    CollectionSort.NUMBER -> AppLocale.sortNumber
    CollectionSort.NEWEST -> AppLocale.sortRecent
    CollectionSort.NAME -> AppLocale.sortNameAsc
    CollectionSort.PRICE_DESC -> AppLocale.sortPriceHigh
    CollectionSort.PRICE_ASC -> AppLocale.sortPriceLow
}

private fun ExpansionOrder.label(): String = when (this) {
    ExpansionOrder.NAME -> "A-Z"
    ExpansionOrder.RECENT -> AppLocale.sortRecent
    ExpansionOrder.MOST_CARDS -> AppLocale.moreCards
    ExpansionOrder.FEWEST_CARDS -> AppLocale.fewerCards
    ExpansionOrder.MOST_VALUE -> AppLocale.expansionOrderMostValue
}

private fun CardCategory.label(): String = when (this) {
    CardCategory.ALL -> AppLocale.categoryAll
    CardCategory.POKEMON -> AppLocale.categoryPokemon
    CardCategory.TRAINER -> AppLocale.categoryTrainer
    CardCategory.ENERGY -> AppLocale.categoryEnergy
}

private fun ValueBucket.label(): String = when (this) {
    ValueBucket.NO_PRICE -> AppLocale.valueNoPrice
    ValueBucket.UNDER_1 -> AppLocale.valueUnder1
    ValueBucket.FROM_1_TO_10 -> AppLocale.value1to10
    ValueBucket.FROM_10_TO_50 -> AppLocale.value10to50
    ValueBucket.OVER_50 -> AppLocale.valueOver50
}

// ── Schermata ─────────────────────────────────────────────────────────────────

/**
 * Le mie carte.
 *
 * Due modi di guardare la stessa collezione: **per espansione**, la
 * fisarmonica di sempre, e **tutte**, una griglia unica. La seconda serve
 * perche' ordinare per prezzo o per data dentro sezioni chiuse non diceva
 * niente: la carta piu' cara della collezione stava in cima alla sua
 * espansione, magari la trentesima della lista.
 *
 * Raggruppamento, filtri e ordinamenti sono calcolati nel ViewModel, fuori dal
 * thread principale: qui si disegna e basta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    onBack: () -> Unit,
    onAddCard: () -> Unit,
    onCardClick: (String) -> Unit,
    viewModel: CollectionViewModel = viewModel()
) {
    val state = viewModel.uiState
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.attachPreferences(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedGroupKeys by remember { mutableStateOf(setOf<String>()) }
    var showFilters by remember { mutableStateOf(false) }

    fun exitSelection() {
        isSelectionMode = false
        selectedGroupKeys = emptySet()
    }

    BackHandler(enabled = isSelectionMode) { exitSelection() }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.successMessage, state.errorMessage) {
        val msg = state.successMessage ?: state.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    val hasActiveFilters = !state.filter.isEmpty
    // In vista per espansione, un ordinamento diverso dal numero apre le
    // sezioni come faceva prima: dentro sezioni chiuse non si vedrebbe.
    val shouldExpandAll = hasActiveFilters || state.sort != CollectionSort.NUMBER
    val visibleExpansionNames = remember(state.sections) { state.sections.map { it.expansion }.toSet() }

    // Espansioni aperte. Chiuse all'ingresso: con collezioni grandi aprirle
    // tutte subito costava.
    // Salvate come lista, e non affidate al salvataggio automatico: tornando
    // dal dettaglio di una carta prima si richiudevano tutte, perche' stavano
    // in un remember che la navigazione butta via.
    var expandedExpansions by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })
    ) { mutableStateOf(setOf<String>()) }
    var wasExpandingAll by remember { mutableStateOf(shouldExpandAll) }
    LaunchedEffect(shouldExpandAll, visibleExpansionNames) {
        expandedExpansions = when {
            shouldExpandAll -> expandedExpansions + visibleExpansionNames
            // Si esce da un filtro: si torna tutto chiuso, come prima.
            wasExpandingAll -> emptySet()
            // Senza filtri si tolgono solo le espansioni sparite. Prima ogni
            // cambio dell'elenco (una carta aggiunta in un set nuovo, l'ultima
            // di un set cancellata) richiudeva tutte quelle aperte a mano.
            else -> expandedExpansions intersect visibleExpansionNames
        }
        wasExpandingAll = shouldExpandAll
    }

    // Le immagini delle prime tessere visibili si chiedono in anticipo, cosi'
    // aprire una sezione non mostra una griglia di scheletri.
    LaunchedEffect(state.visibleGroups, state.layout, expandedExpansions, state.isGridView) {
        val max = if (state.isGridView) 72 else 48
        val source = if (state.layout == CollectionLayout.ALL) {
            state.visibleGroups.asSequence()
        } else {
            state.sections.asSequence()
                .filter { it.expansion in expandedExpansions }
                .flatMap { it.groups.asSequence() }
        }
        source.take(max)
            .map { ImageUrlUtils.safeProxiedImageUrl(it.representative.imageUrl) }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { url ->
                context.imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(url)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .build()
                )
            }
    }

    fun onTileClick(key: String) {
        if (isSelectionMode) {
            selectedGroupKeys = if (key in selectedGroupKeys) selectedGroupKeys - key else selectedGroupKeys + key
            if (selectedGroupKeys.isEmpty()) isSelectionMode = false
        } else {
            onCardClick(key)
        }
    }

    fun onTileLongClick(key: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        isSelectionMode = true
        selectedGroupKeys = selectedGroupKeys + key
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSelectionMode) AppLocale.selectedCount(selectedGroupKeys.size) else AppLocale.myCardsSingleLine,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.textPrimary
                    )
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Default.Close, AppLocale.cancel, tint = AppColors.textPrimary)
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = AppColors.textPrimary)
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        val allKeys = state.visibleGroups.map { it.key }.toSet()
                        val allSelected = allKeys.isNotEmpty() && selectedGroupKeys.containsAll(allKeys)
                        IconButton(onClick = { selectedGroupKeys = if (allSelected) emptySet() else allKeys }) {
                            Icon(
                                imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = AppLocale.selectAll,
                                tint = AppColors.textPrimary
                            )
                        }
                    } else {
                        if (state.isGridView) {
                            IconButton(onClick = { viewModel.toggleGridColumns() }) {
                                Icon(
                                    imageVector = when (state.gridColumns) {
                                        2 -> Icons.Default.ViewModule
                                        3 -> Icons.Default.GridView
                                        4 -> Icons.Default.Apps
                                        else -> Icons.Default.ViewComfy
                                    },
                                    contentDescription = AppLocale.changeGridDensity,
                                    tint = AppColors.textPrimary
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                            Icon(
                                imageVector = if (state.isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = AppLocale.changeView,
                                tint = AppColors.textPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                SummaryStrip(state = state)

                if (!isSelectionMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SearchBar(
                                query = state.searchQuery,
                                onQueryChange = { viewModel.updateSearchQuery(it) }
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        FilterButton(
                            activeCount = state.filter.activeCount,
                            onClick = { showFilters = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Toolbar(
                        state = state,
                        onLayout = viewModel::setLayout,
                        onSort = viewModel::setSort,
                        onExpansionOrder = viewModel::setExpansionOrder,
                        canExpandAll = state.layout == CollectionLayout.BY_EXPANSION &&
                            visibleExpansionNames.isNotEmpty() &&
                            !expandedExpansions.containsAll(visibleExpansionNames),
                        onToggleExpandAll = {
                            expandedExpansions = if (expandedExpansions.containsAll(visibleExpansionNames)) {
                                emptySet()
                            } else {
                                visibleExpansionNames
                            }
                        }
                    )

                    if (state.filter.activeCount > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ActiveFiltersRow(filter = state.filter, viewModel = viewModel)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Crossfade(
                    targetState = state.isLoading,
                    animationSpec = tween(AppMotion.crossfade),
                    label = "collectionLoading"
                ) { isLoadingContent ->
                    when {
                        isLoadingContent -> CollectionSkeleton()
                        state.groups.isEmpty() -> EmptyCollection(onAddCard = onAddCard)
                        state.visibleGroups.isEmpty() -> NoResults(onClear = { viewModel.clearFiltersAndSearch() })
                        state.layout == CollectionLayout.ALL -> AllCardsContent(
                            groups = state.visibleGroups,
                            isGridView = state.isGridView,
                            gridColumns = state.gridColumns,
                            selectedKeys = selectedGroupKeys,
                            isSelectionMode = isSelectionMode,
                            onClick = ::onTileClick,
                            onLongClick = ::onTileLongClick
                        )
                        else -> ByExpansionContent(
                            sections = state.sections,
                            expanded = expandedExpansions,
                            onToggle = { name ->
                                expandedExpansions = if (name in expandedExpansions) expandedExpansions - name
                                else expandedExpansions + name
                            },
                            isGridView = state.isGridView,
                            gridColumns = state.gridColumns,
                            selectedKeys = selectedGroupKeys,
                            isSelectionMode = isSelectionMode,
                            onClick = ::onTileClick,
                            onLongClick = ::onTileLongClick
                        )
                    }
                }
            }

            if (isSelectionMode && selectedGroupKeys.isNotEmpty()) {
                SelectionBar(
                    count = selectedGroupKeys.size,
                    onCancel = { exitSelection() },
                    onDelete = {
                        viewModel.deleteMultipleGroups(selectedGroupKeys)
                        exitSelection()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        if (showFilters) {
            FilterSheet(
                state = state,
                viewModel = viewModel,
                onDismiss = { showFilters = false }
            )
        }
    }
}

// ── Riepilogo in cima ─────────────────────────────────────────────────────────

/**
 * Valore, carte e uniche in una striscia sola. Coi filtri attivi dice anche
 * quante ne stai guardando: "42 di 380 carte", e quanto valgono.
 */
@Composable
private fun SummaryStrip(state: CollectionUiState) {
    val filtered = !state.filter.isEmpty && !state.isLoading
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(listOf(AppColors.blue.copy(alpha = 0.16f), AppColors.surface))
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(AppLocale.collectionStatValue, color = AppColors.textMuted, fontSize = 11.sp)
                Text(
                    formatEur(state.stats.totalValue),
                    color = AppColors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            SummaryNumber(value = state.stats.totalCards, label = AppLocale.collectionStatCards, color = AppColors.blue)
            Spacer(modifier = Modifier.width(18.dp))
            SummaryNumber(value = state.stats.uniqueCards, label = AppLocale.collectionStatUnique, color = AppColors.purple)
        }
        if (filtered) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                AppLocale.collectionResults(state.visibleGroups.size, state.groups.size) +
                    " · " + formatEur(state.visibleValue),
                color = AppColors.blue,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SummaryNumber(value: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text("$value", color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = AppColors.textMuted, fontSize = 11.sp)
    }
}

@Composable
private fun FilterButton(activeCount: Int, onClick: () -> Unit) {
    val active = activeCount > 0
    Surface(
        onClick = onClick,
        color = if (active) AppColors.blue else AppColors.searchBar,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.size(50.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = AppLocale.filters,
                tint = if (active) Color.White else AppColors.textPrimary,
                modifier = Modifier.size(20.dp)
            )
            // Il numero, non un pallino: "3 filtri attivi" si legge da qui.
            if (active) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(AppColors.red),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$activeCount",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                }
            }
        }
    }
}

// ── Barra: vista e ordinamento ────────────────────────────────────────────────

/**
 * Vista e ordinamento in una riga. Prima c'erano due ordinamenti in due posti
 * -- le carte nel pannello filtri, le espansioni in una fila di chip sotto la
 * ricerca -- e non si capiva quale comandasse cosa. Ora un solo menu, diviso
 * in "Carte" ed "Espansioni".
 */
@Composable
private fun Toolbar(
    state: CollectionUiState,
    onLayout: (CollectionLayout) -> Unit,
    onSort: (CollectionSort) -> Unit,
    onExpansionOrder: (ExpansionOrder) -> Unit,
    canExpandAll: Boolean,
    onToggleExpandAll: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SegmentedLayout(selected = state.layout, onSelect = onLayout)
        Spacer(modifier = Modifier.weight(1f))

        if (state.layout == CollectionLayout.BY_EXPANSION && state.sections.isNotEmpty()) {
            IconButton(onClick = onToggleExpandAll, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (canExpandAll) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                    contentDescription = if (canExpandAll) AppLocale.expandAll else AppLocale.collapseAll,
                    tint = AppColors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.searchBar)
                    .clickable { menuOpen = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Sort,
                    contentDescription = AppLocale.sortMenuTitle,
                    tint = AppColors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    state.sort.label(),
                    color = AppColors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = AppColors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = AppColors.surface
            ) {
                MenuHeader(AppLocale.sortSectionCards)
                CollectionSort.entries.forEach { sort ->
                    MenuOption(
                        label = sort.label(),
                        selected = state.sort == sort,
                        onClick = { onSort(sort); menuOpen = false }
                    )
                }
                if (state.layout == CollectionLayout.BY_EXPANSION) {
                    HorizontalDivider(color = AppColors.textMuted.copy(alpha = 0.2f))
                    MenuHeader(AppLocale.sortSectionExpansions)
                    ExpansionOrder.entries.forEach { order ->
                        MenuOption(
                            label = order.label(),
                            selected = state.expansionOrder == order,
                            onClick = { onExpansionOrder(order); menuOpen = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text.uppercase(),
        color = AppColors.textMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
private fun MenuOption(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                color = if (selected) AppColors.blue else AppColors.textPrimary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp
            )
        },
        trailingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.blue, modifier = Modifier.size(18.dp)) }
        } else null,
        onClick = onClick
    )
}

@Composable
private fun SegmentedLayout(selected: CollectionLayout, onSelect: (CollectionLayout) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.searchBar)
            .padding(3.dp)
    ) {
        listOf(
            CollectionLayout.BY_EXPANSION to AppLocale.collectionLayoutByExpansion,
            CollectionLayout.ALL to AppLocale.collectionLayoutAll
        ).forEach { (layout, label) ->
            val isSelected = layout == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) AppColors.blue else Color.Transparent)
                    .clickable { onSelect(layout) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    label,
                    color = if (isSelected) Color.White else AppColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

// ── Contenuto: tutte le carte ─────────────────────────────────────────────────

@Composable
private fun AllCardsContent(
    groups: List<CardGroup>,
    isGridView: Boolean,
    gridColumns: Int,
    selectedKeys: Set<String>,
    isSelectionMode: Boolean,
    onClick: (String) -> Unit,
    onLongClick: (String) -> Unit
) {
    val bottom = if (isSelectionMode) 88.dp else 24.dp
    if (isGridView) {
        val spacing = if (gridColumns > 4) 6.dp else 10.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.fillMaxSize()
        ) {
            gridItems(groups, key = { it.key }) { group ->
                CollectionCardGridItem(
                    card = group.representative,
                    isSelected = group.key in selectedKeys,
                    isSelectionMode = isSelectionMode,
                    gridColumns = gridColumns,
                    ownedVariants = group.variants,
                    sharedKey = group.key,
                    onClick = { onClick(group.key) },
                    onLongClick = { onLongClick(group.key) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(groups, key = { it.key }) { group ->
                CollectionCardListItem(
                    group = group,
                    isSelected = group.key in selectedKeys,
                    isSelectionMode = isSelectionMode,
                    showExpansion = true,
                    onClick = { onClick(group.key) },
                    onLongClick = { onLongClick(group.key) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

// ── Contenuto: per espansione ─────────────────────────────────────────────────

@Composable
private fun ByExpansionContent(
    sections: List<ExpansionGroupSection>,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    isGridView: Boolean,
    gridColumns: Int,
    selectedKeys: Set<String>,
    isSelectionMode: Boolean,
    onClick: (String) -> Unit,
    onLongClick: (String) -> Unit
) {
    // Le righe della griglia si ricalcolano solo se cambiano sezioni o colonne,
    // non a ogni ricomposizione del contenuto.
    val gridRows = remember(sections, gridColumns) {
        sections.associate { it.expansion to it.groups.chunked(gridColumns) }
    }
    val cardSpacing = if (gridColumns > 4) 6.dp else 10.dp

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (isSelectionMode) 88.dp else 24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        sections.forEachIndexed { index, section ->
            val name = section.expansion
            val isExpanded = name in expanded

            if (index > 0) {
                item(key = "gap_$name") { Spacer(modifier = Modifier.height(10.dp)) }
            }

            item(key = "hdr_$name") {
                ExpansionAccordionHeader(
                    expansionName = section.label,
                    totalCards = section.totalQuantity,
                    totalValue = section.totalValue,
                    uniqueCards = section.groups.size,
                    isExpanded = isExpanded,
                    onToggle = { onToggle(name) }
                )
            }

            if (isExpanded) {
                if (isGridView) {
                    itemsIndexed(
                        items = gridRows[name].orEmpty(),
                        // Chiave sull'indice di riga: col primo elemento della
                        // riga due righe potevano collidere.
                        key = { rowIndex, _ -> "row_${name}_$rowIndex" }
                    ) { _, row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(cardSpacing),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppColors.card)
                                .padding(horizontal = 10.dp, vertical = if (gridColumns > 4) 3.dp else 5.dp)
                        ) {
                            row.forEach { group ->
                                CollectionCardGridItem(
                                    card = group.representative,
                                    isSelected = group.key in selectedKeys,
                                    isSelectionMode = isSelectionMode,
                                    gridColumns = gridColumns,
                                    ownedVariants = group.variants,
                                    sharedKey = group.key,
                                    onClick = { onClick(group.key) },
                                    onLongClick = { onLongClick(group.key) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(gridColumns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                } else {
                    items(section.groups, key = { "card_${name}_${it.key}" }) { group ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppColors.card)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            CollectionCardListItem(
                                group = group,
                                isSelected = group.key in selectedKeys,
                                isSelectionMode = isSelectionMode,
                                showExpansion = false,
                                onClick = { onClick(group.key) },
                                onLongClick = { onLongClick(group.key) }
                            )
                        }
                    }
                }

                item(key = "btm_$name") {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                            .background(AppColors.card)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpansionAccordionHeader(
    expansionName: String,
    totalCards: Int,
    totalValue: Double,
    uniqueCards: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val shape = if (isExpanded) RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp) else RoundedCornerShape(12.dp)
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(AppMotion.chevron),
        label = "expansionChevron"
    )
    // Il bordo e' il testo attenuato e non un bianco fisso: il bianco al 8%
    // spariva nel tema chiaro, e la card si confondeva col fondo.
    Surface(
        color = AppColors.card,
        shape = shape,
        border = BorderStroke(1.dp, AppColors.textMuted.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expansionName,
                    color = AppColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = AppLocale.expansionCardsAndCopies(uniqueCards, totalCards),
                    color = AppColors.textMuted,
                    fontSize = 11.sp
                )
            }
            if (totalValue > 0.0) {
                Text(
                    text = formatEur(totalValue),
                    color = AppColors.green,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            // Una freccia sola che ruota, non due icone che si scambiano: lo
            // scambio secco non dice in che verso sta andando la sezione.
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = AppColors.textMuted,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = chevronRotation }
            )
        }
    }
}

// ── Stati vuoti ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyCollection(onAddCard: () -> Unit) {
    CenteredMessage(
        icon = Icons.Default.CollectionsBookmark,
        title = AppLocale.emptyCollectionTitle,
        subtitle = AppLocale.emptyCollectionHint,
        action = AppLocale.addCard,
        onAction = onAddCard
    )
}

/**
 * Nessun risultato per i filtri: non e' una collezione vuota, e deve dirlo --
 * prima le due situazioni mostravano la stessa scritta, e con un filtro
 * dimenticato sembrava che le carte fossero sparite.
 */
@Composable
private fun NoResults(onClear: () -> Unit) {
    CenteredMessage(
        icon = Icons.Default.FilterAltOff,
        title = AppLocale.noResultsTitle,
        subtitle = null,
        action = AppLocale.noResultsAction,
        onAction = onClear
    )
}

@Composable
private fun CenteredMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    action: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.textMuted.copy(alpha = 0.6f), modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, color = AppColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = AppColors.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(action, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Selezione ─────────────────────────────────────────────────────────────────

@Composable
private fun SelectionBar(
    count: Int,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirm by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, AppColors.surface.copy(alpha = 0.95f), AppColors.surface))
            )
            .padding(top = 16.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, null, tint = AppColors.textMuted)
            }
            Text(
                text = AppLocale.selectedCount(count),
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            if (showConfirm) {
                Button(
                    onClick = { showConfirm = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.red),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(AppLocale.confirm, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { showConfirm = false },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AppColors.textMuted.copy(alpha = 0.3f)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(AppLocale.cancel, color = AppColors.textMuted, fontSize = 13.sp)
                }
            } else {
                Button(
                    onClick = { showConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.red),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(AppLocale.delete, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

// ── Filtri attivi ─────────────────────────────────────────────────────────────

/** Ogni filtro attivo come chip da togliere col tocco. */
@Composable
private fun ActiveFiltersRow(filter: CollectionFilter, viewModel: CollectionViewModel) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (filter.category != CardCategory.ALL) {
            item(key = "cat") { RemovableFilterChip(filter.category.label()) { viewModel.setCategory(CardCategory.ALL) } }
        }
        items(filter.types.toList(), key = { "t_$it" }) { RemovableFilterChip(it) { viewModel.toggleType(it) } }
        items(filter.rarities.toList(), key = { "r_$it" }) { RemovableFilterChip(it) { viewModel.toggleRarity(it) } }
        items(filter.expansions.toList(), key = { "e_$it" }) { RemovableFilterChip(it) { viewModel.toggleExpansion(it) } }
        items(filter.variants.toList(), key = { "v_$it" }) { RemovableFilterChip(CardVariants.label(it)) { viewModel.toggleVariant(it) } }
        items(filter.languages.toList(), key = { "l_$it" }) { RemovableFilterChip(it) { viewModel.toggleLanguage(it) } }
        items(filter.values.toList(), key = { "p_${it.name}" }) { RemovableFilterChip(it.label()) { viewModel.toggleValue(it) } }
        if (filter.onlyDuplicates) {
            item(key = "dup") { RemovableFilterChip(AppLocale.filterOnlyDuplicates) { viewModel.setOnlyDuplicates(false) } }
        }
        item(key = "clear") {
            TextButton(onClick = { viewModel.clearFilters() }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(AppLocale.filtersClear, color = AppColors.blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RemovableFilterChip(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.blue.copy(alpha = 0.15f))
            .border(1.dp, AppColors.blue.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(onClick = onRemove)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = AppColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(Icons.Default.Close, contentDescription = null, tint = AppColors.textMuted, modifier = Modifier.size(14.dp))
    }
}

// ── Pannello filtri ───────────────────────────────────────────────────────────

/**
 * Il pannello filtri.
 *
 * Ogni scelta si applica subito, e il tasto in fondo dice quante carte
 * restano: si vede l'effetto prima di chiudere. I chip vanno a capo invece di
 * scorrere di lato -- con cento espansioni la fila orizzontale di prima non si
 * usava -- e ognuno dice quante carte contiene.
 *
 * Niente riquadri `card` dentro il pannello: nel tema chiaro `surface` e
 * `card` sono lo stesso bianco, e le sezioni sparivano.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    state: CollectionUiState,
    viewModel: CollectionViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val facets = state.facets
    val filter = state.filter

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.textMuted.copy(alpha = 0.45f)) }
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    AppLocale.filters,
                    color = AppColors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { viewModel.clearFilters() },
                    enabled = filter.activeCount > 0
                ) {
                    Text(
                        AppLocale.filtersClear,
                        color = if (filter.activeCount > 0) AppColors.blue else AppColors.textMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                FilterSection(AppLocale.filterCategory) {
                    CardCategory.entries.forEach { category ->
                        val count = facets.categories[category] ?: 0
                        if (category == CardCategory.ALL || count > 0) {
                            SelectChip(
                                label = category.label(),
                                count = count,
                                selected = filter.category == category,
                                onClick = { viewModel.setCategory(category) }
                            )
                        }
                    }
                }

                // I tipi solo dove hanno senso: su Allenatori ed Energie non
                // filtrerebbero niente.
                val showTypes = filter.category == CardCategory.ALL || filter.category == CardCategory.POKEMON
                if (showTypes && facets.types.isNotEmpty()) {
                    FilterSection(AppLocale.filterType) {
                        facets.types.forEach { (type, count) ->
                            SelectChip(
                                label = type,
                                count = count,
                                selected = type in filter.types,
                                onClick = { viewModel.toggleType(type) }
                            )
                        }
                    }
                }

                if (facets.rarities.isNotEmpty()) {
                    FilterSection(AppLocale.rarity) {
                        facets.rarities.forEach { (label, count) ->
                            val info = remember(label) { RarityUtils.getRarityInfo(facets.raritySamples[label]) }
                            SelectChip(
                                label = label,
                                count = count,
                                selected = label in filter.rarities,
                                onClick = { viewModel.toggleRarity(label) },
                                leading = { RaritySymbolIcon(info, size = 11.dp) }
                            )
                        }
                    }
                }

                if (facets.variants.size > 1) {
                    FilterSection(AppLocale.filterVariant) {
                        facets.variants.forEach { (variant, count) ->
                            SelectChip(
                                label = CardVariants.label(variant),
                                count = count,
                                selected = variant in filter.variants,
                                onClick = { viewModel.toggleVariant(variant) },
                                leading = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(CardVariants.color(variant))
                                    )
                                }
                            )
                        }
                    }
                }

                if (facets.expansions.size > 1) {
                    ExpansionFilterSection(
                        expansions = facets.expansions,
                        selected = filter.expansions,
                        onToggle = viewModel::toggleExpansion
                    )
                }

                FilterSection(AppLocale.filterValue) {
                    ValueBucket.entries.forEach { bucket ->
                        val count = facets.values[bucket] ?: 0
                        if (count > 0 || bucket in filter.values) {
                            SelectChip(
                                label = bucket.label(),
                                count = count,
                                selected = bucket in filter.values,
                                onClick = { viewModel.toggleValue(bucket) }
                            )
                        }
                    }
                }

                if (facets.languages.size > 1) {
                    FilterSection(AppLocale.filterLanguage) {
                        facets.languages.forEach { (language, count) ->
                            SelectChip(
                                label = language,
                                count = count,
                                selected = language in filter.languages,
                                onClick = { viewModel.toggleLanguage(language) }
                            )
                        }
                    }
                }

                if (facets.duplicates > 0 || filter.onlyDuplicates) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { viewModel.setOnlyDuplicates(!filter.onlyDuplicates) }
                            .padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(AppLocale.filterOnlyDuplicates, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                AppLocale.filterOnlyDuplicatesHint(facets.duplicates),
                                color = AppColors.textMuted,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = filter.onlyDuplicates,
                            onCheckedChange = { viewModel.setOnlyDuplicates(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = AppColors.blue)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Fisso in fondo: dice quante carte restano prima di chiudere.
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(AppLocale.showCardsButton(state.visibleGroups.size), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Spacer(modifier = Modifier.height(18.dp))
    Text(title, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    Spacer(modifier = Modifier.height(10.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/**
 * Le espansioni sono tante: le piu' ricche in vista, le altre dietro "Mostra
 * tutte", e un campo per cercarle. Quelle scelte restano sempre in vista, o
 * non si capirebbe da dove viene un filtro attivo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpansionFilterSection(
    expansions: List<FacetCount>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val needsSearch = expansions.size > EXPANSIONS_PREVIEW
    val shown = remember(expansions, selected, query, showAll) {
        val q = query.trim().lowercase()
        when {
            q.isNotEmpty() -> expansions.filter { it.value.lowercase().contains(q) }
            showAll || !needsSearch -> expansions
            else -> {
                val top = expansions.take(EXPANSIONS_PREVIEW)
                top + expansions.filter { it.value in selected && it !in top }
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))
    Text(AppLocale.filterExpansion, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    if (needsSearch) {
        Spacer(modifier = Modifier.height(10.dp))
        LabSearchField(
            value = query,
            onValueChange = { query = it },
            hint = AppLocale.searchExpansionHint,
            accent = AppColors.blue
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        shown.forEach { (expansion, count) ->
            SelectChip(
                label = expansion,
                count = count,
                selected = expansion in selected,
                onClick = { onToggle(expansion) }
            )
        }
    }
    if (needsSearch && query.isBlank()) {
        TextButton(onClick = { showAll = !showAll }, contentPadding = PaddingValues(0.dp)) {
            Text(
                if (showAll) AppLocale.showFewer else AppLocale.filterShowAllExpansions(expansions.size),
                color = AppColors.blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Il chip dei filtri. Bordo col testo attenuato e non un bianco fisso: deve
 * vedersi sia sul pannello scuro sia su quello chiaro.
 */
@Composable
private fun SelectChip(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(shape)
            .background(if (selected) AppColors.blue.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) AppColors.blue else AppColors.textMuted.copy(alpha = 0.3f),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        leading?.invoke()
        Text(
            label,
            color = if (selected) AppColors.blue else AppColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (count != null) {
            Text("$count", color = AppColors.textMuted, fontSize = 11.sp)
        }
    }
}

// ── Tessere ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionCardGridItem(
    card: PokemonCard,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    gridColumns: Int = 3,
    /** Le stampe possedute di questa carta: la tessera le riunisce tutte. */
    ownedVariants: Set<String> = emptySet(),
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    /**
     * Chiave della transizione condivisa verso il dettaglio: la stessa che
     * finisce nella rotta, cioe' la chiave di gruppo e non l'id della singola
     * variante. Null quando l'elemento non porta al dettaglio.
     */
    sharedKey: String? = null,
    modifier: Modifier = Modifier
) {
    val compact = gridColumns > 4
    val corner = if (compact) 4.dp else 10.dp
    val imageUrl = remember(card.imageUrl) { ImageUrlUtils.safeProxiedImageUrl(card.imageUrl) }
    var failed by remember(imageUrl) { mutableStateOf(imageUrl.isBlank()) }

    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(corner))
            .background(AppColors.card)
            .border(
                width = if (isSelected) (if (compact) 1.dp else 2.dp) else (if (compact) 0.5.dp else 1.dp),
                color = if (isSelected) AppColors.blue else AppColors.textMuted.copy(alpha = 0.15f),
                shape = RoundedCornerShape(corner)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (failed) {
            // Il nome della carta al posto dell'icona di sistema "immagine
            // rotta" di prima: una tessera senza immagine deve dire che carta e'.
            CollectionCardImageFallback(card = card, compact = compact)
        } else {
            // Sotto l'immagine, non dentro: resta visibile mentre arriva.
            CardImageSkeleton(number = card.cardNumber)
            AsyncImage(
                model = imageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (sharedKey != null) Modifier.sharedCardImage(sharedKey) else Modifier)
                    .holoFoil(enabled = RarityUtils.hasFoilFinish(card.rarity))
            )
        }

        if (isSelected) Box(modifier = Modifier.fillMaxSize().background(AppColors.blue.copy(alpha = 0.15f)))

        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(if (compact) 2.dp else 4.dp)
                    .size(if (compact) 12.dp else 20.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AppColors.blue else Color.Black.copy(alpha = 0.5f))
                    .border(if (compact) 1.dp else 1.5.dp, if (isSelected) AppColors.blue else Color.White.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(if (compact) 8.dp else 13.dp))
                }
            }
        }

        // Le stampe possedute, in alto a sinistra: dall'altra parte c'e' gia'
        // il contatore delle copie. Sotto le cinque colonne le tessere sono
        // troppo piccole perche' una lettera si legga, e li' si saltano.
        if (ownedVariants.isNotEmpty() && gridColumns <= 4 && !isSelectionMode) {
            OwnedVariantBadges(
                variants = ownedVariants,
                size = if (gridColumns > 3) 13 else 16,
                fontSize = if (gridColumns > 3) 7 else 9,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(if (compact) 2.dp else 4.dp)
                .size(if (compact) 14.dp else 22.dp)
                .clip(CircleShape)
                .background(AppColors.blue),
            contentAlignment = Alignment.Center
        ) {
            val quantityFontSize = if (compact) 7.sp else 10.sp
            Text(
                text = "x${card.quantity}",
                color = Color.White,
                fontSize = quantityFontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                style = TextStyle(
                    lineHeight = quantityFontSize,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
        }
    }
}

@Composable
private fun CollectionCardImageFallback(card: PokemonCard, compact: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.surface)
            .padding(if (compact) 4.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = card.name,
                color = AppColors.textPrimary,
                fontSize = if (compact) 8.sp else 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (card.cardNumber.isNotBlank()) {
                Text(
                    text = "#${card.cardNumber}",
                    color = AppColors.textMuted,
                    fontSize = if (compact) 7.sp else 8.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Una riga della vista lista. Il prezzo e' quello della stampa piu' cara, come
 * nell'ordinamento: prima mostrava il prezzo della prima stampa, e la riga
 * diceva 1 € per una carta che in collezione valeva 30.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionCardListItem(
    group: CardGroup,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    showExpansion: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val card = group.representative
    val imageUrl = remember(card.imageUrl) { ImageUrlUtils.safeProxiedImageUrl(card.imageUrl) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) AppColors.blue.copy(alpha = 0.15f) else AppColors.card)
            .then(if (isSelected) Modifier.border(1.dp, AppColors.blue, RoundedCornerShape(12.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AppColors.blue else Color.Transparent)
                    .border(1.5.dp, if (isSelected) AppColors.blue else AppColors.textMuted.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }

        Box(modifier = Modifier.size(50.dp, 70.dp).clip(RoundedCornerShape(4.dp))) {
            CardImageSkeleton(number = card.cardNumber)
            AsyncImage(
                model = imageUrl,
                contentDescription = card.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                card.name,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    if (showExpansion) append(group.expansionLabel).append(" · ")
                    if (card.cardNumber.isNotBlank()) append("#").append(card.cardNumber).append(" · ")
                    append("x").append(group.totalQuantity)
                },
                color = AppColors.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            OwnedVariantBadges(variants = group.variants, size = 15, fontSize = 8)
        }

        if (group.topValue > 0) {
            Text(formatEur(group.topValue), color = AppColors.green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        if (!isSelectionMode) {
            Icon(Icons.Default.ChevronRight, null, tint = AppColors.textMuted)
        }
    }
}
