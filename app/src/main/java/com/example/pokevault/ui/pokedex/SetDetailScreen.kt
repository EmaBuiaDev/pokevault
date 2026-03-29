package com.example.pokevault.ui.pokedex

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
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
import com.example.pokevault.data.remote.TcgCard
import com.example.pokevault.ui.theme.*
import com.example.pokevault.viewmodel.SetDetailViewModel

// Mappatura rarità → emoji e colore
data class RarityInfo(
    val emoji: String,
    val color: Color,
    val label: String,
    val sortOrder: Int
)

fun getRarityInfo(rarity: String?): RarityInfo {
    return when (rarity?.lowercase()) {
        "common" -> RarityInfo("●", Color(0xFF9CA3AF), "Comuni", 0)
        "uncommon" -> RarityInfo("◆", Color(0xFF6B7280), "Non comuni", 1)
        "rare" -> RarityInfo("★", Color(0xFFEAB308), "Rare", 2)
        "rare holo" -> RarityInfo("★", Color(0xFFEAB308), "Rare Holo", 3)
        "rare holo ex", "double rare" -> RarityInfo("★★", Color(0xFFF59E0B), "Doppie Rare", 4)
        "rare holo gx" -> RarityInfo("★★", Color(0xFFF59E0B), "Rare GX", 4)
        "rare holo v" -> RarityInfo("★★", Color(0xFFF59E0B), "Rare V", 4)
        "rare ultra", "ultra rare" -> RarityInfo("★★★", Color(0xFFEC4899), "Ultra Rare", 5)
        "rare holo vmax" -> RarityInfo("★★★", Color(0xFFEC4899), "Rare VMAX", 5)
        "rare holo vstar" -> RarityInfo("★★★", Color(0xFFEC4899), "Rare VSTAR", 5)
        "rare secret", "special art rare", "hyper rare" ->
            RarityInfo("★★★★", Color(0xFFE879F9), "Secret Rare", 6)
        "illustration rare" -> RarityInfo("✦", Color(0xFF818CF8), "Illustration Rare", 7)
        "special illustration rare" -> RarityInfo("✦✦", Color(0xFFA78BFA), "Special Illustration", 8)
        "ace spec rare" -> RarityInfo("◈", Color(0xFF38BDF8), "ACE SPEC", 9)
        else -> RarityInfo("●", Color(0xFF6B7280), rarity ?: "Altro", 10)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetDetailScreen(
    setId: String,
    setName: String,
    onBack: () -> Unit,
    viewModel: SetDetailViewModel = viewModel()
) {
    val state = viewModel.uiState
    var selectedCard by remember { mutableStateOf<TcgCard?>(null) }

    LaunchedEffect(setId) {
        viewModel.loadSet(setId)
    }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.successMessage, state.errorMessage) {
        val msg = state.successMessage ?: state.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    // Bottom sheet per dettaglio carta
    if (selectedCard != null) {
        CardDetailBottomSheet(
            card = selectedCard!!,
            isOwned = selectedCard!!.id in state.ownedCardIds,
            isLoading = state.isAddingCard == selectedCard!!.id,
            onToggleOwned = { viewModel.toggleCard(selectedCard!!) },
            onDismiss = { selectedCard = null }
        )
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
                    Column {
                        Text(
                            text = setName,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!state.isLoading) {
                            Text(
                                text = "Data di uscita: ${state.set?.releaseDate ?: ""}",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Indietro", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = BlueCard)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Caricamento carte...", color = TextGray, fontSize = 14.sp)
                    }
                }
            } else {
                // Calcola statistiche per rarità
                val rarityCounts = state.cards.groupBy { getRarityInfo(it.rarity) }
                    .mapValues { entry ->
                        val total = entry.value.size
                        val owned = entry.value.count { it.id in state.ownedCardIds }
                        Pair(owned, total)
                    }
                    .toSortedMap(compareBy { it.sortOrder })

                // Ordina carte per numero
                val sortedCards = state.cards.sortedBy {
                    it.number.toIntOrNull() ?: Int.MAX_VALUE
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ── Header: Set info card ──
                    item(span = { GridItemSpan(3) }) {
                        SetInfoHeader(
                            setName = state.set?.name ?: setName,
                            logoUrl = state.set?.images?.logo ?: "",
                            ownedCount = state.ownedCount,
                            totalCount = state.totalCount,
                            completionPercent = state.completionPercent,
                            rarityCounts = rarityCounts
                        )
                    }

                    // ── View mode tabs ──
                    item(span = { GridItemSpan(3) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkCard),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf("Carte" to "grid", "Lista" to "list", "Griglia" to "large").forEach { (label, mode) ->
                                Text(
                                    text = label,
                                    color = if (state.viewMode == mode) TextWhite else TextMuted,
                                    fontWeight = if (state.viewMode == mode) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.setViewMode(mode) }
                                        .background(
                                            if (state.viewMode == mode) BlueCard.copy(alpha = 0.3f)
                                            else Color.Transparent
                                        )
                                        .padding(vertical = 12.dp)
                                )
                            }
                        }
                    }

                    // ── Cards grid ──
                    when (state.viewMode) {
                        "grid" -> {
                            items(
                                items = sortedCards,
                                key = { it.id },
                                span = { GridItemSpan(1) }
                            ) { card ->
                                TcgCardCompactItem(
                                    card = card,
                                    isOwned = card.id in state.ownedCardIds,
                                    onClick = { selectedCard = card }
                                )
                            }
                        }
                        "list" -> {
                            items(
                                items = sortedCards,
                                key = { it.id },
                                span = { GridItemSpan(3) }
                            ) { card ->
                                TcgCardListRow(
                                    card = card,
                                    isOwned = card.id in state.ownedCardIds,
                                    onClick = { selectedCard = card }
                                )
                            }
                        }
                        "large" -> {
                            items(
                                items = sortedCards,
                                key = { it.id },
                                span = { GridItemSpan(1) }
                            ) { card ->
                                TcgCardLargeItem(
                                    card = card,
                                    isOwned = card.id in state.ownedCardIds,
                                    onClick = { selectedCard = card }
                                )
                            }
                        }
                    }

                    // Bottom spacer
                    item(span = { GridItemSpan(3) }) {
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

// ── Set Info Header con breakdown rarità ──
@Composable
fun SetInfoHeader(
    setName: String,
    logoUrl: String,
    ownedCount: Int,
    totalCount: Int,
    completionPercent: Int,
    rarityCounts: Map<RarityInfo, Pair<Int, Int>>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(DarkCard, DarkSurface)
                )
            )
            .padding(16.dp)
    ) {
        // Logo + Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo
            if (logoUrl.isNotBlank()) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = setName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Stats a destra
            Column(horizontalAlignment = Alignment.End) {
                // Valore totale stimato (placeholder)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, GreenCard.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${completionPercent}%",
                        color = GreenCard,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "$ownedCount/$totalCount",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress bar con gradiente
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF1A1A30))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(completionPercent / 100f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF3B82F6),
                                Color(0xFF8B5CF6),
                                Color(0xFFEC4899)
                            )
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Breakdown per rarità ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            rarityCounts.forEach { (rarityInfo, counts) ->
                val (owned, total) = counts
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Text(
                        text = rarityInfo.emoji,
                        color = rarityInfo.color,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$owned/$total",
                        color = if (owned == total && total > 0) GreenCard else TextWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ── Card item compatto (vista Carte) ──
@Composable
fun TcgCardCompactItem(
    card: TcgCard,
    isOwned: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isOwned) Modifier.border(2.dp, GreenCard.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = card.images.small,
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Scurisci se non posseduta
        if (!isOwned) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )
        }

        // Badge owned
        if (isOwned) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(GreenCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // Prezzo + nome in basso
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
                .padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            val price = card.cardmarket?.prices?.averageSellPrice
                ?: card.tcgplayer?.prices?.values?.firstOrNull()?.market

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
                if (price != null && price > 0) {
                    Text(
                        text = "${"%.2f".format(price)} €",
                        color = GreenCard,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Card item grande (vista Griglia) ──
@Composable
fun TcgCardLargeItem(
    card: TcgCard,
    isOwned: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isOwned) Modifier.border(2.5.dp, GreenCard, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = card.images.large,
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (!isOwned) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        }

        if (isOwned) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(GreenCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Card item lista ──
@Composable
fun TcgCardListRow(
    card: TcgCard,
    isOwned: Boolean,
    onClick: () -> Unit
) {
    val rarityInfo = getRarityInfo(card.rarity)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isOwned) DarkCard else DarkCard.copy(alpha = 0.5f))
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
        ) {
            AsyncImage(
                model = card.images.small,
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (!isOwned) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            }
        }

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rarityInfo.emoji,
                    color = rarityInfo.color,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = card.name,
                    color = if (isOwned) TextWhite else TextMuted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "#${card.number} · ${card.rarity ?: ""}",
                color = TextMuted,
                fontSize = 11.sp
            )
            if (card.hp != null) {
                Text(text = "${card.hp} HP · ${card.types?.joinToString(", ") ?: ""}", color = TextMuted, fontSize = 11.sp)
            }
        }

        // Prezzo
        val price = card.cardmarket?.prices?.averageSellPrice
            ?: card.tcgplayer?.prices?.values?.firstOrNull()?.market
        if (price != null && price > 0) {
            Text(
                text = "${"%.2f".format(price)} €",
                color = GreenCard,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Check
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isOwned) GreenCard else Color.Transparent)
                .then(
                    if (!isOwned) Modifier.border(1.5.dp, TextMuted.copy(alpha = 0.3f), CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isOwned) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
