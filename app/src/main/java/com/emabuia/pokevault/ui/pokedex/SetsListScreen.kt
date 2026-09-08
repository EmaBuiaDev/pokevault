package com.emabuia.pokevault.ui.pokedex

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgSet
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.RarityUtils
import com.emabuia.pokevault.util.minimumEurPriceOrZero
import com.emabuia.pokevault.viewmodel.SetsUiState
import com.emabuia.pokevault.viewmodel.SetsViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

// Formatta data
fun formatDate(date: String): String {
    return try {
        val parts = date.split("/")
        if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else date
    } catch (_: Exception) { date }
}

// ── Animazione Pokéball ──
@Composable
fun PokeballLoadingAnimation(
    message: String = "Caricamento...",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pokeball")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Pokéball disegnata con Canvas
        Box(
            modifier = Modifier
                .size(64.dp)
                .offset(y = (-8 * bounce).dp)
                .rotate(rotation)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val s = size.minDimension
                val r = s / 2
                val cx = size.width / 2
                val cy = size.height / 2

                // Metà superiore rossa
                drawArc(
                    color = Color(0xFFEF4444),
                    startAngle = 180f, sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset.Zero, size = Size(s, s)
                )
                // Metà inferiore bianca
                drawArc(
                    color = Color(0xFFF5F5F5),
                    startAngle = 0f, sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset.Zero, size = Size(s, s)
                )
                // Linea nera centrale
                drawLine(
                    color = Color(0xFF2D2D2D),
                    start = Offset(0f, cy),
                    end = Offset(s, cy),
                    strokeWidth = s * 0.06f
                )
                // Cerchio esterno
                drawCircle(
                    color = Color(0xFF2D2D2D),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = s * 0.05f)
                )
                // Cerchio centrale bianco
                drawCircle(color = Color(0xFFF5F5F5), radius = r * 0.25f, center = Offset(cx, cy))
                // Cerchio centrale bordo
                drawCircle(color = Color(0xFF2D2D2D), radius = r * 0.25f, center = Offset(cx, cy), style = Stroke(width = s * 0.05f))
                // Cerchio interno
                drawCircle(color = Color(0xFFF5F5F5), radius = r * 0.12f, center = Offset(cx, cy))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = message,
            color = TextGray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetsListScreen(
    onBack: () -> Unit,
    onSetClick: (String, String) -> Unit,
    viewModel: SetsViewModel = viewModel()
) {
    val state = viewModel.uiState
    var isSearchingCards by remember { mutableStateOf(false) }
    var selectedCard by remember { mutableStateOf<TcgCard?>(null) }
    var collapsedSeriesKeys by remember { mutableStateOf(emptySet<String>()) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var cardViewMode by remember { mutableStateOf(CardViewMode.GRID) }
    val setsGridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    if (showFilterSheet) {
        CardFilterSheet(
            state = state,
            onDismiss = { showFilterSheet = false },
            onToggleRarity = viewModel::toggleCardRarityFilter,
            onToggleType = viewModel::toggleCardTypeFilter,
            onToggleSupertype = viewModel::toggleCardSupertypeFilter,
            onToggleSubtype = viewModel::toggleCardSubtypeFilter,
            onReset = viewModel::clearCardResultFilters
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Refresh current cached sets without forcing network.
                viewModel.refreshFromCache()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.successMessage, state.errorMessage) {
        val msg = state.successMessage ?: state.errorMessage
        if (msg != null) { snackbarHostState.showSnackbar(msg); viewModel.clearMessages() }
    }

    if (selectedCard != null) {
        CardDetailBottomSheet(
            card = selectedCard!!,
            isOwned = false,
            isLoading = state.isAddingCard == selectedCard!!.id,
            onAddCard = { v, q, c, l ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.addCardWithDetails(selectedCard!!, v, q, c, l)
            },
            onRemoveCard = {},
            onDismiss = { selectedCard = null },
            cardList = state.searchedCards,
            onCardChange = { selectedCard = it }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
    ) {
        TopAppBar(
            title = { Text("Pokédex", fontWeight = FontWeight.Bold, color = TextWhite) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = TextWhite)
                }
            },
            actions = {
                if (!state.isLoading) {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, AppLocale.refresh, tint = TextMuted)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
        )

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCard),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TabItem(AppLocale.extensions, !isSearchingCards) {
                    isSearchingCards = false; viewModel.clearCardSearch()
                }
                TabItem(AppLocale.searchCards, isSearchingCards) {
                    isSearchingCards = true
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SearchBarBg)
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, AppLocale.search, tint = TextMuted, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        val placeholder = if (isSearchingCards) AppLocale.searchCardPlaceholder else AppLocale.searchSetPlaceholder
                        val query = if (isSearchingCards) state.cardSearchQuery else state.searchQuery
                        if (query.isEmpty()) Text(placeholder, color = TextMuted, fontSize = 14.sp)
                        BasicTextField(
                            value = query,
                            onValueChange = {
                                if (isSearchingCards) viewModel.searchCardsByName(it) else viewModel.updateSearch(it)
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(color = TextWhite, fontSize = 14.sp),
                            singleLine = true, cursorBrush = SolidColor(BlueCard),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    val query = if (isSearchingCards) state.cardSearchQuery else state.searchQuery
                    if (query.isNotEmpty()) {
                        Icon(Icons.Default.Close, AppLocale.cancel, tint = TextMuted,
                            modifier = Modifier.size(20.dp).clickable {
                                if (isSearchingCards) viewModel.clearCardSearch() else viewModel.updateSearch("")
                            })
                    }
                }
            }

            if (isSearchingCards) {
                Spacer(modifier = Modifier.height(8.dp))
                val activeFilterCount = state.cardRarityFilter.size + state.cardTypeFilter.size +
                    state.cardSupertypeFilter.size + state.cardSubtypeFilter.size
                val hasFilterOptions = state.availableCardRarities.isNotEmpty() ||
                    state.availableCardTypes.isNotEmpty() ||
                    state.availableCardSupertypes.isNotEmpty() ||
                    state.availableCardSubtypes.isNotEmpty()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = state.isExactCardSearch,
                        onClick = { viewModel.setExactCardSearch(!state.isExactCardSearch) },
                        label = {
                            Text(
                                text = if (state.isExactCardSearch) "Match esatto: ON" else "Match esatto",
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (state.isExactCardSearch) Icons.Default.Check else Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BlueCard,
                            selectedLabelColor = TextWhite,
                            selectedLeadingIconColor = TextWhite,
                            containerColor = DarkCard,
                            labelColor = TextMuted,
                            iconColor = TextMuted
                        )
                    )

                    // Filtri rarita'/tipo/categoria/sottotipo raccolti in un unico
                    // pannello (invece di piu' righe di chip sempre visibili) per non
                    // affollare la barra di ricerca -- valori reali dal risultato
                    // corrente (rarity ora nostra in D1, vedi MIGRATION_PLAN.md M4.6).
                    if (hasFilterOptions) {
                        FilterChip(
                            selected = activeFilterCount > 0,
                            onClick = { showFilterSheet = true },
                            label = {
                                Text(
                                    text = if (activeFilterCount > 0) "Filtri ($activeFilterCount)" else "Filtri",
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BlueCard,
                                selectedLabelColor = TextWhite,
                                selectedLeadingIconColor = TextWhite,
                                containerColor = DarkCard,
                                labelColor = TextMuted,
                                iconColor = TextMuted
                            )
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = { cardViewMode = cardViewMode.toggled() }) {
                        Icon(
                            imageVector = if (cardViewMode == CardViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = "Cambia visualizzazione",
                            tint = TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isSearchingCards) {
            val setReleaseDateById = remember(state.allSets) {
                state.allSets.associate { it.id to it.releaseDate }
            }
            CardSearchResults(
                cards = state.searchedCards,
                isLoading = state.isSearchingCards,
                query = state.cardSearchQuery,
                viewMode = cardViewMode,
                setReleaseDateById = setReleaseDateById,
                onCardClick = { card -> selectedCard = card },
                onCardSetClick = { setId -> onSetClick(setId, "ITA") }
            )
        } else {
            // ── Contenuto: lista unica con intestazioni di sezione per serie,
            // ordinate dalla piu' recente (vedi buildSeriesGroups/setDisplayComparator
            // in SetsViewModel.kt) -- niente piu' tab lingua/chip serie da selezionare.
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PokeballLoadingAnimation(message = AppLocale.loadingSets)
                }
            } else if (state.errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠️", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(state.errorMessage, color = TextGray, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.refresh() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BlueCard)
                        ) { Text(AppLocale.retry) }
                    }
                }
            } else if (state.seriesGroups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(AppLocale.noResults, color = TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyVerticalGrid(
                    state = setsGridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.seriesGroups.forEach { group ->
                        val isCollapsed = group.seriesKey in collapsedSeriesKeys
                        item(span = { GridItemSpan(2) }, key = "header::${group.seriesKey}") {
                            SeriesSectionHeader(
                                label = group.seriesLabel,
                                count = group.sets.size,
                                isCollapsed = isCollapsed,
                                onToggle = {
                                    collapsedSeriesKeys = if (isCollapsed) {
                                        collapsedSeriesKeys - group.seriesKey
                                    } else {
                                        collapsedSeriesKeys + group.seriesKey
                                    }
                                }
                            )
                        }
                        if (!isCollapsed) {
                            items(items = group.sets, key = { "${group.seriesKey}::${it.id}" }) { set ->
                                SetCard(
                                    set = set,
                                    onClick = { onSetClick(set.id, "ITA") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    }
}

// ── Intestazione di sezione (serie), con conteggio e toggle espandi/comprimi ──
@Composable
private fun SeriesSectionHeader(label: String, count: Int, isCollapsed: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(Locale.ROOT),
            color = TextWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 0.5.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$count", color = TextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Visualizzazione risultati ricerca carte: griglia (default) o lista (piu' dettagli per riga) ──
enum class CardViewMode {
    GRID, LIST;

    fun toggled(): CardViewMode = if (this == GRID) LIST else GRID
}

// ── Pannello filtri ricerca carte: rarita'/tipo/categoria/sottotipo raccolti in un
// unico bottom sheet (invece di piu' righe di chip sempre visibili) cosi' la barra di
// ricerca resta pulita anche con molte dimensioni di filtro disponibili. ──
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CardFilterSheet(
    state: SetsUiState,
    onDismiss: () -> Unit,
    onToggleRarity: (String) -> Unit,
    onToggleType: (String) -> Unit,
    onToggleSupertype: (String) -> Unit,
    onToggleSubtype: (String) -> Unit,
    onReset: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filtri", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                val activeCount = state.cardRarityFilter.size + state.cardTypeFilter.size +
                    state.cardSupertypeFilter.size + state.cardSubtypeFilter.size
                if (activeCount > 0) {
                    Text(
                        text = "Azzera",
                        color = BlueCard,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onReset)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (state.availableCardSupertypes.isNotEmpty()) {
                FilterSection(title = "Categoria") {
                    state.availableCardSupertypes.forEach { supertype ->
                        SeriesFilterChip(
                            label = AppLocale.translateSupertype(supertype),
                            count = 0,
                            showCount = false,
                            isSelected = supertype in state.cardSupertypeFilter,
                            onClick = { onToggleSupertype(supertype) }
                        )
                    }
                }
            }

            if (state.availableCardTypes.isNotEmpty()) {
                FilterSection(title = "Tipo") {
                    state.availableCardTypes.forEach { type ->
                        SeriesFilterChip(
                            label = AppLocale.translateType(type),
                            count = 0,
                            showCount = false,
                            isSelected = type in state.cardTypeFilter,
                            onClick = { onToggleType(type) }
                        )
                    }
                }
            }

            if (state.availableCardSubtypes.isNotEmpty()) {
                FilterSection(title = "Sottotipo") {
                    state.availableCardSubtypes.forEach { subtype ->
                        SeriesFilterChip(
                            label = AppLocale.translateSubtype(subtype),
                            count = 0,
                            showCount = false,
                            isSelected = subtype in state.cardSubtypeFilter,
                            onClick = { onToggleSubtype(subtype) }
                        )
                    }
                }
            }

            if (state.availableCardRarities.isNotEmpty()) {
                FilterSection(title = "Rarità") {
                    state.availableCardRarities.forEach { rarity ->
                        val info = RarityUtils.getRarityInfo(rarity)
                        SeriesFilterChip(
                            label = "${info.emoji} ${AppLocale.translateRarity(rarity)}",
                            count = 0,
                            showCount = false,
                            isSelected = rarity in state.cardRarityFilter,
                            onClick = { onToggleRarity(rarity) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(title, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

// ── Tab item ──
@Composable
fun RowScope.TabItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (isSelected) TextWhite else TextMuted,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 14.sp, textAlign = TextAlign.Center,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .background(if (isSelected) BlueCard.copy(alpha = 0.3f) else Color.Transparent)
            .padding(vertical = 12.dp)
    )
}

// ── Filtro serie migliorato con conteggio ──
@Composable
fun SeriesFilterChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    showCount: Boolean = true
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) BlueCard else DarkCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) TextWhite else TextMuted,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp, maxLines = 1
        )
        if (showCount) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.2f)
                        else TextMuted.copy(alpha = 0.15f)
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "$count",
                    color = if (isSelected) TextWhite else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── Set Card con logo, nome e data formattata ──
@Composable
fun SetCard(set: TcgSet, onClick: () -> Unit) {
    val context = LocalContext.current
    val logoUrl = set.images.logo.trim()
    val shouldLoadLogo = logoUrl.isNotBlank()
    // Per-card only: a missing/broken logo just shows the text fallback below,
    // it never affects the set's position in the list (see setDisplayComparator
    // in SetsViewModel.kt for why that used to be the cause of sets visibly
    // "jumping" while scrolling).
    var showFallback by remember(logoUrl) { mutableStateOf(!shouldLoadLogo) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showFallback) {
                    MissingSetLogoFallback(setName = set.name)
                } else if (shouldLoadLogo) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(logoUrl)
                            .crossfade(false)
                            .build(),
                        contentDescription = set.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        onError = { showFallback = true }
                    )
                }
            }

            Column {
                Text(
                    text = set.name, color = TextWhite, fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatDate(set.releaseDate),
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                    val cardCount = maxOf(set.printedTotal, set.total)
                    if (cardCount > 0) {
                        Text(" · ", color = TextMuted, fontSize = 10.sp)
                        Text("$cardCount carte", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MissingSetLogoFallback(setName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BlueCard.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = setName,
            color = TextWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textDecoration = TextDecoration.None,
            lineHeight = 14.sp
        )
    }
}

// ── Card search results ──
@Composable
fun CardSearchResults(
    cards: List<TcgCard>,
    isLoading: Boolean,
    query: String,
    setReleaseDateById: Map<String, String>,
    viewMode: CardViewMode = CardViewMode.GRID,
    onCardClick: (TcgCard) -> Unit = {},
    onCardSetClick: (String) -> Unit
) {
    if (query.length < 2) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔍", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(AppLocale.writeAtLeast2, color = TextMuted, fontSize = 14.sp)
            }
        }
    } else if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PokeballLoadingAnimation(message = AppLocale.searchFor(query))
        }
    } else if (cards.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("😔", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(AppLocale.noResults, color = TextGray, fontSize = 14.sp)
            }
        }
    } else {
        val grouped = cards.groupBy { it.set?.id?.takeIf { id -> id.isNotBlank() } ?: "unknown" }
        val orderedGroups = grouped.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<TcgCard>>> { entry ->
                    parseReleaseDateToEpochUi(
                        setReleaseDateById[entry.key].orEmpty()
                    )
                }.thenBy { entry ->
                    entry.value.firstOrNull()?.set?.name?.lowercase(Locale.ROOT) ?: ""
                }
            )
        val columns = if (viewMode == CardViewMode.GRID) 3 else 1

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(columns) }) {
                Text(AppLocale.resultsCountInExpansions(cards.size, grouped.size), color = TextMuted, fontSize = 13.sp)
            }
            orderedGroups.forEach { (setId, setCards) ->
                val setName = setCards.firstOrNull()?.set?.name ?: AppLocale.unknown
                val formattedReleaseDate = formatReleaseDateUi(setReleaseDateById[setId].orEmpty())
                item(span = { GridItemSpan(columns) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(DarkCard)
                            .clickable {
                                setCards.firstOrNull()?.set?.id?.takeIf { id -> id.isNotBlank() }?.let { onCardSetClick(it) }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(setName, color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            val subtitle = if (formattedReleaseDate.isBlank()) {
                                AppLocale.resultsCount(setCards.size)
                            } else {
                                "${AppLocale.resultsCount(setCards.size)} • $formattedReleaseDate"
                            }
                            Text(subtitle, color = TextMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    }
                }
                items(
                    setCards.sortedBy { extractCardNumberForUi(it.number).toIntOrNull() ?: Int.MAX_VALUE },
                    key = { "${it.id}_$setId" }
                ) { card ->
                    if (viewMode == CardViewMode.GRID) {
                        SearchResultGridCard(card = card, onClick = { onCardClick(card) })
                    } else {
                        SearchResultListRow(card = card, onClick = { onCardClick(card) })
                    }
                }
            }
        }
    }
}

// ── Card di ricerca, vista griglia: immagine + badge rarita' + prezzo ──
@Composable
private fun SearchResultGridCard(card: TcgCard, onClick: () -> Unit) {
    val price = card.cardmarket?.prices.minimumEurPriceOrZero()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        if (card.images.small.isNotBlank()) {
            AsyncImage(
                model = card.images.small,
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (!card.rarity.isNullOrBlank()) {
                val info = RarityUtils.getRarityInfo(card.rarity)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(info.color.copy(alpha = 0.85f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(info.emoji, fontSize = 10.sp)
                }
            }
            if (price > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("${"%.2f".format(price)} €", color = Color(0xFF4ADE80), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "#${extractCardNumberForUi(card.number)} ${card.name}",
                    color = TextWhite,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkSurface),
                contentAlignment = Alignment.Center
            ) {
                Text(AppLocale.noImage, color = TextMuted, fontSize = 10.sp)
            }
        }
    }
}

// ── Card di ricerca, vista lista: piu' dettagli leggibili per riga ──
@Composable
private fun SearchResultListRow(card: TcgCard, onClick: () -> Unit) {
    val price = card.cardmarket?.prices.minimumEurPriceOrZero()
    val rarityInfo = card.rarity?.takeIf { it.isNotBlank() }?.let { RarityUtils.getRarityInfo(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurface)
        ) {
            if (card.images.small.isNotBlank()) {
                AsyncImage(
                    model = card.images.small,
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "#${extractCardNumberForUi(card.number)} ${card.name}",
                color = TextWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (rarityInfo != null) {
                    Text("${rarityInfo.emoji} ${rarityInfo.label}", color = TextMuted, fontSize = 11.sp)
                }
                if (rarityInfo != null && card.set?.name?.isNotBlank() == true) {
                    Text("  •  ", color = TextMuted, fontSize = 11.sp)
                }
                card.set?.name?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (price > 0) {
            Text("${"%.2f".format(price)} €", color = Color(0xFF4ADE80), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun extractCardNumberForUi(rawNumber: String): String {
    return rawNumber.substringBefore('/').trim().trimStart('0').ifEmpty { "0" }
}

private fun formatReleaseDateUi(raw: String): String {
    val source = raw.trim()
    if (source.isBlank()) return ""

    runCatching {
        LocalDate.parse(source, DateTimeFormatter.ISO_LOCAL_DATE)
    }.getOrNull()?.let { date ->
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY))
    }

    val cleaned = source
        .replace(Regex("""(\d+)(st|nd|rd|th)"""), "$1")
        .replace('_', ' ')
        .trim()

    val fallbackParsers = listOf(
        DateTimeFormatter.ofPattern("d MMMM, uuuu", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)
    )
    for (parser in fallbackParsers) {
        try {
            val parsed = LocalDate.parse(cleaned, parser)
            return parsed.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY))
        } catch (_: DateTimeParseException) {
            // Try next parser.
        }
    }

    return source
}

private fun parseReleaseDateToEpochUi(raw: String): Long {
    val source = raw.trim()
    if (source.isBlank()) return Long.MIN_VALUE

    runCatching {
        LocalDate.parse(source, DateTimeFormatter.ISO_LOCAL_DATE)
    }.getOrNull()?.let { return it.toEpochDay() }

    val cleaned = source
        .replace(Regex("""(\d+)(st|nd|rd|th)"""), "$1")
        .replace('_', ' ')
        .trim()

    val fallbackParsers = listOf(
        DateTimeFormatter.ofPattern("d MMMM, uuuu", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)
    )
    for (parser in fallbackParsers) {
        try {
            return LocalDate.parse(cleaned, parser).toEpochDay()
        } catch (_: DateTimeParseException) {
            // Try next parser.
        }
    }

    return Long.MIN_VALUE
}

// SeriesChip non più necessario, sostituito da SeriesFilterChip
