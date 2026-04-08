package com.example.pokevault.ui.premium

import android.app.Activity
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pokevault.data.billing.PremiumManager
import com.example.pokevault.ui.theme.*
import com.example.pokevault.util.AppLocale

@Composable
fun PremiumScreen(
    onBack: () -> Unit
) {
    val premiumManager = remember { PremiumManager.getInstance() }
    val isPremium by premiumManager.isPremium.collectAsState()
    val purchaseState by premiumManager.purchaseState.collectAsState()
    val products by premiumManager.products.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
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
                        .background(DarkCard)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = TextWhite)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "PokeVault Premium",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hero section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(StarGold, StarGold.copy(alpha = 0.6f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = DarkBackground,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isPremium) {
                    Text(
                        text = AppLocale.premiumActiveTitle,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = StarGold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = AppLocale.premiumActiveSubtitle,
                        fontSize = 14.sp,
                        color = TextGray,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = AppLocale.premiumTitle,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = TextWhite,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = AppLocale.premiumSubtitle,
                        fontSize = 14.sp,
                        color = TextGray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Feature comparison
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkCard)
                    .padding(20.dp)
            ) {
                Text(
                    text = AppLocale.premiumFeaturesTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )

                Spacer(modifier = Modifier.height(16.dp))

                FeatureRow(
                    icon = Icons.Default.Style,
                    feature = AppLocale.premiumFeatureDeckFree,
                    isFree = true
                )
                FeatureRow(
                    icon = Icons.Default.Layers,
                    feature = AppLocale.premiumFeatureDeckPremium,
                    isFree = false
                )
                FeatureRow(
                    icon = Icons.Default.Visibility,
                    feature = AppLocale.premiumFeatureMetaFree,
                    isFree = true
                )
                FeatureRow(
                    icon = Icons.Default.AllInclusive,
                    feature = AppLocale.premiumFeatureMetaPremium,
                    isFree = false
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
                        color = TextWhite
                    )

                    // Monthly plan
                    val monthlyProduct = premiumManager.getMonthlyProduct()
                    val monthlyPrice = monthlyProduct?.subscriptionOfferDetails
                        ?.firstOrNull()?.pricingPhases?.pricingPhaseList
                        ?.firstOrNull()?.formattedPrice ?: "1,00 €"

                    PlanCard(
                        title = AppLocale.premiumMonthly,
                        price = "${AppLocale.premiumPriceMonthly(monthlyPrice)}",
                        isHighlighted = false,
                        onClick = {
                            if (activity != null && monthlyProduct != null) {
                                premiumManager.launchPurchaseFlow(activity, monthlyProduct)
                            }
                        }
                    )

                    // Annual plan
                    val annualProduct = premiumManager.getAnnualProduct()
                    val annualPrice = annualProduct?.subscriptionOfferDetails
                        ?.firstOrNull()?.pricingPhases?.pricingPhaseList
                        ?.firstOrNull()?.formattedPrice ?: "10,00 €"

                    PlanCard(
                        title = AppLocale.premiumAnnual,
                        price = "${AppLocale.premiumPriceAnnual(annualPrice)}",
                        badge = AppLocale.premiumSaveBadge,
                        isHighlighted = true,
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
                        tint = BlueCard,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppLocale.premiumRestore,
                        color = BlueCard,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legal footnote
            Text(
                text = AppLocale.premiumLegalNote,
                color = TextMuted,
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
                    CircularProgressIndicator(color = StarGold)
                }
            }
            is PremiumManager.PurchaseState.Success -> {
                LaunchedEffect(Unit) {
                    premiumManager.resetPurchaseState()
                }
            }
            is PremiumManager.PurchaseState.Error -> {
                val error = (purchaseState as PremiumManager.PurchaseState.Error).message
                LaunchedEffect(error) {
                    premiumManager.resetPurchaseState()
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    feature: String,
    isFree: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isFree) TextMuted else StarGold,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = feature,
            fontSize = 13.sp,
            color = if (isFree) TextGray else TextWhite,
            modifier = Modifier.weight(1f)
        )
        Icon(
            if (isFree) Icons.Default.CheckCircleOutline else Icons.Default.Stars,
            contentDescription = null,
            tint = if (isFree) GreenCard else StarGold,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    badge: String? = null,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isHighlighted) StarGold else DarkSurface
    val bgColor = if (isHighlighted) StarGold.copy(alpha = 0.08f) else DarkCard

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
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
                        color = TextWhite
                    )
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = StarGold,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = badge,
                                color = DarkBackground,
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
                    color = if (isHighlighted) StarGold else TextGray
                )
            }

            Icon(
                Icons.Default.ArrowForward,
                contentDescription = null,
                tint = if (isHighlighted) StarGold else TextMuted,
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
        containerColor = DarkSurface,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = StarGold,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                text = title,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = message,
                color = TextGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onUpgrade,
                colors = ButtonDefaults.buttonColors(containerColor = StarGold),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = AppLocale.premiumUpgradeButton,
                    color = DarkBackground,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppLocale.cancel, color = TextMuted)
            }
        }
    )
}
