package com.emabuia.pokevault.ui.trade

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.emabuia.pokevault.data.remote.PokeVaultApiClient
import com.emabuia.pokevault.data.trade.CoarseLocation
import com.emabuia.pokevault.data.trade.TradeCardKey
import com.emabuia.pokevault.data.trade.TradeLists
import com.emabuia.pokevault.data.trade.dto.TradeMatch
import com.emabuia.pokevault.data.trade.dto.TradeMatchItem
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.TradeRadarViewModel
import com.emabuia.pokevault.viewmodel.TradeRadarViewModel.Problem
import com.emabuia.pokevault.viewmodel.TradeRadarViewModel.Screen

/**
 * TradeRadar, fase 1. Esiste solo nel flavor staging (vedi AppNavigation).
 *
 * Tre stati: attivazione, pannello (Match / Le mie carte) ed errore. Proposte,
 * appuntamenti, feedback e classifica arrivano con la fase 2.
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
            if (viewModel.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            when (val screen = viewModel.screen) {
                Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
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
            Text(AppLocale.tradeRadarOnboardingTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(AppLocale.tradeRadarOnboardingText, fontSize = 14.sp, color = AppColors.textSecondary)
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppColors.searchBar).padding(16.dp),
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
                modifier = Modifier.fillMaxWidth().height(48.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Hub(viewModel: TradeRadarViewModel, ready: Screen.Ready) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val paused = ready.profile.paused == true

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(ready.profile.nickname.orEmpty(), fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
            Text(
                if (paused) AppLocale.tradeRadarPausedLabel else AppLocale.tradeRadarActiveLabel,
                fontSize = 12.sp,
                color = if (paused) AppColors.orange else AppColors.green
            )
        }
        Text(AppLocale.tradeRadarAvailable, fontSize = 13.sp, color = AppColors.textSecondary)
        Spacer(Modifier.width(8.dp))
        Switch(checked = !paused, onCheckedChange = { viewModel.setPaused(!it) })
    }

    PrimaryTabRow(selectedTabIndex = tab, containerColor = AppColors.background) {
        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(AppLocale.tradeRadarTabMatches) })
        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(AppLocale.tradeRadarTabMyCards) })
    }

    when (tab) {
        0 -> MatchesTab(viewModel, paused)
        else -> MyCardsTab(viewModel)
    }
}

@Composable
private fun MatchesTab(viewModel: TradeRadarViewModel, paused: Boolean) {
    var level by rememberSaveable { mutableStateOf("all") }
    val shown = viewModel.matches.filter { match ->
        level == "all" || match.theyGive.orEmpty().any { it.level == level }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("all", "wanted", "useful", "possible")) { key ->
                    LevelChip(levelLabel(key), selected = level == key) { level = key }
                }
            }
        }
        when {
            paused -> item { Hint(AppLocale.tradeRadarPausedHint) }
            shown.isEmpty() -> item {
                Hint(if (viewModel.enabledIds.isEmpty()) AppLocale.tradeRadarNoHavesHint else AppLocale.tradeRadarNoMatches)
            }
            else -> items(shown) { match -> MatchCard(match, level) }
        }
        item {
            OutlinedButton(onClick = { viewModel.refreshMatches() }, modifier = Modifier.fillMaxWidth()) {
                Text(AppLocale.tradeRadarRefresh)
            }
        }
    }
}

@Composable
private fun MatchCard(match: TradeMatch, level: String) {
    val theyGive = match.theyGive.orEmpty().filter { level == "all" || it.level == level }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppColors.searchBar).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(match.nickname.orEmpty(), fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
                Text(
                    "${distanceLabel(match.distance)} · ${AppLocale.tradeRadarTradesDone(match.tradesDone ?: 0)}",
                    fontSize = 12.sp,
                    color = AppColors.textSecondary
                )
            }
            if (match.mutual == true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(AppColors.green.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.SwapHoriz, null, tint = AppColors.green, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(AppLocale.tradeRadarMutual, fontSize = 11.sp, color = AppColors.green, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(AppLocale.tradeRadarTheyGive, fontSize = 12.sp, color = AppColors.textMuted)
        CardStrip(theyGive)
        val iGive = match.iGive.orEmpty()
        if (iGive.isNotEmpty()) {
            Text(AppLocale.tradeRadarYouGive, fontSize = 12.sp, color = AppColors.textMuted)
            CardStrip(iGive)
        }
    }
}

@Composable
private fun CardStrip(items: List<TradeMatchItem>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { item ->
            val key = item.key.orEmpty()
            Column(Modifier.width(76.dp)) {
                AsyncImage(
                    model = TradeCardKey.imageUrl(key, PokeVaultApiClient.imageBaseUrl),
                    contentDescription = TradeCardKey.label(key),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.width(76.dp).height(106.dp).clip(RoundedCornerShape(6.dp))
                )
                Text(TradeCardKey.label(key), fontSize = 10.sp, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(reasonLabel(item), fontSize = 10.sp, color = levelColor(item.level), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MyCardsTab(viewModel: TradeRadarViewModel) {
    val duplicates = viewModel.duplicates
    val allOn = duplicates.isNotEmpty() && duplicates.all { it.id in viewModel.enabledIds }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(AppLocale.tradeRadarWantsSummary(viewModel.wantsCount), fontSize = 13.sp, color = AppColors.textSecondary)
            Spacer(Modifier.height(8.dp))
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    AppLocale.tradeRadarDuplicatesTitle(duplicates.size),
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (duplicates.isNotEmpty()) {
                    TextButton(onClick = { viewModel.setAllEnabled(!allOn) }) {
                        Text(if (allOn) AppLocale.tradeRadarNone else AppLocale.tradeRadarAll)
                    }
                }
            }
        }
        if (duplicates.isEmpty()) {
            item { Hint(AppLocale.tradeRadarNoDuplicates) }
        } else {
            items(duplicates, key = { it.id }) { duplicate ->
                DuplicateRow(duplicate, duplicate.id in viewModel.enabledIds) { viewModel.setEnabled(duplicate.id, it) }
            }
        }
    }
}

@Composable
private fun DuplicateRow(duplicate: TradeLists.Duplicate, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.searchBar).padding(10.dp)
    ) {
        AsyncImage(
            model = duplicate.imageUrl,
            contentDescription = duplicate.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(40.dp).height(56.dp).clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(duplicate.name, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${duplicate.setName} · ${duplicate.cardNumber} · ${duplicate.variant}",
                fontSize = 12.sp, color = AppColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "${duplicate.condition} · ${duplicate.language} · ${AppLocale.tradeRadarSpare(duplicate.spare)}",
                fontSize = 12.sp, color = AppColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}

// ── Pezzi comuni ────────────────────────────────────────────────────────────

@Composable
private fun LevelChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) AppColors.background else AppColors.textPrimary,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) AppColors.textPrimary else AppColors.searchBar)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 14.sp, color = AppColors.textSecondary, modifier = Modifier.padding(vertical = 12.dp))
}

private fun levelLabel(level: String): String = when (level) {
    "wanted" -> AppLocale.tradeRadarLevelWanted
    "useful" -> AppLocale.tradeRadarLevelUseful
    "possible" -> AppLocale.tradeRadarLevelPossible
    else -> AppLocale.tradeRadarLevelAll
}

private fun reasonLabel(item: TradeMatchItem): String = when (item.reason) {
    "wishlist" -> AppLocale.tradeRadarReasonWishlist
    "album" -> AppLocale.tradeRadarReasonAlbum
    "set" -> AppLocale.tradeRadarReasonSet
    else -> levelLabel(item.level.orEmpty())
}

@Composable
private fun levelColor(level: String?) = when (level) {
    "wanted" -> AppColors.green
    "useful" -> AppColors.blue
    else -> AppColors.textMuted
}

private fun distanceLabel(distance: String?): String =
    if (distance == "lt5") AppLocale.tradeRadarDistanceNear else AppLocale.tradeRadarDistanceArea

private fun problemText(problem: Problem): String = when (problem) {
    Problem.UNAUTHORIZED -> AppLocale.tradeRadarUnauthorized
    Problem.REJECTED -> AppLocale.tradeRadarRejected
    Problem.NO_LOCATION -> AppLocale.tradeRadarNoLocation
    Problem.UNAVAILABLE -> AppLocale.tradeRadarUnavailable(null)
}
