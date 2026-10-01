package com.emabuia.pokevault.ui.trade

import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.net.Uri
import android.os.Build
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
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.window.Popup
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.ImageLoader
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.remote.PokeVaultApiClient
import com.emabuia.pokevault.data.trade.CoarseLocation
import com.emabuia.pokevault.data.trade.dto.TradeCardHolder
import com.emabuia.pokevault.data.trade.dto.TradeCardOffer
import com.emabuia.pokevault.data.trade.dto.TradeLeaderboardEntry
import com.emabuia.pokevault.data.trade.dto.TradeLeaderboardMe
import com.emabuia.pokevault.data.trade.dto.TradeMatch
import com.emabuia.pokevault.data.trade.dto.TradeMatchItem
import com.emabuia.pokevault.data.trade.dto.TradeOfferItem
import com.emabuia.pokevault.data.trade.dto.TradeProfilePayload
import com.emabuia.pokevault.data.trade.dto.TradeProposal
import com.emabuia.pokevault.data.trade.dto.TradeReputation
import com.emabuia.pokevault.data.trade.dto.TradeSlot
import com.emabuia.pokevault.data.trade.dto.TradeSpot
import com.emabuia.pokevault.data.trade.TradeCardKey
import com.emabuia.pokevault.data.trade.TradeLists
import com.emabuia.pokevault.data.trade.TradePush
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
fun TradeRadarScreen(onBack: () -> Unit, onPremiumRequired: () -> Unit = {}, viewModel: TradeRadarViewModel = viewModel()) {
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
                        IconButton(onClick = { viewModel.openLeaderboard() }) {
                            Icon(Icons.Default.EmojiEvents, AppLocale.tradeRadarLeaderboard, tint = AppColors.gold)
                        }
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
                                    text = { Text(AppLocale.tradeRadarNotifyTitle) },
                                    onClick = { menuOpen = false; viewModel.openNotify() }
                                )
                                DropdownMenuItem(
                                    text = { Text(AppLocale.tradeRadarBlockedTitle) },
                                    onClick = { menuOpen = false; viewModel.openBlocked() }
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
                is Screen.Ready -> Hub(viewModel, screen, onPremiumRequired)
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
private fun Hub(viewModel: TradeRadarViewModel, ready: Screen.Ready, onPremiumRequired: () -> Unit) {
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

    // Notifiche: il telefono si registra entrando, e il permesso di Android 13
    // si chiede qui e una volta sola, non all'apertura dell'app.
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        viewModel.registerPush()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && viewModel.shouldAskPushPermission() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.markPushPermissionAsked()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Arrivati da una notifica: la tab giusta, con i dati freschi.
    val opened = TradePush.pendingOpen
    LaunchedEffect(opened) {
        TradePush.consumeOpen()?.let { open ->
            tab = if (open.screen == "proposals") 1 else 0
            viewModel.refreshProposals()
            viewModel.refreshMatches()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ProfileHeader(
            nickname = ready.profile.nickname.orEmpty(),
            paused = paused,
            onPausedChange = { viewModel.setPaused(it) },
            reputation = reputationText(ready.profile.reputation, ready.profile.tradesDone ?: 0),
            tier = ready.profile.tier,
            // Al terzo scambio si chiede se comparire in classifica (finche' non si risponde).
            inviteToLeaderboard = (ready.profile.tradesDone ?: 0) >= 3 && ready.profile.leaderboardOptIn == null,
            onOpenLeaderboard = { viewModel.openLeaderboard() }
        )
        ready.profile.suspendedUntil?.let { until ->
            SuspensionBanner(until, ready.profile.suspensionReason, ready.profile.nickname.orEmpty(), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        // La domanda sulle carte cercate, finche' non si risponde (si cambia poi da ⋮ → Notifiche).
        if (ready.profile.notify != null && ready.profile.notify.wants == null && !paused && ready.profile.suspendedUntil == null) {
            WantsAlertsCard(onAnswer = { viewModel.setNotifyPref("wants", it) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
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
    viewModel.planner?.let { planner -> PlannerDialog(viewModel, planner) }
    viewModel.closing?.let { closing -> ClosingDialog(viewModel, closing) }
    viewModel.feedback?.let { feedback -> FeedbackDialog(viewModel, feedback) }
    viewModel.leaderboard?.let { board -> LeaderboardDialog(viewModel, board, ready.profile, onPremiumRequired) }
    // Dopo la classifica: aperti da li' (mini profilo) devono starle sopra.
    viewModel.safety?.let { target ->
        if (target.report) {
            ReportDialog(
                nickname = target.nickname,
                busy = viewModel.safetyBusy,
                onSend = { reason, note, alsoBlock -> viewModel.report(reason, note, alsoBlock) },
                onDismiss = { viewModel.closeSafety() }
            )
        } else {
            BlockDialog(target.nickname, busy = viewModel.safetyBusy, onConfirm = { viewModel.block() }, onDismiss = { viewModel.closeSafety() })
        }
    }
    if (viewModel.notifyOpen) {
        NotifySettingsDialog(ready.profile.notify, onChange = { kind, enabled -> viewModel.setNotifyPref(kind, enabled) }, onDismiss = { viewModel.closeNotify() })
    }
    if (viewModel.blockedOpen) {
        BlockedListDialog(viewModel.blocked, onUnblock = { viewModel.unblock(it) }, onDismiss = { viewModel.closeBlocked() })
    }
    // Fuori dalla classifica la festa va in un popup: qui si e' in coda a una colonna gia' piena.
    if (viewModel.leaderboard == null && viewModel.celebration != null) {
        Popup(onDismissRequest = { viewModel.consumeCelebration() }) { CelebrationOverlay(viewModel) }
    }
    LaunchedEffect(ready.profile.tier) { viewModel.checkTier(ready.profile.tier) }
}

@Composable
private fun ProfileHeader(
    nickname: String,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
    reputation: String = "",
    tier: String? = null,
    inviteToLeaderboard: Boolean = false,
    onOpenLeaderboard: () -> Unit = {}
) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(nickname, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.textPrimary)
                TierBadge(tier)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (paused) AppLocale.tradeRadarPausedLabel else AppLocale.tradeRadarActiveLabel,
                    fontSize = 12.sp,
                    color = AppColors.textSecondary
                )
            }
            if (reputation.isNotBlank()) Text(reputation, fontSize = 11.sp, color = AppColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (inviteToLeaderboard) {
                Text(
                    "🏆 " + AppLocale.tradeRadarInviteLeaderboard,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.gold,
                    modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenLeaderboard)
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
                            onPropose = { viewModel.openComposer(match) },
                            onSafety = { report -> viewModel.openSafety(match.id, match.nickname, null, report) }
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
            onSafety = { match, report -> sheet = null; viewModel.openSafety(match.id, match.nickname, null, report) },
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(match.nickname.orEmpty(), fontWeight = FontWeight.Bold, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TierBadge(match.tier, compact = true)
                }
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
    onPropose: () -> Unit,
    onSafety: (report: Boolean) -> Unit
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
                    TierBadge(match.tier, compact = true)
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
                MatchBody(match, level, onCardClick, onPropose, onSafety)
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
    onSafety: (TradeMatch, report: Boolean) -> Unit,
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
                    onPropose = { match -> onPropose(match, current.from?.key) },
                    onSafety = onSafety
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
    onPropose: (TradeMatch) -> Unit,
    onSafety: (TradeMatch, report: Boolean) -> Unit
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
        if (match != null) MatchCard(match, level, onCardClick, onPropose = { onPropose(match) }, onSafety = { onSafety(match, it) })
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
    onPropose: (() -> Unit)? = null,
    onSafety: ((report: Boolean) -> Unit)? = null
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(match.nickname.orEmpty(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.textPrimary)
                    TierBadge(match.tier)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, null, tint = AppColors.textMuted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "${distanceLabel(match.distance)} · ${reputationText(match.reputation, match.tradesDone ?: 0)}",
                        fontSize = 12.sp,
                        color = AppColors.textSecondary
                    )
                }
            }
        }
        MatchBody(match, level, onCardClick, onPropose, onSafety)
    }
}

/** Fascia "Scambio reciproco" e le due file di carte: cosa ti da', cosa vuole da te. */
@Composable
private fun MatchBody(
    match: TradeMatch,
    level: String,
    onCardClick: (TradeMatchItem, theirs: Boolean) -> Unit,
    onPropose: (() -> Unit)? = null,
    onSafety: ((report: Boolean) -> Unit)? = null
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
    if (onSafety != null) PersonSafetyLink(onSafety)
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

/** I contenitori della tab Proposte, nell'ordine in cui si mostrano. */
private enum class ProposalBucket { TO_ANSWER, MEETINGS, AGREED, WAITING, CLOSED }

private fun bucketOf(proposal: TradeProposal, applied: Set<String> = emptySet()): ProposalBucket = when {
    proposal.actionNeeded == true || (proposal.status == "open" && proposal.myTurn == true) -> ProposalBucket.TO_ANSWER
    proposal.status == "done" && proposal.id !in applied -> ProposalBucket.TO_ANSWER
    proposal.status == "open" -> ProposalBucket.WAITING
    proposal.status == "scheduled" -> ProposalBucket.MEETINGS
    proposal.status == "accepted" -> ProposalBucket.AGREED
    else -> ProposalBucket.CLOSED
}

@Composable
private fun bucketStyle(bucket: ProposalBucket): LevelStyle = when (bucket) {
    ProposalBucket.TO_ANSWER -> LevelStyle(AppLocale.tradeRadarSectionToAnswer, AppLocale.tradeRadarBucketEmptyToAnswer, AppColors.orange, Icons.Default.Inbox)
    ProposalBucket.MEETINGS -> LevelStyle(AppLocale.tradeRadarBucketMeetings, AppLocale.tradeRadarBucketEmptyMeetings, AppColors.green, Icons.Default.Event)
    ProposalBucket.AGREED -> LevelStyle(AppLocale.tradeRadarBucketAgreed, AppLocale.tradeRadarBucketEmptyAgreed, AppColors.purple, Icons.Default.Handshake)
    ProposalBucket.WAITING -> LevelStyle(AppLocale.tradeRadarStatusWaiting, AppLocale.tradeRadarBucketEmptyWaiting, AppColors.blue, Icons.Default.Schedule)
    ProposalBucket.CLOSED -> LevelStyle(AppLocale.tradeRadarSectionClosed, AppLocale.tradeRadarBucketEmptyClosed, AppColors.textMuted, Icons.Default.Archive)
}

/** Quando cade un appuntamento fissato, per ordinarli: giorno e ora. */
private fun meetingWhen(proposal: TradeProposal): String =
    proposal.meeting?.slot?.let { "${it.day} ${it.time ?: slotPartTime(it.part)}" } ?: "9999"

/** Per le fasce senza ora (le prime prove): un'ora indicativa solo per ordinarle. */
private fun slotPartTime(part: String?): String = when (part) {
    "morning" -> "09:00"
    "afternoon" -> "15:00"
    else -> "20:00"
}

/**
 * La tab Proposte, pensata per quando sono tante. In cima il prossimo
 * appuntamento, se c'e', grande e con le mappe. Sotto cinque contenitori (tocca a
 * te, appuntamenti, accordi, in attesa, chiuse) e se ne guarda uno alla volta,
 * a righe compatte con un'etichetta che dice che cos'e' ognuna. Il
 * dettaglio con i tasti si apre al tocco. Si apre da sola sul contenitore che
 * conta: prima cio' che aspetta te, poi gli appuntamenti.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProposalsTab(viewModel: TradeRadarViewModel, onGoToMatches: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refreshProposals() }
    val proposals = viewModel.proposals
    val applied = viewModel.appliedClosings
    val byBucket = proposals.groupBy { bucketOf(it, applied) }
    val automatic = listOf(ProposalBucket.TO_ANSWER, ProposalBucket.MEETINGS, ProposalBucket.AGREED, ProposalBucket.WAITING, ProposalBucket.CLOSED)
        .firstOrNull { !byBucket[it].isNullOrEmpty() } ?: ProposalBucket.TO_ANSWER
    val bucket = viewModel.proposalsBucket?.let { name -> ProposalBucket.entries.firstOrNull { it.name == name } } ?: automatic
    var query by rememberSaveable { mutableStateOf("") }
    /** Gli id delle proposte nel dettaglio: una, o la cronologia di una persona. */
    var detail by remember { mutableStateOf<List<String>?>(null) }
    var confirmCancel by remember { mutableStateOf<TradeProposal?>(null) }
    val today = java.time.LocalDate.now().toString()
    val nextMeeting = byBucket[ProposalBucket.MEETINGS].orEmpty()
        .filter { (it.meeting?.slot?.day ?: "") >= today }
        .minByOrNull(::meetingWhen)

    val needle = query.trim()
    fun hit(proposal: TradeProposal): Boolean =
        needle.isEmpty() ||
            proposal.counterpart?.nickname.orEmpty().contains(needle, ignoreCase = true) ||
            proposal.meeting?.spot?.name.orEmpty().contains(needle, ignoreCase = true) ||
            (proposal.give.orEmpty() + proposal.take.orEmpty()).any {
                it.name.orEmpty().contains(needle, ignoreCase = true) || it.setName.orEmpty().contains(needle, ignoreCase = true)
            }
    val shown = byBucket[bucket].orEmpty().filter(::hit)
        .let { list -> if (bucket == ProposalBucket.MEETINGS) list.sortedBy(::meetingWhen) else list }
    val rows: List<List<TradeProposal>> = if (bucket == ProposalBucket.CLOSED) {
        shown.groupBy { it.counterpart?.id ?: it.id }.values.map { group -> group.sortedByDescending { it.updatedAt ?: 0L } }
    } else {
        shown.map { listOf(it) }
    }

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
                else -> {
                    nextMeeting?.let { meeting ->
                        item(key = "nextMeeting") {
                            NextMeetingCard(meeting, onClick = { detail = listOfNotNull(meeting.id) }, modifier = Modifier.animateItem())
                        }
                    }
                    item(key = "buckets") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ProposalBucket.entries) { entry ->
                                val style = bucketStyle(entry)
                                FilterChip(style.label, byBucket[entry].orEmpty().size, style, entry == bucket) {
                                    viewModel.selectProposalsBucket(entry.name)
                                }
                            }
                        }
                    }
                    if (proposals.size > 5) {
                        item(key = "search") { SearchField(query, onChange = { query = it }, modifier = Modifier.fillMaxWidth()) }
                    }
                    if (rows.isEmpty()) {
                        item(key = "emptyBucket") {
                            EmptyState(text = if (needle.isNotEmpty()) AppLocale.tradeRadarNoSearchResults else bucketStyle(bucket).description, radar = false)
                        }
                    }
                    items(rows, key = { group -> "row|" + group.first().id.orEmpty() }) { group ->
                        ProposalRow(
                            group = group,
                            prices = viewModel.prices,
                            applied = applied,
                            onClick = { detail = group.mapNotNull { it.id } },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }

    detail?.let { ids ->
        val group = ids.mapNotNull { id -> proposals.firstOrNull { it.id == id } }
        if (group.isEmpty()) {
            LaunchedEffect(ids) { detail = null }
        } else {
            ProposalSheet(
                group = group,
                viewModel = viewModel,
                onCounter = { proposal -> detail = null; viewModel.openCounter(proposal) },
                onCancelDeal = { confirmCancel = it },
                onPlan = { proposal -> detail = null; viewModel.openPlanner(proposal) },
                onDismiss = { detail = null }
            )
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

/** Il datario: giorno della settimana, numero, mese e ora, come un foglio di calendario. */
@Composable
private fun DateTile(slot: TradeSlot?, size: Dp, color: Color) {
    val date = slot?.let { runCatching { java.time.LocalDate.parse(it.day) }.getOrNull() }
    val locale = if (AppLocale.isItalian) Locale.ITALIAN else Locale.ENGLISH
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(size).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f))
    ) {
        Text(
            date?.format(java.time.format.DateTimeFormatter.ofPattern("EEE", locale))?.uppercase(locale).orEmpty(),
            fontSize = (size.value * 0.17f).sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.onAccent,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(color).padding(vertical = 2.dp)
        )
        Text(date?.dayOfMonth?.toString() ?: "?", fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.Black, color = AppColors.textPrimary)
        Text(
            date?.format(java.time.format.DateTimeFormatter.ofPattern("MMM", locale)).orEmpty(),
            fontSize = (size.value * 0.16f).sp,
            color = AppColors.textSecondary
        )
        Text(
            slot?.time ?: slot?.part?.let(::slotPartLabel).orEmpty(),
            fontSize = (size.value * 0.18f).sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

/** In cima alla tab: il prossimo appuntamento fissato, grande, con chi, dove e le mappe. */
@Composable
private fun NextMeetingCard(proposal: TradeProposal, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val meeting = proposal.meeting
    val shape = RoundedCornerShape(22.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(AppColors.green.copy(alpha = 0.18f), AppColors.card)))
            .background(AppColors.card.copy(alpha = 0.45f))
            .border(1.dp, AppColors.green.copy(alpha = 0.5f), shape)
            .pressScale(scaleDown = 0.98f, onClick = onClick)
            .padding(14.dp)
    ) {
        DateTile(meeting?.slot, size = 64.dp, color = AppColors.green)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(AppLocale.tradeRadarNextMeeting.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.green)
            Text(proposal.counterpart?.nickname.orEmpty(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
            meeting?.spot?.let { spot ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(spotKindIcon(spot.kind), null, tint = spotKindColor(spot.kind), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(spot.name.orEmpty(), fontSize = 13.sp, color = AppColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                AppLocale.tradeRadarRowSummary(proposal.take.orEmpty().sumOf { it.qty ?: 1 }, proposal.give.orEmpty().sumOf { it.qty ?: 1 }),
                fontSize = 12.sp,
                color = AppColors.textMuted
            )
        }
        meeting?.spot?.let { spot ->
            IconButton(onClick = { openInMaps(context, spot) }) {
                Icon(Icons.Default.Map, AppLocale.tradeRadarOpenMaps, tint = AppColors.green)
            }
        }
    }
}

private data class RowTag(val text: String, val icon: ImageVector, val color: Color)

/** Che cos'e' una riga, a colpo d'occhio: proposta, controproposta, accordo, appuntamento... */
@Composable
private fun rowTag(proposal: TradeProposal, groupSize: Int, applied: Set<String> = emptySet()): RowTag {
    val meeting = proposal.meeting
    return when {
        proposal.status == "open" && proposal.myTurn == true && (proposal.revision ?: 1) > 1 ->
            RowTag(AppLocale.tradeRadarTagCounter, Icons.AutoMirrored.Filled.Reply, AppColors.orange)
        proposal.status == "open" && proposal.myTurn == true -> RowTag(AppLocale.tradeRadarTagNew, Icons.Default.Mail, AppColors.orange)
        proposal.status == "open" -> RowTag(AppLocale.tradeRadarTagSent, Icons.AutoMirrored.Filled.Send, AppColors.blue)
        proposal.status == "scheduled" && proposal.doneByOther == true && proposal.doneByMe != true ->
            RowTag(AppLocale.tradeRadarTagConfirmDone, Icons.Default.Verified, AppColors.orange)
        proposal.status == "scheduled" -> RowTag(AppLocale.tradeRadarTagMeeting, Icons.Default.Event, AppColors.green)
        proposal.status == "done" && proposal.id !in applied -> RowTag(AppLocale.tradeRadarUpdateCollection, Icons.Default.Inventory2, AppColors.orange)
        proposal.status == "done" && proposal.myRating == null -> RowTag(AppLocale.tradeRadarTagRate, Icons.Default.Star, AppColors.orange)
        proposal.status == "done" -> RowTag(AppLocale.tradeRadarStatusDone, Icons.Default.Verified, AppColors.green)
        proposal.status == "no_show" && proposal.canDispute == true -> RowTag(AppLocale.tradeRadarTagDispute, Icons.Default.PersonOff, AppColors.orange)
        proposal.status == "no_show" -> RowTag(AppLocale.tradeRadarStatusNoShow, Icons.Default.PersonOff, AppColors.red)
        proposal.status == "accepted" && meeting?.status == "proposed" && meeting.byMe != true ->
            RowTag(AppLocale.tradeRadarTagPickTime, Icons.Default.Schedule, AppColors.orange)
        proposal.status == "accepted" && meeting?.status == "proposed" -> RowTag(AppLocale.tradeRadarTagTimeProposed, Icons.Default.Schedule, AppColors.blue)
        proposal.status == "accepted" -> RowTag(AppLocale.tradeRadarTagDeal, Icons.Default.Handshake, AppColors.purple)
        groupSize > 1 -> RowTag("${proposalStatus(proposal).label} · ${AppLocale.tradeRadarClosedCount(groupSize)}", Icons.Default.Archive, AppColors.textMuted)
        else -> RowTag(proposalStatus(proposal).label, Icons.Default.Archive, AppColors.textMuted)
    }
}

/**
 * Una proposta in una riga: chi, quando, un'etichetta con che cos'e', cosa
 * ricevi e cosa dai in miniatura, e se il bilancio e' equo. Gli appuntamenti
 * hanno il datario al posto dell'iniziale e il luogo al posto delle carte.
 * Nelle chiuse la riga e' la persona, con quante proposte chiuse ci sono.
 */
@Composable
private fun ProposalRow(
    group: List<TradeProposal>,
    prices: Map<String, Double>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    applied: Set<String> = emptySet()
) {
    val proposal = group.first()
    val bucket = bucketOf(proposal, applied)
    val shape = RoundedCornerShape(18.dp)
    val take = proposal.take.orEmpty()
    val give = proposal.give.orEmpty()
    val verdict = balanceVerdict(give, take, prices)
    val tag = rowTag(proposal, group.size, applied)
    val scheduled = proposal.status == "scheduled"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.card)
            .then(
                when (bucket) {
                    ProposalBucket.TO_ANSWER -> Modifier.border(1.dp, AppColors.orange.copy(alpha = 0.5f), shape)
                    ProposalBucket.MEETINGS -> Modifier.border(1.dp, AppColors.green.copy(alpha = 0.45f), shape)
                    else -> Modifier
                }
            )
            .pressScale(scaleDown = 0.98f, onClick = onClick)
            .padding(12.dp)
    ) {
        if (scheduled) DateTile(proposal.meeting?.slot, size = 48.dp, color = AppColors.green)
        else Avatar(proposal.counterpart?.nickname.orEmpty(), size = 42.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    proposal.counterpart?.nickname.orEmpty(),
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!scheduled) Text(timeAgo(proposal.updatedAt), fontSize = 11.sp, color = AppColors.textMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(tag.icon, null, tint = tag.color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(tag.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tag.color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (scheduled) {
                proposal.meeting?.spot?.let { spot ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(spotKindIcon(spot.kind), null, tint = spotKindColor(spot.kind), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(spot.name.orEmpty(), fontSize = 12.sp, color = AppColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniStack(take)
                    Icon(Icons.Default.SwapHoriz, null, tint = AppColors.textMuted, modifier = Modifier.padding(horizontal = 6.dp).size(16.dp))
                    MiniStack(give)
                    Spacer(Modifier.weight(1f))
                    verdict?.let { (text, color) -> InfoPill(text, color) }
                }
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppColors.textMuted)
    }
}

/** Fino a tre miniature una sopra l'altra, con "+N" per il resto delle copie. */
@Composable
private fun MiniStack(items: List<TradeOfferItem>) {
    val copies = items.sumOf { it.qty ?: 1 }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
            items.take(3).forEach { item ->
                Box(Modifier.width(26.dp).aspectRatio(0.716f).clip(RoundedCornerShape(3.dp)).background(AppColors.card)) {
                    CardImageSkeleton()
                    AsyncImage(
                        model = TradeCardKey.imageUrl(item.key.orEmpty(), PokeVaultApiClient.imageBaseUrl),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        val shownCopies = items.take(3).size
        if (copies > shownCopies) {
            Spacer(Modifier.width(4.dp))
            Text("+${copies - shownCopies}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.textSecondary)
        }
    }
}

/**
 * Il dettaglio di una proposta, con i tasti; per le chiuse di una persona
 * anche la cronologia. Contenuto a misura, insets solo in basso.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProposalSheet(
    group: List<TradeProposal>,
    viewModel: TradeRadarViewModel,
    onCounter: (TradeProposal) -> Unit,
    onCancelDeal: (TradeProposal) -> Unit,
    onPlan: (TradeProposal) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.background,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            group.forEachIndexed { index, proposal ->
                if (index == 1) SectionTitle(AppLocale.tradeRadarHistory, group.size - 1)
                ProposalCard(
                    proposal = proposal,
                    prices = viewModel.prices,
                    acting = viewModel.actingOn == proposal.id,
                    onAccept = { viewModel.answer(proposal.id.orEmpty(), "accept") },
                    onDecline = { viewModel.answer(proposal.id.orEmpty(), "decline") },
                    onCounter = { onCounter(proposal) },
                    onCancel = {
                        if (proposal.status == "accepted" || proposal.status == "scheduled") onCancelDeal(proposal)
                        else viewModel.answer(proposal.id.orEmpty(), "cancel")
                    },
                    onPlan = { onPlan(proposal) },
                    onConfirmMeeting = { index -> viewModel.confirmMeeting(proposal.id.orEmpty(), index) },
                    needsCollection = viewModel.needsCollectionUpdate(proposal),
                    onDone = { viewModel.markDone(proposal) },
                    onNoShow = { viewModel.markNoShow(proposal) },
                    onDispute = { viewModel.disputeNoShow(proposal) },
                    onCollection = { viewModel.openClosing(proposal) },
                    onFeedback = { viewModel.openFeedback(proposal) },
                    // Una volta per persona: il gruppo e' tutto con lei.
                    onSafety = if (index == 0) {
                        { report -> viewModel.openSafety(proposal.counterpart?.id, proposal.counterpart?.nickname, proposal.id, report) }
                    } else null
                )
            }
        }
    }
}

/** "adesso", "5 min fa", "3 ore fa", "ieri", "4 giorni fa". */
private fun timeAgo(at: Long?): String {
    if (at == null || at <= 0L) return ""
    val minutes = ((System.currentTimeMillis() - at) / 60_000L).coerceAtLeast(0L)
    return AppLocale.tradeRadarTimeAgo(minutes)
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
    modifier: Modifier = Modifier,
    onPlan: () -> Unit = {},
    onConfirmMeeting: (Int) -> Unit = {},
    needsCollection: Boolean = false,
    onDone: () -> Unit = {},
    onNoShow: () -> Unit = {},
    onDispute: () -> Unit = {},
    onCollection: () -> Unit = {},
    onFeedback: () -> Unit = {},
    onSafety: ((report: Boolean) -> Unit)? = null
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
            .then(if (myTurn || proposal.actionNeeded == true || proposal.status == "accepted" || proposal.status == "scheduled") Modifier.border(1.dp, status.color.copy(alpha = 0.5f), shape) else Modifier)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(other?.nickname.orEmpty(), size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(other?.nickname.orEmpty(), fontWeight = FontWeight.Bold, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TierBadge(other?.tier, compact = true)
                }
                Text(
                    listOfNotNull(
                        distanceLabel(other?.distance),
                        (proposal.revision ?: 1).takeIf { it > 1 && open }?.let { AppLocale.tradeRadarCounterNote(it) },
                        reputationText(other?.reputation, other?.tradesDone ?: 0)
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
            proposal.status == "accepted" || proposal.status == "scheduled" -> MeetingSection(
                proposal = proposal,
                acting = acting,
                onPlan = onPlan,
                onConfirm = onConfirmMeeting,
                onCancel = onCancel,
                onDone = onDone,
                onNoShow = onNoShow
            )
            proposal.status == "done" -> DoneSection(proposal, needsCollection, onCollection = onCollection, onFeedback = onFeedback)
            proposal.status == "no_show" -> NoShowSection(proposal, acting, onDispute)
            proposal.status == "expired" -> Text(AppLocale.tradeRadarExpiredText, fontSize = 12.sp, color = AppColors.textMuted)
        }
        if (onSafety != null) PersonSafetyLink(onSafety)
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
    "accepted" -> if (proposal.meeting?.status == "proposed" && proposal.meeting.byMe != true) {
        StatusStyle(AppLocale.tradeRadarStatusPickSlot, AppColors.orange)
    } else StatusStyle(AppLocale.tradeRadarStatusAccepted, AppColors.green)
    "scheduled" -> StatusStyle(AppLocale.tradeRadarStatusScheduled, AppColors.green)
    "done" -> StatusStyle(AppLocale.tradeRadarStatusDone, AppColors.green)
    "no_show" -> StatusStyle(AppLocale.tradeRadarStatusNoShow, AppColors.red)
    "expired" -> StatusStyle(AppLocale.tradeRadarStatusExpired, AppColors.textMuted)
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
    val unpriced = (give + take).count { prices[it.key.orEmpty()] == null }
    val giveValue = valueOf(give, prices)
    val takeValue = valueOf(take, prices)
    val verdict = balanceVerdict(give, take, prices)
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

private fun valueOf(items: List<TradeOfferItem>, prices: Map<String, Double>): Double =
    items.sumOf { (prices[it.key.orEmpty()] ?: 0.0) * (it.qty ?: 1) }

/** Equo (entro 1 € o il 10%), o a favore di chi, con il suo colore; null senza prezzi. */
@Composable
private fun balanceVerdict(give: List<TradeOfferItem>, take: List<TradeOfferItem>, prices: Map<String, Double>): Pair<String, Color>? {
    val giveValue = valueOf(give, prices)
    val takeValue = valueOf(take, prices)
    val diff = takeValue - giveValue
    return when {
        giveValue == 0.0 && takeValue == 0.0 -> null
        kotlin.math.abs(diff) <= maxOf(1.0, 0.1 * maxOf(giveValue, takeValue)) -> AppLocale.tradeRadarBalanceFair to AppColors.green
        diff > 0 -> AppLocale.tradeRadarBalanceForYou(euro(diff)) to AppColors.blue
        else -> AppLocale.tradeRadarBalanceForThem(euro(-diff)) to AppColors.orange
    }
}

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

// ── Appuntamento (fase 2b) ──────────────────────────────────────────────────

/** Il localizzatore ufficiale dei tornei: solo un link, i suoi dati non si copiano (termini d'uso). */
private const val POKEMON_EVENT_LOCATOR = "https://events.pokemon.com/EventLocator/"

private fun slotPartLabel(part: String?): String = when (part) {
    "morning" -> AppLocale.tradeRadarMorning
    "afternoon" -> AppLocale.tradeRadarAfternoon
    else -> AppLocale.tradeRadarEvening
}

/** "sab 4 ott · Pomeriggio" */
private fun slotLabel(slot: TradeSlot): String {
    val date = runCatching { java.time.LocalDate.parse(slot.day) }.getOrNull() ?: return slot.day
    val locale = if (AppLocale.isItalian) Locale.ITALIAN else Locale.ENGLISH
    val day = date.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", locale))
    return "$day · ${slot.time ?: slotPartLabel(slot.part)}"
}

private fun spotKindIcon(kind: String?): ImageVector = when (kind) {
    "card_shop" -> Icons.Default.Style
    "comics" -> Icons.AutoMirrored.Filled.MenuBook
    "games", "video_games" -> Icons.Default.SportsEsports
    "toys" -> Icons.Default.Toys
    "mall" -> Icons.Default.LocalMall
    "library" -> Icons.Default.LocalLibrary
    else -> Icons.Default.Place
}

private fun spotKindLabel(kind: String?): String = when (kind) {
    "card_shop" -> AppLocale.tradeRadarKindCardShop
    "comics" -> AppLocale.tradeRadarKindComics
    "games" -> AppLocale.tradeRadarKindGames
    "video_games" -> AppLocale.tradeRadarKindVideoGames
    "toys" -> AppLocale.tradeRadarKindToys
    "mall" -> AppLocale.tradeRadarKindMall
    "library" -> AppLocale.tradeRadarKindLibrary
    else -> AppLocale.tradeRadarKindOther
}

@Composable
private fun spotKindColor(kind: String?): Color = when (kind) {
    "card_shop", "comics", "games" -> AppColors.orange
    "video_games", "toys" -> AppColors.purple
    "mall", "library" -> AppColors.blue
    else -> AppColors.textMuted
}

/** Apre il luogo nell'app di mappe: per nome e coordinate, o per nome se e' una segnalazione. */
private fun openInMaps(context: android.content.Context, spot: TradeSpot) {
    val name = Uri.encode(listOfNotNull(spot.name, spot.address?.takeIf { it.isNotBlank() }, spot.city).joinToString(", "))
    // Le coordinate di una segnalazione vengono dall'indirizzo: vanno bene anche prima della verifica.
    val uri = if (spot.lat != null && spot.lon != null && (spot.pending != true || !spot.address.isNullOrBlank())) {
        Uri.parse("geo:${spot.lat},${spot.lon}?q=${spot.lat},${spot.lon}($name)")
    } else {
        Uri.parse("geo:0,0?q=$name")
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/**
 * L'appuntamento dentro la scheda di un accordo. Quattro momenti: da fissare,
 * proposto da me (si aspetta), proposto dall'altro (si sceglie una fascia),
 * fissato (giorno, luogo, mappe).
 */
@Composable
private fun MeetingSection(
    proposal: TradeProposal,
    acting: Boolean,
    onPlan: () -> Unit,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit = {},
    onNoShow: () -> Unit = {}
) {
    val context = LocalContext.current
    val meeting = proposal.meeting
    val nickname = proposal.counterpart?.nickname.orEmpty()
    val status = meeting?.status ?: "none"
    var chosen by remember(proposal.id, meeting?.slots) { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            status == "confirmed" && meeting?.slot != null -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.green.copy(alpha = 0.12f)).padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Event, null, tint = AppColors.green, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(slotLabel(meeting.slot), fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
                    }
                    meeting.spot?.let { SpotSummary(it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    meeting.spot?.let { spot ->
                        Button(
                            onClick = { openInMaps(context, spot) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.green),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Map, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(AppLocale.tradeRadarOpenMaps, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    OutlinedButton(onClick = onPlan, enabled = !acting, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Text(AppLocale.tradeRadarChangeMeeting)
                    }
                }
                ClosingControls(proposal, acting, onDone = onDone, onNoShow = onNoShow)
            }
            status == "proposed" && meeting?.byMe == true -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(innerColor()).padding(12.dp)
                ) {
                    meeting.spot?.let { SpotSummary(it) }
                    FlowRowSlots(meeting.slots.orEmpty())
                    Text(AppLocale.tradeRadarMeetingWaiting(nickname), fontSize = 12.sp, color = AppColors.blue)
                }
                TextButton(onClick = onPlan, enabled = !acting) { Text(AppLocale.tradeRadarChangeMeeting) }
            }
            status == "proposed" -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.orange.copy(alpha = 0.10f))
                        .padding(12.dp)
                ) {
                    Text(AppLocale.tradeRadarMeetingPick(nickname), fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary, fontSize = 14.sp)
                    meeting?.spot?.let { SpotSummary(it) }
                    meeting?.slots.orEmpty().forEachIndexed { index, slot ->
                        val selected = chosen == index
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) AppColors.green.copy(alpha = 0.18f) else AppColors.card)
                                .border(1.dp, if (selected) AppColors.green else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { chosen = index }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                if (selected) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                null,
                                tint = if (selected) AppColors.green else AppColors.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(slotLabel(slot), color = AppColors.textPrimary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPlan, enabled = !acting, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Text(AppLocale.tradeRadarOtherMeeting, maxLines = 1)
                    }
                    Button(
                        onClick = { chosen?.let(onConfirm) },
                        enabled = chosen != null && !acting,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.green),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (acting) CircularProgressIndicator(Modifier.size(16.dp), color = AppColors.onAccent, strokeWidth = 2.dp)
                        else Text(AppLocale.tradeRadarConfirmMeeting, fontWeight = FontWeight.Bold)
                    }
                }
            }
            else -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.green.copy(alpha = 0.12f)).padding(10.dp)
                ) {
                    Icon(Icons.Default.Handshake, null, tint = AppColors.green, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(AppLocale.tradeRadarMeetingNone, fontSize = 12.sp, color = AppColors.textSecondary)
                }
                Button(
                    onClick = onPlan,
                    enabled = !acting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.green),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Event, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(AppLocale.tradeRadarPlanMeeting, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (!meetingTimePassed(proposal)) {
            TextButton(onClick = onCancel, enabled = !acting, modifier = Modifier.align(Alignment.End)) {
                Text(AppLocale.tradeRadarCancelDeal, color = AppColors.red)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowSlots(slots: List<TradeSlot>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        slots.forEach { InfoPill(slotLabel(it), AppColors.blue) }
    }
}

/** Il luogo in breve: icona del tipo, nome, tipo e citta', orari. */
@Composable
private fun SpotSummary(spot: TradeSpot) {
    val color = spotKindColor(spot.kind)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.15f))) {
            Icon(spotKindIcon(spot.kind), null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(spot.name.orEmpty(), fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(spotKindLabel(spot.kind), spot.city?.takeIf { it.isNotBlank() }).joinToString(" · "),
                fontSize = 12.sp, color = AppColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            spot.address?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 11.sp, color = AppColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            spot.openingHours?.takeIf { it.isNotBlank() }?.let {
                Text(AppLocale.tradeRadarOpeningHours(it), fontSize = 11.sp, color = AppColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val badges = spot.badges.orEmpty()
            if (badges.isNotEmpty() || (spot.trades ?: 0) > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                    badges.forEach { InfoPill((if (it == "tournaments") "🏆 " else "") + spotBadgeLabel(it), AppColors.purple) }
                    (spot.trades ?: 0).takeIf { it > 0 }?.let { InfoPill(AppLocale.tradeRadarTradesHere(it), AppColors.green) }
                }
            }
            if (spot.pending == true) {
                Text(AppLocale.tradeRadarPendingExplain, fontSize = 11.sp, color = AppColors.orange)
            }
        }
    }
}

/** I tipi che si possono scegliere segnalando un negozio. */
private val ReportKinds = listOf("card_shop", "comics", "games", "other")

/**
 * "Segnala un negozio": nome, citta' (gia' quella della zona), indirizzo
 * facoltativo e tipo. Dall'indirizzo il server ricava dove sta, cosi' le
 * mappe portano nel posto giusto anche prima della verifica.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportSpotDialog(
    initialName: String,
    initialCity: String,
    onSend: (name: String, city: String, address: String, kind: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var city by rememberSaveable { mutableStateOf(initialCity) }
    var address by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("card_shop") }
    val ready = name.trim().length >= 2 && city.trim().length >= 2
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppLocale.tradeRadarReportTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(AppLocale.tradeRadarReportText, fontSize = 13.sp, color = AppColors.textSecondary)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text(AppLocale.tradeRadarReportName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it.take(60) },
                    label = { Text(AppLocale.tradeRadarReportCity) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.take(120) },
                    label = { Text(AppLocale.tradeRadarReportAddress) },
                    supportingText = { Text(AppLocale.tradeRadarReportAddressHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(AppLocale.tradeRadarReportKind, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReportKinds.forEach { option ->
                        val selected = option == kind
                        val color = spotKindColor(option)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) color else innerColor())
                                .clickable { kind = option }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Icon(spotKindIcon(option), null, tint = if (selected) AppColors.onAccent else color, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                spotKindLabel(option),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) AppColors.onAccent else AppColors.textPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(name.trim(), city.trim(), address.trim(), kind) }, enabled = ready) {
                Text(AppLocale.tradeRadarReportSend, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppLocale.cancel) } }
    )
}

/**
 * Il pannello per fissare l'appuntamento, a schermo intero. Dove: i luoghi
 * migliori a meta' strada (gia' scelto il primo), gli altri, "Manca un
 * negozio?" e il link ai tornei ufficiali. Quando: i prossimi 14 giorni,
 * e per ognuno gli orari ogni mezz'ora, fino a tre proposte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlannerDialog(viewModel: TradeRadarViewModel, planner: TradeRadarViewModel.Planner) {
    val context = LocalContext.current
    // "Piu' vicini a me" senza permesso: lo si chiede li', e se arriva si ordina.
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.locateMe() else viewModel.locationDenied()
    }
    var showAllSpots by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    val today = remember { java.time.LocalDate.now() }
    var selectedDay by remember { mutableStateOf(today.plusDays(1)) }
    val selectedSpot = planner.spots.firstOrNull { it.id == planner.selectedSpot }
    val ready = selectedSpot != null && planner.slots.isNotEmpty()

    if (reporting) {
        // La citta' della zona: quella piu' frequente fra i luoghi trovati.
        val zoneCity = planner.spots.mapNotNull { it.city?.takeIf(String::isNotBlank) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
        ReportSpotDialog(
            initialName = planner.query.trim(),
            initialCity = zoneCity,
            onSend = { name, city, address, kind -> reporting = false; viewModel.reportSpot(name, city, address, kind) },
            onDismiss = { reporting = false }
        )
    }
    Dialog(onDismissRequest = { viewModel.closePlanner() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            containerColor = AppColors.background,
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(AppLocale.tradeRadarPlannerTitle, fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
                            Text(AppLocale.tradeRadarWith(planner.nickname), fontSize = 13.sp, color = AppColors.textSecondary)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.closePlanner() }) {
                            Icon(Icons.Default.Close, AppLocale.tradeRadarClose, tint = AppColors.textPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
                )
            },
            bottomBar = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(AppColors.card)
                        .padding(16.dp)
                ) {
                    Text(
                        when {
                            selectedSpot == null -> AppLocale.tradeRadarPickSpot
                            planner.slots.isEmpty() -> AppLocale.tradeRadarPickSlot
                            else -> AppLocale.tradeRadarPlannerSummary(selectedSpot.name.orEmpty(), planner.slots.size)
                        },
                        fontSize = 12.sp,
                        color = if (ready) AppColors.textSecondary else AppColors.orange
                    )
                    if (planner.changing) Text(AppLocale.tradeRadarPlannerChanging(planner.nickname), fontSize = 11.sp, color = AppColors.textMuted)
                    Button(
                        onClick = { viewModel.sendPlanner() },
                        enabled = ready && !planner.sending,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.green),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        if (planner.sending) CircularProgressIndicator(Modifier.size(20.dp), color = AppColors.onAccent, strokeWidth = 2.dp)
                        else Text(AppLocale.tradeRadarSendMeeting, fontWeight = FontWeight.Bold)
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // ── Dove ──
                item(key = "whereTitle") { ComposerSideTitle(Icons.Default.Place, AppLocale.tradeRadarWhere, AppColors.orange) }
                if (planner.loading || planner.downloadingArea) {
                    item(key = "loadingSpots") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            RadarScope(blips = emptyList(), scanning = true, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (planner.downloadingArea) AppLocale.tradeRadarDownloadingArea else AppLocale.tradeRadarLoadingSpots,
                                fontSize = 13.sp, color = AppColors.textSecondary
                            )
                        }
                    }
                }
                if (!planner.loading && planner.spots.isEmpty()) {
                    item(key = "noSpots") { Text(AppLocale.tradeRadarNoSpots, fontSize = 13.sp, color = AppColors.textSecondary) }
                }
                // Distanza da me: calcolata qui con la posizione del telefono, che non va al server.
                val me = planner.myPosition
                fun fromMe(spot: TradeSpot): Double? =
                    if (me != null && spot.lat != null && spot.lon != null) CoarseLocation.distanceKm(me.first, me.second, spot.lat, spot.lon) else null
                val ordered = if (planner.nearestFirst && me != null) planner.spots.sortedBy { fromMe(it) ?: Double.MAX_VALUE } else planner.spots
                val visible = if (showAllSpots) ordered else ordered.take(3)
                if (planner.spots.isNotEmpty()) {
                    item(key = "suggested") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                SortChip(AppLocale.tradeRadarSortSuggestedSpots, Icons.Default.AutoAwesome, selected = !planner.nearestFirst) {
                                    viewModel.setNearestFirst(false)
                                }
                                SortChip(AppLocale.tradeRadarSortNearMe, Icons.Default.NearMe, selected = planner.nearestFirst && me != null) {
                                    when {
                                        me != null -> viewModel.setNearestFirst(true)
                                        !CoarseLocation.hasPermission(context) -> locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        else -> viewModel.locateMe()
                                    }
                                }
                                if (planner.locating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                            when {
                                planner.locating -> Text(AppLocale.tradeRadarLocating, fontSize = 12.sp, color = AppColors.textMuted)
                                planner.locationUnavailable -> Text(AppLocale.tradeRadarNoLocationForSort, fontSize = 12.sp, color = AppColors.orange)
                            }
                            Text(
                                if (planner.nearestFirst && me != null) AppLocale.tradeRadarNearestSpots else AppLocale.tradeRadarSuggestedSpots,
                                fontSize = 12.sp,
                                color = AppColors.textMuted
                            )
                        }
                    }
                }
                items(visible, key = { "spot|" + it.id.orEmpty() }) { spot ->
                    SpotOption(spot, selected = spot.id == planner.selectedSpot, fromMeKm = fromMe(spot), modifier = Modifier.animateItem()) {
                        spot.id?.let(viewModel::selectSpot)
                    }
                }
                if (planner.spots.size > 3) {
                    item(key = "moreSpots") {
                        AddToggle(
                            if (showAllSpots) AppLocale.tradeRadarFewerSpots else AppLocale.tradeRadarMoreSpots(planner.spots.size - 3),
                            open = showAllSpots,
                            enabled = true
                        ) { showAllSpots = !showAllSpots }
                    }
                }
                item(key = "search") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Text(AppLocale.tradeRadarMissingShop, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary, fontSize = 14.sp)
                        OutlinedTextField(
                            value = planner.query,
                            onValueChange = { viewModel.searchSpot(it) },
                            placeholder = { Text(AppLocale.tradeRadarSearchShop, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = AppColors.textMuted) },
                            trailingIcon = {
                                if (planner.searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                items(planner.searchResults, key = { "found|" + it.osmId.orEmpty() + it.name.orEmpty() }) { candidate ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(innerColor())
                            .clickable { viewModel.addSearchedSpot(candidate) }
                            .padding(10.dp)
                    ) {
                        Icon(spotKindIcon(candidate.kind), null, tint = spotKindColor(candidate.kind), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(candidate.name.orEmpty(), fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(candidate.city?.takeIf { it.isNotBlank() }, candidate.distanceKm?.let { AppLocale.tradeRadarKmAway(it) }).joinToString(" · "),
                                fontSize = 12.sp, color = AppColors.textSecondary
                            )
                        }
                        Icon(Icons.Default.AddCircle, null, tint = AppColors.green, modifier = Modifier.size(22.dp))
                    }
                }
                if (planner.query.trim().length >= 2 && !planner.searching) {
                    item(key = "report") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (planner.searchedEmpty) AppLocale.tradeRadarNotOnMap else AppLocale.tradeRadarNotInList,
                                fontSize = 12.sp,
                                color = AppColors.textSecondary
                            )
                            OutlinedButton(onClick = { reporting = true }, shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.AddLocationAlt, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(AppLocale.tradeRadarReportTitle)
                            }
                        }
                    }
                }
                item(key = "locator") {
                    TextButton(onClick = { openUrl(context, POKEMON_EVENT_LOCATOR) }) {
                        Icon(Icons.Default.EmojiEvents, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(AppLocale.tradeRadarEventLocator)
                    }
                }

                item(key = "divider") { Box(Modifier.padding(vertical = 4.dp)) { HorizontalDivider(color = AppColors.textMuted.copy(alpha = 0.2f)) } }

                // ── Quando ──
                item(key = "whenTitle") { ComposerSideTitle(Icons.Default.Event, AppLocale.tradeRadarWhen, AppColors.blue) }
                item(key = "days") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((0L until 14L).map { today.plusDays(it) }) { date ->
                            DayChip(date, today, selected = date == selectedDay, slotsOnDay = planner.slots.count { it.day == date.toString() }) {
                                selectedDay = date
                            }
                        }
                    }
                }
                item(key = "parts") {
                    TimeGrid(
                        day = selectedDay,
                        today = today,
                        chosen = planner.slots,
                        onToggle = { viewModel.toggleSlot(it) }
                    )
                }
                item(key = "chosen") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(AppLocale.tradeRadarSlotsChosen(planner.slots.size), fontSize = 12.sp, color = AppColors.textMuted)
                        planner.slots.forEach { slot ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.blue.copy(alpha = 0.12f)).padding(start = 12.dp)
                            ) {
                                Text(slotLabel(slot), color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.toggleSlot(slot) }) {
                                    Icon(Icons.Default.Close, AppLocale.tradeRadarRemove, tint = AppColors.textSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Gli orari proponibili, ogni mezz'ora, divisi per parte della giornata. */
private val TimeGroups: List<Pair<String, List<String>>> = listOf(
    "morning" to (9..12).flatMap { h -> listOf("%02d:00".format(h), "%02d:30".format(h)) },
    "afternoon" to (13..18).flatMap { h -> listOf("%02d:00".format(h), "%02d:30".format(h)) },
    "evening" to (19..21).flatMap { h -> listOf("%02d:00".format(h), "%02d:30".format(h)) },
)

/**
 * Gli orari di un giorno: tre gruppi (mattina, pomeriggio, sera) di pulsanti
 * ogni mezz'ora. Un tocco aggiunge o toglie l'orario, fino a tre in tutto;
 * oggi gli orari gia' passati non si vedono.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeGrid(day: java.time.LocalDate, today: java.time.LocalDate, chosen: List<TradeSlot>, onToggle: (TradeSlot) -> Unit) {
    val now = java.time.LocalTime.now()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TimeGroups.forEach { (part, times) ->
            val visible = if (day == today) times.filter { java.time.LocalTime.parse(it).isAfter(now.plusMinutes(30)) } else times
            if (visible.isEmpty()) return@forEach
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (part) {
                            "morning" -> Icons.Default.WbTwilight
                            "afternoon" -> Icons.Default.WbSunny
                            else -> Icons.Default.NightsStay
                        },
                        null,
                        tint = AppColors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(slotPartLabel(part), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textSecondary)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    visible.forEach { time ->
                        val slot = TradeSlot(day = day.toString(), time = time, part = part)
                        val on = chosen.any { it.day == slot.day && it.time == time }
                        val full = !on && chosen.size >= 3
                        val background by animateColorAsState(if (on) AppColors.blue else AppColors.card, tween(AppMotion.current.state), label = "time")
                        Text(
                            time,
                            fontSize = 13.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                on -> AppColors.onAccent
                                full -> AppColors.textMuted.copy(alpha = 0.4f)
                                else -> AppColors.textPrimary
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .width(60.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(background)
                                .pressScale(enabled = !full, scaleDown = 0.92f) { onToggle(slot) }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Un interruttore piccolo per l'ordine dei luoghi. */
@Composable
private fun SortChip(text: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) AppColors.blue else AppColors.card, tween(AppMotion.current.state), label = "sortChip")
    val content = if (selected) AppColors.onAccent else AppColors.textPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .pressScale(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = content)
    }
}

/** Un luogo da scegliere, con il segno di spunta quando e' quello scelto. */
@Composable
private fun SpotOption(spot: TradeSpot, selected: Boolean, fromMeKm: Double? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val border by animateColorAsState(if (selected) AppColors.green else Color.Transparent, tween(AppMotion.current.state), label = "spotBorder")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.card)
            .border(1.5.dp, border, shape)
            .pressScale(scaleDown = 0.98f, onClick = onClick)
            .padding(12.dp)
    ) {
        Box(Modifier.weight(1f)) { SpotSummary(spot) }
        Column(horizontalAlignment = Alignment.End) {
            // Da me, se il telefono sa dove sono; altrimenti dal punto a meta' strada.
            if (fromMeKm != null) {
                Text(AppLocale.tradeRadarKmFromYou(fromMeKm), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textSecondary)
            } else {
                spot.distanceKm?.let { Text(AppLocale.tradeRadarKmAway(it), fontSize = 11.sp, color = AppColors.textMuted) }
            }
            if (spot.pending == true) InfoPill(AppLocale.tradeRadarPendingSpot, AppColors.orange)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            null,
            tint = if (selected) AppColors.green else AppColors.textMuted,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun DayChip(date: java.time.LocalDate, today: java.time.LocalDate, selected: Boolean, slotsOnDay: Int, onClick: () -> Unit) {
    val locale = if (AppLocale.isItalian) Locale.ITALIAN else Locale.ENGLISH
    val background by animateColorAsState(if (selected) AppColors.blue else AppColors.card, tween(AppMotion.current.state), label = "day")
    val content = if (selected) AppColors.onAccent else AppColors.textPrimary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .pressScale(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            if (date == today) AppLocale.tradeRadarToday else date.format(java.time.format.DateTimeFormatter.ofPattern("EEE", locale)),
            fontSize = 11.sp,
            color = content.copy(alpha = 0.8f)
        )
        Text(date.dayOfMonth.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = content)
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (slotsOnDay > 0) (if (selected) AppColors.onAccent else AppColors.blue) else Color.Transparent)
        )
    }
}

// ── Chiusura e feedback (fase 2c) ───────────────────────────────────────────

private val GoodTags = listOf("punctual", "as_described", "kind")
private val BadTags = listOf("late", "worse_condition", "different_card", "rude")
private val SpotVoteTags = listOf("tournaments", "comics", "card_shop")

private fun ratingTagLabel(tag: String): String = when (tag) {
    "punctual" -> AppLocale.tradeRadarTagPunctual
    "as_described" -> AppLocale.tradeRadarTagAsDescribed
    "kind" -> AppLocale.tradeRadarTagKind
    "late" -> AppLocale.tradeRadarTagLate
    "worse_condition" -> AppLocale.tradeRadarTagWorseCondition
    "different_card" -> AppLocale.tradeRadarTagDifferentCard
    else -> AppLocale.tradeRadarTagRude
}

private fun spotBadgeLabel(tag: String): String = when (tag) {
    "tournaments" -> AppLocale.tradeRadarBadgeTournaments
    "comics" -> AppLocale.tradeRadarKindComics
    else -> AppLocale.tradeRadarKindCardShop
}

private fun moodEmoji(mood: String?): String = when (mood) {
    "good" -> "😊"
    "ok" -> "😐"
    "bad" -> "😞"
    else -> ""
}

/**
 * La reputazione in una riga: "Nuovo su TradeRadar", oppure "5 scambi · 😊 100%
 * · Puntuale ×3". La percentuale e' sui 😊 e 😞: i 😐 contano come scambi ma
 * non la abbassano.
 */
private fun reputationText(reputation: TradeReputation?, tradesDone: Int): String {
    if (tradesDone == 0 && reputation == null) return AppLocale.tradeRadarNewMember
    val good = reputation?.good ?: 0
    val bad = reputation?.bad ?: 0
    return listOfNotNull(
        AppLocale.tradeRadarTradesDone(tradesDone),
        (good + bad).takeIf { it > 0 }?.let { "😊 ${(good * 100) / it}%" },
        reputation?.topTags?.firstOrNull()?.let { top -> "${ratingTagLabel(top.tag.orEmpty())} ×${top.count ?: 0}" }
    ).joinToString(" · ")
}

/**
 * Sotto l'appuntamento fissato: "Scambio fatto" dal giorno dell'incontro,
 * "Non si e' presentato" dopo l'ora. Se l'altro ha gia' confermato lo si
 * dice, e se ho confermato io si aspetta lui.
 */
/** L'ora dell'appuntamento confermato e' passata: resta solo chiudere (fatto o non presentato). */
private fun meetingTimePassed(proposal: TradeProposal): Boolean {
    if (proposal.status != "scheduled") return false
    val slot = proposal.meeting?.slot ?: return false
    val today = java.time.LocalDate.now().toString()
    val now = java.time.LocalTime.now().toString().take(5)
    return today > slot.day.orEmpty() || (today == slot.day && now >= (slot.time ?: slotPartTime(slot.part)))
}

@Composable
private fun ClosingControls(proposal: TradeProposal, acting: Boolean, onDone: () -> Unit, onNoShow: () -> Unit) {
    val slot = proposal.meeting?.slot ?: return
    val nickname = proposal.counterpart?.nickname.orEmpty()
    val today = java.time.LocalDate.now().toString()
    val now = java.time.LocalTime.now().toString().take(5)
    val isDay = today >= slot.day
    val afterTime = today > slot.day || (today == slot.day && now >= (slot.time ?: slotPartTime(slot.part)))
    var askNoShow by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            !isDay -> Text(AppLocale.tradeRadarDoneFromDay, fontSize = 12.sp, color = AppColors.textMuted)
            proposal.doneByMe == true -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = AppColors.green, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(AppLocale.tradeRadarDoneWaiting(nickname), fontSize = 12.sp, color = AppColors.textSecondary)
            }
            else -> {
                if (proposal.doneByOther == true) {
                    Text(AppLocale.tradeRadarOtherDone(nickname), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.orange)
                }
                Button(
                    onClick = onDone,
                    enabled = !acting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.green),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (acting) CircularProgressIndicator(Modifier.size(18.dp), color = AppColors.onAccent, strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Default.Verified, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(AppLocale.tradeRadarMarkDone, fontWeight = FontWeight.Bold)
                    }
                }
                if (afterTime && proposal.doneByOther != true) {
                    TextButton(onClick = { askNoShow = true }, enabled = !acting, modifier = Modifier.align(Alignment.End)) {
                        Text(AppLocale.tradeRadarNoShow(nickname), color = AppColors.red)
                    }
                }
            }
        }
    }
    if (askNoShow) {
        AlertDialog(
            onDismissRequest = { askNoShow = false },
            title = { Text(AppLocale.tradeRadarNoShow(nickname)) },
            text = { Text(AppLocale.tradeRadarNoShowText) },
            confirmButton = {
                TextButton(onClick = { askNoShow = false; onNoShow() }) { Text(AppLocale.confirm, color = AppColors.red) }
            },
            dismissButton = { TextButton(onClick = { askNoShow = false }) { Text(AppLocale.cancel) } }
        )
    }
}

/**
 * Uno scambio chiuso per assenza: chi l'ha segnalata, chi e' stato segnalato
 * e, per 48 ore, il suo "Io c'ero". Contestata, lo vedono tutti e due.
 */
@Composable
private fun NoShowSection(proposal: TradeProposal, acting: Boolean, onDispute: () -> Unit) {
    val nickname = proposal.counterpart?.nickname.orEmpty()
    val byMe = proposal.closedByMe == true
    var askDispute by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (byMe) AppLocale.tradeRadarNoShowByMe(nickname) else AppLocale.tradeRadarNoShowByOther,
            fontSize = 12.sp,
            color = AppColors.textMuted
        )
        if (proposal.noShowDisputed == true) {
            Text(
                if (byMe) AppLocale.tradeRadarNoShowDisputedByOther(nickname) else AppLocale.tradeRadarNoShowDisputedByMe,
                fontSize = 12.sp,
                color = AppColors.textSecondary
            )
        } else if (proposal.canDispute == true) {
            val hoursLeft = proposal.disputeUntil
                ?.let { ((it - System.currentTimeMillis() + 3_599_999L) / 3_600_000L).coerceAtLeast(1L) }
            if (hoursLeft != null) Text(AppLocale.tradeRadarDisputeHoursLeft(hoursLeft), fontSize = 12.sp, color = AppColors.orange)
            OutlinedButton(
                onClick = { askDispute = true },
                enabled = !acting,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (acting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(AppLocale.tradeRadarDispute, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (askDispute) {
        AlertDialog(
            onDismissRequest = { askDispute = false },
            title = { Text(AppLocale.tradeRadarDispute) },
            text = { Text(AppLocale.tradeRadarDisputeText(nickname)) },
            confirmButton = {
                TextButton(onClick = { askDispute = false; onDispute() }) { Text(AppLocale.confirm) }
            },
            dismissButton = { TextButton(onClick = { askDispute = false }) { Text(AppLocale.cancel) } }
        )
    }
}

/** Uno scambio chiuso: la collezione da aggiornare, il voto da dare, e quello dell'altro quando si vede. */
@Composable
private fun DoneSection(proposal: TradeProposal, needsCollection: Boolean, onCollection: () -> Unit, onFeedback: () -> Unit) {
    val nickname = proposal.counterpart?.nickname.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.green.copy(alpha = 0.12f)).padding(10.dp)
        ) {
            Text("🎉", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(AppLocale.tradeRadarTradeCompleted(nickname), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary)
        }
        if (proposal.autoClosed == true) {
            Text(
                if (proposal.doneByMe == true) AppLocale.tradeRadarAutoClosedMarked(nickname) else AppLocale.tradeRadarAutoClosedSilent(nickname),
                fontSize = 12.sp,
                color = AppColors.textMuted
            )
        }
        if (needsCollection) {
            Button(
                onClick = onCollection,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.orange),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(AppLocale.tradeRadarUpdateCollection, fontWeight = FontWeight.Bold)
            }
        }
        val mine = proposal.myRating
        if (mine == null) {
            OutlinedButton(onClick = onFeedback, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(AppLocale.tradeRadarHowDidItGo(nickname), fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(
                AppLocale.tradeRadarYourRating("${moodEmoji(mine.mood)} ${mine.tags.orEmpty().joinToString(", ") { ratingTagLabel(it) }}".trim()),
                fontSize = 12.sp,
                color = AppColors.textSecondary
            )
            val theirs = proposal.theirRating
            Text(
                if (theirs != null) AppLocale.tradeRadarTheirRating(nickname, "${moodEmoji(theirs.mood)} ${theirs.tags.orEmpty().joinToString(", ") { ratingTagLabel(it) }}".trim())
                else AppLocale.tradeRadarTheirRatingHidden(nickname),
                fontSize = 12.sp,
                color = if (theirs != null) AppColors.textSecondary else AppColors.textMuted
            )
        }
    }
}

/**
 * Il riepilogo della collezione, carta per carta: gia' tutto spuntato, si
 * toglie la spunta a cio' che non si vuole toccare. Le carte da dare che in
 * collezione non ci sono piu' restano senza spunta, con il motivo.
 */
@Composable
private fun ClosingDialog(viewModel: TradeRadarViewModel, closing: TradeRadarViewModel.Closing) {
    AlertDialog(
        onDismissRequest = { if (!closing.applying) viewModel.closeClosing() },
        title = { Text(AppLocale.tradeRadarUpdateCollection) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(AppLocale.tradeRadarClosingText(closing.nickname), fontSize = 13.sp, color = AppColors.textSecondary)
                if (closing.loading) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                closing.lines.forEachIndexed { index, line ->
                    val color = if (line.giving) AppColors.red else AppColors.green
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(innerColor())
                            .clickable(enabled = !line.missing) { viewModel.toggleClosingLine(index) }
                            .padding(end = 10.dp)
                    ) {
                        Checkbox(checked = line.checked, onCheckedChange = { viewModel.toggleClosingLine(index) }, enabled = !line.missing)
                        Box(Modifier.width(30.dp).height(42.dp).clip(RoundedCornerShape(3.dp))) {
                            CardImageSkeleton()
                            AsyncImage(
                                model = TradeCardKey.imageUrl(line.item.key.orEmpty(), PokeVaultApiClient.imageBaseUrl),
                                contentDescription = line.item.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${if (line.giving) "−" else "+"}${line.item.qty ?: 1} ${line.item.name ?: TradeCardKey.label(line.item.key.orEmpty())}",
                                fontWeight = FontWeight.SemiBold,
                                color = color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                listOfNotNull(line.item.variant, line.item.condition).filter { it.isNotBlank() }.joinToString(" · "),
                                fontSize = 11.sp,
                                color = AppColors.textMuted
                            )
                            when {
                                line.missing -> Text(AppLocale.tradeRadarNotInCollection, fontSize = 11.sp, color = AppColors.orange)
                                line.inWishlist -> Text(AppLocale.tradeRadarLeavesWishlist, fontSize = 11.sp, color = AppColors.blue)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.applyClosing() }, enabled = !closing.loading && !closing.applying) {
                if (closing.applying) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(AppLocale.confirm, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.closeClosing() }, enabled = !closing.applying) { Text(AppLocale.tradeRadarLater) }
        }
    )
}

/**
 * Il voto alla cieca: tre faccine grandi, poi i chip che vanno con la faccina
 * scelta, e una domanda veloce sul luogo. Niente testo libero.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackDialog(viewModel: TradeRadarViewModel, feedback: TradeRadarViewModel.Feedback) {
    AlertDialog(
        onDismissRequest = { if (!feedback.sending) viewModel.closeFeedback() },
        title = { Text(AppLocale.tradeRadarHowDidItGo(feedback.nickname)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(AppLocale.tradeRadarBlindNote(feedback.nickname), fontSize = 12.sp, color = AppColors.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "good" to AppLocale.tradeRadarMoodGood,
                        "ok" to AppLocale.tradeRadarMoodOk,
                        "bad" to AppLocale.tradeRadarMoodBad
                    ).forEach { (mood, label) ->
                        val selected = feedback.mood == mood
                        val color = when (mood) { "good" -> AppColors.green; "ok" -> AppColors.orange; else -> AppColors.red }
                        val scale by animateFloatAsState(if (selected) 1.08f else 1f, AppMotion.pressSpring(), label = "mood")
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) color.copy(alpha = 0.18f) else innerColor())
                                .border(1.5.dp, if (selected) color else Color.Transparent, RoundedCornerShape(16.dp))
                                .clickable { viewModel.setMood(mood) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(moodEmoji(mood), fontSize = 30.sp)
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary, textAlign = TextAlign.Center)
                        }
                    }
                }
                feedback.mood?.let { mood ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (if (mood == "good") GoodTags else BadTags).forEach { tag ->
                            SelectChip(ratingTagLabel(tag), tag in feedback.tags) { viewModel.toggleFeedbackTag(tag) }
                        }
                    }
                }
                if (feedback.askSpot && feedback.spotName != null) {
                    HorizontalDivider(color = AppColors.textMuted.copy(alpha = 0.2f))
                    Text(AppLocale.tradeRadarSpotQuestion(feedback.spotName), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SpotVoteTags.forEach { tag ->
                            SelectChip(spotBadgeLabel(tag), tag in feedback.spotTags) { viewModel.toggleSpotTag(tag) }
                        }
                    }
                    Text(AppLocale.tradeRadarSpotQuestionHint, fontSize = 11.sp, color = AppColors.textMuted)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.sendFeedback() }, enabled = feedback.mood != null && !feedback.sending) {
                if (feedback.sending) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(AppLocale.tradeRadarReportSend, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.closeFeedback() }, enabled = !feedback.sending) { Text(AppLocale.tradeRadarLater) }
        }
    )
}

@Composable
private fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) AppColors.blue else innerColor(), tween(AppMotion.current.state), label = "selectChip")
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) AppColors.onAccent else AppColors.textPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

// ── Classifica e livelli (fase 2e) ──────────────────────────────────────────

private data class TierStyle(val emoji: String, val label: String, val color: Color, val trades: Int)

@Composable
private fun tierStyle(tier: String?): TierStyle? = when (tier) {
    "bronze" -> TierStyle("🥉", AppLocale.tradeRadarTierBronze, Color(0xFFCD7F32), 5)
    "silver" -> TierStyle("🥈", AppLocale.tradeRadarTierSilver, Color(0xFF9EA3A8), 15)
    "gold" -> TierStyle("🥇", AppLocale.tradeRadarTierGold, AppColors.gold, 40)
    "platinum" -> TierStyle("💎", AppLocale.tradeRadarTierPlatinum, Color(0xFF4FC3F7), 100)
    else -> null
}

/** Il livello accanto a un nickname: niente se non c'e'. */
@Composable
private fun TierBadge(tier: String?, compact: Boolean = false) {
    val style = tierStyle(tier) ?: return
    Text(
        if (compact) style.emoji else "${style.emoji} ${style.label}",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = style.color,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(style.color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

/**
 * La classifica dei piu' affidabili, a schermo intero: nella tua zona o in
 * Italia. "Come si sale" sta dietro la i in alto. Poi la tua situazione (con
 * il progresso verso il livello successivo), il podio dei primi tre, le
 * altre posizioni a cascata; toccando una persona si apre il suo mini
 * profilo, e se sei piu' giu' un tasto ti porta alla tua riga.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaderboardDialog(
    viewModel: TradeRadarViewModel,
    board: TradeRadarViewModel.Leaderboard,
    profile: TradeProfilePayload,
    onPremiumRequired: () -> Unit
) {
    val payload = board.payload
    val isPremium by PremiumManager.getInstance().isPremium.collectAsStateWithLifecycle()
    var pickingAvatar by rememberSaveable { mutableStateOf(false) }
    val spriteLoader = rememberSpriteLoader()
    val me = payload?.me
    val entries = payload?.entries.orEmpty()
    // "Come si sale" e' a portata di mano ma chiuso: si apre dalla i in alto.
    var showRules by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<TradeLeaderboardEntry?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // La cascata parte una volta, quando arrivano le righe.
    var cascade by remember { mutableStateOf(false) }
    LaunchedEffect(entries.isNotEmpty()) { if (entries.isNotEmpty()) cascade = true }

    // Indici delle righe nella lista: rules, scope, me, podio, poi dal 4° in giu'.
    val podium = entries.take(3)
    val rest = entries.drop(3)
    val firstRowIndex = 2 + (if (me != null) 1 else 0) + (if (podium.isNotEmpty()) 1 else 0)
    val myIndex = entries.indexOfFirst { it.isMe == true }
    val myListIndex = when {
        myIndex < 0 -> null
        myIndex < 3 -> firstRowIndex - 1
        else -> firstRowIndex + (myIndex - 3)
    }
    val myRowVisible by remember(myListIndex) {
        derivedStateOf { myListIndex == null || listState.layoutInfo.visibleItemsInfo.any { it.index == myListIndex } }
    }

    Dialog(onDismissRequest = { viewModel.closeLeaderboard() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = AppColors.background,
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = { Text(AppLocale.tradeRadarLeaderboard, fontWeight = FontWeight.Bold, color = AppColors.textPrimary) },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.closeLeaderboard() }) {
                                Icon(Icons.Default.Close, AppLocale.tradeRadarClose, tint = AppColors.textPrimary)
                            }
                        },
                        actions = {
                            IconButton(onClick = { showRules = !showRules }) {
                                Icon(
                                    if (showRules) Icons.Filled.Info else Icons.Outlined.Info,
                                    AppLocale.tradeRadarTiersTitle,
                                    tint = if (showRules) AppColors.gold else AppColors.textSecondary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
                    )
                },
                floatingActionButton = {
                    AnimatedVisibility(
                        visible = !myRowVisible && myListIndex != null,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it }
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = { myListIndex?.let { scope.launch { listState.animateScrollToItem(it) } } },
                            containerColor = AppColors.blue,
                            contentColor = AppColors.onAccent,
                            icon = { Icon(Icons.Default.MyLocation, null) },
                            text = { Text(AppLocale.tradeRadarGoToMe, fontWeight = FontWeight.Bold) }
                        )
                    }
                },
                floatingActionButtonPosition = FabPosition.Center
            ) { padding ->
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "rules") {
                        AnimatedVisibility(
                            visible = showRules,
                            enter = expandVertically(tween(AppMotion.current.content)) + fadeIn(tween(AppMotion.current.content)),
                            exit = shrinkVertically(tween(AppMotion.current.state)) + fadeOut(tween(AppMotion.current.state))
                        ) {
                            TiersExplained()
                        }
                    }
                    item(key = "scope") {
                        SegmentedTabs(
                            selected = if (board.scope == "italy") 1 else 0,
                            labels = listOf(AppLocale.tradeRadarLeaderboardZone, AppLocale.tradeRadarLeaderboardItaly),
                            badges = listOf(0, 0),
                            onSelect = { viewModel.setLeaderboardScope(if (it == 1) "italy" else "zone") },
                            modifier = Modifier
                        )
                    }
                    if (me != null) {
                        item(key = "me") {
                            MyStanding(
                                me,
                                total = payload.total ?: 0,
                                onOptIn = { viewModel.setLeaderboardOptIn(it) },
                                nickname = profile.nickname.orEmpty(),
                                avatar = profile.avatar,
                                onPickAvatar = { pickingAvatar = true }
                            )
                        }
                    }
                    when {
                        board.loading && payload == null -> item(key = "loading") {
                            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                RadarScope(blips = emptyList(), scanning = true, modifier = Modifier.size(90.dp))
                            }
                        }
                        entries.isEmpty() -> item(key = "empty") {
                            EmptyState(text = AppLocale.tradeRadarLeaderboardEmpty, radar = false)
                        }
                        else -> {
                            item(key = "podium|${board.scope}") { Podium(podium, spriteLoader, onSelect = { selected = it }) }
                            itemsIndexed(rest, key = { _, entry -> "rank|${entry.rank}|${entry.nickname}" }) { index, entry ->
                                CascadeIn(index = index, visible = cascade, modifier = Modifier.animateItem()) {
                                    LeaderboardRow(entry, onClick = { selected = entry })
                                }
                            }
                        }
                    }
                }
            }
            // La festa si disegna qui dentro: la classifica e' una finestra sua, sopra il resto.
            CelebrationOverlay(viewModel)
        }
    }
    selected?.let { entry ->
        MiniProfileDialog(
            entry,
            onDismiss = { selected = null },
            onSafety = if (entry.isMe != true && entry.id != null) {
                { report -> selected = null; viewModel.openSafety(entry.id, entry.nickname, null, report) }
            } else null
        )
    }
    if (pickingAvatar) {
        AvatarPickerDialog(
            current = profile.avatar,
            currentAnimated = profile.avatarAnimated == true,
            isPremium = isPremium,
            onPick = { avatar, animated ->
                pickingAvatar = false
                viewModel.setAvatar(avatar, animated)
            },
            onPremiumRequired = {
                pickingAvatar = false
                onPremiumRequired()
            },
            onDismiss = { pickingAvatar = false }
        )
    }
}

/** Le soglie dei livelli, in ordine. */
private val TierThresholds = listOf("bronze" to 5, "silver" to 15, "gold" to 40, "platinum" to 100)

/**
 * Verso il livello successivo: "🥉 Bronzo → 🥈 Argento · 9/15". Ricorda anche
 * la condizione del 90% di 😊 quando non e' rispettata.
 */
@Composable
private fun TierProgress(trades: Int, tier: String?, positivePct: Int?) {
    val next = TierThresholds.firstOrNull { (_, need) -> trades < need }
    val previousNeed = TierThresholds.lastOrNull { (_, need) -> trades >= need }?.second ?: 0
    val progress by animateFloatAsState(
        if (next == null) 1f else ((trades - previousNeed).toFloat() / (next.second - previousNeed)).coerceIn(0f, 1f),
        tween(AppMotion.current.bar, easing = AppMotion.standardEasing),
        label = "tierProgress"
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val nextStyle = next?.let { tierStyle(it.first) }
            val currentStyle = tierStyle(tier)
            Text(
                when {
                    next == null -> AppLocale.tradeRadarTierMax
                    currentStyle == null -> AppLocale.tradeRadarTowards("${nextStyle?.emoji} ${nextStyle?.label}")
                    else -> "${currentStyle.emoji} ${currentStyle.label} → ${nextStyle?.emoji} ${nextStyle?.label}"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (next != null) Text("$trades/${next.second}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.gold)
        }
        LinearProgressIndicator(
            progress = { progress },
            color = AppColors.gold,
            trackColor = innerColor(),
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
        if (positivePct != null && positivePct < 90) {
            Text(AppLocale.tradeRadarNeedPositive(positivePct), fontSize = 11.sp, color = AppColors.orange)
        }
    }
}

/** La mia situazione: progresso di livello, e in classifica (posizione), fuori per scelta, da invitare, o cosa manca. */
@Composable
private fun MyStanding(
    me: TradeLeaderboardMe,
    total: Int,
    onOptIn: (Boolean) -> Unit,
    nickname: String,
    avatar: Int?,
    onPickAvatar: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(AppColors.gold.copy(alpha = 0.18f), AppColors.card)))
            .background(AppColors.card.copy(alpha = 0.45f))
            .border(1.dp, AppColors.gold.copy(alpha = 0.4f), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(AppLocale.tradeRadarYouInLeaderboard, fontWeight = FontWeight.Bold, color = AppColors.textPrimary, modifier = Modifier.weight(1f))
            TierBadge(me.tier)
        }
        Text(
            listOfNotNull(
                AppLocale.tradeRadarValidTrades(me.trades ?: 0),
                AppLocale.tradeRadarPartners(me.partners ?: 0),
                me.positivePct?.let { "😊 $it%" }
            ).joinToString(" · "),
            fontSize = 13.sp,
            color = AppColors.textSecondary
        )
        TierProgress(me.trades ?: 0, me.tier, me.positivePct)
        when {
            me.eligible != true -> {
                Text(AppLocale.tradeRadarMissingForLeaderboard(me.missingTrades ?: 0, me.missingPartners ?: 0), fontSize = 13.sp, color = AppColors.textPrimary)
            }
            me.optIn == null -> {
                Text(AppLocale.tradeRadarJoinQuestion, fontSize = 13.sp, color = AppColors.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onOptIn(false) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Text(AppLocale.tradeRadarJoinNo)
                    }
                    Button(
                        onClick = { onOptIn(true) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.green),
                        modifier = Modifier.weight(1f)
                    ) { Text(AppLocale.tradeRadarJoinYes, fontWeight = FontWeight.Bold) }
                }
            }
            me.optIn == false -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(AppLocale.tradeRadarNotJoined, fontSize = 13.sp, color = AppColors.textSecondary, modifier = Modifier.weight(1f))
                TextButton(onClick = { onOptIn(true) }) { Text(AppLocale.tradeRadarJoinYes) }
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    me.rank?.let { AppLocale.tradeRadarYourRank(it, total) } ?: AppLocale.tradeRadarNotInThisZone,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onOptIn(false) }) { Text(AppLocale.tradeRadarLeaveLeaderboard, color = AppColors.textMuted, fontSize = 12.sp) }
            }
        }
        AvatarChoiceRow(nickname, avatar, onPickAvatar)
    }
}

/**
 * Il podio: secondo, primo, terzo, con i blocchi che salgono uno dopo
 * l'altro. Con le animazioni di sistema spente sono gia' alti.
 */
@Composable
private fun Podium(top: List<TradeLeaderboardEntry>, loader: ImageLoader, onSelect: (TradeLeaderboardEntry) -> Unit) {
    val motion = AppMotion.current
    // Ordine sul podio: 2°, 1°, 3°.
    val order = listOfNotNull(top.getOrNull(1), top.getOrNull(0), top.getOrNull(2))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        order.forEach { entry ->
            val rank = entry.rank ?: 0
            val target = when (rank) { 1 -> 96.dp; 2 -> 72.dp; else -> 56.dp }
            val color = when (rank) { 1 -> AppColors.gold; 2 -> Color(0xFF9EA3A8); else -> Color(0xFFCD7F32) }
            val grow = remember { Animatable(if (motion.enabled) 0f else 1f) }
            LaunchedEffect(entry.nickname) {
                if (motion.enabled) {
                    delay(motion.cascade(3 - rank.coerceIn(1, 3)).toLong())
                    grow.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f))
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onSelect(entry) }
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    PodiumFace(entry, rank, loader)
                    Text(if (rank == 1) "👑" else "", fontSize = 16.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.nickname.orEmpty().removePrefix("Test "),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (entry.isMe == true) AppColors.blue else AppColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(AppLocale.tradeRadarTradesDone(entry.trades ?: 0), entry.positivePct?.let { "$it%" }).joinToString(" · "),
                    fontSize = 11.sp,
                    color = AppColors.textSecondary,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(target * grow.value)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.35f))))
                ) {
                    Text(
                        when (rank) { 1 -> "🥇"; 2 -> "🥈"; else -> "🥉" },
                        fontSize = 26.sp,
                        modifier = Modifier.padding(top = 6.dp).graphicsLayer { alpha = grow.value }
                    )
                }
            }
        }
    }
}

/**
 * Sul podio, sopra il nome: il Pokemon scelto (animato se Premium), o
 * l'iniziale se non l'ha scelto o se lo sprite non arriva.
 */
@Composable
private fun PodiumFace(entry: TradeLeaderboardEntry, rank: Int, loader: ImageLoader) {
    val initial: @Composable () -> Unit = { Avatar(entry.nickname.orEmpty(), size = if (rank == 1) 56.dp else 46.dp) }
    val id = entry.avatar
    if (id == null) {
        initial()
        return
    }
    PokemonSprite(id, animated = entry.avatarAnimated == true, size = if (rank == 1) 76.dp else 62.dp, loader = loader, fallback = initial)
}

/** In fondo alla mia situazione: il Pokemon che avrei sul podio, e il tasto per cambiarlo. */
@Composable
private fun AvatarChoiceRow(nickname: String, avatar: Int?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.card.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        if (avatar != null) {
            PokemonSprite(avatar, animated = false, size = 44.dp, fallback = { Avatar(nickname, size = 36.dp) })
        } else {
            Avatar(nickname, size = 36.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(AppLocale.tradeRadarAvatarTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary)
            Text(AppLocale.tradeRadarAvatarHint, fontSize = 11.sp, color = AppColors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (avatar == null) AppLocale.tradeRadarAvatarChoose else AppLocale.tradeRadarAvatarChange,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.blue
        )
    }
}

@Composable
private fun LeaderboardRow(entry: TradeLeaderboardEntry, onClick: () -> Unit) {
    val mine = entry.isMe == true
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (mine) AppColors.blue.copy(alpha = 0.12f) else AppColors.card)
            .then(if (mine) Modifier.border(1.dp, AppColors.blue.copy(alpha = 0.5f), shape) else Modifier)
            .pressScale(scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            "${entry.rank ?: 0}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp)
        )
        Spacer(Modifier.width(6.dp))
        Avatar(entry.nickname.orEmpty(), size = 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (mine) AppLocale.tradeRadarYouName(entry.nickname.orEmpty()) else entry.nickname.orEmpty(),
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                TierBadge(entry.tier, compact = true)
            }
            Text(
                listOfNotNull(AppLocale.tradeRadarTradesDone(entry.trades ?: 0), entry.positivePct?.let { "😊 $it%" }).joinToString(" · "),
                fontSize = 12.sp,
                color = AppColors.textSecondary
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppColors.textMuted)
    }
}

/**
 * Il mini profilo di chi e' in classifica: livello, scambi, persone, voti e
 * chip piu' ricevuti, da quando c'e'. Niente zona ne' carte.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MiniProfileDialog(entry: TradeLeaderboardEntry, onDismiss: () -> Unit, onSafety: ((report: Boolean) -> Unit)? = null) {
    val locale = if (AppLocale.isItalian) Locale.ITALIAN else Locale.ENGLISH
    val since = entry.memberSince?.let {
        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(AppLocale.tradeRadarClose) } },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Avatar(entry.nickname.orEmpty(), size = 64.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.nickname.orEmpty(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
                    TierBadge(entry.tier)
                }
                Text(
                    listOfNotNull("#${entry.rank}", since?.let { AppLocale.tradeRadarMemberSince(it) }).joinToString(" · "),
                    fontSize = 12.sp,
                    color = AppColors.textMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "${entry.trades ?: 0}" to AppLocale.tradeRadarStatTrades,
                        "${entry.partners ?: 0}" to AppLocale.tradeRadarStatPeople,
                        (entry.positivePct?.let { "$it%" } ?: "—") to "😊"
                    ).forEach { (value, label) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(innerColor()).padding(vertical = 8.dp)
                        ) {
                            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
                            Text(label, fontSize = 11.sp, color = AppColors.textSecondary)
                        }
                    }
                }
                Text(
                    "😊 ${entry.good ?: 0}   😐 ${entry.ok ?: 0}   😞 ${entry.bad ?: 0}",
                    fontSize = 13.sp,
                    color = AppColors.textSecondary
                )
                val tags = entry.topTags.orEmpty()
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { InfoPill("${ratingTagLabel(it.tag.orEmpty())} ×${it.count ?: 0}", AppColors.green) }
                    }
                }
            }
        }
    )
}

/**
 * I coriandoli: una sola animazione muove tutte le particelle, che partono
 * dal centro in alto e ricadono. Con le animazioni di sistema spente non c'e'.
 */
@Composable
private fun TradeConfetti(modifier: Modifier = Modifier) {
    val motion = AppMotion.current
    if (!motion.enabled) return
    val progress = remember { Animatable(0f) }
    val colors = listOf(AppColors.gold, AppColors.blue, AppColors.green, AppColors.purple, AppColors.orange, AppColors.red)
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(motion.confetti * 2, easing = LinearEasing)) }
    Canvas(modifier.fillMaxSize()) {
        val particles = 40
        val origin = Offset(size.width / 2f, size.height * 0.25f)
        repeat(particles) { index ->
            val delayFraction = (index % 5) * 0.04f
            val t = ((progress.value - delayFraction) / (1f - delayFraction)).coerceIn(0f, 1f)
            if (t <= 0f) return@repeat
            val angle = Math.toRadians(-160.0 + 140.0 * ((index * 37) % particles) / particles)
            val speed = size.width * (0.35f + 0.25f * ((index * 13) % 7) / 7f)
            // Spinta verso l'esterno e poi gravita' che le riporta giu'.
            val x = origin.x + (cos(angle) * speed * t).toFloat()
            val y = origin.y + (sin(angle) * speed * t).toFloat() + size.height * 0.9f * t * t
            val side = (6 + (index % 3) * 2).dp.toPx()
            rotate(degrees = 720f * t + index * 30f, pivot = Offset(x, y)) {
                drawRect(
                    color = colors[index % colors.size].copy(alpha = (1f - t).coerceIn(0f, 1f)),
                    topLeft = Offset(x - side / 2, y - side / 4),
                    size = androidx.compose.ui.geometry.Size(side, side / 2)
                )
            }
        }
    }
}

/**
 * La festa: coriandoli e un messaggio quando si entra in classifica o si sale
 * di livello. Sparisce da sola dopo qualche secondo, o al tocco.
 */
@Composable
private fun CelebrationOverlay(viewModel: TradeRadarViewModel) {
    val celebration = viewModel.celebration ?: return
    LaunchedEffect(celebration) {
        delay(3_000)
        viewModel.consumeCelebration()
    }
    val message = when (celebration) {
        TradeRadarViewModel.Celebration.Joined -> AppLocale.tradeRadarCelebrateJoined
        is TradeRadarViewModel.Celebration.TierUp -> {
            val style = tierStyle(celebration.tier)
            AppLocale.tradeRadarCelebrateTier("${style?.emoji.orEmpty()} ${style?.label.orEmpty()}".trim())
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { viewModel.consumeCelebration() }
    ) {
        TradeConfetti()
        val scale = remember { Animatable(0.6f) }
        LaunchedEffect(celebration) { scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f)) }
        Text(
            message,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = AppColors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.card)
                .border(1.5.dp, AppColors.gold, RoundedCornerShape(20.dp))
                .padding(horizontal = 22.dp, vertical = 14.dp)
        )
    }
}

/** Come si sale: i livelli, e le regole che impediscono di gonfiarsi a vicenda. */
@Composable
private fun TiersExplained() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppColors.card).padding(14.dp)
    ) {
        Text(AppLocale.tradeRadarTiersTitle, fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
        listOf("bronze", "silver", "gold", "platinum").forEach { tier ->
            val style = tierStyle(tier) ?: return@forEach
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(style.emoji, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(style.label, fontWeight = FontWeight.SemiBold, color = style.color, modifier = Modifier.width(80.dp))
                Text(AppLocale.tradeRadarTierRule(style.trades), fontSize = 12.sp, color = AppColors.textSecondary)
            }
        }
        Text(AppLocale.tradeRadarTiersRules, fontSize = 12.sp, color = AppColors.textMuted)
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
                    reserved = viewModel.reserved[duplicate.id] ?: 0,
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
                reserved = viewModel.reserved[single.id] ?: 0,
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
    onNotify: (Boolean) -> Unit = {},
    /** Copie promesse in un accordo: restano in lista, ma gli altri non le vedono. */
    reserved: Int = 0
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
                    if (reserved > 0) InfoPill(AppLocale.tradeRadarReserved(reserved), AppColors.orange)
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
internal fun Avatar(nickname: String, size: Dp, pulse: Boolean = false) {
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
    Problem.TOO_EARLY -> AppLocale.tradeRadarTooEarly
    Problem.ALREADY_REPORTED -> AppLocale.tradeRadarAlreadyReported
    Problem.TOO_MANY_REPORTS -> AppLocale.tradeRadarTooManyReports
    Problem.SUSPENDED -> AppLocale.tradeRadarSuspendedAction
    Problem.MEETING_PASSED -> AppLocale.tradeRadarMeetingPassed
    Problem.DISPUTE_TOO_LATE -> AppLocale.tradeRadarDisputeTooLate
    Problem.NOT_SCHEDULED -> AppLocale.tradeRadarNotScheduled
}

private fun infoText(info: TradeRadarViewModel.Info): String = when (info) {
    TradeRadarViewModel.Info.PROPOSAL_SENT -> AppLocale.tradeRadarInfoSent
    TradeRadarViewModel.Info.COUNTER_SENT -> AppLocale.tradeRadarInfoCounterSent
    TradeRadarViewModel.Info.ACCEPTED -> AppLocale.tradeRadarInfoAccepted
    TradeRadarViewModel.Info.DECLINED -> AppLocale.tradeRadarInfoDeclined
    TradeRadarViewModel.Info.CANCELLED -> AppLocale.tradeRadarInfoCancelled
    TradeRadarViewModel.Info.MEETING_SENT -> AppLocale.tradeRadarInfoMeetingSent
    TradeRadarViewModel.Info.MEETING_CONFIRMED -> AppLocale.tradeRadarInfoMeetingConfirmed
    TradeRadarViewModel.Info.SPOT_REPORTED -> AppLocale.tradeRadarInfoSpotReported
    TradeRadarViewModel.Info.DONE_WAITING -> AppLocale.tradeRadarInfoDoneWaiting
    TradeRadarViewModel.Info.TRADE_DONE -> AppLocale.tradeRadarInfoTradeDone
    TradeRadarViewModel.Info.NO_SHOW_SENT -> AppLocale.tradeRadarInfoNoShow
    TradeRadarViewModel.Info.DISPUTE_SENT -> AppLocale.tradeRadarInfoDispute
    TradeRadarViewModel.Info.COLLECTION_UPDATED -> AppLocale.tradeRadarInfoCollectionUpdated
    TradeRadarViewModel.Info.FEEDBACK_SENT -> AppLocale.tradeRadarInfoFeedbackSent
    TradeRadarViewModel.Info.LEADERBOARD_JOINED -> AppLocale.tradeRadarInfoJoined
    TradeRadarViewModel.Info.LEADERBOARD_LEFT -> AppLocale.tradeRadarInfoLeft
    TradeRadarViewModel.Info.BLOCKED -> AppLocale.tradeRadarInfoBlocked
    TradeRadarViewModel.Info.UNBLOCKED -> AppLocale.tradeRadarInfoUnblocked
    TradeRadarViewModel.Info.REPORTED -> AppLocale.tradeRadarInfoReported
}
