package com.example.pokevault.ui.pokedex

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.pokevault.util.AppLocale
import com.example.pokevault.viewmodel.SetsViewModel

// Formatta data
fun formatDate(date: String): String {
    return try {
        val parts = date.split("/")
        if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else date
    } catch (_: Exception) { date }
}

// ── Animazione Pokéball ──
@Composable
fun PokeballLoadingAnimation(
    message: String = "Caricamento...",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pokeball")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Pokéball disegnata con Canvas
        Box(
            modifier = Modifier
                .size(64.dp)
                .offset(y = (-8 * bounce).dp)
                .rotate(rotation)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val s = size.minDimension
                val r = s / 2
                val cx = size.width / 2
                val cy = size.height / 2

                // Metà superiore rossa
                drawArc(
                    color = Color(0xFFEF4444),
                    startAngle = 180f, sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset.Zero, size = Size(s, s)
                )
                // Metà inferiore bianca
                drawArc(
                    color = Color(0xFFF5F5F5),
                    startAngle = 0f, sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset.Zero, size = Size(s, s)
                )
                // Linea nera centrale
                drawLine(
                    color = Color(0xFF2D2D2D),
                    start = Offset(0f, cy),
                    end = Offset(s, cy),
                    strokeWidth = s * 0.06f
                )
                // Cerchio esterno
                drawCircle(
                    color = Color(0xFF2D2D2D),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = s * 0.05f)
                )
                // Cerchio centrale bianco
                drawCircle(color = Color(0xFFF5F5F5), radius = r * 0.25f, center = Offset(cx, cy))
                // Cerchio centrale bordo
                drawCircle(color = Color(0xFF2D2D2D), radius = r * 0.25f, center = Offset(cx, cy), style = Stroke(width = s * 0.05f))
                // Cerchio interno
                drawCircle(color = Color(0xFFF5F5F5), radius = r * 0.12f, center = Offset(cx, cy))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = message,
            color = TextGray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

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
                    Icon(Icons.Default.ArrowBack, AppLocale.back, tint = TextWhite)
                }
            },
            actions = {
                if (!state.isLoading) {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Aggiorna", tint = TextMuted)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
        )

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCard),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TabItem(AppLocale.extensions, !isSearchingCards) {
                    isSearchingCards = false; viewModel.clearCardSearch()
                }
                TabItem(AppLocale.searchCards, isSearchingCards) {
                    isSearchingCards = true
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SearchBarBg)
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, AppLocale.search, tint = TextMuted, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        val placeholder = if (isSearchingCards) AppLocale.searchCardPlaceholder else AppLocale.searchSetPlaceholder
                        val query = if (isSearchingCards) state.cardSearchQuery else state.searchQuery
                        if (query.isEmpty()) Text(placeholder, color = TextMuted, fontSize = 14.sp)
                        BasicTextField(
                            value = query,
                            onValueChange = {
                                if (isSearchingCards) viewModel.searchCardsByName(it) else viewModel.updateSearch(it)
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(color = TextWhite, fontSize = 14.sp),
                            singleLine = true, cursorBrush = SolidColor(BlueCard),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    val query = if (isSearchingCards) state.cardSearchQuery else state.searchQuery
                    if (query.isNotEmpty()) {
                        Icon(Icons.Default.Close, AppLocale.cancel, tint = TextMuted,
                            modifier = Modifier.size(20.dp).clickable {
                                if (isSearchingCards) viewModel.clearCardSearch() else viewModel.updateSearch("")
                            })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isSearchingCards) {
            CardSearchResults(state.searchedCards, state.isSearchingCards, state.cardSearchQuery) { setId -> onSetClick(setId) }
        } else {
            // ── Filtri serie migliorati ──
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    SeriesFilterChip(
                        label = AppLocale.all,
                        count = state.allSets.size,
                        isSelected = state.selectedSeries == null,
                        onClick = { viewModel.filterBySeries(null) }
                    )
                }
                items(state.seriesList) { series ->
                    val count = state.allSets.count { it.series == series }
                    SeriesFilterChip(
                        label = series,
                        count = count,
                        isSelected = state.selectedSeries == series,
                        onClick = { viewModel.filterBySeries(series) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (!state.isLoading) {
                Text(
                    text = AppLocale.expansionsCount(state.filteredSets.size),
                    color = TextMuted, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            // ── Contenuto ──
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PokeballLoadingAnimation(message = AppLocale.loadingSets)
                }
            } else if (state.errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠️", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(state.errorMessage, color = TextGray, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.refresh() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BlueCard)
                        ) { Text(AppLocale.retry) }
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

// ── Tab item ──
@Composable
fun RowScope.TabItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (isSelected) TextWhite else TextMuted,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 14.sp, textAlign = TextAlign.Center,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .background(if (isSelected) BlueCard.copy(alpha = 0.3f) else Color.Transparent)
            .padding(vertical = 12.dp)
    )
}

// ── Filtro serie migliorato con conteggio ──
@Composable
fun SeriesFilterChip(label: String, count: Int, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) BlueCard else DarkCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) TextWhite else TextMuted,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp, maxLines = 1
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (isSelected) Color.White.copy(alpha = 0.2f)
                    else TextMuted.copy(alpha = 0.15f)
                )
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text(
                text = "$count",
                color = if (isSelected) TextWhite else TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ── Set Card con total (non printedTotal) e data formattata ──
@Composable
fun SetCard(set: TcgSet, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(185.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Logo
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
                    // Usa total (include secret rare)
                    Text(
                        text = AppLocale.cardsCount(set.total),
                        color = BlueCard,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Data formattata GG/MM/AAAA
                    Text(
                        text = formatDate(set.releaseDate),
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = set.series, color = TextMuted, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Card search results ──
@Composable
fun CardSearchResults(cards: List<TcgCard>, isLoading: Boolean, query: String, onCardSetClick: (String) -> Unit) {
    if (query.length < 2) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔍", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(AppLocale.writeAtLeast2, color = TextMuted, fontSize = 14.sp)
            }
        }
    } else if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PokeballLoadingAnimation(message = AppLocale.searchFor(query))
        }
    } else if (cards.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("😔", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(AppLocale.noResults, color = TextGray, fontSize = 14.sp)
            }
        }
    } else {
        val grouped = cards.groupBy { it.set?.name ?: AppLocale.unknown }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(3) }) {
                Text(AppLocale.resultsCountInExpansions(cards.size, grouped.size), color = TextMuted, fontSize = 13.sp)
            }
            grouped.forEach { (setName, setCards) ->
                item(span = { GridItemSpan(3) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(DarkCard)
                            .clickable { setCards.firstOrNull()?.set?.id?.let { onCardSetClick(it) } }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(setName, color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(AppLocale.resultsCount(setCards.size), color = TextMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    }
                }
                // FIX DEFINITIVO: La chiave deve essere unica in tutta la LazyVerticalGrid.
                // Se una carta appare in più set raggruppati, "it.id" non basta perché si ripete.
                // Usiamo "id_setName" per garantire l'univocità assoluta.
                items(setCards.sortedBy { it.number.toIntOrNull() ?: 999 }, key = { "${it.id}_$setName" }) { card ->
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(10.dp))
                        .clickable { card.set?.id?.let { onCardSetClick(it) } }) {
                        AsyncImage(model = card.images.small, contentDescription = card.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

// SeriesChip non più necessario, sostituito da SeriesFilterChip
