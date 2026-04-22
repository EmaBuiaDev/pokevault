package com.emabuia.pokevault.ui.collection

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.emabuia.pokevault.data.remote.PokeWalletPriceData
import com.emabuia.pokevault.data.remote.RepositoryProvider
import com.emabuia.pokevault.ui.pokedex.PriceSparkline
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.getTypeEmojiForCollection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private fun safeImageUrl(url: String): String {
    return url
        .replace(" ", "%20")
        .replace("(", "%28")
        .replace(")", "%29")
}

@Composable
private fun CollectionDetailImageFallback(card: PokemonCard) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkCard)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = card.name,
                color = TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "-",
                color = TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = card.set.ifBlank { "-" },
                color = TextMuted,
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

    var tempIsGraded by remember { mutableStateOf(false) }
    var tempGrade by remember { mutableStateOf<Float?>(null) }
    var tempGradeStr by remember { mutableStateOf("") }
    var tempCompany by remember { mutableStateOf("") }

    var expandedGrading by remember { mutableStateOf(false) }

    fun loadData() {
        scope.launch {
            repository.getCard(cardId).onSuccess { initialCard ->
                val apiId = initialCard.apiCardId
                if (apiId.isNotBlank()) {
                    repository.getCards().first().let { allCards ->
                        val found = allCards.filter { it.apiCardId == apiId }
                        variants = found
                        editedQuantities = found.associate { it.id to it.quantity }
                    }
                } else {
                    variants = listOf(initialCard)
                    editedQuantities = mapOf(initialCard.id to initialCard.quantity)
                }
            }.onFailure {
                repository.getCards().first().let { allCards ->
                    val found = allCards.filter { it.apiCardId == cardId || it.id == cardId }
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
                    setCode = selected.set,
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

    fun confirmVariantChange(card: PokemonCard, newQty: Int, isGraded: Boolean? = null, grade: Float? = null, company: String? = null) {
        if (isGraded == true) {
            if (grade == null) return
            if (company.isNullOrBlank()) return 
        }

        scope.launch {
            val updatedCard = card.copy(
                quantity = newQty,
                isGraded = isGraded ?: card.isGraded,
                grade = grade ?: card.grade,
                gradingCompany = company ?: card.gradingCompany
            )
            
            if (newQty <= 0) {
                repository.deleteCard(card.id).onSuccess {
                    val remaining = variants.filter { it.id != card.id }
                    if (remaining.isEmpty()) onBack() else loadData()
                }
            } else {
                repository.updateCard(card.id, updatedCard).onSuccess {
                    loadData()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(variants.firstOrNull()?.name ?: "Dettaglio", fontWeight = FontWeight.Bold, color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BlueCard)
            }
        } else if (variants.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Carta non trovata", color = TextGray)
            }
        } else {
            val currentCard = variants.getOrNull(selectedVariantIndex) ?: variants.first()
            val totalQty = variants.sumOf { editedQuantities[it.id] ?: it.quantity }
            
            val isGradingChanged = tempIsGraded != currentCard.isGraded || 
                                 tempGrade != currentCard.grade || 
                                 tempCompany != currentCard.gradingCompany
            
            val canSaveGrading = if (tempIsGraded) {
                tempGrade != null && tempCompany.isNotBlank()
            } else {
                true 
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .aspectRatio(0.71f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                ) {
                    if (currentCard.imageUrl.isNotBlank()) {
                        SubcomposeAsyncImage(
                            model = safeImageUrl(currentCard.imageUrl),
                            contentDescription = currentCard.name,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                            error = { CollectionDetailImageFallback(currentCard) }
                        )
                    } else {
                        CollectionDetailImageFallback(currentCard)
                    }
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(BlueCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("x$totalQty", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Varianti in tuo possesso:",
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )

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
                        },
                        onConfirm = { confirmVariantChange(variant, editedQty) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                DetailSection(title = "Certificazione Grading") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = StarGold, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Carta Gradata", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Inserisci nelle carte gradate", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = tempIsGraded,
                                onCheckedChange = { 
                                    tempIsGraded = it
                                    if (it && tempCompany.isBlank()) {
                                        tempCompany = "PSA"
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = StarGold)
                            )
                            
                            AnimatedVisibility(visible = isGradingChanged && canSaveGrading) {
                                IconButton(
                                    onClick = { confirmVariantChange(currentCard, currentCard.quantity, tempIsGraded, tempGrade, tempCompany) },
                                    modifier = Modifier.padding(start = 8.dp).size(28.dp).background(GreenCard, CircleShape)
                                ) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
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
                                label = { Text("Voto (1-10)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextWhite, 
                                    unfocusedTextColor = TextWhite,
                                    focusedLabelColor = BlueCard,
                                    unfocusedLabelColor = TextMuted
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
                                    label = { Text("Ente", fontSize = 10.sp) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedGrading) },
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextWhite, 
                                        unfocusedTextColor = TextWhite,
                                        focusedLabelColor = BlueCard,
                                        unfocusedLabelColor = TextMuted
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedGrading,
                                    onDismissRequest = { expandedGrading = false },
                                    modifier = Modifier.background(DarkSurface)
                                ) {
                                    CardOptions.GRADING_COMPANIES.forEach { company ->
                                        DropdownMenuItem(
                                            text = { Text(company, color = TextWhite) },
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

                DetailSection(title = "Dettagli ${currentCard.variant}") {
                    DetailRow("Condizione", currentCard.condition)
                    DetailRow("Lingua", currentCard.language)
                    DetailRow("Valore stimato", "€${"%.2f".format(currentCard.estimatedValue)}")
                    if (currentCard.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(currentCard.notes, color = TextGray, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val cardMarketUrl = livePrices?.cardMarketUrl?.takeIf { it.isNotBlank() }
                DetailSection(title = "Prezzi Live") {
                    if (isLoadingLivePrices) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = BlueCard
                            )
                            Text("Caricamento prezzi...", color = TextMuted, fontSize = 12.sp)
                        }
                    } else if (livePrices != null && livePrices?.hasEurPrices == true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("\uD83C\uDDEA\uD83C\uDDFA", fontSize = 14.sp)
                                Text("CardMarket", color = TextGray, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            }
                            if (cardMarketUrl != null) {
                                IconButton(
                                    onClick = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cardMarketUrl)))
                                    },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "Apri su CardMarket",
                                        tint = TextGray,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val mainEurPrice = livePrices?.eurAvg ?: livePrices?.eurLow
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            if (mainEurPrice != null) {
                                Column {
                                    Text("Prezzo medio", color = TextMuted, fontSize = 11.sp)
                                    Text(
                                        "€${String.format("%.2f", mainEurPrice)}",
                                        color = GreenCard,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp
                                    )
                                }
                            }
                            if (livePrices?.eurTrend != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Trend", color = TextMuted, fontSize = 11.sp)
                                    Text(
                                        "€${String.format("%.2f", livePrices?.eurTrend)}",
                                        color = TextWhite,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        if (livePrices?.hasSparklineData == true) {
                            Spacer(modifier = Modifier.height(8.dp))
                            PriceSparkline(
                                avg30 = livePrices!!.eurAvg30!!,
                                avg7 = livePrices!!.eurAvg7!!,
                                avg1 = livePrices!!.eurAvg1!!
                            )
                        }

                        if (livePrices?.eurLow != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = TextMuted.copy(alpha = 0.15f))
                            DetailRow("Prezzo minimo", "€${String.format("%.2f", livePrices?.eurLow)}")
                        }

                        if (livePrices?.usdMarket != null) {
                            HorizontalDivider(color = TextMuted.copy(alpha = 0.15f), modifier = Modifier.padding(top = 2.dp))
                            DetailRow("TCGPlayer", "$${String.format("%.2f", livePrices?.usdMarket)}")
                        }
                    } else {
                        Text("Prezzi live non disponibili per questa carta", color = TextMuted, fontSize = 12.sp)
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

@Composable
fun VariantRow(
    variant: PokemonCard,
    editedQuantity: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onQtyChange: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    val isChanged = editedQuantity != variant.quantity

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) BlueCard.copy(alpha = 0.2f) else DarkCard)
            .border(
                width = 1.dp,
                color = if (isSelected) BlueCard else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = variant.variant,
                    color = if (isSelected) BlueCard else TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                if (variant.isGraded) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(StarGold).padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text("⭐ ${variant.grade}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                text = "${variant.condition} · ${variant.language}",
                color = TextMuted,
                fontSize = 12.sp
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { if (editedQuantity > 0) onQtyChange(editedQuantity - 1) },
                modifier = Modifier.size(36.dp),
                enabled = editedQuantity > 0
            ) {
                Icon(
                    imageVector = if (editedQuantity <= 1) Icons.Default.Delete else Icons.Default.Remove,
                    contentDescription = null,
                    tint = if (editedQuantity <= 1) RedCard.copy(alpha = if(editedQuantity > 0) 1f else 0.3f) else TextWhite,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = "x$editedQuantity",
                color = if (editedQuantity == 0) RedCard else TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.widthIn(min = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            IconButton(onClick = { onQtyChange(editedQuantity + 1) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Add, null, tint = BlueCard, modifier = Modifier.size(18.dp))
            }

            AnimatedVisibility(
                visible = isChanged,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                IconButton(
                    onClick = onConfirm,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(32.dp)
                        .background(if (editedQuantity == 0) RedCard else GreenCard, CircleShape)
                ) {
                    Icon(
                        imageVector = if (editedQuantity == 0) Icons.Default.DeleteForever else Icons.Default.Check,
                        contentDescription = "Conferma",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DarkCard).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
        Text(text = label, color = TextMuted, fontSize = 14.sp)
        Text(text = value, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
