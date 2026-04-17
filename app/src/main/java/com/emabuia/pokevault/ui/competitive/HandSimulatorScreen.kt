package com.emabuia.pokevault.ui.competitive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.simulator.HandSimulationEngine
import com.emabuia.pokevault.data.simulator.HandSimulationSummary
import com.emabuia.pokevault.data.simulator.HandSimulatorLocalStore
import com.emabuia.pokevault.data.simulator.ProblemHandSample
import com.emabuia.pokevault.data.simulator.SavedProblemHand
import com.emabuia.pokevault.data.simulator.SimulatorCard
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.BlueCard
import com.emabuia.pokevault.ui.theme.DarkBackground
import com.emabuia.pokevault.ui.theme.DarkCard
import com.emabuia.pokevault.ui.theme.TextGray
import com.emabuia.pokevault.ui.theme.TextWhite
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.DeckLabViewModel
import kotlin.math.roundToInt
import androidx.compose.ui.text.input.KeyboardType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class HandInsight(
    val title: String,
    val message: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HandSimulatorScreen(
    onBack: () -> Unit,
    onNavigateToPremium: () -> Unit,
    viewModel: DeckLabViewModel = viewModel()
) {
    val context = LocalContext.current
    val premiumManager = remember { PremiumManager.getInstance() }
    val isPremium by premiumManager.isPremium.collectAsState()
    val localStore = remember { HandSimulatorLocalStore(context) }

    var selectedDeckId by remember { mutableStateOf<String?>(null) }
    var deckDropdownExpanded by remember { mutableStateOf(false) }
    var keyCardDropdownExpanded by remember { mutableStateOf(false) }
    var runCountText by remember { mutableStateOf("1000") }
    var selectedKeyCardNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<HandSimulationSummary?>(null) }
    var insights by remember { mutableStateOf<List<HandInsight>>(emptyList()) }
    var saveFeedback by remember { mutableStateOf<String?>(null) }
    var showPremiumDialog by remember { mutableStateOf(false) }
    var showMetricInfoDialog by remember { mutableStateOf(false) }
    var savedReloadTick by remember { mutableStateOf(0) }
    var savedHands by remember { mutableStateOf<List<SavedProblemHand>>(emptyList()) }

    val decks = viewModel.decks
    val selectedDeck = decks.firstOrNull { it.id == selectedDeckId }
    val keyCardOptions = remember(selectedDeck, viewModel.ownedCards) {
        selectedDeck?.let { deck ->
            val cardPool = buildDeckCardPool(deck, viewModel.ownedCards)
            cardPool.map { it.name }.distinct().sorted()
        } ?: emptyList()
    }

    LaunchedEffect(selectedDeckId, savedReloadTick) {
        savedHands = localStore.getSavedHands(selectedDeckId)
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = AppLocale.handSimulatorTitle,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = AppLocale.handSimulatorHowItWorksTitle,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = AppLocale.handSimulatorHowItWorksBody,
                            color = TextGray,
                            fontSize = 12.sp
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkBackground),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = AppLocale.handSimulatorHowItWorksExample,
                                color = TextGray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = AppLocale.handSimulatorDeckLabel,
                            color = TextGray,
                            fontSize = 12.sp
                        )

                        Box {
                            OutlinedButton(
                                onClick = { deckDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = selectedDeck?.name ?: AppLocale.handSimulatorSelectDeck,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            DropdownMenu(
                                expanded = deckDropdownExpanded,
                                onDismissRequest = { deckDropdownExpanded = false }
                            ) {
                                decks.forEach { deck ->
                                    DropdownMenuItem(
                                        text = { Text(deck.name) },
                                        onClick = {
                                            selectedDeckId = deck.id
                                            deckDropdownExpanded = false
                                            selectedKeyCardNames = emptyList()
                                            summary = null
                                            insights = emptyList()
                                            saveFeedback = null
                                            validationMessage = null
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = runCountText,
                            onValueChange = { next ->
                                runCountText = next.filter { it.isDigit() }.take(5)
                            },
                            label = { Text(AppLocale.handSimulatorRunCount) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Box {
                            OutlinedButton(
                                onClick = { keyCardDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = AppLocale.handSimulatorSelectKeyCards,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            DropdownMenu(
                                expanded = keyCardDropdownExpanded,
                                onDismissRequest = { keyCardDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(AppLocale.handSimulatorNoKeyCard) },
                                    onClick = {
                                        selectedKeyCardNames = emptyList()
                                        keyCardDropdownExpanded = false
                                    }
                                )
                                keyCardOptions.forEach { cardName ->
                                    val isSelected = selectedKeyCardNames.contains(cardName)
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isSelected) "[x] $cardName" else "[ ] $cardName"
                                            )
                                        },
                                        onClick = {
                                            selectedKeyCardNames = if (isSelected) {
                                                selectedKeyCardNames - cardName
                                            } else {
                                                selectedKeyCardNames + cardName
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Text(
                            text = AppLocale.handSimulatorSelectedKeyCards,
                            color = TextGray,
                            fontSize = 12.sp
                        )

                        if (selectedKeyCardNames.isEmpty()) {
                            Text(
                                text = AppLocale.handSimulatorNoKeyCardSelected,
                                color = TextGray,
                                fontSize = 12.sp
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                selectedKeyCardNames.forEach { keyName ->
                                    KeyCardChip(
                                        label = keyName,
                                        onRemove = {
                                            selectedKeyCardNames = selectedKeyCardNames - keyName
                                        }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val deck = selectedDeck
                                if (deck == null) {
                                    validationMessage = AppLocale.handSimulatorSelectDeckFirst
                                    return@Button
                                }

                                if (!premiumManager.canRunHandSimulator(deck.id, decks.size)) {
                                    showPremiumDialog = true
                                    return@Button
                                }

                                val runCount = runCountText.toIntOrNull()?.coerceIn(1, 10000) ?: 1000
                                val cardPool = buildDeckCardPool(deck, viewModel.ownedCards)
                                if (cardPool.size < 7) {
                                    validationMessage = AppLocale.handSimulatorInvalidDeck
                                    return@Button
                                }

                                val result = HandSimulationEngine.run(
                                    cardPool = cardPool,
                                    runs = runCount,
                                    keyCardNames = selectedKeyCardNames
                                )

                                summary = result
                                insights = buildInsights(result)
                                validationMessage = null
                                saveFeedback = null

                                if (!isPremium) {
                                    premiumManager.consumeHandSimulatorRun(deck.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BlueCard)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = null
                            )
                            Text(
                                text = AppLocale.handSimulatorRunButton,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        if (!isPremium) {
                            val runsUsed = selectedDeck?.let { premiumManager.getHandSimulatorRuns(it.id) } ?: 0
                            Text(
                                text = AppLocale.handSimulatorFreeLimitInfo(runsUsed),
                                color = TextGray,
                                fontSize = 12.sp
                            )
                        } else {
                            Text(
                                text = AppLocale.handSimulatorPremiumUnlimited,
                                color = TextGray,
                                fontSize = 12.sp
                            )
                        }

                        if (decks.isEmpty()) {
                            Text(
                                text = AppLocale.handSimulatorNoDecks,
                                color = TextWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = AppLocale.handSimulatorNoDecksSubtitle,
                                color = TextGray,
                                fontSize = 12.sp
                            )
                        }

                        validationMessage?.let { message ->
                            Text(
                                text = message,
                                color = TextGray,
                                fontSize = 12.sp
                            )
                        }

                        saveFeedback?.let { feedback ->
                            Text(
                                text = feedback,
                                color = TextGray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            summary?.let { result ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = AppLocale.handSimulatorResults,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                IconButton(onClick = { showMetricInfoDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = AppLocale.handSimulatorMetricInfoTitle,
                                        tint = TextGray
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatPill(
                                    label = AppLocale.handSimulatorTotalRuns,
                                    value = result.totalRuns.toString(),
                                    modifier = Modifier.weight(1f)
                                )
                                StatPill(
                                    label = AppLocale.handSimulatorStarterRate,
                                    value = "${result.starterRate.roundPercent()}%",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatPill(
                                    label = AppLocale.handSimulatorMulliganRate,
                                    value = "${result.mulliganRate.roundPercent()}%",
                                    modifier = Modifier.weight(1f)
                                )
                                StatPill(
                                    label = AppLocale.handSimulatorAvgBasics,
                                    value = result.averageBasics.roundTo2Decimals(),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatPill(
                                    label = AppLocale.handSimulatorEnergyT1,
                                    value = "${result.energyByT1Rate.roundPercent()}%",
                                    modifier = Modifier.weight(1f)
                                )
                                StatPill(
                                    label = AppLocale.handSimulatorOutT1,
                                    value = "${result.outByT1Rate.roundPercent()}%",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatPill(
                                    label = AppLocale.handSimulatorSetupT2,
                                    value = "${result.setupByT2Rate.roundPercent()}%",
                                    modifier = Modifier.weight(1f)
                                )
                                StatPill(
                                    label = AppLocale.handSimulatorAvgMulligans,
                                    value = result.averageMulligans.roundTo2Decimals(),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            result.keyCardByT2Rate?.let { keyRate ->
                                StatPill(
                                    label = AppLocale.handSimulatorKeyByT2,
                                    value = "${keyRate.roundPercent()}%",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Text(
                                text = AppLocale.handSimulatorSampleHand,
                                color = TextGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                result.sampleHand.forEach { cardName ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = DarkBackground),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = cardName,
                                            color = TextWhite,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (insights.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = AppLocale.handSimulatorInsightsTitle,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )

                                insights.forEach { insight ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = DarkBackground),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = insight.title,
                                                color = TextWhite,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = insight.message,
                                                color = TextGray,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (result.problemHands.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = AppLocale.handSimulatorProblemsTitle,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )

                                result.problemHands.take(5).forEach { hand ->
                                    ProblemHandCard(
                                        hand = hand,
                                        onSave = {
                                            val deck = selectedDeck ?: return@ProblemHandCard
                                            localStore.saveProblemHand(
                                                deckId = deck.id,
                                                deckName = deck.name,
                                                cards = hand.cards,
                                                tags = hand.tags
                                            )
                                            savedReloadTick += 1
                                            saveFeedback = AppLocale.handSimulatorSavedToast
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = AppLocale.handSimulatorSavedTitle,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        if (savedHands.isEmpty()) {
                            Text(
                                text = AppLocale.handSimulatorSavedEmpty,
                                color = TextGray,
                                fontSize = 12.sp
                            )
                        } else {
                            savedHands.take(12).forEach { hand ->
                                SavedProblemHandCard(
                                    hand = hand,
                                    onDelete = {
                                        localStore.deleteProblemHand(hand.id)
                                        savedReloadTick += 1
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
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

    if (showMetricInfoDialog) {
        MetricInfoDialog(
            onDismiss = { showMetricInfoDialog = false }
        )
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(
                text = label,
                color = TextGray,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun ProblemHandCard(
    hand: ProblemHandSample,
    onSave: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                hand.tags.forEach { tag ->
                    TagChip(label = translateProblemTag(tag))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                hand.cards.forEach { cardName ->
                    TagChip(label = cardName)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onSave,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlueCard)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Text(
                    text = AppLocale.handSimulatorSaveProblem,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SavedProblemHandCard(
    hand: SavedProblemHand,
    onDelete: () -> Unit
) {
    val df = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    val dateLabel = remember(hand.createdAtMillis) { df.format(Date(hand.createdAtMillis)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hand.deckName,
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dateLabel,
                        color = TextGray,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = AppLocale.delete,
                        tint = TextGray
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                hand.tags.forEach { tag ->
                    TagChip(label = translateProblemTag(tag))
                }
            }
        }
    }
}

@Composable
private fun TagChip(label: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            color = TextGray,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun KeyCardChip(
    label: String,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkBackground),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = label,
                color = TextGray,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = AppLocale.delete,
                    tint = TextGray
                )
            }
        }
    }
}

@Composable
private fun MetricInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Text(
                text = AppLocale.handSimulatorMetricInfoTitle,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(AppLocale.handSimulatorMetricRuns, color = TextGray, fontSize = 12.sp)
                Text(AppLocale.handSimulatorMetricStarter, color = TextGray, fontSize = 12.sp)
                Text(AppLocale.handSimulatorMetricMulligan, color = TextGray, fontSize = 12.sp)
                Text(AppLocale.handSimulatorMetricAvgBasics, color = TextGray, fontSize = 12.sp)
                Text(AppLocale.handSimulatorMetricEnergyT1, color = TextGray, fontSize = 12.sp)
                Text(AppLocale.handSimulatorMetricOutT1, color = TextGray, fontSize = 12.sp)
                Text(AppLocale.handSimulatorMetricSetupT2, color = TextGray, fontSize = 12.sp)
                Text(AppLocale.handSimulatorMetricAvgMulligans, color = TextGray, fontSize = 12.sp)
                Text(AppLocale.handSimulatorMetricKeyByT2, color = TextGray, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = BlueCard)
            ) {
                Text(text = AppLocale.cancel)
            }
        }
    )
}

private fun buildDeckCardPool(deck: Deck, ownedCards: List<PokemonCard>): List<SimulatorCard> {
    val cardMap = ownedCards.associateBy { it.id }
    return deck.cards.mapNotNull { cardId ->
        val card = cardMap[cardId] ?: return@mapNotNull null
        SimulatorCard(
            name = card.name,
            isBasic = card.isBasicPokemon(),
            isEnergy = card.classify() == "Energy",
            isSupporter = card.isSupporterCard(),
            isOutCard = card.isOutCard()
        )
    }
}

private fun PokemonCard.isBasicPokemon(): Boolean {
    // A Pokémon is Basic if:
    // 1. It's classified as Pokémon and has HP > 0
    // 2. Either explicitly has "Basic" subtype OR has no evolution markers
    val isPokemon = classify() == "Pokémon"
    if (!isPokemon || hp <= 0) return false
    
    val subtypesLower = subtypes.map { it.lowercase() }
    
    // Check if explicitly marked as Basic
    if (subtypesLower.any { it.contains("basic") || it.contains("base") }) {
        return true
    }
    
    // Check if it's NOT an evolution type - if no evolution markers, it's implicitly Basic
    val evolutionMarkers = listOf("stage 1", "stage 2", "stage1", "stage2", "v-max", "vmax", "vstar", "v-star", "lv.x")
    val hasEvolutionMarker = subtypesLower.any { subtype ->
        evolutionMarkers.any { marker -> subtype.contains(marker) }
    }
    
    return !hasEvolutionMarker
}

private fun PokemonCard.isSupporterCard(): Boolean {
    if (classify() != "Trainer") return false
    return subtypes.any {
        val normalized = it.lowercase()
        normalized.contains("supporter") || normalized.contains("aiuto")
    }
}

private fun PokemonCard.isOutCard(): Boolean {
    val nameKey = name.lowercase().trim()
    val outKeywords = listOf(
        "ultra ball", "nest ball", "buddy-buddy poffin", "poffin", "earthen vessel",
        "research", "professor", "iono", "pokégear", "pokegear", "colress",
        "artazon", "forest seal stone", "rotom", "lumineon", "squawk"
    )
    val keywordMatch = outKeywords.any { key -> nameKey.contains(key) }
    return keywordMatch || isSupporterCard()
}

private fun buildInsights(summary: HandSimulationSummary): List<HandInsight> {
    val insights = mutableListOf<HandInsight>()

    if (summary.mulliganRate >= 12.0) {
        insights += HandInsight(
            title = AppLocale.handSimulatorInsightBrickTitle,
            message = AppLocale.handSimulatorInsightBrickMessage(summary.mulliganRate.roundPercent())
        )
    }

    if (summary.energyByT1Rate < 72.0) {
        insights += HandInsight(
            title = AppLocale.handSimulatorInsightEnergyTitle,
            message = AppLocale.handSimulatorInsightEnergyMessage(summary.energyByT1Rate.roundPercent())
        )
    }

    if (summary.outByT1Rate < 65.0) {
        insights += HandInsight(
            title = AppLocale.handSimulatorInsightOutTitle,
            message = AppLocale.handSimulatorInsightOutMessage(summary.outByT1Rate.roundPercent())
        )
    }

    if (summary.setupByT2Rate < 60.0) {
        insights += HandInsight(
            title = AppLocale.handSimulatorInsightSetupTitle,
            message = AppLocale.handSimulatorInsightSetupMessage(summary.setupByT2Rate.roundPercent())
        )
    }

    if (insights.isEmpty()) {
        insights += HandInsight(
            title = AppLocale.handSimulatorInsightGoodTitle,
            message = AppLocale.handSimulatorInsightGoodMessage
        )
    }

    return insights
}

private fun translateProblemTag(tag: String): String {
    return when (tag) {
        "NO_ENERGY_T1" -> AppLocale.handSimulatorTagNoEnergyT1
        "NO_OUT_T1" -> AppLocale.handSimulatorTagNoOutT1
        "SETUP_RISK_T2" -> AppLocale.handSimulatorTagSetupRiskT2
        "MISS_KEYCARD_T2" -> AppLocale.handSimulatorTagMissKeyT2
        "NO_BASIC_IN_DECK" -> AppLocale.handSimulatorTagNoBasicDeck
        else -> tag
    }
}

private fun Double.roundPercent(): Int = roundToInt()

private fun Double.roundTo2Decimals(): String {
    val rounded = (this * 100.0).roundToInt() / 100.0
    return rounded.toString()
}
