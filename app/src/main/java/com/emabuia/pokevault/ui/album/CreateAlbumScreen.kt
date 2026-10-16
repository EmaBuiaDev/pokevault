package com.emabuia.pokevault.ui.album

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.data.model.AlbumCover
import com.emabuia.pokevault.data.model.BinderLayout
import com.emabuia.pokevault.data.model.CardClassifier
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AlbumEntries
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.AlbumViewModel

/** I colori fra cui scegliere, nell'ordine in cui si vedono. */
internal val ALBUM_THEMES = listOf(
    "classic", "fire", "water", "grass", "electric", "psychic", "dark", "metal", "fairy", "dragon"
)

/** Pagine proposte a un tocco: il resto si regola col - e il +. */
private val QUICK_PAGES = listOf(1, 2, 4, 8, 12, 20)

/**
 * Crea o modifica un album.
 *
 * Prima era un modulo di tendine: tipo, espansione e categoria in cima, una
 * grandezza in numeri astratti (9, 18, 36...) e nessuna idea di come sarebbe
 * venuto. Chi lo apriva pensava che le tendine fossero obbligatorie e non
 * capiva a cosa servissero.
 *
 * Ora si costruisce il raccoglitore: in cima l'anteprima che cambia mentre si
 * sceglie, poi il tipo di pagina (4, 9 o 12 tasche, come in negozio), quante
 * pagine e il colore. Le regole restano, chiuse e spiegate, per chi le vuole.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAlbumScreen(
    onBack: () -> Unit,
    editAlbumId: String? = null,
    viewModel: AlbumViewModel = viewModel()
) {
    // In modifica l'album puo' arrivare dopo il primo frame (Firestore): si
    // carica appena c'e', una volta sola, senza ricoprire quello che l'utente
    // sta gia' cambiando.
    var loadedForEdit by remember(editAlbumId) { mutableStateOf(false) }
    val editingAlbum = editAlbumId?.let { viewModel.getAlbumById(it) }
    LaunchedEffect(editAlbumId, editingAlbum != null) {
        if (editAlbumId == null) {
            viewModel.resetForm()
        } else if (editingAlbum != null && !loadedForEdit) {
            viewModel.loadAlbumForEdit(editingAlbum)
            loadedForEdit = true
        }
    }

    val isEditing = editAlbumId != null
    val layout = viewModel.albumLayout
    val minCards = viewModel.editingCardCount
    val pages = AlbumEntries.pagesFor(viewModel.albumSize, layout.perPage)
    val minPages = AlbumEntries.pagesFor(minCards.coerceAtLeast(1), layout.perPage)
    val maxPages = (AlbumEntries.MAX_SIZE / layout.perPage).coerceAtLeast(minPages)
    val themeColors = getThemeColors(viewModel.albumTheme)
    val accent = themeColors.first()
    val rulesCount = listOf(viewModel.albumPokemonType, viewModel.albumExpansion, viewModel.albumSupertype)
        .count { it.isNotBlank() }
    var rulesOpen by remember { mutableStateOf(false) }
    LaunchedEffect(loadedForEdit) { if (loadedForEdit && rulesCount > 0) rulesOpen = true }

    fun setPages(newPages: Int) {
        viewModel.albumSize = AlbumEntries.sizeFor(newPages.coerceIn(minPages, maxPages), layout.perPage, minCards)
    }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) AppLocale.editAlbum else AppLocale.createAlbum,
                        color = AppColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        },
        bottomBar = {
            // Il salva resta sempre sotto il pollice: prima stava in fondo al
            // modulo e con la tastiera aperta non si vedeva.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.background)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = { viewModel.saveAlbum(onSuccess = onBack) },
                    enabled = viewModel.albumName.isNotBlank() && !viewModel.isSaving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        disabledContainerColor = accent.copy(alpha = 0.35f)
                    )
                ) {
                    if (viewModel.isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (isEditing) Icons.Default.Check else Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isEditing) AppLocale.save else AppLocale.createAlbum,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            AlbumPreviewCard(
                name = viewModel.albumName,
                description = viewModel.albumDescription,
                colors = themeColors,
                theme = viewModel.albumTheme,
                coverPattern = viewModel.albumCoverPattern,
                coverPokemon = viewModel.albumCoverPokemon,
                layout = layout,
                pages = pages,
                pockets = viewModel.albumSize
            )

            AlbumTextField(
                value = viewModel.albumName,
                onValueChange = { viewModel.albumName = it.take(60) },
                label = AppLocale.albumName,
                placeholder = AppLocale.albumNamePlaceholder,
                accent = accent,
                singleLine = true
            )
            AlbumTextField(
                value = viewModel.albumDescription,
                onValueChange = { viewModel.albumDescription = it.take(200) },
                label = AppLocale.albumDescription,
                accent = accent,
                singleLine = false
            )

            // ── Pagine ────────────────────────────────────────────────────
            FormSection(title = AppLocale.albumLayoutTitle) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BinderLayout.entries.forEach { option ->
                        LayoutOption(
                            layout = option,
                            selected = option == layout,
                            accent = accent,
                            onClick = {
                                if (option != layout) {
                                    // Si tengono le pagine, cambia quante tasche hanno.
                                    val keepPages = pages
                                    viewModel.albumLayout = option
                                    viewModel.albumSize = AlbumEntries.sizeFor(
                                        keepPages.coerceAtMost(AlbumEntries.MAX_SIZE / option.perPage),
                                        option.perPage,
                                        minCards
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            FormSection(title = AppLocale.albumPagesTitle) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PageStepper(
                        pages = pages,
                        canDecrease = pages > minPages,
                        canIncrease = pages < maxPages,
                        accent = accent,
                        summary = AppLocale.albumPagesSummary(pages, viewModel.albumSize),
                        onChange = ::setPages
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(QUICK_PAGES.filter { it in minPages..maxPages }) { quick ->
                            ChoiceChip(
                                label = "$quick",
                                selected = quick == pages,
                                accent = accent,
                                onClick = { setPages(quick) }
                            )
                        }
                    }
                    if (minCards > 0 && pages == minPages) {
                        Text(AppLocale.albumMinPagesHint(minCards), color = AppColors.textMuted, fontSize = 12.sp)
                    }
                }
            }

            // ── Colore ────────────────────────────────────────────────────
            FormSection(title = AppLocale.albumColorTitle, trailing = getThemeLabel(viewModel.albumTheme)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(ALBUM_THEMES) { theme ->
                        ThemeSwatch(
                            colors = getThemeColors(theme),
                            selected = viewModel.albumTheme == theme,
                            label = getThemeLabel(theme),
                            onClick = { viewModel.albumTheme = theme }
                        )
                    }
                }
            }

            // ── Copertina ─────────────────────────────────────────────────
            FormSection(title = AppLocale.albumCoverTitle) {
                CoverPicker(viewModel = viewModel, accent = accent)
            }

            // ── Regole ────────────────────────────────────────────────────
            RulesSection(
                open = rulesOpen,
                activeCount = rulesCount,
                accent = accent,
                onToggle = { rulesOpen = !rulesOpen },
                viewModel = viewModel
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ── Anteprima ─────────────────────────────────────────────────────────────────

/**
 * Il raccoglitore come verra': copertina col colore scelto e la pagina con
 * le sue tasche. Cambia mentre si sceglie, cosi' "9 o 12 tasche" smette di
 * essere un numero e diventa una forma.
 */
@Composable
private fun AlbumPreviewCard(
    name: String,
    description: String,
    colors: List<Color>,
    theme: String,
    coverPattern: String,
    coverPokemon: Int,
    layout: BinderLayout,
    pages: Int,
    pockets: Int
) {
    val accent = colors.first()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.22f), AppColors.surface)))
            .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // La pagina aperta dietro, la copertina davanti
        Box(modifier = Modifier.width(128.dp).height(118.dp)) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .rotate(5f)
                    .width(66.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.background)
                    .border(1.dp, AppColors.textMuted.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .padding(5.dp)
            ) {
                PocketGrid(layout = layout, accent = accent, gap = 3.dp, filled = minOf(pockets, layout.perPage) / 2)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(84.dp)
                    .height(112.dp)
                    .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 14.dp, bottomEnd = 14.dp))
            ) {
                AlbumCoverArt(
                    theme = theme,
                    pattern = coverPattern,
                    pokemon = coverPokemon,
                    modifier = Modifier.fillMaxSize()
                )
                // Il dorso del raccoglitore
                Box(
                    modifier = Modifier
                        .padding(start = 7.dp)
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                name.ifBlank { AppLocale.albumPreviewUntitled },
                color = if (name.isBlank()) AppColors.textMuted else AppColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (description.isNotBlank()) {
                Text(
                    description,
                    color = AppColors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    AppLocale.albumPagesSummary(pages, pockets),
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** Le tasche di una pagina; le prime [filled] colorate, come carte gia' infilate. */
@Composable
private fun PocketGrid(layout: BinderLayout, accent: Color, gap: Dp, filled: Int = 0) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(layout.rows) { row ->
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(layout.columns) { col ->
                    val index = row * layout.columns + col
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (index < filled) accent.copy(alpha = 0.55f)
                                else AppColors.textMuted.copy(alpha = 0.18f)
                            )
                    )
                }
            }
        }
    }
}

// ── Mattoni del modulo ────────────────────────────────────────────────────────

@Composable
private fun FormSection(title: String, trailing: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = AppColors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (trailing != null) {
                Text(trailing, color = AppColors.textMuted, fontSize = 12.sp)
            }
        }
        content()
    }
}

@Composable
private fun AlbumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    accent: Color,
    singleLine: Boolean,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 3,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = AppColors.textMuted.copy(alpha = 0.45f),
            cursorColor = accent,
            focusedLabelColor = accent,
            unfocusedLabelColor = AppColors.textMuted,
            focusedTextColor = AppColors.textPrimary,
            unfocusedTextColor = AppColors.textPrimary,
            focusedPlaceholderColor = AppColors.textMuted,
            unfocusedPlaceholderColor = AppColors.textMuted,
            focusedContainerColor = AppColors.card,
            unfocusedContainerColor = AppColors.card
        ),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun LayoutOption(
    layout: BinderLayout,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border by animateColorAsState(
        if (selected) accent else AppColors.textMuted.copy(alpha = 0.25f),
        label = "layoutBorder"
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent.copy(alpha = 0.10f) else AppColors.card)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
            .pressScale(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.width(44.dp).height(52.dp)) {
            PocketGrid(layout = layout, accent = accent, gap = 2.dp, filled = if (selected) layout.perPage else 0)
        }
        Text(
            AppLocale.albumPocketsPerPage(layout.perPage),
            color = if (selected) accent else AppColors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun PageStepper(
    pages: Int,
    canDecrease: Boolean,
    canIncrease: Boolean,
    accent: Color,
    summary: String,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .border(1.dp, AppColors.textMuted.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton(icon = Icons.Default.Remove, enabled = canDecrease, accent = accent, onClick = { onChange(pages - 1) })
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$pages", color = AppColors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(summary, color = AppColors.textMuted, fontSize = 12.sp)
        }
        StepButton(icon = Icons.Default.Add, enabled = canIncrease, accent = accent, onClick = { onChange(pages + 1) })
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (enabled) accent.copy(alpha = 0.15f) else AppColors.background)
            .pressScale(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) accent else AppColors.textMuted.copy(alpha = 0.5f))
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent else AppColors.card)
            .border(1.dp, if (selected) accent else AppColors.textMuted.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .pressScale(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else AppColors.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun ThemeSwatch(colors: List<Color>, selected: Boolean, label: String, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1f else 0.86f, label = "swatch")
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) colors.first() else Color.Transparent, CircleShape)
            .padding(4.dp)
            .pressScale(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(scale)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Regole ────────────────────────────────────────────────────────────────────

/**
 * Le regole dell'album, chiuse finche' non servono.
 *
 * Erano le prime tre tendine del modulo, e sembravano obbligatorie. Ora si
 * aprono a richiesta, dicono a cosa servono, e si scelgono a chip invece che
 * da un menu.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RulesSection(
    open: Boolean,
    activeCount: Int,
    accent: Color,
    onToggle: () -> Unit,
    viewModel: AlbumViewModel
) {
    val arrow by animateFloatAsState(if (open) 180f else 0f, label = "rulesArrow")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.card)
            .border(1.dp, AppColors.textMuted.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(scaleDown = 0.99f, onClick = onToggle)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                AppLocale.albumRulesTitle,
                color = AppColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (activeCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accent)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("$activeCount", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = AppColors.textMuted,
                modifier = Modifier.rotate(arrow)
            )
        }

        AnimatedVisibility(
            visible = open,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(AppLocale.albumRulesHint, color = AppColors.textMuted, fontSize = 12.sp)

                RuleLabel(AppLocale.albumPokemonType)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChoiceChip(AppLocale.albumRuleAny, viewModel.albumPokemonType.isBlank(), accent) {
                        viewModel.albumPokemonType = ""
                    }
                    AppLocale.getTypes().forEach { type ->
                        val selected = AppLocale.typeToEnglish(viewModel.albumPokemonType)
                            .equals(AppLocale.typeToEnglish(type), ignoreCase = true) && viewModel.albumPokemonType.isNotBlank()
                        ChoiceChip(type, selected, accent) {
                            viewModel.albumPokemonType = type
                        }
                    }
                }

                RuleLabel(AppLocale.albumCategory)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChoiceChip(AppLocale.albumRuleAny, viewModel.albumSupertype.isBlank(), accent) {
                        viewModel.albumSupertype = ""
                    }
                    listOf(
                        CardClassifier.POKEMON to AppLocale.categoryPokemon,
                        CardClassifier.TRAINER to AppLocale.categoryTrainer,
                        CardClassifier.ENERGY to AppLocale.categoryEnergy
                    ).forEach { (value, label) ->
                        ChoiceChip(label, viewModel.albumSupertype.equals(value, ignoreCase = true), accent) {
                            viewModel.albumSupertype = value
                        }
                    }
                }

                RuleLabel(AppLocale.albumExpansion)
                ExpansionPicker(viewModel = viewModel, accent = accent)
            }
        }
    }
}

@Composable
private fun RuleLabel(text: String) {
    Text(text, color = AppColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

/** Le espansioni sono decine: per loro resta il menu, ma dentro le regole. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpansionPicker(viewModel: AlbumViewModel, accent: Color) {
    var expanded by remember { mutableStateOf(false) }
    val expansions = remember(viewModel.ownedCards) { listOf("") + viewModel.getAvailableExpansions() }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = viewModel.albumExpansion.ifBlank { AppLocale.albumAllExpansions },
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = AppColors.textMuted.copy(alpha = 0.4f),
                focusedTextColor = AppColors.textPrimary,
                unfocusedTextColor = AppColors.textPrimary,
                focusedTrailingIconColor = accent,
                unfocusedTrailingIconColor = AppColors.textMuted,
                focusedContainerColor = AppColors.background,
                unfocusedContainerColor = AppColors.background
            ),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = AppColors.surface
        ) {
            expansions.forEach { exp ->
                DropdownMenuItem(
                    text = { Text(exp.ifBlank { AppLocale.albumAllExpansions }, color = AppColors.textPrimary) },
                    onClick = {
                        viewModel.albumExpansion = exp
                        expanded = false
                    }
                )
            }
        }
    }
}

internal fun getThemeLabel(theme: String): String {
    return when (theme) {
        "fire" -> AppLocale.albumThemeFire
        "water" -> AppLocale.albumThemeWater
        "grass" -> AppLocale.albumThemeGrass
        "electric" -> AppLocale.albumThemeElectric
        "dark" -> AppLocale.albumThemeDark
        "psychic" -> AppLocale.albumThemePsychic
        "metal" -> AppLocale.albumThemeMetal
        "fairy" -> AppLocale.albumThemeFairy
        "dragon" -> AppLocale.albumThemeDragon
        else -> AppLocale.albumThemeClassic
    }
}

// ── Copertina ─────────────────────────────────────────────────────────────────

/**
 * La copertina: un motivo e, se si vuole, un Pokemon cercato per nome.
 *
 * Volutamente niente carte: una carta rimpicciolita nell'elenco degli album
 * non si riconosce, un Pokemon disegnato si'.
 */
@Composable
private fun CoverPicker(viewModel: AlbumViewModel, accent: Color) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                AlbumCover.DOTS to AppLocale.albumCoverDots,
                AlbumCover.STRIPES to AppLocale.albumCoverStripes,
                AlbumCover.PLAIN to AppLocale.albumCoverPlain
            ).forEach { (pattern, label) ->
                ChoiceChip(label, viewModel.albumCoverPattern == pattern, accent) {
                    viewModel.albumCoverPattern = pattern
                }
            }
        }

        OutlinedTextField(
            value = viewModel.speciesQuery,
            onValueChange = { viewModel.searchSpecies(it, context) },
            placeholder = { Text(AppLocale.albumCoverSearchHint, color = AppColors.textMuted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = AppColors.textMuted.copy(alpha = 0.45f),
                cursorColor = accent,
                focusedTextColor = AppColors.textPrimary,
                unfocusedTextColor = AppColors.textPrimary,
                focusedLeadingIconColor = accent,
                unfocusedLeadingIconColor = AppColors.textMuted,
                focusedContainerColor = AppColors.card,
                unfocusedContainerColor = AppColors.card
            )
        )

        val results = viewModel.speciesResults
        if (viewModel.speciesQuery.isNotBlank() && results.isEmpty()) {
            Text(AppLocale.albumCoverNoMatch, color = AppColors.textMuted, fontSize = 12.sp)
        }
        if (results.isNotEmpty() || viewModel.albumCoverPokemon > 0) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "none") {
                    PokemonTile(
                        dex = 0,
                        name = AppLocale.albumCoverNoPokemon,
                        selected = viewModel.albumCoverPokemon == 0,
                        accent = accent,
                        onClick = { viewModel.albumCoverPokemon = 0 }
                    )
                }
                items(results, key = { "${it.dex}-${it.name}" }) { species ->
                    PokemonTile(
                        dex = species.dex,
                        name = species.name,
                        selected = viewModel.albumCoverPokemon == species.dex,
                        accent = accent,
                        onClick = { viewModel.albumCoverPokemon = species.dex }
                    )
                }
            }
        }
    }
}

@Composable
private fun PokemonTile(dex: Int, name: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.12f) else AppColors.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else AppColors.textMuted.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .pressScale(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val artwork = AlbumCover.artworkUrl(dex)
        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            if (artwork != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(artwork).crossfade(true).build(),
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Block, contentDescription = null, tint = AppColors.textMuted, modifier = Modifier.size(26.dp))
            }
        }
        Text(
            name,
            color = if (selected) accent else AppColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
