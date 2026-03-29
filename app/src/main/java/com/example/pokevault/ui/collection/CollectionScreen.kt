package com.example.pokevault.ui.collection

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
                    Text("Le mie carte", fontWeight = FontWeight.Bold, color = TextWhite)
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                StatMiniCard(
                    label = "Set",
                    value = "${state.filteredCards.groupBy { it.set }.size}",
                    color = YellowCard,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Ricerca ──
            SearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Filtri set ──
            val sets = state.cards.map { it.set }.distinct().sorted()
            if (sets.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            label = "Tutti (${state.cards.size})",
                            isSelected = state.selectedType == null,
                            onClick = { viewModel.filterByType(null) }
                        )
                    }
                    items(sets) { set ->
                        val count = state.cards.count { it.set == set }
                        FilterChip(
                            label = "$set ($count)",
                            isSelected = state.selectedType == set,
                            onClick = { viewModel.filterByType(set) }
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
            } else if (state.filteredCards.isEmpty()) {
                EmptyCollectionState(hasCards = state.cards.isNotEmpty())
            } else {
                // Raggruppa per set e ordina per cardNumber
                val groupedCards = state.filteredCards
                    .groupBy { it.set }
                    .toSortedMap()
                    .mapValues { entry ->
                        entry.value.sortedBy {
                            it.cardNumber.toIntOrNull() ?: Int.MAX_VALUE
                        }
                    }

                if (state.isGridView) {
                    // Vista griglia raggruppata per set
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        groupedCards.forEach { (setName, cards) ->
                            // Header espansione
                            item(key = "header_$setName") {
                                SetGroupHeader(
                                    setName = setName,
                                    cardCount = cards.size
                                )
                            }

                            // Carte in righe da 3
                            val rows = cards.chunked(3)
                            items(rows, key = { row -> row.map { it.id }.joinToString() }) { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    row.forEach { card ->
                                        CollectionCardGridItem(
                                            card = card,
                                            onClick = { onCardClick(card.id) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    // Riempi spazi vuoti
                                    repeat(3 - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(8.dp)) }
                        }

                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                } else {
                    // Vista lista raggruppata per set
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        groupedCards.forEach { (setName, cards) ->
                            item(key = "header_$setName") {
                                SetGroupHeader(setName = setName, cardCount = cards.size)
                            }

                            items(cards, key = { it.id }) { card ->
                                CollectionCardListItem(
                                    card = card,
                                    onClick = { onCardClick(card.id) },
                                    onDelete = { viewModel.deleteCard(card.id) }
                                )
                            }

                            item { Spacer(modifier = Modifier.height(8.dp)) }
                        }

                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

// ── Header gruppo espansione ──
@Composable
fun SetGroupHeader(setName: String, cardCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = setName.ifBlank { "Senza set" },
            color = TextWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(BlueCard.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = "$cardCount",
                color = BlueCard,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── Card griglia con immagine reale ──
@Composable
fun CollectionCardGridItem(
    card: PokemonCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasImage = card.imageUrl.isNotBlank()

    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(1.5.dp, GreenCard.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        if (hasImage) {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback senza immagine
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = getTypeEmojiForCollection(card.type),
                        fontSize = 28.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = card.name,
                        color = TextWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        // Overlay info in basso
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(horizontal = 5.dp, vertical = 3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = card.name,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (card.estimatedValue > 0) {
                    Text(
                        text = "${"%.2f".format(card.estimatedValue)}€",
                        color = GreenCard,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Badge quantità
        if (card.quantity > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(BlueCard),
                contentAlignment = Alignment.Center
            ) {
                Text("×${card.quantity}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Badge gradata
        if (card.isGraded) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(StarGold.copy(alpha = 0.85f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("⭐${card.grade ?: ""}", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Card lista con immagine reale ──
@Composable
fun CollectionCardListItem(
    card: PokemonCard,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Elimina carta", color = TextWhite) },
            text = { Text("Vuoi eliminare ${card.name}?", color = TextGray) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Elimina", color = RedCard)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Annulla", color = TextGray) }
            },
            containerColor = DarkSurface
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mini immagine
        Box(
            modifier = Modifier
                .width(45.dp)
                .height(63.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurface)
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
                    Text(getTypeEmojiForCollection(card.type), fontSize = 22.sp)
                }
            }
        }

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.name,
                color = TextWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${card.set} · ${card.rarity}",
                color = TextMuted,
                fontSize = 11.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (card.hp > 0) {
                    Text("${card.hp} HP", color = TextGray, fontSize = 11.sp)
                }
                if (card.cardNumber.isNotBlank()) {
                    Text("#${card.cardNumber}", color = TextMuted, fontSize = 11.sp)
                }
                if (card.isGraded) {
                    Text("⭐ ${card.grade}", color = StarGold, fontSize = 11.sp)
                }
            }
        }

        // Valore
        if (card.estimatedValue > 0) {
            Text(
                text = "${"%.2f".format(card.estimatedValue)}€",
                color = GreenCard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }

        // Delete
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Elimina",
            tint = TextMuted.copy(alpha = 0.5f),
            modifier = Modifier
                .size(20.dp)
                .clickable { showDeleteDialog = true }
        )
    }
}

// ── Empty state ──
@Composable
fun EmptyCollectionState(hasCards: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🃏", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (!hasCards) "La tua collezione è vuota"
                else "Nessun risultato trovato",
                color = TextWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (!hasCards) "Vai nel Pokédex per aggiungere le tue carte!"
                else "Prova a cercare qualcos'altro",
                color = TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Helpers ──
@Composable
fun StatMiniCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(10.dp)
    ) {
        Column {
            Text(text = value, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(text = label, color = TextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (isSelected) TextWhite else TextMuted,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) BlueCard else DarkCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

fun getTypeEmojiForCollection(type: String): String {
    return when (type.lowercase()) {
        "fire", "fuoco" -> "🔥"
        "water", "acqua" -> "💧"
        "grass", "erba" -> "🌿"
        "lightning", "elettro" -> "⚡"
        "psychic", "psico" -> "🔮"
        "fighting", "lotta" -> "👊"
        "darkness", "buio" -> "🌑"
        "metal", "metallo" -> "⚙️"
        "dragon", "drago" -> "🐉"
        "fairy", "folletto" -> "🧚"
        "colorless" -> "⭐"
        else -> "🎴"
    }
}
