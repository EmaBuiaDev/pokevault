package com.emabuia.pokevault.ui.premium

import android.app.Activity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.BuildConfig
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale

@Composable
fun PremiumScreen(
    onBack: () -> Unit,
    onNavigateToGiftCodes: () -> Unit = {}
) {
    val premiumManager = remember { PremiumManager.getInstance() }
    val isPremium by premiumManager.isPremium.collectAsStateWithLifecycle()
    val giftUntilMs by premiumManager.giftUntilMs.collectAsStateWithLifecycle()
    val purchaseState by premiumManager.purchaseState.collectAsStateWithLifecycle()
    val billingProblem by premiumManager.billingProblem.collectAsStateWithLifecycle()
    val subscriptionClaimed by premiumManager.subscriptionClaimedByOtherAccount.collectAsStateWithLifecycle()
    val products by premiumManager.products.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AppColors.card)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = AppColors.textPrimary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "PokeVault Premium",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Intestazione: un riquadro pieno al posto dell'icona sola.
            PremiumHero(
                isPremium = isPremium,
                subtitle = when {
                    !isPremium -> AppLocale.premiumSubtitle
                    // Un mese regalo e un abbonamento danno lo stesso
                    // accesso ma non la stessa cosa: dire "attivo" e basta
                    // a chi ha un regalo gli nasconde che ha una scadenza.
                    giftUntilMs > System.currentTimeMillis() -> AppLocale.giftActiveUntil(formatGiftDate(giftUntilMs))
                    else -> AppLocale.premiumActiveSubtitle
                },
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // TradeRadar, la novita' della 3.1.6: prima di tutto il resto.
            if (BuildConfig.TRADE_ENABLED) {
                Spacer(modifier = Modifier.height(14.dp))
                TradeRadarHighlight(modifier = Modifier.padding(horizontal = 20.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Cosa sblocca il Premium, con quanto si ha gratis: prima erano
            // sedici righe alternate gratis/premium, ora una tessera per cosa.
            Text(
                text = AppLocale.premiumUnlockTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.textPrimary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            val benefits = buildList {
                add(Benefit(Icons.Default.Layers, AppLocale.premiumBenefitDecks, AppLocale.premiumFreeAmount(AppLocale.premiumFreeOne)))
                add(Benefit(Icons.Default.PhotoAlbum, AppLocale.premiumBenefitAlbums, AppLocale.premiumFreeAmount(AppLocale.premiumFreeOne)))
                add(Benefit(Icons.Default.Flag, AppLocale.premiumBenefitGoalAlbums, AppLocale.premiumFreeAmount(AppLocale.premiumFreeOne)))
                add(Benefit(Icons.Default.Favorite, AppLocale.premiumBenefitWishlists, AppLocale.premiumFreeAmount(AppLocale.premiumFreeOne)))
                add(Benefit(Icons.Default.EmojiEvents, AppLocale.premiumBenefitTournaments, AppLocale.premiumFreeAmount(AppLocale.premiumFreeOne)))
                add(Benefit(Icons.Default.Visibility, AppLocale.premiumBenefitMeta, AppLocale.premiumFreeAmount(AppLocale.premiumFreeMetaViews)))
                add(Benefit(Icons.Default.Casino, AppLocale.premiumBenefitHandSim, AppLocale.premiumFreeAmount(AppLocale.premiumFreeHandSim)))
                add(Benefit(Icons.Default.Share, AppLocale.premiumBenefitExport, AppLocale.premiumOnly))
                add(Benefit(Icons.Default.CatchingPokemon, AppLocale.premiumBenefitHomeSprite, AppLocale.premiumOnly))
                if (BuildConfig.TRADE_ENABLED) {
                    add(Benefit(Icons.Default.AutoAwesome, AppLocale.premiumBenefitAvatars, AppLocale.premiumFreeAmount(AppLocale.premiumFreeAvatars)))
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                benefits.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { benefit ->
                            BenefitTile(benefit, isPremium = isPremium, modifier = Modifier.weight(1f))
                        }
                        // Una tessera sola nell'ultima riga resta larga meta'.
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Chi arriva qui e non vuole pagare ha comunque una strada: un
            // amico può regalargli un mese. Il collegamento sta dopo la lista,
            // non prima, per non trasformare la schermata Premium in una
            // caccia al codice.
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.card)
                    .border(
                        1.dp,
                        AppColors.gold.copy(alpha = 0.22f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable(onClick = onNavigateToGiftCodes)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CardGiftcard,
                    contentDescription = null,
                    tint = AppColors.gold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = AppLocale.giftSettingsLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.textPrimary
                    )
                    Text(
                        text = AppLocale.giftSettingsSubtitle,
                        fontSize = 12.sp,
                        color = AppColors.textMuted
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = AppColors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (!isPremium) {
                Spacer(modifier = Modifier.height(28.dp))

                // Subscription plans
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = AppLocale.premiumChoosePlan,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.textPrimary
                    )

                    // Ha un abbonamento sul telefono ma appartiene a un altro
                    // account: senza dirlo, leggerebbe solo "non sei premium"
                    // con un addebito attivo sul Play Store.
                    if (subscriptionClaimed) {
                        Surface(
                            color = AppColors.gold.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, AppColors.gold.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = AppLocale.billingClaimedTitle,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = AppLocale.billingClaimedBody,
                                    fontSize = 12.sp,
                                    color = AppColors.textSecondary,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    // Il servizio non risponde: si dice qui, accanto ai piani
                    // che non funzionano, invece di gridarlo con una snackbar
                    // appena si apre la schermata.
                    billingProblem?.let { problem ->
                        Surface(
                            color = AppColors.card,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, AppColors.textMuted.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = AppLocale.billingUnavailableTitle,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = billingProblemMessage(problem),
                                    fontSize = 12.sp,
                                    color = AppColors.textMuted,
                                    lineHeight = 17.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(
                                    onClick = { premiumManager.retryBillingConnection() },
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = AppLocale.billingRetry,
                                        color = AppColors.blue,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    // Monthly plan
                    val monthlyProduct = premiumManager.getMonthlyProduct()
                    val monthlyPrice = premiumManager.getBasePlanFormattedPrice(monthlyProduct)
                        ?: "3,00 €"

                    PlanCard(
                        title = AppLocale.premiumMonthly,
                        price = "${AppLocale.premiumPriceMonthly(monthlyPrice)}",
                        isHighlighted = false,
                        enabled = monthlyProduct != null && billingProblem == null,
                        onClick = {
                            if (activity != null && monthlyProduct != null) {
                                premiumManager.launchPurchaseFlow(activity, monthlyProduct)
                            }
                        }
                    )

                    // Annual plan
                    val annualProduct = premiumManager.getAnnualProduct()
                    val annualPrice = premiumManager.getBasePlanFormattedPrice(annualProduct)
                        ?: "20,00 €"

                    PlanCard(
                        title = AppLocale.premiumAnnual,
                        price = "${AppLocale.premiumPriceAnnual(annualPrice)}",
                        badge = AppLocale.premiumSaveBadge,
                        isHighlighted = true,
                        enabled = annualProduct != null && billingProblem == null,
                        onClick = {
                            if (activity != null && annualProduct != null) {
                                premiumManager.launchPurchaseFlow(activity, annualProduct)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Restore purchases
                TextButton(
                    onClick = { premiumManager.restorePurchases() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = null,
                        tint = AppColors.blue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppLocale.premiumRestore,
                        color = AppColors.blue,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legal footnote
            Text(
                text = AppLocale.premiumLegalNote,
                color = AppColors.textMuted,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Purchase state overlay
        when (purchaseState) {
            is PremiumManager.PurchaseState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppColors.gold)
                }
            }
            is PremiumManager.PurchaseState.Success -> {
                // Prima l'esito veniva azzerato senza mostrare nulla: un acquisto
                // riuscito e uno fallito erano indistinguibili per l'utente.
                LaunchedEffect(Unit) {
                    snackbarHostState.showSnackbar(AppLocale.premiumPurchaseSuccess)
                    premiumManager.resetPurchaseState()
                }
            }
            is PremiumManager.PurchaseState.Pending -> {
                LaunchedEffect(Unit) {
                    snackbarHostState.showSnackbar(AppLocale.premiumPurchasePending)
                    premiumManager.resetPurchaseState()
                }
            }
            is PremiumManager.PurchaseState.NotAcknowledged -> {
                LaunchedEffect(Unit) {
                    snackbarHostState.showSnackbar(AppLocale.premiumPurchaseNotAcknowledged)
                    premiumManager.resetPurchaseState()
                }
            }
            is PremiumManager.PurchaseState.Failed -> {
                // Arriva qui SOLO un acquisto che l'utente ha avviato davvero.
                // I guasti del servizio che capitano all'avvio non passano piu'
                // di qui: stanno in billingProblem, e si vedono come riga
                // spenta accanto ai piani invece che come snackbar allarmista.
                val problem = (purchaseState as PremiumManager.PurchaseState.Failed).problem
                LaunchedEffect(problem) {
                    snackbarHostState.showSnackbar(
                        AppLocale.premiumPurchaseError(billingProblemMessage(problem))
                    )
                    premiumManager.resetPurchaseState()
                }
            }
            else -> {}
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** Il guasto, nella lingua scelta dall'utente. */
private fun billingProblemMessage(problem: PremiumManager.Companion.BillingProblem): String =
    when (problem) {
        PremiumManager.Companion.BillingProblem.DISCONNECTED -> AppLocale.billingProblemDisconnected
        PremiumManager.Companion.BillingProblem.NETWORK -> AppLocale.billingProblemNetwork
        PremiumManager.Companion.BillingProblem.UNAVAILABLE -> AppLocale.billingProblemUnavailable
        PremiumManager.Companion.BillingProblem.MISCONFIGURED -> AppLocale.billingProblemMisconfigured
        PremiumManager.Companion.BillingProblem.OTHER -> AppLocale.billingProblemOther
    }

private fun formatGiftDate(epochMs: Long): String =
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
        .format(java.util.Date(epochMs))

private data class Benefit(val icon: ImageVector, val title: String, val freeNote: String)

/** L'intestazione: riquadro oro-arancio, corona che respira piano (ferma se le animazioni sono spente). */
@Composable
private fun PremiumHero(isPremium: Boolean, subtitle: String, modifier: Modifier = Modifier) {
    val breathing = if (AppMotion.enabled) {
        val transition = rememberInfiniteTransition(label = "premiumCrown")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
            label = "premiumCrownScale"
        )
        value
    } else {
        1f
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(AppColors.gold, AppColors.orange)))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(68.dp)
                .graphicsLayer {
                    scaleX = breathing
                    scaleY = breathing
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
        ) {
            Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (isPremium) AppLocale.premiumActiveTitle else AppLocale.premiumTitle,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.88f),
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
    }
}

/** TradeRadar, la funzione di punta: un riquadro coi colori del radar. */
@Composable
private fun TradeRadarHighlight(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(AppColors.green, AppColors.blue)))
            .padding(16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Icon(Icons.Default.Radar, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(AppLocale.premiumTradeRadarTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(3.dp))
            Text(AppLocale.premiumTradeRadarText, fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f), lineHeight = 16.sp)
        }
    }
}

/**
 * Una cosa che il Premium sblocca. Bordo sempre visibile: nel tema chiaro
 * card e sfondo sono lo stesso bianco, e senza bordo la tessera sparirebbe.
 */
@Composable
private fun BenefitTile(benefit: Benefit, isPremium: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .border(1.dp, AppColors.gold.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(AppColors.gold.copy(alpha = 0.15f))
        ) {
            Icon(benefit.icon, contentDescription = null, tint = AppColors.gold, modifier = Modifier.size(19.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = benefit.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.textPrimary,
            lineHeight = 17.sp,
            minLines = 2
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isPremium) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AppColors.green, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = benefit.freeNote,
                fontSize = 11.sp,
                color = AppColors.textMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    badge: String? = null,
    isHighlighted: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    // Un piano che non si puo' comprare deve sembrare non comprabile: prima il
    // tocco partiva comunque e non succedeva niente, senza spiegazioni.
    val borderColor = when {
        !enabled -> AppColors.surface
        isHighlighted -> AppColors.gold
        else -> AppColors.surface
    }
    val bgColor = if (isHighlighted && enabled) AppColors.gold.copy(alpha = 0.08f) else AppColors.card

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.textPrimary
                    )
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = AppColors.gold,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = badge,
                                color = AppColors.background,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = price,
                    fontSize = 13.sp,
                    color = if (isHighlighted) AppColors.gold else AppColors.textSecondary
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (isHighlighted) AppColors.gold else AppColors.textMuted,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun PremiumRequiredDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.surface,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = AppColors.gold,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                text = title,
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = message,
                color = AppColors.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onUpgrade,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.gold),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = AppLocale.premiumUpgradeButton,
                    color = AppColors.background,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppLocale.cancel, color = AppColors.textMuted)
            }
        }
    )
}
