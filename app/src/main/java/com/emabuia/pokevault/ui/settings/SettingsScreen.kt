package com.emabuia.pokevault.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.viewmodel.AuthViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    authViewModel: AuthViewModel,
    onAccountDeleted: () -> Unit,
    onLogout: () -> Unit = {},
    onNavigateToPremium: () -> Unit = {}
) {
    val context = LocalContext.current
    val premiumManager = remember { PremiumManager.getInstance() }
    val isPremium by premiumManager.isPremium.collectAsState()
    val selectedHomeSpriteId by premiumManager.selectedHomeSpriteId.collectAsState()
    val homeSpriteIds = remember { premiumManager.homeSpriteIds }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showHomeSpriteDialog by remember { mutableStateOf(false) }
    var showPremiumHomeSpriteDialog by remember { mutableStateOf(false) }
    var showCreatorSection by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
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
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = TextWhite)
                }
                Text(
                    text = AppLocale.settingsTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lingua
            SettingsItem(
                icon = Icons.Default.Language,
                title = AppLocale.languageLabel,
                subtitle = AppLocale.languageSubtitle,
                onClick = { AppLocale.toggle(context) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Privacy Policy link
            SettingsItem(
                icon = Icons.Default.PrivacyTip,
                title = AppLocale.privacyPolicyLabel,
                subtitle = AppLocale.privacyPolicySubtitle,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppLocale.privacyPolicyUrl))
                    context.startActivity(intent)
                }
            )

            // Terms of Service
            SettingsItem(
                icon = Icons.Default.Description,
                title = AppLocale.termsLabel,
                subtitle = AppLocale.termsSubtitle,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppLocale.termsUrl))
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            SocialPromoCard(
                title = AppLocale.tikTokLabel,
                subtitle = AppLocale.tikTokSubtitle,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppLocale.tikTokUrl))
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Premium section
            SettingsItem(
                icon = Icons.Default.WorkspacePremium,
                title = AppLocale.premiumSettingsLabel,
                subtitle = if (isPremium) AppLocale.premiumSettingsSubtitleActive
                           else AppLocale.premiumSettingsSubtitleFree,
                onClick = onNavigateToPremium,
                accentColor = StarGold
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsItem(
                icon = Icons.Default.CatchingPokemon,
                title = AppLocale.homeSpriteSettingsTitle,
                subtitle = AppLocale.homeSpriteSettingsSubtitle,
                onClick = {
                    if (premiumManager.canChooseHomeSprite()) {
                        showHomeSpriteDialog = true
                    } else {
                        showPremiumHomeSpriteDialog = true
                    }
                },
                accentColor = if (isPremium) BlueCard else TextMuted
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                DarkCard.copy(alpha = 0.95f),
                                DarkSurface.copy(alpha = 0.92f)
                            )
                        )
                    )
                    .border(
                        BorderStroke(1.dp, StarGold.copy(alpha = 0.22f)),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { showCreatorSection = !showCreatorSection }
                    .animateContentSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(StarGold.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "👨🏻‍💻",
                            fontSize = 18.sp
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppLocale.creatorSectionTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Text(
                            text = "Indie dev note",
                            fontSize = 11.sp,
                            color = StarGold
                        )
                    }

                    Icon(
                        imageVector = if (showCreatorSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = StarGold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = StarGold.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StarGold.copy(alpha = 0.18f))
                ) {
                    Text(
                        text = "Grazie per essere arrivato fin qui.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextWhite,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                if (showCreatorSection) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = AppLocale.creatorSectionBody,
                        fontSize = 12.sp,
                        color = TextMuted,
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Made with care in Italy",
                        fontSize = 11.sp,
                        color = TextGray,
                        letterSpacing = 0.3.sp
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Tocca per leggere il messaggio completo",
                        fontSize = 11.sp,
                        color = TextGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Logout
            SettingsItem(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = AppLocale.logoutLabel,
                subtitle = AppLocale.logoutSubtitle,
                onClick = onLogout,
                accentColor = RedCard
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Trademark disclaimer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard.copy(alpha = 0.6f))
                    .padding(16.dp)
            ) {
                Text(
                    text = AppLocale.disclaimerTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = AppLocale.disclaimerBody,
                    fontSize = 12.sp,
                    color = TextMuted,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Delete account section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = AppLocale.dangerZone,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = RedCard,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RedCard.copy(alpha = 0.15f),
                        contentColor = RedCard
                    )
                ) {
                    Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppLocale.deleteAccountButton,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (deleteError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = deleteError!!, color = RedCard, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        DeleteAccountDialog(
            isDeleting = isDeleting,
            onConfirm = {
                isDeleting = true
                deleteError = null
                authViewModel.deleteAccount(
                    onSuccess = {
                        isDeleting = false
                        showDeleteDialog = false
                        onAccountDeleted()
                    },
                    onError = { error ->
                        isDeleting = false
                        deleteError = error
                        showDeleteDialog = false
                    }
                )
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showHomeSpriteDialog) {
        HomeSpritePickerDialog(
            spriteIds = homeSpriteIds,
            selectedSpriteId = selectedHomeSpriteId,
            onSelectSprite = { spriteId -> premiumManager.setSelectedHomeSpriteId(spriteId) },
            onDismiss = { showHomeSpriteDialog = false }
        )
    }

    if (showPremiumHomeSpriteDialog) {
        PremiumRequiredDialog(
            title = AppLocale.premiumHomeSpriteTitle,
            message = AppLocale.premiumHomeSpriteMessage,
            onDismiss = { showPremiumHomeSpriteDialog = false },
            onUpgrade = {
                showPremiumHomeSpriteDialog = false
                onNavigateToPremium()
            }
        )
    }
}

@Composable
private fun SocialPromoCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        DarkCard.copy(alpha = 0.96f),
                        BlueCard.copy(alpha = 0.22f)
                    )
                )
            )
            .border(
                BorderStroke(1.dp, BlueCard.copy(alpha = 0.30f)),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.30f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "TikTok",
                color = TextWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextMuted,
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = BlueCard,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun HomeSpritePickerDialog(
    spriteIds: List<Int>,
    selectedSpriteId: Int,
    onSelectSprite: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = AppLocale.homeSpriteDialogTitle,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        onSelectSprite(0)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedSpriteId == 0) BlueCard else DarkCard
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(AppLocale.homeSpriteRandom)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(spriteIds) { spriteId ->
                            val spriteUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$spriteId.png"
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(DarkCard)
                                    .border(
                                        width = if (selectedSpriteId == spriteId) 2.dp else 1.dp,
                                        color = if (selectedSpriteId == spriteId) BlueCard else Color.White.copy(alpha = 0.12f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        onSelectSprite(spriteId)
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = spriteUrl,
                                    contentDescription = "Sprite $spriteId",
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(AppLocale.cancel, color = TextMuted)
            }
        }
    )
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    accentColor: androidx.compose.ui.graphics.Color = BlueCard
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = accentColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextWhite)
            Text(text = subtitle, fontSize = 12.sp, color = TextMuted)
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DeleteAccountDialog(
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !isDeleting, dismissOnClickOutside = !isDeleting)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Warning,
                null,
                tint = RedCard,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = AppLocale.deleteAccountTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = AppLocale.deleteAccountMessage,
                fontSize = 14.sp,
                color = TextGray,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isDeleting) {
                CircularProgressIndicator(color = RedCard, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(AppLocale.deletingAccount, color = TextMuted, fontSize = 13.sp)
            } else {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedCard)
                ) {
                    Text(
                        text = AppLocale.deleteAccountConfirm,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextGray)
                ) {
                    Text(
                        text = AppLocale.cancel,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
