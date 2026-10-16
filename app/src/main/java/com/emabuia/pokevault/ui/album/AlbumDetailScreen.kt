package com.emabuia.pokevault.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.emabuia.pokevault.data.model.BinderLayout
import com.emabuia.pokevault.ui.components.FillBar
import com.emabuia.pokevault.ui.components.NotFoundOrLoadingView
import com.emabuia.pokevault.ui.components.StatTile
import com.emabuia.pokevault.ui.components.formatEurCompact
import com.emabuia.pokevault.ui.components.holoFoil
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AlbumSlot
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.CollectorLab
import com.emabuia.pokevault.util.ImageUrlUtils
import com.emabuia.pokevault.util.RarityUtils
import com.emabuia.pokevault.viewmodel.AlbumViewModel
import kotlinx.coroutines.launch

/** Griglia continua oppure pagine del raccoglitore, come uno vero. */
private enum class AlbumViewMode { GRID, BINDER }

/** Le due schede del foglio di inserimento. */
internal enum class AddSource { COLLECTION, CATALOG }

/** Le carte da trovare si vedono, ma spente: grigie e velate. */
private val ToFindFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit = {},
    viewModel: AlbumViewModel = viewModel()
) {
    val album = viewModel.getAlbumById(albumId)
    var addSheetSource by remember { mutableStateOf<AddSource?>(null) }
    var actionSlot by remember { mutableStateOf<AlbumSlot?>(null) }
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
    val slots = remember(album, viewModel.ownedCards) { viewModel.slotsFor(album) }
    val layout = BinderLayout.of(album.layout)
    val themeColors = getThemeColors(album.theme)
    val accent = themeColors.first()
    val isFull = album.cardIds.size >= album.size
    val albumValue = remember(slots) { slots.sumOf { it.value } }
    val found = remember(slots) { slots.count { it.isOwned } }
    val expansions = remember(slots) {
        slots.map { it.set.trim() }.filter { it.isNotBlank() }.distinct().size
    }

    /**
     * La rimozione si puo' annullare.
     *
     * Il pulsante di rimozione e' un bersaglio piccolo sopra l'immagine: un
     * tocco sbagliato toglie una carta sistemata a mano, e prima da li'
     * l'ordine era perso per sempre. Qui l'indice viene ricordato e lo
     * snackbar lo rimette dov'era -- con la sua carta del catalogo, se era
     * una "da trovare".
     */
    fun removeWithUndo(slot: AlbumSlot) {
        val index = album.cardIds.indexOf(slot.key).coerceAtLeast(0)
        viewModel.removeCardFromAlbum(albumId, slot.key)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = AppLocale.albumCardRemoved,
                actionLabel = AppLocale.undo,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreCardToAlbum(albumId, slot.key, index, slot.catalog)
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
                    // La lente porta dritta al catalogo: cercare una carta da
                    // dentro l'album e' il gesto piu' frequente.
                    if (!isFull) {
                        IconButton(onClick = { addSheetSource = AddSource.CATALOG }) {
                            Icon(Icons.Default.Search, contentDescription = AppLocale.albumSearchCatalog, tint = accent)
                        }
                    }
                    if (slots.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewMode = if (viewMode == AlbumViewMode.GRID) AlbumViewMode.BINDER else AlbumViewMode.GRID
                            }
                        ) {
                            Icon(
                                imageVector = if (viewMode == AlbumViewMode.GRID) Icons.Default.AutoStories else Icons.Default.GridView,
                                contentDescription = if (viewMode == AlbumViewMode.GRID) AppLocale.albumViewBinder else AppLocale.albumViewGrid,
                                tint = accent
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        },
        floatingActionButton = {
            if (!isFull && slots.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { addSheetSource = AddSource.COLLECTION },
                    containerColor = accent,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(AppLocale.albumAddCards, fontWeight = FontWeight.SemiBold) }
                )
            }
        }
    ) { padding ->
        if (slots.isEmpty()) {
            EmptyAlbum(
                layout = layout,
                colors = themeColors,
                onFromCollection = { addSheetSource = AddSource.COLLECTION },
                onSearchCatalog = { addSheetSource = AddSource.CATALOG },
                modifier = Modifier.padding(padding)
            )
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
                    found = found,
                    slotsShown = slots.size,
                    expansions = expansions,
                    accent = accent
                )

                when (viewMode) {
                    AlbumViewMode.GRID -> LazyVerticalGrid(
                        columns = GridCells.Fixed(layout.columns),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp)
                    ) {
                        items(slots, key = { it.key }) { slot ->
                            SlotItem(
                                slot = slot,
                                isCover = album.coverImageUrl.isNotBlank() && album.coverImageUrl == slot.imageUrl,
                                onClick = { actionSlot = slot },
                                onRemove = { removeWithUndo(slot) }
                            )
                        }
                        val emptySlots = album.size - album.cardIds.size
                        if (emptySlots > 0) {
                            items(emptySlots, key = { "empty-$it" }) {
                                EmptyPocket(
                                    modifier = Modifier.aspectRatio(0.72f),
                                    onClick = { addSheetSource = AddSource.COLLECTION }
                                )
                            }
                        }
                    }

                    AlbumViewMode.BINDER -> BinderView(
                        album = album,
                        slots = slots,
                        layout = layout,
                        accent = accent,
                        onSlotClick = { slot -> actionSlot = slot },
                        onEmptySlotClick = { addSheetSource = AddSource.COLLECTION },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    addSheetSource?.let { source ->
        AddCardsSheet(
            album = album,
            viewModel = viewModel,
            initialSource = source,
            accent = accent,
            onDismiss = {
                addSheetSource = null
                viewModel.clearCatalogSearch()
            },
            onAdded = { count ->
                addSheetSource = null
                viewModel.clearCatalogSearch()
                scope.launch { snackbarHostState.showSnackbar(AppLocale.albumCardsAdded(count)) }
            }
        )
    }

    actionSlot?.let { slot ->
        SlotActionsSheet(
            slot = slot,
            album = album,
            onDismiss = { actionSlot = null },
            onOpen = slot.owned?.let { owned ->
                {
                    actionSlot = null
                    onCardClick(owned.id)
                }
            },
            onIHaveIt = slot.catalog?.takeIf { !slot.isOwned }?.let { catalog ->
                {
                    actionSlot = null
                    viewModel.addCatalogCardToCollection(catalog) { ok ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (ok) AppLocale.albumAddedToCollection else AppLocale.albumAddToCollectionFailed
                            )
                        }
                    }
                }
            },
            onSetCover = {
                viewModel.setAlbumCover(albumId, slot.imageUrl)
                actionSlot = null
                scope.launch { snackbarHostState.showSnackbar(AppLocale.albumCoverUpdated) }
            },
            onMove = { delta ->
                viewModel.moveCardInAlbum(albumId, slot.key, delta)
                actionSlot = null
            },
            onRemove = {
                actionSlot = null
                removeWithUndo(slot)
            }
        )
    }
}

// ── Album vuoto ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyAlbum(
    layout: BinderLayout,
    colors: List<Color>,
    onFromCollection: () -> Unit,
    onSearchCatalog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = colors.first()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Una pagina vuota del raccoglitore, con le sue tasche
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(170.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.20f), AppColors.surface)))
                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                repeat(layout.rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        repeat(layout.columns) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            AppLocale.albumEmptyTitle,
            color = AppColors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            AppLocale.albumEmptyHint,
            color = AppColors.textMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onSearchCatalog,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(AppLocale.albumSearchCatalog, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onFromCollection,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Style, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(AppLocale.albumFromCollection, color = accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Riassunto dell'album ──────────────────────────────────────────────────────

@Composable
private fun AlbumStatsStrip(
    used: Int,
    size: Int,
    value: Double,
    found: Int,
    slotsShown: Int,
    expansions: Int,
    accent: Color
) {
    val hasToFind = found < slotsShown
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
            // Con carte da trovare conta di piu' quante ne hai gia' trovate
            // che quante espansioni tocca l'album.
            if (hasToFind) {
                StatTile(
                    label = AppLocale.albumFoundLabel,
                    value = "$found/$slotsShown",
                    icon = Icons.Default.TaskAlt,
                    accent = AppColors.gold,
                    modifier = Modifier.weight(1f)
                )
            } else {
                StatTile(
                    label = AppLocale.albumExpansionsLabel,
                    value = "$expansions",
                    icon = Icons.Default.Category,
                    accent = AppColors.blue,
                    modifier = Modifier.weight(1f)
                )
            }
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
 * L'album sfogliato a pagine, col formato scelto (4, 9 o 12 tasche).
 *
 * E' il gesto che un collezionista fa davvero: le carte stanno in pagine, non
 * in una colonna infinita, e la posizione di una carta nella pagina e' parte di
 * come l'album e' stato costruito.
 */
@Composable
private fun BinderView(
    album: Album,
    slots: List<AlbumSlot>,
    layout: BinderLayout,
    accent: Color,
    onSlotClick: (AlbumSlot) -> Unit,
    onEmptySlotClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pageCount = remember(album.size, layout) {
        CollectorLab.binderPageCount(album.size, layout.perPage).coerceAtLeast(1)
    }
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp)
        ) { page ->
            val pageSlots = remember(page, slots, layout) { CollectorLab.binderPage(page, slots, layout.perPage) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.surface)
                    .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (rowIndex in 0 until layout.rows) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (columnIndex in 0 until layout.columns) {
                            val slot = pageSlots.getOrNull(rowIndex * layout.columns + columnIndex)
                            // Le tasche oltre la grandezza dell'album non esistono:
                            // l'ultima pagina puo' essere a meta'.
                            val pocketIndex = page * layout.perPage + rowIndex * layout.columns + columnIndex
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                when {
                                    slot != null -> SlotImage(
                                        slot = slot,
                                        isCover = album.coverImageUrl.isNotBlank() && album.coverImageUrl == slot.imageUrl,
                                        onClick = { onSlotClick(slot) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    pocketIndex < album.size -> EmptyPocket(
                                        modifier = Modifier.fillMaxSize(),
                                        onClick = onEmptySlotClick
                                    )
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
            // Con tante pagine i pallini non stanno in una riga: oltre le
            // dodici resta solo il numero.
            if (pageCount <= 12) {
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
            }
            Text(
                AppLocale.albumBinderPage(pagerState.currentPage + 1, pageCount),
                color = AppColors.textMuted,
                fontSize = 11.sp
            )
        }
    }
}

// ── Tasche ────────────────────────────────────────────────────────────────────

/** La carta nella tasca: a colori se ce l'hai, spenta e con l'etichetta se e' da trovare. */
@Composable
private fun SlotImage(
    slot: AlbumSlot,
    isCover: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.card)
            .pressScale(onClick = onClick)
            // La lamina resta alle carte che nel gioco brillano davvero, e solo
            // a quelle che hai: se brillasse tutto non distinguerebbe piu' niente.
            .holoFoil(enabled = slot.isOwned && RarityUtils.hasFoilFinish(slot.rarity))
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(ImageUrlUtils.safeProxiedImageUrl(slot.imageUrl))
                .crossfade(true)
                .build(),
            contentDescription = slot.name,
            contentScale = ContentScale.Crop,
            colorFilter = if (slot.isOwned) null else ToFindFilter,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (slot.isOwned) 1f else 0.55f)
        )
        if (!slot.isOwned) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    AppLocale.albumToFind,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
        if (isCover) {
            CoverBadge(modifier = Modifier.align(Alignment.TopStart).padding(3.dp))
        }
    }
}

@Composable
private fun SlotItem(
    slot: AlbumSlot,
    isCover: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Box(modifier = Modifier.aspectRatio(0.72f)) {
        SlotImage(slot = slot, isCover = isCover, onClick = onClick, modifier = Modifier.fillMaxSize())
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
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(9.dp))
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

/** La tasca vuota: tratteggio leggero e un +, tocca e si riempie. */
@Composable
private fun EmptyPocket(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.textPrimary.copy(alpha = 0.04f))
            .border(1.dp, AppColors.textMuted.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
            .pressScale(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = AppLocale.albumAddCards,
            tint = AppColors.textMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Azioni su una tasca ───────────────────────────────────────────────────────

/**
 * Il menu di una carta dell'album.
 *
 * Prima un tocco apriva il dettaglio e non c'era altro: la copertina non si
 * poteva scegliere (il campo esisteva ma nessuno lo scriveva) e l'ordine era
 * quello di inserimento, per sempre. Per una carta da trovare c'e' anche
 * "Ce l'ho", che la mette in collezione e accende la tasca.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotActionsSheet(
    slot: AlbumSlot,
    album: Album,
    onDismiss: () -> Unit,
    onOpen: (() -> Unit)?,
    onIHaveIt: (() -> Unit)?,
    onSetCover: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit
) {
    val index = album.cardIds.indexOf(slot.key)
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
                        .width(44.dp)
                        .height(60.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppColors.background)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(ImageUrlUtils.safeProxiedImageUrl(slot.imageUrl))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = if (slot.isOwned) null else ToFindFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        slot.name,
                        color = AppColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            slot.set.takeIf { it.isNotBlank() },
                            slot.number.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        color = AppColors.textMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatusPill(
                        text = if (slot.isOwned) AppLocale.albumOwnedBadge else AppLocale.albumToFind,
                        color = if (slot.isOwned) AppColors.green else AppColors.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            onIHaveIt?.let {
                SheetAction(icon = Icons.Default.AddTask, label = AppLocale.albumIHaveIt, tint = AppColors.green, onClick = it)
            }
            onOpen?.let {
                SheetAction(icon = Icons.AutoMirrored.Filled.OpenInNew, label = AppLocale.albumOpenCard, onClick = it)
            }
            SheetAction(icon = Icons.Default.Star, label = AppLocale.albumSetCover, tint = AppColors.gold, onClick = onSetCover)
            if (!isFirst) {
                SheetAction(icon = Icons.Default.VerticalAlignTop, label = AppLocale.albumMoveFirst, onClick = { onMove(-index) })
                SheetAction(icon = Icons.Default.ArrowUpward, label = AppLocale.albumMoveBack, onClick = { onMove(-1) })
            }
            if (!isLast) {
                SheetAction(icon = Icons.Default.ArrowDownward, label = AppLocale.albumMoveForward, onClick = { onMove(1) })
            }
            SheetAction(icon = Icons.Default.DeleteOutline, label = AppLocale.albumRemoveCard, tint = AppColors.red, onClick = onRemove)
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
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
