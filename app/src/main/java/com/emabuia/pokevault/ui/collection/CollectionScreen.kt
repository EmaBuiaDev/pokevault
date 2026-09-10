package com.emabuia.pokevault.ui.collection

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
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
import com.emabuia.pokevault.data.model.collectionGroupKey
import com.emabuia.pokevault.ui.home.components.SearchBar
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.ImageUrlUtils
import com.emabuia.pokevault.util.getTypeEmojiForCollection
import com.emabuia.pokevault.viewmodel.CollectionViewModel
import com.emabuia.pokevault.viewmodel.SortOrder
import com.emabuia.pokevault.viewmodel.SupertypeFilter

private enum class ExpansionSortOrder {
    BY_NAME_ASC,
    BY_TOTAL_CARDS_DESC,
    BY_TOTAL_CARDS_ASC
}

/** Etichette localizzate, al posto del nome grezzo dell'enum. */
private fun SortOrder.label(): String = when (this) {
    SortOrder.NEWEST -> AppLocale.sortRecent
    SortOrder.PRICE_ASC -> AppLocale.sortPriceAsc
    SortOrder.PRICE_DESC -> AppLocale.sortPriceDesc
    SortOrder.NAME_ASC -> AppLocale.sortNameAsc
    SortOrder.NUMBER -> AppLocale.sortSetNumber
}

private fun SupertypeFilter.label(): String = when (this) {
    SupertypeFilter.ALL -> AppLocale.categoryAll
    SupertypeFilter.POKEMON -> AppLocale.categoryPokemon
    SupertypeFilter.TRAINER -> AppLocale.categoryTrainer
    SupertypeFilter.ENERGY -> AppLocale.categoryEnergy
}

/**
 * Una sezione dell'accordion per espansione, con gli aggregati gia' calcolati.
 *
 * Totali e righe della griglia venivano ricalcolati dentro il builder della
 * LazyColumn, quindi su tutte le carte di tutte le espansioni a ogni
 * ricomposizione del contenuto. Qui sono calcolati una volta sola, dentro il
 * remember che costruisce le sezioni.
 */
private data class ExpansionSection(
    val name: String,
    val groups: List<Pair<String, List<PokemonCard>>>,
    val totalQuantity: Int,
    val totalValue: Double,
    val gridRows: List<List<Pair<String, List<PokemonCard>>>>
)

@Composable
private fun CollectionCardImageFallback(card: PokemonCard, compact: Boolean) {
    val titleSize = if (compact) 8.sp else 10.sp
    val detailSize = if (compact) 7.sp else 8.sp
    val series = "-"
    val setName = AppLocale.displaySetName(card.set).ifBlank { "-" }

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
                fontSize = titleSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = series,
                color = AppColors.textMuted,
                fontSize = detailSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = setName,
                color = AppColors.textMuted,
                fontSize = detailSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

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

    // Selection mode
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedGroupKeys by remember { mutableStateOf(setOf<String>()) }

    var showFilters by remember { mutableStateOf(false) }
    var expansionSortOrder by rememberSaveable { mutableStateOf(ExpansionSortOrder.BY_NAME_ASC) }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedGroupKeys = emptySet()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.successMessage, state.errorMessage) {
        val msg = state.successMessage ?: state.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    // Compute grouped cards by logical card key and organize them by expansion.
    val groupedCards = remember(state.filteredCards) {
        state.filteredCards
            .groupBy { it.collectionGroupKey() }
            .entries
            .map { (key, group) -> key to group }
    }
    val groupedByExpansion = remember(groupedCards, state.sortOrder) {
        groupedCards.groupBy { (_, group) ->
            group.firstOrNull()?.set?.takeIf { it.isNotBlank() } ?: if (AppLocale.isItalian) "Espansione sconosciuta" else "Unknown Expansion"
        }.mapValues { entry ->
            entry.value.sortedWith { a, b ->
                val cardA = a.second.firstOrNull()
                val cardB = b.second.firstOrNull()
                when (state.sortOrder) {
                    SortOrder.NEWEST -> 0
                    SortOrder.PRICE_ASC -> (cardA?.estimatedValue ?: 0.0).compareTo(cardB?.estimatedValue ?: 0.0)
                    SortOrder.PRICE_DESC -> (cardB?.estimatedValue ?: 0.0).compareTo(cardA?.estimatedValue ?: 0.0)
                    SortOrder.NAME_ASC -> (cardA?.name ?: "").lowercase().compareTo((cardB?.name ?: "").lowercase())
                    SortOrder.NUMBER -> {
                        val numA = cardA?.cardNumber ?: ""
                        val numB = cardB?.cardNumber ?: ""
                        val digitA = numA.filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
                        val digitB = numB.filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
                        if (digitA != digitB) digitA.compareTo(digitB) else numA.compareTo(numB)
                    }
                }
            }
        }
    }
    val visibleExpansionNames = remember(groupedByExpansion) { groupedByExpansion.keys.toSet() }
    val hasActiveFilters = state.searchQuery.isNotBlank() ||
        state.selectedSet != null ||
        state.selectedType != null ||
        state.selectedRarity != null ||
        state.supertypeFilter != SupertypeFilter.ALL ||
        state.sortOrder != SortOrder.NUMBER

    // Gestione espansioni aperte (inizialmente vuoto = tutte chiuse)
    var expandedExpansions by remember { mutableStateOf(setOf<String>()) }

    // Prefetch immagini per rendere piu' fluida l'apertura delle espansioni.
    LaunchedEffect(state.filteredCards, state.isGridView, expandedExpansions) {
        val maxPrefetch = if (state.isGridView) 72 else 48
        val cardsToPrefetch = if (expandedExpansions.isEmpty()) {
            state.filteredCards.asSequence().take(maxPrefetch)
        } else {
            state.filteredCards
                .asSequence()
                .filter { card ->
                    val expansionName = card.set.takeIf { it.isNotBlank() }
                        ?: if (AppLocale.isItalian) "Espansione sconosciuta" else "Unknown Expansion"
                    expansionName in expandedExpansions
                }
                .take(maxPrefetch)
        }
        cardsToPrefetch
            .map { ImageUrlUtils.safeProxiedImageUrl(it.imageUrl) }
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

    // Performance: con tante carte evitare di espandere tutto all'ingresso.
    // Auto-espandi solo quando ci sono filtri attivi, per mostrare subito i risultati filtrati.
    LaunchedEffect(hasActiveFilters, visibleExpansionNames) {
        expandedExpansions = if (hasActiveFilters && visibleExpansionNames.isNotEmpty()) {
            expandedExpansions + visibleExpansionNames
        } else {
            emptySet()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(AppLocale.selectedCount(selectedGroupKeys.size), fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
                    } else {
                        Text(AppLocale.myCardsSingleLine, fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { isSelectionMode = false; selectedGroupKeys = emptySet() }) {
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
                        IconButton(onClick = {
                            selectedGroupKeys = if (selectedGroupKeys.size == groupedCards.size) {
                                emptySet()
                            } else {
                                groupedCards.map { it.first }.toSet()
                            }
                        }) {
                            Icon(
                                imageVector = if (selectedGroupKeys.size == groupedCards.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = AppLocale.selectAll,
                                tint = AppColors.textPrimary
                            )
                        }
                    } else {
                        if (state.isGridView) {
                            IconButton(onClick = { viewModel.toggleGridColumns() }) {
                                Icon(
                                    imageVector = when(state.gridColumns) {
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
                // ── Stats ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMiniCard("Carte", "${state.stats.totalCards}", AppColors.blue, Modifier.weight(1f))
                    StatMiniCard("Uniche", "${state.stats.uniqueCards}", AppColors.purple, Modifier.weight(1f))
                    StatMiniCard("Valore", "\u20AC${"%.2f".format(state.stats.totalValue)}", AppColors.green, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SearchBar(
                                query = state.searchQuery,
                                onQueryChange = { viewModel.updateSearchQuery(it) }
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            onClick = { showFilters = true },
                            color = if (hasActiveFilters) AppColors.blue.copy(alpha = 0.9f) else AppColors.card,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.size(50.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = AppLocale.filters,
                                    tint = AppColors.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                if (hasActiveFilters) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF5D5D))
                                    )
                                }
                            }
                        }
                    }

                    if (hasActiveFilters) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ActiveFiltersRow(state = state, viewModel = viewModel)
                    }

                    Spacer(modifier = Modifier.height(if (hasActiveFilters) 12.dp else 14.dp))
                    ExpansionSortRow(
                        selectedOrder = expansionSortOrder,
                        onOrderSelected = { expansionSortOrder = it },
                        onExpandAll = { expandedExpansions = visibleExpansionNames },
                        onCollapseAll = { expandedExpansions = emptySet() },
                        canExpandAll = visibleExpansionNames.isNotEmpty() && (visibleExpansionNames.size > expandedExpansions.size),
                        canCollapseAll = expandedExpansions.isNotEmpty()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── Contenuto ──
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.blue)
                    }
                } else {
                    if (groupedCards.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(AppLocale.emptyCollectionTitle, color = AppColors.textMuted)
                        }
                    } else {
                        // Totali e righe della griglia precalcolati qui, non dentro il
                        // builder della LazyColumn: prima venivano risommati su tutte le
                        // carte di tutte le espansioni a ogni ricomposizione del
                        // contenuto, e chunked() riallocava una lista di liste per ogni
                        // sezione espansa.
                        val expansionSections = remember(groupedByExpansion, expansionSortOrder, state.gridColumns) {
                            groupedByExpansion
                                .toList()
                                .map { (name, groups) ->
                                    ExpansionSection(
                                        name = name,
                                        groups = groups,
                                        totalQuantity = groups.sumOf { (_, cards) -> cards.sumOf { it.quantity } },
                                        totalValue = groups.sumOf { (_, cards) ->
                                            cards.sumOf { card -> card.estimatedValue * card.quantity }
                                        },
                                        gridRows = groups.chunked(state.gridColumns)
                                    )
                                }
                                .sortedWith(
                                    when (expansionSortOrder) {
                                        ExpansionSortOrder.BY_NAME_ASC ->
                                            compareBy { it.name.lowercase() }
                                        ExpansionSortOrder.BY_TOTAL_CARDS_DESC ->
                                            compareByDescending<ExpansionSection> { it.totalQuantity }
                                                .thenBy { it.name.lowercase() }
                                        ExpansionSortOrder.BY_TOTAL_CARDS_ASC ->
                                            compareBy<ExpansionSection> { it.totalQuantity }
                                                .thenBy { it.name.lowercase() }
                                    }
                                )
                        }

                        // Flat LazyColumn: header + righe carte come item separati.
                        // Compose renderizza solo gli elementi visibili → nessun lag su espansioni con molte carte.
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 0.dp,
                                bottom = if (isSelectionMode) 80.dp else 20.dp
                            )
                        ) {
                            expansionSections.forEachIndexed { sectionIndex, section ->
                                val expansionName = section.name
                                val cardsInExpansion = section.groups
                                val totalQuantity = section.totalQuantity
                                val totalExpansionValue = section.totalValue
                                val isExpanded = expansionName in expandedExpansions
                                val cardSpacing = if (state.gridColumns > 4) 6.dp else 10.dp

                                // Spaziatura tra sezioni
                                if (sectionIndex > 0) {
                                    item(key = "gap_$expansionName") {
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                }

                                // Header sezione (sempre visibile)
                                item(key = "hdr_$expansionName") {
                                    ExpansionAccordionHeader(
                                        expansionName = AppLocale.displaySetName(expansionName),
                                        totalCards = totalQuantity,
                                        totalValue = totalExpansionValue,
                                        uniqueCards = cardsInExpansion.size,
                                        isExpanded = isExpanded,
                                        onToggle = {
                                            expandedExpansions = if (isExpanded) {
                                                expandedExpansions - expansionName
                                            } else {
                                                expandedExpansions + expansionName
                                            }
                                        }
                                    )
                                }

                                // Carte: lazy item per riga/carta, solo quando espansa
                                if (isExpanded) {
                                    if (state.isGridView) {
                                        val rows = section.gridRows
                                        itemsIndexed(
                                            items = rows,
                                            // Chiave sull'indice di riga: la precedente usava il
                                            // primo elemento della riga, quindi cambiava a ogni
                                            // variazione del contenuto e due righe che iniziavano
                                            // con lo stesso gruppo potevano collidere.
                                            key = { rowIndex, _ -> "row_${expansionName}_$rowIndex" }
                                        ) { _, row ->
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(cardSpacing),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(AppColors.card)
                                                    .padding(horizontal = 10.dp, vertical = if (state.gridColumns > 4) 3.dp else 5.dp)
                                            ) {
                                                row.forEach { (groupKey, group) ->
                                                    val representative = group.first()
                                                    val totalQty = group.sumOf { it.quantity }
                                                    CollectionCardGridItem(
                                                        card = representative.copy(quantity = totalQty),
                                                        isSelected = groupKey in selectedGroupKeys,
                                                        isSelectionMode = isSelectionMode,
                                                        gridColumns = state.gridColumns,
                                                        onClick = {
                                                            if (isSelectionMode) {
                                                                selectedGroupKeys = if (groupKey in selectedGroupKeys) {
                                                                    selectedGroupKeys - groupKey
                                                                } else {
                                                                    selectedGroupKeys + groupKey
                                                                }
                                                                if (selectedGroupKeys.isEmpty()) isSelectionMode = false
                                                            } else {
                                                                    onCardClick(groupKey)
                                                            }
                                                        },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            isSelectionMode = true
                                                            selectedGroupKeys = selectedGroupKeys + groupKey
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                repeat(state.gridColumns - row.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    } else {
                                        items(
                                            items = cardsInExpansion,
                                            key = { pair -> "card_${expansionName}_${pair.first}" }
                                        ) { (groupKey, group) ->
                                            val representative = group.first()
                                            val totalQty = group.sumOf { it.quantity }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(AppColors.card)
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                CollectionCardListItem(
                                                    card = representative.copy(quantity = totalQty),
                                                    isSelected = groupKey in selectedGroupKeys,
                                                    isSelectionMode = isSelectionMode,
                                                    onClick = {
                                                        if (isSelectionMode) {
                                                            selectedGroupKeys = if (groupKey in selectedGroupKeys) {
                                                                selectedGroupKeys - groupKey
                                                            } else {
                                                                selectedGroupKeys + groupKey
                                                            }
                                                            if (selectedGroupKeys.isEmpty()) isSelectionMode = false
                                                        } else {
                                                                onCardClick(groupKey)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        isSelectionMode = true
                                                        selectedGroupKeys = selectedGroupKeys + groupKey
                                                    },
                                                    // La riga mostra la quantita' aggregata del gruppo,
                                                    // quindi il delete deve rimuovere il gruppo intero:
                                                    // prima cancellava solo il documento del
                                                    // rappresentante e le altre varianti restavano.
                                                    onDelete = { viewModel.deleteMultipleGroups(setOf(groupKey)) }
                                                )
                                            }
                                        }
                                    }

                                    // Chiusura visiva della sezione espansa
                                    item(key = "btm_$expansionName") {
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
                }
            }

            // Selection bottom bar
            if (isSelectionMode && selectedGroupKeys.isNotEmpty()) {
                var showConfirm by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, AppColors.surface.copy(alpha = 0.95f), AppColors.surface)
                            )
                        )
                        .padding(top = 16.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = { isSelectionMode = false; selectedGroupKeys = emptySet() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, null, tint = AppColors.textMuted)
                        }

                        Text(
                            text = AppLocale.selectedCount(selectedGroupKeys.size),
                            color = AppColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )

                        if (showConfirm) {
                            Button(
                                onClick = {
                                    viewModel.deleteMultipleGroups(selectedGroupKeys)
                                    isSelectionMode = false
                                    selectedGroupKeys = emptySet()
                                    showConfirm = false
                                },
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
        }

        if (showFilters) {
            FilterBottomSheet(
                state = state,
                viewModel = viewModel,
                onDismiss = { showFilters = false }
            )
        }
    }
}

@Composable
private fun ExpansionSortRow(
    selectedOrder: ExpansionSortOrder,
    onOrderSelected: (ExpansionSortOrder) -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    canExpandAll: Boolean,
    canCollapseAll: Boolean
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Surface(
                color = AppColors.card,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                        tint = AppColors.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = AppLocale.expansionOrder,
                        color = AppColors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        item {
            FilterChip(
                label = "A-Z",
                isSelected = selectedOrder == ExpansionSortOrder.BY_NAME_ASC,
                onClick = { onOrderSelected(ExpansionSortOrder.BY_NAME_ASC) }
            )
        }
        item {
            FilterChip(
                label = AppLocale.moreCards,
                isSelected = selectedOrder == ExpansionSortOrder.BY_TOTAL_CARDS_DESC,
                onClick = { onOrderSelected(ExpansionSortOrder.BY_TOTAL_CARDS_DESC) }
            )
        }
        item {
            FilterChip(
                label = AppLocale.fewerCards,
                isSelected = selectedOrder == ExpansionSortOrder.BY_TOTAL_CARDS_ASC,
                onClick = { onOrderSelected(ExpansionSortOrder.BY_TOTAL_CARDS_ASC) }
            )
        }
        item {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = canExpandAll, onClick = onExpandAll),
                color = if (canExpandAll) AppColors.card else AppColors.card.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, AppColors.textMuted.copy(alpha = 0.3f))
            ) {
                Text(
                    text = AppLocale.expandAll,
                    color = if (canExpandAll) AppColors.textPrimary else AppColors.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        item {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = canCollapseAll, onClick = onCollapseAll),
                color = if (canCollapseAll) AppColors.card else AppColors.card.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, AppColors.textMuted.copy(alpha = 0.3f))
            ) {
                Text(
                    text = AppLocale.collapseAll,
                    color = if (canCollapseAll) AppColors.textPrimary else AppColors.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    state: com.emabuia.pokevault.viewmodel.CollectionUiState,
    viewModel: CollectionViewModel,
    onDismiss: () -> Unit
) {
    fun canonicalSetLabel(rawSet: String): String {
        return AppLocale.displaySetName(rawSet)
            .ifBlank { if (AppLocale.isItalian) "Espansione sconosciuta" else "Unknown Expansion" }
            .trim()
    }

    val setCounts = remember(state.cards) {
        state.cards
            .groupBy { canonicalSetLabel(it.set) }
            .mapValues { it.value.sumOf { c -> c.quantity } }
            .toList()
            .sortedByDescending { it.second }
    }
    val rarityCounts = remember(state.cards) {
        state.cards.filter { it.rarity.isNotBlank() }
            .groupBy { it.rarity }
            .mapValues { it.value.sumOf { c -> c.quantity } }
            .toList()
            .sortedByDescending { it.second }
    }
    val types = AppLocale.getTypes()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.textMuted.copy(alpha = 0.45f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(AppLocale.filters, style = MaterialTheme.typography.headlineSmall, color = AppColors.textPrimary)
                    Text(AppLocale.organizeCardsHint, color = AppColors.textMuted, fontSize = 12.sp)
                }
                TextButton(onClick = {
                    viewModel.filterBySupertype(SupertypeFilter.ALL)
                    viewModel.filterBySet(null)
                    viewModel.filterByType(null)
                    viewModel.filterByRarity(null)
                    viewModel.updateSortOrder(SortOrder.NUMBER)
                }) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = AppColors.blue)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(AppLocale.resetFilters, color = AppColors.blue)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            FilterSectionCard(
                title = "Ordinamento",
                icon = Icons.AutoMirrored.Filled.Sort
            ) {
                item {
                    FilterChip(
                        label = AppLocale.sortRecent,
                        isSelected = state.sortOrder == SortOrder.NEWEST,
                        onClick = { viewModel.updateSortOrder(SortOrder.NEWEST) }
                    )
                }
                item {
                    FilterChip(
                        label = AppLocale.sortPriceAsc,
                        isSelected = state.sortOrder == SortOrder.PRICE_ASC,
                        onClick = { viewModel.updateSortOrder(SortOrder.PRICE_ASC) }
                    )
                }
                item {
                    FilterChip(
                        label = AppLocale.sortPriceDesc,
                        isSelected = state.sortOrder == SortOrder.PRICE_DESC,
                        onClick = { viewModel.updateSortOrder(SortOrder.PRICE_DESC) }
                    )
                }
                item {
                    FilterChip(
                        label = AppLocale.sortNameAsc,
                        isSelected = state.sortOrder == SortOrder.NAME_ASC,
                        onClick = { viewModel.updateSortOrder(SortOrder.NAME_ASC) }
                    )
                }
                item {
                    FilterChip(
                        label = AppLocale.sortSetNumber,
                        isSelected = state.sortOrder == SortOrder.NUMBER,
                        onClick = { viewModel.updateSortOrder(SortOrder.NUMBER) }
                    )
                }
            }

            FilterSectionCard(
                title = "Categoria",
                icon = Icons.Default.Category
            ) {
                item {
                    FilterChip(
                        label = "Tutti",
                        isSelected = state.supertypeFilter == SupertypeFilter.ALL,
                        onClick = { viewModel.filterBySupertype(SupertypeFilter.ALL) }
                    )
                }
                item {
                    FilterChip(
                        label = "Pokémon",
                        isSelected = state.supertypeFilter == SupertypeFilter.POKEMON,
                        onClick = { viewModel.filterBySupertype(SupertypeFilter.POKEMON) }
                    )
                }
                item {
                    FilterChip(
                        label = "Trainer",
                        isSelected = state.supertypeFilter == SupertypeFilter.TRAINER,
                        onClick = { viewModel.filterBySupertype(SupertypeFilter.TRAINER) }
                    )
                }
                item {
                    FilterChip(
                        label = "Energy",
                        isSelected = state.supertypeFilter == SupertypeFilter.ENERGY,
                        onClick = { viewModel.filterBySupertype(SupertypeFilter.ENERGY) }
                    )
                }
            }

            FilterSectionCard(
                title = "Tipologia",
                icon = Icons.Default.Bolt
            ) {
                item {
                    FilterChip(
                        label = "Tutti",
                        isSelected = state.selectedType == null,
                        onClick = { viewModel.filterByType(null) }
                    )
                }
                items(types, key = { it }) { type ->
                    FilterChip(
                        label = type,
                        isSelected = state.selectedType == type,
                        onClick = { viewModel.filterByType(type) }
                    )
                }
            }

            if (setCounts.isNotEmpty()) {
                FilterSectionCard(
                    title = "Espansione",
                    icon = Icons.Default.CollectionsBookmark
                ) {
                    item {
                        FilterChip(
                            label = "Tutti i set",
                            isSelected = state.selectedSet == null,
                            onClick = { viewModel.filterBySet(null) }
                        )
                    }
                    items(setCounts, key = { it.first }) { (setLabel, count) ->
                        FilterChip(
                            label = "$setLabel ($count)",
                            isSelected = state.selectedSet == setLabel,
                            onClick = { viewModel.filterBySet(setLabel) }
                        )
                    }
                }
            }

            if (rarityCounts.isNotEmpty()) {
                FilterSectionCard(
                    title = AppLocale.rarity,
                    icon = Icons.Default.AutoAwesome
                ) {
                    item {
                        FilterChip(
                            label = AppLocale.all,
                            isSelected = state.selectedRarity == null,
                            onClick = { viewModel.filterByRarity(null) }
                        )
                    }
                    items(rarityCounts, key = { it.first }) { (rarity, count) ->
                        FilterChip(
                            label = "${AppLocale.translateRarity(rarity)} ($count)",
                            isSelected = state.selectedRarity == rarity,
                            onClick = { viewModel.filterByRarity(rarity) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Done, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppLocale.showResults, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ActiveFiltersRow(
    state: com.emabuia.pokevault.viewmodel.CollectionUiState,
    viewModel: CollectionViewModel
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.selectedSet != null) {
            item {
                RemovableFilterChip(
                    label = "Set: ${state.selectedSet}",
                    onRemove = { viewModel.filterBySet(null) }
                )
            }
        }
        if (state.selectedType != null) {
            item {
                RemovableFilterChip(
                    label = "Tipo: ${state.selectedType}",
                    onRemove = { viewModel.filterByType(null) }
                )
            }
        }
        if (state.selectedRarity != null) {
            item {
                RemovableFilterChip(
                    label = "Rarità: ${AppLocale.translateRarity(state.selectedRarity)}",
                    onRemove = { viewModel.filterByRarity(null) }
                )
            }
        }
        if (state.supertypeFilter != SupertypeFilter.ALL) {
            item {
                RemovableFilterChip(
                    label = "${AppLocale.filterCategoryPrefix}: ${state.supertypeFilter.label()}",
                    onRemove = { viewModel.filterBySupertype(SupertypeFilter.ALL) }
                )
            }
        }
        if (state.sortOrder != SortOrder.NUMBER) {
            item {
                RemovableFilterChip(
                    label = "${AppLocale.filterSortPrefix}: ${state.sortOrder.label()}",
                    onRemove = { viewModel.updateSortOrder(SortOrder.NUMBER) }
                )
            }
        }
    }
}

@Composable
fun RemovableFilterChip(label: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AppColors.blue.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, AppColors.blue.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onRemove)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                color = AppColors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.Close, contentDescription = null, tint = AppColors.textMuted, modifier = Modifier.size(14.dp))
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
    val shape = if (isExpanded)
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    else
        RoundedCornerShape(12.dp)
    Surface(
        color = AppColors.card,
        shape = shape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = AppColors.blue.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, AppColors.blue.copy(alpha = 0.25f))
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesomeMosaic,
                    contentDescription = null,
                    tint = AppColors.blue,
                    modifier = Modifier.padding(6.dp).size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = expansionName,
                        color = AppColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\u20AC${"%.2f".format(totalValue)}",
                            color = AppColors.green,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = AppColors.blue.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, AppColors.blue.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "x$totalCards",
                                color = AppColors.blue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "$uniqueCards uniche · $totalCards tot.",
                    color = AppColors.textMuted,
                    fontSize = 11.sp
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = AppColors.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun FilterSectionCard(
    title: String,
    icon: ImageVector,
    content: LazyListScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = AppColors.card,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = AppColors.blue, modifier = Modifier.size(18.dp))
                Text(title, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

@Composable
fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) AppColors.blue else AppColors.card,
        border = if (!isSelected) BorderStroke(1.dp, AppColors.textMuted.copy(alpha = 0.3f)) else null
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else AppColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionCardGridItem(
    card: PokemonCard,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    gridColumns: Int = 3,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(if (gridColumns > 4) 4.dp else 10.dp))
            .background(AppColors.card)
            .then(
                when {
                    isSelected -> Modifier.border(if (gridColumns > 4) 1.dp else 2.dp, AppColors.blue, RoundedCornerShape(if (gridColumns > 4) 4.dp else 10.dp))
                    else -> Modifier.border(if (gridColumns > 4) 0.5.dp else 1.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(if (gridColumns > 4) 4.dp else 10.dp))
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = ImageUrlUtils.safeProxiedImageUrl(card.imageUrl),
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            error = painterResource(id = android.R.drawable.ic_menu_report_image) // Fallback invisibile o icona standard
        )

        if (isSelected) Box(modifier = Modifier
            .fillMaxSize()
            .background(AppColors.blue.copy(alpha = 0.15f)))

        // Selection checkbox (top-left)
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(if (gridColumns > 4) 2.dp else 4.dp)
                    .size(if (gridColumns > 4) 12.dp else 20.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AppColors.blue else Color.Black.copy(alpha = 0.5f))
                    .border(if (gridColumns > 4) 1.dp else 1.5.dp, if (isSelected) AppColors.blue else Color.White.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(if (gridColumns > 4) 8.dp else 13.dp))
                }
            }
        }

        // Quantity badge (top-right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(if (gridColumns > 4) 2.dp else 4.dp)
                .size(if (gridColumns > 4) 14.dp else 22.dp)
                .clip(CircleShape).background(AppColors.blue),
            contentAlignment = Alignment.Center
        ) {
            val compact = gridColumns > 4
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionCardListItem(
    card: PokemonCard,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) AppColors.blue.copy(alpha = 0.15f) else AppColors.card)
            .then(
                if (isSelected) Modifier.border(1.dp, AppColors.blue, RoundedCornerShape(12.dp)) else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Selection checkbox
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AppColors.blue else Color.Transparent)
                    .border(1.5.dp, if (isSelected) AppColors.blue else AppColors.textMuted.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }

        AsyncImage(
            model = ImageUrlUtils.safeProxiedImageUrl(card.imageUrl),
            contentDescription = card.name,
            modifier = Modifier.size(50.dp, 70.dp).clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.name, color = AppColors.textPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                if (card.estimatedValue > 0) {
                    Text("\u20AC${"%.2f".format(card.estimatedValue)}", color = AppColors.green, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            Text("${AppLocale.displaySetName(card.set)} \u00B7 x${card.quantity}", color = AppColors.textMuted, fontSize = 12.sp)
        }
        if (!isSelectionMode) {
            Icon(Icons.Default.ChevronRight, null, tint = AppColors.textMuted)
        }
    }
}

@Composable
fun StatMiniCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.1f)).padding(10.dp)) {
        Column {
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(label, color = AppColors.textMuted, fontSize = 10.sp)
        }
    }
}
