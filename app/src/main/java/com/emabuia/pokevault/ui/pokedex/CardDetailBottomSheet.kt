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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.emabuia.pokevault.ui.components.CardVariants
import com.emabuia.pokevault.ui.components.OwnedVariantBadges
import com.emabuia.pokevault.ui.components.RaritySymbolIcon
import com.emabuia.pokevault.util.RarityUtils.getRarityInfo

/**
 * La scheda di una carta, aperta dal Pokedex e dalle wishlist.
 *
 * La colonna scorre, il tasto che aggiunge la carta no: sta in una barra
 * fissa in fondo. Prima era una pastiglia stretta in fila con le info sopra
 * l'immagine, messa li' perche' piu' in basso sarebbe finita sotto la piega;
 * il risultato era che l'azione principale della schermata sembrava un
 * accessorio, e stava dentro una riga che scorre di lato. Fissa in fondo si
 * raggiunge da qualunque punto della scheda, e il form sotto l'immagine ha
 * tutto lo spazio che gli serve.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    isLoadingPokeWalletPrices: Boolean = false,
    /**
     * Quali stampe di QUESTA carta si hanno gia'. Vuoto dove il chiamante non
     * lo sa: li' le pastiglie non dicono nulla, invece di dire "non ce l'hai"
     * a chi ce l'ha.
     */
    ownedVariants: Set<String> = emptySet()
) {
    val rarityInfo = getRarityInfo(card.rarity)
    val context = LocalContext.current

    // Varianti disponibili (API + fallback per rarità)
    val availableVariants = remember(card) {
        CardOptions.getVariantsForCard(card.tcgplayer?.prices?.keys ?: emptySet(), card.rarity, card.set?.releaseDate)
    }
    val resolvedLanguageOptions = remember(languageOptions) {
        languageOptions.distinct().ifEmpty { CardOptions.LANGUAGES }
    }
    val resolvedDefaultLanguage = remember(card.id, defaultLanguage, resolvedLanguageOptions) {
        defaultLanguage?.takeIf { it in resolvedLanguageOptions } ?: resolvedLanguageOptions.first()
    }

    // State form.
    //
    // Chiavi su card.id, non `remember` nudo: scorrendo col dito da una carta
    // all'altra la scheda non si ricrea, e' la stessa composizione con un
    // `card` diverso. Senza chiave la stampa scelta restava quella di prima
    // anche dove non esiste -- si apriva una Ultra Rara, che ha la sola Holo,
    // e partiva selezionata la "Reverse" rimasta dalla carta precedente.
    var selectedVariant by remember(card.id) {
        mutableStateOf(
            // Si parte da una stampa che manca: chi riapre una carta che ha
            // gia' quasi sempre sta aggiungendo l'altra versione.
            availableVariants.firstOrNull { it !in ownedVariants }
                ?: availableVariants.firstOrNull()
                ?: "Normal"
        )
    }
    var quantity by remember(card.id) { mutableIntStateOf(1) }
    var selectedCondition by remember(card.id) { mutableStateOf("Near Mint") }
    var selectedLanguage by remember(card.id, resolvedDefaultLanguage) { mutableStateOf(resolvedDefaultLanguage) }

    val currentCardIndex = remember(card.id, cardList) { cardList.indexOfFirst { it.id == card.id } }
    val canBrowse = currentCardIndex >= 0 && cardList.size > 1

    // Prezzo per variante selezionata
    val variantKey = CardOptions.getVariantApiKey(selectedVariant)
    val price = card.tcgplayer?.prices?.get(variantKey)?.market
        ?: card.cardmarket?.prices?.lowPrice
        ?: card.cardmarket?.prices?.averageSellPrice

    val illustrator = card.artist?.trim()?.takeIf { it.isNotBlank() }

    // Calcolati qui in cima e non dentro il blocco dei prezzi live: i link ai
    // marketplace stanno fra le pastiglie sotto l'immagine, dove si vedono
    // senza scorrere. Quelli di PokeWallet arrivano dopo il caricamento,
    // quelli della carta inglese di appoggio ci sono gia' all'apertura.
    //
    // Ogni pastiglia si mostra solo quando il suo indirizzo c'e' davvero.
    // Prima comparivano spente durante il caricamento, per farle vedere
    // accendersi: dove i prezzi non arrivavano l'effetto era che apparivano e
    // sparivano subito. Un indirizzo, una volta noto, non torna mai ignoto --
    // il fallback sulla carta inglese regge anche se PokeWallet non risponde
    // -- quindi cosi' le pastiglie possono solo comparire, mai svanire.
    val cardMarketUrl = pokeWalletPrices?.cardMarketUrl?.takeIf { it.isNotBlank() }
        ?: card.cardmarket?.url?.takeIf { it.isNotBlank() }
    val tcgPlayerUrl = pokeWalletPrices?.tcgPlayerUrl?.takeIf { it.isNotBlank() }
        ?: card.tcgplayer?.url?.takeIf { it.isNotBlank() }

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
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 14.dp, bottom = 20.dp)
                ) {
                    // ── Header: nome, numero, set ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = card.name,
                                color = AppColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                lineHeight = 27.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 3.dp)
                            ) {
                                if (card.types != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(getTypeColorForTcg(card.types.firstOrNull()))
                                    )
                                }
                                Text("#${card.number}", color = AppColors.textMuted, fontSize = 13.sp)
                                if (card.set != null) {
                                    Text("·", color = AppColors.textMuted, fontSize = 13.sp)
                                    Text(
                                        text = card.set.name,
                                        color = AppColors.textSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Per uscire c'era solo il tocco fuori o il tasto
                        // indietro: da quando la scheda e' alta quasi quanto
                        // lo schermo, il "fuori" e' una striscia sottile.
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AppColors.card)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = AppLocale.close,
                                tint = AppColors.textMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── Immagine carta ──
                    //
                    // Il gesto sta sul Box a tutta larghezza e l'immagine
                    // dentro e' piu' stretta: si puo' scorrere partendo anche
                    // dal margine, e restano i due lati liberi dove mettere le
                    // frecce. Frecce e contatore stanno SOPRA l'immagine e non
                    // in una riga sotto: quella riga costava quaranta punti di
                    // altezza, ed e' lo spazio che serviva per far salire i
                    // link dei marketplace sopra la piega.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .let { baseModifier ->
                                if (canBrowse) {
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
                            modifier = Modifier
                                .fillMaxWidth(0.78f)
                                .aspectRatio(0.72f)
                                .clip(RoundedCornerShape(14.dp))
                        )

                        // Frecce e contatore, sopra l'immagine.
                        //
                        // Che si potesse passare da una carta all'altra col
                        // dito non lo diceva niente: il gesto c'era gia', qui
                        // si vede. Le frecce stanno ai bordi del Box, cioe'
                        // nel margine accanto all'immagine; il contatore in
                        // basso, su una pastiglia scura che lo tiene leggibile
                        // sopra qualunque illustrazione.
                        if (canBrowse) {
                            CardBrowseArrow(
                                icon = Icons.Default.ChevronLeft,
                                enabled = currentCardIndex > 0,
                                contentDescription = AppLocale.previousCard,
                                modifier = Modifier.align(Alignment.CenterStart),
                                onClick = { onCardChange(cardList[currentCardIndex - 1]) }
                            )
                            CardBrowseArrow(
                                icon = Icons.Default.ChevronRight,
                                enabled = currentCardIndex < cardList.lastIndex,
                                contentDescription = AppLocale.nextCard,
                                modifier = Modifier.align(Alignment.CenterEnd),
                                onClick = { onCardChange(cardList[currentCardIndex + 1]) }
                            )
                            Text(
                                text = "${currentCardIndex + 1} / ${cardList.size}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── Info pills ──
                    //
                    // Tutto quello che si dice della carta sta qui, della
                    // stessa misura: rarita', prezzo, illustratore e i link ai
                    // marketplace. L'illustratore non e' piu' in corsivo sotto
                    // l'immagine -- e' un'informazione come le altre -- e i
                    // link non sono piu' due tasti larghi su una riga propria.
                    //
                    // FlowRow e non una riga che scorre di lato: le pastiglie
                    // sono cinque e in una riga sola le ultime -- cioe' proprio
                    // i link -- finirebbero fuori schermo, da cercare col dito.
                    // Andando a capo si vedono tutte.
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // rarityInfo.label, non la stringa grezza: quella
                        // arriva in inglese dal catalogo ("Holo Rare") e
                        // quando manca lasciava un "Sconosciuto" scritto
                        // a mano, fuori da AppLocale.
                        InfoPill(
                            icon = "",
                            text = rarityInfo.label,
                            color = if (rarityInfo.adaptive) AppColors.textPrimary else rarityInfo.color,
                            leading = { RaritySymbolIcon(rarityInfo, size = 12.dp) }
                        )
                        if (price != null && price > 0) {
                            InfoPill(icon = "💰", text = "${"%.2f".format(price)} €", color = AppColors.green)
                        }
                        if (illustrator != null) {
                            InfoPill(
                                icon = "",
                                text = illustrator,
                                color = AppColors.lavender,
                                leading = {
                                    Icon(
                                        imageVector = Icons.Default.Brush,
                                        contentDescription = AppLocale.illustrator,
                                        tint = AppColors.lavender,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            )
                        }

                        // I link: erano in fondo, nell'intestazione dei prezzi
                        // live, e per aprire Cardmarket si scorreva tutta la
                        // scheda ogni volta. Qui si vedono appena si apre.
                        if (cardMarketUrl != null) {
                            MarketLinkPill(label = "Cardmarket") {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cardMarketUrl)))
                            }
                        }
                        if (tcgPlayerUrl != null) {
                            MarketLinkPill(label = "TCGplayer") {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tcgPlayerUrl)))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // ── Stato possesso ──
                    if (isOwned) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(AppColors.green.copy(alpha = 0.10f))
                                .border(1.dp, AppColors.green.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.green),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(17.dp))
                            }
                            Text(
                                text = AppLocale.inCollection,
                                color = AppColors.green,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            // Le collezioni vecchie possono non avere la
                            // stampa salvata: li' non si mostra nulla, invece
                            // di disegnare una pastiglia inventata.
                            if (ownedVariants.isNotEmpty()) {
                                OwnedVariantBadges(variants = ownedVariants, size = 18, fontSize = 10)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // ── Form: com'e' la copia che si sta aggiungendo ──
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppColors.card)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // La stampa si sceglie con delle pastiglie, non con un
                        // menu a tendina: sono due o tre, ci stanno in riga, e
                        // ognuna sa dire se quella stampa e' gia' in
                        // collezione -- in una tendina chiusa non si vedeva.
                        FormField(label = AppLocale.printLabel) {
                            if (availableVariants.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    availableVariants.forEach { variant ->
                                        PrintChoiceChip(
                                            variant = variant,
                                            selected = variant == selectedVariant,
                                            alreadyOwned = variant in ownedVariants,
                                            onClick = { selectedVariant = variant }
                                        )
                                    }
                                }
                            } else {
                                // Una stampa sola non e' una scelta, ma va
                                // detto lo stesso quale finira' in collezione.
                                StaticFieldValue(text = CardVariants.label(selectedVariant))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FormField(label = AppLocale.quantity, modifier = Modifier.weight(1f)) {
                                QuantityStepper(
                                    quantity = quantity,
                                    onDecrease = { if (quantity > 1) quantity-- },
                                    onIncrease = { quantity++ }
                                )
                            }
                            FormField(label = AppLocale.condition, modifier = Modifier.weight(1f)) {
                                OptionSelector(
                                    options = CardOptions.CONDITIONS,
                                    selected = selectedCondition,
                                    onSelect = { selectedCondition = it }
                                )
                            }
                        }

                        FormField(label = AppLocale.languageLabel) {
                            // Tutte le lingue, anche aprendo una carta dalla
                            // sezione italiana: l'immagine che si vede non
                            // decide in che lingua e' la copia che si ha in
                            // mano. Parte selezionata quella del set.
                            OptionSelector(
                                options = resolvedLanguageOptions,
                                selected = selectedLanguage,
                                onSelect = { selectedLanguage = it }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

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
                        if (card.subtypes != null) DetailInfoRow(
                            "Sottotipo",
                            card.subtypes.joinToString(", ") { AppLocale.translateSubtype(it) }
                        )
                        if (card.hp != null) DetailInfoRow("HP", card.hp)
                        if (card.types != null) DetailInfoRow("Tipo", card.types.joinToString(", "))
                        if (card.rarity != null) DetailInfoRow("Rarità", card.rarity)
                        DetailInfoRow("Numero", "#${card.number}")
                        if (illustrator != null) DetailInfoRow(AppLocale.illustrator, illustrator)
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

                    Spacer(modifier = Modifier.height(20.dp))

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
                    } else if (pokeWalletPrices != null && pokeWalletPrices.hasEurPrices) {
                        // Qui l'intestazione e' solo il titolo: i tasti verso
                        // Cardmarket e TCGplayer sono saliti sotto l'immagine,
                        // e tenerne una seconda copia qui voleva dire due
                        // strade per la stessa cosa nella stessa schermata.
                        Text(
                            text = AppLocale.livePrices,
                            color = AppColors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
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
                                            "€${String.format("%.2f", mainEurPrice)}",
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
                                            "€${String.format("%.2f", pokeWalletPrices.eurTrend)}",
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
                                DetailInfoRow(AppLocale.minPrice, "€${String.format("%.2f", pokeWalletPrices.eurLow)}")
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
                                    Text("🇺🇸", fontSize = 14.sp)
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
                    }
                }

                // ── La barra fissa: l'azione della schermata ──
                CollectionActionBar(
                    isOwned = isOwned,
                    isLoading = isLoading,
                    quantity = quantity,
                    onAdd = {
                        onAddCard(selectedVariant, quantity, selectedCondition, selectedLanguage)
                        onDismiss()
                    },
                    onRemove = {
                        onRemoveCard()
                        onDismiss()
                    }
                )
            }
        }
    }
}

/**
 * La barra in fondo alla scheda: aggiungi a sinistra, rimuovi a destra.
 *
 * Il cestino compare solo su una carta che si ha gia': su una che manca non
 * c'e' niente da togliere.
 */
@Composable
private fun CollectionActionBar(
    isOwned: Boolean,
    isLoading: Boolean,
    quantity: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    val label = when {
        quantity > 1 -> AppLocale.addCopies(quantity)
        isOwned -> AppLocale.addCopy
        else -> AppLocale.addToCollection
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.background)
    ) {
        HorizontalDivider(color = AppColors.textMuted.copy(alpha = 0.12f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { if (!isLoading) onAdd() },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isOwned) {
                OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .width(52.dp)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(0.dp),
                    border = BorderStroke(1.dp, AppColors.red.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = AppLocale.removeFromCollection,
                        tint = AppColors.red,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** L'etichetta piccola sopra un controllo del form, sempre della stessa misura. */
@Composable
private fun FormField(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = AppColors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        content()
    }
}

// I campi del form (tendine, contatore, pastiglie) stanno dentro un riquadro
// AppColors.card e si disegnano su AppColors.background con questo filo di
// bordo -- NON su AppColors.surface: nel tema chiaro `surface` e `card` sono
// lo stesso bianco, e un campo su `surface` dentro il riquadro sparirebbe,
// lasciando una lastra bianca senza confini. `background` invece stacca dalla
// card in tutti e due i temi, e il bordo la chiude comunque.
private const val FIELD_BORDER_ALPHA = 0.18f

/** Un campo che non si puo' cambiare: la forma di [OptionSelector], senza freccia. */
@Composable
private fun StaticFieldValue(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.background)
            .border(1.dp, AppColors.textMuted.copy(alpha = FIELD_BORDER_ALPHA), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 13.dp)
    ) {
        Text(
            text = text,
            color = AppColors.textSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Una stampa fra cui scegliere, misura scheda.
 *
 * Il colore e' quello che la stampa ha gia' in griglia ([CardVariants.color]),
 * cosi' la reverse e' lo stesso azzurro in tutta l'app. Quella che si ha gia'
 * resta scegliibile -- una seconda copia e' legittima -- ma porta la spunta.
 */
@Composable
private fun PrintChoiceChip(
    variant: String,
    selected: Boolean,
    alreadyOwned: Boolean,
    onClick: () -> Unit
) {
    val tint = CardVariants.color(variant)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) tint.copy(alpha = 0.22f) else AppColors.background)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) tint else AppColors.textMuted.copy(alpha = FIELD_BORDER_ALPHA),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (alreadyOwned) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = AppLocale.alreadyOwnedPrint,
                tint = AppColors.green,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = CardVariants.label(variant),
            color = if (selected) AppColors.textPrimary else AppColors.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/** Il contatore delle copie: meno, numero, piu'. Alto quanto [OptionSelector]. */
@Composable
private fun QuantityStepper(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.background)
            .border(1.dp, AppColors.textMuted.copy(alpha = FIELD_BORDER_ALPHA), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton(
            icon = Icons.Default.Remove,
            contentDescription = "-",
            enabled = quantity > 1,
            // Una velatura del colore del testo, non una tinta fissa: cosi'
            // schiarisce col tema scuro e scurisce con quello chiaro, e il
            // tasto si vede sopra il campo in tutti e due.
            container = AppColors.textPrimary.copy(alpha = 0.09f),
            tint = AppColors.textPrimary,
            onClick = onDecrease
        )
        Text(
            text = "$quantity",
            color = AppColors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )
        StepperButton(
            icon = Icons.Default.Add,
            contentDescription = "+",
            enabled = true,
            container = AppColors.blue,
            tint = Color.White,
            onClick = onIncrease
        )
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    container: Color,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) container else container.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier.size(17.dp)
        )
    }
}

/**
 * La freccia per la carta prima o dopo, spenta a inizio e fine lista.
 *
 * Sta al bordo del riquadro dell'immagine, quindi di norma nel margine
 * accanto alla carta; sugli schermi stretti il margine si assottiglia e la
 * freccia finisce a cavallo dell'illustrazione. Per questo il fondo e' nero
 * velato e non [AppColors.card]: sopra una carta chiara un cerchio chiaro
 * sparirebbe.
 */
@Composable
private fun CardBrowseArrow(
    icon: ImageVector,
    enabled: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = if (enabled) 0.45f else 0.18f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 0.95f else 0.35f),
            modifier = Modifier.size(19.dp)
        )
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
                    Text("€${String.format("%.2f", v)}", color = AppColors.textSecondary, fontSize = 10.sp)
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
                // Vedi [FIELD_BORDER_ALPHA]: `surface` qui sparirebbe col tema chiaro.
                .background(AppColors.background)
                .border(1.dp, AppColors.textMuted.copy(alpha = FIELD_BORDER_ALPHA), RoundedCornerShape(12.dp))
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
fun InfoPill(
    icon: String,
    text: String,
    color: Color,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    bordered: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .then(
                if (bordered) Modifier.border(1.dp, color.copy(alpha = 0.40f), RoundedCornerShape(20.dp))
                else Modifier
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // `leading` serve al segno di rarita', che e' disegnato su Canvas e
        // dentro una stringa non ci sta.
        if (leading != null) leading() else Text(text = icon, fontSize = 12.sp)
        Text(text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        trailing?.invoke()
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

/**
 * Il link a un marketplace, della stessa misura delle pastiglie accanto.
 *
 * E' un [InfoPill] con un bordo e la freccetta in coda: il bordo lo distingue
 * dalle pastiglie che sono solo informazione, perche' questa si tocca.
 */
@Composable
private fun MarketLinkPill(label: String, onClick: () -> Unit) {
    InfoPill(
        icon = "",
        text = label,
        color = AppColors.blue,
        bordered = true,
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = AppColors.blue,
                modifier = Modifier.size(11.dp)
            )
        }
    )
}

/** Vedi [TypeColors]: i colori dei tipi stanno tutti in un punto solo. */
@Composable
fun getTypeColorForTcg(type: String?): Color = TypeColors.of(type)
