package com.emabuia.pokevault.ui.trade

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.emabuia.pokevault.data.remote.PokeVaultApiClient
import com.emabuia.pokevault.data.trade.CoarseLocation
import com.emabuia.pokevault.data.trade.dto.TradeMatch
import com.emabuia.pokevault.data.trade.dto.TradeMatchItem
import com.emabuia.pokevault.data.trade.TradeCardKey
import com.emabuia.pokevault.data.trade.TradeLists
import com.emabuia.pokevault.ui.components.CardImageSkeleton
import com.emabuia.pokevault.ui.components.CascadeIn
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.AppMotion
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.TradeRadarViewModel
import com.emabuia.pokevault.viewmodel.TradeRadarViewModel.Problem
import com.emabuia.pokevault.viewmodel.TradeRadarViewModel.Screen
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

/**
 * TradeRadar, fase 1. Esiste solo nel flavor staging (vedi AppNavigation).
 *
 * Tre stati: attivazione, pannello (Match / Le mie carte) ed errore. Proposte,
 * appuntamenti, feedback e classifica arrivano con la fase 2, e partiranno
 * dalla scheda del match ([MatchCard]).
 *
 * Le tre etichette del server (wanted / useful / possible) qui si chiamano
 * "La cerchi", "Ti manca" e "Altre carte", ciascuna con colore e icona propri
 * ([LevelStyle]): sono la cosa da capire per prima, per questo la legenda si
 * mostra in cima finche' non si preme "Ho capito" e poi resta dietro al "?".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeRadarScreen(onBack: () -> Unit, viewModel: TradeRadarViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDeactivate by remember { mutableStateOf(false) }

    val notice = viewModel.notice
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(problemText(notice))
            viewModel.consumeNotice()
        }
    }

    val ready = viewModel.screen as? Screen.Ready

    Scaffold(
        containerColor = AppColors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(AppLocale.tradeRadarTitle, fontWeight = FontWeight.Bold, color = AppColors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = AppColors.textPrimary)
                    }
                },
                actions = {
                    if (ready != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, null, tint = AppColors.textPrimary)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(AppLocale.tradeRadarRefreshZone) },
                                    onClick = { menuOpen = false; viewModel.refreshZone() }
                                )
                                DropdownMenuItem(
                                    text = { Text(AppLocale.tradeRadarDeactivate, color = AppColors.red) },
                                    onClick = { menuOpen = false; confirmDeactivate = true }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Nel pannello il lavoro in corso lo dice il radar che gira.
            if (viewModel.busy && ready == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            when (val screen = viewModel.screen) {
                Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RadarScope(blips = emptyList(), scanning = true, modifier = Modifier.size(140.dp))
                }
                Screen.Onboarding -> Onboarding(viewModel)
                is Screen.Ready -> Hub(viewModel, screen)
                is Screen.Error -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(problemText(screen.message), color = AppColors.textPrimary)
                    OutlinedButton(onClick = { viewModel.load() }) { Text(AppLocale.retry) }
                }
            }
        }
    }

    if (confirmDeactivate) {
        AlertDialog(
            onDismissRequest = { confirmDeactivate = false },
            title = { Text(AppLocale.tradeRadarDeactivate) },
            text = { Text(AppLocale.tradeRadarDeactivateText) },
            confirmButton = {
                TextButton(onClick = { confirmDeactivate = false; viewModel.deactivate() }) {
                    Text(AppLocale.confirm, color = AppColors.red)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeactivate = false }) { Text(AppLocale.cancel) } }
        )
    }
}

// ── Attivazione ─────────────────────────────────────────────────────────────

@Composable
private fun Onboarding(viewModel: TradeRadarViewModel) {
    val context = LocalContext.current
    var nickname by rememberSaveable { mutableStateOf("") }
    var adult by rememberSaveable { mutableStateOf(false) }
    var consent by rememberSaveable { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.activate(nickname, adult, consent)
    }
    val nicknameOk = nickname.trim().length in 3..20
    val canActivate = nicknameOk && adult && consent && !viewModel.busy

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                RadarScope(blips = DemoBlips, scanning = false, modifier = Modifier.size(132.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    AppLocale.tradeRadarOnboardingTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    AppLocale.tradeRadarOnboardingText,
                    fontSize = 14.sp,
                    color = AppColors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppColors.card).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(AppLocale.tradeRadarSafetyTitle, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary)
                Text(AppLocale.tradeRadarSafetyText, fontSize = 13.sp, color = AppColors.textSecondary)
            }
        }
        item {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(20) },
                label = { Text(AppLocale.tradeRadarNickname) },
                supportingText = { Text(AppLocale.tradeRadarNicknameHint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { CheckRow(adult, { adult = it }, AppLocale.tradeRadarAdult) }
        item { CheckRow(consent, { consent = it }, AppLocale.tradeRadarConsent) }
        item {
            Text(AppLocale.tradeRadarLocationNote, fontSize = 12.sp, color = AppColors.textMuted)
        }
        item {
            Button(
                onClick = {
                    if (CoarseLocation.hasPermission(context)) viewModel.activate(nickname, adult, consent)
                    else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
                enabled = canActivate,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(AppLocale.tradeRadarActivate, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text, fontSize = 14.sp, color = AppColors.textPrimary)
    }
}

// ── Pannello ────────────────────────────────────────────────────────────────

@Composable
private fun Hub(viewModel: TradeRadarViewModel, ready: Screen.Ready) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val paused = ready.profile.paused == true
    val motion = AppMotion.current

    // Tenuto qui e non dentro la tab: tornando sui Match la cascata non si rigioca.
    var cascadeStarted by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.matchesLoaded) {
        if (viewModel.matchesLoaded) cascadeStarted = true
    }

    Column(Modifier.fillMaxSize()) {
        ProfileHeader(
            nickname = ready.profile.nickname.orEmpty(),
            paused = paused,
            onPausedChange = { viewModel.setPaused(it) }
        )
        SegmentedTabs(
            selected = tab,
            labels = listOf(AppLocale.tradeRadarTabMatches, AppLocale.tradeRadarTabMyCards),
            badges = listOf(viewModel.matches.size, viewModel.enabledIds.size),
            onSelect = { tab = it }
        )
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (fadeIn(tween(motion.content)) + slideInHorizontally(tween(motion.content)) { direction * it / 8 }) togetherWith
                    fadeOut(tween(motion.state))
            },
            label = "tradeTab",
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> MatchesTab(viewModel, paused, cascadeStarted, onGoToMyCards = { tab = 1 })
                else -> MyCardsTab(viewModel)
            }
        }
    }
}

@Composable
private fun ProfileHeader(nickname: String, paused: Boolean, onPausedChange: (Boolean) -> Unit) {
    val statusColor by animateColorAsState(
        if (paused) AppColors.orange else AppColors.green,
        tween(AppMotion.current.state),
        label = "status"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.card)
            .padding(12.dp)
    ) {
        Avatar(nickname, size = 44.dp, pulse = !paused)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(nickname, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.textPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (paused) AppLocale.tradeRadarPausedLabel else AppLocale.tradeRadarActiveLabel,
                    fontSize = 12.sp,
                    color = AppColors.textSecondary
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Switch(checked = !paused, onCheckedChange = { onPausedChange(!it) })
            Text(AppLocale.tradeRadarAvailable, fontSize = 10.sp, color = AppColors.textMuted)
        }
    }
}

/** Due pillole con l'indicatore che scorre sotto quella scelta. */
@Composable
private fun SegmentedTabs(selected: Int, labels: List<String>, badges: List<Int>, onSelect: (Int) -> Unit) {
    val motion = AppMotion.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(innerColor())
            .padding(4.dp)
    ) {
        val segment = maxWidth / labels.size
        val indicatorOffset by animateDpAsState(
            segment * selected,
            tween(motion.chevron, easing = AppMotion.standardEasing),
            label = "tabIndicator"
        )
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(19.dp))
                .background(AppColors.card)
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                val textColor by animateColorAsState(
                    if (index == selected) AppColors.textPrimary else AppColors.textMuted,
                    tween(motion.state),
                    label = "tabText"
                )
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(19.dp))
                        .clickable { onSelect(index) }
                ) {
                    Text(label, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    val badge = badges.getOrNull(index) ?: 0
                    if (badge > 0) {
                        Spacer(Modifier.width(6.dp))
                        CountBadge(badge, if (index == selected) AppColors.blue else AppColors.textMuted)
                    }
                }
            }
        }
    }
}

// ── Match ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchesTab(
    viewModel: TradeRadarViewModel,
    paused: Boolean,
    cascadeStarted: Boolean,
    onGoToMyCards: () -> Unit
) {
    var level by rememberSaveable { mutableStateOf("all") }
    var showLegend by remember { mutableStateOf(false) }
    var selectedCard by remember { mutableStateOf<TradeMatchItem?>(null) }
    // L'indicatore del pull-to-refresh solo per un aggiornamento tirato a mano:
    // per gli altri basta il radar.
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.refreshing) { if (!viewModel.refreshing) pulled = false }

    val matches = viewModel.matches
    val shown = matches.filter { match -> level == "all" || match.theyGive.orEmpty().any { it.level == level } }
    val counts = Levels.associateWith { key -> matches.count { m -> m.theyGive.orEmpty().any { it.level == key } } }
    val scanning = viewModel.refreshing || viewModel.busy

    PullToRefreshBox(
        isRefreshing = pulled && viewModel.refreshing,
        onRefresh = { pulled = true; viewModel.refreshMatches() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "radar") {
                RadarHero(
                    matches = if (paused) emptyList() else matches,
                    scanning = scanning && !paused,
                    paused = paused,
                    onRefresh = { viewModel.refreshMatches() }
                )
            }
            if (!paused && !viewModel.levelsExplained) {
                item(key = "intro") {
                    LevelsIntro(onDismiss = { viewModel.dismissLevelsIntro() }, modifier = Modifier.animateItem())
                }
            }
            if (!paused && matches.isNotEmpty()) {
                item(key = "filters") {
                    FilterRow(
                        selected = level,
                        total = matches.size,
                        counts = counts,
                        onSelect = { level = it },
                        onHelp = { showLegend = true }
                    )
                }
            }
            when {
                paused -> item(key = "paused") {
                    EmptyState(text = AppLocale.tradeRadarPausedHint)
                }
                matches.isEmpty() && viewModel.matchesLoaded -> item(key = "empty") {
                    val noHaves = viewModel.enabledIds.isEmpty()
                    EmptyState(
                        text = if (noHaves) AppLocale.tradeRadarNoHavesHint else AppLocale.tradeRadarNoMatches,
                        action = if (noHaves) AppLocale.tradeRadarGoToMyCards to onGoToMyCards else null
                    )
                }
                shown.isEmpty() && matches.isNotEmpty() -> item(key = "filtered") {
                    EmptyState(text = AppLocale.tradeRadarNoneForFilter, radar = false)
                }
                else -> itemsIndexed(shown, key = { index, match -> match.nickname ?: "match-$index" }) { index, match ->
                    CascadeIn(index = index, visible = cascadeStarted, modifier = Modifier.animateItem()) {
                        MatchCard(match, level, onCardClick = { selectedCard = it })
                    }
                }
            }
        }
    }

    if (showLegend) {
        AlertDialog(
            onDismissRequest = { showLegend = false },
            title = { Text(AppLocale.tradeRadarLevelsTitle) },
            text = { LevelsLegend() },
            confirmButton = { TextButton(onClick = { showLegend = false }) { Text(AppLocale.tradeRadarLevelsGotIt) } }
        )
    }
    selectedCard?.let { card -> CardDetailDialog(card, onDismiss = { selectedCard = null }) }
}

/** Il radar in cima: quanti sono vicini, quanti reciproci, e il tasto per aggiornare. */
@Composable
private fun RadarHero(matches: List<TradeMatch>, scanning: Boolean, paused: Boolean, onRefresh: () -> Unit) {
    val mutualCount = matches.count { it.mutual == true }
    val motion = AppMotion.current
    val spinTransition = rememberInfiniteTransition(label = "refreshSpin")
    val turning by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(motion.pullSpin.coerceAtLeast(1), easing = LinearEasing), RepeatMode.Restart),
        label = "turning"
    )
    val spin = if (scanning && motion.enabled) turning else 0f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(AppColors.green.copy(alpha = 0.14f), AppColors.blue.copy(alpha = 0.10f), AppColors.card)
                )
            )
            .background(AppColors.card.copy(alpha = 0.55f))
            .padding(16.dp)
    ) {
        RadarScope(
            blips = matches.map { Blip.of(it) },
            scanning = scanning,
            dimmed = paused,
            modifier = Modifier.size(92.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            val title = when {
                paused -> AppLocale.tradeRadarPausedLabel
                scanning && matches.isEmpty() -> AppLocale.tradeRadarScanning
                matches.isEmpty() -> AppLocale.tradeRadarNobodyYet
                else -> AppLocale.tradeRadarNearby(matches.size)
            }
            AnimatedContent(targetState = title, label = "radarTitle") { text ->
                Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
            }
            Spacer(Modifier.height(4.dp))
            if (mutualCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SwapHoriz, null, tint = AppColors.green, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(AppLocale.tradeRadarMutualCount(mutualCount), fontSize = 13.sp, color = AppColors.green, fontWeight = FontWeight.SemiBold)
                }
            } else if (!paused) {
                Text(AppLocale.tradeRadarPullToRefresh, fontSize = 12.sp, color = AppColors.textMuted)
            }
        }
        if (!paused) {
            IconButton(onClick = onRefresh, enabled = !scanning) {
                Icon(
                    Icons.Default.Refresh,
                    AppLocale.tradeRadarRefresh,
                    tint = AppColors.textPrimary,
                    modifier = Modifier.rotate(spin)
                )
            }
        }
    }
}

@Composable
private fun LevelsIntro(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.card)
            .border(1.dp, AppColors.blue.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lightbulb, null, tint = AppColors.blue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(AppLocale.tradeRadarLevelsTitle, fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
        }
        LevelsLegend()
        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
            modifier = Modifier.align(Alignment.End)
        ) { Text(AppLocale.tradeRadarLevelsGotIt, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun LevelsLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(AppLocale.tradeRadarLevelsIntro, fontSize = 13.sp, color = AppColors.textSecondary)
        Levels.forEach { key ->
            val style = levelStyle(key)
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(style.color.copy(alpha = 0.16f))
                ) {
                    Icon(style.icon, null, tint = style.color, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(style.label, fontWeight = FontWeight.Bold, color = style.color, fontSize = 14.sp)
                    Text(style.description, fontSize = 13.sp, color = AppColors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    selected: String,
    total: Int,
    counts: Map<String, Int>,
    onSelect: (String) -> Unit,
    onHelp: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            item { FilterChip(AppLocale.tradeRadarLevelAll, total, null, selected == "all") { onSelect("all") } }
            items(Levels) { key ->
                val style = levelStyle(key)
                FilterChip(style.label, counts[key] ?: 0, style, selected == key) { onSelect(key) }
            }
        }
        IconButton(onClick = onHelp) {
            Icon(Icons.AutoMirrored.Outlined.HelpOutline, AppLocale.tradeRadarLevelsHelp, tint = AppColors.textSecondary)
        }
    }
}

@Composable
private fun FilterChip(text: String, count: Int, style: LevelStyle?, selected: Boolean, onClick: () -> Unit) {
    val motion = AppMotion.current
    val accent = style?.color ?: AppColors.textPrimary
    val background by animateColorAsState(
        if (selected) accent else AppColors.card,
        tween(motion.state),
        label = "chipBg"
    )
    val content by animateColorAsState(
        if (selected) (if (style == null) AppColors.background else AppColors.onAccent) else AppColors.textPrimary,
        tween(motion.state),
        label = "chipText"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .pressScale(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (style != null) {
            Icon(style.icon, null, tint = if (selected) content else style.color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = content)
        Spacer(Modifier.width(6.dp))
        Text(count.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = content.copy(alpha = 0.7f))
    }
}

@Composable
private fun MatchCard(match: TradeMatch, level: String, onCardClick: (TradeMatchItem) -> Unit) {
    val theyGive = match.theyGive.orEmpty().filter { level == "all" || it.level == level }
    val iGive = match.iGive.orEmpty()
    val mutual = match.mutual == true
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.card)
            .then(if (mutual) Modifier.border(1.5.dp, AppColors.green.copy(alpha = 0.45f), shape) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(match.nickname.orEmpty(), size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(match.nickname.orEmpty(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.textPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, null, tint = AppColors.textMuted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    val trades = match.tradesDone ?: 0
                    Text(
                        "${distanceLabel(match.distance)} · ${if (trades == 0) AppLocale.tradeRadarNewMember else AppLocale.tradeRadarTradesDone(trades)}",
                        fontSize = 12.sp,
                        color = AppColors.textSecondary
                    )
                }
            }
        }

        if (mutual) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.green.copy(alpha = 0.13f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.SwapHoriz, null, tint = AppColors.green, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(AppLocale.tradeRadarMutual, fontSize = 13.sp, color = AppColors.green, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${match.theyGive.orEmpty().size} ⇄ ${iGive.size}", fontSize = 13.sp, color = AppColors.green, fontWeight = FontWeight.Bold)
            }
        }

        SectionTitle(AppLocale.tradeRadarTheyGive, theyGive.size)
        CardStrip(theyGive, onCardClick)

        if (iGive.isNotEmpty()) {
            SwapDivider()
            SectionTitle(AppLocale.tradeRadarYouGive, iGive.size)
            CardStrip(iGive, onCardClick)
        } else {
            Text(AppLocale.tradeRadarOneWay, fontSize = 12.sp, color = AppColors.textMuted)
        }
    }
}

@Composable
private fun SectionTitle(text: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textSecondary)
        Spacer(Modifier.width(6.dp))
        CountBadge(count, AppColors.textMuted)
    }
}

@Composable
private fun SwapDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = AppColors.textMuted.copy(alpha = 0.2f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp).size(28.dp).clip(CircleShape).background(innerColor())
        ) {
            Icon(Icons.Default.SwapVert, null, tint = AppColors.textSecondary, modifier = Modifier.size(16.dp))
        }
        HorizontalDivider(Modifier.weight(1f), color = AppColors.textMuted.copy(alpha = 0.2f))
    }
}

@Composable
private fun CardStrip(items: List<TradeMatchItem>, onCardClick: (TradeMatchItem) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { "${it.key}|${it.variant}|${it.condition}|${it.language}" }) { item ->
            TradeCardTile(item, onClick = { onCardClick(item) })
        }
    }
}

@Composable
private fun TradeCardTile(item: TradeMatchItem, onClick: () -> Unit) {
    val key = item.key.orEmpty()
    val style = levelStyle(item.level)
    Column(Modifier.width(98.dp).pressScale(onClick = onClick)) {
        Box(Modifier.width(98.dp).height(137.dp).clip(RoundedCornerShape(8.dp))) {
            CardImageSkeleton(number = key.substringAfter(':'))
            AsyncImage(
                model = TradeCardKey.imageUrl(key, PokeVaultApiClient.imageBaseUrl),
                contentDescription = item.name ?: TradeCardKey.label(key),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            if ((item.qty ?: 1) > 1) {
                Text(
                    "×${item.qty}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            LevelRibbon(style, Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.height(5.dp))
        Text(
            item.name ?: TradeCardKey.label(key),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val reason = reasonLabel(item)
        Text(
            reason ?: item.setName ?: TradeCardKey.label(key),
            fontSize = 10.sp,
            color = if (reason != null) style.color else AppColors.textMuted,
            fontWeight = if (reason != null) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LevelRibbon(style: LevelStyle, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(style.color.copy(alpha = 0.92f))
            .padding(vertical = 3.dp)
    ) {
        Icon(style.icon, null, tint = AppColors.onAccent, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(3.dp))
        Text(style.label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AppColors.onAccent, maxLines = 1)
    }
}

/** La carta grande, con l'etichetta spiegata per esteso: il tocco su una carta risponde a "perche' me la mostri?". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardDetailDialog(item: TradeMatchItem, onDismiss: () -> Unit) {
    val key = item.key.orEmpty()
    val style = levelStyle(item.level)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(AppColors.card)
                .padding(20.dp)
        ) {
            Box(Modifier.fillMaxWidth(0.72f).aspectRatio(0.716f).clip(RoundedCornerShape(12.dp))) {
                CardImageSkeleton(number = key.substringAfter(':'))
                AsyncImage(
                    model = TradeCardKey.imageUrl(key, PokeVaultApiClient.imageBaseUrl).replace("size=low", "size=high"),
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    item.name ?: TradeCardKey.label(key),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    "${item.setName ?: TradeCardKey.setCodeOf(key).uppercase()} · n. ${key.substringAfter(':')}",
                    fontSize = 13.sp,
                    color = AppColors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(style.color.copy(alpha = 0.12f))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(style.icon, null, tint = style.color, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(reasonLabel(item) ?: style.label, fontWeight = FontWeight.Bold, color = style.color, fontSize = 14.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(style.description, fontSize = 12.sp, color = AppColors.textSecondary, textAlign = TextAlign.Center)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOfNotNull(item.variant, item.condition, item.language, item.qty?.let { AppLocale.tradeRadarCopies(it) })
                    .filter { it.isNotBlank() }
                    .forEach { InfoPill(it) }
            }
            TextButton(onClick = onDismiss) { Text(AppLocale.tradeRadarClose) }
        }
    }
}

// ── Le mie carte ────────────────────────────────────────────────────────────

@Composable
private fun MyCardsTab(viewModel: TradeRadarViewModel) {
    val duplicates = viewModel.duplicates
    val offers = viewModel.offers
    val manual = viewModel.manualOffers
    val duplicatesOn = duplicates.count { it.id in offers }
    val allOn = duplicates.isNotEmpty() && duplicatesOn == duplicates.size
    var picking by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        if (duplicates.isEmpty()) 0f else duplicatesOn.toFloat() / duplicates.size,
        tween(AppMotion.current.bar, easing = AppMotion.standardEasing),
        label = "offered"
    )

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "summary") {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppColors.card).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(AppLocale.tradeRadarOfferTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.textPrimary)
                AnimatedContent(targetState = offers.size to offers.values.sum(), label = "offerSummary") { (cards, copies) ->
                    Text(AppLocale.tradeRadarOfferSummary(cards, copies), fontSize = 13.sp, color = AppColors.textSecondary)
                }
                Text(AppLocale.tradeRadarWantsSummary(viewModel.wantsCount), fontSize = 12.sp, color = AppColors.textMuted)
            }
        }

        // Doppioni: li trova l'app, l'utente sceglie quali e quante copie.
        item(key = "dupHeader") {
            Column(Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(AppLocale.tradeRadarDuplicatesTitle(duplicates.size), fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
                        if (duplicates.isNotEmpty()) {
                            Text(AppLocale.tradeRadarOffered(duplicatesOn, duplicates.size), fontSize = 12.sp, color = AppColors.textSecondary)
                        }
                    }
                    if (duplicates.isNotEmpty()) {
                        TextButton(onClick = { viewModel.setAllEnabled(!allOn) }) {
                            Text(if (allOn) AppLocale.tradeRadarNone else AppLocale.tradeRadarAll, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (duplicates.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        color = AppColors.green,
                        trackColor = AppColors.card,
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }
        if (duplicates.isEmpty()) {
            item(key = "dupEmpty") { EmptyState(text = AppLocale.tradeRadarNoDuplicates, radar = false) }
        } else {
            items(duplicates, key = { "dup|${it.id}" }) { duplicate ->
                OfferRow(
                    offer = duplicate,
                    quantity = offers[duplicate.id],
                    onToggle = { viewModel.setEnabled(duplicate.id, it) },
                    onQuantity = { viewModel.setQuantity(duplicate.id, it) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        // Carte singole: entrano solo se l'utente le aggiunge.
        item(key = "manualHeader") {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(AppLocale.tradeRadarManualTitle, fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
                Text(AppLocale.tradeRadarManualHint, fontSize = 12.sp, color = AppColors.textSecondary)
            }
        }
        items(manual, key = { "manual|${it.id}" }) { single ->
            OfferRow(
                offer = single,
                quantity = offers[single.id],
                onToggle = { viewModel.setEnabled(single.id, it) },
                onQuantity = {},
                manual = true,
                notify = single.id in viewModel.notifyIds,
                onNotify = { viewModel.setNotify(single.id, it) },
                modifier = Modifier.animateItem()
            )
        }
        item(key = "manualAdd") {
            OutlinedButton(
                onClick = { picking = true },
                enabled = viewModel.singles.size > manual.size,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).animateItem()
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(AppLocale.tradeRadarAddCard, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (picking) {
        SinglesPicker(
            singles = viewModel.singles.filter { it.id !in offers },
            onPick = { viewModel.setEnabled(it.id, true) },
            onDismiss = { picking = false }
        )
    }
}

/**
 * Una carta offribile. Con l'interruttore acceso, se ci sono piu' copie da
 * dare, compare il selettore: di default se ne offre una, le altre restano.
 * Le carte aggiunte a mano hanno la X al posto dell'interruttore e la
 * campanella, che le fa entrare negli avvisi come i doppioni.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OfferRow(
    offer: TradeLists.Duplicate,
    quantity: Int?,
    onToggle: (Boolean) -> Unit,
    onQuantity: (Int) -> Unit,
    modifier: Modifier = Modifier,
    manual: Boolean = false,
    notify: Boolean = false,
    onNotify: (Boolean) -> Unit = {}
) {
    val motion = AppMotion.current
    val enabled = quantity != null
    val border by animateColorAsState(
        if (enabled) AppColors.green.copy(alpha = 0.5f) else Color.Transparent,
        tween(motion.state),
        label = "offerBorder"
    )
    val imageAlpha by animateFloatAsState(if (enabled) 1f else 0.55f, tween(motion.state), label = "offerAlpha")
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.card)
            .border(1.dp, border, shape)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (manual) Modifier else Modifier.clickable { onToggle(!enabled) })
                .padding(10.dp)
        ) {
            AsyncImage(
                model = offer.imageUrl,
                contentDescription = offer.name,
                contentScale = ContentScale.Fit,
                alpha = imageAlpha,
                modifier = Modifier.width(48.dp).height(67.dp).clip(RoundedCornerShape(5.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(offer.name, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${offer.setName} · ${offer.cardNumber}",
                    fontSize = 12.sp, color = AppColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoPill(offer.variant)
                    InfoPill(offer.condition)
                    if (manual) {
                        InfoPill(AppLocale.tradeRadarOnlyCopy, AppColors.orange)
                        InfoPill(
                            if (notify) AppLocale.tradeRadarNotifyOn else AppLocale.tradeRadarNotifyOff,
                            if (notify) AppColors.blue else null
                        )
                    } else {
                        InfoPill(AppLocale.tradeRadarSpare(offer.spare), AppColors.green)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            if (manual) {
                BellButton(on = notify, onChange = onNotify)
                IconButton(onClick = { onToggle(false) }) {
                    Icon(Icons.Default.Close, AppLocale.tradeRadarRemove, tint = AppColors.textSecondary)
                }
            } else {
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
        }
        AnimatedVisibility(
            visible = enabled && offer.spare > 1,
            enter = expandVertically(tween(motion.content)) + fadeIn(tween(motion.content)),
            exit = shrinkVertically(tween(motion.state)) + fadeOut(tween(motion.state))
        ) {
            QuantityStepper(
                quantity = quantity ?: 1,
                max = offer.spare,
                onChange = onQuantity
            )
        }
    }
}

/** La campanella: accesa e' piena e blu, e all'accensione fa un piccolo scatto. */
@Composable
private fun BellButton(on: Boolean, onChange: (Boolean) -> Unit) {
    val motion = AppMotion.current
    val tint by animateColorAsState(if (on) AppColors.blue else AppColors.textMuted, tween(motion.state), label = "bellTint")
    val background by animateColorAsState(
        if (on) AppColors.blue.copy(alpha = 0.14f) else Color.Transparent,
        tween(motion.state),
        label = "bellBg"
    )
    val ring = remember { Animatable(0f) }
    LaunchedEffect(on) {
        if (on && motion.enabled) {
            ring.snapTo(0f)
            ring.animateTo(1f, tween(motion.celebration, easing = LinearEasing))
        }
    }
    // Oscilla due volte e si ferma: sin su due giri, smorzato verso la fine.
    val swing = if (ring.value in 0f..0.999f && ring.value > 0f) {
        (sin(ring.value * 4 * Math.PI) * 18 * (1 - ring.value)).toFloat()
    } else 0f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background)
            .pressScale(scaleDown = 0.88f) { onChange(!on) }
    ) {
        Icon(
            if (on) Icons.Default.NotificationsActive else Icons.Outlined.NotificationsOff,
            AppLocale.tradeRadarNotifyToggle,
            tint = tint,
            modifier = Modifier.size(20.dp).rotate(swing)
        )
    }
}

@Composable
private fun QuantityStepper(quantity: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(innerColor())
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(AppLocale.tradeRadarCopiesToOffer, fontSize = 13.sp, color = AppColors.textSecondary, modifier = Modifier.weight(1f))
        StepButton(Icons.Default.Remove, enabled = quantity > 1) { onChange(quantity - 1) }
        val duration = AppMotion.current.state
        AnimatedContent(
            targetState = quantity,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically(tween(duration)) { if (up) it else -it } + fadeIn(tween(duration))) togetherWith
                    (slideOutVertically(tween(duration)) { if (up) -it else it } + fadeOut(tween(duration)))
            },
            label = "quantity",
            modifier = Modifier.width(36.dp)
        ) { value ->
            Text(
                value.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        StepButton(Icons.Default.Add, enabled = quantity < max) { onChange(quantity + 1) }
        Spacer(Modifier.width(8.dp))
        Text(AppLocale.tradeRadarOutOf(max), fontSize = 12.sp, color = AppColors.textMuted)
    }
}

@Composable
private fun StepButton(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(
        if (enabled) AppColors.textPrimary else AppColors.textMuted.copy(alpha = 0.4f),
        tween(AppMotion.current.state),
        label = "stepTint"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(AppColors.card)
            .pressScale(enabled = enabled, scaleDown = 0.88f, onClick = onClick)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * Il pannello da cui si aggiungono a mano le carte singole. Resta aperto
 * dopo ogni scelta: la carta aggiunta sparisce dalla lista e si puo'
 * continuare.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SinglesPicker(singles: List<TradeLists.Duplicate>, onPick: (TradeLists.Duplicate) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(singles, query) {
        val needle = query.trim()
        if (needle.isEmpty()) singles
        else singles.filter { it.name.contains(needle, ignoreCase = true) || it.setName.contains(needle, ignoreCase = true) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.background,
        // Altezza in frazione: senza questi insets il pannello rimbalza dopo un fling (vedi DeckLabScreen).
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) }
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp)) {
            Text(AppLocale.tradeRadarPickerTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
            Text(AppLocale.tradeRadarManualHint, fontSize = 12.sp, color = AppColors.textSecondary)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(AppLocale.tradeRadarPickerSearch) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            if (shown.isEmpty()) {
                EmptyState(text = if (singles.isEmpty()) AppLocale.tradeRadarPickerNoSingles else AppLocale.tradeRadarPickerEmpty, radar = false)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(shown, key = { it.id }) { single ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.card)
                            .clickable { onPick(single) }
                            .padding(10.dp)
                    ) {
                        AsyncImage(
                            model = single.imageUrl,
                            contentDescription = single.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.width(40.dp).height(56.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(single.name, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${single.setName} · ${single.cardNumber} · ${single.variant}",
                                fontSize = 12.sp, color = AppColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(AppColors.green.copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Default.Add, AppLocale.tradeRadarAddCard, tint = AppColors.green, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Pezzi comuni ────────────────────────────────────────────────────────────

/** Il colore degli elementi dentro una card: nel tema chiaro card e surface sono lo stesso bianco. */
@Composable
private fun innerColor(): Color = if (AppColors.isLight) AppColors.searchBar else AppColors.surface

@Composable
private fun InfoPill(text: String, accent: Color? = null) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = accent ?: AppColors.textSecondary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent?.copy(alpha = 0.12f) ?: innerColor())
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun CountBadge(count: Int, color: Color) {
    Text(
        count.toString(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

/** Iniziale del nickname su un gradiente scelto dal nickname stesso: ognuno ha sempre il suo. */
@Composable
private fun Avatar(nickname: String, size: Dp, pulse: Boolean = false) {
    val palette = listOf(
        AppColors.blue to AppColors.purple,
        AppColors.green to AppColors.blue,
        AppColors.orange to AppColors.red,
        AppColors.purple to AppColors.orange,
        AppColors.lavender to AppColors.blue
    )
    val (from, to) = palette[nickname.hashCode().absoluteValue % palette.size]
    val motion = AppMotion.current
    val transition = rememberInfiniteTransition(label = "avatarPulse")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(motion.scanRing.coerceAtLeast(1), easing = LinearEasing), RepeatMode.Restart),
        label = "wave"
    )
    val pulseColor = AppColors.green
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        if (pulse && motion.enabled) {
            Canvas(Modifier.size(size)) {
                val radius = this.size.minDimension / 2
                drawCircle(
                    color = pulseColor.copy(alpha = 0.45f * (1f - wave)),
                    radius = radius * (0.85f + 0.3f * wave),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size * 0.86f)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(from, to)))
        ) {
            Text(
                nickname.trim().take(1).uppercase().ifEmpty { "?" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp
            )
        }
    }
}

/** Un collezionista sul radar: angolo e distanza dal centro, fissi per nickname. */
private data class Blip(val angle: Float, val distance: Float, val mutual: Boolean) {
    companion object {
        fun of(match: TradeMatch): Blip {
            val hash = (match.nickname ?: "").hashCode().absoluteValue
            val near = match.distance == "lt5"
            val spread = (hash / 360 % 100) / 100f
            return Blip(
                angle = (hash % 360).toFloat(),
                distance = if (near) 0.32f + 0.22f * spread else 0.62f + 0.24f * spread,
                mutual = match.mutual == true
            )
        }
    }
}

/** I punti finti del radar nella schermata di attivazione. */
private val DemoBlips = listOf(Blip(40f, 0.45f, true), Blip(160f, 0.75f, false), Blip(250f, 0.55f, false))

/**
 * Il radar: tre anelli, un fascio che gira e un punto per ogni collezionista.
 * Ogni punto si accende quando il fascio ci passa sopra e poi sfuma. Con le
 * animazioni di sistema spente resta fermo, con i punti tutti accesi.
 */
@Composable
private fun RadarScope(blips: List<Blip>, scanning: Boolean, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val motion = AppMotion.current
    val transition = rememberInfiniteTransition(label = "radar")
    val sweepDuration = (if (scanning) motion.scanSweep else motion.scanSweep * 3).coerceAtLeast(1)
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(sweepDuration, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )
    val animate = motion.enabled && !dimmed
    val angle = if (animate) sweep else 0f
    val accent = if (dimmed) AppColors.textMuted else AppColors.green
    val ring = AppColors.textMuted.copy(alpha = 0.28f)
    val mutualColor = AppColors.green
    val otherColor = AppColors.blue
    val center = AppColors.textPrimary

    Canvas(modifier) {
        val radius = size.minDimension / 2
        val c = this.center
        drawCircle(accent.copy(alpha = 0.07f), radius, c)
        for (i in 1..3) drawCircle(ring, radius * i / 3f, c, style = Stroke(1.dp.toPx()))
        drawLine(ring, Offset(c.x - radius, c.y), Offset(c.x + radius, c.y), 1.dp.toPx())
        drawLine(ring, Offset(c.x, c.y - radius), Offset(c.x, c.y + radius), 1.dp.toPx())

        if (animate) {
            rotate(angle, c) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1f to accent.copy(alpha = 0.45f),
                        center = c
                    ),
                    radius = radius,
                    center = c
                )
                drawLine(accent.copy(alpha = 0.9f), c, Offset(c.x + radius, c.y), 2.dp.toPx())
            }
        }

        blips.forEach { blip ->
            val radians = Math.toRadians(blip.angle.toDouble())
            val position = Offset(
                c.x + (cos(radians) * radius * blip.distance).toFloat(),
                c.y + (sin(radians) * radius * blip.distance).toFloat()
            )
            // Quanto e' passato da quando il fascio l'ha toccato: 0 appena toccato, 1 un giro fa.
            val behind = if (animate) ((angle - blip.angle + 360f) % 360f) / 360f else 0f
            val glow = if (dimmed) 0.35f else 1f - 0.7f * behind
            val color = if (blip.mutual) mutualColor else otherColor
            drawCircle(color.copy(alpha = 0.25f * glow), 7.dp.toPx(), position)
            drawCircle(color.copy(alpha = glow), 3.5.dp.toPx(), position)
        }
        drawCircle(center, 3.dp.toPx(), c)
    }
}

@Composable
private fun EmptyState(text: String, action: Pair<String, () -> Unit>? = null, radar: Boolean = true) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 12.dp)
    ) {
        if (radar) RadarScope(blips = emptyList(), scanning = false, dimmed = true, modifier = Modifier.size(96.dp))
        Text(text, fontSize = 14.sp, color = AppColors.textSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Button(onClick = action.second, shape = RoundedCornerShape(14.dp)) {
                Text(action.first, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Etichette ───────────────────────────────────────────────────────────────

/** L'ordine in cui si presentano: dalla piu' interessante alla meno. */
private val Levels = listOf("wanted", "useful", "possible")

private data class LevelStyle(val label: String, val description: String, val color: Color, val icon: ImageVector)

@Composable
private fun levelStyle(level: String?): LevelStyle = when (level) {
    "wanted" -> LevelStyle(AppLocale.tradeRadarLevelWanted, AppLocale.tradeRadarLevelWantedText, AppColors.orange, Icons.Default.Favorite)
    "useful" -> LevelStyle(AppLocale.tradeRadarLevelUseful, AppLocale.tradeRadarLevelUsefulText, AppColors.blue, Icons.Default.AddCircle)
    else -> LevelStyle(AppLocale.tradeRadarLevelPossible, AppLocale.tradeRadarLevelPossibleText, AppColors.textMuted, Icons.Default.Explore)
}

/** Il motivo preciso, quando c'e'; null per "Ti manca" e "Altre carte", che non ne hanno uno. */
private fun reasonLabel(item: TradeMatchItem): String? = when (item.reason) {
    "wishlist" -> AppLocale.tradeRadarReasonWishlist
    "album" -> AppLocale.tradeRadarReasonAlbum
    "set" -> {
        val owned = item.setOwned
        val size = item.setSize
        if (owned != null && size != null && size > 0) AppLocale.tradeRadarReasonSetProgress(owned, size)
        else AppLocale.tradeRadarReasonSet
    }
    else -> null
}

private fun distanceLabel(distance: String?): String =
    if (distance == "lt5") AppLocale.tradeRadarDistanceNear else AppLocale.tradeRadarDistanceArea

private fun problemText(problem: Problem): String = when (problem) {
    Problem.UNAUTHORIZED -> AppLocale.tradeRadarUnauthorized
    Problem.REJECTED -> AppLocale.tradeRadarRejected
    Problem.NO_LOCATION -> AppLocale.tradeRadarNoLocation
    Problem.UNAVAILABLE -> AppLocale.tradeRadarUnavailable(null)
}
