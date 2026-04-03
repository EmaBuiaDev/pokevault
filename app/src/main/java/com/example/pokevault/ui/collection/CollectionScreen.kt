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

            // ── Stats ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatMiniCard("Carte", "${state.stats.totalCards}", BlueCard, Modifier.weight(1f))
                StatMiniCard("Uniche", "${state.stats.uniqueCards}", PurpleCard, Modifier.weight(1f))
                StatMiniCard("Valore", "€${"%.0f".format(state.stats.totalValue)}", GreenCard, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            SearchBar(query = state.searchQuery, onQueryChange = { viewModel.updateSearchQuery(it) })

            Spacer(modifier = Modifier.height(12.dp))

            // ── Contenuto Raggruppato ──
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BlueCard)
                }
            } else {
                // RAGGRUPPAMENTO: Per apiCardId (o combinazione nome+set)
                val groupedCards = state.filteredCards
                    .groupBy { it.apiCardId.ifBlank { "${it.name}_${it.set}_${it.cardNumber}" } }
                    .values
                    .toList()

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.isGridView) {
                        items(groupedCards.chunked(3)) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { group ->
                                    val representative = group.first()
                                    val totalQty = group.sumOf { it.quantity }
                                    CollectionCardGridItem(
                                        card = representative.copy(quantity = totalQty),
                                        onClick = { onCardClick(representative.apiCardId.ifBlank { representative.id }) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    } else {
                        items(groupedCards) { group ->
                            val representative = group.first()
                            val totalQty = group.sumOf { it.quantity }
                            CollectionCardListItem(
                                card = representative.copy(quantity = totalQty),
                                onClick = { onCardClick(representative.apiCardId.ifBlank { representative.id }) },
                                onDelete = { viewModel.deleteCard(representative.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionCardGridItem(card: PokemonCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = card.imageUrl,
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // Badge Quantità Totale
        Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)
                .clip(CircleShape).background(BlueCard),
            contentAlignment = Alignment.Center
        ) {
            Text("x${card.quantity}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CollectionCardListItem(card: PokemonCard, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DarkCard)
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = card.imageUrl,
            contentDescription = card.name,
            modifier = Modifier.size(50.dp, 70.dp).clip(RoundedCornerShape(4.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(card.name, color = TextWhite, fontWeight = FontWeight.Bold)
            Text("${card.set} · x${card.quantity}", color = TextMuted, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
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
