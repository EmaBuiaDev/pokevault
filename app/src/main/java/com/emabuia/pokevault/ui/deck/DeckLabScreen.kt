package com.emabuia.pokevault.ui.deck

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.DeckLabViewModel
import com.emabuia.pokevault.viewmodel.MetaDeckViewModel

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
    var showPremiumDeckExportDialog by remember { mutableStateOf(false) }
    var showDeckExportDialog by remember { mutableStateOf(false) }
    var decklistExportText by remember { mutableStateOf("") }
    var decklistExportName by remember { mutableStateOf("") }
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
                    if (result.missingMetaDeckCards.isEmpty() && result.matched > 0) {
                        showSheet = true
                    }
                    // If missing cards exist, ImportResultDialog handles the flow
                } else {
                    metaDeckViewModel.selectDeck(null)
                    showPremiumDeckDialog = true
                }
            }
        )
        return
    }

    // Se siamo nella vista dettaglio di un Meta Deck archetype, mostriamola a tutto schermo
    if (selectedTabIndex == 1 && metaDeckViewModel.selectedDeck != null) {
        MetaDeckDetailView(
            deck = metaDeckViewModel.selectedDeck!!,
            onBack = { metaDeckViewModel.selectDeck(null) },
            onImport = {
                val metaDeck = metaDeckViewModel.selectedDeck!!
                if (premiumManager.canCreateDeck(viewModel.decks.size)) {
                    val result = viewModel.importFromMetaDeck(metaDeck)
                    metaDeckViewModel.selectDeck(null)
                    if (result.missingMetaDeckCards.isEmpty() && result.matched > 0) {
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
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
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
                        },
                        onExport = {
                            val deckToExport = selectedDeck ?: return@DeckDetailView
                            if (premiumManager.canExportDecklist()) {
                                decklistExportText = viewModel.buildPtcgDecklist(deckToExport)
                                decklistExportName = deckToExport.name.ifBlank { "Deck" }
                                showDeckExportDialog = true
                            } else {
                                showPremiumDeckExportDialog = true
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
                                if (result.missingMetaDeckCards.isEmpty() && result.matched > 0) {
                                    showSheet = true
                                }
                            } else {
                                metaDeckViewModel.selectDeck(null)
                                showPremiumDeckDialog = true
                            }
                        },
                        onCardClick = { metaDeck ->
                            metaDeckViewModel.selectDeck(metaDeck)
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
                                if (result.missingMetaDeckCards.isEmpty() && result.matched > 0) {
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
                    if (result.missingMetaDeckCards.isEmpty() && result.matched > 0) {
                        showSheet = true
                    }
                    // If missing cards exist, ImportResultDialog handles the flow
                }
            )
        }

        // Risultato import
        val importResult = viewModel.importResult
        if (importResult != null) {
            ImportResultDialog(
                result = importResult,
                isAddingMissingCards = viewModel.isAddingMissingCards,
                onDismiss = {
                    viewModel.clearImportResult()
                    if (viewModel.selectedCardsIds.isNotEmpty()) {
                        showSheet = true
                    }
                },
                onAddMissingCards = {
                    viewModel.addMissingCardsToCollection(importResult.missingMetaDeckCards) {
                        showSheet = true
                    }
                }
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

        if (showPremiumDeckExportDialog) {
            PremiumRequiredDialog(
                title = AppLocale.premiumDeckExportTitle,
                message = AppLocale.premiumDeckExportMessage,
                onDismiss = { showPremiumDeckExportDialog = false },
                onUpgrade = {
                    showPremiumDeckExportDialog = false
                    onNavigateToPremium()
                }
            )
        }

        if (showDeckExportDialog) {
            DeckExportDialog(
                deckName = decklistExportName,
                decklistText = decklistExportText,
                onDismiss = { showDeckExportDialog = false }
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
    val coverUrls = remember(deck) { deck.displayCoverImageUrls() }
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
            if (coverUrls.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverUrls.first())
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
                if (coverUrls.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        coverUrls.forEach { coverUrl ->
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(coverUrl)
                                    .size(120, 168)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(30.dp, 42.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)), RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
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
    onDuplicate: () -> Unit,
    onExport: () -> Unit
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
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
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
                    IconButton(
                        onClick = onExport,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = StarGold, modifier = Modifier.size(18.dp))
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
fun DeckExportDialog(
    deckName: String,
    decklistText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = AppLocale.deckExportTitle,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = decklistText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 360.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlueCard,
                        unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = DarkCard,
                        unfocusedContainerColor = DarkCard
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("PTCG Decklist", decklistText))
                            Toast.makeText(context, AppLocale.deckExportCopied, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
                    ) {
                        Text(AppLocale.deckExportCopy)
                    }

                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "$deckName - PTCG Decklist")
                                putExtra(Intent.EXTRA_TEXT, decklistText)
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, AppLocale.deckExportShareChooser)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueCard)
                    ) {
                        Text(AppLocale.deckExportShare)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(AppLocale.cancel, color = TextMuted)
            }
        }
    )
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
    val focusManager = LocalFocusManager.current
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
            .imePadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
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
                    val coverPickerEnabled = viewModel.selectedCardsIds.isNotEmpty()
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.25f))
                            .clickable(enabled = coverPickerEnabled) {
                                showCoverPicker = !showCoverPicker
                            }
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            repeat(2) { index ->
                                val url = viewModel.coverImageUrls.getOrNull(index)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp, 68.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(DarkBackground)
                                        .border(
                                            BorderStroke(1.dp, BlueCard.copy(alpha = if (coverPickerEnabled) 0.8f else 0.3f)),
                                            RoundedCornerShape(4.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!url.isNullOrBlank()) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = "Copertina deck ${index + 1}",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Surface(
                                color = BlueCard.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${viewModel.coverImageUrls.size}/2",
                                    color = TextWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Icon(
                                imageVector = if (showCoverPicker) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showCoverPicker) "Chiudi selezione copertine" else "Apri selezione copertine",
                                tint = if (coverPickerEnabled) BlueCard else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = if (coverPickerEnabled) "Tocca per scegliere 2 copertine" else "Aggiungi carte per scegliere le copertine",
                            color = if (coverPickerEnabled) TextWhite else TextMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    TextField(
                        value = viewModel.newDeckName,
                        onValueChange = { viewModel.newDeckName = it },
                        placeholder = { Text("Nome deck...", color = TextMuted, fontSize = 14.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
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
                    Text(text = "Scegli 2 Copertine", color = LavenderCard, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${viewModel.coverImageUrls.size}/2",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    IconButton(onClick = { showCoverPicker = false }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val selectedCards = viewModel.ownedCards.filter { it.id in viewModel.selectedCardsIds }.distinctBy { it.imageUrl }
                    items(selectedCards) { card ->
                        val isSelectedCover = viewModel.coverImageUrls.contains(card.imageUrl)
                        Box(
                            modifier = Modifier
                                .size(48.dp, 68.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(
                                    BorderStroke(if (isSelectedCover) 2.dp else 1.dp, if (isSelectedCover) BlueCard else TextMuted.copy(alpha = 0.4f)),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { viewModel.toggleCoverCard(card.imageUrl) }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(card.imageUrl)
                                    .size(120, 168)
                                    .build(),
                                contentDescription = "Seleziona copertina ${card.name}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (isSelectedCover) {
                                Surface(
                                    color = BlueCard,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Copertina selezionata",
                                        tint = TextWhite,
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                            }
                        }
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
            onClick = {
                focusManager.clearFocus()
                onSave()
            },
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
    isAddingMissingCards: Boolean = false,
    onDismiss: () -> Unit,
    onAddMissingCards: () -> Unit = {}
) {
    val hasMissingCards = result.missingMetaDeckCards.isNotEmpty()

    AlertDialog(
        onDismissRequest = { if (!isAddingMissingCards) onDismiss() },
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (result.matched > 0 && !hasMissingCards) Icons.Default.CheckCircle
                    else if (hasMissingCards) Icons.Default.Warning
                    else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (result.matched > 0 && !hasMissingCards) GreenCard else YellowCard,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppLocale.importResultTitle, color = TextWhite, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "${result.matched} ${AppLocale.importCardsFound} ${result.totalRequested}",
                    color = TextWhite,
                    fontSize = 14.sp
                )

                    if (result.setMismatchWarnings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Carte abbinate con espansione diversa (${result.setMismatchWarnings.size}):",
                            color = OrangeCard,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        result.setMismatchWarnings.take(8).forEach { card ->
                            Text(
                                text = "• $card",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        if (result.setMismatchWarnings.size > 8) {
                            Text(
                                text = "... e altre ${result.setMismatchWarnings.size - 8}",
                                color = TextMuted.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }

                if (result.missingCards.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${AppLocale.importMissingTitle} (${result.missing}):",
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
                            text = "... ${AppLocale.importAndMore} ${result.missingCards.size - 10}",
                            color = TextMuted.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }

                if (hasMissingCards) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = OrangeCard.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.AddCircle,
                                contentDescription = null,
                                tint = OrangeCard,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = AppLocale.importAddMissingMessage,
                                color = TextWhite,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else if (result.matched > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = AppLocale.importMatchedMessage,
                        color = GreenCard.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = AppLocale.importNoMatchMessage,
                        color = RedCard.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }

                if (isAddingMissingCards) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = OrangeCard,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppLocale.importAddingCards,
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (hasMissingCards && !isAddingMissingCards) {
                Button(
                    onClick = onAddMissingCards,
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeCard),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppLocale.importAddMissingConfirm, fontSize = 13.sp)
                }
            } else if (!isAddingMissingCards) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = BlueCard),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("OK")
                }
            }
        },
        dismissButton = {
            if (hasMissingCards && !isAddingMissingCards) {
                TextButton(onClick = onDismiss) {
                    Text(AppLocale.importAddMissingSkip, color = TextMuted)
                }
            }
        }
    )
}
