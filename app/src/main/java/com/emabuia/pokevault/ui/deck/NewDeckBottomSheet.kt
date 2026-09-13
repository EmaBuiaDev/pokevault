@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emabuia.pokevault.ui.deck

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.DeckLabViewModel

private const val STEP_CARDS = 0
private const val STEP_DETAILS = 1

/**
 * L'editor del deck, in due passi.
 *
 * Prima era un solo pannello che teneva insieme nome, copertina, tre tab di
 * categoria, ricerca locale, ricerca online, griglia e salvataggio. Nome e
 * copertina stavano per giunta chiusi dietro a un chevron da sedici dp, per cui
 * dopo un import — quando le carte ci sono gia' e l'unica cosa che resta da
 * fare e' proprio dare un nome e scegliere la copertina — si atterrava sulla
 * griglia delle carte e bisognava scoprire quel chevron.
 *
 * Ora i due momenti sono separati, e dopo un import si parte dal secondo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDeckBottomSheetContent(
    viewModel: DeckLabViewModel,
    isEditing: Boolean = false,
    onSave: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Dopo un import le carte ci sono gia': il passo che serve e' il secondo.
    var step by remember {
        mutableIntStateOf(if (viewModel.isImportReviewMode) STEP_DETAILS else STEP_CARDS)
    }

    var tcgCardToAdd by remember { mutableStateOf<TcgCard?>(null) }
    var tcgAddQty by remember { mutableIntStateOf(1) }

    val canSave = viewModel.newDeckName.isNotBlank() && viewModel.selectedCardsIds.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            // La tastiera comprime il pannello, e va bene: ogni passo ha un
            // corpo che scorre, quindi quello che resta visibile si raggiunge
            // comunque. Prima non era cosi', perche' il corpo conteneva due
            // griglie a peso fisso che si accorciavano fino a sparire.
            .imePadding()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
    ) {
        DeckEditorHeader(
            isEditing = isEditing,
            cardCount = viewModel.selectedCardsIds.size
        )

        Spacer(modifier = Modifier.height(10.dp))

        DeckStepSwitch(
            step = step,
            onSelect = {
                focusManager.clearFocus()
                step = it
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Un solo figlio con weight(1f) alla volta. Prima la griglia locale e
        // quella dei risultati online potevano essere presenti insieme, e si
        // spartivano l'altezza dimezzandosi a vicenda.
        Box(modifier = Modifier.weight(1f)) {
            when (step) {
                STEP_CARDS -> DeckCardsStep(
                    viewModel = viewModel,
                    onTcgCardClick = { card ->
                        tcgCardToAdd = card
                        tcgAddQty = 1
                    }
                )

                else -> DeckDetailsStep(
                    viewModel = viewModel,
                    onGoToCards = {
                        focusManager.clearFocus()
                        step = STEP_CARDS
                    }
                )
            }
        }

        viewModel.validationError?.let { error ->
            Text(
                text = error,
                color = AppColors.red,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                focusManager.clearFocus()
                onSave()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.blue,
                disabledContainerColor = AppColors.card,
                disabledContentColor = AppColors.textMuted
            ),
            enabled = canSave && !viewModel.isSaving
        ) {
            if (viewModel.isSaving) {
                CircularProgressIndicator(color = AppColors.onAccent, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    text = if (isEditing) AppLocale.deckSaveChanges else AppLocale.deckSaveNew,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    tcgCardToAdd?.let { dialogCard ->
        TcgCardQuantityDialog(
            card = dialogCard,
            quantity = tcgAddQty,
            onQuantityChange = { tcgAddQty = it },
            onConfirm = {
                val qty = tcgAddQty
                tcgCardToAdd = null
                viewModel.addTcgCardToDeck(dialogCard, qty, context)
            },
            onDismiss = { tcgCardToAdd = null }
        )
    }
}

@Composable
private fun DeckEditorHeader(isEditing: Boolean, cardCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isEditing) AppLocale.deckEditTitle else AppLocale.deckNewTitle,
            color = AppColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )

        // 60 e' il numero di carte di un mazzo legale: il contatore e' un
        // obiettivo, non una capienza, e cambia colore quando lo si raggiunge.
        val counterColor by animateColorAsState(
            targetValue = if (cardCount == 60) AppColors.green else AppColors.textMuted,
            animationSpec = tween(AppMotion.state),
            label = "deckCounter"
        )

        Surface(
            color = counterColor.copy(alpha = 0.14f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "$cardCount / 60",
                color = counterColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DeckStepSwitch(step: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(AppLocale.deckStepCards, AppLocale.deckStepDetails)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.card)
            .padding(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == step
            val background by animateColorAsState(
                targetValue = if (selected) AppColors.blue else Color.Transparent,
                animationSpec = tween(AppMotion.state),
                label = "stepBackground"
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) AppColors.onAccent else AppColors.textSecondary,
                animationSpec = tween(AppMotion.state),
                label = "stepText"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(background)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Passo 1: le carte
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun DeckCardsStep(
    viewModel: DeckLabViewModel,
    onTcgCardClick: (TcgCard) -> Unit
) {
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var cardSearchQuery by remember { mutableStateOf("") }
    var pendingSelectedCounts by remember { mutableStateOf(mapOf<String, Int>()) }
    val tabs = listOf("Pokémon", "Trainer", "Energia")

    LaunchedEffect(selectedTabIndex, cardSearchQuery) {
        pendingSelectedCounts = emptyMap()
    }

    val filteredCards = remember(
        selectedTabIndex,
        viewModel.ownedCards,
        viewModel.selectedCardsIds,
        viewModel.isImportReviewMode,
        cardSearchQuery
    ) {
        val ownedById = viewModel.ownedCards.associateBy { it.id }
        val deckCardKeys = if (viewModel.isImportReviewMode) {
            viewModel.selectedCardsIds
                .mapNotNull { id -> ownedById[id] }
                .map { viewModel.getCardKey(it) }
                .toSet()
        } else {
            emptySet()
        }

        val sourceCards = if (viewModel.isImportReviewMode) {
            viewModel.ownedCards.filter { card -> viewModel.getCardKey(card) in deckCardKeys }
        } else {
            viewModel.ownedCards
        }

        sourceCards
            .filter { card ->
                val category = viewModel.classifyCard(card)
                val tabMatch = when (selectedTabIndex) {
                    0 -> category == "Pokémon"
                    1 -> category == "Trainer"
                    2 -> category == "Energy"
                    else -> true
                }
                val queryMatch = cardSearchQuery.isBlank() ||
                    card.name.contains(cardSearchQuery, ignoreCase = true) ||
                    card.cardNumber.contains(cardSearchQuery, ignoreCase = true) ||
                    card.set.contains(cardSearchQuery, ignoreCase = true)
                tabMatch && queryMatch
            }
            .distinctBy { viewModel.getCardKey(it) }
    }

    val pendingTotal = pendingSelectedCounts.values.sum()

    Column(modifier = Modifier.fillMaxSize()) {
        if (viewModel.isImportReviewMode) {
            ImportReviewBanner(onShowWholeCollection = { viewModel.exitImportReviewMode() })
            Spacer(modifier = Modifier.height(8.dp))
        }

        SecondaryTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = AppColors.blue,
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
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    selectedContentColor = AppColors.blue,
                    unselectedContentColor = AppColors.textMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = cardSearchQuery,
                onValueChange = {
                    cardSearchQuery = it
                    if (it.isBlank()) viewModel.clearTcgSearch()
                },
                placeholder = {
                    Text(AppLocale.deckSearchCards, color = AppColors.textMuted, fontSize = 12.sp)
                },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = AppColors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    if (cardSearchQuery.isNotBlank()) {
                        IconButton(
                            onClick = {
                                cardSearchQuery = ""
                                viewModel.clearTcgSearch()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = AppLocale.clearSearch,
                                tint = AppColors.textMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AppColors.card,
                    unfocusedContainerColor = AppColors.card,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = AppColors.blue,
                    focusedTextColor = AppColors.textPrimary,
                    unfocusedTextColor = AppColors.textPrimary
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
            )

            Button(
                onClick = {
                    filteredCards.forEach { card ->
                        val qty = pendingSelectedCounts[viewModel.getCardKey(card)] ?: 0
                        repeat(qty) { viewModel.addCardToDeck(card) }
                    }
                    pendingSelectedCounts = emptyMap()
                },
                enabled = pendingTotal > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.blue,
                    disabledContainerColor = AppColors.card,
                    disabledContentColor = AppColors.textMuted
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (pendingTotal > 0) {
                        AppLocale.deckPendingSelection(pendingTotal)
                    } else {
                        AppLocale.deckAddButton
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        if (cardSearchQuery.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { viewModel.searchCardsInSets(cardSearchQuery, context = context) },
                    enabled = !viewModel.isSearchingCards,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    if (viewModel.isSearchingCards) {
                        CircularProgressIndicator(
                            color = AppColors.purple,
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = AppColors.purple,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = AppLocale.deckSearchOnline,
                        color = AppColors.purple,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            viewModel.tcgSearchError?.let { error ->
                Text(
                    text = error,
                    color = AppColors.yellow,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val onlineResults = viewModel.tcgSearchResults

        if (filteredCards.isEmpty() && onlineResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (cardSearchQuery.isNotBlank()) {
                        AppLocale.deckNoLocalResults
                    } else {
                        AppLocale.deckNoCardsInCategory
                    },
                    color = AppColors.textMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            // Collezione e risultati online nella stessa griglia, sotto due
            // intestazioni: sono due sorgenti della stessa cosa, e come due
            // griglie affiancate si rubavano l'altezza a vicenda.
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredCards.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        GridSectionHeader(
                            label = AppLocale.deckInYourCollection,
                            count = filteredCards.size
                        )
                    }
                    items(filteredCards, key = { "owned_" + viewModel.getCardKey(it) }) { card ->
                        val key = viewModel.getCardKey(card)
                        val inDeckCount = viewModel.getQuantityInDeck(card)
                        val totalOwned = viewModel.getTotalOwnedQuantity(card)
                        val availableToAdd = (totalOwned - inDeckCount).coerceAtLeast(0)
                        val pendingCount = pendingSelectedCounts[key] ?: 0

                        CardSelectionItem(
                            card = card,
                            inDeckCount = inDeckCount,
                            totalOwned = totalOwned,
                            isEditable = true,
                            pendingSelectionCount = pendingCount,
                            onAdd = {
                                if (pendingCount < availableToAdd) {
                                    pendingSelectedCounts =
                                        pendingSelectedCounts + (key to (pendingCount + 1))
                                }
                            },
                            onRemove = { viewModel.removeCardFromDeck(card) }
                        )
                    }
                }

                if (onlineResults.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        GridSectionHeader(
                            label = AppLocale.deckOnlineResults,
                            count = onlineResults.size
                        )
                    }
                    items(onlineResults, key = { "tcg_" + it.id }) { tcgCard ->
                        TcgCardSearchItem(
                            card = tcgCard,
                            onClick = { onTcgCardClick(tcgCard) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridSectionHeader(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AppColors.textMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "($count)",
            color = AppColors.textMuted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ImportReviewBanner(onShowWholeCollection: () -> Unit) {
    Surface(
        color = AppColors.blue.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.PlaylistAddCheck,
                contentDescription = null,
                tint = AppColors.blue,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = AppLocale.deckImportReviewTitle,
                    color = AppColors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = AppLocale.deckImportReviewBody,
                    color = AppColors.textMuted,
                    fontSize = 10.sp
                )
            }
            TextButton(onClick = onShowWholeCollection) {
                Text(
                    text = AppLocale.deckShowWholeCollection,
                    color = AppColors.blue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Passo 2: nome e copertina
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun DeckDetailsStep(
    viewModel: DeckLabViewModel,
    onGoToCards: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    val deckCards = remember(viewModel.ownedCards, viewModel.selectedCardsIds) {
        val selected = viewModel.selectedCardsIds.toSet()
        viewModel.ownedCards
            .filter { it.id in selected && it.imageUrl.isNotBlank() }
            .distinctBy { it.imageUrl }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (viewModel.isImportReviewMode) {
            ImportedRecap(
                cardCount = viewModel.selectedCardsIds.size,
                onGoToCards = onGoToCards
            )
        }

        Column {
            Text(
                text = AppLocale.deckNameLabel,
                color = AppColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = viewModel.newDeckName,
                onValueChange = { viewModel.newDeckName = it },
                placeholder = {
                    Text(AppLocale.deckNamePlaceholder, color = AppColors.textMuted, fontSize = 13.sp)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.blue,
                    unfocusedBorderColor = AppColors.textMuted.copy(alpha = 0.4f),
                    cursorColor = AppColors.blue,
                    focusedTextColor = AppColors.textPrimary,
                    unfocusedTextColor = AppColors.textPrimary
                )
            )
        }

        DeckCoverSection(
            deckCards = deckCards,
            selectedCovers = viewModel.coverImageUrls,
            onToggleCover = { viewModel.toggleCoverCard(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ImportedRecap(cardCount: Int, onGoToCards: () -> Unit) {
    Surface(
        color = AppColors.green.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = AppColors.green,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = AppLocale.deckImportedTitle,
                    color = AppColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = AppLocale.deckImportedBody(cardCount),
                    color = AppColors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            TextButton(onClick = onGoToCards) {
                Text(
                    text = AppLocale.deckGoToCards,
                    color = AppColors.green,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * La copertina: anteprima sopra, carte del deck a griglia sotto.
 *
 * Prima era un AlertDialog con dentro una LazyRow: per trovare la carta da
 * mettere in copertina si scorreva orizzontalmente sessanta miniature dentro
 * una finestra alta un centimetro. Qui le carte stanno nella stessa griglia a
 * cinque colonne usata per costruire il deck, e l'anteprima mostra subito il
 * risultato invece di lasciarlo indovinare.
 */
@Composable
private fun DeckCoverSection(
    deckCards: List<PokemonCard>,
    selectedCovers: List<String>,
    onToggleCover: (String) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = AppLocale.deckCoverTitle,
                color = AppColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${selectedCovers.size}/2",
                color = if (selectedCovers.isEmpty()) AppColors.textMuted else AppColors.blue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = AppLocale.deckCoverHint,
            color = AppColors.textMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        CoverPreview(selectedCovers = selectedCovers)

        Spacer(modifier = Modifier.height(12.dp))

        if (deckCards.isEmpty()) {
            Text(
                text = AppLocale.deckNoCardsYet,
                color = AppColors.textMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
            )
            return
        }

        // La griglia sta dentro una colonna scorrevole, quindi non puo'
        // scorrere a sua volta: le righe si calcolano e l'altezza e' fissa.
        val columns = 5
        val rows = (deckCards.size + columns - 1) / columns
        val rowHeight = 78.dp
        val gridHeight = rowHeight * rows + 8.dp * (rows - 1).coerceAtLeast(0)

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(deckCards, key = { it.imageUrl }) { card ->
                CoverCandidate(
                    card = card,
                    isSelected = selectedCovers.contains(card.imageUrl),
                    onClick = { onToggleCover(card.imageUrl) }
                )
            }
        }
    }
}

/**
 * Come apparira' il deck nell'elenco.
 *
 * Due slot, perche' due sono le copertine: vuoti mostrano il posto che
 * occuperanno, cosi' il rapporto fra "ne ho scelta una" e "ne servono due" si
 * vede invece di doverlo leggere nel contatore.
 */
@Composable
private fun CoverPreview(selectedCovers: List<String>) {
    Surface(
        color = AppColors.card,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(2) { index ->
                val url = selectedCovers.getOrNull(index)
                Box(
                    modifier = Modifier
                        .size(54.dp, 76.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.background)
                        .border(
                            BorderStroke(
                                1.dp,
                                if (url != null) AppColors.blue.copy(alpha = 0.6f)
                                else AppColors.textMuted.copy(alpha = 0.3f)
                            ),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (url != null) {
                        AsyncImage(
                            model = url,
                            contentDescription = AppLocale.deckCover(index + 1),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = AppColors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (index == 0) Spacer(modifier = Modifier.width(8.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = AppLocale.deckCoverPreviewTitle,
                    color = AppColors.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (selectedCovers.isEmpty()) {
                        AppLocale.deckCoverEmpty
                    } else {
                        AppLocale.deckCoverChosen(selectedCovers.size)
                    },
                    color = AppColors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun CoverCandidate(
    card: PokemonCard,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(78.dp)
            .clip(RoundedCornerShape(7.dp))
            .border(
                BorderStroke(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) AppColors.blue else AppColors.textMuted.copy(alpha = 0.3f)
                ),
                RoundedCornerShape(7.dp)
            )
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(card.imageUrl)
                .size(140, 200)
                .build(),
            contentDescription = AppLocale.selectCover(card.name),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        if (isSelected) {
            Surface(
                color = AppColors.blue,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = AppLocale.selectedCover,
                        tint = AppColors.onAccent,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Dialog quantita' per le carte trovate online
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun TcgCardQuantityDialog(
    card: TcgCard,
    quantity: Int,
    onQuantityChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.card,
        title = {
            Text(
                text = card.name,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = card.images.small,
                    contentDescription = card.name,
                    modifier = Modifier
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                card.set?.name?.let { setName ->
                    Text(text = setName, color = AppColors.textMuted, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = AppLocale.howManyCopiesToAdd,
                    color = AppColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(
                        onClick = { if (quantity > 1) onQuantityChange(quantity - 1) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AppColors.background)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = AppLocale.minus, tint = AppColors.textPrimary)
                    }
                    Text(
                        text = "$quantity",
                        color = AppColors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                    IconButton(
                        onClick = { if (quantity < 4) onQuantityChange(quantity + 1) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AppColors.background)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = AppLocale.plus, tint = AppColors.textPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = AppLocale.addedToCollectionAndDeck,
                    color = AppColors.textMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(AppLocale.addCopiesToDeck(quantity), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppLocale.cancel, color = AppColors.textMuted)
            }
        }
    )
}
