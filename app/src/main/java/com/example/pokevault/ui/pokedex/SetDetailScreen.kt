package com.example.pokevault.ui.pokedex

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.pokevault.data.model.CardOptions
import com.example.pokevault.data.remote.TcgCard
import com.example.pokevault.ui.theme.*
import com.example.pokevault.viewmodel.SetDetailViewModel
import kotlin.math.cos
import kotlin.math.sin

// ══════════════════════════════════════
// RARITY — usa direttamente il valore API, NO raggruppamento forzato
// Ogni rarità unica dell'API ha la sua riga nel breakdown
// ══════════════════════════════════════

data class RarityInfo(
    val drawIcon: DrawScope.(Color) -> Unit,
    val color: Color,
    val label: String,
    val sortOrder: Int
)

// Drawing functions
private fun drCircle(): DrawScope.(Color) -> Unit = { c -> drawCircle(color = c, radius = size.minDimension * 0.4f) }
private fun drDiamond(): DrawScope.(Color) -> Unit = { c ->
    val cx = size.width / 2; val cy = size.height / 2; val r = size.minDimension * 0.4f
    drawPath(Path().apply { moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close() }, c)
}
private fun drStar5(filled: Boolean): DrawScope.(Color) -> Unit = { c ->
    val cx = size.width / 2; val cy = size.height / 2; val o = size.minDimension * 0.42f; val i = o * 0.4f
    val p = Path().apply {
        for (n in 0 until 5) { val oa = Math.toRadians(-90.0 + n * 72.0); val ia = Math.toRadians(-54.0 + n * 72.0)
            if (n == 0) moveTo(cx + o * cos(oa).toFloat(), cy + o * sin(oa).toFloat()) else lineTo(cx + o * cos(oa).toFloat(), cy + o * sin(oa).toFloat())
            lineTo(cx + i * cos(ia).toFloat(), cy + i * sin(ia).toFloat()) }; close() }
    if (filled) drawPath(p, c) else drawPath(p, c, style = Stroke(width = size.minDimension * 0.08f))
}
private fun drDoubleStar5(filled: Boolean): DrawScope.(Color) -> Unit = { c ->
    val s = size.minDimension * 0.32f; val si = s * 0.4f
    listOf(size.width * 0.32f, size.width * 0.68f).forEach { cx ->
        val cy = size.height / 2; val p = Path().apply {
            for (n in 0 until 5) { val oa = Math.toRadians(-90.0 + n * 72.0); val ia = Math.toRadians(-54.0 + n * 72.0)
                if (n == 0) moveTo(cx + s * cos(oa).toFloat(), cy + s * sin(oa).toFloat()) else lineTo(cx + s * cos(oa).toFloat(), cy + s * sin(oa).toFloat())
                lineTo(cx + si * cos(ia).toFloat(), cy + si * sin(ia).toFloat()) }; close() }
        if (filled) drawPath(p, c) else { drawPath(p, c.copy(alpha = 0.3f)); drawPath(p, c, style = Stroke(width = size.minDimension * 0.06f)) }
    }
}
private fun drStar4(filled: Boolean, pattern: Boolean = false): DrawScope.(Color) -> Unit = { c ->
    val cx = size.width / 2; val cy = size.height / 2; val r = size.minDimension * 0.42f; val ri = r * 0.28f
    val p = Path().apply {
        for (n in 0 until 4) { val oa = Math.toRadians(-90.0 + n * 90.0); val ia = Math.toRadians(-45.0 + n * 90.0)
            if (n == 0) moveTo(cx + r * cos(oa).toFloat(), cy + r * sin(oa).toFloat()) else lineTo(cx + r * cos(oa).toFloat(), cy + r * sin(oa).toFloat())
            lineTo(cx + ri * cos(ia).toFloat(), cy + ri * sin(ia).toFloat()) }; close() }
    if (filled) { drawPath(p, c); if (pattern) drawPath(p, Color.White.copy(alpha = 0.3f), style = Stroke(width = size.minDimension * 0.06f)) }
    else { drawPath(p, c.copy(alpha = 0.25f)); drawPath(p, c, style = Stroke(width = size.minDimension * 0.07f)) }
}

// Mappa 1:1 con il valore API — NESSUN raggruppamento
fun getRarityInfo(rarity: String?): RarityInfo {
    val r = rarity?.trim() ?: ""
    return when {
        r.equals("Common", true) -> RarityInfo(drCircle(), Color(0xFFE0E0E0), "Common", 0)
        r.equals("Uncommon", true) -> RarityInfo(drDiamond(), Color(0xFFE0E0E0), "Uncommon", 1)
        r.equals("Rare", true) -> RarityInfo(drStar5(true), Color(0xFFD4D4D4), "Rare", 2)
        r.equals("Rare Holo", true) -> RarityInfo(drStar5(true), Color(0xFFD4D4D4), "Rare Holo", 3)
        r.equals("Double Rare", true) -> RarityInfo(drDoubleStar5(false), Color(0xFFD4AA50), "Double Rare", 4)
        r.equals("Rare Holo EX", true) -> RarityInfo(drDoubleStar5(false), Color(0xFFD4AA50), "Rare Holo EX", 4)
        r.equals("Rare Holo GX", true) -> RarityInfo(drDoubleStar5(false), Color(0xFFD4AA50), "Rare Holo GX", 4)
        r.equals("Rare Holo V", true) -> RarityInfo(drDoubleStar5(false), Color(0xFFD4AA50), "Rare Holo V", 4)
        r.equals("Rare Holo VMAX", true) -> RarityInfo(drDoubleStar5(false), Color(0xFFD4AA50), "Rare Holo VMAX", 5)
        r.equals("Rare Holo VSTAR", true) -> RarityInfo(drDoubleStar5(false), Color(0xFFD4AA50), "Rare Holo VSTAR", 5)
        r.equals("Rare Holo LV.X", true) -> RarityInfo(drDoubleStar5(false), Color(0xFFD4AA50), "Rare Holo LV.X", 5)
        r.equals("Illustration Rare", true) -> RarityInfo(drStar4(false), Color(0xFFD4AA50), "Illustration Rare", 6)
        r.equals("Rare Ultra", true) || r.equals("Ultra Rare", true) -> RarityInfo(drDoubleStar5(true), Color(0xFFFFD700), "Ultra Rare", 7)
        r.equals("Rare Rainbow", true) -> RarityInfo(drDoubleStar5(true), Color(0xFFFFD700), "Rare Rainbow", 7)
        r.equals("Rare Shiny GX", true) -> RarityInfo(drDoubleStar5(true), Color(0xFFFFD700), "Rare Shiny GX", 7)
        r.equals("Special Illustration Rare", true) -> RarityInfo(drDoubleStar5(false), Color(0xFFCCA040), "Special Illustration Rare", 8)
        r.equals("Hyper Rare", true) -> RarityInfo(drStar4(true, true), Color(0xFFFFD700), "Hyper Rare", 9)
        r.equals("Rare Secret", true) -> RarityInfo(drStar4(true, true), Color(0xFFFFD700), "Rare Secret", 9)
        r.equals("Special Art Rare", true) -> RarityInfo(drStar4(true, true), Color(0xFFFFD700), "Special Art Rare", 9)
        r.equals("ACE SPEC Rare", true) || r.equals("Rare ACE", true) -> RarityInfo(drStar4(true), Color(0xFFFFD700), "ACE SPEC Rare", 10)
        r.equals("Amazing Rare", true) -> RarityInfo(drStar5(true), Color(0xFF38BDF8), "Amazing Rare", 3)
        r.equals("Rare BREAK", true) -> RarityInfo(drStar5(true), Color(0xFFD4D4D4), "Rare BREAK", 3)
        r.equals("Rare Prime", true) -> RarityInfo(drStar5(true), Color(0xFFD4D4D4), "Rare Prime", 3)
        r.equals("Rare Prism Star", true) -> RarityInfo(drStar5(true), Color(0xFFD4D4D4), "Rare Prism Star", 3)
        r.equals("Rare Shining", true) -> RarityInfo(drStar5(true), Color(0xFFD4D4D4), "Rare Shining", 3)
        r.equals("Rare Shiny", true) -> RarityInfo(drStar5(true), Color(0xFFD4D4D4), "Rare Shiny", 3)
        r.equals("Rare Holo Star", true) -> RarityInfo(drStar5(true), Color(0xFFD4D4D4), "Rare Holo Star", 3)
        r.equals("LEGEND", true) -> RarityInfo(drStar5(true), Color(0xFFFFD700), "LEGEND", 7)
        r.equals("Promo", true) -> RarityInfo(drStar5(true), Color(0xFF38BDF8), "Promo", 2)
        r.isEmpty() -> RarityInfo(drCircle(), Color(0xFF555555), "Senza rarità", 99)
        else -> RarityInfo(drCircle(), Color(0xFF888888), r, 50)
    }
}

@Composable
fun RarityIcon(rarity: String?, size: Int = 18, modifier: Modifier = Modifier) {
    val info = getRarityInfo(rarity)
    Canvas(modifier = modifier.size(size.dp)) { info.drawIcon(this, info.color) }
}

fun formatReleaseDate(date: String): String {
    return try { val p = date.split("/"); if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else date } catch (_: Exception) { date }
}

fun getCardPrice(card: TcgCard): Double? {
    return card.cardmarket?.prices?.averageSellPrice ?: card.tcgplayer?.prices?.values?.firstOrNull()?.market
}

// ══════════════════════════════════════
// LONG PRESS POPUP — varianti fluttuanti
// ══════════════════════════════════════

@Composable
fun CardWithLongPress(
    card: TcgCard,
    isOwned: Boolean,
    onCardClick: () -> Unit,
    onQuickAdd: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showVariantPopup by remember { mutableStateOf(false) }
    var selectedVariant by remember { mutableStateOf<String?>(null) }

    // Varianti disponibili per questa carta
    val availableVariants = remember(card) {
        val keys = card.tcgplayer?.prices?.keys ?: emptySet()
        CardOptions.getVariantsFromApi(keys)
    }

    // Animazione scala per long press
    val scale by animateFloatAsState(
        targetValue = if (showVariantPopup) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "scale"
    )

    // Shimmer per conferma
    var showShimmer by remember { mutableStateOf(false) }
    val shimmerAlpha by animateFloatAsState(
        targetValue = if (showShimmer) 0.6f else 0f,
        animationSpec = tween(400),
        label = "shimmer",
        finishedListener = { showShimmer = false }
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Column {
            // ── Carta pulita ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .scale(scale)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (isOwned) Modifier.border(2.dp, GreenCard.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        else Modifier
                    )
                    .pointerInput(card.id) {
                        detectTapGestures(
                            onTap = { onCardClick() },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showVariantPopup = true
                            }
                        )
                    }
            ) {
                AsyncImage(
                    model = card.images.small,
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Scurisci se non posseduta
                if (!isOwned) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                }

                // Shimmer effect conferma
                if (shimmerAlpha > 0) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = shimmerAlpha * 0.5f),
                                    Color.White.copy(alpha = shimmerAlpha),
                                    Color.White.copy(alpha = shimmerAlpha * 0.5f)
                                )
                            )
                        )
                    )
                }

                // Badge owned
                if (isOwned) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                            .size(18.dp).clip(CircleShape).background(GreenCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                }
            }

            // ── Prezzo sotto la carta (pulito) ──
            val price = getCardPrice(card)
            if (price != null && price > 0) {
                Text(
                    text = "${"%.2f".format(price)} €",
                    color = GreenCard,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                )
            }
        }

        // ── Popup varianti fluttuante ──
        AnimatedVisibility(
            visible = showVariantPopup,
            enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-48).dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, TextMuted.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                availableVariants.forEach { variant ->
                    val isSelected = selectedVariant == variant
                    val variantColor = when {
                        variant.contains("Holo", true) -> Color(0xFFFFD700)
                        variant.contains("Reverse", true) -> Color(0xFF8B5CF6)
                        variant.contains("1st", true) -> Color(0xFFEF4444)
                        else -> BlueCard
                    }
                    val shortLabel = when {
                        variant.contains("Reverse", true) -> "REV"
                        variant.contains("Holofoil", true) -> "HOLO"
                        variant.contains("1st Edition Holo", true) -> "1st H"
                        variant.contains("1st Edition", true) -> "1st"
                        else -> "NRM"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) variantColor.copy(alpha = 0.3f)
                                else Color.Transparent
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) variantColor else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                selectedVariant = variant
                                onQuickAdd(variant)
                                showVariantPopup = false
                                selectedVariant = null
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = shortLabel,
                                color = if (isSelected) variantColor else TextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            // Prezzo variante
                            val variantKey = CardOptions.getVariantApiKey(variant)
                            val vPrice = card.tcgplayer?.prices?.get(variantKey)?.market
                            if (vPrice != null) {
                                Text(
                                    text = "${"%.2f".format(vPrice)}€",
                                    color = TextMuted,
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                }

                // Bottone chiudi
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                        .background(TextMuted.copy(alpha = 0.15f))
                        .clickable { showVariantPopup = false },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ══════════════════════════════════════
// MAIN SCREEN
// ══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetDetailScreen(
    setId: String, setName: String, onBack: () -> Unit,
    viewModel: SetDetailViewModel = viewModel()
) {
    val state = viewModel.uiState
    var selectedCard by remember { mutableStateOf<TcgCard?>(null) }
    var selectedRarityFilter by remember { mutableStateOf<String?>(null) }
    var selectedTypeFilter by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(setId) { viewModel.loadSet(setId) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.successMessage, state.errorMessage) {
        val msg = state.successMessage ?: state.errorMessage
        if (msg != null) { snackbarHostState.showSnackbar(msg); viewModel.clearMessages() }
    }

    if (selectedCard != null) {
        CardDetailBottomSheet(
            card = selectedCard!!, isOwned = selectedCard!!.id in state.ownedCardIds,
            isLoading = state.isAddingCard == selectedCard!!.id,
            onAddCard = { v, q, c, l -> viewModel.addCardWithDetails(selectedCard!!, v, q, c, l) },
            onRemoveCard = { viewModel.removeCard(selectedCard!!); selectedCard = null },
            onDismiss = { selectedCard = null }
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, containerColor = DarkBackground) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            TopAppBar(
                title = {
                    Column {
                        Text(state.set?.name ?: setName, fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!state.isLoading && state.set != null)
                            Text("Data di uscita: ${formatReleaseDate(state.set.releaseDate)}", color = TextMuted, fontSize = 12.sp)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextWhite) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PokeballLoadingAnimation("Caricamento carte...") }
            } else {
                val filteredCards = state.cards.filter { card ->
                    val mR = selectedRarityFilter == null || card.rarity?.trim() == selectedRarityFilter
                    val mT = selectedTypeFilter == null || card.types?.contains(selectedTypeFilter) == true
                    mR && mT
                }.sortedBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }

                // Breakdown: raggruppato per valore API esatto
                val rarityCounts = state.cards
                    .groupBy { it.rarity?.trim() ?: "" }
                    .map { (rawRarity, cards) ->
                        getRarityInfo(rawRarity) to Pair(cards.count { it.id in state.ownedCardIds }, cards.size)
                    }
                    .sortedBy { it.first.sortOrder }
                    .toMap()

                val availableTypes = state.cards.flatMap { it.types ?: emptyList() }.distinct().sorted()

                // Rarità per filtri (ordinate)
                val sortedRarities = state.cards
                    .mapNotNull { it.rarity?.trim() }
                    .distinct()
                    .sortedBy { getRarityInfo(it).sortOrder }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(span = { GridItemSpan(3) }) {
                        SetInfoHeader(state.set?.images?.logo ?: "", state.ownedCount, state.displayTotal, state.completionPercent, rarityCounts)
                    }

                    // Filtri rarità
                    item(span = { GridItemSpan(3) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            item { FilterPill("Tutte", null, selectedRarityFilter == null) { selectedRarityFilter = null } }
                            items(sortedRarities) { rarity ->
                                val info = getRarityInfo(rarity)
                                FilterPillWithIcon(rarity, info, selectedRarityFilter == rarity) { selectedRarityFilter = rarity }
                            }
                        }
                    }

                    // Filtri tipo
                    if (availableTypes.isNotEmpty()) {
                        item(span = { GridItemSpan(3) }) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                                item { FilterPill("Tutti i tipi", null, selectedTypeFilter == null) { selectedTypeFilter = null } }
                                items(availableTypes) { type ->
                                    TypePill(type, selectedTypeFilter == type) { selectedTypeFilter = type }
                                }
                            }
                        }
                    }

                    // Vista tabs
                    item(span = { GridItemSpan(3) }) {
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(DarkCard), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("Carte" to "grid", "Lista" to "list", "Griglia" to "large").forEach { (l, m) ->
                                Text(l, color = if (state.viewMode == m) TextWhite else TextMuted,
                                    fontWeight = if (state.viewMode == m) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 13.sp, textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f).clickable { viewModel.setViewMode(m) }
                                        .background(if (state.viewMode == m) BlueCard.copy(alpha = 0.3f) else Color.Transparent).padding(vertical = 10.dp))
                            }
                        }
                    }

                    // ── Carte ──
                    when (state.viewMode) {
                        "grid" -> items(filteredCards, key = { it.id }) { card ->
                            CardWithLongPress(
                                card = card,
                                isOwned = card.id in state.ownedCardIds,
                                onCardClick = { selectedCard = card },
                                onQuickAdd = { variant -> viewModel.addCardWithDetails(card, variant, 1, "Mint", "🇮🇹 Italiano") }
                            )
                        }
                        "list" -> items(filteredCards, key = { it.id }, span = { GridItemSpan(3) }) { card ->
                            CardListRow(card, card.id in state.ownedCardIds, { selectedCard = card })
                        }
                        "large" -> items(filteredCards, key = { it.id }) { card ->
                            LargeCardItem(card, card.id in state.ownedCardIds) { selectedCard = card }
                        }
                    }
                    item(span = { GridItemSpan(3) }) { Spacer(modifier = Modifier.height(40.dp)) }
                }
            }
        }
    }
}

// ══════════════════════════════════════
// LIST + LARGE ITEMS
// ══════════════════════════════════════

@Composable
fun CardListRow(card: TcgCard, isOwned: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (isOwned) DarkCard else DarkCard.copy(alpha = 0.5f))
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.width(42.dp).height(58.dp).clip(RoundedCornerShape(6.dp))) {
            AsyncImage(model = card.images.small, contentDescription = card.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (!isOwned) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        }
        RarityIcon(rarity = card.rarity, size = 16)
        Column(modifier = Modifier.weight(1f)) {
            Text(card.name, color = if (isOwned) TextWhite else TextMuted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("#${card.number} · ${card.rarity ?: ""}", color = TextMuted, fontSize = 10.sp)
        }
        val price = getCardPrice(card)
        if (price != null && price > 0) Text("${"%.2f".format(price)} €", color = GreenCard, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (isOwned) {
            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(GreenCard), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun LargeCardItem(card: TcgCard, isOwned: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(12.dp))
        .then(if (isOwned) Modifier.border(2.5.dp, GreenCard, RoundedCornerShape(12.dp)) else Modifier).clickable(onClick = onClick)) {
        AsyncImage(model = card.images.large, contentDescription = card.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (!isOwned) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        if (isOwned) Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(GreenCard), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

// ══════════════════════════════════════
// HEADER + PILLS
// ══════════════════════════════════════

@Composable
fun SetInfoHeader(logoUrl: String, ownedCount: Int, displayTotal: Int, completionPercent: Int, rarityCounts: Map<RarityInfo, Pair<Int, Int>>) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(listOf(DarkCard, DarkSurface))).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (logoUrl.isNotBlank()) AsyncImage(model = logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.weight(1f).height(55.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, GreenCard.copy(alpha = 0.4f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("${completionPercent}%", color = GreenCard, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("$ownedCount/$displayTotal", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1A1A30))) {
            Box(modifier = Modifier.fillMaxWidth(completionPercent / 100f).height(8.dp).clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899)))))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            rarityCounts.forEach { (info, counts) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 1.dp)) {
                    Canvas(modifier = Modifier.size(20.dp)) { info.drawIcon(this, info.color) }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row {
                        Text("${counts.first}", color = if (counts.first == counts.second && counts.second > 0) GreenCard else TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("/${counts.second}", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FilterPill(label: String, value: String?, isSelected: Boolean, onClick: () -> Unit) {
    Text(label, color = if (isSelected) TextWhite else TextMuted, fontSize = 11.sp, maxLines = 1,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (isSelected) BlueCard.copy(alpha = 0.5f) else DarkCard)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp))
}

@Composable
fun FilterPillWithIcon(rarity: String, info: RarityInfo, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) BlueCard.copy(alpha = 0.5f) else DarkCard)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(modifier = Modifier.size(14.dp)) { info.drawIcon(this, info.color) }
        Text(rarity, color = if (isSelected) TextWhite else TextMuted, fontSize = 11.sp, maxLines = 1,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun TypePill(type: String, isSelected: Boolean, onClick: () -> Unit) {
    val tc = getTypeColorForTcg(type)
    Row(
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) tc.copy(alpha = 0.4f) else DarkCard)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(tc))
        Text(type, color = if (isSelected) TextWhite else TextMuted, fontSize = 11.sp, maxLines = 1,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
