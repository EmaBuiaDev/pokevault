package com.example.pokevault.ui.deck

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.alpha
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
import com.example.pokevault.data.billing.PremiumManager
import com.example.pokevault.data.model.Deck
import com.example.pokevault.data.model.PokemonCard
import com.example.pokevault.ui.premium.PremiumRequiredDialog
import com.example.pokevault.ui.theme.*
import com.example.pokevault.util.AppLocale
import com.example.pokevault.viewmodel.DeckLabViewModel
import com.example.pokevault.viewmodel.MetaDeckViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckLabScreen(
    onBack: () -> Unit,
    onCardClick: (String) -> Unit = {},
    onNavigateToPremium: () -> Unit = {},
    viewModel: DeckLabViewModel = viewModel(),
    metaDeckViewModel: MetaDeckViewModel = viewModel()
) {
    val premiumManager = remember { PremiumManager.getInstance() }
    val isPremium by premiumManager.isPremium.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showPremiumDeckDialog by remember { mutableStateOf(false) }
    var showPremiumMetaDeckDialog by remember { mutableStateOf(false) }
    var selectedDeck by remember { mutableStateOf<Deck?>(null) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val deckLabTabs = listOf(AppLocale.deckLabMyDecks, AppLocale.deckLabMetaDeck, AppLocale.deckLabWinTournament)

    if (selectedDeck != null) {
        BackHandler { selectedDeck = null }
    }

    // Se siamo nella vista dettaglio di un Win Tournament deck, mostriamola a tutto schermo
    if (selectedTabIndex == 2 && metaDeckViewModel.selectedDeck != null) {
        MetaDeckSection(
            viewModel = metaDeckViewModel,
            onImportDeck = { metaDeck ->
                if (premiumManager.canCreateDeck(viewModel.decks.size)) {
                    val result = viewModel.importFromMetaDeck(metaDeck)
                    metaDeckViewModel.selectDeck(null)
                    if (result.matched > 0) {
                        showSheet = true
                    }
                } else {
                    metaDeckViewModel.selectDeck(null)
                    showPremiumDeckDialog = true
                }
            }
        )
        return
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
                                text = when (selectedTabIndex) {
                                    0 -> AppLocale.deckLabMyDecksSubtitle
                                    1 -> AppLocale.deckLabMetaDeckSubtitle
                                    else -> AppLocale.deckLabWinTournamentSubtitle
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tabs: I Miei Deck | Meta Deck
                    SecondaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = BlueCard,
                        divider = {}
                    ) {
                        deckLabTabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Text(
                                        text = title,
                                        fontSize = 13.sp,
                                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                selectedContentColor = BlueCard,
                                unselectedContentColor = TextMuted
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedDeck == null && selectedTabIndex == 0) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Bottone Importa
                    SmallFloatingActionButton(
                        onClick = {
                            if (premiumManager.canCreateDeck(viewModel.decks.size)) {
                                showImportDialog = true
                            } else {
                                showPremiumDeckDialog = true
                            }
                        },
                        containerColor = PurpleCard,
                        contentColor = TextWhite,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Importa deck")
                    }
                    // Bottone Crea
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (premiumManager.canCreateDeck(viewModel.decks.size)) {
                                viewModel.resetNewDeckState()
                                showSheet = true
                            } else {
                                showPremiumDeckDialog = true
                            }
                        },
                        containerColor = BlueCard,
                        contentColor = TextWhite,
                        shape = RoundedCornerShape(16.dp),
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("Crea un nuovo deck") }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(if (selectedDeck == null) padding else PaddingValues(0.dp))) {
            when {
                selectedDeck != null -> {
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
                            if (premiumManager.canCreateDeck(viewModel.decks.size)) {
                                viewModel.duplicateDeck(selectedDeck!!)
                                selectedDeck = null
                            } else {
                                showPremiumDeckDialog = true
                            }
                        }
                    )
                }

                selectedTabIndex == 0 -> {
                    // Tab: I Miei Deck
                    if (viewModel.decks.isEmpty()) {
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

                selectedTabIndex == 1 -> {
                    // Tab: Meta Deck (archetipi classifica)
                    MetaArchetypeSection(
                        viewModel = metaDeckViewModel,
                        onImportDeck = { metaDeck ->
                            if (premiumManager.canCreateDeck(viewModel.decks.size)) {
                                val result = viewModel.importFromMetaDeck(metaDeck)
                                metaDeckViewModel.selectDeck(null)
                                if (result.matched > 0) {
                                    showSheet = true
                                }
                            } else {
                                metaDeckViewModel.selectDeck(null)
                                showPremiumDeckDialog = true
                            }
                        }
                    )
                }

                selectedTabIndex == 2 -> {
                    // Tab: Win Tournament (ex Meta Deck)
                    MetaDeckSection(
                        viewModel = metaDeckViewModel,
                        onImportDeck = { metaDeck ->
                            if (premiumManager.canCreateDeck(viewModel.decks.size)) {
                                val result = viewModel.importFromMetaDeck(metaDeck)
                                metaDeckViewModel.selectDeck(null)
                                if (result.matched > 0) {
                                    showSheet = true
                                }
                            } else {
                                metaDeckViewModel.selectDeck(null)
                                showPremiumDeckDialog = true
                            }
                        },
                        onPremiumRequired = { showPremiumMetaDeckDialog = true }
                    )
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

        // Dialog Importa da Testo
        if (showImportDialog) {
            DeckImportDialog(
                onDismiss = { showImportDialog = false },
                onImport = { text ->
                    val result = viewModel.importFromText(text)
                    showImportDialog = false
                    if (result.matched > 0) {
                        showSheet = true
                    }
                }
            )
        }

        // Risultato import
        val importResult = viewModel.importResult
        if (importResult != null) {
            ImportResultDialog(
                result = importResult,
                onDismiss = { viewModel.clearImportResult() }
            )
        }

        // Premium gate dialogs
        if (showPremiumDeckDialog) {
            PremiumRequiredDialog(
                title = AppLocale.premiumDeckLimitTitle,
                message = AppLocale.premiumDeckLimitMessage,
                onDismiss = { showPremiumDeckDialog = false },
                onUpgrade = {
                    showPremiumDeckDialog = false
                    onNavigateToPremium()
                }
            )
        }

        if (showPremiumMetaDeckDialog) {
            PremiumRequiredDialog(
                title = AppLocale.premiumMetaDeckLimitTitle,
                message = AppLocale.premiumMetaDeckLimitMessage,
                onDismiss = { showPremiumMetaDeckDialog = false },
                onUpgrade = {
                    showPremiumMetaDeckDialog = false
                    onNavigateToPremium()
                }
            )
        }
    }
}

@Composable
fun DeckItem(
    deck: Deck,
    onClick: () -> Unit,
    allOwnedCards: List<PokemonCard>
) {
    val cardCounts = remember(deck.cards) { deck.cards.groupingBy { it }.eachCount() }
    val uniqueDeckCards = remember(deck.cards, allOwnedCards) {
        allOwnedCards.filter { it.id in cardCounts.keys }
    }
    
    val pokemonCount = remember(uniqueDeckCards, cardCounts) { 
        uniqueDeckCards.filter { it.classify() == "Pokémon" }.sumOf { cardCounts[it.id] ?: 0 } 
    }
    val trainerCount = remember(uniqueDeckCards, cardCounts) { 
        uniqueDeckCards.filter { it.classify() == "Trainer" }.sumOf { cardCounts[it.id] ?: 0 } 
    }
    val energyCount = remember(uniqueDeckCards, cardCounts) { 
        uniqueDeckCards.filter { it.classify() == "Energy" }.sumOf { cardCounts[it.id] ?: 0 } 
    }

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
    fun getCardKey(card: PokemonCard): String =
        card.apiCardId.ifEmpty { "${card.name}-${card.set}-${card.cardNumber}-${card.variant}" }

    val idToCard = remember(allOwnedCards) { allOwnedCards.associateBy { it.id } }
    
    val groupedCards = remember(deck.cards, idToCard) {
        deck.cards.mapNotNull { idToCard[it] }
            .groupBy { getCardKey(it) }
            .map { (_, instances) -> instances.first() to instances.size }
    }
    
    val cardsByCategory = remember(groupedCards) {
        listOf("Pokémon", "Trainer", "Energy").map { cat ->
            val filtered = groupedCards.filter { (card, _) ->
                card.classify() == cat
            }
            cat to filtered
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
                    text = "Totale: ${deck.cards.size} carte",
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
            items(cardsByCategory) { (category, cardsList) ->
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val displayTitle = when(category) {
                            "Energy" -> "ENERGIA"
                            else -> category.uppercase()
                        }
                        Text(
                            text = displayTitle,
                            color = LavenderCard,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${cardsList.sumOf { it.second }}",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    cardsList.chunked(4).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { (card, quantity) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.71f)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(card.imageUrl)
                                            .crossfade(true)
                                            .size(250, 350)
                                            .build(),
                                        contentDescription = card.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onCardClick(card.id) }
                                    )
                                    if (quantity > 1) {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.7f),
                                            shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        ) {
                                            Text(
                                                text = "x$quantity",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
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
    // ... (rest of the code remains the same)
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
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Pokémon", "Trainer", "Energia")

    val filteredCards = remember(selectedTabIndex, viewModel.ownedCards) {
        viewModel.ownedCards
            .filter { card ->
                val category = card.classify()
                when (selectedTabIndex) {
                    0 -> category == "Pokémon"
                    1 -> category == "Trainer"
                    2 -> category == "Energy"
                    else -> true
                }
            }
            .distinctBy { viewModel.getCardKey(it) }
    }

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
        
        SecondaryTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = BlueCard,
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { 
                        Text(
                            text = title, 
                            fontSize = 12.sp, 
                            fontWeight = if(selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal 
                        ) 
                    },
                    selectedContentColor = BlueCard,
                    unselectedContentColor = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredCards, key = { viewModel.getCardKey(it) }) { card ->
                val inDeckCount = viewModel.getQuantityInDeck(card)
                val totalOwned = viewModel.getTotalOwnedQuantity(card)
                CardSelectionItem(
                    card = card,
                    inDeckCount = inDeckCount,
                    totalOwned = totalOwned,
                    isEditable = true,
                    onAdd = { viewModel.addCardToDeck(card) },
                    onRemove = { viewModel.removeCardFromDeck(card) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardSelectionItem(
    card: PokemonCard,
    inDeckCount: Int,
    totalOwned: Int,
    isEditable: Boolean = false,
    onAdd: () -> Unit = {},
    onRemove: () -> Unit = {}
) {
    val canAddMore = totalOwned > inDeckCount

    Box(
        modifier = Modifier
            .aspectRatio(0.71f)
            .clip(RoundedCornerShape(8.dp))
            .border(
                BorderStroke(
                    if (inDeckCount > 0 && isEditable) 2.dp else 1.dp, 
                    if (inDeckCount > 0 && isEditable) BlueCard else Color.White.copy(alpha = 0.1f)
                ),
                RoundedCornerShape(8.dp)
            )
            .combinedClickable(
                enabled = isEditable,
                onClick = onAdd,
                onLongClick = { if(inDeckCount > 0) onRemove() }
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(card.imageUrl)
                .size(200, 280)
                .build(),
            contentDescription = card.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isEditable && !canAddMore && inDeckCount == 0) 0.5f else 1f)
        )
        
        if (isEditable && !canAddMore) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
        }

        if (inDeckCount > 0 && isEditable) {
            Surface(
                color = BlueCard,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp),
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$inDeckCount",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        if (totalOwned > 1 && inDeckCount < totalOwned) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(bottomStart = 6.dp),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = "x$totalOwned",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
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
            val t = (analysis.supertypesCount["Trainer"] ?: 0)
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

// ══════════════════════════════════════
// IMPORT DIALOGS
// ══════════════════════════════════════

@Composable
fun DeckImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var decklistText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FileDownload, contentDescription = null, tint = PurpleCard, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Importa Deck", color = TextWhite, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "Incolla una decklist in formato PTCG standard:",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Es: 4 Charizard ex SVI 125",
                    color = TextMuted.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = decklistText,
                    onValueChange = { decklistText = it },
                    placeholder = {
                        Text(
                            "Pokémon: 12\n4 Charizard ex SVI 125\n2 Charmander SVI 10\n...",
                            color = TextMuted.copy(alpha = 0.4f),
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkCard,
                        unfocusedContainerColor = DarkCard,
                        focusedIndicatorColor = PurpleCard,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = PurpleCard,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (decklistText.isNotBlank()) onImport(decklistText) },
                enabled = decklistText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PurpleCard),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Importa")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla", color = TextMuted)
            }
        }
    )
}

@Composable
fun ImportResultDialog(
    result: DeckLabViewModel.ImportResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (result.matched > 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (result.matched > 0) GreenCard else YellowCard,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Risultato Import", color = TextWhite, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "${result.matched} carte trovate su ${result.totalRequested} richieste",
                    color = TextWhite,
                    fontSize = 14.sp
                )

                if (result.missingCards.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Carte mancanti (${result.missing}):",
                        color = YellowCard,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    result.missingCards.take(10).forEach { card ->
                        Text(
                            text = "• $card",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    if (result.missingCards.size > 10) {
                        Text(
                            text = "... e altre ${result.missingCards.size - 10}",
                            color = TextMuted.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }

                if (result.matched > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Le carte trovate sono state aggiunte al deck. Puoi modificarlo prima di salvare.",
                        color = GreenCard.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nessuna carta corrisponde alla tua collezione. Aggiungi le carte alla collezione prima di importare.",
                        color = RedCard.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = BlueCard),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("OK")
            }
        }
    )
}
