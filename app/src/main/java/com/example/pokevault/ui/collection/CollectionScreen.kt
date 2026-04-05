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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.pokevault.data.model.PokemonCard
import com.example.pokevault.ui.home.components.SearchBar
import com.example.pokevault.ui.theme.*
import com.example.pokevault.util.getTypeEmojiForCollection
import com.example.pokevault.viewmodel.CollectionViewModel

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
            .sortedBy { (_, group) -> group.first().cardNumber.toIntOrNull() ?: Int.MAX_VALUE }
            .map { (key, group) -> key to group }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
        ) {
            // ── Top Bar ──
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
                        // Select all
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
                SearchBar(query = state.searchQuery, onQueryChange = { viewModel.updateSearchQuery(it) })
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── FILTRI ESPANSIONI CON CONTEGGIO ──
            val setCounts = remember(state.cards) {
                state.cards.groupBy { it.set }
                    .mapValues { it.value.sumOf { c -> c.quantity } }
                    .toList()
                    .sortedByDescending { it.second }
            }

            if (setCounts.isNotEmpty() && !isSelectionMode) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            label = "Tutte (${state.stats.totalCards})",
                            isSelected = state.selectedType == null,
                            onClick = { viewModel.filterByType(null) }
                        )
                    }
                    items(setCounts) { (setName, count) ->
                        FilterChip(
                            label = "$setName ($count)",
                            isSelected = state.selectedType == setName,
                            onClick = { viewModel.filterByType(setName) }
                        )
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
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 8.dp,
                            bottom = if (isSelectionMode) 80.dp else 8.dp
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

                    // Selection bottom bar
                    if (isSelectionMode && selectedGroupKeys.isNotEmpty()) {
                        var showConfirm by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, DarkSurface.copy(alpha = 0.95f), DarkSurface)
                                    )
                                )
                                .padding(top = 16.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Cancel
                            IconButton(
                                onClick = { isSelectionMode = false; selectedGroupKeys = emptySet() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Close, null, tint = TextMuted)
                            }

                            // Info
                            Text(
                                text = "${selectedGroupKeys.size} carte",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )

                            if (showConfirm) {
                                // Confirm delete
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
                                // Cancel confirm
                                OutlinedButton(
                                    onClick = { showConfirm = false },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, TextMuted.copy(alpha = 0.3f)),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Annulla", color = TextMuted, fontSize = 13.sp)
                                }
                            } else {
                                // Delete button
                                Button(
                                    onClick = { showConfirm = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = RedCard),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Elimina ${selectedGroupKeys.size}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
            Text(card.name, color = TextWhite, fontWeight = FontWeight.Bold)
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
