package com.emabuia.pokevault.ui.pokedex

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import coil.compose.AsyncImage
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.remote.PokeWalletPriceData
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.RarityUtils.getRarityInfo

@Composable
fun CardDetailBottomSheet(
    card: TcgCard,
    isOwned: Boolean,
    isLoading: Boolean,
    onAddCard: (variant: String, quantity: Int, condition: String, language: String) -> Unit,
    onRemoveCard: () -> Unit,
    onDismiss: () -> Unit,
    cardList: List<TcgCard> = emptyList(),
    onCardChange: (TcgCard) -> Unit = {},
    pokeWalletPrices: PokeWalletPriceData? = null,
    isLoadingPokeWalletPrices: Boolean = false
) {
    val rarityInfo = getRarityInfo(card.rarity)
    val context = LocalContext.current

    // Varianti disponibili (API + fallback per rarità)
    val availableVariants = remember(card) {
        CardOptions.getVariantsForCard(card.tcgplayer?.prices?.keys ?: emptySet(), card.rarity)
    }

    // State form
    var selectedVariant by remember { mutableStateOf(availableVariants.firstOrNull() ?: "Normal") }
    var quantity by remember { mutableIntStateOf(1) }
    var selectedCondition by remember { mutableStateOf("Near Mint") }
    var selectedLanguage by remember { mutableStateOf("🇮🇹 Italiano") }
    var showAddForm by remember { mutableStateOf(!isOwned) }

    val currentCardIndex = remember(card.id, cardList) { cardList.indexOfFirst { it.id == card.id } }

    // Prezzo per variante selezionata
    val variantKey = CardOptions.getVariantApiKey(selectedVariant)
    val price = card.tcgplayer?.prices?.get(variantKey)?.market
        ?: card.cardmarket?.prices?.lowPrice
        ?: card.cardmarket?.prices?.averageSellPrice

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(DarkBackground)
                    .clickable(enabled = false, onClick = {})
            ) {
                // ── Handle ──
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextMuted.copy(alpha = 0.3f))
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    // ── Header: nome, numero, set ──
                    Text(
                        text = card.name,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (card.types != null) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(getTypeColorForTcg(card.types.firstOrNull()))
                            )
                        }
                        Text("#${card.number}", color = TextMuted, fontSize = 13.sp)
                        if (card.set != null) {
                            Text("·", color = TextMuted, fontSize = 13.sp)
                            Text(card.set.name, color = TextGray, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Immagine carta ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(16.dp))
                            .let { baseModifier ->
                                if (currentCardIndex >= 0 && cardList.size > 1) {
                                    baseModifier.pointerInput(card.id, cardList) {
                                        var totalDragX = 0f
                                        detectHorizontalDragGestures(
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                totalDragX += dragAmount
                                            },
                                            onDragEnd = {
                                                val swipeThreshold = 80f
                                                when {
                                                    totalDragX <= -swipeThreshold && currentCardIndex < cardList.lastIndex -> {
                                                        onCardChange(cardList[currentCardIndex + 1])
                                                    }
                                                    totalDragX >= swipeThreshold && currentCardIndex > 0 -> {
                                                        onCardChange(cardList[currentCardIndex - 1])
                                                    }
                                                }
                                                totalDragX = 0f
                                            },
                                            onDragCancel = { totalDragX = 0f }
                                        )
                                    }
                                } else {
                                    baseModifier
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = card.images.large,
                            contentDescription = card.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Info pills + Azione ──
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Le pill prendono lo spazio rimanente e scrollano
                        // orizzontalmente se non ci stanno: in questo modo il
                        // bottone "Aggiungi" non viene mai compresso e il suo
                        // testo resta leggibile su una sola riga.
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InfoPill(icon = "✦", text = card.rarity ?: "Sconosciuto", color = rarityInfo.color)
                            if (price != null && price > 0) {
                                InfoPill(icon = "💰", text = "${"%.2f".format(price)} €", color = GreenCard)
                            }
                        }

                        // Bottone aggiungi inline - larghezza intrinseca,
                        // misurato prima del contenitore pesato quindi non si
                        // restringe mai.
                        if (!isOwned || showAddForm) {
                            Surface(
                                onClick = {
                                    if (!isLoading) {
                                        onAddCard(selectedVariant, quantity, selectedCondition, selectedLanguage)
                                        onDismiss()
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = BlueCard
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                    Text(
                                        text = "Aggiungi",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── Sezione stato possesso ──
                    if (isOwned && !showAddForm) {
                        // Carta già posseduta
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(GreenCard.copy(alpha = 0.1f))
                                .border(1.dp, GreenCard.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(GreenCard),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    Column {
                                        Text("Nella tua collezione", color = GreenCard, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        Text("Tocca per aggiungere un'altra copia", color = TextMuted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Aggiungi altra copia
                            Button(
                                onClick = { showAddForm = true },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BlueCard)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Aggiungi copia", fontSize = 13.sp)
                            }
                            // Rimuovi
                            OutlinedButton(
                                onClick = { onRemoveCard(); onDismiss() },
                                modifier = Modifier.height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RedCard.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.Delete, null, tint = RedCard, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // ── Form personalizzazione (opzionale) ──
                    if (!isOwned || showAddForm) {
                        Text(
                            text = if (isOwned) "Personalizza copia" else "Personalizza",
                            color = TextMuted,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // ── Riga 1: Quantità + Condizione ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Quantità
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Quantità", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(DarkCard)
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { if (quantity > 1) quantity-- },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DarkSurface)
                                    ) {
                                        Icon(Icons.Default.Remove, null, tint = TextWhite, modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        text = "$quantity",
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    IconButton(
                                        onClick = { quantity++ },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(BlueCard)
                                    ) {
                                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            // Condizione
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Condizione", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                                OptionSelector(
                                    options = CardOptions.CONDITIONS,
                                    selected = selectedCondition,
                                    onSelect = { selectedCondition = it }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // ── Riga 2: Lingua + Versione ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Lingua
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Lingua", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                                OptionSelector(
                                    options = CardOptions.LANGUAGES,
                                    selected = selectedLanguage,
                                    onSelect = { selectedLanguage = it }
                                )
                            }

                            // Versione/Variante
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Versione", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                                OptionSelector(
                                    options = availableVariants,
                                    selected = selectedVariant,
                                    onSelect = { selectedVariant = it }
                                )
                            }
                        }

                    }

                    // ── Dettagli carta ──
                    Text("Dettagli", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkCard)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (card.supertype.isNotBlank()) DetailInfoRow("Supertipo", card.supertype)
                        if (card.subtypes != null) DetailInfoRow("Sottotipo", card.subtypes.joinToString(", "))
                        if (card.hp != null) DetailInfoRow("HP", card.hp)
                        if (card.types != null) DetailInfoRow("Tipo", card.types.joinToString(", "))
                        if (card.rarity != null) DetailInfoRow("Rarità", card.rarity)
                        DetailInfoRow("Numero", "#${card.number}")
                        if (card.set != null) {
                            DetailInfoRow("Set", card.set.name)
                            DetailInfoRow("Serie", card.set.series)
                        }

                        // Varianti disponibili con prezzi
                        val variants = card.tcgplayer?.prices
                        if (variants != null && variants.isNotEmpty()) {
                            HorizontalDivider(color = TextMuted.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))
                            Text("Prezzi per variante", color = TextWhite, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            variants.forEach { (key, priceInfo) ->
                                val variantName = when (key) {
                                    "normal" -> "Normal"
                                    "holofoil" -> "Holofoil"
                                    "reverseHolofoil" -> "Reverse Holo"
                                    "1stEditionHolofoil" -> "1st Ed. Holo"
                                    "1stEditionNormal" -> "1st Edition"
                                    else -> key
                                }
                                val mkt = priceInfo.market
                                if (mkt != null && mkt > 0) {
                                    DetailInfoRow(variantName, "${"%.2f".format(mkt)} €")
                                }
                            }
                        }
                    }

                    // ── Prezzi Live (PokeWallet) ──
                    if (isLoadingPokeWalletPrices) {
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
                        Spacer(modifier = Modifier.height(12.dp))
                    } else if (pokeWalletPrices != null && pokeWalletPrices.hasEurPrices) {
                        Text("Prezzi Live", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        val cardMarketUrl = pokeWalletPrices.cardMarketUrl
                            ?.takeIf { it.isNotBlank() }
                            ?: card.cardmarket?.url?.takeIf { it.isNotBlank() }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkCard)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "Apri su CardMarket",
                                            tint = TextGray,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            val mainEurPrice = pokeWalletPrices.eurAvg ?: pokeWalletPrices.eurLow
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                if (mainEurPrice != null) {
                                    Column {
                                        Text("Prezzo medio", color = TextMuted, fontSize = 11.sp)
                                        Text(
                                            "\u20AC${".2f".format(mainEurPrice).let { String.format("%.2f", mainEurPrice) }}",
                                            color = GreenCard,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 22.sp
                                        )
                                    }
                                }
                                if (pokeWalletPrices.eurTrend != null) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Trend", color = TextMuted, fontSize = 11.sp)
                                        Text(
                                            "\u20AC${String.format("%.2f", pokeWalletPrices.eurTrend)}",
                                            color = TextWhite,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            if (pokeWalletPrices.hasSparklineData) {
                                Spacer(modifier = Modifier.height(4.dp))
                                PriceSparkline(
                                    avg30 = pokeWalletPrices.eurAvg30!!,
                                    avg7 = pokeWalletPrices.eurAvg7!!,
                                    avg1 = pokeWalletPrices.eurAvg1!!
                                )
                            }

                            if (pokeWalletPrices.eurLow != null) {
                                HorizontalDivider(color = TextMuted.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 2.dp))
                                DetailInfoRow("Prezzo minimo", "\u20AC${String.format("%.2f", pokeWalletPrices.eurLow)}")
                            }
                        }

                        if (pokeWalletPrices.usdMarket != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(DarkCard)
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("\uD83C\uDDFA\uD83C\uDDF8", fontSize = 14.sp)
                                    Text("TCGPlayer", color = TextGray, fontSize = 13.sp)
                                }
                                Text(
                                    "\$${String.format("%.2f", pokeWalletPrices.usdMarket)}",
                                    color = TextWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }

            }
        }
    }
}

@Composable
fun PriceSparkline(avg30: Double, avg7: Double, avg1: Double) {
    val points = listOf(avg30, avg7, avg1)
    val labels = listOf("30gg", "7gg", "Oggi")
    val min = points.min()
    val max = points.max()
    val range = (max - min).coerceAtLeast(0.01)

    val trendColor = when {
        avg1 > avg30 * 1.01 -> Color(0xFF22C55E)   // green: rising
        avg1 < avg30 * 0.99 -> Color(0xFFEF4444)   // red: falling
        else -> Color(0xFF9CA3AF)                    // gray: stable
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            val w = size.width
            val h = size.height
            val pad = 12f
            val xStep = (w - 2 * pad) / (points.size - 1)

            val coords = points.mapIndexed { i, v ->
                val x = pad + i * xStep
                val y = (h - pad) - ((v - min) / range * (h - 2 * pad)).toFloat()
                Offset(x, y)
            }

            for (i in 0 until coords.size - 1) {
                drawLine(
                    color = trendColor,
                    start = coords[i],
                    end = coords[i + 1],
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
            coords.forEach { offset ->
                drawCircle(color = trendColor, radius = 4.5f, center = offset)
                drawCircle(color = android.graphics.Color.BLACK.let { Color(it) }, radius = 2f, center = offset)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEachIndexed { i, v ->
                Column(
                    horizontalAlignment = when (i) {
                        0 -> Alignment.Start
                        points.lastIndex -> Alignment.End
                        else -> Alignment.CenterHorizontally
                    }
                ) {
                    Text(labels[i], color = TextMuted, fontSize = 10.sp)
                    Text("\u20AC${String.format("%.2f", v)}", color = TextGray, fontSize = 10.sp)
                }
            }
        }
    }
}

// ── Dropdown compatto ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSelector(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkCard)
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 13.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selected,
                    color = TextWhite,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DarkSurface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option,
                            color = if (option == selected) BlueCard else TextWhite,
                            fontSize = 13.sp,
                            fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = { onSelect(option); expanded = false },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun InfoPill(icon: String, text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = icon, fontSize = 12.sp)
        Text(text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextMuted, fontSize = 13.sp)
        Text(text = value, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

fun getTypeColorForTcg(type: String?): Color {
    return when (type?.lowercase()) {
        "fire" -> Color(0xFFEF4444)
        "water" -> Color(0xFF3B82F6)
        "grass" -> Color(0xFF22C55E)
        "lightning" -> Color(0xFFEAB308)
        "psychic" -> Color(0xFF8B5CF6)
        "fighting" -> Color(0xFFF97316)
        "darkness" -> Color(0xFF6366F1)
        "metal" -> Color(0xFF6B7280)
        "dragon" -> Color(0xFF7C3AED)
        "fairy" -> Color(0xFFEC4899)
        "colorless" -> Color(0xFF9CA3AF)
        else -> Color(0xFF6B7280)
    }
}
