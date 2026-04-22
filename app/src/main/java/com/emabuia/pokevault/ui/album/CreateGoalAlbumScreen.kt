package com.emabuia.pokevault.ui.album

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
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
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.emabuia.pokevault.data.model.GoalCriteriaType
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgSet
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.GoalAlbumViewModel

private fun safeImageUrl(url: String): String {
    return url
        .replace(" ", "%20")
        .replace("(", "%28")
        .replace(")", "%29")
}

@Composable
private fun GoalCardImageFallback(card: TcgCard, compact: Boolean) {
    val titleSize = if (compact) 7.sp else 9.sp
    val detailSize = if (compact) 6.sp else 7.sp
    val series = card.set?.series?.takeIf { it.isNotBlank() } ?: "-"
    val setName = card.set?.name?.takeIf { it.isNotBlank() } ?: "-"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .background(DarkSurface)
            .padding(if (compact) 4.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = card.name,
                color = TextWhite,
                fontSize = titleSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = series,
                color = TextMuted,
                fontSize = detailSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = setName,
                color = TextMuted,
                fontSize = detailSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGoalAlbumScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onPremiumRequired: () -> Unit,
    viewModel: GoalAlbumViewModel = viewModel()
) {
    var showPremiumDialog by remember { mutableStateOf(false) }
    var setSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadAvailableSets()
        if (!viewModel.canCreate()) {
            showPremiumDialog = true
        }
    }

    // Aggiorna la preview quando cambia criterio
    LaunchedEffect(viewModel.formCriteriaType, viewModel.formCriteriaValue) {
        if (viewModel.formCriteriaValue.isNotBlank()) {
            viewModel.loadPreview()
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(AppLocale.createChaseTitle, color = TextWhite, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppLocale.back,
                            tint = TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Nome ──────────────────────────────────────────────────────
            OutlinedTextField(
                value = viewModel.formName,
                onValueChange = { viewModel.formName = it },
                label = { Text(AppLocale.chaseNameLabel, color = TextGray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OrangeCard,
                    unfocusedBorderColor = TextMuted,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    cursorColor = OrangeCard
                )
            )

            // ── Tipo criterio ─────────────────────────────────────────────
            Text(AppLocale.chaseCriteriaTypeLabel, color = TextGray, fontSize = 13.sp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                GoalCriteriaType.entries.forEach { type ->
                    FilterChip(
                        selected = viewModel.formCriteriaType == type,
                        onClick = {
                            viewModel.formCriteriaType = type
                            viewModel.formCriteriaValue = ""
                        },
                        label = {
                            Text(
                                text = type.toLabel(),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OrangeCard,
                            selectedLabelColor = TextWhite,
                            containerColor = DarkSurface,
                            labelColor = TextGray
                        )
                    )
                }
            }

            // ── Valore criterio ───────────────────────────────────────────
            when (viewModel.formCriteriaType) {
                GoalCriteriaType.SET -> SetPicker(
                    sets = viewModel.availableSets,
                    selectedValue = viewModel.formCriteriaValue,
                    searchQuery = setSearchQuery,
                    onSearchChange = { setSearchQuery = it },
                    onSelect = { viewModel.formCriteriaValue = it }
                )
                GoalCriteriaType.RARITY -> RarityPicker(
                    selected = viewModel.formCriteriaValue,
                    onSelect = { viewModel.formCriteriaValue = it }
                )
                GoalCriteriaType.SUPERTYPE -> SupertypePicker(
                    selected = viewModel.formCriteriaValue,
                    onSelect = { viewModel.formCriteriaValue = it }
                )
                GoalCriteriaType.TYPE -> TypePicker(
                    selected = viewModel.formCriteriaValue,
                    onSelect = { viewModel.formCriteriaValue = it }
                )
                GoalCriteriaType.CUSTOM -> CustomCardSearch(viewModel)
            }

            // ── Preview ───────────────────────────────────────────────────
            if (viewModel.isPreviewLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OrangeCard, modifier = Modifier.size(28.dp))
                }
            } else if (viewModel.previewCards.isNotEmpty()) {
                PreviewSection(cards = viewModel.previewCards)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Salva ─────────────────────────────────────────────────────
            Button(
                onClick = {
                    if (!viewModel.canCreate()) {
                        showPremiumDialog = true
                        return@Button
                    }
                    viewModel.saveGoalAlbum(onSuccess = onSaved)
                },
                enabled = viewModel.formName.isNotBlank()
                    && viewModel.formCriteriaValue.isNotBlank()
                    && !viewModel.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeCard)
            ) {
                if (viewModel.isSaving) {
                    CircularProgressIndicator(color = TextWhite, modifier = Modifier.size(20.dp))
                } else {
                    Text(AppLocale.saveChase, color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showPremiumDialog) {
        PremiumRequiredDialog(
            title = AppLocale.premiumChaseLimitTitle,
            message = AppLocale.premiumChaseLimitMessage,
            onDismiss = { showPremiumDialog = false; onBack() },
            onUpgrade = {
                showPremiumDialog = false
                onPremiumRequired()
            }
        )
    }
}

// ── Set picker ────────────────────────────────────────────────────────────────

@Composable
private fun SetPicker(
    sets: List<TcgSet>,
    selectedValue: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    val filtered = remember(searchQuery, sets) {
        if (searchQuery.isBlank()) sets
        else sets.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(AppLocale.searchSet, color = TextMuted, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OrangeCard,
                unfocusedBorderColor = TextMuted,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                cursorColor = OrangeCard
            )
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.heightIn(max = 240.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(filtered, key = { _, s -> s.id }) { idx, set ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 })
                ) {
                    val isSelected = set.id == selectedValue
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) OrangeCard.copy(alpha = 0.2f) else DarkSurface)
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) OrangeCard else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelect(set.id) }
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(set.images.symbol)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = set.name,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = set.name,
                                color = if (isSelected) OrangeCard else TextWhite,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RarityPicker(selected: String, onSelect: (String) -> Unit) {
    val rarities = listOf(
        "Rare Holo EX", "Ultra Rare", "Secret Rare", "Hyper Rare",
        "Special Illustration Rare", "Illustration Rare", "Double Rare",
        "Rare Holo", "Rare", "Uncommon", "Common"
    )
    CriteriaChipList(options = rarities, selected = selected, onSelect = onSelect)
}

@Composable
private fun SupertypePicker(selected: String, onSelect: (String) -> Unit) {
    CriteriaChipList(options = listOf("Pokémon", "Trainer", "Energy"), selected = selected, onSelect = onSelect)
}

@Composable
private fun TypePicker(selected: String, onSelect: (String) -> Unit) {
    val types = listOf("Fire", "Water", "Grass", "Lightning", "Psychic", "Fighting", "Darkness", "Metal", "Dragon", "Fairy", "Colorless")
    CriteriaChipList(options = types, selected = selected, onSelect = onSelect)
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CriteriaChipList(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column {
        Text(AppLocale.chaseCriteriaValueLabel, color = TextGray, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(option, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OrangeCard,
                        selectedLabelColor = TextWhite,
                        containerColor = DarkSurface,
                        labelColor = TextGray
                    )
                )
            }
        }
    }
}

@Composable
private fun CustomCardSearch(viewModel: GoalAlbumViewModel) {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<TcgCard>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val tcgRepo = remember { com.emabuia.pokevault.data.remote.RepositoryProvider.tcgRepository }

    // Api ids selezionati, separati da virgola in formCriteriaValue
    val selectedIds = remember(viewModel.formCriteriaValue) {
        viewModel.formCriteriaValue.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableStateList()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(AppLocale.chaseCustomSearchLabel, color = TextGray, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(AppLocale.searchCardPlaceholder, color = TextMuted, fontSize = 14.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OrangeCard,
                    unfocusedBorderColor = TextMuted,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    cursorColor = OrangeCard
                )
            )
            Button(
                onClick = {
                    if (query.isNotBlank()) {
                        isSearching = true
                        scope.launch {
                            searchResults = tcgRepo.searchCards("name:\"$query\"").getOrElse { emptyList() }
                            isSearching = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = OrangeCard)
            ) {
                if (isSearching) {
                    CircularProgressIndicator(color = TextWhite, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextWhite)
                }
            }
        }

        if (searchResults.isNotEmpty()) {
            Text(
                "${searchResults.size} risultati — tocca per aggiungere/rimuovere",
                color = TextMuted,
                fontSize = 12.sp
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.heightIn(max = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(searchResults, key = { _, c -> c.id }) { _, card ->
                    val isSelected = card.id in selectedIds
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) OrangeCard else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                if (isSelected) selectedIds.remove(card.id) else selectedIds.add(card.id)
                                viewModel.formCriteriaValue = selectedIds.joinToString(",")
                            }
                    ) {
                        SubcomposeAsyncImage(
                            model = safeImageUrl(card.images.small),
                            contentDescription = card.name,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                            error = { GoalCardImageFallback(card = card, compact = false) }
                        )
                    }
                }
            }
        }

        if (selectedIds.isNotEmpty()) {
            Text(
                "${selectedIds.size} carte selezionate",
                color = OrangeCard,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PreviewSection(cards: List<TcgCard>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${cards.size} carte target",
            color = OrangeCard,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.heightIn(max = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(cards.take(12), key = { _, c -> c.id }) { _, card ->
                SubcomposeAsyncImage(
                    model = safeImageUrl(card.images.small),
                    contentDescription = card.name,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp)),
                    error = { GoalCardImageFallback(card = card, compact = true) }
                )
            }
        }
        if (cards.size > 12) {
            Text("+ ${cards.size - 12} altre carte", color = TextMuted, fontSize = 12.sp)
        }
    }
}

private fun GoalCriteriaType.toLabel(): String = when (this) {
    GoalCriteriaType.SET -> "Set"
    GoalCriteriaType.RARITY -> "Rarità"
    GoalCriteriaType.SUPERTYPE -> "Categoria"
    GoalCriteriaType.TYPE -> "Tipo"
    GoalCriteriaType.CUSTOM -> "Personalizz."
}
