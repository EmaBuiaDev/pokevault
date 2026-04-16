package com.emabuia.pokevault.ui.deck

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.data.model.MetaDeck
import com.emabuia.pokevault.data.model.MetaDeckCard
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.MetaDeckViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetaDeckSection(
    viewModel: MetaDeckViewModel,
    onImportDeck: ((MetaDeck) -> Unit)? = null,
    onPremiumRequired: () -> Unit = {}
) {
    val premiumManager = remember { PremiumManager.getInstance() }
    val selectedDeck = viewModel.selectedDeck

    if (selectedDeck != null) {
        BackHandler { viewModel.selectDeck(null) }
        MetaDeckDetailView(
            deck = selectedDeck,
            onBack = { viewModel.selectDeck(null) },
            onImport = if (onImportDeck != null) {
                { onImportDeck(selectedDeck) }
            } else null
        )
    } else {
        MetaDeckListView(
            viewModel = viewModel,
            onSelectDeck = { deck ->
                if (premiumManager.canViewMetaDeck()) {
                    premiumManager.consumeMetaDeckView()
                    viewModel.selectDeck(deck)
                } else {
                    onPremiumRequired()
                }
            }
        )
    }
}

@Composable
fun MetaDeckListView(
    viewModel: MetaDeckViewModel,
    onSelectDeck: (MetaDeck) -> Unit = { viewModel.selectDeck(it) }
) {
    val premiumManager = remember { PremiumManager.getInstance() }
    val isPremium by premiumManager.isPremium.collectAsState()
    val viewsRemaining = premiumManager.metaDeckViewsRemaining

    // Tick ogni 30s così l'indicatore "aggiornato Xm fa" si aggiorna da solo.
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }
    var rateLimitedMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Info banner: spiega da dove arrivano i dati e come sono ordinati.
        MetaInfoBanner(
            title = AppLocale.metaWinnersInfoTitle,
            body = AppLocale.metaWinnersInfoBody,
            lastUpdated = viewModel.lastUpdated,
            rateLimitMessage = rateLimitedMessage,
            tick = tick
        )

        // Format selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FormatChip(
                label = "Standard",
                selected = viewModel.selectedFormat == "standard",
                onClick = { viewModel.selectFormat("standard") }
            )
            FormatChip(
                label = "Expanded",
                selected = viewModel.selectedFormat == "expanded",
                onClick = { viewModel.selectFormat("expanded") }
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = {
                    val started = viewModel.refresh()
                    if (!started) {
                        rateLimitedMessage = AppLocale.metaRefreshCooldown(
                            viewModel.refreshCooldownSeconds
                        )
                    } else {
                        rateLimitedMessage = null
                    }
                },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(DarkCard)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Aggiorna",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Nascondi il messaggio di rate limit dopo qualche secondo.
        LaunchedEffect(rateLimitedMessage) {
            if (rateLimitedMessage != null) {
                delay(3_000)
                rateLimitedMessage = null
            }
        }

        when {
            viewModel.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = BlueCard,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Caricamento meta deck...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            viewModel.errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(40.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = RedCard,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Errore di connessione",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = viewModel.errorMessage ?: "",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.refresh() },
                            colors = ButtonDefaults.buttonColors(containerColor = BlueCard),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Riprova")
                        }
                    }
                }
            }

            viewModel.metaDecks.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(40.dp)
                    ) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = LavenderCard,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Nessun meta deck trovato",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Prova a cambiare formato o riprova pi\u00f9 tardi.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isPremium) {
                        item {
                            Surface(
                                color = StarGold.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = StarGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = AppLocale.premiumMetaDeckViewsRemaining(viewsRemaining),
                                        color = StarGold,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    items(viewModel.metaDecks, key = { it.id }) { deck ->
                        MetaDeckItem(
                            deck = deck,
                            onClick = { onSelectDeck(deck) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

/**
 * Banner informativo per le sezioni Meta / Win Tournament.
 * Spiega da dove arrivano i dati + mostra "aggiornato Xm fa".
 * Il parametro `tick` serve solo a forzare la ricomposizione periodica.
 */
@Composable
fun MetaInfoBanner(
    title: String,
    body: String,
    lastUpdated: Long?,
    rateLimitMessage: String?,
    tick: Long = 0L
) {
    val updatedLabel = remember(lastUpdated, tick) {
        if (lastUpdated == null) null
        else {
            val diffMs = System.currentTimeMillis() - lastUpdated
            val minutes = diffMs / 60_000L
            when {
                minutes < 1L -> AppLocale.metaLastUpdatedNow
                minutes < 60L -> AppLocale.metaLastUpdatedMinutes(minutes)
                else -> AppLocale.metaLastUpdatedHours(minutes / 60L)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        color = BlueCard.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = BlueCard,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = body,
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                if (updatedLabel != null || rateLimitMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (updatedLabel != null) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = updatedLabel,
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                        if (rateLimitMessage != null) {
                            if (updatedLabel != null) {
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Icon(
                                Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                tint = YellowCard,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = rateLimitMessage,
                                color = YellowCard,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FormatChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) BlueCard else DarkCard,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) TextWhite else TextMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun MetaDeckItem(
    deck: MetaDeck,
    onClick: () -> Unit
) {
    val pokemonCount = deck.cards.filter { it.type == "pokemon" }.sumOf { it.qty }
    val trainerCount = deck.cards.filter { it.type == "trainer" }.sumOf { it.qty }
    val energyCount = deck.cards.filter { it.type == "energy" }.sumOf { it.qty }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Archetype + Placement
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deck.archetype ?: "Deck Sconosciuto",
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (deck.player != null) {
                        Text(
                            text = deck.player,
                            color = LavenderCard,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                deck.placement?.let { place ->
                    PlacementBadge(placement = place)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tournament info
            if (deck.tournament != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = YellowCard,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = deck.tournament,
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Card distribution
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatMini(label = "Pok\u00e9mon", value = "$pokemonCount", color = BlueCard)
                    StatMini(label = "Trainer", value = "$trainerCount", color = PurpleCard)
                    StatMini(label = "Energy", value = "$energyCount", color = GreenCard)
                }

                // Winrate
                deck.winrate?.let { wr ->
                    Surface(
                        color = when {
                            wr >= 0.7 -> GreenCard.copy(alpha = 0.15f)
                            wr >= 0.5 -> YellowCard.copy(alpha = 0.15f)
                            else -> RedCard.copy(alpha = 0.15f)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${(wr * 100).toInt()}% WR",
                            color = when {
                                wr >= 0.7 -> GreenCard
                                wr >= 0.5 -> YellowCard
                                else -> RedCard
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Date
            deck.date?.let { date ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatDate(date),
                    color = TextMuted.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun PlacementBadge(placement: Int) {
    val (bgColor, textColor) = when (placement) {
        1 -> YellowCard.copy(alpha = 0.2f) to YellowCard
        2 -> Color(0xFFC0C0C0).copy(alpha = 0.2f) to Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32).copy(alpha = 0.2f) to Color(0xFFCD7F32)
        else -> DarkBackground.copy(alpha = 0.5f) to TextMuted
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = "#$placement",
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun StatMini(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = TextMuted,
            fontSize = 9.sp
        )
    }
}

// ══════════════════════════════════════
// DETAIL VIEW
// ══════════════════════════════════════

@Composable
fun MetaDeckDetailView(
    deck: MetaDeck,
    onBack: () -> Unit,
    onImport: (() -> Unit)? = null
) {
    val pokemonCards = deck.cards.filter { it.type == "pokemon" }
    val trainerCards = deck.cards.filter { it.type == "trainer" }
    val energyCards = deck.cards.filter { it.type == "energy" }

    val totalCards = deck.cards.sumOf { it.qty }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BlueCard.copy(alpha = 0.15f),
                            DarkBackground
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DarkCard)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Indietro",
                            tint = TextWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        deck.placement?.let { PlacementBadge(it) }

                        if (onImport != null) {
                            IconButton(
                                onClick = onImport,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(PurpleCard.copy(alpha = 0.3f))
                            ) {
                                Icon(
                                    Icons.Default.FileDownload,
                                    contentDescription = "Importa in DeckLab",
                                    tint = PurpleCard,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = deck.archetype ?: "Deck Sconosciuto",
                    color = TextWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )

                if (deck.player != null) {
                    Text(
                        text = deck.player,
                        color = LavenderCard,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tournament + Date
                if (deck.tournament != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = YellowCard,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = deck.tournament,
                            color = TextMuted,
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DeckStatChip(
                        icon = Icons.Default.CatchingPokemon,
                        label = "Pok\u00e9mon",
                        value = "${pokemonCards.sumOf { it.qty }}",
                        color = BlueCard
                    )
                    DeckStatChip(
                        icon = Icons.Default.Handyman,
                        label = "Trainer",
                        value = "${trainerCards.sumOf { it.qty }}",
                        color = PurpleCard
                    )
                    DeckStatChip(
                        icon = Icons.Default.Bolt,
                        label = "Energy",
                        value = "${energyCards.sumOf { it.qty }}",
                        color = GreenCard
                    )
                    DeckStatChip(
                        icon = Icons.Default.Layers,
                        label = "Totale",
                        value = "$totalCards",
                        color = TextWhite
                    )

                    deck.winrate?.let { wr ->
                        DeckStatChip(
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            label = "Winrate",
                            value = "${(wr * 100).toInt()}%",
                            color = when {
                                wr >= 0.7 -> GreenCard
                                wr >= 0.5 -> YellowCard
                                else -> RedCard
                            }
                        )
                    }
                }
            }
        }

        // Decklist
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (pokemonCards.isNotEmpty()) {
                item {
                    DeckCategorySection(
                        title = "POK\u00c9MON",
                        cards = pokemonCards,
                        accentColor = BlueCard
                    )
                }
            }
            if (trainerCards.isNotEmpty()) {
                item {
                    DeckCategorySection(
                        title = "TRAINER",
                        cards = trainerCards,
                        accentColor = PurpleCard
                    )
                }
            }
            if (energyCards.isNotEmpty()) {
                item {
                    DeckCategorySection(
                        title = "ENERGIA",
                        cards = energyCards,
                        accentColor = GreenCard
                    )
                }
            }
            // Bottone Importa in fondo alla decklist
            if (onImport != null) {
                item {
                    Button(
                        onClick = onImport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleCard)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importa in DeckLab", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun DeckStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text = value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = TextMuted, fontSize = 9.sp)
    }
}

@Composable
fun DeckCategorySection(
    title: String,
    cards: List<MetaDeckCard>,
    accentColor: Color
) {
    Column {
        // Category header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = Color.White.copy(alpha = 0.1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${cards.sumOf { it.qty }}",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Card list
        cards.forEach { card ->
            DeckCardRow(card = card, accentColor = accentColor)
        }
    }
}

@Composable
fun DeckCardRow(
    card: MetaDeckCard,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Quantity badge
        Surface(
            color = accentColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "${card.qty}",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Card name
        Text(
            text = card.name,
            color = TextWhite,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Set + Number
        if (card.set != null || card.number != null) {
            Text(
                text = listOfNotNull(card.set, card.number).joinToString(" "),
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

private fun formatDate(isoDate: String): String {
    return try {
        // ISO date: "2023-03-02T23:00:00.000Z" → "02/03/2023"
        val parts = isoDate.take(10).split("-")
        if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else isoDate.take(10)
    } catch (_: Exception) {
        isoDate.take(10)
    }
}
