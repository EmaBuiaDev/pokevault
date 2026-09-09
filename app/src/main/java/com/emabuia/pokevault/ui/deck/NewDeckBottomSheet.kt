package com.emabuia.pokevault.ui.deck

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.DeckLabViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDeckBottomSheetContent(
    viewModel: DeckLabViewModel,
    isEditing: Boolean = false,
    onSave: () -> Unit
) {
    var showCoverPicker by remember { mutableStateOf(false) }
    var showSetupSection by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val tabs = listOf("Pokémon", "Trainer", "Energia")

    var cardSearchQuery by remember { mutableStateOf("") }
    var tcgCardToAdd by remember { mutableStateOf<TcgCard?>(null) }
    var tcgAddQty by remember { mutableIntStateOf(1) }
    var pendingSelectedCounts by remember { mutableStateOf(mapOf<String, Int>()) }

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

    val canSave = viewModel.newDeckName.isNotBlank() && viewModel.selectedCardsIds.isNotEmpty()
    val pendingSelectionTotal = pendingSelectedCounts.values.sum()
    val hasPendingSelection = pendingSelectionTotal > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .padding(horizontal = 16.dp)
            .imePadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkCard)
                .padding(10.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isEditing) "Modifica Deck" else "Nuovo Deck",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { showSetupSection = !showSetupSection }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = if (showSetupSection) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showSetupSection) "Chiudi impostazioni" else "Apri impostazioni",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${viewModel.selectedCardsIds.size} / 60",
                            color = TextWhite,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (showSetupSection) {
                    Spacer(modifier = Modifier.height(8.dp))

                    TextField(
                        value = viewModel.newDeckName,
                        onValueChange = { viewModel.newDeckName = it },
                        placeholder = { Text(AppLocale.deckNamePlaceholder, color = TextMuted, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
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
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(2) { index ->
                            val url = viewModel.coverImageUrls.getOrNull(index)
                            Box(
                                modifier = Modifier
                                    .size(32.dp, 46.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkBackground)
                                    .border(
                                        BorderStroke(1.dp, BlueCard.copy(alpha = 0.5f)),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable(enabled = !url.isNullOrBlank()) { viewModel.toggleCoverCard(url.orEmpty()) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (!url.isNullOrBlank()) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = AppLocale.deckCover(index + 1),
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        OutlinedButton(
                            onClick = { showCoverPicker = true },
                            enabled = viewModel.selectedCardsIds.isNotEmpty(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BlueCard),
                            border = BorderStroke(1.dp, if (viewModel.selectedCardsIds.isNotEmpty()) BlueCard.copy(alpha = 0.5f) else TextMuted.copy(alpha = 0.3f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${viewModel.coverImageUrls.size}/2", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        if (viewModel.isImportReviewMode) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = BlueCard.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, contentDescription = null, tint = BlueCard, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Revisione import", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Vedi solo le carte appena importate", color = TextMuted, fontSize = 10.sp)
                    }
                    TextButton(onClick = { viewModel.exitImportReviewMode() }) {
                        Text("Tutta la collezione", color = BlueCard, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    selectedContentColor = BlueCard,
                    unselectedContentColor = TextMuted
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
                    Text(
                        text = "Cerca carte",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    if (cardSearchQuery.isNotBlank()) {
                        IconButton(onClick = {
                            cardSearchQuery = ""
                            viewModel.clearTcgSearch()
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = AppLocale.clearSearch,
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkCard,
                    unfocusedContainerColor = DarkCard,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = BlueCard,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
            )

            Button(
                onClick = {
                    if (hasPendingSelection) {
                        filteredCards
                            .forEach { card ->
                                val qty = pendingSelectedCounts[viewModel.getCardKey(card)] ?: 0
                                repeat(qty) { viewModel.addCardToDeck(card) }
                            }
                        pendingSelectedCounts = emptyMap()
                    }
                },
                enabled = hasPendingSelection,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BlueCard,
                    disabledContainerColor = DarkCard,
                    disabledContentColor = TextMuted
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (hasPendingSelection) "Aggiungi $pendingSelectionTotal" else "Aggiungi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        if (cardSearchQuery.isNotBlank() || hasPendingSelection) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    hasPendingSelection -> "$pendingSelectionTotal carte selezionate"
                    filteredCards.isNotEmpty() -> "${filteredCards.size} risultati"
                    else -> "Nessun risultato locale"
                },
                color = if (hasPendingSelection) YellowCard else TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Affordanza sempre visibile appena si scrive qualcosa: prima "Cerca nei set TCG"
        // compariva solo dopo zero risultati locali, quindi la ricerca online restava
        // scoperta finche' non si falliva prima una ricerca nella propria collezione.
        if (cardSearchQuery.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { viewModel.searchCardsInSets(cardSearchQuery, context = context) },
                    enabled = !viewModel.isSearchingCards,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    if (viewModel.isSearchingCards) {
                        CircularProgressIndicator(color = PurpleCard, modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, tint = PurpleCard, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cerca anche nei set TCG online", color = PurpleCard, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (viewModel.tcgSearchError != null) {
                Text(
                    text = viewModel.tcgSearchError!!,
                    color = YellowCard,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (filteredCards.isNotEmpty()) {
            Text(
                text = "Nella tua collezione",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredCards, key = { viewModel.getCardKey(it) }) { card ->
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
                            if (availableToAdd <= 0) return@CardSelectionItem
                            if (pendingCount < availableToAdd) {
                                pendingSelectedCounts = pendingSelectedCounts + (key to (pendingCount + 1))
                            }
                        },
                        onRemove = { viewModel.removeCardFromDeck(card) }
                    )
                }
            }
        } else if (cardSearchQuery.isNotBlank()) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nessuna carta trovata nella tua collezione.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nessuna carta in questa categoria.\nAggiungi carte alla tua collezione o cerca nei set.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }

        if (viewModel.tcgSearchResults.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Risultati online (${viewModel.tcgSearchResults.size}) - tocca per aggiungere al deck",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.tcgSearchResults, key = { it.id }) { tcgCard ->
                    TcgCardSearchItem(
                        card = tcgCard,
                        onClick = {
                            tcgCardToAdd = tcgCard
                            tcgAddQty = 1
                        }
                    )
                }
            }
        }

        if (viewModel.validationError != null) {
            Text(
                text = viewModel.validationError!!,
                color = RedCard,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

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

    tcgCardToAdd?.let { dialogCard ->
        AlertDialog(
            onDismissRequest = { tcgCardToAdd = null },
            containerColor = DarkCard,
            title = {
                Text(
                    text = dialogCard.name,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = dialogCard.images.small,
                        contentDescription = dialogCard.name,
                        modifier = Modifier
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    dialogCard.set?.name?.let { setName ->
                        Text(text = setName, color = TextMuted, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = AppLocale.howManyCopiesToAdd,
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(
                            onClick = { if (tcgAddQty > 1) tcgAddQty-- },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(DarkBackground)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = AppLocale.minus, tint = TextWhite)
                        }
                        Text(
                            text = "$tcgAddQty",
                            color = TextWhite,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        IconButton(
                            onClick = { if (tcgAddQty < 4) tcgAddQty++ },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(DarkBackground)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = AppLocale.plus, tint = TextWhite)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = AppLocale.addedToCollectionAndDeck,
                        color = TextMuted,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val card = tcgCardToAdd ?: return@Button
                        val qty = tcgAddQty
                        tcgCardToAdd = null
                        viewModel.addTcgCardToDeck(card, qty, context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueCard),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(AppLocale.addCopiesToDeck(tcgAddQty), color = TextWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { tcgCardToAdd = null }) {
                    Text(AppLocale.cancel, color = TextMuted)
                }
            }
        )
    }

    if (showCoverPicker && viewModel.selectedCardsIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showCoverPicker = false },
            containerColor = DarkCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(AppLocale.choose2Covers, color = TextWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${viewModel.coverImageUrls.size}/2", color = TextMuted, fontSize = 12.sp)
                }
            },
            text = {
                Column {
                    Text(
                        text = AppLocale.onlyDeckCardsCanBeCover,
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val selectedCards = viewModel.ownedCards.filter { it.id in viewModel.selectedCardsIds }.distinctBy { it.imageUrl }
                        items(selectedCards) { card ->
                            val isSelectedCover = viewModel.coverImageUrls.contains(card.imageUrl)
                            Box(
                                modifier = Modifier
                                    .size(56.dp, 80.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(
                                        BorderStroke(if (isSelectedCover) 2.dp else 1.dp, if (isSelectedCover) BlueCard else TextMuted.copy(alpha = 0.35f)),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { viewModel.toggleCoverCard(card.imageUrl) }
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

                                if (isSelectedCover) {
                                    Surface(
                                        color = BlueCard,
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(18.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Check, contentDescription = AppLocale.selectedCover, tint = TextWhite, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showCoverPicker = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueCard),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(AppLocale.close, color = TextWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCoverPicker = false }) {
                    Text(AppLocale.cancel, color = TextMuted)
                }
            }
        )
    }
}

