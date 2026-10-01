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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Handshake
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
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.emabuia.pokevault.data.remote.PokeVaultApiClient
import com.emabuia.pokevault.data.trade.CoarseLocation
import com.emabuia.pokevault.data.trade.dto.TradeCardHolder
import com.emabuia.pokevault.data.trade.dto.TradeCardOffer
import com.emabuia.pokevault.data.trade.dto.TradeMatch
import com.emabuia.pokevault.data.trade.dto.TradeMatchItem
import com.emabuia.pokevault.data.trade.dto.TradeOfferItem
import com.emabuia.pokevault.data.trade.dto.TradeProposal
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
import java.util.Locale
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
    val info = viewModel.info
    LaunchedEffect(info) {
        if (info != null) {
            snackbar.showSnackbar(infoText(info))
            viewModel.consumeInfo()
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
    // Dopo aver mandato una proposta si va a vederla.
    LaunchedEffect(viewModel.focusProposals) {
        if (viewModel.focusProposals > 0) tab = 1
    }

    Column(Modifier.fillMaxSize()) {
        ProfileHeader(
            nickname = ready.profile.nickname.orEmpty(),
            paused = paused,
            onPausedChange = { viewModel.setPaused(it) }
        )
        SegmentedTabs(
            selected = tab,
            labels = listOf(AppLocale.tradeRadarTabMatches, AppLocale.tradeRadarTabProposals, AppLocale.tradeRadarTabMyCards),
            // Sulle Proposte il numero conta solo quelle in cui tocca a te.
            badges = listOf(viewModel.matches.size, viewModel.proposalsToAnswer, viewModel.enabledIds.size),
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
                0 -> MatchesTab(viewModel, paused, cascadeStarted, onGoToMyCards = { tab = 2 })
                1 -> ProposalsTab(viewModel, onGoToMatches = { tab = 0 })
                else -> MyCardsTab(viewModel)
            }
        }
    }

    viewModel.composer?.let { composer -> ComposerDialog(viewModel, composer) }
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
private fun SegmentedTabs(
    selected: Int,
    labels: List<String>,
    badges: List<Int>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
) {
    val motion = AppMotion.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
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

/** Come si guardano i match: dalle carte (quali posso avere e da chi) o dalle persone. */
private enum class MatchView { CARDS, PEOPLE }

/** Ordine della vista per persona. BEST e' quello del server (punteggio). */
private enum class PeopleSort { BEST, NEAR, MOST }

/** Cosa mostra il pannello dal basso: chi ha una carta, o la scheda di una persona. */
private sealed class SheetPage {
    data class Holders(val card: TradeCardOffer) : SheetPage()
    /** [from]: la carta da cui si e' arrivati, per tornarci con la freccia. */
    data class Person(val index: Int, val from: TradeCardOffer?) : SheetPage()
}

/**
 * La tab Match pensata per molte persone vicine.
 *
 * Con cento collezionisti una scheda a testa non si legge: si parte dalle
 * carte. In cima gli scambi consigliati (i primi per punteggio del server),
 * poi la vista "Per carta" -- ogni carta che puoi ricevere con chi ce l'ha --
 * o "Per persona", a righe compatte che si aprono al tocco. Ricerca e
 * filtri per etichetta valgono per entrambe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchesTab(
    viewModel: TradeRadarViewModel,
    paused: Boolean,
    cascadeStarted: Boolean,
    onGoToMyCards: () -> Unit
) {
    var level by rememberSaveable { mutableStateOf("all") }
    var view by rememberSaveable { mutableStateOf(MatchView.CARDS) }
    var sort by rememberSaveable { mutableStateOf(PeopleSort.BEST) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var sheet by remember { mutableStateOf<SheetPage?>(null) }
    var showLegend by remember { mutableStateOf(false) }
    /** La carta toccata, e se e' una di quelle che dai (le etichette allora parlano dell'altro). */
    var selectedCard by remember { mutableStateOf<Pair<TradeMatchItem, Boolean>?>(null) }
    // L'indicatore del pull-to-refresh solo per un aggiornamento tirato a mano:
    // per gli altri basta il radar.
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.refreshing) { if (!viewModel.refreshing) pulled = false }

    val matches = viewModel.matches
    val cards = viewModel.cards
    val needle = query.trim()
    fun hit(vararg texts: String?) = needle.isEmpty() || texts.any { it?.contains(needle, ignoreCase = true) == true }

    val shownCards = cards.filter { card ->
        (level == "all" || card.level == level) && hit(card.name, card.setName, card.key)
    }
    val shownPeople = matches.withIndex()
        .filter { (_, match) ->
            (level == "all" || match.theyGive.orEmpty().any { it.level == level }) &&
                (hit(match.nickname) || match.theyGive.orEmpty().any { hit(it.name, it.setName) })
        }
        .let { list ->
            when (sort) {
                PeopleSort.BEST -> list
                PeopleSort.NEAR -> list.sortedBy { if (it.value.distance == "lt5") 0 else 1 }
                PeopleSort.MOST -> list.sortedByDescending { it.value.theyGiveCount ?: it.value.theyGive.orEmpty().size }
            }
        }
    val counts = if (view == MatchView.CARDS) {
        Levels.associateWith { key -> cards.count { it.level == key } }
    } else {
        Levels.associateWith { key -> matches.count { m -> m.theyGive.orEmpty().any { it.level == key } } }
    }
    // Con poche persone i consigliati ripeterebbero la lista: compaiono da quattro in su.
    val recommended = if (matches.size >= 4) {
        matches.withIndex().filter { it.value.mutual == true || it.value.level == "wanted" }.take(4)
            .ifEmpty { matches.withIndex().take(3) }
    } else emptyList()
    val scanning = viewModel.refreshing || viewModel.busy

    PullToRefreshBox(
        isRefreshing = pulled && viewModel.refreshing,
        onRefresh = { pulled = true; viewModel.refreshMatches() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
            if (!paused && recommended.isNotEmpty() && needle.isEmpty()) {
                item(key = "recommended") {
                    RecommendedRow(recommended, onOpen = { sheet = SheetPage.Person(it, null) }, modifier = Modifier.animateItem())
                }
            }
            if (!paused && matches.isNotEmpty()) {
                item(key = "controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                        SegmentedTabs(
                            selected = view.ordinal,
                            labels = listOf(AppLocale.tradeRadarViewByCard, AppLocale.tradeRadarViewByPerson),
                            badges = listOf(cards.size, matches.size),
                            onSelect = { view = MatchView.entries[it] },
                            modifier = Modifier
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SearchField(query, onChange = { query = it }, modifier = Modifier.weight(1f))
                            AnimatedVisibility(visible = view == MatchView.PEOPLE) {
                                SortButton(sort, onChange = { sort = it })
                            }
                        }
                        FilterRow(
                            selected = level,
                            total = if (view == MatchView.CARDS) cards.size else matches.size,
                            counts = counts,
                            onSelect = { level = it },
                            onHelp = { showLegend = true }
                        )
                    }
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
                matches.isEmpty() -> Unit
                view == MatchView.CARDS && shownCards.isEmpty() -> item(key = "noCards") {
                    EmptyState(text = if (needle.isNotEmpty()) AppLocale.tradeRadarNoSearchResults else AppLocale.tradeRadarNoneForFilter, radar = false)
                }
                view == MatchView.PEOPLE && shownPeople.isEmpty() -> item(key = "noPeople") {
                    EmptyState(text = if (needle.isNotEmpty()) AppLocale.tradeRadarNoSearchResults else AppLocale.tradeRadarNoneForFilter, radar = false)
                }
                view == MatchView.CARDS -> itemsIndexed(shownCards, key = { _, card -> "card|${card.key}" }) { position, card ->
                    CascadeIn(index = position, visible = cascadeStarted, modifier = Modifier.animateItem()) {
                        CardOfferRow(card, matches, onClick = { sheet = SheetPage.Holders(card) })
                    }
                }
                else -> itemsIndexed(shownPeople, key = { _, entry -> "person|${entry.index}" }) { position, (index, match) ->
                    CascadeIn(index = position, visible = cascadeStarted, modifier = Modifier.animateItem()) {
                        CompactMatchRow(
                            match = match,
                            level = level,
                            expanded = index in expanded,
                            onToggle = { expanded = if (index in expanded) expanded - index else expanded + index },
                            onCardClick = { card, theirs -> selectedCard = card to theirs },
                            onPropose = { viewModel.openComposer(match) }
                        )
                    }
                }
            }
        }
    }

    sheet?.let { page ->
        TradeSheet(
            page = page,
            matches = matches,
            level = level,
            onNavigate = { sheet = it },
            onCardClick = { card, theirs -> selectedCard = card to theirs },
            onPropose = { match, presetKey -> sheet = null; viewModel.openComposer(match, presetKey) },
            onDismiss = { sheet = null }
        )
    }
    if (showLegend) {
        AlertDialog(
            onDismissRequest = { showLegend = false },
            title = { Text(AppLocale.tradeRadarLevelsTitle) },
            text = { LevelsLegend() },
            confirmButton = { TextButton(onClick = { showLegend = false }) { Text(AppLocale.tradeRadarLevelsGotIt) } }
        )
    }
    selectedCard?.let { (card, theirs) -> CardDetailDialog(card, theirs, onDismiss = { selectedCard = null }) }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(AppLocale.tradeRadarSearchHint, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = AppColors.textMuted) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, null, tint = AppColors.textMuted) }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    )
}

@Composable
private fun SortButton(sort: PeopleSort, onChange: (PeopleSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, AppLocale.tradeRadarSort, tint = AppColors.textSecondary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PeopleSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (option) {
                                PeopleSort.BEST -> AppLocale.tradeRadarSortBest
                                PeopleSort.NEAR -> AppLocale.tradeRadarSortNear
                                PeopleSort.MOST -> AppLocale.tradeRadarSortMost
                            },
                            fontWeight = if (option == sort) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = { open = false; onChange(option) }
                )
            }
        }
    }
}

/** I primi scambi per punteggio, in orizzontale: spesso basta guardare questi. */
@Composable
private fun RecommendedRow(recommended: List<IndexedValue<TradeMatch>>, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = AppColors.orange, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(AppLocale.tradeRadarRecommended, fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(recommended, key = { it.index }) { (index, match) ->
                RecommendedCard(match, onClick = { onOpen(index) })
            }
        }
    }
}

@Composable
private fun RecommendedCard(match: TradeMatch, onClick: () -> Unit) {
    val mutual = match.mutual == true
    val shape = RoundedCornerShape(20.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .width(232.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        (if (mutual) AppColors.green else AppColors.orange).copy(alpha = 0.16f),
                        AppColors.card
                    )
                )
            )
            .background(AppColors.card.copy(alpha = 0.5f))
            .then(if (mutual) Modifier.border(1.dp, AppColors.green.copy(alpha = 0.45f), shape) else Modifier)
            .pressScale(scaleDown = 0.97f, onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(match.nickname.orEmpty(), size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(match.nickname.orEmpty(), fontWeight = FontWeight.Bold, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(distanceLabel(match.distance), fontSize = 11.sp, color = AppColors.textSecondary)
            }
            if (mutual) Icon(Icons.Default.SwapHoriz, AppLocale.tradeRadarMutual, tint = AppColors.green, modifier = Modifier.size(20.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            match.theyGive.orEmpty().take(4).forEach { item -> MiniCard(item, width = 44.dp) }
        }
        Text(
            AppLocale.tradeRadarGiveTake(match.theyGiveCount ?: match.theyGive.orEmpty().size, match.iGiveCount ?: match.iGive.orEmpty().size),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (mutual) AppColors.green else AppColors.textSecondary
        )
    }
}

/** Una miniatura di carta con il filo colorato del livello sotto. */
@Composable
private fun MiniCard(item: TradeMatchItem, width: Dp) {
    val key = item.key.orEmpty()
    val style = levelStyle(item.level)
    Box(Modifier.width(width).aspectRatio(0.716f).clip(RoundedCornerShape(4.dp))) {
        CardImageSkeleton()
        AsyncImage(
            model = TradeCardKey.imageUrl(key, PokeVaultApiClient.imageBaseUrl),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(style.color))
    }
}

/** Una riga della vista per carta: la carta, perche' ti interessa, e chi ce l'ha. */
@Composable
private fun CardOfferRow(card: TradeCardOffer, matches: List<TradeMatch>, onClick: () -> Unit) {
    val key = card.key.orEmpty()
    val style = levelStyle(card.level)
    val holders = card.holders.orEmpty()
    val count = card.holderCount ?: holders.size
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .pressScale(scaleDown = 0.98f, onClick = onClick)
            .padding(10.dp)
    ) {
        Box(Modifier.width(48.dp).height(67.dp).clip(RoundedCornerShape(5.dp))) {
            CardImageSkeleton()
            AsyncImage(
                model = TradeCardKey.imageUrl(key, PokeVaultApiClient.imageBaseUrl),
                contentDescription = card.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(style.color))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(card.name ?: TradeCardKey.label(key), fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(card.setName ?: TradeCardKey.setCodeOf(key).uppercase(), fontSize = 12.sp, color = AppColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(style.icon, null, tint = style.color, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    reasonText(card.reason, card.setOwned, card.setSize) ?: style.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = style.color,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StackedAvatars(
                nicknames = holders.take(3).mapNotNull { matches.getOrNull(it.match ?: -1)?.nickname },
                extra = (count - 3).coerceAtLeast(0)
            )
            Text(AppLocale.tradeRadarHolders(count), fontSize = 11.sp, color = AppColors.textMuted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppColors.textMuted)
    }
}

/** Le iniziali una sopra l'altra, come le facce di un gruppo, con "+N" per il resto. */
@Composable
private fun StackedAvatars(nicknames: List<String>, extra: Int) {
    val ring = AppColors.card
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp), verticalAlignment = Alignment.CenterVertically) {
        nicknames.forEach { nickname ->
            Box(Modifier.size(28.dp).clip(CircleShape).background(ring), contentAlignment = Alignment.Center) {
                Avatar(nickname, size = 28.dp)
            }
        }
        if (extra > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(28.dp).clip(CircleShape).background(ring).padding(2.dp).clip(CircleShape).background(innerColor())
            ) {
                Text("+$extra", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AppColors.textSecondary)
            }
        }
    }
}

/** Una persona nella vista per persona: una riga che si apre sulla scheda intera. */
@Composable
private fun CompactMatchRow(
    match: TradeMatch,
    level: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCardClick: (TradeMatchItem, theirs: Boolean) -> Unit,
    onPropose: () -> Unit
) {
    val motion = AppMotion.current
    val mutual = match.mutual == true
    val shape = RoundedCornerShape(20.dp)
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, tween(motion.chevron), label = "rowChevron")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.card)
            .then(if (mutual) Modifier.border(1.dp, AppColors.green.copy(alpha = 0.4f), shape) else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(12.dp)
        ) {
            Avatar(match.nickname.orEmpty(), size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(match.nickname.orEmpty(), fontWeight = FontWeight.Bold, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (mutual) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.SwapHoriz, AppLocale.tradeRadarMutual, tint = AppColors.green, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    "${distanceLabel(match.distance)} · ${AppLocale.tradeRadarGiveTake(match.theyGiveCount ?: match.theyGive.orEmpty().size, match.iGiveCount ?: match.iGive.orEmpty().size)}",
                    fontSize = 12.sp,
                    color = AppColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AnimatedVisibility(visible = !expanded, enter = fadeIn(), exit = fadeOut()) {
                Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                    match.theyGive.orEmpty().take(3).forEach { MiniCard(it, width = 26.dp) }
                }
            }
            Icon(Icons.Default.ExpandMore, null, tint = AppColors.textMuted, modifier = Modifier.rotate(arrow))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(motion.content)) + fadeIn(tween(motion.content)),
            exit = shrinkVertically(tween(motion.state)) + fadeOut(tween(motion.state))
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MatchBody(match, level, onCardClick, onPropose)
            }
        }
    }
}

/**
 * Il pannello dal basso: chi ha una carta, e da li' la scheda di una persona,
 * con la freccia per tornare alla carta. Contenuto a misura (niente altezza
 * in frazione), quindi niente rimbalzo; gli insets restano solo in basso.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TradeSheet(
    page: SheetPage,
    matches: List<TradeMatch>,
    level: String,
    onNavigate: (SheetPage) -> Unit,
    onCardClick: (TradeMatchItem, theirs: Boolean) -> Unit,
    /** La persona e, se si arriva da una carta, quella carta gia' nella proposta. */
    onPropose: (TradeMatch, presetKey: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val motion = AppMotion.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.background,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) }
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState is SheetPage.Person
                (fadeIn(tween(motion.content)) + slideInHorizontally(tween(motion.content)) { if (forward) it / 6 else -it / 6 }) togetherWith
                    fadeOut(tween(motion.state))
            },
            label = "sheetPage"
        ) { current ->
            when (current) {
                is SheetPage.Holders -> HoldersPage(current.card, matches, onOpen = { onNavigate(SheetPage.Person(it, current.card)) })
                is SheetPage.Person -> PersonPage(
                    match = matches.getOrNull(current.index),
                    level = level,
                    onBack = current.from?.let { card -> { onNavigate(SheetPage.Holders(card)) } },
                    onCardClick = onCardClick,
                    onPropose = { match -> onPropose(match, current.from?.key) }
                )
            }
        }
    }
}

@Composable
private fun HoldersPage(card: TradeCardOffer, matches: List<TradeMatch>, onOpen: (Int) -> Unit) {
    val key = card.key.orEmpty()
    val style = levelStyle(card.level)
    val holders = card.holders.orEmpty()
    val count = card.holderCount ?: holders.size
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Box(Modifier.width(72.dp).height(100.dp).clip(RoundedCornerShape(8.dp))) {
                    CardImageSkeleton()
                    AsyncImage(
                        model = TradeCardKey.imageUrl(key, PokeVaultApiClient.imageBaseUrl),
                        contentDescription = card.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(card.name ?: TradeCardKey.label(key), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
                    Text(card.setName ?: TradeCardKey.setCodeOf(key).uppercase(), fontSize = 13.sp, color = AppColors.textSecondary)
                    InfoPill(reasonText(card.reason, card.setOwned, card.setSize) ?: style.label, style.color)
                }
            }
            Text(AppLocale.tradeRadarHoldersTitle(count), fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
        }
        items(holders, key = { it.match ?: -1 }) { holder ->
            val match = matches.getOrNull(holder.match ?: -1) ?: return@items
            HolderRow(match, holder, onClick = { holder.match?.let(onOpen) })
        }
        if (count > holders.size) {
            item { Text(AppLocale.tradeRadarMoreHolders(count - holders.size), fontSize = 12.sp, color = AppColors.textMuted) }
        }
    }
}

@Composable
private fun HolderRow(match: TradeMatch, holder: TradeCardHolder, onClick: () -> Unit) {
    val mutual = match.mutual == true
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.card)
            .pressScale(scaleDown = 0.98f, onClick = onClick)
            .padding(10.dp)
    ) {
        Avatar(match.nickname.orEmpty(), size = 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(match.nickname.orEmpty(), fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    distanceLabel(match.distance),
                    holder.qty?.takeIf { it > 1 }?.let { "×$it" },
                    holder.condition?.takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                fontSize = 12.sp,
                color = AppColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (mutual) {
            InfoPill(AppLocale.tradeRadarMutualShort, AppColors.green)
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppColors.textMuted)
    }
}

@Composable
private fun PersonPage(
    match: TradeMatch?,
    level: String,
    onBack: (() -> Unit)?,
    onCardClick: (TradeMatchItem, theirs: Boolean) -> Unit,
    onPropose: (TradeMatch) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(AppLocale.back)
            }
        }
        if (match != null) MatchCard(match, level, onCardClick, onPropose = { onPropose(match) })
    }
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
        Text(AppLocale.tradeRadarLevelsTheirSide, fontSize = 12.sp, color = AppColors.textMuted)
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

/** La scheda intera di una persona: intestazione e [MatchBody]. */
@Composable
private fun MatchCard(
    match: TradeMatch,
    level: String,
    onCardClick: (TradeMatchItem, theirs: Boolean) -> Unit,
    onPropose: (() -> Unit)? = null
) {
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
        MatchBody(match, level, onCardClick, onPropose)
    }
}

/** Fascia "Scambio reciproco" e le due file di carte: cosa ti da', cosa vuole da te. */
@Composable
private fun MatchBody(
    match: TradeMatch,
    level: String,
    onCardClick: (TradeMatchItem, theirs: Boolean) -> Unit,
    onPropose: (() -> Unit)? = null
) {
    val theyGive = match.theyGive.orEmpty().filter { level == "all" || it.level == level }
    val iGive = match.iGive.orEmpty()
    if (match.mutual == true) {
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
            Text(
                "${match.theyGiveCount ?: match.theyGive.orEmpty().size} ⇄ ${match.iGiveCount ?: iGive.size}",
                fontSize = 13.sp, color = AppColors.green, fontWeight = FontWeight.Bold
            )
        }
    }

    SectionTitle(AppLocale.tradeRadarTheyGive, theyGive.size)
    CardStrip(theyGive, theirs = false) { onCardClick(it, false) }

    if (iGive.isNotEmpty()) {
        SwapDivider()
        SectionTitle(AppLocale.tradeRadarYouGive, iGive.size)
        // Qui le etichette dicono quanto la carta interessa all'ALTRO.
        CardStrip(iGive, theirs = true) { onCardClick(it, true) }
    } else {
        Text(AppLocale.tradeRadarOneWay, fontSize = 12.sp, color = AppColors.textMuted)
    }
    if (onPropose != null && match.id != null) {
        Button(
            onClick = onPropose,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (match.mutual == true) AppColors.green else AppColors.blue),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(AppLocale.tradeRadarPropose, fontWeight = FontWeight.Bold)
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
private fun CardStrip(items: List<TradeMatchItem>, theirs: Boolean, onCardClick: (TradeMatchItem) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { "${it.key}|${it.variant}|${it.condition}|${it.language}" }) { item ->
            TradeCardTile(item, theirs, onClick = { onCardClick(item) })
        }
    }
}

/** [theirs]: la carta e' tua e va all'altro, quindi l'etichetta parla di lui. */
@Composable
private fun TradeCardTile(item: TradeMatchItem, theirs: Boolean, onClick: () -> Unit) {
    val key = item.key.orEmpty()
    val style = levelStyle(item.level, theirs)
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
        val reason = reasonLabel(item, theirs)
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
private fun CardDetailDialog(item: TradeMatchItem, theirs: Boolean, onDismiss: () -> Unit) {
    val key = item.key.orEmpty()
    val style = levelStyle(item.level, theirs)
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
                    Text(reasonLabel(item, theirs) ?: style.label, fontWeight = FontWeight.Bold, color = style.color, fontSize = 14.sp)
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

// ── Proposte ────────────────────────────────────────────────────────────────

/**
 * La tab Proposte: prima quelle in cui tocca a te, poi quelle che aspettano
 * l'altro, gli accordi fatti e le chiuse. Niente testo libero: si accetta, si
 * rifiuta o si fa una controproposta cambiando carte e quantita'.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProposalsTab(viewModel: TradeRadarViewModel, onGoToMatches: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refreshProposals() }
    val proposals = viewModel.proposals
    val sections = listOf(
        AppLocale.tradeRadarSectionToAnswer to proposals.filter { it.status == "open" && it.myTurn == true },
        AppLocale.tradeRadarSectionWaiting to proposals.filter { it.status == "open" && it.myTurn != true },
        AppLocale.tradeRadarSectionAccepted to proposals.filter { it.status == "accepted" },
        AppLocale.tradeRadarSectionClosed to proposals.filter { it.status == "declined" || it.status == "cancelled" },
    ).filter { it.second.isNotEmpty() }
    var confirmCancel by remember { mutableStateOf<TradeProposal?>(null) }

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = { viewModel.refreshProposals() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                !viewModel.proposalsLoaded -> item {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        RadarScope(blips = emptyList(), scanning = true, modifier = Modifier.size(96.dp))
                    }
                }
                proposals.isEmpty() -> item {
                    EmptyState(text = AppLocale.tradeRadarNoProposals, action = AppLocale.tradeRadarGoToMatches to onGoToMatches)
                }
                else -> sections.forEach { (title, list) ->
                    item(key = "title|$title") {
                        SectionTitle(title, list.size)
                    }
                    items(list, key = { it.id.orEmpty() }) { proposal ->
                        ProposalCard(
                            proposal = proposal,
                            prices = viewModel.prices,
                            acting = viewModel.actingOn == proposal.id,
                            onAccept = { viewModel.answer(proposal.id.orEmpty(), "accept") },
                            onDecline = { viewModel.answer(proposal.id.orEmpty(), "decline") },
                            onCounter = { viewModel.openCounter(proposal) },
                            onCancel = {
                                if (proposal.status == "accepted") confirmCancel = proposal
                                else viewModel.answer(proposal.id.orEmpty(), "cancel")
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    confirmCancel?.let { proposal ->
        AlertDialog(
            onDismissRequest = { confirmCancel = null },
            title = { Text(AppLocale.tradeRadarCancelDeal) },
            text = { Text(AppLocale.tradeRadarCancelDealText(proposal.counterpart?.nickname.orEmpty())) },
            confirmButton = {
                TextButton(onClick = { confirmCancel = null; viewModel.answer(proposal.id.orEmpty(), "cancel") }) {
                    Text(AppLocale.confirm, color = AppColors.red)
                }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = null }) { Text(AppLocale.cancel) } }
        )
    }
}

@Composable
private fun ProposalCard(
    proposal: TradeProposal,
    prices: Map<String, Double>,
    acting: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCounter: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val other = proposal.counterpart
    val status = proposalStatus(proposal)
    val shape = RoundedCornerShape(20.dp)
    val open = proposal.status == "open"
    val myTurn = open && proposal.myTurn == true
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.card)
            .then(if (myTurn || proposal.status == "accepted") Modifier.border(1.dp, status.color.copy(alpha = 0.5f), shape) else Modifier)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(other?.nickname.orEmpty(), size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(other?.nickname.orEmpty(), fontWeight = FontWeight.Bold, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        distanceLabel(other?.distance),
                        (proposal.revision ?: 1).takeIf { it > 1 && open }?.let { AppLocale.tradeRadarCounterNote(it) }
                    ).joinToString(" · "),
                    fontSize = 12.sp,
                    color = AppColors.textSecondary
                )
            }
            InfoPill(status.label, status.color)
        }
        ProposalSide(AppLocale.tradeRadarReceive, proposal.take.orEmpty())
        ProposalSide(AppLocale.tradeRadarGive, proposal.give.orEmpty())
        BalanceLine(proposal.give.orEmpty(), proposal.take.orEmpty(), prices, compact = true)

        when {
            myTurn -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDecline, enabled = !acting) {
                    Text(AppLocale.tradeRadarDecline, color = AppColors.red, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(onClick = onCounter, enabled = !acting, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    Text(AppLocale.tradeRadarCounter, maxLines = 1)
                }
                Button(
                    onClick = onAccept,
                    enabled = !acting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.green),
                    modifier = Modifier.weight(1f)
                ) {
                    if (acting) CircularProgressIndicator(Modifier.size(16.dp), color = AppColors.onAccent, strokeWidth = 2.dp)
                    else Text(AppLocale.tradeRadarAccept, fontWeight = FontWeight.Bold)
                }
            }
            open -> OutlinedButton(onClick = onCancel, enabled = !acting, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(AppLocale.tradeRadarWithdraw)
            }
            proposal.status == "accepted" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.green.copy(alpha = 0.12f)).padding(10.dp)
                ) {
                    Icon(Icons.Default.Handshake, null, tint = AppColors.green, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(AppLocale.tradeRadarAcceptedNext, fontSize = 12.sp, color = AppColors.textSecondary)
                }
                TextButton(onClick = onCancel, enabled = !acting, modifier = Modifier.align(Alignment.End)) {
                    Text(AppLocale.tradeRadarCancelDeal, color = AppColors.red)
                }
            }
        }
    }
}

/** Una riga di carte di una proposta: "Ricevi" o "Dai", con le copie. */
@Composable
private fun ProposalSide(title: String, items: List<TradeOfferItem>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textMuted, modifier = Modifier.width(52.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items) { item ->
                Box {
                    Box(Modifier.width(42.dp).aspectRatio(0.716f).clip(RoundedCornerShape(4.dp))) {
                        CardImageSkeleton()
                        AsyncImage(
                            model = TradeCardKey.imageUrl(item.key.orEmpty(), PokeVaultApiClient.imageBaseUrl),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if ((item.qty ?: 1) > 1) {
                        Text(
                            "×${item.qty}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class StatusStyle(val label: String, val color: Color)

@Composable
private fun proposalStatus(proposal: TradeProposal): StatusStyle = when (proposal.status) {
    "open" -> if (proposal.myTurn == true) StatusStyle(AppLocale.tradeRadarStatusYourTurn, AppColors.orange)
        else StatusStyle(AppLocale.tradeRadarStatusWaiting, AppColors.blue)
    "accepted" -> StatusStyle(AppLocale.tradeRadarStatusAccepted, AppColors.green)
    "declined" -> StatusStyle(
        if (proposal.closedByMe == true) AppLocale.tradeRadarStatusDeclinedByMe else AppLocale.tradeRadarStatusDeclined,
        AppColors.textMuted
    )
    else -> StatusStyle(
        if (proposal.closedByMe == true) AppLocale.tradeRadarStatusCancelledByMe else AppLocale.tradeRadarStatusCancelled,
        AppColors.textMuted
    )
}

/**
 * Il bilancio in euro, solo informativo: quanto dai, quanto ricevi, e se e'
 * equo. Non blocca e non corregge niente. Le carte senza prezzo restano fuori
 * dal conto e si dice quante sono.
 */
@Composable
private fun BalanceLine(give: List<TradeOfferItem>, take: List<TradeOfferItem>, prices: Map<String, Double>, compact: Boolean = false) {
    fun total(items: List<TradeOfferItem>) = items.sumOf { (prices[it.key.orEmpty()] ?: 0.0) * (it.qty ?: 1) }
    val unpriced = (give + take).count { prices[it.key.orEmpty()] == null }
    val giveValue = total(give)
    val takeValue = total(take)
    val diff = takeValue - giveValue
    val fair = kotlin.math.abs(diff) <= maxOf(1.0, 0.1 * maxOf(giveValue, takeValue))
    val verdict = when {
        giveValue == 0.0 && takeValue == 0.0 -> null
        fair -> AppLocale.tradeRadarBalanceFair to AppColors.green
        diff > 0 -> AppLocale.tradeRadarBalanceForYou(euro(diff)) to AppColors.blue
        else -> AppLocale.tradeRadarBalanceForThem(euro(-diff)) to AppColors.orange
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Balance, null, tint = AppColors.textMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "${AppLocale.tradeRadarBalanceGive(euro(giveValue))} · ${AppLocale.tradeRadarBalanceTake(euro(takeValue))}",
                fontSize = 12.sp,
                color = AppColors.textSecondary,
                modifier = Modifier.weight(1f)
            )
            verdict?.let { (text, color) -> InfoPill(text, color) }
        }
        if (!compact || unpriced > 0) {
            Text(
                listOfNotNull(
                    AppLocale.tradeRadarBalanceNote.takeIf { !compact },
                    unpriced.takeIf { it > 0 }?.let { AppLocale.tradeRadarNoPrices(it) }
                ).joinToString(" "),
                fontSize = 11.sp,
                color = AppColors.textMuted
            )
        }
    }
}

private fun euro(value: Double): String = "€%.2f".format(Locale.ITALY, value)

/**
 * La composizione, a schermo intero: cosa ricevi (fra le sue offerte) e cosa
 * dai (fra le tue), con le copie, e in fondo il bilancio e l'invio. Le carte
 * si aggiungono da elenchi che si aprono sotto ciascuna parte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerDialog(viewModel: TradeRadarViewModel, composer: TradeRadarViewModel.Composer) {
    val prices = viewModel.prices
    val myOffers = viewModel.myOffers
    var addingTake by remember { mutableStateOf(false) }
    var addingGive by remember { mutableStateOf(false) }
    val takeItems = composer.theirOffers.filter { viewModel.offerId(it) in composer.take }
        .map { it.copy(qty = composer.take[viewModel.offerId(it)]) }
    val giveItems = myOffers.filter { viewModel.offerId(it) in composer.give }
        .map { it.copy(qty = composer.give[viewModel.offerId(it)]) }
    val nickname = composer.nickname

    Dialog(
        onDismissRequest = { viewModel.closeComposer() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = AppColors.background,
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (composer.counterTo != null) AppLocale.tradeRadarCounterTitle else AppLocale.tradeRadarComposerTitle,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.textPrimary
                            )
                            Text(AppLocale.tradeRadarTo(nickname), fontSize = 13.sp, color = AppColors.textSecondary)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.closeComposer() }) {
                            Icon(Icons.Default.Close, AppLocale.tradeRadarClose, tint = AppColors.textPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
                )
            },
            bottomBar = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(AppColors.card)
                        .padding(16.dp)
                ) {
                    BalanceLine(giveItems, takeItems, prices)
                    val ready = giveItems.isNotEmpty() && takeItems.isNotEmpty()
                    if (!ready && !composer.loading) {
                        Text(AppLocale.tradeRadarPickBothSides, fontSize = 12.sp, color = AppColors.orange)
                    }
                    Button(
                        onClick = { viewModel.sendComposer() },
                        enabled = ready && !composer.sending,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        if (composer.sending) CircularProgressIndicator(Modifier.size(20.dp), color = AppColors.onAccent, strokeWidth = 2.dp)
                        else Text(
                            if (composer.counterTo != null) AppLocale.tradeRadarSendCounter else AppLocale.tradeRadarSend,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        ) { padding ->
            if (composer.loading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    RadarScope(blips = emptyList(), scanning = true, modifier = Modifier.size(110.dp))
                }
                return@Scaffold
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Cosa ricevi.
                item(key = "takeTitle") { ComposerSideTitle(Icons.AutoMirrored.Filled.CallReceived, AppLocale.tradeRadarReceiveFrom(nickname), AppColors.green) }
                items(takeItems, key = { "take|" + viewModel.offerId(it) }) { item ->
                    val id = viewModel.offerId(item)
                    ComposerItemRow(
                        item = item,
                        max = composer.theirOffers.firstOrNull { viewModel.offerId(it) == id }?.qty ?: 1,
                        price = prices[item.key.orEmpty()],
                        onQuantity = { viewModel.setTake(id, it) },
                        modifier = Modifier.animateItem()
                    )
                }
                val moreTheirs = composer.theirOffers.filter { viewModel.offerId(it) !in composer.take }
                item(key = "takeAdd") {
                    AddToggle(AppLocale.tradeRadarAddTheirs, open = addingTake, enabled = moreTheirs.isNotEmpty()) { addingTake = !addingTake }
                }
                if (addingTake) {
                    if (moreTheirs.isEmpty()) item(key = "takeNone") { Text(AppLocale.tradeRadarNothingMoreTheirs, fontSize = 12.sp, color = AppColors.textMuted) }
                    items(moreTheirs, key = { "takePick|" + viewModel.offerId(it) }) { item ->
                        PickRow(item, prices[item.key.orEmpty()], Modifier.animateItem()) { viewModel.setTake(viewModel.offerId(item), 1) }
                    }
                }

                item(key = "divider") { Box(Modifier.padding(vertical = 6.dp)) { SwapDivider() } }

                // Cosa dai.
                item(key = "giveTitle") { ComposerSideTitle(Icons.AutoMirrored.Filled.CallMade, AppLocale.tradeRadarGiveTo(nickname), AppColors.orange) }
                items(giveItems, key = { "give|" + viewModel.offerId(it) }) { item ->
                    val id = viewModel.offerId(item)
                    ComposerItemRow(
                        item = item,
                        max = myOffers.firstOrNull { viewModel.offerId(it) == id }?.qty ?: 1,
                        price = prices[item.key.orEmpty()],
                        onQuantity = { viewModel.setGive(id, it) },
                        modifier = Modifier.animateItem()
                    )
                }
                val moreMine = myOffers.filter { viewModel.offerId(it) !in composer.give }
                if (myOffers.isEmpty()) {
                    item(key = "giveNone") { Text(AppLocale.tradeRadarNoOffersYet, fontSize = 12.sp, color = AppColors.orange) }
                } else {
                    item(key = "giveAdd") {
                        AddToggle(AppLocale.tradeRadarAddMine, open = addingGive, enabled = moreMine.isNotEmpty()) { addingGive = !addingGive }
                    }
                    if (addingGive) {
                        items(moreMine, key = { "givePick|" + viewModel.offerId(it) }) { item ->
                            PickRow(item, prices[item.key.orEmpty()], Modifier.animateItem()) { viewModel.setGive(viewModel.offerId(item), 1) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerSideTitle(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.15f))
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
    }
}

/** Una carta scelta: immagine, dati della copia, prezzo e copie con − e +. A 1, il − la toglie. */
@Composable
private fun ComposerItemRow(item: TradeOfferItem, max: Int, price: Double?, onQuantity: (Int) -> Unit, modifier: Modifier = Modifier) {
    val quantity = item.qty ?: 1
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.card)
            .padding(10.dp)
    ) {
        Box(Modifier.width(40.dp).height(56.dp).clip(RoundedCornerShape(4.dp))) {
            CardImageSkeleton()
            AsyncImage(
                model = TradeCardKey.imageUrl(item.key.orEmpty(), PokeVaultApiClient.imageBaseUrl),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name ?: TradeCardKey.label(item.key.orEmpty()), fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.setName, item.condition?.takeIf { it.isNotBlank() }).joinToString(" · "),
                fontSize = 11.sp, color = AppColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(price?.let { "~" + euro(it) } ?: AppLocale.tradeRadarNoPrice, fontSize = 11.sp, color = AppColors.textMuted)
        }
        StepButton(if (quantity <= 1) Icons.Default.Close else Icons.Default.Remove, enabled = true) { onQuantity(quantity - 1) }
        Text(
            quantity.toString(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(30.dp)
        )
        StepButton(Icons.Default.Add, enabled = quantity < max) { onQuantity(quantity + 1) }
    }
}

@Composable
private fun AddToggle(text: String, open: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val arrow by animateFloatAsState(if (open) 45f else 0f, tween(AppMotion.current.chevron), label = "addToggle")
    TextButton(onClick = onClick, enabled = enabled || open) {
        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp).rotate(arrow))
        Spacer(Modifier.width(6.dp))
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** Una carta da aggiungere: al tocco entra nella proposta con una copia. */
@Composable
private fun PickRow(item: TradeOfferItem, price: Double?, modifier: Modifier = Modifier, onPick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(innerColor())
            .clickable(onClick = onPick)
            .padding(8.dp)
    ) {
        Box(Modifier.width(32.dp).height(45.dp).clip(RoundedCornerShape(3.dp))) {
            CardImageSkeleton()
            AsyncImage(
                model = TradeCardKey.imageUrl(item.key.orEmpty(), PokeVaultApiClient.imageBaseUrl),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name ?: TradeCardKey.label(item.key.orEmpty()), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.setName, price?.let { "~" + euro(it) }, (item.qty ?: 1).takeIf { it > 1 }?.let { "×$it" }).joinToString(" · "),
                fontSize = 11.sp, color = AppColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Default.AddCircle, null, tint = AppColors.green, modifier = Modifier.size(22.dp))
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

/**
 * Stesso livello, due voci: per le carte che ricevi parla di te ("Ti manca"),
 * per quelle che dai parla dell'altro ("Non ce l'ha"). Colore e icona restano
 * uguali, cosi' il livello si riconosce da entrambe le parti.
 */
@Composable
private fun levelStyle(level: String?, theirs: Boolean = false): LevelStyle = when (level) {
    "wanted" -> LevelStyle(
        if (theirs) AppLocale.tradeRadarTheirLevelWanted else AppLocale.tradeRadarLevelWanted,
        if (theirs) AppLocale.tradeRadarTheirLevelWantedText else AppLocale.tradeRadarLevelWantedText,
        AppColors.orange, Icons.Default.Favorite
    )
    "useful" -> LevelStyle(
        if (theirs) AppLocale.tradeRadarTheirLevelUseful else AppLocale.tradeRadarLevelUseful,
        if (theirs) AppLocale.tradeRadarTheirLevelUsefulText else AppLocale.tradeRadarLevelUsefulText,
        AppColors.blue, Icons.Default.AddCircle
    )
    else -> LevelStyle(
        AppLocale.tradeRadarLevelPossible,
        if (theirs) AppLocale.tradeRadarTheirLevelPossibleText else AppLocale.tradeRadarLevelPossibleText,
        AppColors.textMuted, Icons.Default.Explore
    )
}

/** Il motivo preciso, quando c'e'; null per "Ti manca" e "Altre carte", che non ne hanno uno. */
private fun reasonLabel(item: TradeMatchItem, theirs: Boolean = false): String? =
    reasonText(item.reason, item.setOwned, item.setSize, theirs)

private fun reasonText(reason: String?, setOwned: Int?, setSize: Int?, theirs: Boolean = false): String? = when (reason) {
    "wishlist" -> if (theirs) AppLocale.tradeRadarTheirReasonWishlist else AppLocale.tradeRadarReasonWishlist
    "album" -> if (theirs) AppLocale.tradeRadarTheirReasonAlbum else AppLocale.tradeRadarReasonAlbum
    "set" -> if (setOwned != null && setSize != null && setSize > 0) AppLocale.tradeRadarReasonSetProgress(setOwned, setSize)
        else AppLocale.tradeRadarReasonSet
    else -> null
}

private fun distanceLabel(distance: String?): String = when (distance) {
    "lt5" -> AppLocale.tradeRadarDistanceNear
    // Solo nelle proposte: chi nel frattempo si e' spostato fuori zona.
    "far" -> AppLocale.tradeRadarDistanceFar
    else -> AppLocale.tradeRadarDistanceArea
}

private fun problemText(problem: Problem): String = when (problem) {
    Problem.UNAUTHORIZED -> AppLocale.tradeRadarUnauthorized
    Problem.REJECTED -> AppLocale.tradeRadarRejected
    Problem.NO_LOCATION -> AppLocale.tradeRadarNoLocation
    Problem.UNAVAILABLE -> AppLocale.tradeRadarUnavailable(null)
    Problem.ALREADY_OPEN -> AppLocale.tradeRadarAlreadyOpen
    Problem.NOT_AVAILABLE -> AppLocale.tradeRadarNotAvailable
}

private fun infoText(info: TradeRadarViewModel.Info): String = when (info) {
    TradeRadarViewModel.Info.PROPOSAL_SENT -> AppLocale.tradeRadarInfoSent
    TradeRadarViewModel.Info.COUNTER_SENT -> AppLocale.tradeRadarInfoCounterSent
    TradeRadarViewModel.Info.ACCEPTED -> AppLocale.tradeRadarInfoAccepted
    TradeRadarViewModel.Info.DECLINED -> AppLocale.tradeRadarInfoDeclined
    TradeRadarViewModel.Info.CANCELLED -> AppLocale.tradeRadarInfoCancelled
}
