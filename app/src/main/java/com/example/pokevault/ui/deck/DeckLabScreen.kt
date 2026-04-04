package com.example.pokevault.ui.deck

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.pokevault.data.model.Deck
import com.example.pokevault.data.model.PokemonCard
import com.example.pokevault.ui.theme.*
import com.example.pokevault.viewmodel.DeckLabViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckLabScreen(
    onBack: () -> Unit,
    onCardClick: (String) -> Unit = {},
    viewModel: DeckLabViewModel = viewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .statusBarsPadding()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(DarkCard)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro", tint = TextWhite)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Deck Lab",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Crea mini-deck con le carte che possiedi",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { 
                    viewModel.resetNewDeckState()
                    showSheet = true 
                },
                containerColor = BlueCard,
                contentColor = TextWhite,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Crea un nuovo deck") }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (viewModel.decks.isEmpty()) {
                EmptyDecksPlaceholder()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(viewModel.decks) { deck ->
                        var isExpanded by remember { mutableStateOf(false) }
                        
                        DeckItem(
                            deck = deck,
                            isExpanded = isExpanded,
                            onExpandClick = { isExpanded = !isExpanded },
                            onDelete = { viewModel.deleteDeck(deck.id) },
                            onDuplicate = { viewModel.duplicateDeck(deck) },
                            onEdit = { 
                                // Setup VM for editing and show sheet
                            },
                            onCardClick = onCardClick,
                            allOwnedCards = viewModel.ownedCards
                        )
                    }
                }
            }
        }

        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = sheetState,
                containerColor = DarkSurface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) }
            ) {
                NewDeckBottomSheetContent(
                    viewModel = viewModel,
                    onSave = {
                        viewModel.saveDeck {
                            showSheet = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DeckItem(
    deck: Deck,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    onCardClick: (String) -> Unit,
    allOwnedCards: List<PokemonCard>
) {
    val deckCards = allOwnedCards.filter { it.id in deck.cards }
    val pokemonCount = deckCards.count { it.supertype.equals("Pokémon", ignoreCase = true) }
    val trainerCount = deckCards.count { it.supertype.equals("Trainer", ignoreCase = true) }
    val energyCount = deckCards.count { it.supertype.equals("Energy", ignoreCase = true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpandClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deck.name,
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Sezione Tipologie richieste
                    Text(
                        text = "$pokemonCount Pokémon, $trainerCount Allenatore, $energyCount Energia",
                        color = BlueCard,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    Text(
                        text = "${deck.totalCards} carte totali",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    deck.mainTypes.forEach { type ->
                        TypeBadge(type)
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Carte nel deck",
                        color = LavenderCard,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(deckCards) { card ->
                            AsyncImage(
                                model = card.imageUrl,
                                contentDescription = card.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(60.dp)
                                    .aspectRatio(0.71f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onCardClick(card.id) }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifica", tint = BlueCard, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onDuplicate) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplica", tint = GreenCard, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Elimina", tint = RedCard, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TypeBadge(type: String) {
    val emoji = when (type.lowercase()) {
        "fuoco", "fire" -> "🔥"
        "acqua", "water" -> "💧"
        "elettro", "lightning" -> "⚡"
        "psico", "psychic" -> "🔮"
        "erba", "grass" -> "🌿"
        "lotta", "fighting" -> "👊"
        "oscurità", "darkness" -> "🌙"
        "metallo", "metal" -> "⚙️"
        "folletto", "fairy" -> "✨"
        "drago", "dragon" -> "🐲"
        "normale", "colorless" -> "⚪"
        else -> "🔘"
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 14.sp)
    }
}

@Composable
fun EmptyDecksPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(DarkCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Science, contentDescription = null, tint = LavenderCard, modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Ancora nessun deck",
            color = TextWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Inizia a sperimentare nel laboratorio e crea la tua squadra perfetta.",
            color = TextMuted,
            textAlign = TextAlign.Center,
            fontSize = 14.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDeckBottomSheetContent(
    viewModel: DeckLabViewModel,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "Nuovo Deck",
            color = TextWhite,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 1) Nome del deck
        OutlinedTextField(
            value = viewModel.newDeckName,
            onValueChange = { viewModel.newDeckName = it },
            placeholder = { Text("Nome del deck", color = TextMuted) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = BlueCard,
                unfocusedBorderColor = DarkCard,
                cursorColor = BlueCard,
                containerColor = DarkCard
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 2) Selezione carte
        Text(
            text = "Seleziona le tue carte (${viewModel.selectedCardsIds.size})",
            color = TextWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.ownedCards) { card ->
                val isSelected = viewModel.selectedCardsIds.contains(card.id)
                CardSelectionItem(
                    card = card,
                    isSelected = isSelected,
                    onClick = { viewModel.toggleCardSelection(card.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3) Analisi intelligente
        if (viewModel.selectedCardsIds.isNotEmpty()) {
            AnalysisSection(viewModel)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BlueCard),
            enabled = viewModel.newDeckName.isNotBlank() && viewModel.selectedCardsIds.isNotEmpty() && !viewModel.isSaving
        ) {
            if (viewModel.isSaving) {
                CircularProgressIndicator(color = TextWhite, modifier = Modifier.size(24.dp))
            } else {
                Text("Salva Deck", fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun CardSelectionItem(
    card: PokemonCard,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.71f)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) BlueCard else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = card.imageUrl,
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BlueCard.copy(alpha = 0.2f)),
                contentAlignment = Alignment.TopEnd
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = BlueCard,
                    modifier = Modifier.padding(4.dp).size(20.dp)
                )
            }
        }
    }
}

@Composable
fun AnalysisSection(viewModel: DeckLabViewModel) {
    val analysis = viewModel.currentAnalysis
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .padding(16.dp)
    ) {
        Text(
            text = "Analisi Lab",
            color = LavenderCard,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AnalysisInfoItem("Media HP", analysis.averageHp.toInt().toString())
            AnalysisInfoItem("Tipi", analysis.typesCount.size.toString())
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Nuova visualizzazione suddivisione in analisi
        val p = analysis.supertypesCount["Pokémon"] ?: 0
        val t = analysis.supertypesCount["Trainer"] ?: 0
        val e = analysis.supertypesCount["Energy"] ?: 0
        Text(
            text = "$p Pokémon, $t Allenatore, $e Energia",
            color = TextWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))
        
        if (analysis.recommendedEnergy.isNotEmpty()) {
            Text(
                text = "Energia consigliata: ${analysis.recommendedEnergy.joinToString(", ")}",
                color = TextGray,
                fontSize = 12.sp
            )
        }

        if (analysis.synergies.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = YellowCard, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = analysis.synergies.first(),
                    color = YellowCard,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AnalysisInfoItem(label: String, value: String) {
    Column {
        Text(text = label, color = TextMuted, fontSize = 11.sp)
        Text(text = value, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
