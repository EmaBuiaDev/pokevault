package com.emabuia.pokevault.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.data.model.GoalAlbum
import com.emabuia.pokevault.ui.components.CascadeIn
import com.emabuia.pokevault.ui.components.CoverCollage
import com.emabuia.pokevault.ui.components.FillBar
import com.emabuia.pokevault.ui.components.ProgressRing
import com.emabuia.pokevault.ui.components.SkeletonBlock
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.illustrator.IllustratorAvatar
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AlbumRow
import com.emabuia.pokevault.util.AlbumSort
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.ChaseRow
import com.emabuia.pokevault.util.ChaseSort
import com.emabuia.pokevault.util.CollectorLab
import com.emabuia.pokevault.util.IllustratorRow
import com.emabuia.pokevault.viewmodel.AlbumViewModel
import com.emabuia.pokevault.viewmodel.GoalAlbumViewModel
import com.emabuia.pokevault.viewmodel.IllustratorViewModel

/**
 * L'ingresso del Collector Lab: tre modi di collezionare, ognuno una volta.
 *
 * Prima la pagina diceva le stesse cose due o tre volte: un riassunto che
 * mescolava album e chase, poi le card d'ingresso di Album e Chase, poi un
 * "quasi fatto" che era di nuovo un chase, poi gli album recenti che erano di
 * nuovo gli album, e in fondo i bottoni per crearli -- una terza volta. Chi
 * apriva non capiva quale riquadro usare, e toccare la card Album vuota
 * *creava* un album invece di aprire la sezione.
 *
 * Ora ogni strumento ha una sezione sola, con la stessa forma per tutti: cosa
 * fa, il suo contenuto vero, e il "+ Nuovo" in fondo alla sua fila. In cima
 * un riquadro solo, il prossimo passo concreto. Toccare non crea mai niente
 * di nascosto: si crea solo dal tasto che dice "Nuovo".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumListScreen(
    onBack: () -> Unit,
    onCreateAlbum: (String?) -> Unit,
    onAlbumClick: (String) -> Unit,
    onOpenAlbumList: () -> Unit = {},
    onOpenChaseList: () -> Unit = {},
    onOpenIllustrators: () -> Unit = {},
    onIllustratorClick: (String) -> Unit = {},
    onCreateChase: () -> Unit = {},
    onChaseClick: (String) -> Unit = {},
    onPremiumRequired: () -> Unit = {},
    viewModel: AlbumViewModel = viewModel(),
    goalViewModel: GoalAlbumViewModel = viewModel(),
    illustratorViewModel: IllustratorViewModel = viewModel()
) {
    var showChasePremiumDialog by remember { mutableStateOf(false) }
    var showAlbumPremiumDialog by remember { mutableStateOf(false) }
    val premiumManager = remember { com.emabuia.pokevault.data.billing.PremiumManager.getInstance() }

    val context = LocalContext.current
    LaunchedEffect(Unit) { illustratorViewModel.loadIndex(context) }

    val albumRows = viewModel.albumRows
    val chaseRows = remember(goalViewModel.goalAlbums, goalViewModel.ownedCards) {
        goalViewModel.chaseRows { it.criteriaSummary() }
    }
    val illustratorRows = illustratorViewModel.rows

    val recentAlbums = remember(albumRows) { CollectorLab.sortAlbums(albumRows, AlbumSort.RECENT).take(8) }
    val topChases = remember(chaseRows) { CollectorLab.sortChases(chaseRows, ChaseSort.CLOSEST).take(3) }
    // Gli artisti che contano per te: i seguiti prima, poi quelli di cui hai
    // gia' qualche carta, dal piu' avanti. Gli altri trecento e passa non
    // stanno in vetrina -- sono a un tocco, in "Vedi tutti".
    val myIllustrators = remember(illustratorRows) {
        illustratorRows
            .filter { it.isFollowed || it.owned > 0 }
            .sortedWith(
                compareByDescending<IllustratorRow> { it.isFollowed }
                    .thenByDescending { it.percent }
                    .thenByDescending { it.owned }
            )
            .take(10)
    }
    val startedIllustrators = remember(illustratorRows) { illustratorRows.count { it.owned > 0 } }
    val nextStep = remember(chaseRows, illustratorRows) { pickNextStep(chaseRows, illustratorRows) }

    val isLoading = viewModel.isLoading || goalViewModel.isLoading

    // La cascata si gioca una volta sola, al primo arrivo dei dati: rigiocarla a
    // ogni ricomposizione la trasformerebbe in un inciampo.
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) { if (!isLoading) revealed = true }

    fun createAlbum() {
        if (premiumManager.canCreateAlbum(albumRows.size)) onCreateAlbum(null)
        else showAlbumPremiumDialog = true
    }

    fun createChase() {
        if (goalViewModel.canCreate()) onCreateChase() else showChasePremiumDialog = true
    }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(AppLocale.albumTitle, color = AppColors.textPrimary, fontWeight = FontWeight.Bold)
                        Text(AppLocale.collectorLabSubtitle, color = AppColors.textMuted, fontSize = 11.sp)
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        }
    ) { padding ->
        if (isLoading && albumRows.isEmpty() && chaseRows.isEmpty()) {
            CollectorLabSkeleton(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)
        ) {
            // ── In cima: una cosa sola da fare ───────────────────────────
            item(key = "hero") {
                CascadeIn(index = 0, visible = revealed, modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Si aspetta l'indice degli illustratori prima di scegliere:
                    // decidere coi soli chase mostrava un riquadro e poi lo
                    // cambiava sotto il dito appena arrivavano gli artisti.
                    if (illustratorViewModel.isLoading) {
                        SkeletonBlock(
                            modifier = Modifier.fillMaxWidth().height(130.dp),
                            shape = RoundedCornerShape(22.dp),
                            index = 0
                        )
                    } else if (nextStep != null) {
                        NextStepCard(
                            step = nextStep,
                            onClick = {
                                when (nextStep.kind) {
                                    NextStepKind.CHASE -> onChaseClick(nextStep.id)
                                    NextStepKind.ILLUSTRATOR -> onIllustratorClick(nextStep.id)
                                }
                            }
                        )
                    } else {
                        IntroCard()
                    }
                }
            }

            // ── Album ─────────────────────────────────────────────────────
            item(key = "album-header") {
                CascadeIn(index = 1, visible = revealed) {
                    LabSectionHeader(
                        icon = Icons.Default.PhotoLibrary,
                        accent = AppColors.orange,
                        title = AppLocale.collectorAlbumTitle,
                        count = albumRows.size,
                        hint = AppLocale.collectorAlbumHint,
                        onSeeAll = if (albumRows.isNotEmpty()) onOpenAlbumList else null
                    )
                }
            }
            item(key = "album-row") {
                CascadeIn(index = 2, visible = revealed) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(recentAlbums, key = { it.id }) { row ->
                            RecentAlbumCard(row = row, onClick = { onAlbumClick(row.id) })
                        }
                        item(key = "new-album") {
                            NewTile(
                                label = AppLocale.collectorNewAlbum,
                                hint = if (albumRows.isEmpty()) AppLocale.collectorAlbumEmptyHint else null,
                                accent = AppColors.orange,
                                wide = albumRows.isEmpty(),
                                onClick = { createAlbum() }
                            )
                        }
                    }
                }
            }

            // ── Chase ─────────────────────────────────────────────────────
            item(key = "chase-header") {
                CascadeIn(index = 3, visible = revealed) {
                    LabSectionHeader(
                        icon = Icons.Default.TrackChanges,
                        accent = AppColors.red,
                        title = AppLocale.collectorChaseTitle,
                        count = chaseRows.size,
                        hint = AppLocale.collectorChaseHint,
                        onSeeAll = if (chaseRows.isNotEmpty()) onOpenChaseList else null
                    )
                }
            }
            item(key = "chase-list") {
                CascadeIn(index = 4, visible = revealed, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        topChases.forEach { row ->
                            ChaseMiniRow(row = row, onClick = { onChaseClick(row.id) })
                        }
                        NewTile(
                            label = AppLocale.collectorNewChase,
                            hint = if (chaseRows.isEmpty()) AppLocale.collectorChaseEmptyHint else null,
                            accent = AppColors.red,
                            wide = true,
                            onClick = { createChase() }
                        )
                    }
                }
            }

            // ── Illustratori ──────────────────────────────────────────────
            item(key = "illustrators-header") {
                CascadeIn(index = 5, visible = revealed) {
                    LabSectionHeader(
                        icon = Icons.Default.Brush,
                        accent = AppColors.purple,
                        title = AppLocale.illustratorsTitle,
                        count = startedIllustrators,
                        hint = AppLocale.collectorIllustratorsHint,
                        onSeeAll = onOpenIllustrators
                    )
                }
            }
            item(key = "illustrators-row") {
                CascadeIn(index = 6, visible = revealed) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(myIllustrators, key = { it.key }) { row ->
                            IllustratorTile(row = row, onClick = { onIllustratorClick(row.key) })
                        }
                        item(key = "discover") {
                            NewTile(
                                label = if (illustratorRows.isEmpty()) AppLocale.illustratorsTitle
                                else AppLocale.collectorDiscoverIllustrators(illustratorRows.size),
                                hint = if (myIllustrators.isEmpty()) AppLocale.collectorIllustratorsEmptyHint else null,
                                accent = AppColors.purple,
                                wide = myIllustrators.isEmpty(),
                                icon = Icons.AutoMirrored.Filled.ArrowForward,
                                onClick = onOpenIllustrators
                            )
                        }
                    }
                }
            }
        }
    }

    if (showChasePremiumDialog) {
        PremiumRequiredDialog(
            title = AppLocale.premiumChaseLimitTitle,
            message = AppLocale.premiumChaseLimitMessage,
            onDismiss = { showChasePremiumDialog = false },
            onUpgrade = {
                showChasePremiumDialog = false
                onPremiumRequired()
            }
        )
    }

    if (showAlbumPremiumDialog) {
        PremiumRequiredDialog(
            title = AppLocale.premiumAlbumLimitTitle,
            message = AppLocale.premiumAlbumLimitMessage,
            onDismiss = { showAlbumPremiumDialog = false },
            onUpgrade = {
                showAlbumPremiumDialog = false
                onPremiumRequired()
            }
        )
    }
}

// ── Il prossimo passo ─────────────────────────────────────────────────────────

internal enum class NextStepKind { CHASE, ILLUSTRATOR }

internal data class NextStep(
    val kind: NextStepKind,
    val id: String,
    val name: String,
    val owned: Int,
    val total: Int,
    val percent: Float,
    val previewUrls: List<String>
) {
    val missing: Int get() = (total - owned).coerceAtLeast(0)
}

/**
 * Il traguardo piu' vicino fra chase e illustratori iniziati: quello a cui
 * mancano meno carte. Non la percentuale piu' alta -- un artista da 579 carte
 * non sara' mai "al 90%", ma se gliene mancano due e' comunque la cosa piu'
 * facile da chiudere oggi.
 *
 * Solo cose avviate e non finite: un chase completato non e' piu' un passo, e
 * un artista di cui non hai nessuna carta non e' un traguardo vicino.
 */
internal fun pickNextStep(chases: List<ChaseRow>, illustrators: List<IllustratorRow>): NextStep? {
    val fromChases = chases
        .filter { it.total > 0 && it.owned > 0 && !it.isComplete }
        .map { NextStep(NextStepKind.CHASE, it.id, it.name, it.owned, it.total, it.percent, emptyList()) }
    val fromIllustrators = illustrators
        .filter { it.owned > 0 && !it.isComplete }
        .map {
            NextStep(NextStepKind.ILLUSTRATOR, it.key, it.displayName, it.owned, it.total, it.percent, it.previewUrls)
        }
    return (fromChases + fromIllustrators)
        .minWithOrNull(compareBy<NextStep> { it.missing }.thenByDescending { it.percent })
}

@Composable
private fun NextStepCard(step: NextStep, onClick: () -> Unit) {
    val accent = if (step.kind == NextStepKind.CHASE) AppColors.red else AppColors.purple
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.22f), AppColors.surface)))
            .pressScale(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(text = AppLocale.collectorNextStep, accent = accent)
            Spacer(modifier = Modifier.width(6.dp))
            Pill(
                text = if (step.kind == NextStepKind.CHASE) AppLocale.collectorChaseTitle else AppLocale.illustrator,
                accent = AppColors.textMuted
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = AppColors.textMuted,
                modifier = Modifier.size(16.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(percent = step.percent, size = 58.dp, stroke = 5.dp, accent = accent, labelSize = 12)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    step.name,
                    color = AppColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    AppLocale.collectorMissingToFinish(step.missing),
                    color = AppColors.textSecondary,
                    fontSize = 13.sp
                )
                Text("${step.owned}/${step.total}", color = AppColors.textMuted, fontSize = 11.sp)
            }
            if (step.previewUrls.isNotEmpty()) {
                CoverCollage(
                    urls = step.previewUrls,
                    gradient = listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.12f)),
                    slotSize = 34.dp
                )
            }
        }
    }
}

/** Per chi e' all'inizio: i tre strumenti spiegati in una riga ciascuno. */
@Composable
private fun IntroCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(AppColors.orange.copy(alpha = 0.16f), AppColors.surface)))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            AppLocale.collectorIntroTitle,
            color = AppColors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )
        IntroLine(Icons.Default.PhotoLibrary, AppColors.orange, AppLocale.collectorAlbumTitle, AppLocale.collectorAlbumHint)
        IntroLine(Icons.Default.TrackChanges, AppColors.red, AppLocale.collectorChaseTitle, AppLocale.collectorChaseHint)
        IntroLine(Icons.Default.Brush, AppColors.purple, AppLocale.illustratorsTitle, AppLocale.collectorIllustratorsHint)
    }
}

@Composable
private fun IntroLine(icon: ImageVector, accent: Color, title: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon = icon, accent = accent, size = 30.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, color = AppColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = AppColors.textMuted, fontSize = 12.sp)
        }
    }
}

// ── Mattoni comuni alle tre sezioni ───────────────────────────────────────────

/**
 * L'intestazione di una sezione: icona, nome, quanti ne hai, cosa fa, e "Vedi
 * tutti". La riga di spiegazione e' li' per chi apre la prima volta: senza,
 * "Chase" non dice niente a nessuno.
 */
@Composable
private fun LabSectionHeader(
    icon: ImageVector,
    accent: Color,
    title: String,
    count: Int,
    hint: String,
    onSeeAll: (() -> Unit)?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp)
    ) {
        IconBadge(icon = icon, accent = accent, size = 34.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = AppColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (count > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Pill(text = count.toString(), accent = accent)
                }
            }
            Text(hint, color = AppColors.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text(AppLocale.collectorSeeAll, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, accent: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.2f))
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun Pill(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Il tasto "+ Nuovo" in fondo alla fila della sua sezione. Quando la sezione e'
 * vuota si allarga e porta una riga di spiegazione: e' l'unica cosa sullo
 * schermo per quello strumento, deve dire lui cosa succede toccandolo.
 */
@Composable
private fun NewTile(
    label: String,
    hint: String?,
    accent: Color,
    wide: Boolean,
    onClick: () -> Unit,
    icon: ImageVector = Icons.Default.Add
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .then(if (wide) Modifier.fillParentOrWidth() else Modifier.width(132.dp).height(172.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .pressScale(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = if (wide) Arrangement.Start else Arrangement.Center
    ) {
        if (wide) {
            IconBadge(icon = icon, accent = accent, size = 34.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (hint != null) {
                    Text(hint, color = AppColors.textMuted, fontSize = 12.sp)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconBadge(icon = icon, accent = accent, size = 38.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    label,
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/**
 * Dentro una LazyRow `fillMaxWidth` non ha un genitore da riempire e il riquadro
 * si stringe al contenuto: la larghezza dello schermo, meno i margini, e' la
 * misura che serve perche' la tessera vuota abbia l'aria di una card e non di
 * un bottone.
 */
@Composable
private fun Modifier.fillParentOrWidth(): Modifier {
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    return this.width(screenWidth - 32.dp)
}

// ── Contenuti delle sezioni ───────────────────────────────────────────────────

@Composable
private fun RecentAlbumCard(row: AlbumRow, onClick: () -> Unit) {
    val gradient = getThemeColors(row.theme)
    Column(
        modifier = Modifier
            .width(132.dp)
            .height(172.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.surface)
            .pressScale(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CoverCollage(urls = row.previewUrls, gradient = gradient, slotSize = 56.dp)
        Text(
            row.name,
            color = AppColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(AppLocale.albumSlots(row.used, row.size), color = AppColors.textMuted, fontSize = 11.sp)
        FillBar(percent = row.fillPercent, modifier = Modifier.fillMaxWidth(), accent = gradient.first())
    }
}

@Composable
private fun ChaseMiniRow(row: ChaseRow, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.surface)
            .pressScale(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        ProgressRing(percent = row.percent, size = 42.dp, stroke = 3.dp, accent = AppColors.red, labelSize = 10)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.name,
                color = AppColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                row.criteriaLabel,
                color = AppColors.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${row.owned}/${row.total}",
                color = if (row.isComplete) AppColors.gold else AppColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (row.isComplete) AppLocale.chaseCompleteBadge else AppLocale.chaseMissingCount(row.missing),
                color = AppColors.textMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun IllustratorTile(row: IllustratorRow, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .height(172.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.surface)
            .pressScale(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.Top) {
            IllustratorAvatar(key = row.key, displayName = row.displayName, size = 42.dp)
            Spacer(modifier = Modifier.weight(1f))
            if (row.isFollowed) {
                Icon(Icons.Default.Star, contentDescription = null, tint = AppColors.gold, modifier = Modifier.size(16.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                row.displayName,
                color = AppColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text("${row.owned}/${row.total}", color = AppColors.textMuted, fontSize = 11.sp)
            FillBar(percent = row.percent, modifier = Modifier.fillMaxWidth(), accent = AppColors.purple)
        }
    }
}

// ── Caricamento ───────────────────────────────────────────────────────────────

@Composable
private fun CollectorLabSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth().height(130.dp), shape = RoundedCornerShape(22.dp), index = 0)
        repeat(3) { section ->
            SkeletonBlock(modifier = Modifier.fillMaxWidth(0.6f).height(34.dp), index = section * 2 + 1)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) {
                    SkeletonBlock(
                        modifier = Modifier.width(132.dp).height(172.dp),
                        shape = RoundedCornerShape(16.dp),
                        index = section * 2 + 2
                    )
                }
            }
        }
    }
}

// ── Helpers condivisi con le altre schermate della sezione ────────────────────

/** "Set · Paldea Evolved": il criterio del chase in una riga. */
internal fun GoalAlbum.criteriaSummary(): String =
    criteriaType.displayName() + (if (criteriaValue.isNotBlank()) " · $criteriaValue" else "")

internal fun com.emabuia.pokevault.data.model.GoalCriteriaType.displayName(): String = when (this) {
    com.emabuia.pokevault.data.model.GoalCriteriaType.SET -> AppLocale.criteriaSet
    com.emabuia.pokevault.data.model.GoalCriteriaType.RARITY -> AppLocale.criteriaRarity
    com.emabuia.pokevault.data.model.GoalCriteriaType.SUPERTYPE -> AppLocale.criteriaSupertype
    com.emabuia.pokevault.data.model.GoalCriteriaType.TYPE -> AppLocale.criteriaType
    com.emabuia.pokevault.data.model.GoalCriteriaType.CUSTOM -> AppLocale.criteriaCustom
}

/** Vedi [TypeColors]: i colori dei tipi stanno tutti in un punto solo. */
@Composable
fun getThemeColors(theme: String): List<Color> = TypeColors.gradientFor(theme)
