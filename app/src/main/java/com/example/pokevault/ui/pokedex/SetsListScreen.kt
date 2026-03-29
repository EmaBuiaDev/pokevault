package com.example.pokevault.ui.pokedex

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.pokevault.data.remote.TcgCard
import com.example.pokevault.data.remote.TcgSet
import com.example.pokevault.ui.theme.*
import com.example.pokevault.viewmodel.SetsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetsListScreen(
    onBack: () -> Unit,
    onSetClick: (String) -> Unit,
    viewModel: SetsViewModel = viewModel()
) {
    val state = viewModel.uiState
    var isSearchingCards by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = { Text("Pokédex", fontWeight = FontWeight.Bold, color = TextWhite) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Indietro", tint = TextWhite)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
        )

        // ── Barra ricerca con toggle ──
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // Toggle: Espansioni / Cerca carte
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCard),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = "Espansioni",
                    color = if (!isSearchingCards) TextWhite else TextMuted,
                    fontWeight = if (!isSearchingCards) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            isSearchingCards = false
                            viewModel.clearCardSearch()
                        }
                        .background(
                            if (!isSearchingCards) BlueCard.copy(alpha = 0.3f)
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .padding(vertical = 12.dp)
                )
                Text(
                    text = "Cerca carte",
                    color = if (isSearchingCards) TextWhite else TextMuted,
                    fontWeight = if (isSearchingCards) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isSearchingCards = true }
                        .background(
                            if (isSearchingCards) BlueCard.copy(alpha = 0.3f)
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .padding(vertical = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Barra di ricerca
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SearchBarBg)
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Search, "Cerca",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        val placeholder = if (isSearchingCards) "Cerca una carta tra tutte le espansioni..."
                        else "Cerca un'espansione..."
                        val query = if (isSearchingCards) state.cardSearchQuery else state.searchQuery

                        if (query.isEmpty()) {
                            Text(placeholder, color = TextMuted, fontSize = 14.sp)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = {
                                if (isSearchingCards) viewModel.searchCardsByName(it)
                                else viewModel.updateSearch(it)
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = TextWhite, fontSize = 14.sp
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(BlueCard),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Bottone cancella
                    val query = if (isSearchingCards) state.cardSearchQuery else state.searchQuery
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close, "Cancella",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    if (isSearchingCards) viewModel.clearCardSearch()
                                    else viewModel.updateSearch("")
                                }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Contenuto basato su modalità ──
        if (isSearchingCards) {
            // Risultati ricerca carte globale
            CardSearchResults(
                cards = state.searchedCards,
                isLoading = state.isSearchingCards,
                query = state.cardSearchQuery,
                onCardSetClick = { setId -> onSetClick(setId) }
            )
        } else {
            // Filtri serie
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    SeriesChip(
                        label = "Tutte",
                        isSelected = state.selectedSeries == null,
                        onClick = { viewModel.filterBySeries(null) }
                    )
                }
                items(state.seriesList) { series ->
                    SeriesChip(
                        label = series,
                        isSelected = state.selectedSeries == series,
                        onClick = { viewModel.filterBySeries(series) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${state.filteredSets.size} espansioni",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Griglia espansioni
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BlueCard)
                }
            } else if (state.errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠️", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(state.errorMessage, color = TextGray, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { viewModel.refresh() }) {
                            Text("Riprova", color = BlueCard)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = state.filteredSets, key = { it.id }) { set ->
                        SetCard(set = set, onClick = { onSetClick(set.id) })
                    }
                }
            }
        }
    }
}

// ── Risultati ricerca carte ──
@Composable
fun CardSearchResults(
    cards: List<TcgCard>,
    isLoading: Boolean,
    query: String,
    onCardSetClick: (String) -> Unit
) {
    if (query.length < 2) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔍", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Scrivi almeno 2 caratteri per cercare",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        }
    } else if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = BlueCard)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Cerco \"$query\"...", color = TextGray, fontSize = 14.sp)
            }
        }
    } else if (cards.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("😔", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Nessuna carta trovata per \"$query\"", color = TextGray, fontSize = 14.sp)
            }
        }
    } else {
        // Raggruppa per set
        val grouped = cards.groupBy { it.set?.name ?: "Sconosciuto" }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Conteggio risultati
            item(span = { GridItemSpan(3) }) {
                Text(
                    text = "${cards.size} carte trovate in ${grouped.size} espansioni",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            grouped.forEach { (setName, setCards) ->
                // Header set
                item(span = { GridItemSpan(3) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkCard)
                            .clickable {
                                val setId = setCards.firstOrNull()?.set?.id
                                if (setId != null) onCardSetClick(setId)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(setName, color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${setCards.size} risultati", color = TextMuted, fontSize = 11.sp)
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Apri set",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Carte del set
                items(
                    items = setCards.sortedBy { it.number.toIntOrNull() ?: 999 },
                    key = { it.id }
                ) { card ->
                    SearchResultCardItem(card = card, onClick = {
                        val setId = card.set?.id
                        if (setId != null) onCardSetClick(setId)
                    })
                }
            }
        }
    }
}

@Composable
fun SearchResultCardItem(card: TcgCard, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = card.images.small,
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ── Set card (invariato ma qui per completezza) ──
@Composable
fun SetCard(set: TcgSet, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = set.images.logo,
                    contentDescription = set.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(70.dp)
                )
            }
            Column {
                Text(
                    text = set.name, color = TextWhite, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${set.printedTotal} carte", color = BlueCard, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(set.releaseDate, color = TextMuted, fontSize = 11.sp)
                }
                Text(set.series, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun SeriesChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (isSelected) TextWhite else TextMuted,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 13.sp, maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) BlueCard else DarkCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
