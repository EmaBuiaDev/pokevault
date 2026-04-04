package com.example.pokevault.ui.deck

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    var selectedDeck by remember { mutableStateOf<Deck?>(null) }

    if (selectedDeck != null) {
        BackHandler { selectedDeck = null }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            if (selectedDeck == null) {
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = TextWhite)
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
            }
        },
        floatingActionButton = {
            if (selectedDeck == null) {
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
        }
    ) { padding ->
        Box(modifier = Modifier.padding(if (selectedDeck == null) padding else PaddingValues(0.dp))) {
            if (selectedDeck != null) {
                DeckDetailView(
                    deck = selectedDeck!!,
                    allOwnedCards = viewModel.ownedCards,
                    onBack = { selectedDeck = null },
                    onCardClick = onCardClick,
                    onEdit = {
                        viewModel.prepareEdit(selectedDeck!!)
                        showSheet = true
                    },
                    onDelete = {
                        viewModel.deleteDeck(selectedDeck!!.id)
                        selectedDeck = null
                    },
                    onDuplicate = {
                        viewModel.duplicateDeck(selectedDeck!!)
                        selectedDeck = null
                    }
                )
            } else if (viewModel.decks.isEmpty()) {
                EmptyDecksPlaceholder()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(viewModel.decks, key = { it.id }) { deck ->
                        DeckItem(
                            deck = deck,
                            onClick = { selectedDeck = deck },
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
                            selectedDeck = null
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
    onClick: () -> Unit,
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
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                deck.mainTypes.take(2).forEach { type ->
                    TypeBadge(type, small = true)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = deck.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$pokemonCount Pokémon • $trainerCount Trainer • $energyCount Energy",
                    color = BlueCard,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DeckDetailView(
    deck: Deck,
    allOwnedCards: List<PokemonCard>,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit
) {
    val deckCards = remember(deck.cards, allOwnedCards) {
        allOwnedCards.filter { it.id in deck.cards }
    }
    
    val cardsByCategory = remember(deckCards) {
        listOf("Pokémon", "Trainer", "Energy").map { cat ->
            cat to deckCards.filter { it.supertype.equals(cat, ignoreCase = true) }
        }.filter { it.second.isNotEmpty() }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
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
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent,
                                DarkBackground
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = BlueCard, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GreenCard, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = RedCard, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = deck.name,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Totale: ${deckCards.size} carte",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(cardsByCategory) { (category, cards) ->
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = category.uppercase(),
                            color = LavenderCard,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${cards.size}",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    cards.chunked(4).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { card ->
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(card.imageUrl)
                                        .crossfade(true)
                                        .size(250, 350)
                                        .build(),
                                    contentDescription = card.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.71f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onCardClick(card.id) }
                                )
                            }
                            repeat(4 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun TypeBadge(type: String, small: Boolean = false) {
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
            modifier = Modifier.size(if (small) 24.dp else 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = if (small) 12.sp else 14.sp)
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
                .size(80.dp)
                .clip(CircleShape)
                .background(DarkCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Science, contentDescription = null, tint = LavenderCard, modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Ancora nessun deck",
            color = TextWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Inizia a sperimentare nel laboratorio e crea la tua squadra perfetta.",
            color = TextMuted,
            textAlign = TextAlign.Center,
            fontSize = 13.sp
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkCard)
                .padding(14.dp)
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Surface(
                        color = Color.Black.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${viewModel.selectedCardsIds.size} / 60",
                            color = TextWhite,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp, 56.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkBackground)
                            .border(BorderStroke(1.dp, BlueCard.copy(alpha = 0.5f)), RoundedCornerShape(4.dp))
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
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    TextField(
                        value = viewModel.newDeckName,
                        onValueChange = { viewModel.newDeckName = it },
                        placeholder = { Text("Nome deck...", color = TextMuted, fontSize = 14.sp) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = BlueCard,
                            unfocusedIndicatorColor = TextMuted.copy(alpha = 0.5f),
                            cursorColor = BlueCard,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        singleLine = true
                    )
                }
            }
        }

        if (showCoverPicker && viewModel.selectedCardsIds.isNotEmpty()) {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Scegli Copertina", color = LavenderCard, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { showCoverPicker = false }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val selectedCards = viewModel.ownedCards.filter { it.id in viewModel.selectedCardsIds }.distinctBy { it.imageUrl }
                    items(selectedCards) { card ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(card.imageUrl)
                                .size(120, 168)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(44.dp, 62.dp)
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

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Tua Collezione", color = LavenderCard, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
            Text(text = viewModel.validationError!!, color = RedCard, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
        }

        if (viewModel.selectedCardsIds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            AnalysisSection(viewModel)
        }

        Spacer(modifier = Modifier.height(12.dp))

        val canSave = viewModel.newDeckName.isNotBlank() && viewModel.selectedCardsIds.isNotEmpty()
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (canSave) BlueCard else DarkCard),
            enabled = canSave && !viewModel.isSaving
        ) {
            if (viewModel.isSaving) {
                CircularProgressIndicator(color = TextWhite, modifier = Modifier.size(20.dp))
            } else {
                Text(text = if (isEditing) "Salva Modifiche" else "Salva Deck", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
            .clip(RoundedCornerShape(8.dp))
            .border(
                BorderStroke(if (isSelected) 2.dp else 0.dp, BlueCard),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(card.imageUrl)
                .size(200, 280)
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
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BlueCard, modifier = Modifier.padding(4.dp).size(18.dp))
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
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Analytics, contentDescription = null, tint = LavenderCard, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Analisi Lab", color = LavenderCard, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AnalysisInfoItem("Media HP", analysis.averageHp.toInt().toString())
            AnalysisInfoItem("Tipi", analysis.typesCount.size.toString())
            
            val p = analysis.supertypesCount["Pokémon"] ?: 0
            val t = analysis.supertypesCount["Trainer"] ?: 0
            val e = analysis.supertypesCount["Energy"] ?: 0
            
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "Ripartizione", color = TextMuted, fontSize = 10.sp)
                Text(text = "$p Pokémon / $t Trainer / $e Energy", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AnalysisInfoItem(label: String, value: String) {
    Column {
        Text(text = label, color = TextMuted, fontSize = 10.sp)
        Text(text = value, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
