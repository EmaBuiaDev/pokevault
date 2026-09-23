package com.emabuia.pokevault.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.emabuia.pokevault.data.model.GoalCriteriaType
import com.emabuia.pokevault.data.remote.ItalianCardAttribute
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ui.components.LabSearchField
import com.emabuia.pokevault.ui.components.CompletionBanner
import com.emabuia.pokevault.ui.components.ErrorStateView
import com.emabuia.pokevault.ui.components.ProgressRing
import com.emabuia.pokevault.ui.components.SkeletonBlock
import com.emabuia.pokevault.ui.components.formatEur
import com.emabuia.pokevault.ui.components.formatEurCompact
import com.emabuia.pokevault.ui.components.holoFoil
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.ui.wishlist.WishlistPickerDialog
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.ChaseCardSort
import com.emabuia.pokevault.util.CollectorLab
import com.emabuia.pokevault.util.ImageUrlUtils
import com.emabuia.pokevault.util.RarityUtils
import com.emabuia.pokevault.util.minimumEurPriceOrZero
import com.emabuia.pokevault.viewmodel.GoalAlbumViewModel
import com.emabuia.pokevault.viewmodel.GoalProgress
import com.emabuia.pokevault.viewmodel.WishlistViewModel
import kotlinx.coroutines.launch

private enum class ChaseTab { ALL, OWNED, MISSING, DUPLICATES }

@Composable
private fun CardImageFallback(card: TcgCard) {
    val series = card.set?.series?.takeIf { it.isNotBlank() } ?: "-"
    val setName = card.set?.name?.takeIf { it.isNotBlank() } ?: "-"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .background(AppColors.surface)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = card.name,
                color = AppColors.textPrimary,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = series,
                color = AppColors.textMuted,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = setName,
                color = AppColors.textMuted,
                fontSize = 8.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Il dettaglio di un chase.
 *
 * Oltre a dire quante carte mancano dice ora anche *quanto costano* e *da quale
 * conviene partire: i prezzi arrivano insieme alle carte del set, quindi la
 * stima non costa una chiamata in piu' (vedi [CollectorLab.completionCost]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalAlbumDetailScreen(
    goalAlbumId: String,
    onBack: () -> Unit,
    viewModel: GoalAlbumViewModel = viewModel(),
    wishlistViewModel: WishlistViewModel = viewModel()
) {
    val album = viewModel.getGoalAlbumById(goalAlbumId)
    var selectedTab by remember { mutableStateOf(ChaseTab.ALL) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ChaseCardSort.NUMBER) }
    var compact by remember { mutableStateOf(false) }
    var sheetCard by remember { mutableStateOf<TcgCard?>(null) }
    var wishlistTargets by remember { mutableStateOf<List<String>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Carichiamo le TcgCard per il dettaglio (da cache/api progressivamente)
    var targetCards by remember { mutableStateOf<List<TcgCard>>(emptyList()) }
    var isLoadingCards by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val tcgRepo = remember { com.emabuia.pokevault.data.remote.RepositoryProvider.tcgRepository }

    // Le chiavi sono l'identita' del chase, non l'oggetto: con `album` bastava
    // che il documento tornasse dallo snapshot listener (anche identico) per
    // rifare il fetch di tutto il set.
    LaunchedEffect(album?.id, album?.criteriaType, album?.criteriaValue, reloadToken) {
        if (album == null) return@LaunchedEffect
        isLoadingCards = true
        loadFailed = false
        val loaded = when (album.criteriaType) {
            GoalCriteriaType.SET ->
                tcgRepo.getCardsBySet(album.criteriaValue).getOrElse { emptyList() }
            GoalCriteriaType.RARITY ->
                tcgRepo.searchItalianCardsByAttribute(ItalianCardAttribute.RARITY, album.criteriaValue, context)
                    .getOrElse { emptyList() }
            GoalCriteriaType.SUPERTYPE ->
                tcgRepo.searchItalianCardsByAttribute(ItalianCardAttribute.SUPERTYPE, album.criteriaValue, context)
                    .getOrElse { emptyList() }
            GoalCriteriaType.TYPE ->
                tcgRepo.searchItalianCardsByAttribute(ItalianCardAttribute.TYPE, album.criteriaValue, context)
                    .getOrElse { emptyList() }
            GoalCriteriaType.CUSTOM ->
                album.criteriaValue.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .mapNotNull { apiId -> tcgRepo.getCard(apiId).getOrNull() }
        }
        targetCards = loaded
        // Un chase ha per definizione delle carte obiettivo: zero carte non e'
        // un chase vuoto, e' una richiesta andata male. Prima si finiva sullo
        // stesso "Nessuna carta disponibile" di un filtro senza risultati.
        loadFailed = loaded.isEmpty() && album.targetCardApiIds.isNotEmpty()
        isLoadingCards = false
    }

    if (album == null) {
        // Vedi AlbumDetailScreen: prima era uno spinner incondizionato, quindi
        // infinito per un chase cancellato o con id non valido.
        com.emabuia.pokevault.ui.components.NotFoundOrLoadingView(
            isLoading = viewModel.isLoading,
            message = AppLocale.chaseNotFound,
            onBack = onBack,
            accentColor = AppColors.orange
        )
        return
    }

    val progress = remember(album, viewModel.ownedCards, targetCards) {
        viewModel.getProgress(album, targetCards)
    }
    val completionCost = remember(progress.missing) { CollectorLab.completionCost(progress.missing) }
    val pricedMissing = remember(progress.missing) { CollectorLab.pricedCount(progress.missing) }
    val cheapestMissing = remember(progress.missing) { CollectorLab.cheapestMissing(progress.missing) }
    val priciestMissing = remember(progress.missing) { CollectorLab.mostExpensiveMissing(progress.missing) }

    val ownedApiIds = remember(viewModel.ownedCards) {
        viewModel.ownedCards
            .asSequence()
            .filter { it.quantity >= 1 }
            .map { it.apiCardId.trim() }
            .toHashSet()
    }

    val tabCards: List<TcgCard> = remember(selectedTab, targetCards, ownedApiIds, progress) {
        when (selectedTab) {
            ChaseTab.ALL -> targetCards
            ChaseTab.OWNED -> targetCards.filter { it.id.trim() in ownedApiIds }
            ChaseTab.MISSING -> progress.missing
            ChaseTab.DUPLICATES -> {
                val dupIds = progress.duplicates.map { it.apiCardId.trim() }.toSet()
                targetCards.filter { it.id.trim() in dupIds }
            }
        }
    }
    val displayCards = remember(tabCards, query, sort) {
        CollectorLab.sortChaseCards(CollectorLab.filterChaseCards(tabCards, query), sort)
    }

    fun openWishlistPicker(cardIds: List<String>) {
        if (cardIds.isEmpty()) return
        if (wishlistViewModel.wishlists.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar(AppLocale.chaseWishlistNoList) }
            return
        }
        wishlistTargets = cardIds
    }

    Scaffold(
        containerColor = AppColors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            album.name,
                            color = AppColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            album.criteriaSummary(),
                            color = AppColors.textMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppLocale.back, tint = AppColors.textPrimary)
                    }
                },
                actions = {
                    if (progress.missing.isNotEmpty()) {
                        IconButton(onClick = { openWishlistPicker(progress.missing.map { it.id }) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = AppLocale.chaseAddMissingToWishlist,
                                tint = AppColors.blue
                            )
                        }
                    }
                    IconButton(onClick = { compact = !compact }) {
                        Icon(
                            imageVector = if (compact) Icons.Default.GridView else Icons.Default.Apps,
                            contentDescription = AppLocale.chaseDensityToggle,
                            tint = AppColors.textSecondary
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = AppLocale.delete, tint = AppColors.textMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LabSearchField(
                    value = query,
                    onValueChange = { query = it },
                    hint = AppLocale.chaseSearchCards,
                    modifier = Modifier.weight(1f)
                )
                ChaseSortMenu(sort = sort, onSelect = { sort = it })
            }

            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = AppColors.surface,
                contentColor = AppColors.orange,
                edgePadding = 8.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = AppColors.orange
                    )
                }
            ) {
                ChaseTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = tab.toLabel(progress),
                                fontSize = 12.sp,
                                color = if (selectedTab == tab) AppColors.orange else AppColors.textSecondary,
                                fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            when {
                isLoadingCards -> ChaseGridSkeleton()

                loadFailed -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    ErrorStateView(message = AppLocale.chaseLoadError)
                    Button(
                        onClick = { reloadToken++ },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.orange),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(AppLocale.retry, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Il riassunto scorre con le carte invece di stare fisso in
                // cima: con banner, anello e costo inchiodati, su un telefono
                // piccolo della griglia restava visibile una riga e mezza.
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(if (compact) 5 else 3),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
                ) {
                    if (progress.total > 0 && progress.owned >= progress.total) {
                        item(key = "completed", span = { GridItemSpan(maxLineSpan) }) {
                            CompletionBanner(
                                title = AppLocale.chaseCompletedTitle,
                                subtitle = AppLocale.chaseCompletedSubtitle
                            )
                        }
                    }

                    item(key = "progress", span = { GridItemSpan(maxLineSpan) }) {
                        ChaseProgressHeader(progress = progress)
                    }

                    if (progress.missing.isNotEmpty()) {
                        item(key = "cost", span = { GridItemSpan(maxLineSpan) }) {
                            CompletionCostCard(
                                cost = completionCost,
                                priced = pricedMissing,
                                missing = progress.missing.size,
                                cheapest = cheapestMissing,
                                priciest = priciestMissing,
                                onCardClick = { card -> sheetCard = card }
                            )
                        }
                    }

                    if (displayCards.isEmpty()) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = if (query.isNotBlank()) {
                                    AppLocale.chaseCardsNoResults
                                } else {
                                    selectedTab.emptyMessage()
                                },
                                color = AppColors.textMuted,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(32.dp)
                            )
                        }
                    }

                    items(displayCards, key = { it.id }) { card ->
                        ChaseCardItem(
                            card = card,
                            isOwned = card.id.trim() in ownedApiIds,
                            compact = compact,
                            onClick = { sheetCard = card }
                        )
                    }
                }
            }
        }
    }

    sheetCard?.let { card ->
        ChaseCardSheet(
            card = card,
            isOwned = card.id.trim() in ownedApiIds,
            onDismiss = { sheetCard = null },
            onAddToCollection = {
                sheetCard = null
                viewModel.addMissingCardToCollection(card) { success ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (success) AppLocale.chaseCardAdded else AppLocale.chaseCardAddError
                        )
                    }
                }
            },
            onAddToWishlist = {
                sheetCard = null
                openWishlistPicker(listOf(card.id))
            }
        )
    }

    wishlistTargets?.let { cardIds ->
        WishlistPickerDialog(
            wishlists = wishlistViewModel.wishlists,
            selectedWishlistIds = emptySet(),
            canCreateNew = false,
            title = AppLocale.chaseAddMissingToWishlist,
            confirmLabel = AppLocale.wishlistAddToList,
            onDismiss = { wishlistTargets = null },
            onCreateNewRequested = {
                wishlistTargets = null
                scope.launch { snackbarHostState.showSnackbar(AppLocale.chaseWishlistNoList) }
            },
            onConfirmSelection = { selectedIds ->
                wishlistTargets = null
                if (selectedIds.isNotEmpty()) {
                    wishlistViewModel.addCardsToWishlists(selectedIds, cardIds) { success ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (success) AppLocale.chaseWishlistAdded(cardIds.size)
                                else AppLocale.chaseWishlistError
                            )
                        }
                    }
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = AppColors.surface,
            title = { Text(AppLocale.chaseDeleteTitle, color = AppColors.textPrimary) },
            text = { Text(AppLocale.chaseDeleteMessage, color = AppColors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGoalAlbum(goalAlbumId)
                    showDeleteDialog = false
                    onBack()
                }) { Text(AppLocale.delete, color = AppColors.red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(AppLocale.cancel, color = AppColors.textSecondary)
                }
            }
        )
    }
}

// ── Progress Header ────────────────────────────────────────────────────────────

@Composable
private fun ChaseProgressHeader(progress: GoalProgress) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ProgressRing(
            percent = progress.percentage,
            size = 72.dp,
            stroke = 7.dp,
            labelSize = 16
        )

        Spacer(modifier = Modifier.width(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            StatRow(label = AppLocale.chaseStatTotal, value = "${progress.total}")
            StatRow(label = AppLocale.chaseStatOwned, value = "${progress.owned}", color = AppColors.green)
            StatRow(label = AppLocale.chaseStatMissing, value = "${progress.missing.size}", color = AppColors.red)
            StatRow(label = AppLocale.chaseStatDuplicates, value = "${progress.duplicates.size}", color = AppColors.blue)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: Color = AppColors.textPrimary) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AppColors.textSecondary, fontSize = 12.sp)
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Quanto costa chiudere ──────────────────────────────────────────────────────

/**
 * Il prezzo del traguardo.
 *
 * E' la domanda che un collezionista si fa davvero davanti a un set incompleto,
 * e la risposta era gia' nei dati: i prezzi arrivano insieme alle carte. La
 * nota sotto dice su quante mancanti la stima e' calcolata, perche' una somma
 * senza il suo denominatore sembra piu' precisa di quello che e'.
 */
@Composable
private fun CompletionCostCard(
    cost: Double,
    priced: Int,
    missing: Int,
    cheapest: TcgCard?,
    priciest: TcgCard?,
    onCardClick: (TcgCard) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.green.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Payments,
                    contentDescription = null,
                    tint = AppColors.green,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    AppLocale.chaseCompletionCost,
                    color = AppColors.textSecondary,
                    fontSize = 12.sp
                )
                Text(
                    if (cost > 0.0) formatEur(cost) else AppLocale.priceUnavailable,
                    color = AppColors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                if (cost > 0.0) {
                    Text(
                        AppLocale.chaseCompletionCostNote(priced, missing),
                        color = AppColors.textMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        if (cheapest != null) {
            MissingHintRow(
                label = AppLocale.chaseNextCheapest,
                card = cheapest,
                accent = AppColors.green,
                onClick = { onCardClick(cheapest) }
            )
        }
        if (priciest != null && priciest.id != cheapest?.id) {
            MissingHintRow(
                label = AppLocale.chaseBiggestHurdle,
                card = priciest,
                accent = AppColors.red,
                onClick = { onCardClick(priciest) }
            )
        }
    }
}

@Composable
private fun MissingHintRow(
    label: String,
    card: TcgCard,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.surface)
            .pressScale(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = AppColors.textMuted, fontSize = 10.sp)
            Text(
                text = card.name + (if (card.number.isNotBlank()) " · ${card.number}" else ""),
                color = AppColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = formatEurCompact(card.cardmarket?.prices.minimumEurPriceOrZero()),
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Ordinamento ────────────────────────────────────────────────────────────────

@Composable
private fun ChaseSortMenu(sort: ChaseCardSort, onSelect: (ChaseCardSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labels = listOf(
        AppLocale.chaseSortNumber,
        AppLocale.chaseSortCardName,
        AppLocale.chaseSortPriceDesc,
        AppLocale.chaseSortPriceAsc
    )

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.SwapVert,
                contentDescription = AppLocale.chaseSortCardsLabel,
                tint = AppColors.orange
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = AppColors.surface
        ) {
            ChaseCardSort.entries.forEachIndexed { index, entry ->
                DropdownMenuItem(
                    text = {
                        Text(
                            labels[index],
                            color = if (entry == sort) AppColors.orange else AppColors.textPrimary,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        onSelect(entry)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ── Card Item ──────────────────────────────────────────────────────────────────

@Composable
private fun ChaseCardItem(
    card: TcgCard,
    isOwned: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    val price = card.cardmarket?.prices.minimumEurPriceOrZero()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .pressScale(onClick = onClick)
            .holoFoil(enabled = isOwned && RarityUtils.hasFoilFinish(card.rarity))
    ) {
        SubcomposeAsyncImage(
            model = ImageUrlUtils.safeImageUrl(card.images.small),
            contentDescription = card.name,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                // Le mancanti restano in ombra: la griglia deve dire a colpo
                // d'occhio dove sono i buchi.
                .alpha(if (isOwned) 1f else 0.35f),
            error = { CardImageFallback(card) }
        )

        if (!isOwned) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(AppColors.background.copy(alpha = 0.72f))
                    .padding(vertical = 3.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (price > 0.0) formatEurCompact(price) else AppLocale.chaseCardMissingLabel,
                    color = if (price > 0.0) AppColors.green else AppColors.textSecondary,
                    fontSize = if (compact) 8.sp else 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else if (!compact) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(AppColors.green.copy(alpha = 0.9f))
                    .padding(2.dp)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = AppLocale.chaseOwnedBadge,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

// ── Foglio di una carta ────────────────────────────────────────────────────────

/**
 * Le azioni su una carta del chase.
 *
 * Prima un tocco sulla carta la infilava dritta in collezione, senza conferma e
 * senza dire con quale variante: bastava sfiorare la griglia per ritrovarsi una
 * carta che non si ha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChaseCardSheet(
    card: TcgCard,
    isOwned: Boolean,
    onDismiss: () -> Unit,
    onAddToCollection: () -> Unit,
    onAddToWishlist: () -> Unit
) {
    val price = card.cardmarket?.prices.minimumEurPriceOrZero()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    SubcomposeAsyncImage(
                        model = ImageUrlUtils.safeImageUrl(card.images.small),
                        contentDescription = card.name,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                        error = { CardImageFallback(card) }
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        card.name,
                        color = AppColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            card.set?.name?.takeIf { it.isNotBlank() },
                            card.number.takeIf { it.isNotBlank() },
                            card.rarity?.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        color = AppColors.textMuted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (price > 0.0) formatEur(price) else AppLocale.priceUnavailable,
                        color = if (price > 0.0) AppColors.green else AppColors.textMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isOwned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AppColors.green,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppLocale.chaseOwnedBadge, color = AppColors.green, fontSize = 13.sp)
                }
            } else {
                Button(
                    onClick = onAddToCollection,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.orange)
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = AppColors.textPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppLocale.addCard, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onAddToWishlist,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = null,
                        tint = AppColors.blue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppLocale.wishlistAddToList, color = AppColors.blue)
                }
            }
        }
    }
}

// ── Scheletro ──────────────────────────────────────────────────────────────────

@Composable
private fun ChaseGridSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(3) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { column ->
                    SkeletonBlock(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.72f),
                        shape = RoundedCornerShape(10.dp),
                        index = row * 3 + column
                    )
                }
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun ChaseTab.toLabel(progress: GoalProgress): String = when (this) {
    ChaseTab.ALL -> AppLocale.chaseTabAll(progress.total)
    ChaseTab.OWNED -> AppLocale.chaseTabOwned(progress.owned)
    ChaseTab.MISSING -> AppLocale.chaseTabMissing(progress.missing.size)
    ChaseTab.DUPLICATES -> AppLocale.chaseTabDuplicates(progress.duplicates.size)
}

private fun ChaseTab.emptyMessage(): String = when (this) {
    ChaseTab.ALL -> AppLocale.chaseEmptyAll
    ChaseTab.OWNED -> AppLocale.chaseEmptyOwned
    ChaseTab.MISSING -> AppLocale.chaseEmptyMissing
    ChaseTab.DUPLICATES -> AppLocale.chaseEmptyDuplicates
}
