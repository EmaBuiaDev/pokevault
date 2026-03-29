package com.example.pokevault.ui.collection

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pokevault.data.model.PokemonCard
import com.example.pokevault.ui.home.components.SearchBar
import com.example.pokevault.ui.theme.*
import com.example.pokevault.util.Constants
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

    // Snackbar per messaggi
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.successMessage, state.errorMessage) {
        val msg = state.successMessage ?: state.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCard,
                containerColor = BlueCard,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi carta")
            }
        },
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
                    Text("Le mie carte", fontWeight = FontWeight.SemiBold, color = TextWhite)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Indietro", tint = TextWhite)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            imageVector = if (state.isGridView) Icons.Default.ViewList
                                          else Icons.Default.GridView,
                            contentDescription = "Cambia vista",
                            tint = TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )

            // ── Stats Cards ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatMiniCard(
                    label = "Carte",
                    value = "${state.stats.totalCards}",
                    color = BlueCard,
                    modifier = Modifier.weight(1f)
                )
                StatMiniCard(
                    label = "Uniche",
                    value = "${state.stats.uniqueCards}",
                    color = PurpleCard,
                    modifier = Modifier.weight(1f)
                )
                StatMiniCard(
                    label = "Valore",
                    value = "€${"%.0f".format(state.stats.totalValue)}",
                    color = GreenCard,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Ricerca ──
            SearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Filtri per tipo ──
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    TypeFilterChip(
                        label = "Tutti",
                        isSelected = state.selectedType == null,
                        onClick = { viewModel.filterByType(null) }
                    )
                }
                items(Constants.POKEMON_TYPES.keys.toList()) { type ->
                    TypeFilterChip(
                        label = type,
                        isSelected = state.selectedType == type,
                        onClick = { viewModel.filterByType(type) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Contenuto principale ──
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BlueCard)
                }
            } else if (state.filteredCards.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🃏", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (state.cards.isEmpty()) "Nessuna carta nella collezione"
                                   else "Nessun risultato trovato",
                            color = TextGray,
                            fontSize = 16.sp
                        )
                        if (state.cards.isEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Tocca + per aggiungere la prima carta!",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                // Griglia o Lista
                LazyVerticalGrid(
                    columns = if (state.isGridView) GridCells.Fixed(2) else GridCells.Fixed(1),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = state.filteredCards,
                        key = { it.id }
                    ) { card ->
                        if (state.isGridView) {
                            CardGridItem(
                                card = card,
                                onClick = { onCardClick(card.id) },
                                onDelete = { viewModel.deleteCard(card.id) }
                            )
                        } else {
                            CardListItem(
                                card = card,
                                onClick = { onCardClick(card.id) },
                                onDelete = { viewModel.deleteCard(card.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatMiniCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(12.dp)
    ) {
        Column {
            Text(text = value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(text = label, color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
fun TypeFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = if (isSelected) TextWhite else TextMuted,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) BlueCard else DarkCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun CardGridItem(
    card: PokemonCard,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val typeColor = getTypeColor(card.type)
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Elimina carta") },
            text = { Text("Vuoi eliminare ${card.name} dalla collezione?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Elimina", color = RedCard)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annulla")
                }
            },
            containerColor = DarkSurface
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: tipo e delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(typeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = card.type, color = typeColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }

                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Opzioni",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { showDeleteDialog = true }
                )
            }

            // Centro: emoji tipo
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getTypeEmoji(card.type),
                    fontSize = 40.sp
                )
            }

            // Bottom: info
            Column {
                Text(
                    text = card.name,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "${card.hp} HP", color = typeColor, fontSize = 12.sp)
                    if (card.estimatedValue > 0) {
                        Text(
                            text = "€${"%.2f".format(card.estimatedValue)}",
                            color = GreenCard,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Text(
                    text = card.set,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Badge quantità
        if (card.quantity > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(BlueCard),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "×${card.quantity}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Badge gradata
        if (card.isGraded) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 40.dp, start = 14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(StarGold.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "⭐ ${card.grade ?: ""}",
                    color = StarGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun CardListItem(
    card: PokemonCard,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val typeColor = getTypeColor(card.type)
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Elimina carta") },
            text = { Text("Vuoi eliminare ${card.name} dalla collezione?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Elimina", color = RedCard)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annulla")
                }
            },
            containerColor = DarkSurface
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Icona tipo
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(typeColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = getTypeEmoji(card.type), fontSize = 28.sp)
        }

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.name,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = "${card.set} · ${card.rarity}",
                color = TextGray,
                fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "${card.hp} HP", color = typeColor, fontSize = 12.sp)
                if (card.isGraded) {
                    Text(text = "⭐ ${card.grade}", color = StarGold, fontSize = 12.sp)
                }
            }
        }

        // Valore e quantità
        Column(horizontalAlignment = Alignment.End) {
            if (card.estimatedValue > 0) {
                Text(
                    text = "€${"%.2f".format(card.estimatedValue)}",
                    color = GreenCard,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            if (card.quantity > 1) {
                Text(text = "×${card.quantity}", color = TextMuted, fontSize = 12.sp)
            }
        }

        // Delete
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Elimina",
            tint = TextMuted,
            modifier = Modifier
                .size(20.dp)
                .clickable { showDeleteDialog = true }
        )
    }
}

// Helper functions
fun getTypeColor(type: String): Color {
    return when (type.lowercase()) {
        "fuoco" -> RedCard
        "acqua" -> BlueCard
        "erba" -> GreenCard
        "elettro" -> YellowCard
        "psico" -> PurpleCard
        "lotta" -> Color(0xFFF97316)
        "buio" -> Color(0xFF6366F1)
        "metallo" -> Color(0xFF6B7280)
        "drago" -> Color(0xFF7C3AED)
        "folletto" -> Color(0xFFEC4899)
        else -> Color(0xFF9CA3AF)
    }
}

fun getTypeEmoji(type: String): String {
    return when (type.lowercase()) {
        "fuoco" -> "🔥"
        "acqua" -> "💧"
        "erba" -> "🌿"
        "elettro" -> "⚡"
        "psico" -> "🔮"
        "lotta" -> "👊"
        "buio" -> "🌑"
        "metallo" -> "⚙️"
        "drago" -> "🐉"
        "folletto" -> "🧚"
        else -> "🎴"
    }
}
