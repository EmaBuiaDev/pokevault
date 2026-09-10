package com.emabuia.pokevault.ui.deck

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.model.CardClassifier
import com.emabuia.pokevault.data.model.Deck
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
    // isPremium NON viene raccolto qui: tutti i gate di questo schermo stanno
    // dentro lambda di click, quindi leggono _isPremium.value al momento del
    // tocco, che e' gia' il comportamento corretto. Raccoglierlo senza usarlo
    // faceva solo ricomporre l'intero schermo a ogni cambio di stato premium.
    val context = LocalContext.current

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
    val winTournamentDeck = metaDeckViewModel.selectedDeck.takeIf { selectedTabIndex == 2 }
    if (winTournamentDeck != null) {
        BackHandler { metaDeckViewModel.selectDeck(null) }
        MetaDeckDetailView(
            deck = winTournamentDeck,
            onBack = { metaDeckViewModel.selectDeck(null) },
            onImport = {
                if (premiumManager.canCreateDeck(viewModel.decks.size)) {
                    viewModel.importFromMetaDeck(winTournamentDeck)
                    metaDeckViewModel.selectDeck(null)
                    // Il risultato (matched/missing) e il passo successivo sono gestiti
                    // sempre da ImportResultDialog, unico punto di controllo del flusso
                    // post-import -- vedi il suo onDismiss/onAddMissingCards piu' sotto.
                } else {
                    metaDeckViewModel.selectDeck(null)
                    showPremiumDeckDialog = true
                }
            }
        )
        return
    }

    // Se siamo nella vista dettaglio di un Meta Deck archetype, mostriamola a tutto schermo
    val metaArchetypeDeck = metaDeckViewModel.selectedDeck.takeIf { selectedTabIndex == 1 }
    if (metaArchetypeDeck != null) {
        BackHandler { metaDeckViewModel.selectDeck(null) }
        MetaDeckDetailView(
            deck = metaArchetypeDeck,
            onBack = { metaDeckViewModel.selectDeck(null) },
            onImport = {
                if (premiumManager.canCreateDeck(viewModel.decks.size)) {
                    viewModel.importFromMetaDeck(metaArchetypeDeck)
                    metaDeckViewModel.selectDeck(null)
                } else {
                    metaDeckViewModel.selectDeck(null)
                    showPremiumDeckDialog = true
                }
            }
        )
        return
    }

    Scaffold(
        containerColor = AppColors.background,
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
                                .background(AppColors.card)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppLocale.back, tint = AppColors.textPrimary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Deck Lab",
                                style = MaterialTheme.typography.headlineMedium,
                                color = AppColors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (selectedTabIndex) {
                                    0 -> AppLocale.deckLabMyDecksSubtitle
                                    1 -> AppLocale.deckLabMetaDeckSubtitle
                                    else -> AppLocale.deckLabWinTournamentSubtitle
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.textMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tabs: I Miei Deck | Meta Deck
                    SecondaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = AppColors.blue,
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
                                selectedContentColor = AppColors.blue,
                                unselectedContentColor = AppColors.textMuted
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
                        containerColor = AppColors.purple,
                        contentColor = AppColors.textPrimary,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = AppLocale.importDeck)
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
                        containerColor = AppColors.blue,
                        contentColor = AppColors.textPrimary,
                        shape = RoundedCornerShape(16.dp),
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text(AppLocale.createNewDeck) }
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
                        // Indice costruito una volta per l'intera lista, invece che
                        // scandito da ogni riga.
                        val ownedById = remember(viewModel.ownedCards) {
                            viewModel.ownedCards.associateBy { it.id }
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(viewModel.decks, key = { it.id }) { deck ->
                                DeckItem(
                                    deck = deck,
                                    onClick = { selectedDeck = deck },
                                    ownedById = ownedById
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
                                viewModel.importFromMetaDeck(metaDeck)
                                metaDeckViewModel.selectDeck(null)
                            } else {
                                metaDeckViewModel.selectDeck(null)
                                showPremiumDeckDialog = true
                            }
                        },
                        onCardClick = { metaDeck ->
                            // Stesso gate della tab Win Tournament: senza, questa tab
                            // offriva decklist illimitate agli utenti free.
                            if (premiumManager.canViewMetaDeck()) {
                                premiumManager.consumeMetaDeckView()
                                metaDeckViewModel.selectDeck(metaDeck)
                            } else {
                                showPremiumMetaDeckDialog = true
                            }
                        }
                    )
                }

                selectedTabIndex == 2 -> {
                    // Tab: Win Tournament – tornei con top 3 vincitori per evento
                    WinTournamentSection(
                        viewModel = metaDeckViewModel,
                        onImportDeck = { metaDeck ->
                            if (premiumManager.canCreateDeck(viewModel.decks.size)) {
                                viewModel.importFromMetaDeck(metaDeck)
                                metaDeckViewModel.selectDeck(null)
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
                containerColor = AppColors.surface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.textMuted) }
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
                    viewModel.importFromText(text)
                    showImportDialog = false
                    // ImportResultDialog piu' sotto gestisce sempre il passo successivo
                    // (matched/missing), qui non serve altro.
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
                    viewModel.addMissingCardsToCollection(importResult.missingMetaDeckCards, context) {
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
                .background(AppColors.card),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Science, contentDescription = null, tint = AppColors.lavender, modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Ancora nessun deck",
            color = AppColors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Inizia a sperimentare nel laboratorio e crea la tua squadra perfetta.",
            color = AppColors.textMuted,
            textAlign = TextAlign.Center,
            fontSize = 13.sp
        )
    }
}

