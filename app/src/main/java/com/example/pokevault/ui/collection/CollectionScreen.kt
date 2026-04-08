package com.example.pokevault.ui.collection

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.pokevault.data.model.PokemonCard
import com.example.pokevault.ui.home.components.SearchBar
import com.example.pokevault.ui.theme.*
import com.example.pokevault.util.AppLocale
import com.example.pokevault.util.getTypeEmojiForCollection
import com.example.pokevault.viewmodel.CollectionViewModel
import com.example.pokevault.viewmodel.SortOrder
import com.example.pokevault.viewmodel.SupertypeFilter

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

    // Compute grouped cards with keys
    val groupedCards = remember(state.filteredCards) {
        state.filteredCards
            .groupBy { it.apiCardId.ifBlank { "${it.name}_${it.set}_${it.cardNumber}" } }
            .entries
            .map { (key, group) -> key to group }
    }

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
                    StatMiniCard("Valore", "\u20AC${"%.0f".format(state.stats.totalValue)}", GreenCard, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isSelectionMode) {
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
                        Spacer(modifier = Modifier.width(8.dp))
                        // Pulsante Filtri
                        Surface(
                            onClick = { showFilters = true },
                            color = if (state.selectedSet != null || state.selectedType != null || state.supertypeFilter != SupertypeFilter.ALL || state.sortOrder != SortOrder.NEWEST) BlueCard else DarkCard,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(48.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Filtri",
                                    tint = TextWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                                // Pallino notifica se filtri attivi
                                if (state.selectedSet != null || state.selectedType != null || state.supertypeFilter != SupertypeFilter.ALL) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color.Red)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
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
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 0.dp,
                                bottom = if (isSelectionMode) 80.dp else 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (state.isGridView) {
                                items(groupedCards.chunked(3)) { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        row.forEach { (groupKey, group) ->
                                            val representative = group.first()
                                            val totalQty = group.sumOf { it.quantity }
                                            CollectionCardGridItem(
                                                card = representative.copy(quantity = totalQty),
                                                isSelected = groupKey in selectedGroupKeys,
                                                isSelectionMode = isSelectionMode,
                                                onClick = {
                                                    if (isSelectionMode) {
                                                        selectedGroupKeys = if (groupKey in selectedGroupKeys)
                                                            selectedGroupKeys - groupKey else selectedGroupKeys + groupKey
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
                                        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }
                            } else {
                                items(groupedCards) { (groupKey, group) ->
                                    val representative = group.first()
                                    val totalQty = group.sumOf { it.quantity }
                                    CollectionCardListItem(
                                        card = representative.copy(quantity = totalQty),
                                        isSelected = groupKey in selectedGroupKeys,
                                        isSelectionMode = isSelectionMode,
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedGroupKeys = if (groupKey in selectedGroupKeys)
                                                    selectedGroupKeys - groupKey else selectedGroupKeys + groupKey
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    state: com.example.pokevault.viewmodel.CollectionUiState,
    viewModel: CollectionViewModel,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filtri e Ordinamento", style = MaterialTheme.typography.headlineSmall, color = TextWhite)
                TextButton(onClick = {
                    viewModel.filterBySupertype(SupertypeFilter.ALL)
                    viewModel.filterBySet(null)
                    viewModel.filterByType(null)
                    viewModel.updateSortOrder(SortOrder.NEWEST)
                }) {
                    Text("Reset", color = BlueCard)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── ORDINAMENTO ──
            FilterSectionTitle("Ordina per")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { FilterChip(label = "Recenti", isSelected = state.sortOrder == SortOrder.NEWEST, onClick = { viewModel.updateSortOrder(SortOrder.NEWEST) }) }
                item { FilterChip(label = "€ Crescente", isSelected = state.sortOrder == SortOrder.PRICE_ASC, onClick = { viewModel.updateSortOrder(SortOrder.PRICE_ASC) }) }
                item { FilterChip(label = "€ Decrescente", isSelected = state.sortOrder == SortOrder.PRICE_DESC, onClick = { viewModel.updateSortOrder(SortOrder.PRICE_DESC) }) }
                item { FilterChip(label = "Nome A-Z", isSelected = state.sortOrder == SortOrder.NAME_ASC, onClick = { viewModel.updateSortOrder(SortOrder.NAME_ASC) }) }
                item { FilterChip(label = "N° Set", isSelected = state.sortOrder == SortOrder.NUMBER, onClick = { viewModel.updateSortOrder(SortOrder.NUMBER) }) }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── CATEGORIA (Supertype) ──
            FilterSectionTitle("Categoria")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { FilterChip(label = "Tutti", isSelected = state.supertypeFilter == SupertypeFilter.ALL, onClick = { viewModel.filterBySupertype(SupertypeFilter.ALL) }) }
                item { FilterChip(label = "Pokémon", isSelected = state.supertypeFilter == SupertypeFilter.POKEMON, onClick = { viewModel.filterBySupertype(SupertypeFilter.POKEMON) }) }
                item { FilterChip(label = "Trainer", isSelected = state.supertypeFilter == SupertypeFilter.TRAINER, onClick = { viewModel.filterBySupertype(SupertypeFilter.TRAINER) }) }
                item { FilterChip(label = "Energy", isSelected = state.supertypeFilter == SupertypeFilter.ENERGY, onClick = { viewModel.filterBySupertype(SupertypeFilter.ENERGY) }) }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── TIPOLOGIA (Elemental Type) ──
            val types = AppLocale.getTypes()
            FilterSectionTitle("Tipologia")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { FilterChip(label = "Tutti", isSelected = state.selectedType == null, onClick = { viewModel.filterByType(null) }) }
                items(types) { type ->
                    FilterChip(label = type, isSelected = state.selectedType == type, onClick = { viewModel.filterByType(type) })
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── ESPANSIONI (Set) ──
            val setCounts = remember(state.cards) {
                state.cards.groupBy { it.set }
                    .mapValues { it.value.sumOf { c -> c.quantity } }
                    .toList()
                    .sortedByDescending { it.second }
            }
            if (setCounts.isNotEmpty()) {
                FilterSectionTitle("Espansione")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { FilterChip(label = "Tutti i set", isSelected = state.selectedSet == null, onClick = { viewModel.filterBySet(null) }) }
                    items(setCounts) { (setName, count) ->
                        FilterChip(label = "$setName ($count)", isSelected = state.selectedSet == setName, onClick = { viewModel.filterBySet(setName) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlueCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Mostra Risultati", fontWeight = FontWeight.Bold)
            }
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
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .then(
                when {
                    isSelected -> Modifier.border(2.dp, BlueCard, RoundedCornerShape(10.dp))
                    else -> Modifier.border(1.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (card.imageUrl.isNotBlank()) {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(getTypeEmojiForCollection(card.type), fontSize = 32.sp)
            }
        }

        if (isSelected) Box(modifier = Modifier
            .fillMaxSize()
            .background(BlueCard.copy(alpha = 0.15f)))

        // Selection checkbox (top-left)
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) BlueCard else Color.Black.copy(alpha = 0.5f))
                    .border(1.5.dp, if (isSelected) BlueCard else Color.White.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }

        // Quantity badge (top-right)
        Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)
                .clip(CircleShape).background(BlueCard),
            contentAlignment = Alignment.Center
        ) {
            Text("x${card.quantity}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                modifier = Modifier.size(50.dp, 70.dp).clip(RoundedCornerShape(4.dp))
            )
        } else {
            Box(modifier = Modifier.size(50.dp, 70.dp).clip(RoundedCornerShape(4.dp)).background(DarkSurface), contentAlignment = Alignment.Center) {
                Text(getTypeEmojiForCollection(card.type), fontSize = 24.sp)
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
