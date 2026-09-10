package com.emabuia.pokevault.ui.pokedex

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.remote.PokeWalletPriceData
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.RarityUtils.getRarityInfo

@Composable
fun CardDetailBottomSheet(
    card: TcgCard,
    isOwned: Boolean,
    isLoading: Boolean,
    languageOptions: List<String> = CardOptions.LANGUAGES,
    defaultLanguage: String? = null,
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
    val resolvedLanguageOptions = remember(languageOptions) {
        languageOptions.distinct().ifEmpty { CardOptions.LANGUAGES }
    }
    val resolvedDefaultLanguage = remember(card.id, defaultLanguage, resolvedLanguageOptions) {
        defaultLanguage?.takeIf { it in resolvedLanguageOptions } ?: resolvedLanguageOptions.first()
    }

    // State form
    var selectedVariant by remember { mutableStateOf(availableVariants.firstOrNull() ?: "Normal") }
    var quantity by remember { mutableIntStateOf(1) }
    var selectedCondition by remember { mutableStateOf("Near Mint") }
    var selectedLanguage by remember(card.id, resolvedDefaultLanguage) { mutableStateOf(resolvedDefaultLanguage) }
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
                    .background(AppColors.background)
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
                        .background(AppColors.textMuted.copy(alpha = 0.3f))
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
                        color = AppColors.textPrimary,
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
                        Text("#${card.number}", color = AppColors.textMuted, fontSize = 13.sp)
                        if (card.set != null) {
                            Text("·", color = AppColors.textMuted, fontSize = 13.sp)
                            Text(card.set.name, color = AppColors.textSecondary, fontSize = 13.sp)
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
                                InfoPill(icon = "💰", text = "${"%.2f".format(price)} €", color = AppColors.green)
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
                                color = AppColors.blue
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
                                .background(AppColors.green.copy(alpha = 0.1f))
                                .border(1.dp, AppColors.green.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
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
                                            .background(AppColors.green),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    Column {
                                        Text(AppLocale.inCollection, color = AppColors.green, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        Text(AppLocale.tapToAddCopy, color = AppColors.textMuted, fontSize = 12.sp)
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
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(AppLocale.addCopy, fontSize = 13.sp)
                            }
                            // Rimuovi
                            OutlinedButton(
                                onClick = { onRemoveCard(); onDismiss() },
                                modifier = Modifier.height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.red.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.Delete, null, tint = AppColors.red, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // ── Form personalizzazione (opzionale) ──
                    if (!isOwned || showAddForm) {
                        Text(
                            text = if (isOwned) "Personalizza copia" else "Personalizza",
                            color = AppColors.textMuted,
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
                                Text(AppLocale.quantity, color = AppColors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AppColors.card)
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { if (quantity > 1) quantity-- },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AppColors.surface)
                                    ) {
                                        Icon(Icons.Default.Remove, null, tint = AppColors.textPrimary, modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        text = "$quantity",
                                        color = AppColors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    IconButton(
                                        onClick = { quantity++ },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AppColors.blue)
                                    ) {
                                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            // Condizione
                            Column(modifier = Modifier.weight(1f)) {
                                Text(AppLocale.condition, color = AppColors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
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
                                Text(AppLocale.languageLabel, color = AppColors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                                OptionSelector(
                                    options = resolvedLanguageOptions,
                                    selected = selectedLanguage,
                                    onSelect = { selectedLanguage = it }
                                )
                            }

                            // Versione/Variante
                            Column(modifier = Modifier.weight(1f)) {
                                Text(AppLocale.version, color = AppColors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                                OptionSelector(
                                    options = availableVariants,
                                    selected = selectedVariant,
                                    onSelect = { selectedVariant = it }
                                )
                            }
                        }

                    }

                    // ── Dettagli carta ──
                    Text(AppLocale.details, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.card)
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
                            HorizontalDivider(color = AppColors.textMuted.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))
                            Text(AppLocale.pricesByVariant, color = AppColors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
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
                                color = AppColors.blue
                            )
                            Text(AppLocale.loadingPrices, color = AppColors.textMuted, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    } else if (pokeWalletPrices != null && pokeWalletPrices.hasEurPrices) {
                        val cardMarketUrl = pokeWalletPrices.cardMarketUrl
                            ?.takeIf { it.isNotBlank() }
                            ?: card.cardmarket?.url?.takeIf { it.isNotBlank() }
                        val tcgPlayerUrl = pokeWalletPrices.tcgPlayerUrl
                            ?.takeIf { it.isNotBlank() }
                            ?: card.tcgplayer?.url?.takeIf { it.isNotBlank() }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = AppLocale.livePrices,
                                color = AppColors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CompactMarketplaceHeaderButtonBottomSheet(
                                    label = "CardMarket",
                                    url = cardMarketUrl,
                                    onOpenUrl = { url ->
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                )
                                CompactMarketplaceHeaderButtonBottomSheet(
                                    label = "TCGPlayer",
                                    url = tcgPlayerUrl,
                                    onOpenUrl = { url ->
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(AppColors.card)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val mainEurPrice = pokeWalletPrices.eurAvg ?: pokeWalletPrices.eurLow
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                if (mainEurPrice != null) {
                                    Column {
                                        Text(AppLocale.averagePrice, color = AppColors.textMuted, fontSize = 11.sp)
                                        Text(
                                            "\u20AC${".2f".format(mainEurPrice).let { String.format("%.2f", mainEurPrice) }}",
                                            color = AppColors.green,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 22.sp
                                        )
                                    }
                                }
                                if (pokeWalletPrices.eurTrend != null) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(AppLocale.trend, color = AppColors.textMuted, fontSize = 11.sp)
                                        Text(
                                            "\u20AC${String.format("%.2f", pokeWalletPrices.eurTrend)}",
                                            color = AppColors.textPrimary,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            if (pokeWalletPrices.hasSparklineData) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Cardmarket History", color = AppColors.textSecondary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                PriceSparkline(
                                    avg30 = pokeWalletPrices.eurAvg30 ?: 0.0,
                                    avg7 = pokeWalletPrices.eurAvg7 ?: 0.0,
                                    avg1 = pokeWalletPrices.eurAvg1 ?: 0.0
                                )
                            }

                            if (pokeWalletPrices.eurLow != null) {
                                HorizontalDivider(color = AppColors.textMuted.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 2.dp))
                                DetailInfoRow(AppLocale.minPrice, "\u20AC${String.format("%.2f", pokeWalletPrices.eurLow)}")
                            }

                        }

                        if (pokeWalletPrices.usdMarket != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AppColors.card)
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("\uD83C\uDDFA\uD83C\uDDF8", fontSize = 14.sp)
                                    Text("TCGPlayer", color = AppColors.textSecondary, fontSize = 13.sp)
                                }
                                Text(
                                    "\$${String.format("%.2f", pokeWalletPrices.usdMarket)}",
                                    color = AppColors.textPrimary,
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
                    Text(labels[i], color = AppColors.textMuted, fontSize = 10.sp)
                    Text("\u20AC${String.format("%.2f", v)}", color = AppColors.textSecondary, fontSize = 10.sp)
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
                .background(AppColors.card)
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
                    color = AppColors.textPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = AppColors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(AppColors.surface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option,
                            color = if (option == selected) AppColors.blue else AppColors.textPrimary,
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
        Text(text = label, color = AppColors.textMuted, fontSize = 13.sp)
        Text(text = value, color = AppColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CompactMarketplaceHeaderButtonBottomSheet(
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

/** Vedi [TypeColors]: i colori dei tipi stanno tutti in un punto solo. */
@Composable
fun getTypeColorForTcg(type: String?): Color = TypeColors.of(type)
