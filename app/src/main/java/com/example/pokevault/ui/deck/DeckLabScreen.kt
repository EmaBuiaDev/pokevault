package com.example.pokevault.ui.deck

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
                    items(viewModel.decks, key = { it.id }) { deck ->
                        var isExpanded by remember { mutableStateOf(false) }
                        
                        DeckItem(
                            deck = deck,
                            isExpanded = isExpanded,
                            onExpandClick = { isExpanded = !isExpanded },
                            onDelete = { viewModel.deleteDeck(deck.id) },
                            onDuplicate = { viewModel.duplicateDeck(deck) },
                            onEdit = { 
                                viewModel.prepareEdit(deck)
                                showSheet = true
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
                onDismissRequest = { 
                    showSheet = false
                    viewModel.resetNewDeckState()
                },
                sheetState = sheetState,
                containerColor = DarkSurface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) }
            ) {
                NewDeckBottomSheetContent(
                    viewModel = viewModel,
                    isEditing = viewModel.editingDeckId != null,
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
    val deckCards = remember(deck.cards, allOwnedCards) {
        allOwnedCards.filter { it.id in deck.cards }
    }
    val pokemonCount = remember(deckCards) { deckCards.count { it.supertype.equals("Pokémon", ignoreCase = true) } }
    val trainerCount = remember(deckCards) { deckCards.count { it.supertype.equals("Trainer", ignoreCase = true) } }
    val energyCount = remember(deckCards) { deckCards.count { it.supertype.equals("Energy", ignoreCase = true) } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpandClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column {
            // Hero Section con Mascotte
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                if (deck.coverImageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(deck.coverImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(DarkCard, BlueCard.copy(alpha = 0.2f)))
                    ))
                }

                // Overlay Gradiente Moderno per leggibilità
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.1f),
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Black.copy(alpha = 0.95f)
                                )
                            )
                        )
                )

                // Badge Tipi (Top Right)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    deck.mainTypes.take(3).forEach { type ->
                        TypeBadge(type)
                    }
                }

                // Info Deck (Bottom Left)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Text(
                        text = deck.name,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$pokemonCount Pokémon • $trainerCount Trainer • $energyCount Energy",
                        color = BlueCard,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Carte nel deck (${deckCards.size})",
                        color = LavenderCard,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(deckCards.take(25)) { card ->
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(card.imageUrl)
                                    .crossfade(true)
                                    .size(200, 280)
                                    .build(),
                                contentDescription = card.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .width(65.dp)
                                    .aspectRatio(0.71f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onCardClick(card.id) }
                            )
                        }
                        if (deckCards.size > 25) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .size(65.dp, 92.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkBackground),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "+${deckCards.size - 25}", color = TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    
                    Divider(color = Color.White.copy(alpha = 0.05f), thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = BlueCard, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Modifica", color = BlueCard, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
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
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 16.sp)
        }
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
    isEditing: Boolean = false,
    onSave: () -> Unit
) {
    var showCoverPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
    ) {
        // Header Area - Pulita senza sfondi invasivi
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DarkCard)
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditing) "Modifica Deck" else "Nuovo Deck",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Surface(
                        color = Color.Black.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${viewModel.selectedCardsIds.size} / 60",
                            color = TextWhite,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Anteprima copertina cliccabile
                    Box(
                        modifier = Modifier
                            .size(45.dp, 63.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkBackground)
                            .border(BorderStroke(1.dp, BlueCard.copy(alpha = 0.5f)), RoundedCornerShape(6.dp))
                            .clickable { if(viewModel.selectedCardsIds.isNotEmpty()) showCoverPicker = !showCoverPicker },
                        contentAlignment = Alignment.Center
                    ) {
                        if (viewModel.coverImageUrl.isNotEmpty()) {
                            AsyncImage(
                                model = viewModel.coverImageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    TextField(
                        value = viewModel.newDeckName,
                        onValueChange = { viewModel.newDeckName = it },
                        placeholder = { Text("Nome deck...", color = TextMuted) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.textFieldColors(
                            containerColor = Color.Transparent,
                            focusedIndicatorColor = BlueCard,
                            cursorColor = BlueCard,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        singleLine = true
                    )
                }
            }
        }

        // Integrated Cover Picker
        if (showCoverPicker && viewModel.selectedCardsIds.isNotEmpty()) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Scegli Copertina", color = LavenderCard, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { showCoverPicker = false }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val selectedCards = viewModel.ownedCards.filter { it.id in viewModel.selectedCardsIds }.distinctBy { it.imageUrl }
                    items(selectedCards) { card ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(card.imageUrl)
                                .size(150, 210)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(50.dp, 70.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(
                                    BorderStroke(if (viewModel.coverImageUrl == card.imageUrl) 2.dp else 0.dp, BlueCard),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { 
                                    viewModel.selectCoverCard(card.imageUrl)
                                    showCoverPicker = false
                                }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grid Collection
        Text(text = "Tua Collezione", color = LavenderCard, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(viewModel.ownedCards, key = { it.id }) { card ->
                CardSelectionItem(
                    card = card,
                    isSelected = viewModel.selectedCardsIds.contains(card.id),
                    onClick = { viewModel.toggleCardSelection(card.id) }
                )
            }
        }

        if (viewModel.validationError != null) {
            Text(text = viewModel.validationError!!, color = RedCard, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        }

        if (viewModel.selectedCardsIds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            AnalysisSection(viewModel)
        }

        Spacer(modifier = Modifier.height(16.dp))

        val canSave = viewModel.newDeckName.isNotBlank() && viewModel.selectedCardsIds.isNotEmpty()
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (canSave) BlueCard else DarkCard),
            enabled = canSave && !viewModel.isSaving
        ) {
            if (viewModel.isSaving) {
                CircularProgressIndicator(color = TextWhite, modifier = Modifier.size(24.dp))
            } else {
                Text(text = if (isEditing) "Salva Modifiche" else "Salva Deck", fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
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
                BorderStroke(if (isSelected) 3.dp else 0.dp, BlueCard),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(card.imageUrl)
                .size(250, 350)
                .build(),
            contentDescription = card.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        if (isSelected) {
            Box(
                modifier = Modifier.fillMaxSize().background(BlueCard.copy(alpha = 0.2f)),
                contentAlignment = Alignment.TopEnd
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BlueCard, modifier = Modifier.padding(6.dp).size(22.dp))
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
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Analytics, contentDescription = null, tint = LavenderCard, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Analisi Lab", color = LavenderCard, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AnalysisInfoItem("Media HP", analysis.averageHp.toInt().toString())
            AnalysisInfoItem("Tipi", analysis.typesCount.size.toString())
            
            val p = analysis.supertypesCount["Pokémon"] ?: 0
            val t = analysis.supertypesCount["Trainer"] ?: 0
            val e = analysis.supertypesCount["Energy"] ?: 0
            
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "Ripartizione", color = TextMuted, fontSize = 11.sp)
                Text(text = "$p Pokémon / $t Trainer / $e Energy", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AnalysisInfoItem(label: String, value: String) {
    Column {
        Text(text = label, color = TextMuted, fontSize = 11.sp)
        Text(text = value, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
