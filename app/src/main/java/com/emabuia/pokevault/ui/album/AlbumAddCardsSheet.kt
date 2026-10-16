package com.emabuia.pokevault.ui.album

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.emabuia.pokevault.data.catalog.CatalogLang
import com.emabuia.pokevault.data.model.Album
import com.emabuia.pokevault.data.model.AlbumCatalogCard
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AlbumEntries
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.ImageUrlUtils
import com.emabuia.pokevault.viewmodel.AlbumViewModel

/**
 * Il foglio di inserimento, con due schede: la tua collezione e il catalogo.
 *
 * Prima si potevano mettere nell'album solo carte gia' possedute: per una
 * carta nuova bisognava uscire, cercarla, aggiungerla e tornare. Ora la si
 * cerca da qui. Le scelte delle due schede si sommano e si confermano una
 * volta sola, nelle tasche libere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddCardsSheet(
    album: Album,
    viewModel: AlbumViewModel,
    initialSource: AddSource,
    accent: Color,
    onDismiss: () -> Unit,
    onAdded: (Int) -> Unit
) {
    // Senza chiave di proposito: lo stato muore con il foglio, cosi' riaprirlo
    // non ripropone una selezione di carte che nel frattempo sono gia' entrate
    // nell'album e non compaiono piu' nella griglia.
    var source by remember { mutableStateOf(initialSource) }
    var selectedOwned by remember { mutableStateOf(listOf<String>()) }
    var selectedCatalog by remember { mutableStateOf(listOf<AlbumCatalogCard>()) }
    val freeSlots = (album.size - album.cardIds.size).coerceAtLeast(0)
    val selectedCount = selectedOwned.size + selectedCatalog.size
    val canPickMore = selectedCount < freeSlots

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Vedi la nota sui pannelli che rimbalzano: con l'altezza in frazione
        // gli insets vanno solo in basso (dove c'e' anche la tastiera).
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)
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
                    text = AppLocale.albumSlotsLeft((freeSlots - selectedCount).coerceAtLeast(0)),
                    color = if (canPickMore) AppColors.textMuted else AppColors.red,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SourceToggle(
                source = source,
                accent = accent,
                collectionCount = selectedOwned.size,
                catalogCount = selectedCatalog.size,
                onSelect = { source = it }
            )

            if (AlbumEntries.hasRules(album)) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp).align(Alignment.CenterVertically))
                    if (album.pokemonType.isNotBlank()) RuleTag(AppLocale.translateType(album.pokemonType), accent)
                    if (album.expansion.isNotBlank()) RuleTag(album.expansion, accent)
                    if (album.supertype.isNotBlank()) RuleTag(AppLocale.translateSupertype(album.supertype), accent)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (freeSlots == 0) {
                Text(
                    text = AppLocale.albumFull,
                    color = AppColors.red,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                return@Column
            }

            AnimatedContent(
                targetState = source,
                modifier = Modifier.weight(1f),
                label = "addSource"
            ) { current ->
                when (current) {
                    AddSource.COLLECTION -> CollectionPane(
                        album = album,
                        viewModel = viewModel,
                        accent = accent,
                        selected = selectedOwned,
                        canPickMore = canPickMore,
                        onToggle = { id ->
                            selectedOwned = if (id in selectedOwned) selectedOwned - id
                            else if (canPickMore) selectedOwned + id
                            else selectedOwned
                        }
                    )
                    AddSource.CATALOG -> CatalogPane(
                        album = album,
                        viewModel = viewModel,
                        accent = accent,
                        selected = selectedCatalog,
                        selectedOwned = selectedOwned,
                        canPickMore = canPickMore,
                        autoFocus = initialSource == AddSource.CATALOG,
                        onToggle = { card ->
                            selectedCatalog = if (selectedCatalog.any { it.apiId == card.apiId }) {
                                selectedCatalog.filterNot { it.apiId == card.apiId }
                            } else if (canPickMore) {
                                selectedCatalog + card
                            } else {
                                selectedCatalog
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { onAdded(viewModel.addToAlbum(album, selectedOwned, selectedCatalog)) },
                enabled = selectedCount > 0,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    disabledContainerColor = AppColors.textMuted.copy(alpha = 0.15f)
                )
            ) {
                Text(
                    text = AppLocale.albumAddSelected(selectedCount),
                    color = if (selectedCount == 0) AppColors.textMuted else Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Scelta della scheda ───────────────────────────────────────────────────────

@Composable
private fun SourceToggle(
    source: AddSource,
    accent: Color,
    collectionCount: Int,
    catalogCount: Int,
    onSelect: (AddSource) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.background)
            .border(1.dp, AppColors.textMuted.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SourceTab(
            label = AppLocale.albumTabCollection,
            icon = Icons.Default.Style,
            count = collectionCount,
            selected = source == AddSource.COLLECTION,
            accent = accent,
            onClick = { onSelect(AddSource.COLLECTION) },
            modifier = Modifier.weight(1f)
        )
        SourceTab(
            label = AppLocale.albumTabCatalog,
            icon = Icons.Default.TravelExplore,
            count = catalogCount,
            selected = source == AddSource.CATALOG,
            accent = accent,
            onClick = { onSelect(AddSource.CATALOG) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SourceTab(
    label: String,
    icon: ImageVector,
    count: Int,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent else Color.Transparent)
            .pressScale(scaleDown = 0.98f, onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val content = if (selected) Color.White else AppColors.textSecondary
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = content, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (count > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) Color.White.copy(alpha = 0.25f) else accent.copy(alpha = 0.18f))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text("$count", color = if (selected) Color.White else accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Collezione ────────────────────────────────────────────────────────────────

@Composable
private fun CollectionPane(
    album: Album,
    viewModel: AlbumViewModel,
    accent: Color,
    selected: List<String>,
    canPickMore: Boolean,
    onToggle: (String) -> Unit
) {
    val filteredCards = remember(album, viewModel.ownedCards) { viewModel.getFilteredCardsForAlbum(album) }
    var query by remember { mutableStateOf("") }
    val visibleCards = remember(filteredCards, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) filteredCards
        else filteredCards.filter { card ->
            card.name.lowercase().contains(q) ||
                card.set.lowercase().contains(q) ||
                card.cardNumber.lowercase().contains(q)
        }
    }

    Column {
        SheetSearchField(
            value = query,
            onValueChange = { query = it },
            hint = AppLocale.albumSearchPlaceholder,
            accent = accent
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (visibleCards.isEmpty()) {
            PaneMessage(icon = Icons.Default.Style, text = AppLocale.albumNoMatchingCards)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(visibleCards, key = { it.id }) { card ->
                    val isSelected = card.id in selected
                    PickTile(
                        imageUrl = card.imageUrl,
                        title = card.name,
                        subtitle = listOf(card.set, card.cardNumber).filter { it.isNotBlank() }.joinToString(" · "),
                        selected = isSelected,
                        enabled = isSelected || canPickMore,
                        accent = accent,
                        onClick = { onToggle(card.id) }
                    )
                }
            }
        }
    }
}

// ── Catalogo ──────────────────────────────────────────────────────────────────

@Composable
private fun CatalogPane(
    album: Album,
    viewModel: AlbumViewModel,
    accent: Color,
    selected: List<AlbumCatalogCard>,
    selectedOwned: List<String>,
    canPickMore: Boolean,
    autoFocus: Boolean,
    onToggle: (AlbumCatalogCard) -> Unit
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focusRequester.requestFocus() } }

    val results = viewModel.catalogResults
    val ownedByApiId = viewModel.ownedByApiId
    val rulesOn = AlbumEntries.hasRules(album)
    val visible = remember(results, album) {
        if (rulesOn) results.filter { AlbumEntries.matchesRules(album, it) } else results
    }
    val hidden = results.size - visible.size
    // Gia' nell'album: come "da trovare", oppure con la tua copia (anche
    // quella appena scelta nell'altra scheda).
    val alreadyInside = remember(album, ownedByApiId, selectedOwned) {
        val taken = album.cardIds.toSet() + selectedOwned
        AlbumEntries.catalogApiIds(album) +
            ownedByApiId.filterValues { it.id in taken }.keys
    }
    val query = viewModel.catalogQuery.trim()

    Column {
        if (viewModel.englishAvailable) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LangChip("🇮🇹 Italiano", viewModel.catalogLang == CatalogLang.IT, accent) {
                    viewModel.chooseCatalogLang(CatalogLang.IT, context)
                }
                LangChip("🇬🇧 English", viewModel.catalogLang == CatalogLang.EN, accent) {
                    viewModel.chooseCatalogLang(CatalogLang.EN, context)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        SheetSearchField(
            value = viewModel.catalogQuery,
            onValueChange = { viewModel.searchCatalog(it, context) },
            hint = AppLocale.albumCatalogSearchHint,
            accent = accent,
            modifier = Modifier.focusRequester(focusRequester),
            loading = viewModel.isSearchingCatalog
        )
        Spacer(modifier = Modifier.height(10.dp))

        when {
            query.length < 2 -> CatalogIntro(accent)
            viewModel.isSearchingCatalog && results.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
            viewModel.catalogSearchFailed -> PaneMessage(icon = Icons.Default.CloudOff, text = AppLocale.albumCatalogError)
            visible.isEmpty() -> PaneMessage(
                icon = Icons.Default.SearchOff,
                text = if (hidden > 0) AppLocale.albumHiddenByRules(hidden) else AppLocale.albumCatalogNoResults
            )
            else -> Column {
                if (hidden > 0) {
                    Text(
                        AppLocale.albumHiddenByRules(hidden),
                        color = AppColors.textMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visible, key = { it.apiId }) { card ->
                        val isSelected = selected.any { it.apiId == card.apiId }
                        val inside = card.apiId in alreadyInside
                        PickTile(
                            imageUrl = card.imageUrl,
                            title = card.name,
                            subtitle = listOf(card.set, card.number).filter { it.isNotBlank() }.joinToString(" · "),
                            selected = isSelected,
                            enabled = !inside && (isSelected || canPickMore),
                            accent = accent,
                            badge = when {
                                inside -> AppLocale.albumAlreadyInside
                                card.apiId in ownedByApiId -> AppLocale.albumOwnedBadge
                                else -> null
                            },
                            badgeColor = if (inside) AppColors.textMuted else AppColors.green,
                            onClick = { if (!inside) onToggle(card) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogIntro(accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.12f), Color.Transparent)))
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.TravelExplore, contentDescription = null, tint = accent, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            AppLocale.albumCatalogIntro,
            color = AppColors.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ── Mattoni ──────────────────────────────────────────────────────────────────

/** Il campo di ricerca del foglio: sul fondo del foglio serve il colore di sfondo, non quello delle card. */
@Composable
private fun SheetSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    accent: Color,
    modifier: Modifier = Modifier,
    loading: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(hint, color = AppColors.textMuted, fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = when {
            loading -> {
                { CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp)) }
            }
            value.isNotEmpty() -> {
                {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = AppLocale.cancel, modifier = Modifier.size(18.dp))
                    }
                }
            }
            else -> null
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = AppColors.textMuted.copy(alpha = 0.3f),
            cursorColor = accent,
            focusedTextColor = AppColors.textPrimary,
            unfocusedTextColor = AppColors.textPrimary,
            focusedLeadingIconColor = accent,
            unfocusedLeadingIconColor = AppColors.textMuted,
            focusedTrailingIconColor = AppColors.textMuted,
            unfocusedTrailingIconColor = AppColors.textMuted,
            focusedContainerColor = AppColors.background,
            unfocusedContainerColor = AppColors.background
        )
    )
}

@Composable
private fun PickTile(
    imageUrl: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
    badge: String? = null,
    badgeColor: Color = AppColors.green
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.background)
            .then(if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(10.dp)) else Modifier)
            .pressScale(enabled = enabled, onClick = onClick)
            .alpha(if (enabled || selected) 1f else 0.45f)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(ImageUrlUtils.safeProxiedImageUrl(imageUrl))
                .crossfade(true)
                .build(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accent.copy(alpha = 0.30f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.92f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(badge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PaneMessage(icon: ImageVector, text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.textMuted, modifier = Modifier.size(30.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text, color = AppColors.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LangChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else AppColors.background)
            .border(1.dp, if (selected) accent else AppColors.textMuted.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .pressScale(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (selected) accent else AppColors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun RuleTag(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
