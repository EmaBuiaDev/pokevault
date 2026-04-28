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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.ui.home.components.SearchBar
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.getTypeEmojiForCollection
import com.emabuia.pokevault.viewmodel.CollectionViewModel
import com.emabuia.pokevault.viewmodel.SortOrder
import com.emabuia.pokevault.viewmodel.SupertypeFilter

private fun safeImageUrl(url: String): String {
    return url
        .replace(" ", "%20")
        .replace("(", "%28")
        .replace(")", "%29")
}

private enum class ExpansionSortOrder {
    BY_NAME_ASC,
    BY_TOTAL_CARDS_DESC,
    BY_TOTAL_CARDS_ASC
}

@Composable
private fun CollectionCardImageFallback(card: PokemonCard, compact: Boolean) {
    val titleSize = if (compact) 8.sp else 10.sp
    val detailSize = if (compact) 7.sp else 8.sp
    val series = "-"
    val setName = card.set.ifBlank { "-" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSurface)
            .padding(if (compact) 4.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = card.name,
                color = TextWhite,
                fontSize = titleSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = series,
                color = TextMuted,
                fontSize = detailSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = setName,
                color = TextMuted,
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
            .groupBy { it.apiCardId.ifBlank { "${it.name}_${it.set}_${it.cardNumber}" } }
            .entries
            .map { (key, group) -> key to group }
    }
    val groupedByExpansion = remember(groupedCards) {
        groupedCards.groupBy { (_, group) ->
            group.firstOrNull()?.set?.takeIf { it.isNotBlank() } ?: "Espansione sconosciuta"
        }.mapValues { entry ->
            entry.value.sortedWith { a, b ->
                val cardA = a.second.firstOrNull()
                val cardB = b.second.firstOrNull()
                val numA = cardA?.cardNumber ?: ""
                val numB = cardB?.cardNumber ?: ""
                
                val digitA = numA.filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
                val digitB = numB.filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
                
                if (digitA != digitB) digitA.compareTo(digitB)
                else numA.compareTo(numB)
            }
        }
    }
    val visibleExpansionNames = remember(groupedByExpansion) { groupedByExpansion.keys.toSet() }
    
    // Gestione espansioni aperte (inizialmente vuoto = tutte chiuse)
    var expandedExpansions by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("${selectedGroupKeys.size} selezionate", fontWeight = FontWeight.Bold, color = TextWhite)
                    } else {
                        Text("Le mie carte", fontWeight = FontWeight.Bold, color = TextWhite)
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { isSelectionMode = false; selectedGroupKeys = emptySet() }) {
                            Icon(Icons.Default.Close, "Annulla", tint = TextWhite)
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = TextWhite)
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
                                contentDescription = "Seleziona tutto",
                                tint = TextWhite
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
                                    contentDescription = "Cambia densità griglia",
                                    tint = TextWhite
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                            Icon(
                                imageVector = if (state.isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = "Cambia vista",
                                tint = TextWhite
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
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
                    StatMiniCard("Carte", "${state.stats.totalCards}", BlueCard, Modifier.weight(1f))
                    StatMiniCard("Uniche", "${state.stats.uniqueCards}", PurpleCard, Modifier.weight(1f))
                    StatMiniCard("Valore", "\u20AC${"%.2f".format(state.stats.totalValue)}", GreenCard, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isSelectionMode) {
                    val hasActiveFilters = state.selectedSet != null ||
                        state.selectedType != null ||
                        state.selectedRarity != null ||
                        state.supertypeFilter != SupertypeFilter.ALL ||
                        state.sortOrder != SortOrder.NEWEST

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
                            color = if (hasActiveFilters) BlueCard.copy(alpha = 0.9f) else DarkCard,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.size(50.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Filtri",
                                    tint = TextWhite,
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
                        CircularProgressIndicator(color = BlueCard)
                    }
                } else {
                    if (groupedCards.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Nessuna carta trovata", color = TextMuted)
                        }
                    } else {
                        val expansionSections = groupedByExpansion
                            .toList()
                            .sortedWith(
                                when (expansionSortOrder) {
                                    ExpansionSortOrder.BY_NAME_ASC -> compareBy { it.first.lowercase() }
                                    ExpansionSortOrder.BY_TOTAL_CARDS_DESC -> compareByDescending<Pair<String, List<Pair<String, List<PokemonCard>>>>> {
                                        it.second.sumOf { (_, cards) -> cards.sumOf { card -> card.quantity } }
                                    }.thenBy { it.first.lowercase() }
                                    ExpansionSortOrder.BY_TOTAL_CARDS_ASC -> compareBy<Pair<String, List<Pair<String, List<PokemonCard>>>>> {
                                        it.second.sumOf { (_, cards) -> cards.sumOf { card -> card.quantity } }
                                    }.thenBy { it.first.lowercase() }
                                }
                            )

                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 0.dp,
                                bottom = if (isSelectionMode) 80.dp else 20.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(expansionSections, key = { it.first }) { (expansionName, cardsInExpansion) ->
                                val totalQuantity = cardsInExpansion.sumOf { (_, group) -> group.sumOf { it.quantity } }
                                val isExpanded = expansionName in expandedExpansions

                                ExpansionAccordionSection(
                                    expansionName = expansionName,
                                    totalCards = totalQuantity,
                                    uniqueCards = cardsInExpansion.size,
                                    isCollapsed = !isExpanded,
                                    onToggle = {
                                        expandedExpansions = if (isExpanded) {
                                            expandedExpansions - expansionName
                                        } else {
                                            expandedExpansions + expansionName
                                        }
                                    },
                                    content = {
                                        if (state.isGridView) {
                                            Column(verticalArrangement = Arrangement.spacedBy(if (state.gridColumns > 4) 6.dp else 10.dp)) {
                                                cardsInExpansion.chunked(state.gridColumns).forEach { row ->
                                                    Row(horizontalArrangement = Arrangement.spacedBy(if (state.gridColumns > 4) 6.dp else 10.dp)) {
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
                                                                        onCardClick(representative.apiCardId.ifBlank { representative.id })
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
                                            }
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                cardsInExpansion.forEach { (groupKey, group) ->
                                                    val representative = group.first()
                                                    val totalQty = group.sumOf { it.quantity }
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
                                                                onCardClick(representative.apiCardId.ifBlank { representative.id })
                                                            }
                                                        },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            isSelectionMode = true
                                                            selectedGroupKeys = selectedGroupKeys + groupKey
                                                        },
                                                        onDelete = { viewModel.deleteCard(representative.id) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                )
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
                                listOf(Color.Transparent, DarkSurface.copy(alpha = 0.95f), DarkSurface)
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
                            Icon(Icons.Default.Close, null, tint = TextMuted)
                        }

                        Text(
                            text = "${selectedGroupKeys.size} selezionate",
                            color = TextWhite,
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
                                colors = ButtonDefaults.buttonColors(containerColor = RedCard),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Conferma", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = { showConfirm = false },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, TextMuted.copy(alpha = 0.3f)),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Annulla", color = TextMuted, fontSize = 13.sp)
                            }
                        } else {
                            Button(
                                onClick = { showConfirm = true },
                                colors = ButtonDefaults.buttonColors(containerColor = RedCard),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Elimina", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                color = DarkCard,
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
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Ordine espansioni",
                        color = TextMuted,
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
                label = "Più carte",
                isSelected = selectedOrder == ExpansionSortOrder.BY_TOTAL_CARDS_DESC,
                onClick = { onOrderSelected(ExpansionSortOrder.BY_TOTAL_CARDS_DESC) }
            )
        }
        item {
            FilterChip(
                label = "Meno carte",
                isSelected = selectedOrder == ExpansionSortOrder.BY_TOTAL_CARDS_ASC,
                onClick = { onOrderSelected(ExpansionSortOrder.BY_TOTAL_CARDS_ASC) }
            )
        }
        item {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = canExpandAll, onClick = onExpandAll),
                color = if (canExpandAll) DarkCard else DarkCard.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, TextMuted.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "Espandi tutte",
                    color = if (canExpandAll) TextWhite else TextMuted,
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
                color = if (canCollapseAll) DarkCard else DarkCard.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, TextMuted.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "Chiudi tutte",
                    color = if (canCollapseAll) TextWhite else TextMuted,
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
    val setCounts = remember(state.cards) {
        state.cards.groupBy { it.set.ifBlank { "Espansione sconosciuta" } }
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
        containerColor = DarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted.copy(alpha = 0.45f)) }
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
                    Text("Filtri", style = MaterialTheme.typography.headlineSmall, color = TextWhite)
                    Text("Organizza le carte senza perdere il contesto", color = TextMuted, fontSize = 12.sp)
                }
                TextButton(onClick = {
                    viewModel.filterBySupertype(SupertypeFilter.ALL)
                    viewModel.filterBySet(null)
                    viewModel.filterByType(null)
                    viewModel.filterByRarity(null)
                    viewModel.updateSortOrder(SortOrder.NEWEST)
                }) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = BlueCard)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", color = BlueCard)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            FilterSectionCard(
                title = "Ordinamento",
                icon = Icons.AutoMirrored.Filled.Sort
            ) {
                item {
                    FilterChip(
                        label = "Recenti",
                        isSelected = state.sortOrder == SortOrder.NEWEST,
                        onClick = { viewModel.updateSortOrder(SortOrder.NEWEST) }
                    )
                }
                item {
                    FilterChip(
                        label = "€ Crescente",
                        isSelected = state.sortOrder == SortOrder.PRICE_ASC,
                        onClick = { viewModel.updateSortOrder(SortOrder.PRICE_ASC) }
                    )
                }
                item {
                    FilterChip(
                        label = "€ Decrescente",
                        isSelected = state.sortOrder == SortOrder.PRICE_DESC,
                        onClick = { viewModel.updateSortOrder(SortOrder.PRICE_DESC) }
                    )
                }
                item {
                    FilterChip(
                        label = "Nome A-Z",
                        isSelected = state.sortOrder == SortOrder.NAME_ASC,
                        onClick = { viewModel.updateSortOrder(SortOrder.NAME_ASC) }
                    )
                }
                item {
                    FilterChip(
                        label = "N° Set",
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
                items(types) { type ->
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
                    items(setCounts) { (setName, count) ->
                        FilterChip(
                            label = "$setName ($count)",
                            isSelected = state.selectedSet == setName,
                            onClick = { viewModel.filterBySet(setName) }
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
                    items(rarityCounts) { (rarity, count) ->
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
                colors = ButtonDefaults.buttonColors(containerColor = BlueCard),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Done, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mostra risultati", fontWeight = FontWeight.Bold)
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
                    label = "Categoria: ${state.supertypeFilter.name}",
                    onRemove = { viewModel.filterBySupertype(SupertypeFilter.ALL) }
                )
            }
        }
        if (state.sortOrder != SortOrder.NEWEST) {
            item {
                RemovableFilterChip(
                    label = "Ordine: ${state.sortOrder.name}",
                    onRemove = { viewModel.updateSortOrder(SortOrder.NEWEST) }
                )
            }
        }
    }
}

@Composable
fun RemovableFilterChip(label: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BlueCard.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, BlueCard.copy(alpha = 0.5f))
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
                color = TextWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun ExpansionAccordionSection(
    expansionName: String,
    totalCards: Int,
    uniqueCards: Int,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        color = DarkCard,
        shape = RoundedCornerShape(12.dp), // Angoli leggermente meno arrotondati per un look più pulito
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 10.dp, vertical = 8.dp) // Padding ridotto
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = BlueCard.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, BlueCard.copy(alpha = 0.25f))
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesomeMosaic,
                        contentDescription = null,
                        tint = BlueCard,
                        modifier = Modifier.padding(6.dp).size(14.dp) // Icona e contenitore più piccoli
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = expansionName,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, // Leggermente più piccolo
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = BlueCard.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, BlueCard.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "x$totalCards",
                                color = BlueCard,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "$uniqueCards uniche · $totalCards tot.", // Testo abbreviato per restare su una riga
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                Icon(
                    imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = !isCollapsed,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    content()
                }
            }
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
        color = DarkCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = BlueCard, modifier = Modifier.size(18.dp))
                Text(title, color = TextWhite, fontWeight = FontWeight.SemiBold)
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
fun FilterSectionTitle(title: String) {
    Text(
        text = title,
        color = TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) BlueCard else DarkCard,
        border = if (!isSelected) BorderStroke(1.dp, TextMuted.copy(alpha = 0.3f)) else null
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else TextWhite,
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
            .background(DarkCard)
            .then(
                when {
                    isSelected -> Modifier.border(if (gridColumns > 4) 1.dp else 2.dp, BlueCard, RoundedCornerShape(if (gridColumns > 4) 4.dp else 10.dp))
                    else -> Modifier.border(if (gridColumns > 4) 0.5.dp else 1.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(if (gridColumns > 4) 4.dp else 10.dp))
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (card.imageUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = safeImageUrl(card.imageUrl),
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = { CollectionCardImageFallback(card = card, compact = gridColumns > 4) }
            )
        } else {
            CollectionCardImageFallback(card = card, compact = gridColumns > 4)
        }

        if (isSelected) Box(modifier = Modifier
            .fillMaxSize()
            .background(BlueCard.copy(alpha = 0.15f)))

        // Selection checkbox (top-left)
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(if (gridColumns > 4) 2.dp else 4.dp)
                    .size(if (gridColumns > 4) 12.dp else 20.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) BlueCard else Color.Black.copy(alpha = 0.5f))
                    .border(if (gridColumns > 4) 1.dp else 1.5.dp, if (isSelected) BlueCard else Color.White.copy(alpha = 0.4f), CircleShape),
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
                .clip(CircleShape).background(BlueCard),
            contentAlignment = Alignment.Center
        ) {
            Text("x${card.quantity}", color = Color.White, fontSize = if (gridColumns > 4) 7.sp else 10.sp, fontWeight = FontWeight.Bold)
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
            .background(if (isSelected) BlueCard.copy(alpha = 0.15f) else DarkCard)
            .then(
                if (isSelected) Modifier.border(1.dp, BlueCard, RoundedCornerShape(12.dp)) else Modifier
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
                    .background(if (isSelected) BlueCard else Color.Transparent)
                    .border(1.5.dp, if (isSelected) BlueCard else TextMuted.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }

        if (card.imageUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = safeImageUrl(card.imageUrl),
                contentDescription = card.name,
                modifier = Modifier.size(50.dp, 70.dp).clip(RoundedCornerShape(4.dp)),
                error = { CollectionCardImageFallback(card = card, compact = true) }
            )
        } else {
            Box(modifier = Modifier.size(50.dp, 70.dp).clip(RoundedCornerShape(4.dp))) {
                CollectionCardImageFallback(card = card, compact = true)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.name, color = TextWhite, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                if (card.estimatedValue > 0) {
                    Text("\u20AC${"%.2f".format(card.estimatedValue)}", color = GreenCard, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            Text("${card.set} \u00B7 x${card.quantity}", color = TextMuted, fontSize = 12.sp)
        }
        if (!isSelectionMode) {
            Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
        }
    }
}

@Composable
fun StatMiniCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.1f)).padding(10.dp)) {
        Column {
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(label, color = TextMuted, fontSize = 10.sp)
        }
    }
}
