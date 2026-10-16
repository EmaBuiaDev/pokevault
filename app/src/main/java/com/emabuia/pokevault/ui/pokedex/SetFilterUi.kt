package com.emabuia.pokevault.ui.pokedex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.italian.ItalianPriceBucket
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.RarityUtils
import com.emabuia.pokevault.viewmodel.SetDetailUiState

/** Illustratori mostrati prima di "Tutti i N": un set ne ha anche cinquanta. */
private const val ARTISTS_PREVIEW = 12

/**
 * La barra sopra le carte di un'espansione. Sta fuori dalla griglia, quindi
 * resta ferma mentre si scorre: prima ricerca e filtri erano righe della
 * griglia, e per cambiare filtro a meta' set bisognava tornare in cima.
 */
@Composable
fun SetFilterBar(
    state: SetDetailUiState,
    onSearch: (String) -> Unit,
    onToggleOwnership: (SetOwnership) -> Unit,
    onOpenFilters: () -> Unit,
    onSort: (SetCardSort) -> Unit,
    onViewMode: (String) -> Unit,
    onFilterChange: ((SetCardFilter) -> SetCardFilter) -> Unit
) {
    val filter = state.filter
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.background)
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SetSearchField(state = state, onSearch = onSearch, modifier = Modifier.weight(1f))
            SortButton(selected = state.sort, onSort = onSort)
            IconButton(onClick = { onViewMode(if (state.viewMode == "grid") "list" else "grid") }) {
                Icon(
                    imageVector = if (state.viewMode == "grid") Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    contentDescription = AppLocale.changeView,
                    tint = AppColors.textMuted
                )
            }
        }

        // Filtri e Mancanti/Possedute sempre a vista, con la scritta: le due
        // icone di prima (un imbuto barrato e una spunta) non le capiva nessuno.
        val owned = state.ownedCount
        val missing = (state.cards.size - owned).coerceAtLeast(0)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarChip(
                label = if (filter.panelCount > 0) "${AppLocale.filters} (${filter.panelCount})" else AppLocale.filters,
                icon = Icons.Default.Tune,
                selected = filter.panelCount > 0,
                color = AppColors.blue,
                onClick = onOpenFilters
            )
            BarChip(
                label = AppLocale.setMissingCount(missing),
                icon = Icons.Default.RadioButtonUnchecked,
                selected = filter.ownership == SetOwnership.MISSING,
                color = AppColors.red,
                onClick = { onToggleOwnership(SetOwnership.MISSING) }
            )
            BarChip(
                label = AppLocale.setOwnedCount(owned),
                icon = Icons.Default.CheckCircle,
                selected = filter.ownership == SetOwnership.OWNED,
                color = AppColors.green,
                onClick = { onToggleOwnership(SetOwnership.OWNED) }
            )
        }

        // I filtri accesi, ognuno col suo ✕: chiuso il pannello si vede
        // ancora cosa sta restringendo la griglia.
        val active = activeFilterChips(filter)
        if (active.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppLocale.clearShort,
                    color = AppColors.blue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onFilterChange { it.clearPanel() } }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
                active.forEach { chip ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppColors.blue.copy(alpha = 0.18f))
                            .clickable { onFilterChange(chip.remove) }
                            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp)
                    ) {
                        Text(chip.label, color = AppColors.textPrimary, fontSize = 12.sp, maxLines = 1)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Close,
                            contentDescription = AppLocale.cancel,
                            tint = AppColors.textMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetSearchField(state: SetDetailUiState, onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.card)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = AppColors.textMuted, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (state.searchQuery.isEmpty()) {
                    Text(AppLocale.setSearchPlaceholder, color = AppColors.textMuted, fontSize = 13.sp, maxLines = 1)
                }
                BasicTextField(
                    value = state.searchQuery,
                    onValueChange = onSearch,
                    textStyle = TextStyle(color = AppColors.textPrimary, fontSize = 13.sp),
                    singleLine = true,
                    cursorBrush = SolidColor(AppColors.blue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // La traduzione del nome (italiano -> inglese) in corso o riuscita.
            val isTranslating = state.searchQuery.length >= 3 && state.translatedQuery.isEmpty() &&
                !state.cards.any { it.name.contains(state.searchQuery, ignoreCase = true) }
            if (isTranslating) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = AppColors.blue, strokeWidth = 1.5.dp)
                Spacer(modifier = Modifier.width(4.dp))
            } else if (state.translatedQuery.isNotBlank() && state.searchQuery.isNotEmpty()) {
                Icon(Icons.Default.Translate, null, tint = AppColors.green, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            if (state.searchQuery.isNotEmpty()) {
                Icon(
                    Icons.Default.Close, AppLocale.cancel, tint = AppColors.textMuted,
                    modifier = Modifier.size(16.dp).clickable { onSearch("") }
                )
            }
        }
    }
}

@Composable
private fun SortButton(selected: SetCardSort, onSort: (SetCardSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = AppLocale.sortBy,
                // Acceso quando l'ordine non e' quello di sempre: la griglia
                // fuori ordine di numero deve dire perche'.
                tint = if (selected != SetCardSort.NUMBER) AppColors.blue else AppColors.textMuted
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = AppColors.card
        ) {
            Text(
                AppLocale.sortBy,
                color = AppColors.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            SetCardSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = {
                        Text(
                            sort.label(),
                            color = AppColors.textPrimary,
                            fontWeight = if (sort == selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    },
                    trailingIcon = {
                        if (sort == selected) Icon(Icons.Default.Check, null, tint = AppColors.blue, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                        onSort(sort)
                        open = false
                    }
                )
            }
        }
    }
}

private fun SetCardSort.label(): String = when (this) {
    SetCardSort.NUMBER -> AppLocale.sortByNumber
    SetCardSort.PRICE_DESC -> AppLocale.sortByPriceDesc
    SetCardSort.PRICE_ASC -> AppLocale.sortByPriceAsc
    SetCardSort.NAME -> AppLocale.sortByName
    SetCardSort.RARITY -> AppLocale.sortByRarity
    SetCardSort.HP_DESC -> AppLocale.sortByHp
}

@Composable
private fun BarChip(label: String, icon: ImageVector, selected: Boolean, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.2f) else AppColors.card)
            .border(1.dp, if (selected) color else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Icon(icon, null, tint = if (selected) color else AppColors.textMuted, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            color = if (selected) AppColors.textPrimary else AppColors.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** Un filtro acceso, con quello che serve per spegnerlo. */
class ActiveFilterChip(val label: String, val remove: (SetCardFilter) -> SetCardFilter)

fun activeFilterChips(filter: SetCardFilter): List<ActiveFilterChip> = buildList {
    if (filter.onlyWishlist) add(ActiveFilterChip(AppLocale.filterOnlyWishlist) { it.copy(onlyWishlist = false) })
    if (filter.missingVariant) add(ActiveFilterChip(AppLocale.filterMissingVariant) { it.copy(missingVariant = false) })
    filter.supertypes.forEach { v -> add(ActiveFilterChip(AppLocale.translateSupertype(v)) { it.copy(supertypes = it.supertypes - v) }) }
    filter.types.forEach { v -> add(ActiveFilterChip(AppLocale.translateType(v)) { it.copy(types = it.types - v) }) }
    filter.rarities.forEach { v -> add(ActiveFilterChip(AppLocale.translateRarity(v)) { it.copy(rarities = it.rarities - v) }) }
    filter.mechanics.forEach { v -> add(ActiveFilterChip(v.label(AppLocale.isItalian)) { it.copy(mechanics = it.mechanics - v) }) }
    filter.stages.forEach { v -> add(ActiveFilterChip(AppLocale.translateSubtype(v)) { it.copy(stages = it.stages - v) }) }
    filter.hpBuckets.forEach { v -> add(ActiveFilterChip(v.label) { it.copy(hpBuckets = it.hpBuckets - v) }) }
    filter.artists.forEach { v -> add(ActiveFilterChip(v) { it.copy(artists = it.artists - v) }) }
    filter.priceBuckets.forEach { v -> add(ActiveFilterChip(v.label) { it.copy(priceBuckets = it.priceBuckets - v) }) }
}

/**
 * Il pannello dei filtri, dal basso: lo stesso disegno di quello di "Cerca
 * carte" (sezioni che vanno a capo, non righe che scorrono di lato). Ogni
 * tocco filtra subito; il pulsante in fondo dice quante carte restano e
 * chiude.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetFilterSheet(
    filter: SetCardFilter,
    facets: SetCardFacets,
    resultCount: Int,
    onFilterChange: ((SetCardFilter) -> SetCardFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAllArtists by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(AppLocale.filters, color = AppColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (filter.panelCount > 0) {
                    Text(
                        AppLocale.clearFilters,
                        color = AppColors.blue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onFilterChange { it.clearPanel() } }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp)
            ) {
                SheetSection(AppLocale.filterCollectionSection) {
                    SeriesFilterChip(
                        label = AppLocale.filterOnlyWishlist,
                        count = 0,
                        showCount = false,
                        isSelected = filter.onlyWishlist,
                        onClick = { onFilterChange { it.copy(onlyWishlist = !it.onlyWishlist) } }
                    )
                    SeriesFilterChip(
                        label = AppLocale.filterMissingVariant,
                        count = 0,
                        showCount = false,
                        isSelected = filter.missingVariant,
                        onClick = { onFilterChange { it.copy(missingVariant = !it.missingVariant) } }
                    )
                }
                Text(
                    AppLocale.filterMissingVariantHint,
                    color = AppColors.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )

                if (facets.supertypes.size > 1) {
                    SheetSection(AppLocale.filterCategoryPrefix) {
                        facets.supertypes.forEach { (value, count) ->
                            SeriesFilterChip(
                                label = AppLocale.translateSupertype(value), count = count,
                                isSelected = value in filter.supertypes,
                                onClick = { onFilterChange { it.copy(supertypes = it.supertypes.toggle(value)) } }
                            )
                        }
                    }
                }

                if (facets.types.isNotEmpty()) {
                    SheetSection(AppLocale.cardType) {
                        facets.types.forEach { (value, count) ->
                            SeriesFilterChip(
                                label = AppLocale.translateType(value), count = count,
                                isSelected = value in filter.types,
                                onClick = { onFilterChange { it.copy(types = it.types.toggle(value)) } }
                            )
                        }
                    }
                }

                if (facets.rarities.isNotEmpty()) {
                    SheetSection(AppLocale.rarity) {
                        facets.rarities.forEach { (value, count) ->
                            // Col segno disegnato, e il nome della stringa esatta:
                            // l'etichetta raggruppata unirebbe rarita' diverse.
                            RarityFilterChip(
                                info = RarityUtils.getRarityInfo(value),
                                name = AppLocale.translateRarity(value),
                                count = count,
                                isSelected = value in filter.rarities,
                                onClick = { onFilterChange { it.copy(rarities = it.rarities.toggle(value)) } }
                            )
                        }
                    }
                }

                if (facets.mechanics.isNotEmpty()) {
                    SheetSection(AppLocale.cardVariant) {
                        facets.mechanics.forEach { (value, count) ->
                            SeriesFilterChip(
                                label = value.label(AppLocale.isItalian), count = count,
                                isSelected = value in filter.mechanics,
                                onClick = { onFilterChange { it.copy(mechanics = it.mechanics.toggle(value)) } }
                            )
                        }
                    }
                }

                if (facets.stages.isNotEmpty()) {
                    SheetSection(AppLocale.filterStage) {
                        facets.stages.forEach { (value, count) ->
                            SeriesFilterChip(
                                label = AppLocale.translateSubtype(value), count = count,
                                isSelected = value in filter.stages,
                                onClick = { onFilterChange { it.copy(stages = it.stages.toggle(value)) } }
                            )
                        }
                    }
                }

                if (facets.hpBuckets.isNotEmpty()) {
                    SheetSection(AppLocale.cardHp) {
                        facets.hpBuckets.forEach { (value, count) ->
                            SeriesFilterChip(
                                label = value.label, count = count,
                                isSelected = value in filter.hpBuckets,
                                onClick = { onFilterChange { it.copy(hpBuckets = it.hpBuckets.toggle(value)) } }
                            )
                        }
                    }
                }

                if (facets.artists.isNotEmpty()) {
                    // I primi dodici, piu' quelli gia' scelti anche se oltre.
                    val shown = if (showAllArtists) facets.artists else {
                        facets.artists.take(ARTISTS_PREVIEW) +
                            facets.artists.drop(ARTISTS_PREVIEW).filter { it.value in filter.artists }
                    }
                    SheetSection(AppLocale.illustrator) {
                        shown.forEach { (value, count) ->
                            SeriesFilterChip(
                                label = value, count = count,
                                isSelected = value in filter.artists,
                                onClick = { onFilterChange { it.copy(artists = it.artists.toggle(value)) } }
                            )
                        }
                        if (facets.artists.size > ARTISTS_PREVIEW) {
                            SeriesFilterChip(
                                label = if (showAllArtists) AppLocale.showLess else AppLocale.showAllArtists(facets.artists.size),
                                count = 0,
                                showCount = false,
                                isSelected = false,
                                onClick = { showAllArtists = !showAllArtists }
                            )
                        }
                    }
                }

                SheetSection(AppLocale.cardPrice) {
                    ItalianPriceBucket.entries.forEach { value ->
                        SeriesFilterChip(
                            label = value.label, count = 0, showCount = false,
                            isSelected = value in filter.priceBuckets,
                            onClick = { onFilterChange { it.copy(priceBuckets = it.priceBuckets.toggle(value)) } }
                        )
                    }
                }
                Text(
                    AppLocale.setPriceCaveat,
                    color = AppColors.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue)
            ) {
                Text(AppLocale.showCardsButton(resultCount), fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetSection(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Text(title, color = AppColors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
