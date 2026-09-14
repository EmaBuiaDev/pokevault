package com.emabuia.pokevault.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.emabuia.pokevault.data.model.Album
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.ui.components.CollectorSearchField
import com.emabuia.pokevault.ui.components.FillBar
import com.emabuia.pokevault.ui.components.NotFoundOrLoadingView
import com.emabuia.pokevault.ui.components.StatTile
import com.emabuia.pokevault.ui.components.formatEurCompact
import com.emabuia.pokevault.ui.components.holoFoil
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.CollectorLab
import com.emabuia.pokevault.util.ImageUrlUtils
import com.emabuia.pokevault.util.RarityUtils
import com.emabuia.pokevault.viewmodel.AlbumViewModel
import kotlinx.coroutines.launch

/** Griglia continua oppure pagine da nove, come un raccoglitore vero. */
private enum class AlbumViewMode { GRID, BINDER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit = {},
    viewModel: AlbumViewModel = viewModel()
) {
    val album = viewModel.getAlbumById(albumId)
    var showAddSheet by remember { mutableStateOf(false) }
    var actionCard by remember { mutableStateOf<PokemonCard?>(null) }
    var viewMode by remember { mutableStateOf(AlbumViewMode.GRID) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (album == null) {
        // Prima si mostrava uno spinner incondizionato: per un album cancellato
        // o con id non valido girava all'infinito, senza messaggio e senza modo
        // di tornare indietro. "Sto caricando" e "non esiste" vanno distinti.
        NotFoundOrLoadingView(
            isLoading = viewModel.isLoading,
            message = AppLocale.albumNotFound,
            onBack = onBack,
            accentColor = AppColors.orange
        )
        return
    }

    // Memoizzato: veniva ricalcolato a ogni ricomposizione dello schermo.
    val albumCards = remember(album, viewModel.ownedCards) { viewModel.getCardsForAlbum(album) }
    val themeColors = getThemeColors(album.theme)
    val accent = themeColors.first()
    val isFull = album.cardIds.size >= album.size
    val albumValue = remember(albumCards) { albumCards.sumOf { it.estimatedValue } }
    val expansions = remember(albumCards) {
        albumCards.map { it.set.trim() }.filter { it.isNotBlank() }.distinct().size
    }

    /**
     * La rimozione si puo' annullare.
     *
     * Il pulsante di rimozione e' un bersaglio piccolo sopra l'immagine: un
     * tocco sbagliato toglie una carta sistemata a mano, e prima da li'
     * l'ordine era perso per sempre. Qui l'indice viene ricordato e lo
     * snackbar lo rimette dov'era.
     */
    fun removeWithUndo(card: PokemonCard) {
        val index = album.cardIds.indexOf(card.id).coerceAtLeast(0)
        viewModel.removeCardFromAlbum(albumId, card.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = AppLocale.albumCardRemoved,
                actionLabel = AppLocale.undo,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreCardToAlbum(albumId, card.id, index)
            }
        }
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
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            AppLocale.albumSlots(album.cardIds.size, album.size),
                            color = AppColors.textMuted,
                            fontSize = 12.sp
                        )
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
                    if (albumCards.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewMode = if (viewMode == AlbumViewMode.GRID) {
                                    AlbumViewMode.BINDER
                                } else {
                                    AlbumViewMode.GRID
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (viewMode == AlbumViewMode.GRID) {
                                    Icons.Default.AutoStories
                                } else {
                                    Icons.Default.GridView
                                },
                                contentDescription = if (viewMode == AlbumViewMode.GRID) {
                                    AppLocale.albumViewBinder
                                } else {
                                    AppLocale.albumViewGrid
                                },
                                tint = accent
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.background
                )
            )
        },
        floatingActionButton = {
            if (!isFull) {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = accent,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = AppLocale.albumAddCards, tint = Color.White)
                }
            }
        }
    ) { padding ->
        if (albumCards.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PhotoAlbum,
                        contentDescription = null,
                        tint = AppColors.textMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        AppLocale.albumAddCards,
                        color = AppColors.textSecondary,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { showAddSheet = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = accent.copy(alpha = 0.2f)
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(AppLocale.albumAddCards, color = accent)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                AlbumStatsStrip(
                    used = album.cardIds.size,
                    size = album.size,
                    value = albumValue,
                    expansions = expansions,
                    accent = accent
                )

                when (viewMode) {
                    AlbumViewMode.GRID -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
                    ) {
                        items(albumCards, key = { it.id }) { card ->
                            AlbumCardItem(
                                card = card,
                                isCover = album.coverImageUrl.isNotBlank() &&
                                    album.coverImageUrl == card.imageUrl,
                                onClick = { actionCard = card },
                                onRemove = { removeWithUndo(card) }
                            )
                        }
                        // Empty slots
                        val emptySlots = album.size - albumCards.size
                        if (emptySlots > 0) {
                            items(emptySlots) {
                                EmptySlot(onClick = { showAddSheet = true })
                            }
                        }
                    }

                    AlbumViewMode.BINDER -> BinderView(
                        album = album,
                        cards = albumCards,
                        accent = accent,
                        onCardClick = { card -> actionCard = card },
                        onEmptySlotClick = { showAddSheet = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // Add cards bottom sheet
    if (showAddSheet) {
        AddCardsBottomSheet(
            album = album,
            viewModel = viewModel,
            onDismiss = { showAddSheet = false },
            onAdded = { count ->
                showAddSheet = false
                scope.launch { snackbarHostState.showSnackbar(AppLocale.albumCardsAdded(count)) }
            }
        )
    }

    actionCard?.let { card ->
        CardActionsSheet(
            card = card,
            album = album,
            onDismiss = { actionCard = null },
            onOpen = {
                actionCard = null
                onCardClick(card.id)
            },
            onSetCover = {
                viewModel.setAlbumCover(albumId, card.imageUrl)
                actionCard = null
                scope.launch { snackbarHostState.showSnackbar(AppLocale.albumCoverUpdated) }
            },
            onMove = { delta ->
                viewModel.moveCardInAlbum(albumId, card.id, delta)
                actionCard = null
            },
            onRemove = {
                actionCard = null
                removeWithUndo(card)
            }
        )
    }
}

// ── Riassunto dell'album ──────────────────────────────────────────────────────

@Composable
private fun AlbumStatsStrip(
    used: Int,
    size: Int,
    value: Double,
    expansions: Int,
    accent: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = AppLocale.albumFilledLabel,
                value = "${CollectorLab.fillPercent(used, size).toInt()}%",
                icon = Icons.Default.Inventory2,
                accent = accent,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = AppLocale.albumValueLabel,
                value = formatEurCompact(value),
                icon = Icons.Default.Savings,
                accent = AppColors.green,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = AppLocale.albumExpansionsLabel,
                value = "$expansions",
                icon = Icons.Default.Category,
                accent = AppColors.blue,
                modifier = Modifier.weight(1f)
            )
        }
        FillBar(
            percent = CollectorLab.fillPercent(used, size),
            modifier = Modifier.fillMaxWidth(),
            accent = accent
        )
    }
}

// ── Vista raccoglitore ────────────────────────────────────────────────────────

/**
 * L'album sfogliato a pagine da nove.
 *
 * E' il gesto che un collezionista fa davvero: le carte stanno in pagine, non
 * in una colonna infinita, e la posizione di una carta nella pagina e' parte di
 * come l'album e' stato costruito.
 */
@Composable
private fun BinderView(
    album: Album,
    cards: List<PokemonCard>,
    accent: Color,
    onCardClick: (PokemonCard) -> Unit,
    onEmptySlotClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pageCount = remember(album.size) {
        CollectorLab.binderPageCount(album.size).coerceAtLeast(1)
    }
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) { page ->
            val slots = remember(page, cards) { CollectorLab.binderPage(page, cards) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.surface.copy(alpha = 0.6f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (rowIndex in 0 until 3) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (columnIndex in 0 until 3) {
                            val card = slots.getOrNull(rowIndex * 3 + columnIndex)
                            Box(modifier = Modifier.weight(1f)) {
                                if (card != null) {
                                    BinderSlot(
                                        card = card,
                                        isCover = album.coverImageUrl.isNotBlank() &&
                                            album.coverImageUrl == card.imageUrl,
                                        onClick = { onCardClick(card) }
                                    )
                                } else {
                                    BinderEmptySlot(onClick = onEmptySlotClick)
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pageCount) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) accent
                            else AppColors.textMuted.copy(alpha = 0.35f)
                        )
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                AppLocale.albumBinderPage(pagerState.currentPage + 1, pageCount),
                color = AppColors.textMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun BinderSlot(card: PokemonCard, isCover: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.card)
            .pressScale(onClick = onClick)
            // La lamina resta alle carte che nel gioco brillano davvero: se
            // brillasse tutto non distinguerebbe piu' niente.
            .holoFoil(enabled = card.isRareLike())
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(ImageUrlUtils.safeProxiedImageUrl(card.imageUrl))
                .crossfade(true)
                .build(),
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (isCover) {
            CoverBadge(modifier = Modifier.align(Alignment.TopStart).padding(3.dp))
        }
    }
}

@Composable
private fun BinderEmptySlot(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.card.copy(alpha = 0.45f))
            .pressScale(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = AppLocale.albumAddCards,
            tint = AppColors.textMuted.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Griglia ───────────────────────────────────────────────────────────────────

@Composable
private fun AlbumCardItem(
    card: PokemonCard,
    isCover: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(10.dp))
            .pressScale(onClick = onClick)
            .holoFoil(enabled = card.isRareLike())
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(ImageUrlUtils.safeProxiedImageUrl(card.imageUrl))
                .crossfade(true)
                .build(),
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (isCover) {
            CoverBadge(modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .padding(2.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = AppLocale.albumRemoveCard,
                tint = Color.White,
                modifier = Modifier
                    .size(18.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(9.dp)
                    )
                    .padding(2.dp)
            )
        }
    }
}

@Composable
private fun CoverBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(AppColors.gold.copy(alpha = 0.9f))
            .padding(3.dp)
    ) {
        Icon(
            Icons.Default.Star,
            contentDescription = AppLocale.albumSetCover,
            tint = Color.Black,
            modifier = Modifier.size(11.dp)
        )
    }
}

@Composable
private fun EmptySlot(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.card.copy(alpha = 0.5f))
            .pressScale(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = AppLocale.albumAddCards,
            tint = AppColors.textMuted.copy(alpha = 0.3f),
            modifier = Modifier.size(24.dp)
        )
    }
}

// ── Azioni su una carta ───────────────────────────────────────────────────────

/**
 * Il menu di una carta dell'album.
 *
 * Prima un tocco apriva il dettaglio e non c'era altro: la copertina non si
 * poteva scegliere (il campo esisteva ma nessuno lo scriveva) e l'ordine era
 * quello di inserimento, per sempre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardActionsSheet(
    card: PokemonCard,
    album: Album,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSetCover: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit
) {
    val index = album.cardIds.indexOf(card.id)
    val isFirst = index <= 0
    val isLast = index == album.cardIds.lastIndex

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.card)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(ImageUrlUtils.safeProxiedImageUrl(card.imageUrl))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        card.name,
                        color = AppColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            card.set.takeIf { it.isNotBlank() },
                            card.cardNumber.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        color = AppColors.textMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            SheetAction(
                icon = Icons.Default.OpenInNew,
                label = AppLocale.albumOpenCard,
                onClick = onOpen
            )
            SheetAction(
                icon = Icons.Default.Star,
                label = AppLocale.albumSetCover,
                tint = AppColors.gold,
                onClick = onSetCover
            )
            if (!isFirst) {
                SheetAction(
                    icon = Icons.Default.VerticalAlignTop,
                    label = AppLocale.albumMoveFirst,
                    onClick = { onMove(-index) }
                )
                SheetAction(
                    icon = Icons.Default.ArrowUpward,
                    label = AppLocale.albumMoveBack,
                    onClick = { onMove(-1) }
                )
            }
            if (!isLast) {
                SheetAction(
                    icon = Icons.Default.ArrowDownward,
                    label = AppLocale.albumMoveForward,
                    onClick = { onMove(1) }
                )
            }
            SheetAction(
                icon = Icons.Default.DeleteOutline,
                label = AppLocale.albumRemoveCard,
                tint = AppColors.red,
                onClick = onRemove
            )
        }
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = AppColors.textSecondary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressScale(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = AppColors.textPrimary, fontSize = 14.sp)
    }
}

// ── Inserimento carte ─────────────────────────────────────────────────────────

/**
 * Il foglio di inserimento, a selezione multipla.
 *
 * Prima ogni tocco era una scrittura su Firestore e il foglio restava aperto
 * senza dire cosa era entrato: riempire un album da 36 voleva 36 tocchi al
 * buio. Ora si scelgono le carte e si conferma una volta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCardsBottomSheet(
    album: Album,
    viewModel: AlbumViewModel,
    onDismiss: () -> Unit,
    onAdded: (Int) -> Unit
) {
    val filteredCards = remember(album, viewModel.ownedCards) { viewModel.getFilteredCardsForAlbum(album) }
    // Senza chiave di proposito: lo stato muore con il foglio, cosi' riaprirlo
    // non ripropone una selezione di carte che nel frattempo sono gia' entrate
    // nell'album e non compaiono piu' nella griglia.
    var searchQuery by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val visibleCards = remember(filteredCards, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isBlank()) filteredCards
        else filteredCards.filter { card ->
            card.name.lowercase().contains(query) ||
                card.set.lowercase().contains(query) ||
                card.cardNumber.lowercase().contains(query)
        }
    }
    val freeSlots = (album.size - album.cardIds.size).coerceAtLeast(0)
    val isFull = freeSlots == 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = AppLocale.albumAddCards,
                    color = AppColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = AppLocale.albumSlotsLeft(freeSlots),
                    color = if (isFull) AppColors.red else AppColors.textMuted,
                    fontSize = 12.sp
                )
            }

            if (album.pokemonType.isNotBlank() || album.expansion.isNotBlank() || album.supertype.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (album.pokemonType.isNotBlank()) {
                        FilterTag(AppLocale.translateType(album.pokemonType))
                    }
                    if (album.expansion.isNotBlank()) {
                        FilterTag(album.expansion)
                    }
                    if (album.supertype.isNotBlank()) {
                        FilterTag(album.supertype)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            CollectorSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                hint = AppLocale.albumSearchPlaceholder
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (isFull) {
                Text(
                    text = AppLocale.albumFull,
                    color = AppColors.red,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else if (visibleCards.isEmpty()) {
                Text(
                    text = AppLocale.albumNoMatchingCards,
                    color = AppColors.textMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(visibleCards, key = { it.id }) { card ->
                        val isSelected = card.id in selected
                        Box(
                            modifier = Modifier
                                .aspectRatio(0.72f)
                                .clip(RoundedCornerShape(10.dp))
                                .pressScale {
                                    selected = selected.toMutableSet().apply {
                                        if (!remove(card.id) && size < freeSlots) add(card.id)
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(ImageUrlUtils.safeProxiedImageUrl(card.imageUrl))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = card.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(AppColors.orange.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = card.name,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val ids = selected.toList()
                        viewModel.addCardsToAlbum(album.id, ids)
                        onAdded(ids.size)
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.orange,
                        disabledContainerColor = AppColors.card
                    )
                ) {
                    Text(
                        text = AppLocale.albumAddSelected(selected.size),
                        color = if (selected.isEmpty()) AppColors.textMuted else AppColors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterTag(text: String) {
    Surface(
        color = AppColors.orange.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = AppColors.orange,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** Vedi [RarityUtils.hasFoilFinish]: la lamina va solo alle rarita' lucide. */
private fun PokemonCard.isRareLike(): Boolean = RarityUtils.hasFoilFinish(rarity)
