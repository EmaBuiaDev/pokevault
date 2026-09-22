package com.emabuia.pokevault.ui.collection

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.emabuia.pokevault.data.firebase.FirestoreRepository
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.model.collectionCardKey
import com.emabuia.pokevault.data.model.collectionGroupKey
import com.emabuia.pokevault.ui.components.CardVariants
import com.emabuia.pokevault.data.remote.PokeWalletPriceData
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.ui.navigation.sharedCardImage
import com.emabuia.pokevault.ui.pokedex.PriceSparkline
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.ImageUrlUtils
import com.emabuia.pokevault.util.getTypeEmojiForCollection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import com.emabuia.pokevault.ui.components.holoFoil
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.graded.companyLabel
import com.emabuia.pokevault.ui.graded.onAccentColor
import com.emabuia.pokevault.ui.graded.tierColor
import com.emabuia.pokevault.ui.graded.tierLabel
import com.emabuia.pokevault.util.GradeTier
import com.emabuia.pokevault.util.GradedLab
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Codice set da usare per la ricerca prezzi.
 *
 * Prima veniva passato `card.set`, cioe' il NOME VISUALIZZATO dell'espansione,
 * dove getCardPrices si aspetta un codice: la ricerca bruciava tutti e sei i
 * candidati di prioritizedSetLookupCandidates piu' due chiamate di fallback,
 * fino a ~7 richieste HTTP per ogni apertura del dettaglio.
 *
 * apiCardId e' il TcgCard.id da cui SetDetailViewModel.resolvePriceLookup
 * ricava lo stesso valore: "ita:<setCode>:..." per le carte italiane,
 * "<setId>-<numero>" per le altre. Se non si ricava nulla si torna al
 * comportamento precedente, cosi' il caso peggiore resta quello di prima.
 */
private fun resolveSetCodeForPrices(card: PokemonCard): String {
    val apiId = card.apiCardId.trim()

    if (apiId.startsWith("ita:", ignoreCase = true)) {
        val overlayCode = apiId.split(':').getOrNull(1).orEmpty()
        if (overlayCode.isNotBlank()) return overlayCode
    }

    val setCode = apiId.substringBeforeLast('-', missingDelimiterValue = "")
        .substringBefore("__")
    return setCode.ifBlank { card.set }
}

@Composable
private fun CollectionDetailImageFallback(card: PokemonCard) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.card)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = card.name,
                color = AppColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "-",
                color = AppColors.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = AppLocale.displaySetName(card.set).ifBlank { "-" },
                color = AppColors.textMuted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    cardId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    val repository = remember { FirestoreRepository() }
    val tcgRepository = remember { RepositoryProvider.tcgRepository }
    val pokeWalletRepository = remember { RepositoryProvider.pokeWalletRepository }
    val context = LocalContext.current

    var variants by remember { mutableStateOf<List<PokemonCard>>(emptyList()) }
    var editedQuantities by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedVariantIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    var livePrices by remember { mutableStateOf<PokeWalletPriceData?>(null) }
    var isLoadingLivePrices by remember { mutableStateOf(false) }
    val livePriceCacheByApiId = remember { mutableStateMapOf<String, PokeWalletPriceData?>() }

    // L'illustratore, per apiCardId. Una mappa e non una variabile sola
    // perche' la schermata elenca tutte le stampe della carta e si passa
    // dall'una all'altra: ognuna ha il suo apiCardId, e chi e' gia' stato
    // cercato non si cerca due volte -- nemmeno quando la risposta e' "non lo
    // so", ed e' il motivo per cui il valore e' nullable invece di assente.
    val illustratorByApiId = remember { mutableStateMapOf<String, String?>() }

    var tempIsGraded by remember { mutableStateOf(false) }
    var tempGrade by remember { mutableStateOf<Float?>(null) }
    var tempGradeStr by remember { mutableStateOf("") }
    var tempCompany by remember { mutableStateOf("") }

    var expandedGrading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // rememberSaveable e non remember: dopo una rotazione la carta deve
    // restare girata come l'utente l'ha lasciata.
    var isFlipped by rememberSaveable { mutableStateOf(false) }

    fun loadData() {
        scope.launch {
            repository.getCard(cardId).onSuccess { initialCard ->
                repository.getCards().first().let { allCards ->
                    val groupKey = initialCard.collectionGroupKey()
                    val found = allCards.filter { it.collectionGroupKey() == groupKey }
                    val resolved = found.ifEmpty { listOf(initialCard) }
                    variants = resolved
                    editedQuantities = resolved.associate { it.id to it.quantity }
                }
            }.onFailure {
                repository.getCards().first().let { allCards ->
                    // `collectionCardKey` e' la chiave con cui la collezione
                    // apre una tessera: raccoglie tutte le stampe della carta,
                    // che la schermata sa gia' elencare una per riga con la
                    // sua quantita'. Le altre due forme restano per chi arriva
                    // qui con l'id del documento o della carta a catalogo.
                    val found = allCards
                        .filter {
                            it.collectionGroupKey() == cardId ||
                                it.collectionCardKey() == cardId ||
                                it.apiCardId == cardId ||
                                it.id == cardId
                        }
                        .sortedBy { CardVariants.order(it.variant) }
                    variants = found
                    editedQuantities = found.associate { it.id to it.quantity }
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(cardId) {
        loadData()
    }

    LaunchedEffect(selectedVariantIndex, variants) {
        variants.getOrNull(selectedVariantIndex)?.let {
            tempIsGraded = it.isGraded
            tempGrade = it.grade
            tempGradeStr = it.grade?.toString() ?: ""
            tempCompany = it.gradingCompany
        }
    }

    // L'illustratore ha un effetto suo e non viaggia con i prezzi: quello sotto
    // esce prima di leggere la carta quando il prezzo e' gia' in cache, e la
    // carta e' l'unica cosa che porta il nome.
    LaunchedEffect(selectedVariantIndex, variants) {
        val apiCardId = variants.getOrNull(selectedVariantIndex)
            ?.apiCardId
            ?.takeIf { it.isNotBlank() }
            ?: return@LaunchedEffect
        if (illustratorByApiId.containsKey(apiCardId)) return@LaunchedEffect

        illustratorByApiId[apiCardId] = tcgRepository
            .italianIllustratorsByCardId(context, setOf(apiCardId))[apiCardId]
    }

    LaunchedEffect(selectedVariantIndex, variants) {
        val selected = variants.getOrNull(selectedVariantIndex)
        if (selected == null || selected.apiCardId.isBlank()) {
            livePrices = null
            isLoadingLivePrices = false
            return@LaunchedEffect
        }

        val cached = livePriceCacheByApiId[selected.apiCardId]
        if (cached != null || livePriceCacheByApiId.containsKey(selected.apiCardId)) {
            livePrices = cached
            isLoadingLivePrices = false
            return@LaunchedEffect
        }

        isLoadingLivePrices = true

        val remoteCard = tcgRepository.getCard(selected.apiCardId).getOrNull()
        val localPriceData = remoteCard?.let { remote ->
            val firstUsdPrice = remote.tcgplayer?.prices?.values?.firstOrNull { it.market != null || it.low != null }
            PokeWalletPriceData(
                eurAvg = remote.cardmarket?.prices?.averageSellPrice,
                eurLow = remote.cardmarket?.prices?.lowPrice,
                eurTrend = remote.cardmarket?.prices?.trendPrice,
                eurAvg1 = remote.cardmarket?.prices?.avg1,
                eurAvg7 = remote.cardmarket?.prices?.avg7,
                eurAvg30 = remote.cardmarket?.prices?.avg30,
                cardMarketUrl = remote.cardmarket?.url,
                usdMarket = firstUsdPrice?.market,
                usdLow = firstUsdPrice?.low,
                tcgPlayerUrl = remote.tcgplayer?.url
            )
        }

        val needsEnrichment = localPriceData == null ||
            !localPriceData.hasEurPrices ||
            !localPriceData.hasSparklineData ||
            localPriceData.cardMarketUrl.isNullOrBlank()

        val enriched = if (needsEnrichment) {
            pokeWalletRepository
                .getCardPrices(
                    cardName = selected.name,
                    setCode = resolveSetCodeForPrices(selected),
                    cardNumber = selected.cardNumber
                )
                .getOrNull()
        } else {
            null
        }

        val resolved = mergePriceData(primary = localPriceData, fallback = enriched)
        livePriceCacheByApiId[selected.apiCardId] = resolved
        livePrices = resolved
        isLoadingLivePrices = false
    }

    /**
     * Cosa c'e' di non salvato, in questo momento.
     *
     * Prima ogni riga variante aveva la sua spunta di conferma, e la spunta
     * compariva DENTRO la riga: il "+" e il "-" che si stavano premendo
     * scivolavano di lato nel momento esatto in cui li si usava, e il secondo
     * tocco finiva sul pulsante sbagliato. Adesso i due tasti stanno fermi
     * sempre, e la conferma e' una barra sola in fondo alla pagina.
     *
     * La barra raccoglie anche il grading: due conferme diverse nella stessa
     * schermata volevano dire premerne una, credere di aver salvato, e uscire
     * lasciando indietro l'altra meta'.
     */
    val selectedCard = variants.getOrNull(selectedVariantIndex)

    val pendingQuantities = variants.filter {
        (editedQuantities[it.id] ?: it.quantity) != it.quantity
    }

    // Voto ed ente contano solo a interruttore acceso: accenderlo e rispegnerlo
    // lascia scritto "PSA" nello stato temporaneo, e senza questa condizione la
    // barra resterebbe li' a chiedere di salvare una modifica che sullo schermo
    // non si vede piu'.
    val isGradingChanged = selectedCard != null && (
        tempIsGraded != selectedCard.isGraded ||
            (tempIsGraded && (tempGrade != selectedCard.grade || tempCompany != selectedCard.gradingCompany))
        )

    val pendingCount = pendingQuantities.size + if (isGradingChanged) 1 else 0

    val pendingRemoval = pendingQuantities.any { (editedQuantities[it.id] ?: it.quantity) <= 0 }

    fun discardPendingChanges() {
        editedQuantities = variants.associate { it.id to it.quantity }
        selectedCard?.let {
            tempIsGraded = it.isGraded
            tempGrade = it.grade
            tempGradeStr = it.grade?.toString() ?: ""
            tempCompany = it.gradingCompany
        }
    }

    /**
     * Salva tutto quello che e' in sospeso, in un colpo solo.
     *
     * Le validazioni del grading stanno prima di qualsiasi scrittura: se manca
     * il voto o l'ente non deve partire nemmeno l'aggiornamento della
     * quantita', o l'utente vedrebbe meta' del salvataggio andare a buon fine e
     * l'altra meta' no. Prima queste due validazioni facevano un return
     * silenzioso: si premeva conferma e non succedeva niente, senza alcuna
     * spiegazione.
     */
    fun commitPendingChanges() {
        if (isGradingChanged && tempIsGraded) {
            if (tempGrade == null) {
                scope.launch { snackbarHostState.showSnackbar(AppLocale.gradingGradeRequired) }
                return
            }
            if (tempCompany.isBlank()) {
                scope.launch { snackbarHostState.showSnackbar(AppLocale.gradingCompanyRequired) }
                return
            }
        }

        val gradedVariantId = selectedCard?.id

        scope.launch {
            // Niente loadData() alla fine: quella rilegge l'INTERA collezione
            // (getCards().first()) e ricalcola collectionGroupKey per ogni
            // carta, solo per aggiornare varianti che sono gia' in mano. Lo
            // stato locale contiene tutto il necessario.
            var remaining = variants
            var quantities = editedQuantities

            for (variant in variants) {
                val newQty = quantities[variant.id] ?: variant.quantity
                val carriesGrading = isGradingChanged && variant.id == gradedVariantId
                if (newQty == variant.quantity && !carriesGrading) continue

                if (newQty <= 0) {
                    repository.deleteCard(variant.id).onSuccess {
                        remaining = remaining.filter { it.id != variant.id }
                        quantities = quantities - variant.id
                    }
                } else {
                    val updated = variant.copy(
                        quantity = newQty,
                        isGraded = if (carriesGrading) tempIsGraded else variant.isGraded,
                        // Come prima: togliendo la spunta il voto resta scritto
                        // sul documento, non viene azzerato.
                        grade = if (carriesGrading) tempGrade ?: variant.grade else variant.grade,
                        gradingCompany = if (carriesGrading) {
                            tempCompany.ifBlank { variant.gradingCompany }
                        } else {
                            variant.gradingCompany
                        }
                    )
                    repository.updateCard(variant.id, updated).onSuccess {
                        remaining = remaining.map { if (it.id == variant.id) updated else it }
                        quantities = quantities + (variant.id to newQty)
                    }
                }
            }

            if (remaining.isEmpty()) {
                onBack()
            } else {
                variants = remaining
                editedQuantities = quantities
                selectedVariantIndex = selectedVariantIndex.coerceAtMost(remaining.lastIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(variants.firstOrNull()?.name ?: "Dettaglio", fontWeight = FontWeight.Bold, color = AppColors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = AppColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = pendingCount > 0,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                PendingChangesBar(
                    pendingCount = pendingCount,
                    isRemoval = pendingRemoval,
                    onDiscard = { discardPendingChanges() },
                    onSave = { commitPendingChanges() }
                )
            }
        },
        containerColor = AppColors.background
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.blue)
            }
        } else if (variants.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(AppLocale.noCardFound, color = AppColors.textSecondary)
            }
        } else {
            val currentCard = variants.getOrNull(selectedVariantIndex) ?: variants.first()
            val totalQty = variants.sumOf { editedQuantities[it.id] ?: it.quantity }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Il palco della carta: e' lei il soggetto della pagina, e si
                // gira col dito. Quando la carta e' gradata il palco diventa la
                // slab, con l'etichetta dell'ente sopra.
                CardStage(
                    card = currentCard,
                    sharedKey = cardId,
                    totalQuantity = totalQty,
                    isFlipped = isFlipped,
                    onFlipChange = { isFlipped = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Le quattro cose che identificano la carta, in fila sotto
                // l'immagine: prima erano sparse fra il retro e due sezioni
                // diverse, e per leggere il numero bisognava girare la carta.
                CardFactsRow(card = currentCard)

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        AppLocale.myVariantsLabel,
                        color = AppColors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${AppLocale.detailTotalCopies}: $totalQty",
                        color = AppColors.textMuted,
                        fontSize = 12.sp
                    )
                }

                variants.forEachIndexed { index, variant ->
                    val editedQty = editedQuantities[variant.id] ?: variant.quantity
                    VariantRow(
                        variant = variant,
                        editedQuantity = editedQty,
                        isSelected = selectedVariantIndex == index,
                        onClick = { selectedVariantIndex = index },
                        onQtyChange = { newQty ->
                            if (newQty >= 0) {
                                editedQuantities = editedQuantities.toMutableMap().apply { put(variant.id, newQty) }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                DetailSection(title = AppLocale.detailCertification) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = AppColors.gold, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(AppLocale.gradedCardSection, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(AppLocale.insertInGradedCards, color = AppColors.textMuted, fontSize = 11.sp)
                            }
                        }

                        // La spunta di conferma non sta piu' qui: la raccoglie
                        // la barra in fondo insieme alle quantita'.
                        Switch(
                            checked = tempIsGraded,
                            onCheckedChange = {
                                tempIsGraded = it
                                if (it && tempCompany.isBlank()) {
                                    tempCompany = "PSA"
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = AppColors.gold)
                        )
                    }

                    if (tempIsGraded) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tempGradeStr,
                                onValueChange = { input ->
                                    val sanitized = input.replace(',', '.')
                                    var dotCount = 0
                                    val filtered = sanitized.filter { 
                                        if (it == '.') {
                                            dotCount++
                                            dotCount <= 1
                                        } else {
                                            it.isDigit()
                                        }
                                    }
                                    
                                    val numericValue = filtered.toFloatOrNull()
                                    if (filtered.isEmpty()) {
                                        tempGradeStr = ""
                                        tempGrade = null
                                    } else if (numericValue != null && numericValue <= 10f) {
                                        tempGradeStr = filtered
                                        tempGrade = numericValue
                                    } else if (numericValue != null && numericValue > 10f) {
                                        // Blocca a 10 se superiore
                                        tempGradeStr = "10"
                                        tempGrade = 10f
                                    }
                                },
                                label = { Text(AppLocale.gradeLabel, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = AppColors.textPrimary, 
                                    unfocusedTextColor = AppColors.textPrimary,
                                    focusedLabelColor = AppColors.blue,
                                    unfocusedLabelColor = AppColors.textMuted
                                )
                            )
                            
                            ExposedDropdownMenuBox(
                                expanded = expandedGrading,
                                onExpandedChange = { expandedGrading = it },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = tempCompany.ifBlank { "PSA" },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(AppLocale.gradingAgency, fontSize = 10.sp) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedGrading) },
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = AppColors.textPrimary, 
                                        unfocusedTextColor = AppColors.textPrimary,
                                        focusedLabelColor = AppColors.blue,
                                        unfocusedLabelColor = AppColors.textMuted
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedGrading,
                                    onDismissRequest = { expandedGrading = false },
                                    modifier = Modifier.background(AppColors.surface)
                                ) {
                                    CardOptions.GRADING_COMPANIES.forEach { company ->
                                        DropdownMenuItem(
                                            text = { Text(company, color = AppColors.textPrimary) },
                                            onClick = {
                                                tempCompany = company
                                                expandedGrading = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                DetailSection(title = "${AppLocale.details} ${currentCard.variant}") {
                    DetailRow(AppLocale.condition, currentCard.condition)
                    DetailRow(AppLocale.languageLabel, currentCard.language)
                    DetailRow(AppLocale.estimatedValue, "€${"%.2f".format(currentCard.estimatedValue)}")
                    // Ultimo, e non in mezzo agli altri, perche' e' l'unico
                    // che parla della carta invece che di questa copia:
                    // condizione, lingua e valore cambiano da copia a copia,
                    // l'illustratore no. Arriva da D1 e copre il 98% del
                    // catalogo: dove manca la riga non c'e' affatto, invece di
                    // un trattino da riempire.
                    illustratorByApiId[currentCard.apiCardId]?.let { illustrator ->
                        DetailRow(AppLocale.illustrator, illustrator)
                    }
                    if (currentCard.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(currentCard.notes, color = AppColors.textSecondary, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val cardMarketUrl = livePrices?.cardMarketUrl?.takeIf { it.isNotBlank() }
                val tcgPlayerUrl = livePrices?.tcgPlayerUrl?.takeIf { it.isNotBlank() }
                DetailSection(
                    title = AppLocale.livePrices,
                    headerTrailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CompactMarketplaceHeaderButton(
                                label = "CardMarket",
                                url = cardMarketUrl,
                                onOpenUrl = { url ->
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            )
                            CompactMarketplaceHeaderButton(
                                label = "TCGPlayer",
                                url = tcgPlayerUrl,
                                onOpenUrl = { url ->
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            )
                        }
                    }
                ) {
                    if (isLoadingLivePrices) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = AppColors.blue
                            )
                            Text(AppLocale.loadingPrices, color = AppColors.textMuted, fontSize = 12.sp)
                        }
                    } else if (livePrices != null && livePrices?.hasEurPrices == true) {
                        Spacer(modifier = Modifier.height(2.dp))

                        val mainEurPrice = livePrices?.eurAvg ?: livePrices?.eurLow
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            if (mainEurPrice != null) {
                                Column {
                                    Text(AppLocale.averagePrice, color = AppColors.textMuted, fontSize = 11.sp)
                                    Text(
                                        "€${String.format("%.2f", mainEurPrice)}",
                                        color = AppColors.green,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp
                                    )
                                }
                            }
                            if (livePrices?.eurTrend != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(AppLocale.trend, color = AppColors.textMuted, fontSize = 11.sp)
                                    Text(
                                        "€${String.format("%.2f", livePrices?.eurTrend)}",
                                        color = AppColors.textPrimary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        if (livePrices?.hasSparklineData == true) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Cardmarket History", color = AppColors.textSecondary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            PriceSparkline(
                                avg30 = livePrices?.eurAvg30 ?: 0.0,
                                avg7 = livePrices?.eurAvg7 ?: 0.0,
                                avg1 = livePrices?.eurAvg1 ?: 0.0
                            )
                        }

                        if (livePrices?.eurLow != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = AppColors.textMuted.copy(alpha = 0.15f))
                            DetailRow(AppLocale.minPrice, "€${String.format("%.2f", livePrices?.eurLow)}")
                        }

                        if (livePrices?.usdMarket != null) {
                            HorizontalDivider(color = AppColors.textMuted.copy(alpha = 0.15f), modifier = Modifier.padding(top = 2.dp))
                            DetailRow("TCGPlayer", "$${String.format("%.2f", livePrices?.usdMarket)}")
                        }
                    } else {
                        Text(AppLocale.livePricesUnavailable, color = AppColors.textMuted, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

private fun mergePriceData(primary: PokeWalletPriceData?, fallback: PokeWalletPriceData?): PokeWalletPriceData? {
    if (primary == null) return fallback
    if (fallback == null) return primary

    return PokeWalletPriceData(
        eurAvg = primary.eurAvg ?: fallback.eurAvg,
        eurLow = primary.eurLow ?: fallback.eurLow,
        eurTrend = primary.eurTrend ?: fallback.eurTrend,
        eurAvg1 = primary.eurAvg1 ?: fallback.eurAvg1,
        eurAvg7 = primary.eurAvg7 ?: fallback.eurAvg7,
        eurAvg30 = primary.eurAvg30 ?: fallback.eurAvg30,
        eurVariantType = primary.eurVariantType ?: fallback.eurVariantType,
        usdMarket = primary.usdMarket ?: fallback.usdMarket,
        usdLow = primary.usdLow ?: fallback.usdLow,
        cardMarketUrl = primary.cardMarketUrl ?: fallback.cardMarketUrl,
        tcgPlayerUrl = primary.tcgPlayerUrl ?: fallback.tcgPlayerUrl
    )
}

/**
 * Larghezza del gruppo −/quantita'/+.
 *
 * E' fissa, e questo e' il punto: la riga puo' cambiare stato quanto vuole —
 * selezionata, modificata, in procinto di essere rimossa — ma i due tasti
 * restano esattamente dove il dito li ha appena trovati. Prima la spunta di
 * conferma compariva dentro la riga e li spostava di lato proprio mentre li si
 * stava premendo.
 */
private val StepperWidth = 124.dp

/** Larghezza della casella del numero: tiene tre cifre senza allargarsi. */
private val StepperValueWidth = 40.dp

@Composable
fun VariantRow(
    variant: PokemonCard,
    editedQuantity: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onQtyChange: (Int) -> Unit
) {
    val isChanged = editedQuantity != variant.quantity
    val willBeRemoved = editedQuantity <= 0

    val accent = when {
        willBeRemoved -> AppColors.red
        isChanged -> AppColors.green
        isSelected -> AppColors.blue
        else -> AppColors.textMuted
    }

    val shape = RoundedCornerShape(14.dp)
    val borderColor by animateColorAsState(
        targetValue = if (isSelected || isChanged) accent else Color.Transparent,
        animationSpec = tween(AppMotion.state),
        label = "variantBorder"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) accent.copy(alpha = 0.14f) else AppColors.card,
        animationSpec = tween(AppMotion.state),
        label = "variantBackground"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .pressScale(onClick = onClick)
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Il pallino e' l'unico posto dove lo stato della riga si vede: occupa
        // sempre gli stessi 8 dp, quindi cambiando colore non sposta niente.
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isSelected || isChanged) accent else accent.copy(alpha = 0.25f))
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = variant.variant,
                    color = if (isSelected) accent else AppColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (variant.isGraded) {
                    Spacer(modifier = Modifier.width(6.dp))
                    val tint = tierColor(GradedLab.tierOf(variant.grade))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(tint)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = companyLabel(GradedLab.companyKey(variant)) + " " + GradedLab.formatGrade(variant.grade),
                            color = onAccentColor(tint),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
            Text(
                text = variant.condition + " · " + variant.language,
                color = AppColors.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        QuantityStepper(
            value = editedQuantity,
            original = variant.quantity,
            accent = accent,
            onChange = onQtyChange
        )
    }
}

/**
 * Meno, numero, piu'. E niente altro.
 *
 * Il numero sta in una casella di larghezza fissa e cambia scorrendo, come il
 * rullo di un contatore: si vede *che* e' cambiato senza che nulla si sposti.
 * Il meno diventa un cestino quando il tocco successivo porterebbe a zero, cosi'
 * la rimozione si annuncia prima di succedere invece di essere una sorpresa.
 */
@Composable
private fun QuantityStepper(
    value: Int,
    original: Int,
    accent: Color,
    onChange: (Int) -> Unit
) {
    val willRemove = value <= 1

    Row(
        modifier = Modifier
            .width(StepperWidth)
            .height(40.dp)
            .clip(CircleShape)
            .background(AppColors.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { if (value > 0) onChange(value - 1) },
            enabled = value > 0,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (willRemove) Icons.Default.Delete else Icons.Default.Remove,
                contentDescription = AppLocale.quantity,
                tint = when {
                    value <= 0 -> AppColors.textMuted.copy(alpha = 0.35f)
                    willRemove -> AppColors.red
                    else -> AppColors.textPrimary
                },
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedContent(
            targetState = value,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInVertically { it } + fadeIn()) togetherWith
                        (slideOutVertically { -it } + fadeOut())
                } else {
                    (slideInVertically { -it } + fadeIn()) togetherWith
                        (slideOutVertically { it } + fadeOut())
                }
            },
            modifier = Modifier.width(StepperValueWidth),
            label = "quantity"
        ) { qty ->
            Text(
                text = "x" + qty,
                color = when {
                    qty <= 0 -> AppColors.red
                    qty != original -> accent
                    else -> AppColors.textPrimary
                },
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        IconButton(
            onClick = { onChange(value + 1) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = AppLocale.quantity,
                tint = AppColors.blue,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * La barra che raccoglie le modifiche non salvate.
 *
 * Entra dal basso solo quando c'e' qualcosa da salvare, dice quante cose sono e
 * offre le due uscite: annulla, che rimette tutto com'era, e salva. Quando fra
 * le modifiche c'e' una variante portata a zero diventa rossa e lo scrive: una
 * rimozione non deve poter succedere per inerzia.
 */
@Composable
private fun PendingChangesBar(
    pendingCount: Int,
    isRemoval: Boolean,
    onDiscard: () -> Unit,
    onSave: () -> Unit
) {
    val accent = if (isRemoval) AppColors.red else AppColors.green

    Surface(color = AppColors.card, shadowElevation = 12.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = AppLocale.detailPendingChanges(pendingCount),
                    color = AppColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isRemoval) {
                    Text(
                        text = AppLocale.detailDeleteVariant,
                        color = AppColors.red,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            TextButton(onClick = onDiscard) {
                Text(AppLocale.cancel, color = AppColors.textMuted, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.width(4.dp))

            Button(
                onClick = onSave,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = if (isRemoval) Icons.Default.DeleteForever else Icons.Default.Check,
                    contentDescription = null,
                    tint = onAccentColor(accent),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRemoval) AppLocale.delete else AppLocale.save,
                    color = onAccentColor(accent),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Quanti gradi di rotazione vale un pixel di trascinamento. */
private const val DegreesPerPixel = 0.6f

/** Oltre meta' giro la carta cade sull'altra faccia invece di tornare indietro. */
private const val FlipCommitDegrees = 90f

/**
 * Il palco della carta.
 *
 * Una carta gradata non e' la stessa carta con un bollino sopra: e' un blocco di
 * plastica con l'etichetta dell'ente stampata in cima, ed e' quella l'immagine
 * che si ha in testa aprendola. Per questo qui il palco cambia forma — cornice e
 * etichetta della slab quando la variante e' gradata, solo la carta quando non
 * lo e' — invece di appiccicare una stellina all'angolo.
 *
 * L'etichetta e' la stessa che disegna la griglia delle Gradate (vedi
 * GradeVisuals): la slab toccata li' e questa sono lo stesso oggetto.
 */
@Composable
private fun CardStage(
    card: PokemonCard,
    sharedKey: String,
    totalQuantity: Int,
    isFlipped: Boolean,
    onFlipChange: (Boolean) -> Unit
) {
    if (card.isGraded) {
        SlabFrame(card = card, modifier = Modifier.fillMaxWidth(0.88f)) {
            FlipCard(
                card = card,
                sharedKey = sharedKey,
                totalQuantity = totalQuantity,
                isFlipped = isFlipped,
                onFlipChange = onFlipChange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        FlipCard(
            card = card,
            sharedKey = sharedKey,
            totalQuantity = totalQuantity,
            isFlipped = isFlipped,
            onFlipChange = onFlipChange,
            modifier = Modifier.fillMaxWidth(0.8f)
        )
    }
}

/**
 * La carta che si gira in mano.
 *
 * Prima era un tocco e basta: la carta partiva e faceva il suo mezzo giro da
 * sola. Adesso la rotazione segue il dito grado per grado, e quando si lascia la
 * presa cade sulla faccia piu' vicina — se non si e' passata la meta' torna da
 * dove veniva, come una carta vera che non si e' girata abbastanza. Il tocco
 * secco continua a funzionare: e' la stessa cosa, fatta in fretta.
 *
 * Il trascinamento e' solo orizzontale di proposito. La pagina scorre in
 * verticale e un gesto su due assi si mangerebbe lo scroll di chi parte col dito
 * appoggiato sulla carta.
 */
@Composable
private fun FlipCard(
    card: PokemonCard,
    sharedKey: String,
    totalQuantity: Int,
    isFlipped: Boolean,
    onFlipChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = AppMotion.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // rememberSaveable per isFlipped sta in chi chiama; qui basta un Animatable
    // che riparte dalla faccia giusta, perche' e' l'unico modo di avere una
    // rotazione che il dito puo' interrompere a meta'. Con animateFloatAsState
    // il valore sarebbe di sola lettura e il trascinamento non avrebbe dove
    // scrivere.
    val spin = remember { Animatable(if (isFlipped) 180f else 0f) }

    // Le lambda di pointerInput vengono ricordate una volta sola: senza questi
    // due lo stato letto dentro onDragEnd sarebbe quello della prima
    // composizione, e la carta si girerebbe sempre dalla stessa parte.
    val flipped by rememberUpdatedState(isFlipped)
    val onFlip by rememberUpdatedState(onFlipChange)

    var everFlipped by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isFlipped) {
        val target = if (isFlipped) 180f else 0f
        if (spin.value != target) {
            // La durata e' proporzionale a quanto manca. Col tocco secco manca
            // mezzo giro intero e il tempo e' quello di sempre; se il dito ha
            // gia' portato la carta a un passo dall'altra faccia, restarci
            // dietro per altri tre quarti di secondo la farebbe sembrare
            // incollata.
            val remaining = (abs(target - spin.value) / 180f).coerceIn(0.35f, 1f)
            spin.animateTo(
                targetValue = target,
                animationSpec = tween((motion.flip * remaining).toInt(), easing = AppMotion.standardEasing)
            )
        }
    }

    // La rotazione si legge dentro graphicsLayer e non qui fuori: letta in
    // composizione rifarebbe l'intera schermata a ogni fotogramma del giro,
    // immagine compresa. Cosi' cambia solo il disegno, e l'unica cosa che
    // ricompone e' il cambio di faccia — una volta per mezzo giro.
    val facingFront by remember {
        derivedStateOf { cos(spin.value * Math.PI / 180.0) >= 0.0 }
    }

    Box(
        modifier = modifier
            .aspectRatio(0.71f)
            .graphicsLayer {
                rotationY = spin.value
                // Senza una distanza di camera esplicita la prospettiva e'
                // quasi ortogonale e la rotazione sembra uno schiacciamento
                // invece che un giro.
                cameraDistance = 14f * density
            }
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .clickable {
                everFlipped = true
                onFlipChange(!isFlipped)
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val landed = spin.value > FlipCommitDegrees
                        if (landed != flipped) {
                            everFlipped = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onFlip(landed)
                        } else {
                            scope.launch {
                                spin.animateTo(
                                    targetValue = if (landed) 180f else 0f,
                                    animationSpec = tween(motion.flip / 2, easing = AppMotion.standardEasing)
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            spin.animateTo(
                                targetValue = if (flipped) 180f else 0f,
                                animationSpec = tween(motion.flip / 2, easing = AppMotion.standardEasing)
                            )
                        }
                    }
                ) { change, drag ->
                    change.consume()
                    scope.launch {
                        // Il margine oltre le due facce e' quanto basta a far
                        // sentire che la carta e' arrivata in fondo, senza
                        // lasciarla girare all'infinito.
                        spin.snapTo(
                            (spin.value + drag * DegreesPerPixel)
                                .coerceIn(-FlipCommitDegrees, 180f + FlipCommitDegrees)
                        )
                    }
                }
            }
    ) {
        if (facingFront) {
            if (card.imageUrl.isNotBlank()) {
                SubcomposeAsyncImage(
                    model = ImageUrlUtils.safeImageUrl(card.imageUrl),
                    contentDescription = card.name,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedCardImage(sharedKey),
                    error = { CollectionDetailImageFallback(card) }
                )
            } else {
                CollectionDetailImageFallback(card)
            }

            // Il riflesso si sposta con la rotazione: e' la luce della stanza
            // che scorre sulla lamina mentre la carta gira. Sta sopra
            // l'immagine e non la tocca quando la carta e' ferma di fronte.
            if (motion.enabled) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawBehind {
                            // Anche qui la rotazione si legge in fase di
                            // disegno: il riflesso e' l'unica cosa che deve
                            // rifarsi a ogni fotogramma.
                            val glare = sin(spin.value * Math.PI / 180.0).toFloat()
                            val center = size.width * (0.5f + glare * 0.7f)
                            val band = size.width * 0.45f
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.22f * abs(glare)),
                                        Color.Transparent
                                    ),
                                    start = Offset(center - band, 0f),
                                    end = Offset(center + band, size.height)
                                )
                            )
                        }
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AppColors.blue),
                contentAlignment = Alignment.Center
            ) {
                Text("x$totalQuantity", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            // L'invito sparisce appena la carta e' stata girata una volta: e'
            // servito a dire che si puo' fare, e da li' in poi e' rumore sopra
            // l'illustrazione.
            androidx.compose.animation.AnimatedVisibility(
                visible = !everFlipped,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .clip(CircleShape)
                        .background(AppColors.background.copy(alpha = 0.78f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Autorenew,
                        contentDescription = null,
                        tint = AppColors.textSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = AppLocale.detailFlipHint,
                        color = AppColors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // Controrotazione: il retro e' disegnato dentro un layer gia' girato
            // di 180 gradi, senza questa il testo uscirebbe specchiato.
            CardBackFace(
                card = card,
                totalQuantity = totalQuantity,
                modifier = Modifier.graphicsLayer { rotationY = 180f }
            )
        }
    }
}

/**
 * La cornice della slab.
 *
 * Stessa etichetta della griglia delle Gradate, in grande: ente a sinistra,
 * fascia sotto, voto nel riquadro a destra. Il 10 si prende il gradiente e la
 * lamina, come li'.
 */
@Composable
private fun SlabFrame(
    card: PokemonCard,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val tier = GradedLab.tierOf(card.grade)
    val accent = tierColor(tier)
    val isGem = tier == GradeTier.GEM
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(AppColors.card, AppColors.surface)))
            .border(1.5.dp, accent.copy(alpha = if (isGem) 0.6f else 0.3f), shape)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isGem) {
                        Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.42f), accent.copy(alpha = 0.14f))
                        )
                    } else {
                        SolidColor(accent.copy(alpha = 0.16f))
                    }
                )
                .holoFoil(enabled = isGem)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = companyLabel(GradedLab.companyKey(card)),
                    color = accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tierLabel(tier),
                    color = AppColors.textMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = GradedLab.formatGrade(card.grade),
                    color = onAccentColor(accent),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        content()
    }
}

/**
 * Le targhette sotto la carta.
 *
 * Espansione, numero, rarita' e lingua erano sparsi fra il retro della carta e
 * due sezioni piu' in basso: per leggere il numero di una carta bisognava
 * girarla. Qui stanno in fila, in una riga sola che scorre di lato se non ci
 * stanno, e il colore e' quello del tipo della carta.
 */
@Composable
private fun CardFactsRow(card: PokemonCard) {
    val accent = TypeColors.of(card.type)
    val facts = buildList {
        val setName = AppLocale.displaySetName(card.set)
        if (setName.isNotBlank()) add(setName)
        if (card.cardNumber.isNotBlank()) add("#" + card.cardNumber)
        if (card.rarity.isNotBlank()) add(AppLocale.translateRarity(card.rarity))
        if (card.language.isNotBlank()) add(card.language)
    }

    if (facts.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (card.type.isNotBlank()) {
            FactChip(
                text = getTypeEmojiForCollection(card.type) + " " + AppLocale.translateType(card.type),
                accent = accent,
                filled = true
            )
        }
        facts.forEach { fact ->
            FactChip(text = fact, accent = accent, filled = false)
        }
    }
}

@Composable
private fun FactChip(text: String, accent: Color, filled: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (filled) accent.copy(alpha = 0.18f) else AppColors.card)
            .border(
                width = 1.dp,
                color = if (filled) accent.copy(alpha = 0.45f) else AppColors.textMuted.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = if (filled) accent else AppColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * Retro della carta nel flip.
 *
 * Non ripete il dettaglio completo che sta piu' giu' nella pagina: mostra le
 * righe che uno guarda mentre ha la carta in mano, piu' il prezzo. Se la carta
 * e' gradata le prime due righe sono ente e voto, che sono cio' che uno cerca
 * sul retro di una slab. Il resto (varianti, mercati) resta dove sta.
 */
@Composable
private fun CardBackFace(
    card: PokemonCard,
    totalQuantity: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(AppColors.card, AppColors.surface))
            )
            .padding(18.dp)
    ) {
        Text(
            text = card.name,
            color = AppColors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (card.isGraded) {
            CardBackRow(companyLabel(GradedLab.companyKey(card)), AppLocale.gradingAgency)
            CardBackRow(GradedLab.formatGrade(card.grade), AppLocale.detailGradeShort)
        }
        CardBackRow(AppLocale.displaySetName(card.set).ifBlank { "-" }, AppLocale.set)
        CardBackRow(card.cardNumber.ifBlank { "-" }, AppLocale.cardNumberLabel)
        CardBackRow(card.rarity.ifBlank { "-" }, AppLocale.rarity)
        if (!card.isGraded) {
            CardBackRow(card.condition.ifBlank { "-" }, AppLocale.condition)
        }
        CardBackRow("x$totalQuantity", AppLocale.quantity)

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.green.copy(alpha = 0.12f))
                .padding(12.dp)
        ) {
            Text(
                text = AppLocale.estimatedValue,
                color = AppColors.textSecondary,
                fontSize = 12.sp
            )
            Text(
                text = "€" + "%.2f".format(card.estimatedValue),
                color = AppColors.green,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
    }
}


@Composable
private fun CardBackRow(value: String, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = AppColors.textSecondary, fontSize = 12.sp)
        Text(
            text = value,
            color = AppColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
fun DetailSection(
    title: String,
    headerTrailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppColors.card).padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            headerTrailing()
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = AppColors.textMuted, fontSize = 14.sp)
        Text(text = value, color = AppColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CompactMarketplaceHeaderButton(
    label: String,
    url: String?,
    onOpenUrl: (String) -> Unit
) {
    val isEnabled = !url.isNullOrBlank()

    Surface(
        onClick = {
            if (isEnabled) {
                onOpenUrl(url!!)
            }
        },
        enabled = isEnabled,
        shape = RoundedCornerShape(10.dp),
        color = if (isEnabled) AppColors.blue.copy(alpha = 0.14f) else AppColors.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isEnabled) AppColors.blue.copy(alpha = 0.45f) else AppColors.textMuted.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 86.dp)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (isEnabled) AppColors.textPrimary else AppColors.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = if (isEnabled) "Open $label" else "$label unavailable",
                tint = if (isEnabled) AppColors.blue else AppColors.textMuted.copy(alpha = 0.7f),
                modifier = Modifier.size(10.dp)
            )
        }
    }
}
