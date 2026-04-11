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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.viewmodel.SetDetailViewModel
import com.emabuia.pokevault.util.RarityUtils
import com.emabuia.pokevault.util.RarityInfo
import com.emabuia.pokevault.util.AppLocale

fun formatReleaseDate(date: String): String {
    return try {
        val parts = date.split("/")
        if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else date
    } catch (_: Exception) { date }
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

// Pre-resolved search context to avoid iterating maps per card
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

fun matchesItalianSearch(card: TcgCard, query: String, translatedQuery: String = ""): Boolean {
    return matchesSearchContext(card, buildSearchContext(query, translatedQuery))
}

fun matchesSearchContext(card: TcgCard, ctx: ItalianSearchContext): Boolean {
    if (ctx.q.isEmpty()) return true

    // Name match (English + translated)
    if (card.name.contains(ctx.q, ignoreCase = true)) return true
    if (ctx.translatedQuery.isNotBlank() && card.name.contains(ctx.translatedQuery, ignoreCase = true)) return true

    // Card number match
    if (card.number == ctx.q || card.number.contains(ctx.q)) return true

    // Pre-resolved Italian type matches
    if (ctx.matchedTypes.isNotEmpty()) {
        if (card.types?.any { t -> t.lowercase() in ctx.matchedTypes } == true) return true
    }

    // Pre-resolved Italian category matches
    if (ctx.matchedCategories.isNotEmpty()) {
        val superLower = card.supertype.lowercase()
        if (ctx.matchedCategories.any { superLower.contains(it) }) return true
        if (card.subtypes?.any { s -> ctx.matchedCategories.any { s.contains(it, ignoreCase = true) } } == true) return true
    }

    // Pre-resolved Italian rarity matches
    if (ctx.matchedRarities.isNotEmpty()) {
        val rarityLower = card.rarity?.lowercase()
        if (rarityLower != null && ctx.matchedRarities.any { rarityLower.contains(it) }) return true
    }

    // Direct English matches
    if (card.rarity?.contains(ctx.q, ignoreCase = true) == true) return true
    if (card.supertype.contains(ctx.q, ignoreCase = true)) return true
    if (card.subtypes?.any { it.contains(ctx.q, ignoreCase = true) } == true) return true

    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetDetailScreen(
    setId: String, setName: String, onBack: () -> Unit,
    viewModel: SetDetailViewModel = viewModel()
) {
    val state = viewModel.uiState
    val haptic = LocalHapticFeedback.current
    var selectedCard by remember { mutableStateOf<TcgCard?>(null) }
    var quickAddCard by remember { mutableStateOf<TcgCard?>(null) }
    var selectedRarityFilter by remember { mutableStateOf<String?>(null) }

    // Selection mode state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedCardIds by remember { mutableStateOf(setOf<String>()) }
    var selectionVariant by remember { mutableStateOf("Normal") }
    val gridState = rememberLazyGridState()

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedCardIds = emptySet()
    }

    // Exit quick add popup when entering selection mode
    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode) quickAddCard = null
    }

    LaunchedEffect(setId) { viewModel.loadSet(setId) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.successMessage, state.errorMessage) {
        val msg = state.successMessage ?: state.errorMessage
        if (msg != null) { snackbarHostState.showSnackbar(msg); viewModel.clearMessages() }
    }

    val sortedCards = remember(state.cards, state.ownedCardIds, state.searchQuery, state.translatedQuery, state.showOnlyMissing, selectedRarityFilter) {
        val searchCtx = buildSearchContext(state.searchQuery, state.translatedQuery)
        state.cards.filter { card ->
            val matchesRarity = selectedRarityFilter == null || card.rarity == selectedRarityFilter
            val matchesSearch = matchesSearchContext(card, searchCtx)
            val matchesMissing = !state.showOnlyMissing || card.id !in state.ownedCardIds
            matchesRarity && matchesSearch && matchesMissing
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
            onAddCard = { v, q, c, l -> 
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.addCardWithDetails(selectedCard!!, v, q, c, l) 
            },
            onRemoveCard = { viewModel.removeCard(selectedCard!!); selectedCard = null },
            onDismiss = { selectedCard = null }
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
                .statusBarsPadding()
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(state.set?.name ?: "", fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.set != null) {
                            Text("${if (AppLocale.isItalian) "Data di uscita" else "Release date"}: ${formatReleaseDate(state.set.releaseDate)}", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, AppLocale.back, tint = TextWhite) } },
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
                                    .background(if (state.showOnlyMissing) BlueCard.copy(alpha = 0.2f) else DarkCard)
                                    .border(
                                        1.dp,
                                        if (state.showOnlyMissing) BlueCard else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.toggleShowOnlyMissing() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (state.showOnlyMissing) Icons.Default.FilterAlt else Icons.Default.FilterAltOff,
                                        contentDescription = null,
                                        tint = if (state.showOnlyMissing) BlueCard else TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (AppLocale.isItalian) "Mancanti" else "Missing", color = if (state.showOnlyMissing) BlueCard else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    item(span = { GridItemSpan(3) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            item {
                                RarityFilterChip("${AppLocale.all} (${state.cards.size})", selectedRarityFilter == null) { selectedRarityFilter = null }
                            }
                            items(distinctRarities) { rarity ->
                                val info = RarityUtils.getRarityInfo(rarity)
                                val count = state.cards.count { it.rarity == rarity }
                                RarityFilterChip("${info.emoji} ${AppLocale.translateRarity(rarity)} ($count)", selectedRarityFilter == rarity, info.color) { selectedRarityFilter = rarity }
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
                                    onVariantSelected = { variant ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.addCardWithDetails(card, variant, 1, "Near Mint", "🇮🇹 Italiano")
                                        quickAddCard = null
                                    }
                                )
                            }
                            "list" -> items(sortedCards, key = { "${it.id}_${it.number}" }, span = { GridItemSpan(3) }) { card ->
                                TcgCardListRow(card, card.id in state.ownedCardIds) { selectedCard = card }
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

        Spacer(modifier = Modifier.height(10.dp))

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
    isAdding: Boolean = false,
    isPopupOpen: Boolean = false,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onQuickAddClick: () -> Unit,
    onVariantSelected: (String) -> Unit = {}
) {
    val variantOptions = remember(card.tcgplayer?.prices?.keys, card.rarity) {
        CardOptions.getVariantsForCard(card.tcgplayer?.prices?.keys ?: emptySet(), card.rarity)
    }

    Box(modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(0.72f)
        .clip(RoundedCornerShape(10.dp))
        .then(
            when {
                isSelected -> Modifier.border(2.dp, BlueCard, RoundedCornerShape(10.dp))
                isOwned -> Modifier.border(2.dp, GreenCard.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                else -> Modifier
            }
        )
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(model = card.images.small, contentDescription = card.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())

        if (!isOwned && !isSelected) Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)))

        if (isSelected) Box(modifier = Modifier
            .fillMaxSize()
            .background(BlueCard.copy(alpha = 0.15f)))

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

        // Bottom area
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
                // Normal name/price + add button
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(card.name, color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val price = card.cardmarket?.prices?.lowPrice
                            ?: card.cardmarket?.prices?.averageSellPrice
                            ?: card.tcgplayer?.prices?.values?.firstOrNull()?.market
                        if (price != null && price > 0) {
                            Text("${"%.2f".format(price)}€", color = GreenCard, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!isSelectionMode) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
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
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 1.5.dp)
                            } else {
                                Icon(Icons.Default.Add, null, tint = if (isOwned) TextMuted.copy(alpha = 0.5f) else Color.White, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TcgCardListRow(card: TcgCard, isOwned: Boolean, onClick: () -> Unit) {
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
            AsyncImage(model = card.images.small, contentDescription = card.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
        val price = card.cardmarket?.prices?.lowPrice ?: card.cardmarket?.prices?.averageSellPrice ?: card.tcgplayer?.prices?.values?.firstOrNull()?.market
        if (price != null && price > 0) Text("${"%.2f".format(price)}€", color = GreenCard, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
