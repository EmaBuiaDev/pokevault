package com.emabuia.pokevault.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.emabuia.pokevault.ui.components.FillBar
import com.emabuia.pokevault.ui.components.ProgressRing
import com.emabuia.pokevault.ui.components.SkeletonBlock
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AlbumRow
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.ChaseRow
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

    // Le tre porte: quanti ne hai e quanto sei avanti, tutto insieme.
    val albumFill = remember(albumRows) { CollectorLab.fillPercent(albumRows.sumOf { it.used }, albumRows.sumOf { it.size }) }
    val chaseFill = remember(chaseRows) { CollectorLab.fillPercent(chaseRows.sumOf { it.owned }, chaseRows.sumOf { it.total }) }
    val startedIllustrators = remember(illustratorRows) { illustratorRows.count { it.owned > 0 } }
    val illustratorFill = remember(illustratorRows, startedIllustrators) {
        CollectorLab.fillPercent(startedIllustrators, illustratorRows.size)
    }
    val continueSteps = remember(albumRows, chaseRows, illustratorRows) {
        pickContinueSteps(albumRows, chaseRows, illustratorRows)
    }

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
            // ── Le tre porte: Album, Chase, Illustratori ──────────────────
            item(key = "doors") {
                CascadeIn(index = 0, visible = revealed, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LabDoor(
                            icon = Icons.Default.PhotoLibrary,
                            accent = AppColors.orange,
                            title = AppLocale.collectorAlbumTitle,
                            value = albumRows.size.toString(),
                            percent = albumFill,
                            onClick = onOpenAlbumList,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        LabDoor(
                            icon = Icons.Default.TrackChanges,
                            accent = AppColors.red,
                            title = AppLocale.collectorChaseTitle,
                            value = chaseRows.size.toString(),
                            percent = chaseFill,
                            onClick = onOpenChaseList,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        LabDoor(
                            icon = Icons.Default.Brush,
                            accent = AppColors.purple,
                            title = AppLocale.collectorArtistsShort,
                            value = if (illustratorViewModel.isLoading) "…" else "$startedIllustrators/${illustratorRows.size}",
                            percent = illustratorFill,
                            onClick = onOpenIllustrators,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }

            // ── Continua: le tre cose piu' vicine al traguardo ────────────
            item(key = "continue") {
                CascadeIn(index = 1, visible = revealed, modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Si aspetta l'indice degli illustratori prima di scegliere:
                    // decidere senza gli artisti cambierebbe la lista sotto il dito.
                    if (illustratorViewModel.isLoading) {
                        SkeletonBlock(
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            shape = RoundedCornerShape(18.dp),
                            index = 0
                        )
                    } else if (continueSteps.isEmpty()) {
                        IntroCard()
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                AppLocale.collectorContinue,
                                color = AppColors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            )
                            continueSteps.forEach { step ->
                                ContinueRow(step = step, onClick = {
                                    when (step.kind) {
                                        NextStepKind.ALBUM -> onAlbumClick(step.id)
                                        NextStepKind.CHASE -> onChaseClick(step.id)
                                        NextStepKind.ILLUSTRATOR -> onIllustratorClick(step.id)
                                    }
                                })
                            }
                        }
                    }
                }
            }

            // ── Nuovo ─────────────────────────────────────────────────────
            item(key = "new") {
                CascadeIn(index = 2, visible = revealed, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NewButton(
                            label = AppLocale.collectorNewAlbum,
                            accent = AppColors.orange,
                            onClick = { createAlbum() },
                            modifier = Modifier.weight(1f)
                        )
                        NewButton(
                            label = AppLocale.collectorNewChase,
                            accent = AppColors.red,
                            onClick = { createChase() },
                            modifier = Modifier.weight(1f)
                        )
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

// ── Continua ──────────────────────────────────────────────────────────────────

internal enum class NextStepKind { ALBUM, CHASE, ILLUSTRATOR }

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
 * I traguardi piu' vicini fra album, chase e illustratori iniziati: quelli a
 * cui mancano meno carte. Non la percentuale piu' alta -- un artista da 579
 * carte non sara' mai "al 90%", ma se gliene mancano due e' comunque la cosa
 * piu' facile da chiudere oggi.
 *
 * Solo cose avviate e non finite: un chase completato non e' piu' un passo, e
 * un artista di cui non hai nessuna carta non e' un traguardo vicino.
 */
internal fun pickContinueSteps(
    albums: List<AlbumRow>,
    chases: List<ChaseRow>,
    illustrators: List<IllustratorRow>,
    limit: Int = 3
): List<NextStep> {
    val fromAlbums = albums
        .filter { it.size > 0 && it.used > 0 && !it.isFull }
        .map { NextStep(NextStepKind.ALBUM, it.id, it.name, it.used, it.size, it.fillPercent, it.previewUrls) }
    val fromChases = chases
        .filter { it.total > 0 && it.owned > 0 && !it.isComplete }
        .map { NextStep(NextStepKind.CHASE, it.id, it.name, it.owned, it.total, it.percent, emptyList()) }
    val fromIllustrators = illustrators
        .filter { it.owned > 0 && !it.isComplete }
        .map {
            NextStep(NextStepKind.ILLUSTRATOR, it.key, it.displayName, it.owned, it.total, it.percent, it.previewUrls)
        }
    return (fromAlbums + fromChases + fromIllustrators)
        .sortedWith(compareBy<NextStep> { it.missing }.thenByDescending { it.percent })
        .take(limit)
}

/** Il primo passo fra chase e illustratori (la regola di [pickContinueSteps]). */
internal fun pickNextStep(chases: List<ChaseRow>, illustrators: List<IllustratorRow>): NextStep? =
    pickContinueSteps(emptyList(), chases, illustrators, limit = 1).firstOrNull()

@Composable
private fun NextStepKind.accent(): Color = when (this) {
    NextStepKind.ALBUM -> AppColors.orange
    NextStepKind.CHASE -> AppColors.red
    NextStepKind.ILLUSTRATOR -> AppColors.purple
}

private fun NextStepKind.icon(): ImageVector = when (this) {
    NextStepKind.ALBUM -> Icons.Default.PhotoLibrary
    NextStepKind.CHASE -> Icons.Default.TrackChanges
    NextStepKind.ILLUSTRATOR -> Icons.Default.Brush
}

@Composable
private fun ContinueRow(step: NextStep, onClick: () -> Unit) {
    val accent = step.kind.accent()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.surface)
            .pressScale(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        ProgressRing(percent = step.percent, size = 42.dp, stroke = 3.dp, accent = accent, labelSize = 10)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(step.kind.icon(), contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    step.name,
                    color = AppColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(AppLocale.collectorMissingToFinish(step.missing), color = AppColors.textMuted, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text("${step.owned}/${step.total}", color = AppColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Una delle tre porte: icona, nome, quanti, e una barra di avanzamento. */
@Composable
private fun LabDoor(
    icon: ImageVector,
    accent: Color,
    title: String,
    value: String,
    percent: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.18f), AppColors.surface)))
            .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(18.dp))
            .pressScale(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconBadge(icon = icon, accent = accent, size = 30.dp)
        Text(title, color = AppColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(modifier = Modifier.weight(1f))
        FillBar(percent = percent, modifier = Modifier.fillMaxWidth(), accent = accent)
    }
}

@Composable
private fun NewButton(label: String, accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .pressScale(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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

// ── Mattoni ──────────────────────────────────────────────────────────────────

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
