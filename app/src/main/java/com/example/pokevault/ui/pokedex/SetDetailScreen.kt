package com.example.pokevault.ui.pokedex

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetDetailScreen(
    setId: String,
    setName: String,
    onBack: () -> Unit,
    viewModel: SetDetailViewModel = viewModel()
) {
    val state = viewModel.uiState

    // Carica il set
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
                            fontWeight = FontWeight.SemiBold,
                            color = TextWhite,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!state.isLoading) {
                            Text(
                                text = "${state.ownedCount}/${state.totalCount} carte · ${state.completionPercent}%",
                                color = TextGray,
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

            // ── Barra completamento ──
            if (!state.isLoading && state.totalCount > 0) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkCard)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(state.completionPercent / 100f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        state.completionPercent == 100 -> GreenCard
                                        state.completionPercent > 50 -> BlueCard
                                        state.completionPercent > 25 -> YellowCard
                                        else -> RedCard
                                    }
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // View mode tabs
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkCard)
                    ) {
                        listOf("Carte" to "grid", "Lista" to "list").forEach { (label, mode) ->
                            Text(
                                text = label,
                                color = if (state.viewMode == mode) TextWhite else TextMuted,
                                fontWeight = if (state.viewMode == mode) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clickable { viewModel.setViewMode(mode) }
                                    .background(
                                        if (state.viewMode == mode) BlueCard.copy(alpha = 0.3f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Contenuto ──
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = BlueCard)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Caricamento carte...", color = TextGray, fontSize = 14.sp)
                    }
                }
            } else {
                val columns = if (state.viewMode == "grid") 3 else 1

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = state.cards,
                        key = { it.id }
                    ) { card ->
                        if (state.viewMode == "grid") {
                            TcgCardGridItem(
                                card = card,
                                isOwned = card.id in state.ownedCardIds,
                                isAdding = state.isAddingCard == card.id,
                                onToggle = { viewModel.toggleCard(card) }
                            )
                        } else {
                            TcgCardListItem(
                                card = card,
                                isOwned = card.id in state.ownedCardIds,
                                isAdding = state.isAddingCard == card.id,
                                onToggle = { viewModel.toggleCard(card) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TcgCardGridItem(
    card: TcgCard,
    isOwned: Boolean,
    isAdding: Boolean,
    onToggle: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isOwned) GreenCard else Color.Transparent,
        label = "border"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isOwned) Modifier.border(2.dp, borderColor, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onToggle)
    ) {
        // Immagine carta
        AsyncImage(
            model = card.images.small,
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay se non posseduta
        if (!isOwned) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        }

        // Badge posseduta
        if (isOwned) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(GreenCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Posseduta",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Loading
        if (isAdding) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = BlueCard,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        // Prezzo in basso
        val price = card.cardmarket?.prices?.averageSellPrice
            ?: card.tcgplayer?.prices?.values?.firstOrNull()?.market
        if (price != null && price > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${"%.2f".format(price)} €",
                    color = GreenCard,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun TcgCardListItem(
    card: TcgCard,
    isOwned: Boolean,
    isAdding: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .clickable(onClick = onToggle)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mini immagine
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(70.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = card.images.small,
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (!isOwned) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                )
            }
        }

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.name,
                color = if (isOwned) TextWhite else TextMuted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "#${card.number} · ${card.rarity ?: ""}",
                color = TextMuted,
                fontSize = 12.sp
            )
            if (card.hp != null) {
                Text(text = "${card.hp} HP", color = TextMuted, fontSize = 11.sp)
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
                fontWeight = FontWeight.Medium
            )
        }

        // Toggle posseduta
        if (isAdding) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = BlueCard,
                strokeWidth = 2.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isOwned) GreenCard else DarkSurface)
                    .then(
                        if (!isOwned) Modifier.border(1.dp, TextMuted.copy(alpha = 0.3f), CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isOwned) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Posseduta",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
