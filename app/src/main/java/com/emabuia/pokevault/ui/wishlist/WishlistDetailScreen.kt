package com.emabuia.pokevault.ui.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ui.components.FillBar
import com.emabuia.pokevault.ui.components.LabSearchField
import com.emabuia.pokevault.ui.components.SkeletonBlock
import com.emabuia.pokevault.ui.components.SortChipRow
import com.emabuia.pokevault.ui.components.StatTile
import com.emabuia.pokevault.ui.components.formatEur
import com.emabuia.pokevault.ui.components.formatEurCompact
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.pokedex.CardDetailBottomSheet
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.CollectorLab
import com.emabuia.pokevault.util.ImageUrlUtils
import com.emabuia.pokevault.util.WishlistCardFilter
import com.emabuia.pokevault.util.WishlistCardSort
import com.emabuia.pokevault.util.WishlistLab
import com.emabuia.pokevault.util.WishlistRow
import com.emabuia.pokevault.util.minimumEurPriceOrZero
import com.emabuia.pokevault.viewmodel.SetDetailViewModel
import com.emabuia.pokevault.viewmodel.WishlistViewModel

/**
 * Il dettaglio di una wishlist.
 *
 * Prima era l'elenco delle carte e basta: nessun totale, nessun modo di
 * separare quelle gia' comprate da quelle che mancano, nessun ordine se non il
 * numero di carta. Qui in cima c'e' quanto costa finirla e quante ne restano, e
 * le carte si possono filtrare per stato, cercare e ordinare per prezzo — che e'
 * l'ordine con cui si decide cosa comprare per primo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistDetailScreen(
    wishlistId: String,
    onBack: () -> Unit,
    viewModel: WishlistViewModel = viewModel(),
    setDetailViewModel: SetDetailViewModel = viewModel()
) {
    val wishlist = viewModel.getWishlistById(wishlistId)
    val setDetailState = setDetailViewModel.uiState
    val snackbarHostState = remember { SnackbarHostState() }

    var removeCardId by remember { mutableStateOf<String?>(null) }
    var selectedCard by remember { mutableStateOf<TcgCard?>(null) }
    var showCleanupDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(WishlistCardSort.PRICE_DESC) }
    var filter by remember { mutableStateOf(WishlistCardFilter.ALL) }

    val ownedIds = viewModel.ownedCardIds
    val cards by remember(wishlistId) { derivedStateOf { viewModel.cardsOf(wishlistId) } }
    val isLoadingCards = viewModel.isLoadingCardsOf(wishlistId)
    val accent = wishlistAccentColor(wishlist?.resolvedAccentKey ?: "")

    val row = remember(wishlist, cards, ownedIds) {
        wishlist?.let { WishlistLab.row(it, viewModel.cardsById, ownedIds) }
    }
    val missingCards = remember(cards, ownedIds) { cards.filter { it.id !in ownedIds } }
    val visibleCards = remember(cards, query, sort, filter, ownedIds) {
        WishlistLab.sortCards(WishlistLab.filterCards(cards, query, filter, ownedIds), sort)
    }
    val affordable = remember(missingCards, wishlist?.budgetEur) {
        WishlistLab.affordableWithin(missingCards, wishlist?.budgetEur ?: 0.0)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (wishlist != null) {
                            WishlistBadge(
                                iconKey = wishlist.iconKey,
                                accentKey = wishlist.resolvedAccentKey,
                                size = 30.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column {
                            Text(
                                text = wishlist?.name ?: AppLocale.wishlistTitle,
                                color = AppColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (wishlist != null) {
                                Text(
                                    text = wishlistIconLabel(wishlist.iconKey),
                                    color = AppColors.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppLocale.back,
                            tint = AppColors.textPrimary
                        )
                    }
                },
                actions = {
                    if (row != null && row.owned > 0) {
                        IconButton(onClick = { showCleanupDialog = true }) {
                            Icon(
                                Icons.Default.PlaylistRemove,
                                contentDescription = AppLocale.wishlistCleanupAction,
                                tint = AppColors.green
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        }
    ) { padding ->
        when {
            wishlist == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(AppLocale.wishlistNotFound, color = AppColors.textMuted, fontSize = 14.sp)
                }
            }

            wishlist.cardIds.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        WishlistGlyph(
                            iconKey = wishlist.iconKey,
                            accent = accent,
                            size = 56.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            AppLocale.wishlistCardsEmpty,
                            color = AppColors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            AppLocale.wishlistCardsEmptySubtitle,
                            color = AppColors.textMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            cards.isEmpty() && isLoadingCards -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(5) { index ->
                        SkeletonBlock(
                            modifier = Modifier.fillMaxWidth().height(86.dp),
                            shape = RoundedCornerShape(14.dp),
                            index = index
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                ) {
                    if (row != null) {
                        item(key = "summary") {
                            WishlistDetailHeader(
                                row = row,
                                accent = accent,
                                affordable = affordable.size,
                                cheapest = CollectorLab.cheapestMissing(missingCards),
                                mostExpensive = CollectorLab.mostExpensiveMissing(missingCards)
                            )
                        }
                    }

                    item(key = "controls") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            LabSearchField(
                                value = query,
                                onValueChange = { query = it },
                                hint = AppLocale.wishlistCardSearchHint,
                                accent = accent
                            )
                            SortChipRow(
                                labels = listOf(
                                    AppLocale.wishlistFilterAll,
                                    AppLocale.wishlistFilterMissing,
                                    AppLocale.wishlistFilterOwned
                                ),
                                selectedIndex = filter.ordinal,
                                onSelect = { index -> filter = WishlistCardFilter.entries[index] },
                                accent = accent
                            )
                            SortChipRow(
                                labels = listOf(
                                    AppLocale.wishlistCardSortNumber,
                                    AppLocale.wishlistCardSortName,
                                    AppLocale.wishlistCardSortPriceDesc,
                                    AppLocale.wishlistCardSortPriceAsc,
                                    AppLocale.wishlistCardSortSet
                                ),
                                selectedIndex = sort.ordinal,
                                onSelect = { index -> sort = WishlistCardSort.entries[index] },
                                accent = accent
                            )
                        }
                    }

                    if (visibleCards.isEmpty()) {
                        item(key = "no-results") {
                            val allTaken = filter == WishlistCardFilter.MISSING && query.isBlank()
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (allTaken) {
                                        AppLocale.wishlistAllTakenTitle
                                    } else {
                                        AppLocale.wishlistNoCardResults
                                    },
                                    color = AppColors.textSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (allTaken) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        AppLocale.wishlistAllTakenSubtitle,
                                        color = AppColors.textMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    items(visibleCards, key = { it.id }) { card ->
                        WishlistCardRow(
                            card = card,
                            isOwned = card.id in ownedIds,
                            accent = accent,
                            onClick = { selectedCard = card },
                            onRemove = { removeCardId = card.id }
                        )
                    }

                    if (isLoadingCards) {
                        item(key = "loading-more") {
                            Text(
                                text = AppLocale.wishlistPartialTotal,
                                color = AppColors.textMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    selectedCard?.let { card ->
        CardDetailBottomSheet(
            card = card,
            isOwned = card.id in ownedIds,
            isLoading = setDetailState.isAddingCard == card.id,
            onAddCard = { variant, quantity, condition, language ->
                setDetailViewModel.addCardWithDetails(card, variant, quantity, condition, language)
            },
            onRemoveCard = {
                viewModel.removeCardFromWishlist(wishlistId, card.id)
                selectedCard = null
            },
            onDismiss = { selectedCard = null },
            cardList = visibleCards,
            onCardChange = { selectedCard = it }
        )
    }

    LaunchedEffect(setDetailState.successMessage, setDetailState.errorMessage) {
        val msg = setDetailState.successMessage ?: setDetailState.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            setDetailViewModel.clearMessages()
        }
    }

    LaunchedEffect(viewModel.successMessage, viewModel.errorMessage) {
        val msg = viewModel.successMessage ?: viewModel.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    val cardToRemove = cards.firstOrNull { it.id == removeCardId }
    if (cardToRemove != null) {
        AlertDialog(
            onDismissRequest = { removeCardId = null },
            containerColor = AppColors.surface,
            title = { Text(AppLocale.wishlistRemoveCardTitle, color = AppColors.textPrimary) },
            text = { Text(cardToRemove.name, color = AppColors.textSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeCardFromWishlist(wishlistId, cardToRemove.id)
                        removeCardId = null
                    }
                ) {
                    Text(AppLocale.delete, color = AppColors.red)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeCardId = null }) {
                    Text(AppLocale.cancel, color = AppColors.textMuted)
                }
            }
        )
    }

    if (showCleanupDialog && row != null) {
        AlertDialog(
            onDismissRequest = { showCleanupDialog = false },
            containerColor = AppColors.surface,
            title = { Text(AppLocale.wishlistCleanupTitle, color = AppColors.textPrimary) },
            text = {
                Text(AppLocale.wishlistCleanupMessage(row.owned), color = AppColors.textSecondary)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeOwnedCards(wishlistId)
                        showCleanupDialog = false
                    }
                ) {
                    Text(AppLocale.wishlistCleanupAction, color = AppColors.green)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupDialog = false }) {
                    Text(AppLocale.cancel, color = AppColors.textMuted)
                }
            }
        )
    }
}

/**
 * Il riassunto della lista.
 *
 * Il numero grande e' quanto manca da spendere, non quanto vale la lista: una
 * wishlist e' una spesa da fare, e il valore di quello che hai gia' comprato non
 * aiuta a decidere niente.
 */
@Composable
private fun WishlistDetailHeader(
    row: WishlistRow,
    accent: Color,
    affordable: Int,
    cheapest: TcgCard?,
    mostExpensive: TcgCard?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.surface, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = AppLocale.wishlistStatCards,
                value = row.total.toString(),
                icon = Icons.Default.Style,
                accent = accent,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = AppLocale.wishlistStatTaken,
                value = "${row.owned}",
                icon = Icons.Default.ShoppingBag,
                accent = AppColors.green,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = AppLocale.wishlistStatCost,
                value = formatEurCompact(row.cost),
                icon = Icons.Default.Savings,
                accent = if (row.isOverBudget) AppColors.red else AppColors.gold,
                modifier = Modifier.weight(1f)
            )
        }

        if (row.total > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FillBar(percent = row.ownedPercent, modifier = Modifier.weight(1f), accent = accent)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppLocale.wishlistTakenCount(row.owned, row.total),
                    color = AppColors.textMuted,
                    fontSize = 11.sp
                )
            }
        }

        if (row.hasBudget) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FillBar(
                        percent = row.budgetPercent.coerceAtMost(100f),
                        modifier = Modifier.weight(1f),
                        accent = if (row.isOverBudget) AppColors.red else AppColors.green
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${AppLocale.wishlistBudget} ${formatEurCompact(row.budgetEur)}",
                        color = AppColors.textMuted,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = if (row.isOverBudget) {
                        "${AppLocale.wishlistOverBudget} · ${AppLocale.wishlistAffordable(affordable)}"
                    } else {
                        AppLocale.wishlistBudgetLeft(formatEur(row.budgetLeft))
                    },
                    color = if (row.isOverBudget) AppColors.red else AppColors.textSecondary,
                    fontSize = 11.sp
                )
            }
        }

        if (cheapest != null || mostExpensive != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                cheapest?.let {
                    PriceHint(
                        label = AppLocale.wishlistCheapest,
                        card = it,
                        color = AppColors.green,
                        modifier = Modifier.weight(1f)
                    )
                }
                mostExpensive?.let {
                    PriceHint(
                        label = AppLocale.wishlistMostExpensive,
                        card = it,
                        color = AppColors.orange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (row.unpricedMissing > 0) {
            Text(
                text = AppLocale.wishlistUnpricedNote(row.unpricedMissing),
                color = AppColors.textMuted,
                fontSize = 10.sp
            )
        }
    }
}

/** "La piu' economica: Charizard · € 4,20". Il prossimo passo, in una riga. */
@Composable
private fun PriceHint(
    label: String,
    card: TcgCard,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, color = AppColors.textMuted, fontSize = 10.sp)
        Text(
            text = card.name,
            color = AppColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formatEur(card.cardmarket?.prices.minimumEurPriceOrZero()),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun WishlistCardRow(
    card: TcgCard,
    isOwned: Boolean,
    accent: Color,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val price = card.cardmarket?.prices.minimumEurPriceOrZero()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.card.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .pressScale(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SubcomposeAsyncImage(
            model = ImageUrlUtils.safeImageUrl(card.images.small),
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 48.dp, height = 66.dp)
                .background(AppColors.surface, RoundedCornerShape(8.dp)),
            error = { WishlistCardImageFallback(card) }
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.name,
                    color = AppColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isOwned) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        modifier = Modifier
                            .background(AppColors.green.copy(alpha = 0.18f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AppColors.green,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = AppLocale.wishlistOwnedBadge,
                            color = AppColors.green,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = "#${card.number} · ${card.set?.name ?: "-"}",
                color = AppColors.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (price > 0.0) formatEur(price) else "—",
                color = if (price > 0.0) accent else AppColors.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = AppLocale.delete,
                tint = AppColors.textMuted
            )
        }
    }
}

/** Il segnaposto per quando l'immagine non arriva: dice almeno di che carta si tratta. */
@Composable
private fun WishlistCardImageFallback(card: TcgCard) {
    val series = card.set?.series?.takeIf { it.isNotBlank() } ?: "-"
    val setName = card.set?.name?.takeIf { it.isNotBlank() } ?: "-"
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 66.dp)
            .background(AppColors.surface, RoundedCornerShape(8.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = card.name,
                color = AppColors.textPrimary,
                fontSize = 7.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = series,
                color = AppColors.textMuted,
                fontSize = 6.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = setName,
                color = AppColors.textMuted,
                fontSize = 6.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
