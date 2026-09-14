@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.emabuia.pokevault.ui.competitive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.simulator.HandEvaluation
import com.emabuia.pokevault.data.simulator.HandSimulationEngine
import com.emabuia.pokevault.data.simulator.HandSimulationSummary
import com.emabuia.pokevault.data.simulator.HandSimulatorLocalStore
import com.emabuia.pokevault.data.simulator.HandTrait
import com.emabuia.pokevault.data.simulator.HandVerdict
import com.emabuia.pokevault.data.simulator.PracticeDealer
import com.emabuia.pokevault.data.simulator.PracticeHand
import com.emabuia.pokevault.data.simulator.PracticeTally
import com.emabuia.pokevault.data.simulator.SavedProblemHand
import com.emabuia.pokevault.data.simulator.SimulatorCard
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.DeckLabViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Le sole quantita' di run proposte: un campo numerico libero non aiutava nessuno. */
private val RUN_PRESETS = listOf(500, 1_000, 5_000, 10_000)

private const val MODE_PRACTICE = 0
private const val MODE_ANALYSIS = 1

/**
 * Hand Simulator: due modi sullo stesso mazzo.
 *
 * Prima era un unico scroll che mescolava tutorial, configurazione, risultati,
 * insight, mani problematiche e vault: tutto allo stesso livello, quindi senza
 * un ordine in cui leggerlo. Ora la schermata chiede una cosa sola in cima —
 * quale mazzo — e poi divide le due domande possibili: "questa mano la tengo?"
 * (Prova) e "ogni quanto il mazzo parte?" (Analisi).
 */
@Composable
fun HandSimulatorScreen(
    onBack: () -> Unit,
    onNavigateToPremium: () -> Unit,
    viewModel: DeckLabViewModel = viewModel()
) {
    val context = LocalContext.current
    val premiumManager = remember { PremiumManager.getInstance() }
    val isPremium by premiumManager.isPremium.collectAsStateWithLifecycle()
    val localStore = remember { HandSimulatorLocalStore(context) }
    val scope = rememberCoroutineScope()

    var mode by remember { mutableIntStateOf(MODE_PRACTICE) }
    var selectedDeckId by remember { mutableStateOf<String?>(null) }
    var runCount by remember { mutableIntStateOf(RUN_PRESETS[1]) }
    var selectedKeyCards by remember { mutableStateOf<List<String>>(emptyList()) }

    var practiceHand by remember { mutableStateOf<PracticeHand?>(null) }
    var tally by remember { mutableStateOf(PracticeTally()) }
    var dealKey by remember { mutableIntStateOf(0) }

    var summary by remember { mutableStateOf<HandSimulationSummary?>(null) }
    var accuracyWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSimulating by remember { mutableStateOf(false) }

    var savedHands by remember { mutableStateOf<List<SavedProblemHand>>(emptyList()) }
    var savedReloadTick by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }

    var deckMenuOpen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }
    var zoomedCard by remember { mutableStateOf<SimulatorCard?>(null) }

    val decks = viewModel.decks
    val selectedDeck = decks.firstOrNull { it.id == selectedDeckId }

    val cardPool = remember(selectedDeck, viewModel.ownedCards) {
        selectedDeck?.let { buildDeckCardPool(it, viewModel.ownedCards) } ?: emptyList()
    }
    val deckCardNames = remember(cardPool) {
        cardPool.map { it.name }.distinct().sorted()
    }

    LaunchedEffect(selectedDeckId, savedReloadTick) {
        savedHands = localStore.getSavedHands(selectedDeckId)
    }

    // Le carte importate prima che il catalogo avesse lo stadio sono in
    // collezione senza: qui si riparano, perche' e' questa la schermata in cui
    // un Pokemon contato come Base al posto di una Fase 1 cambia i numeri.
    LaunchedEffect(Unit) {
        viewModel.ensureCardStagesFromCatalog(context)
    }

    // Un solo mazzo: sceglierlo a mano sarebbe un tocco imposto senza scelta.
    LaunchedEffect(decks) {
        if (selectedDeckId == null && decks.size == 1) {
            selectedDeckId = decks.first().id
        }
    }

    fun resetForDeck(deckId: String) {
        selectedDeckId = deckId
        selectedKeyCards = emptyList()
        practiceHand = null
        tally = PracticeTally()
        summary = null
        accuracyWarnings = emptyList()
        feedback = null
    }

    fun dealFresh() {
        practiceHand = PracticeDealer.deal(cardPool, selectedKeyCards)
        dealKey += 1
    }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = AppLocale.handSimulatorTitle,
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
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = AppLocale.handSimulatorHowItWorksTitle,
                            tint = AppColors.textSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // La barra di controllo resta fissa: il mazzo e la modalita' sono
            // il contesto di tutto il resto, e scorrendo si perdevano di vista.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        DeckSelector(
                            deckName = selectedDeck?.name,
                            onClick = { deckMenuOpen = true }
                        )
                        DropdownMenu(
                            expanded = deckMenuOpen,
                            onDismissRequest = { deckMenuOpen = false }
                        ) {
                            if (decks.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(AppLocale.handSimulatorNoDecks) },
                                    onClick = { deckMenuOpen = false }
                                )
                            }
                            decks.forEach { deck ->
                                DropdownMenuItem(
                                    text = { Text(deck.name) },
                                    onClick = {
                                        resetForDeck(deck.id)
                                        deckMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.card)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = AppLocale.handSimulatorSettingsTitle,
                            tint = AppColors.textSecondary
                        )
                    }
                }

                ModeSwitch(
                    options = listOf(
                        AppLocale.handSimulatorModePractice,
                        AppLocale.handSimulatorModeAnalysis
                    ),
                    selectedIndex = mode,
                    onSelect = { mode = it }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                feedback?.let { message ->
                    item(key = "feedback") {
                        Text(
                            text = message,
                            color = AppColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                if (mode == MODE_PRACTICE) {
                    practiceSection(
                        hasDeck = selectedDeck != null,
                        deckTooSmall = cardPool.size < PracticeDealer.OPENING_SIZE,
                        hand = practiceHand,
                        tally = tally,
                        dealKey = dealKey,
                        savedHands = savedHands,
                        onDeal = { dealFresh() },
                        onKeep = {
                            tally = tally.copy(dealt = tally.dealt + 1, kept = tally.kept + 1)
                            dealFresh()
                        },
                        onMulligan = {
                            val current = practiceHand
                            tally = tally.copy(
                                dealt = tally.dealt + 1,
                                mulliganed = tally.mulliganed + 1
                            )
                            practiceHand = if (current == null) {
                                PracticeDealer.deal(cardPool, selectedKeyCards)
                            } else {
                                PracticeDealer.mulligan(current, cardPool, selectedKeyCards)
                            }
                            dealKey += 1
                        },
                        onDraw = {
                            practiceHand?.let { current ->
                                practiceHand = PracticeDealer.drawOne(current, selectedKeyCards)
                            }
                        },
                        onSaveHand = {
                            val deck = selectedDeck
                            val hand = practiceHand
                            if (deck != null && hand != null) {
                                localStore.saveProblemHand(
                                    deckId = deck.id,
                                    deckName = deck.name,
                                    cards = hand.opening.map { it.name },
                                    tags = hand.evaluation.storeTags()
                                )
                                savedReloadTick += 1
                                feedback = AppLocale.handSimulatorSavedToast
                            }
                        },
                        onDeleteSaved = { id ->
                            localStore.deleteProblemHand(id)
                            savedReloadTick += 1
                        },
                        onCardClick = { zoomedCard = it }
                    )
                } else {
                    analysisSection(
                        hasDeck = selectedDeck != null,
                        summary = summary,
                        accuracyWarnings = accuracyWarnings,
                        isSimulating = isSimulating,
                        runCount = runCount,
                        freeLimitNote = when {
                            isPremium -> AppLocale.handSimulatorPremiumUnlimited
                            selectedDeck == null -> null
                            else -> AppLocale.handSimulatorFreeLimitInfo(
                                premiumManager.getHandSimulatorRuns(selectedDeck.id)
                            )
                        },
                        onRun = {
                            val deck = selectedDeck ?: return@analysisSection
                            if (!premiumManager.canRunHandSimulator(deck.id, decks.size)) {
                                showPremiumDialog = true
                                return@analysisSection
                            }
                            if (cardPool.size < PracticeDealer.OPENING_SIZE) {
                                feedback = AppLocale.handSimulatorInvalidDeck
                                return@analysisSection
                            }

                            feedback = null
                            accuracyWarnings = deckAccuracyWarnings(deck, viewModel.ownedCards)
                            isSimulating = true

                            // Fino a 10.000 mescolate di una lista da 60 carte:
                            // fuori dal main thread, altrimenti la UI resta
                            // bloccata per secondi.
                            scope.launch {
                                val result = withContext(Dispatchers.Default) {
                                    HandSimulationEngine.run(
                                        cardPool = cardPool,
                                        runs = runCount,
                                        keyCardNames = selectedKeyCards
                                    )
                                }
                                summary = result
                                isSimulating = false
                                if (!isPremium) {
                                    premiumManager.consumeHandSimulatorRun(deck.id)
                                }
                            }
                        },
                        onSaveProblemHand = { hand ->
                            val deck = selectedDeck ?: return@analysisSection
                            localStore.saveProblemHand(
                                deckId = deck.id,
                                deckName = deck.name,
                                cards = hand.cards.map { it.name },
                                tags = hand.tags
                            )
                            savedReloadTick += 1
                            feedback = AppLocale.handSimulatorSavedToast
                        },
                        onCardClick = { zoomedCard = it }
                    )
                }

                item(key = "bottom-space") {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    if (showSettings) {
        SimulatorSettingsSheet(
            runCount = runCount,
            onRunCountChange = { runCount = it },
            deckCardNames = deckCardNames,
            selectedKeyCards = selectedKeyCards,
            onToggleKeyCard = { name ->
                selectedKeyCards = if (selectedKeyCards.contains(name)) {
                    selectedKeyCards - name
                } else {
                    selectedKeyCards + name
                }
            },
            onClearKeyCards = { selectedKeyCards = emptyList() },
            onDismiss = { showSettings = false }
        )
    }

    if (showHelp) {
        HelpDialog(onDismiss = { showHelp = false })
    }

    if (showPremiumDialog) {
        PremiumRequiredDialog(
            title = AppLocale.premiumHandSimulatorTitle,
            message = AppLocale.premiumHandSimulatorMessage,
            onDismiss = { showPremiumDialog = false },
            onUpgrade = {
                showPremiumDialog = false
                onNavigateToPremium()
            }
        )
    }

    zoomedCard?.let { card ->
        CardZoomDialog(card = card, onDismiss = { zoomedCard = null })
    }
}

@Composable
private fun DeckSelector(
    deckName: String?,
    onClick: () -> Unit
) {
    Surface(
        color = AppColors.card,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = deckName ?: AppLocale.handSimulatorSelectDeck,
                color = if (deckName == null) AppColors.textSecondary else AppColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = AppColors.textSecondary
            )
        }
    }
}

/**
 * Impostazioni: run e key card, fuori dal flusso principale.
 *
 * Stavano in cima alla schermata e andavano superate ogni volta, anche da chi
 * voleva solo pescare una mano. Sono scelte che si fanno una volta per mazzo,
 * quindi vivono meglio dietro un'icona.
 */
@Composable
private fun SimulatorSettingsSheet(
    runCount: Int,
    onRunCountChange: (Int) -> Unit,
    deckCardNames: List<String>,
    selectedKeyCards: List<String>,
    onToggleKeyCard: (String) -> Unit,
    onClearKeyCards: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val filtered = remember(deckCardNames, query) {
        if (query.isBlank()) deckCardNames
        else deckCardNames.filter { it.contains(query.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = AppLocale.handSimulatorSettingsTitle,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Text(
                text = AppLocale.handSimulatorRunsLabel,
                color = AppColors.textSecondary,
                fontSize = 12.sp
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RUN_PRESETS.forEach { preset ->
                    val selected = preset == runCount
                    Surface(
                        color = if (selected) AppColors.blue else AppColors.card,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { onRunCountChange(preset) }
                    ) {
                        Text(
                            text = formatRunPreset(preset),
                            color = if (selected) AppColors.onAccent else AppColors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = AppLocale.handSimulatorSelectKeyCards,
                    color = AppColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                if (selectedKeyCards.isNotEmpty()) {
                    TextButton(onClick = onClearKeyCards) {
                        Text(
                            text = AppLocale.handSimulatorClearKeyCards,
                            color = AppColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Text(
                text = AppLocale.handSimulatorKeyCardsHint,
                color = AppColors.textMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            if (selectedKeyCards.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectedKeyCards.forEach { name ->
                        Surface(
                            color = AppColors.blue.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { onToggleKeyCard(name) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                            ) {
                                Text(
                                    text = name,
                                    color = AppColors.blue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = AppColors.blue,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(AppLocale.handSimulatorSearchCard) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (filtered.isEmpty()) {
                Text(
                    text = AppLocale.handSimulatorNoSearchResults,
                    color = AppColors.textSecondary,
                    fontSize = 12.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filtered, key = { it }) { name ->
                        val selected = selectedKeyCards.contains(name)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onToggleKeyCard(name) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = name,
                                color = if (selected) AppColors.textPrimary else AppColors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = AppColors.blue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = AppLocale.handSimulatorDone,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.card,
        title = {
            Text(
                text = AppLocale.handSimulatorHowItWorksTitle,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            // Undici voci: su uno schermo corto il dialog non le contiene.
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = AppLocale.handSimulatorHowItWorksBody,
                    color = AppColors.textSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = AppLocale.handSimulatorMetricScore,
                    color = AppColors.textSecondary,
                    fontSize = 12.sp
                )
                // Tutte le metriche che la schermata mostra, anche quelle
                // chiuse dentro "Tutte le metriche": una riga vista e non
                // spiegata e' peggio di una riga non mostrata.
                Text(AppLocale.handSimulatorMetricStarter, color = AppColors.textSecondary, fontSize = 12.sp)
                Text(AppLocale.handSimulatorMetricEnergyT1, color = AppColors.textSecondary, fontSize = 12.sp)
                Text(AppLocale.handSimulatorMetricOutT1, color = AppColors.textSecondary, fontSize = 12.sp)
                Text(AppLocale.handSimulatorMetricSetupT2, color = AppColors.textSecondary, fontSize = 12.sp)
                Text(AppLocale.handSimulatorMetricKeyByT2, color = AppColors.textSecondary, fontSize = 12.sp)
                Text(AppLocale.handSimulatorMetricRuns, color = AppColors.textSecondary, fontSize = 12.sp)
                Text(AppLocale.handSimulatorMetricMulligan, color = AppColors.textSecondary, fontSize = 12.sp)
                Text(AppLocale.handSimulatorMetricAvgBasics, color = AppColors.textSecondary, fontSize = 12.sp)
                Text(AppLocale.handSimulatorMetricAvgMulligans, color = AppColors.textSecondary, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue)
            ) {
                Text(text = AppLocale.handSimulatorDone)
            }
        }
    )
}

/**
 * La carta a schermo intero.
 *
 * Nel ventaglio una carta e' larga ottanta dp: basta a riconoscerla, non a
 * leggerne il testo, ed e' proprio il testo che serve quando ci si chiede se
 * quella mano si sblocca.
 */
@Composable
private fun CardZoomDialog(
    card: SimulatorCard,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDismiss)
        ) {
            SimCardFace(
                card = card,
                cornerRadius = 14.dp,
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .aspectRatio(63f / 88f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = card.name,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
    }
}

private fun formatRunPreset(preset: Int): String =
    if (preset >= 1000) "${preset / 1000}k" else preset.toString()

/**
 * I tag con cui una mano finisce nel vault locale.
 *
 * Sono gli stessi che produce la simulazione statistica, cosi' le mani salvate
 * dalla Prova e quelle salvate dall'Analisi restano confrontabili nella stessa
 * lista.
 */
private fun HandEvaluation.storeTags(): List<String> {
    val tags = mutableListOf<String>()
    if (energies == 0) tags += "NO_ENERGY_T1"
    if (outs == 0) tags += "NO_OUT_T1"
    if (weaknesses.contains(HandTrait.MISS_KEY_CARD)) tags += "MISS_KEYCARD_T2"
    if (verdict == HandVerdict.RISKY || verdict == HandVerdict.MULLIGAN) tags += "SETUP_RISK_T2"
    return tags
}
