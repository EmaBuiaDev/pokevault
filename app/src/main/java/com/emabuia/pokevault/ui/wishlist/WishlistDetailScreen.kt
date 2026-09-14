package com.emabuia.pokevault.ui.wishlist

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ui.components.CollectorSearchField
import com.emabuia.pokevault.ui.components.EmptyStateView
import com.emabuia.pokevault.ui.components.SkeletonBlock
import com.emabuia.pokevault.ui.components.SortChipRow
import com.emabuia.pokevault.ui.components.StatTile
import com.emabuia.pokevault.ui.components.formatEurCompact
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.pokedex.CardDetailBottomSheet
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.ImageUrlUtils
import com.emabuia.pokevault.util.WishlistCardFilter
import com.emabuia.pokevault.util.WishlistCardRow
import com.emabuia.pokevault.util.WishlistCardSort
import com.emabuia.pokevault.util.WishlistLab
import com.emabuia.pokevault.viewmodel.SetDetailViewModel
import com.emabuia.pokevault.viewmodel.WishlistViewModel
import kotlinx.coroutines.launch

/**
 * Il dettaglio di una lista.
 *
 * Prima era un elenco di carte con il prezzo a destra: diceva cosa avevi
 * segnato, mai quanto costava chiudere la lista, quale prendere per prima, ne'
 * cosa fare quando finalmente la carta arrivava. Adesso la lista ha dei conti,
 * un ordine che parte dalla priorita' e un'uscita verso la collezione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistDetailScreen(
    wishlistId: String,
    onBack: () -> Unit,
    onAddCards: () -> Unit,
    viewModel: WishlistViewModel = viewModel(),
    setDetailViewModel: SetDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wishlist = viewModel.getWishlistById(wishlistId)
    val setDetailState = setDetailViewModel.uiState

    var cards by remember(wishlistId, wishlist?.cardIds) { mutableStateOf<List<TcgCard>>(emptyList()) }
    var isLoading by remember(wishlistId, wishlist?.cardIds) { mutableStateOf(true) }
    var removeCardId by remember { mutableStateOf<String?>(null) }
    var selectedCard by remember { mutableStateOf<TcgCard?>(null) }
    var editingCardId by remember { mutableStateOf<String?>(null) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(WishlistCardSort.PRIORITY) }
    var filter by remember { mutableStateOf(WishlistCardFilter.ALL) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.observeOwnedCards() }

    LaunchedEffect(wishlistId, wishlist?.cardIds) {
        val current = viewModel.getWishlistById(wishlistId)
        if (current == null) {
            isLoading = false
            cards = emptyList()
        } else {
            isLoading = true
            cards = viewModel.loadCardsForWishlist(current)
            isLoading = false
        }
    }

    // I metadati cambiano senza che le carte si ricarichino: le righe si
    // ricalcolano anche quando cambia solo una priorita' o un tetto di prezzo.
    val rows = remember(wishlist, cards, viewModel.ownedCardIds) {
        if (wishlist == null) emptyList()
        else WishlistLab.cardRows(wishlist, cards, viewModel.ownedCardIds)
    }
    val visibleRows = remember(rows, query, sort, filter) {
        WishlistLab.sortCards(WishlistLab.filterCards(rows, query, filter), sort)
    }
    val stats = remember(rows, wishlist?.budget) {
        WishlistLab.stats(rows, wishlist?.budget ?: 0.0)
    }
    val nextPick = remember(rows) { WishlistLab.nextPick(rows) }

    val sortOptions = listOf(
        WishlistCardSort.PRIORITY,
        WishlistCardSort.PRICE_DESC,
        WishlistCardSort.PRICE_ASC,
        WishlistCardSort.NAME,
        WishlistCardSort.NUMBER,
        WishlistCardSort.RECENT
    )
    val sortLabels = listOf(
        AppLocale.wishlistSortPriority,
        AppLocale.wishlistSortPriceDesc,
        AppLocale.wishlistSortPriceAsc,
        AppLocale.wishlistSortName,
        AppLocale.wishlistSortNumber,
        AppLocale.wishlistSortRecent
    )
    val filterOptions = listOf(
        WishlistCardFilter.ALL,
        WishlistCardFilter.HIGH,
        WishlistCardFilter.DEALS,
        WishlistCardFilter.MISSING,
        WishlistCardFilter.OWNED
    )
    val filterLabels = listOf(
        AppLocale.wishlistFilterAll,
        AppLocale.wishlistFilterHigh,
        AppLocale.wishlistFilterDeals,
        AppLocale.wishlistFilterMissing,
        AppLocale.wishlistFilterOwned
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = wishlist?.name ?: AppLocale.wishlistTitle,
                            color = AppColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (rows.isNotEmpty()) {
                            Text(
                                text = AppLocale.wishlistCardsCount(stats.cards) +
                                    " · " + AppLocale.wishlistMissingCount(stats.missing),
                                color = AppColors.textMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
                    if (wishlist != null) {
                        IconButton(onClick = { showBudgetDialog = true }) {
                            Icon(
                                Icons.Default.Savings,
                                contentDescription = AppLocale.wishlistBudgetTitle,
                                tint = if (wishlist.hasBudget) AppColors.green else AppColors.textMuted
                            )
                        }
                        IconButton(
                            onClick = {
                                if (visibleRows.isEmpty()) {
                                    scope.launch { snackbarHostState.showSnackbar(AppLocale.wishlistShareEmpty) }
                                } else {
                                    val text = WishlistLab.shareText(
                                        listName = wishlist.name,
                                        rows = visibleRows,
                                        totalLabel = AppLocale.wishlistShareTotal,
                                        ownedLabel = AppLocale.wishlistOwnedBadge,
                                        priorityLabel = { priorityLabel(it) }
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, wishlist.name)
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, AppLocale.wishlistShareChooser)
                                    )
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = AppLocale.wishlistShare,
                                tint = AppColors.textMuted
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        },
        // Il dettaglio era un vicolo cieco: le carte si potevano solo togliere,
        // e per aggiungerne una bisognava uscire, ricordarsi dov'e' la ricerca e
        // rientrare dalla lista giusta.
        floatingActionButton = {
            if (wishlist != null) {
                FloatingActionButton(
                    onClick = onAddCards,
                    containerColor = AppColors.purple,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = AppLocale.wishlistAddCards, tint = AppColors.textPrimary)
                }
            }
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

            isLoading && cards.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(5) { index ->
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(84.dp),
                            index = index
                        )
                    }
                }
            }

            rows.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        icon = Icons.Default.Favorite,
                        title = AppLocale.wishlistCardsEmpty,
                        subtitle = AppLocale.wishlistCardsEmptySubtitle
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
                ) {
                    item(key = "stats") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                label = AppLocale.wishlistFilterMissing,
                                value = stats.missing.toString(),
                                icon = Icons.Default.Favorite,
                                accent = AppColors.red,
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = AppLocale.wishlistEstimatedCost,
                                value = formatEurCompact(stats.cost),
                                icon = Icons.Default.Sell,
                                accent = AppColors.blue,
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = AppLocale.wishlistFilterDeals,
                                value = stats.deals.toString(),
                                icon = Icons.Default.LocalOffer,
                                accent = AppColors.green,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Quante delle mancanti hanno davvero un prezzo: senza
                    // questa riga il costo stimato sembrerebbe un totale certo.
                    if (stats.missing > 0) {
                        item(key = "priced") {
                            Text(
                                text = AppLocale.wishlistPricedNote(stats.pricedMissing, stats.missing),
                                color = AppColors.textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (stats.hasBudget) {
                        item(key = "budget") {
                            BudgetSection(
                                percent = stats.budgetPercent,
                                budget = stats.budget,
                                left = stats.budgetLeft,
                                isOver = stats.isOverBudget
                            )
                        }
                    }

                    if (nextPick != null) {
                        item(key = "next") {
                            NextPickCard(
                                row = nextPick,
                                onClick = { selectedCard = nextPick.card }
                            )
                        }
                    }

                    item(key = "search") {
                        CollectorSearchField(
                            value = query,
                            onValueChange = { query = it },
                            hint = AppLocale.wishlistSearchCards
                        )
                    }

                    item(key = "sort") {
                        SortChipRow(
                            labels = sortLabels,
                            selectedIndex = sortOptions.indexOf(sort),
                            onSelect = { index -> sort = sortOptions[index] },
                            accent = AppColors.purple
                        )
                    }

                    item(key = "filter") {
                        SortChipRow(
                            labels = filterLabels,
                            selectedIndex = filterOptions.indexOf(filter),
                            onSelect = { index -> filter = filterOptions[index] },
                            accent = AppColors.blue
                        )
                    }

                    if (visibleRows.isEmpty()) {
                        item(key = "no-results") {
                            EmptyStateView(
                                icon = Icons.Default.Search,
                                title = AppLocale.wishlistNoResults,
                                subtitle = ""
                            )
                        }
                    }

                    items(visibleRows, key = { it.cardId }) { row ->
                        WishlistCardItem(
                            row = row,
                            onClick = { selectedCard = row.card },
                            onEdit = { editingCardId = row.cardId }
                        )
                    }
                }
            }
        }
    }

    if (selectedCard != null) {
        val card = selectedCard!!
        CardDetailBottomSheet(
            card = card,
            isOwned = card.id in viewModel.ownedCardIds,
            isLoading = setDetailState.isAddingCard == card.id,
            onAddCard = { variant, quantity, condition, language ->
                setDetailViewModel.addCardWithDetails(card, variant, quantity, condition, language)
            },
            onRemoveCard = {
                viewModel.removeCardFromWishlist(wishlistId, card.id)
                selectedCard = null
            },
            onDismiss = { selectedCard = null },
            // Lo sfoglia-carte del bottom sheet segue l'ordine che hai davanti:
            // scorrere la lista filtrata e trovarsi sotto le carte escluse dal
            // filtro sarebbe un'altra lista.
            cardList = remember(visibleRows) { visibleRows.map { it.card } },
            onCardChange = { selectedCard = it }
        )
    }

    val editingRow = rows.firstOrNull { it.cardId == editingCardId }
    if (editingRow != null) {
        WishlistItemDialog(
            cardName = editingRow.card.name,
            initialPriority = editingRow.priority,
            initialNote = editingRow.item.note,
            initialTargetPrice = editingRow.item.targetPrice,
            currentPrice = editingRow.price,
            isOwned = editingRow.isOwned,
            isSaving = viewModel.isSaving,
            onDismiss = { editingCardId = null },
            onConfirm = { priority, note, targetPrice ->
                viewModel.updateCardMeta(wishlistId, editingRow.cardId, priority, note, targetPrice) { success ->
                    if (success) editingCardId = null
                }
            },
            onMarkPurchased = {
                viewModel.markAsPurchased(wishlistId, editingRow.card) { success ->
                    if (success) editingCardId = null
                }
            },
            onRemove = {
                editingCardId = null
                removeCardId = editingRow.cardId
            }
        )
    }

    if (showBudgetDialog && wishlist != null) {
        WishlistBudgetDialog(
            initialBudget = wishlist.budget,
            isSaving = viewModel.isSaving,
            onDismiss = { showBudgetDialog = false },
            onConfirm = { budget ->
                viewModel.updateBudget(wishlistId, budget) { success ->
                    if (success) showBudgetDialog = false
                }
            }
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
}

/** Il budget con la sua barra: quanto manca, o di quanto hai sforato. */
@Composable
private fun BudgetSection(
    percent: Float,
    budget: Double,
    left: Double,
    isOver: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.card, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Savings,
                    contentDescription = null,
                    tint = if (isOver) AppColors.red else AppColors.green,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = AppLocale.wishlistBudget + " " + formatEurCompact(budget),
                    color = AppColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = if (isOver) {
                    AppLocale.wishlistOverBudget(WishlistLab.formatPrice(-left))
                } else {
                    AppLocale.wishlistBudgetLeft(WishlistLab.formatPrice(left))
                },
                color = if (isOver) AppColors.red else AppColors.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        BudgetBar(percent = percent)
    }
}

/**
 * La carta da prendere adesso.
 *
 * Una wishlist lunga e' una domanda aperta ogni volta che la si apre: questa
 * card la chiude con una risposta sola, la piu' economica fra quelle in alta
 * priorita' (o fra quelle gia' scese sotto il tetto, se ce ne sono).
 */
@Composable
private fun NextPickCard(row: WishlistCardRow, onClick: () -> Unit) {
    val accent = if (row.isDeal) AppColors.green else AppColors.purple
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .pressScale { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SubcomposeAsyncImage(
            model = ImageUrlUtils.safeImageUrl(row.card.images.small),
            contentDescription = row.card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 40.dp, height = 56.dp)
                .background(AppColors.surface, RoundedCornerShape(8.dp)),
            error = { WishlistCardImageFallback(row.card) }
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = AppLocale.wishlistNextPick,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = row.card.name,
                color = AppColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (row.hasPrice) WishlistLab.formatPrice(row.price) else AppLocale.wishlistNoPrice,
                color = AppColors.textSecondary,
                fontSize = 12.sp
            )
        }
        if (row.isDeal) DealTag()
    }
}

@Composable
private fun WishlistCardItem(
    row: WishlistCardRow,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val card = row.card
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.card.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .pressScale { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PriorityBars(priority = row.priority)

        SubcomposeAsyncImage(
            model = ImageUrlUtils.safeImageUrl(card.images.small),
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 48.dp, height = 66.dp)
                .background(AppColors.surface, RoundedCornerShape(8.dp)),
            error = { WishlistCardImageFallback(card) }
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = card.name,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "#${card.number} · ${card.set?.name ?: "-"}",
                color = AppColors.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (row.hasPrice) WishlistLab.formatPrice(row.price) else AppLocale.wishlistNoPrice,
                    color = if (row.isDeal) AppColors.green else AppColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                if (row.isDeal) DealTag()
                if (row.isOwned) OwnedTag()
            }

            // Il tetto si vede solo quando c'e': una riga "obiettivo —" su ogni
            // carta sarebbe rumore su un dato che quasi nessuno compila.
            if (row.item.hasTarget && !row.isDeal) {
                Text(
                    text = if (row.overTargetBy > 0.0) {
                        AppLocale.wishlistOverTarget(WishlistLab.formatPrice(row.overTargetBy))
                    } else {
                        AppLocale.wishlistTargetLabel(WishlistLab.formatPrice(row.item.targetPrice))
                    },
                    color = AppColors.textMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (row.item.hasNote) {
                Text(
                    text = row.item.note,
                    color = AppColors.textMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onEdit) {
            Icon(
                Icons.Default.Tune,
                contentDescription = AppLocale.wishlistCardSettings,
                tint = AppColors.textMuted
            )
        }
    }
}

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
