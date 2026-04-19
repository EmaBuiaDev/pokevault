package com.emabuia.pokevault.ui.pokedex

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.ui.wishlist.CreateWishlistDialog
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.RarityInfo
import com.emabuia.pokevault.util.RarityUtils
import com.emabuia.pokevault.viewmodel.SetDetailViewModel
import com.emabuia.pokevault.viewmodel.WishlistViewModel
import java.util.Locale

fun formatReleaseDate(date: String): String {
    return try {
        val parts = date.split("/")
        if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else date
    } catch (_: Exception) {
        date
    }
}

private fun resolveDisplayPrice(card: TcgCard): Double? {
    return card.cardmarket?.prices?.averageSellPrice
        ?: card.cardmarket?.prices?.lowPrice
}

private fun formatPriceEur(price: Double): String {
    return "€ ${String.format(Locale.ITALY, "%.2f", price)}"
}

private val ITALIAN_TYPE_MAP = mapOf(
    "fuoco" to "fire", "acqua" to "water", "erba" to "grass",
    "elettro" to "lightning", "elettrico" to "lightning", "fulmine" to "lightning",
    "psico" to "psychic", "psichico" to "psychic",
    "lotta" to "fighting", "combattimento" to "fighting",
    "buio" to "darkness", "oscurita" to "darkness", "oscurità" to "darkness",
    "metallo" to "metal", "acciaio" to "metal",
    "drago" to "dragon", "folletto" to "fairy", "fata" to "fairy",
    "incolore" to "colorless", "normale" to "colorless"
)

private val ITALIAN_CATEGORY_MAP = mapOf(
    "allenatore" to "trainer", "aiuto" to "supporter", "sostenitore" to "supporter",
    "stadio" to "stadium", "strumento" to "tool", "oggetto" to "item",
    "energia" to "energy", "pokemon" to "pokémon"
)

private val ITALIAN_RARITY_MAP = mapOf(
    "comune" to "common", "non comune" to "uncommon",
    "rara" to "rare", "ultra rara" to "ultra rare",
    "segreta" to "secret", "iper rara" to "hyper rare",
    "illustrazione" to "illustration", "promo" to "promo",
    "doppia rara" to "double rare", "olografica" to "holo"
)

data class ItalianSearchContext(
    val q: String,
    val translatedQuery: String,
    val matchedTypes: Set<String>,
    val matchedCategories: Set<String>,
    val matchedRarities: Set<String>
)

fun buildSearchContext(query: String, translatedQuery: String = ""): ItalianSearchContext {
    val q = query.lowercase().trim()
    val types = mutableSetOf<String>()
    val categories = mutableSetOf<String>()
    val rarities = mutableSetOf<String>()

    if (q.isNotEmpty()) {
        ITALIAN_TYPE_MAP.forEach { (it, en) -> if (q.contains(it)) types.add(en.lowercase()) }
        ITALIAN_CATEGORY_MAP.forEach { (it, en) -> if (q.contains(it)) categories.add(en.lowercase()) }
        ITALIAN_RARITY_MAP.forEach { (it, en) -> if (q.contains(it)) rarities.add(en.lowercase()) }
    }

    return ItalianSearchContext(q, translatedQuery, types, categories, rarities)
}

fun matchesSearchContext(card: TcgCard, ctx: ItalianSearchContext): Boolean {
    if (ctx.q.isBlank()) return true

    val inName = card.name.contains(ctx.q, ignoreCase = true)
    val inTranslated = ctx.translatedQuery.isNotBlank() && card.name.contains(ctx.translatedQuery, ignoreCase = true)
    val inType = card.types?.any { it.lowercase() in ctx.matchedTypes || it.contains(ctx.q, ignoreCase = true) } == true
    val inSupertype = card.supertype.contains(ctx.q, ignoreCase = true) || card.supertype.lowercase() in ctx.matchedCategories
    val inRarity = card.rarity?.let { it.contains(ctx.q, ignoreCase = true) || it.lowercase() in ctx.matchedRarities } == true
    val inNumber = card.number.contains(ctx.q, ignoreCase = true)

    return inName || inTranslated || inType || inSupertype || inRarity || inNumber
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetDetailScreen(
    setId: String,
    setName: String,
    onBack: () -> Unit,
    viewModel: SetDetailViewModel = viewModel(),
    wishlistViewModel: WishlistViewModel = viewModel()
) {
    val state = viewModel.uiState
    val haptic = LocalHapticFeedback.current
    val premiumManager = remember { PremiumManager.getInstance() }
    val isPremium by premiumManager.isPremium.collectAsState()
    var selectedCard by remember { mutableStateOf<TcgCard?>(null) }
    var quickAddCard by remember { mutableStateOf<TcgCard?>(null) }
    var selectedRarityFilter by remember { mutableStateOf<String?>(null) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var pickerCard by remember { mutableStateOf<TcgCard?>(null) }
    var createDialogCard by remember { mutableStateOf<TcgCard?>(null) }

    LaunchedEffect(selectedCard?.id) {
        val card = selectedCard
        if (card != null) viewModel.loadPokeWalletPrices(card)
        else viewModel.clearPokeWalletPrices()
    }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedCardIds by remember { mutableStateOf(setOf<String>()) }
    var selectionVariant by remember { mutableStateOf("Normal") }
    val gridState = rememberLazyGridState()

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedCardIds = emptySet()
    }

    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode) quickAddCard = null
    }

    LaunchedEffect(setId) { viewModel.loadSet(setId) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.successMessage, state.errorMessage) {
        val msg = state.successMessage ?: state.errorMessage
        if (msg != null) { snackbarHostState.showSnackbar(msg); viewModel.clearMessages() }
    }
    LaunchedEffect(wishlistViewModel.successMessage, wishlistViewModel.errorMessage) {
        val msg = wishlistViewModel.successMessage ?: wishlistViewModel.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            wishlistViewModel.clearMessages()
        }
    }

    val sortedCards = remember(state.cards, state.ownedCardIds, state.searchQuery, state.translatedQuery, state.showOnlyMissing, state.showOnlyOwned, state.selectedType, state.selectedSupertype, selectedRarityFilter) {
        val searchCtx = buildSearchContext(state.searchQuery, state.translatedQuery)
        state.cards.filter { card ->
            val matchesRarity = selectedRarityFilter == null || card.rarity == selectedRarityFilter
            val matchesSearch = matchesSearchContext(card, searchCtx)
            val matchesMissing = !state.showOnlyMissing || card.id !in state.ownedCardIds
            val matchesOwned = !state.showOnlyOwned || card.id in state.ownedCardIds
            val matchesType = state.selectedType == null || card.types?.contains(state.selectedType) == true
            val matchesSupertype = state.selectedSupertype == null || card.supertype == state.selectedSupertype
            
            matchesRarity && matchesSearch && matchesMissing && matchesOwned && matchesType && matchesSupertype
        }.sortedBy {
            it.number.replace(Regex("[^0-9]"), "").toIntOrNull() ?: Int.MAX_VALUE
        }
    }

    val rarityCounts = remember(state.cards, state.ownedCardIds) {
        state.cards.groupBy { RarityUtils.getRarityInfo(it.rarity) }
            .mapValues { (_, cards) -> Pair(cards.count { it.id in state.ownedCardIds }, cards.size) }
            .toSortedMap(compareBy { it.sortOrder })
    }

    val distinctRarities = remember(state.cards) {
        state.cards.mapNotNull { it.rarity }
            .distinct()
            .sortedBy { RarityUtils.getRarityInfo(it).sortOrder }
    }

    if (selectedCard != null) {
        CardDetailBottomSheet(
            card = selectedCard!!,
            isOwned = selectedCard!!.id in state.ownedCardIds,
            isLoading = state.isAddingCard == selectedCard!!.id,
            pokeWalletPrices = state.selectedCardPokeWalletPrices,
            isLoadingPokeWalletPrices = state.isLoadingPokeWalletPrices,
            onAddCard = { v, q, c, l -> 
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.addCardWithDetails(selectedCard!!, v, q, c, l) 
            },
            onRemoveCard = { viewModel.removeCard(selectedCard!!); selectedCard = null },
            onDismiss = { selectedCard = null },
            cardList = sortedCards,
            onCardChange = { selectedCard = it }
        )
    }

    if (pickerCard != null) {
        WishlistPickerDialog(
            wishlists = wishlistViewModel.wishlists,
            canCreateNew = premiumManager.canCreateWishlist(wishlistViewModel.wishlists.size),
            onDismiss = { pickerCard = null },
            onCreateNew = {
                createDialogCard = pickerCard
                pickerCard = null
            },
            onConfirmWishlist = { wishlistId ->
                val card = pickerCard
                if (card != null) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    wishlistViewModel.addCardToWishlist(wishlistId, card.id)
                }
                pickerCard = null
            }
        )
    }

    if (createDialogCard != null) {
        CreateWishlistDialog(
            onDismiss = { createDialogCard = null },
            onConfirm = { name, iconKey ->
                val card = createDialogCard
                if (card != null) {
                    wishlistViewModel.createWishlistAndAddCard(name, iconKey, card.id) { success ->
                        if (success) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            createDialogCard = null
                        }
                    }
                }
            },
            isSaving = wishlistViewModel.isSaving
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
        ) {
            TopAppBar(
                title = {
                    Column {
                        val cleanSetName = state.set?.name?.substringAfterLast(":")?.trim() ?: ""
                        Text(cleanSetName, fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.set != null) {
                            Text("${if (AppLocale.isItalian) "Data di uscita" else "Release date"}: ${formatReleaseDate(state.set.releaseDate)}", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = TextWhite) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )

            Box(modifier = Modifier.fillMaxSize()) {
                if (state.isLoading && state.set == null) {
                    // Full loading only when we have no data at all
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PokeballLoadingAnimation(message = AppLocale.loading)
                    }
                } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp,
                        bottom = if (isSelectionMode) 80.dp else 8.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.pointerInput(sortedCards.size, state.viewMode) {
                        if (state.viewMode == "grid") {
                            val headerCount = 4
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val item = gridState.layoutInfo.visibleItemsInfo.find { info ->
                                        offset.x.toInt() in info.offset.x until (info.offset.x + info.size.width) &&
                                        offset.y.toInt() in info.offset.y until (info.offset.y + info.size.height)
                                    }
                                    val cardIndex = (item?.index ?: -1) - headerCount
                                    val card = sortedCards.getOrNull(cardIndex)
                                    if (card != null) {
                                        isSelectionMode = true
                                        selectedCardIds = selectedCardIds + card.id
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (isSelectionMode) {
                                        change.consume()
                                        val item = gridState.layoutInfo.visibleItemsInfo.find { info ->
                                            change.position.x.toInt() in info.offset.x until (info.offset.x + info.size.width) &&
                                            change.position.y.toInt() in info.offset.y until (info.offset.y + info.size.height)
                                        }
                                        val cardIndex = (item?.index ?: -1) - headerCount
                                        val card = sortedCards.getOrNull(cardIndex)
                                        if (card != null && card.id !in selectedCardIds) {
                                            selectedCardIds = selectedCardIds + card.id
                                        }
                                    }
                                }
                            )
                        }
                    }
                ) {
                    item(span = { GridItemSpan(3) }) {
                        SetInfoHeader(
                            logoUrl = state.set?.images?.logo ?: "",
                            ownedCount = state.ownedCount,
                            displayTotal = state.displayTotal,
                            completionPercent = state.completionPercent,
                            rarityCounts = rarityCounts
                        )
                    }

                    item(span = { GridItemSpan(3) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkCard)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (state.searchQuery.isEmpty()) Text(if (AppLocale.isItalian) "Cerca in italiano o inglese..." else "Search in Italian or English...", color = TextMuted, fontSize = 13.sp)
                                        BasicTextField(
                                            value = state.searchQuery,
                                            onValueChange = { viewModel.updateSearchQuery(it) },
                                            textStyle = androidx.compose.ui.text.TextStyle(color = TextWhite, fontSize = 13.sp),
                                            singleLine = true,
                                            cursorBrush = SolidColor(BlueCard)
                                        )
                                    }
                                    // Translation indicator
                                    val isTranslating = state.searchQuery.length >= 3 && state.translatedQuery.isEmpty()
                                        && !state.cards.any { it.name.contains(state.searchQuery, ignoreCase = true) }
                                    if (isTranslating && state.searchQuery.isNotEmpty()) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            color = BlueCard,
                                            strokeWidth = 1.5.dp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    } else if (state.translatedQuery.isNotBlank() && state.searchQuery.isNotEmpty()) {
                                        Icon(Icons.Default.Translate, null, tint = GreenCard, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    if (state.searchQuery.isNotEmpty()) {
                                        Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier
                                            .size(16.dp)
                                            .clickable { viewModel.updateSearchQuery("") })
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (state.showOnlyMissing) RedCard.copy(alpha = 0.2f) else DarkCard)
                                    .border(1.dp, if (state.showOnlyMissing) RedCard else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable { viewModel.toggleShowOnlyMissing() }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterAltOff,
                                    contentDescription = null,
                                    tint = if (state.showOnlyMissing) RedCard else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (state.showOnlyOwned) GreenCard.copy(alpha = 0.2f) else DarkCard)
                                    .border(1.dp, if (state.showOnlyOwned) GreenCard else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable { viewModel.toggleShowOnlyOwned() }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (state.showOnlyOwned) GreenCard else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    item(span = { GridItemSpan(3) }) {
                        val activeFiltersCount = listOf(
                            state.selectedSupertype,
                            state.selectedType,
                            selectedRarityFilter
                        ).count { it != null }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkCard)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { filtersExpanded = !filtersExpanded }
                                    .padding(horizontal = 2.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = if (activeFiltersCount > 0) BlueCard else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (AppLocale.isItalian) "Filtri" else "Filters",
                                    color = TextWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )

                                if (activeFiltersCount > 0) {
                                    Text(
                                        text = "$activeFiltersCount",
                                        color = BlueCard,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .border(1.dp, BlueCard.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }

                                Icon(
                                    imageVector = if (filtersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            AnimatedVisibility(
                                visible = filtersExpanded,
                                enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(220)),
                                exit = fadeOut(animationSpec = tween(140)) + shrinkVertically(animationSpec = tween(180))
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        state.availableSupertypes.forEach { supertype ->
                                            item {
                                                FilterChip(
                                                    label = AppLocale.translateSupertype(supertype),
                                                    isSelected = state.selectedSupertype == supertype,
                                                    onClick = { viewModel.selectSupertype(if (state.selectedSupertype == supertype) null else supertype) }
                                                )
                                            }
                                        }

                                        item {
                                            VerticalDivider(
                                                modifier = Modifier
                                                    .height(20.dp)
                                                    .padding(horizontal = 4.dp),
                                                color = TextMuted.copy(alpha = 0.3f)
                                            )
                                        }

                                        state.availableTypes.forEach { type ->
                                            item {
                                                FilterChip(
                                                    label = AppLocale.translateType(type),
                                                    isSelected = state.selectedType == type,
                                                    onClick = { viewModel.selectType(if (state.selectedType == type) null else type) }
                                                )
                                            }
                                        }
                                    }

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        item {
                                            RarityFilterChip("${AppLocale.all} (${state.cards.size})", selectedRarityFilter == null) {
                                                selectedRarityFilter = null
                                            }
                                        }
                                        items(distinctRarities) { rarity ->
                                            val info = RarityUtils.getRarityInfo(rarity)
                                            val count = state.cards.count { it.rarity == rarity }
                                            RarityFilterChip(
                                                "${info.emoji} ${AppLocale.translateRarity(rarity)} ($count)",
                                                selectedRarityFilter == rarity,
                                                info.color
                                            ) { selectedRarityFilter = rarity }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(span = { GridItemSpan(3) }) {
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkCard), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf((if (AppLocale.isItalian) "Carte" else "Cards") to "grid", (if (AppLocale.isItalian) "Lista" else "List") to "list").forEach { (label, mode) ->
                                Text(label, color = if (state.viewMode == mode) TextWhite else TextMuted,
                                    fontWeight = if (state.viewMode == mode) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 13.sp, textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.setViewMode(mode) }
                                        .background(if (state.viewMode == mode) BlueCard.copy(alpha = 0.3f) else Color.Transparent)
                                        .padding(vertical = 10.dp))
                            }
                        }
                    }
                    if (state.isLoadingCards) {
                        // Shimmer placeholder while cards load
                        items(12) {
                            ShimmerCardPlaceholder()
                        }
                    } else if (sortedCards.isEmpty()) {
                        item(span = { GridItemSpan(3) }) {
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                Text(AppLocale.noResults, color = TextMuted, fontSize = 14.sp)
                            }
                        }
                    } else {
                        when (state.viewMode) {
                            "grid" -> items(sortedCards, key = { "${it.id}_${it.number}" }) { card ->
                                TcgCardCompactItem(
                                    card = card,
                                    isOwned = card.id in state.ownedCardIds,
                                    isWishlisted = wishlistViewModel.isCardWishlisted(card.id),
                                    canViewPrices = premiumManager.canViewPrices(),
                                    isAdding = state.isAddingCard == card.id,
                                    isPopupOpen = quickAddCard?.id == card.id,
                                    isSelected = card.id in selectedCardIds,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedCardIds = if (card.id in selectedCardIds) selectedCardIds - card.id else selectedCardIds + card.id
                                            if (selectedCardIds.isEmpty()) isSelectionMode = false
                                        } else {
                                            selectedCard = card
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isSelectionMode = true
                                        selectedCardIds = selectedCardIds + card.id
                                    },
                                    onQuickAddClick = {
                                        if (!isSelectionMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val variants = CardOptions.getVariantsForCard(
                                                card.tcgplayer?.prices?.keys ?: emptySet(), card.rarity
                                            )
                                            if (variants.size <= 1) {
                                                viewModel.addCardWithDetails(card, variants.firstOrNull() ?: "Holo", 1, "Near Mint", "🇮🇹 Italiano")
                                            } else {
                                                quickAddCard = if (quickAddCard?.id == card.id) null else card
                                            }
                                        }
                                    },
                                    onWishlistClick = {
                                        if (isSelectionMode) return@TcgCardCompactItem

                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (wishlistViewModel.isCardWishlisted(card.id)) {
                                            wishlistViewModel.removeCardFromAllWishlists(card.id)
                                            return@TcgCardCompactItem
                                        }

                                        val wishlists = wishlistViewModel.wishlists
                                        when {
                                            wishlists.isEmpty() -> createDialogCard = card
                                            wishlists.size == 1 && !isPremium -> wishlistViewModel.addCardToWishlist(
                                                wishlists.first().id,
                                                card.id
                                            )
                                            else -> pickerCard = card
                                        }
                                    },
                                    onVariantSelected = { variant ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.addCardWithDetails(card, variant, 1, "Near Mint", "🇮🇹 Italiano")
                                        quickAddCard = null
                                    }
                                )
                            }
                            "list" -> items(sortedCards, key = { "${it.id}_${it.number}" }, span = { GridItemSpan(3) }) { card ->
                                TcgCardListRow(
                                    card = card,
                                    isOwned = card.id in state.ownedCardIds,
                                    isWishlisted = wishlistViewModel.isCardWishlisted(card.id),
                                    canViewPrices = premiumManager.canViewPrices(),
                                    onClick = { selectedCard = card },
                                    onWishlistClick = {
                                        if (wishlistViewModel.isCardWishlisted(card.id)) {
                                            wishlistViewModel.removeCardFromAllWishlists(card.id)
                                        } else {
                                            val wishlists = wishlistViewModel.wishlists
                                            when {
                                                wishlists.isEmpty() -> createDialogCard = card
                                                wishlists.size == 1 && !isPremium -> wishlistViewModel.addCardToWishlist(
                                                    wishlists.first().id,
                                                    card.id
                                                )
                                                else -> pickerCard = card
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    item(span = { GridItemSpan(3) }) { Spacer(modifier = Modifier.height(40.dp)) }
                }

                // Selection bottom bar
                if (isSelectionMode && selectedCardIds.isNotEmpty()) {
                    SelectionBottomBar(
                        selectedCount = selectedCardIds.size,
                        selectedVariant = selectionVariant,
                        onVariantChange = { selectionVariant = it },
                        onAddAll = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val cards = sortedCards.filter { it.id in selectedCardIds }
                            viewModel.addMultipleCards(cards, selectionVariant)
                            isSelectionMode = false
                            selectedCardIds = emptySet()
                        },
                        onCancel = {
                            isSelectionMode = false
                            selectedCardIds = emptySet()
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
                } // close else (have data)
            } // close outer Box
        }
    }
}

@Composable
fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (isSelected) TextWhite else TextMuted,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) BlueCard.copy(alpha = 0.5f) else DarkCard)
            .border(1.dp, if (isSelected) BlueCard else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
fun ShimmerCardPlaceholder(modifier: Modifier = Modifier) {
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val alpha by shimmer.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = alpha))
    )
}

@Composable
fun SelectionBottomBar(
    selectedCount: Int,
    selectedVariant: String,
    onVariantChange: (String) -> Unit,
    onAddAll: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val variants = CardOptions.DEFAULT_VARIANTS

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, DarkSurface.copy(alpha = 0.95f), DarkSurface)
                )
            )
            .padding(top = 16.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Cancel
        IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted)
        }

        // Variant pills
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            variants.forEach { variant ->
                val isActive = variant == selectedVariant
                Text(
                    text = when (variant) {
                        "Normal" -> "Norm"
                        "Reverse" -> "Rev"
                        else -> variant
                    },
                    color = if (isActive) TextWhite else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isActive) BlueCard.copy(alpha = 0.7f) else DarkCard)
                        .border(
                            1.dp,
                            if (isActive) BlueCard else Color.Transparent,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onVariantChange(variant) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Add all button
        Button(
            onClick = onAddAll,
            colors = ButtonDefaults.buttonColors(containerColor = GreenCard),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("$selectedCount", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
fun SetInfoHeader(
    logoUrl: String,
    ownedCount: Int,
    displayTotal: Int,
    completionPercent: Int,
    rarityCounts: Map<RarityInfo, Pair<Int, Int>>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(DarkCard, DarkSurface)))
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (logoUrl.isNotBlank()) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(1f)
                        .height(55.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, GreenCard.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${completionPercent}%",
                        color = GreenCard,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$ownedCount/$displayTotal",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1A1A30))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(completionPercent / 100f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
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

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            rarityCounts.forEach { (info, counts) ->
                if (counts.second > 0) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = info.emoji,
                            color = info.color,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${counts.first}/${counts.second}",
                            color = if (counts.first == counts.second) GreenCard else TextWhite.copy(
                                alpha = 0.8f
                            ),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RarityFilterChip(label: String, isSelected: Boolean, color: Color = BlueCard, onClick: () -> Unit) {
    Text(label, maxLines = 1, color = if (isSelected) TextWhite else TextMuted,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) color.copy(alpha = 0.5f) else DarkCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TcgCardCompactItem(
    card: TcgCard,
    isOwned: Boolean,
    isWishlisted: Boolean,
    canViewPrices: Boolean,
    isAdding: Boolean = false,
    isPopupOpen: Boolean = false,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onQuickAddClick: () -> Unit,
    onWishlistClick: () -> Unit,
    onVariantSelected: (String) -> Unit = {}
) {
    val variantOptions = remember(card.tcgplayer?.prices?.keys, card.rarity) {
        CardOptions.getVariantsForCard(card.tcgplayer?.prices?.keys ?: emptySet(), card.rarity)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(10.dp))
            .then(
                when {
                    isAdding -> Modifier.border(2.dp, BlueCard.copy(alpha = 0.95f), RoundedCornerShape(10.dp))
                    isSelected -> Modifier.border(2.dp, BlueCard, RoundedCornerShape(10.dp))
                    isOwned -> Modifier.border(2.dp, GreenCard.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    else -> Modifier
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            if (card.images.small.isNotBlank()) {
                AsyncImage(model = card.images.small, contentDescription = card.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No image", color = TextMuted, fontSize = 10.sp)
                }
            }

            if (!isOwned && !isSelected && !isAdding) Box(modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)))

            if (isSelected) Box(modifier = Modifier
                .fillMaxSize()
                .background(BlueCard.copy(alpha = 0.15f)))

            if (isAdding && !isSelected) Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BlueCard.copy(alpha = 0.18f))
            )

            // Selection checkbox (top-left)
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) BlueCard else Color.Black.copy(alpha = 0.5f))
                        .border(1.5.dp, if (isSelected) BlueCard else Color.White.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
            }

            // Owned badge (top-right)
            if (isOwned && !isSelectionMode) {
                Box(modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(GreenCard), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }

            // Bottom area - Name only (price moved outside)
            Column(modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                if (isPopupOpen) {
                    // Inline variant pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        variantOptions.forEach { variant ->
                            val label = when (variant) {
                                "Normal" -> "Norm"
                                "Reverse" -> "Rev"
                                "1st Edition Holo" -> "1st H"
                                "1st Edition" -> "1st"
                                "Unlimited Holo" -> "Unl H"
                                else -> variant.take(5)
                            }
                            Text(
                                text = label,
                                color = TextWhite,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BlueCard.copy(alpha = 0.7f))
                                    .clickable { onVariantSelected(variant) }
                                    .padding(vertical = 6.dp)
                            )
                        }
                        Icon(
                            Icons.Default.Close, null,
                            tint = TextMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .clickable { onQuickAddClick() }
                        )
                    }
                } else {
                    // Card name + price + actions inside card
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isSelectionMode) {
                            Text(
                                card.name,
                                color = TextWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            val heartColor by animateColorAsState(
                                targetValue = if (isWishlisted) RedCard else Color.White.copy(alpha = 0.92f),
                                label = "wishlistHeartColor"
                            )
                            val heartScale by animateFloatAsState(
                                targetValue = if (isWishlisted) 1.08f else 1f,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                label = "wishlistHeartScale"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = card.name,
                                    color = TextWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (canViewPrices) {
                                        val price = resolveDisplayPrice(card)
                                        if (price != null && price > 0) {
                                            Text(
                                                text = formatPriceEur(price),
                                                color = GreenCard,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .border(1.dp, GreenCard.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                                                    .background(DarkCard)
                                                    .padding(horizontal = 7.dp, vertical = 1.dp)
                                            )
                                        } else {
                                            Text(
                                                text = if (AppLocale.isItalian) "Prezzo N/D" else "Price N/A",
                                                color = TextMuted,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    } else {
                                        Text("🔒", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(DarkSurface.copy(alpha = 0.78f))
                                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                                .clickable { onWishlistClick() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isWishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = AppLocale.wishlistTitle,
                                                tint = heartColor,
                                                modifier = Modifier.size((13.dp * heartScale))
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        isAdding -> BlueCard.copy(alpha = 0.9f)
                                                        isOwned -> Color.Transparent
                                                        else -> DarkSurface.copy(alpha = 0.8f)
                                                    }
                                                )
                                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                                .clickable { onQuickAddClick() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isAdding) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(12.dp),
                                                    color = Color.White,
                                                    strokeWidth = 1.5.dp
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Add,
                                                    null,
                                                    tint = if (isOwned) TextMuted.copy(alpha = 0.5f) else Color.White,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TcgCardListRow(
    card: TcgCard,
    isOwned: Boolean,
    isWishlisted: Boolean,
    canViewPrices: Boolean,
    onClick: () -> Unit,
    onWishlistClick: () -> Unit
) {
    val rarityInfo = RarityUtils.getRarityInfo(card.rarity)
    Row(modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(if (isOwned) DarkCard else DarkCard.copy(alpha = 0.5f))
        .clickable(onClick = onClick)
        .padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier
            .width(45.dp)
            .height(63.dp)
            .clip(RoundedCornerShape(6.dp))) {
            if (card.images.small.isNotBlank()) {
                AsyncImage(model = card.images.small, contentDescription = card.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No image", color = TextMuted, fontSize = 9.sp, textAlign = TextAlign.Center)
                }
            }
            if (!isOwned) Box(modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rarityInfo.emoji, color = rarityInfo.color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Text(card.name, color = if (isOwned) TextWhite else TextMuted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("#${card.number} · ${AppLocale.translateRarity(card.rarity ?: "")}", color = TextMuted, fontSize = 11.sp)
        }
        if (canViewPrices) {
            val price = resolveDisplayPrice(card)
            if (price != null && price > 0) {
                Text(
                    text = formatPriceEur(price),
                    color = GreenCard,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, GreenCard.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                        .background(DarkCard)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else {
                Text(
                    text = if (AppLocale.isItalian) "Prezzo N/D" else "Price N/A",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Text("🔒 Premium", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Icon(
            imageVector = if (isWishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = AppLocale.wishlistTitle,
            tint = if (isWishlisted) RedCard else TextMuted,
            modifier = Modifier
                .size(20.dp)
                .clickable { onWishlistClick() }
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (isOwned) GreenCard else Color.Transparent)
            .then(
                if (!isOwned) Modifier.border(
                    1.5.dp,
                    TextMuted.copy(alpha = 0.3f),
                    CircleShape
                ) else Modifier
            ), contentAlignment = Alignment.Center) {
            if (isOwned) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun WishlistPickerDialog(
    wishlists: List<Wishlist>,
    canCreateNew: Boolean,
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit,
    onConfirmWishlist: (String) -> Unit
) {
    var selectedWishlistId by remember(wishlists) { mutableStateOf(wishlists.firstOrNull()?.id.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = AppLocale.wishlistAddToList,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(AppLocale.wishlistChooseList, color = TextMuted, fontSize = 13.sp)

                wishlists.forEach { wishlist ->
                    val selected = selectedWishlistId == wishlist.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) BlueCard.copy(alpha = 0.22f) else DarkCard)
                            .border(
                                1.dp,
                                if (selected) BlueCard else TextMuted.copy(alpha = 0.2f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedWishlistId = wishlist.id }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (selected) BlueCard else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = wishlist.name,
                            color = TextWhite,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (selectedWishlistId.isNotBlank()) onConfirmWishlist(selectedWishlistId) },
                enabled = selectedWishlistId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BlueCard)
            ) {
                Text(AppLocale.addCard, color = TextWhite)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (canCreateNew) {
                    TextButton(onClick = onCreateNew) {
                        Text(AppLocale.wishlistCreateNewList, color = PurpleCard)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(AppLocale.cancel, color = TextMuted)
                }
            }
        }
    )
}
