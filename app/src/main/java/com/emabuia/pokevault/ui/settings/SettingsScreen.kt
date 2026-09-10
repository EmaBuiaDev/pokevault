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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.emabuia.pokevault.viewmodel.DeleteReauthMethod
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
    val isPremium by premiumManager.isPremium.collectAsStateWithLifecycle()
    val selectedHomeSpriteId by premiumManager.selectedHomeSpriteId.collectAsStateWithLifecycle()
    val homeSpriteIds = remember { premiumManager.homeSpriteIds }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showHomeSpriteDialog by remember { mutableStateOf(false) }
    var showPremiumHomeSpriteDialog by remember { mutableStateOf(false) }
    var showCreatorSection by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var showReauthDialog by remember { mutableStateOf(false) }
    var reauthMethod by remember { mutableStateOf(DeleteReauthMethod.UNKNOWN) }
    var reauthPassword by remember { mutableStateOf("") }

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
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = AppColors.textPrimary)
                }
                Text(
                    text = AppLocale.settingsTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textPrimary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // TikTok promo compact (sempre in cima, evidenziata)
            SocialPromoCard(
                title = AppLocale.tikTokLabel,
                subtitle = AppLocale.tikTokSubtitle,
                compact = true,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppLocale.tikTokUrl))
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Lingua
            SettingsItem(
                icon = Icons.Default.Language,
                title = AppLocale.languageLabel,
                subtitle = AppLocale.languageSubtitle,
                onClick = { AppLocale.toggle(context) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tema: l'app era scura per costruzione, senza alcun modo di
            // scegliere. Cicla fra Sistema, Chiaro e Scuro.
            SettingsItem(
                icon = when (ThemePreference.current) {
                    ThemeMode.LIGHT -> Icons.Default.LightMode
                    ThemeMode.DARK -> Icons.Default.DarkMode
                    ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                },
                title = AppLocale.themeLabel,
                subtitle = AppLocale.themeSubtitle(ThemePreference.current.code),
                onClick = {
                    val next = when (ThemePreference.current) {
                        ThemeMode.SYSTEM -> ThemeMode.LIGHT
                        ThemeMode.LIGHT -> ThemeMode.DARK
                        ThemeMode.DARK -> ThemeMode.SYSTEM
                    }
                    ThemePreference.set(next, context)
                }
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

            // Premium section
            SettingsItem(
                icon = Icons.Default.WorkspacePremium,
                title = AppLocale.premiumSettingsLabel,
                subtitle = if (isPremium) AppLocale.premiumSettingsSubtitleActive
                           else AppLocale.premiumSettingsSubtitleFree,
                onClick = onNavigateToPremium,
                accentColor = AppColors.gold
            )

            // Play richiede un percorso in-app per gestire o disdire
            // l'abbonamento: prima non esisteva da nessuna parte.
            if (isPremium) {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsItem(
                    icon = Icons.Default.ManageAccounts,
                    title = AppLocale.manageSubscriptionLabel,
                    subtitle = AppLocale.manageSubscriptionSubtitle,
                    onClick = {
                        val uri = Uri.parse(
                            "https://play.google.com/store/account/subscriptions" +
                                "?sku=${PremiumManager.PRODUCT_MONTHLY}" +
                                "&package=${context.packageName}"
                        )
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                )
            }

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
                accentColor = if (isPremium) AppColors.blue else AppColors.textMuted
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
                                AppColors.card.copy(alpha = 0.95f),
                                AppColors.surface.copy(alpha = 0.92f)
                            )
                        )
                    )
                    .border(
                        BorderStroke(1.dp, AppColors.gold.copy(alpha = 0.22f)),
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
                            .background(AppColors.gold.copy(alpha = 0.16f)),
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
                            color = AppColors.textPrimary
                        )
                        Text(
                            text = "Indie dev note",
                            fontSize = 11.sp,
                            color = AppColors.gold
                        )
                    }

                    Icon(
                        imageVector = if (showCreatorSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = AppColors.gold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = AppColors.gold.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AppColors.gold.copy(alpha = 0.18f))
                ) {
                    Text(
                        text = "Grazie per essere arrivato fin qui.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.textPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                if (showCreatorSection) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = AppLocale.creatorSectionBody,
                        fontSize = 12.sp,
                        color = AppColors.textMuted,
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Made with care in Italy",
                        fontSize = 11.sp,
                        color = AppColors.textSecondary,
                        letterSpacing = 0.3.sp
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Tocca per leggere il messaggio completo",
                        fontSize = 11.sp,
                        color = AppColors.textSecondary
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
                accentColor = AppColors.red
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Trademark disclaimer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.card.copy(alpha = 0.6f))
                    .padding(16.dp)
            ) {
                Text(
                    text = AppLocale.disclaimerTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = AppLocale.disclaimerBody,
                    fontSize = 12.sp,
                    color = AppColors.textMuted,
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
                    color = AppColors.red,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.red.copy(alpha = 0.15f),
                        contentColor = AppColors.red
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
                    Text(text = deleteError!!, color = AppColors.red, fontSize = 13.sp)
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
                    onRequiresRecentLogin = { method ->
                        isDeleting = false
                        showDeleteDialog = false
                        deleteError = null
                        reauthMethod = method
                        reauthPassword = ""
                        showReauthDialog = true
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

    if (showReauthDialog) {
        ReauthenticateDeleteDialog(
            method = reauthMethod,
            password = reauthPassword,
            isBusy = isDeleting,
            onPasswordChange = { reauthPassword = it },
            onConfirmPassword = {
                isDeleting = true
                deleteError = null
                authViewModel.reauthenticateAndDeleteWithPassword(
                    password = reauthPassword,
                    onSuccess = {
                        isDeleting = false
                        showReauthDialog = false
                        onAccountDeleted()
                    },
                    onError = { error ->
                        isDeleting = false
                        deleteError = error
                    }
                )
            },
            onConfirmGoogle = {
                isDeleting = true
                deleteError = null
                authViewModel.reauthenticateAndDeleteWithGoogle(
                    context = context,
                    onSuccess = {
                        isDeleting = false
                        showReauthDialog = false
                        onAccountDeleted()
                    },
                    onError = { error ->
                        isDeleting = false
                        deleteError = error
                    }
                )
            },
            onDismiss = {
                if (!isDeleting) {
                    showReauthDialog = false
                }
            }
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
    onClick: () -> Unit,
    compact: Boolean = false
) {
    val vertPad = if (compact) 10.dp else 16.dp
    val iconSize = if (compact) 34.dp else 44.dp
    val titleSize = if (compact) 13.sp else 15.sp
    val subtitleSize = if (compact) 11.sp else 12.sp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF1A0030),
                        Color(0xFF0D1B4B)
                    )
                )
            )
            .border(
                BorderStroke(1.5.dp, Color(0xFFEE1D52).copy(alpha = 0.85f)),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = vertPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFEE1D52), Color(0xFF010101))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "TT",
                color = Color.White,
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (!compact) Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = subtitleSize,
                color = Color(0xFFEE1D52).copy(alpha = 0.85f),
                lineHeight = 15.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = Color(0xFFEE1D52),
            modifier = Modifier.size(if (compact) 18.dp else 22.dp)
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
        containerColor = AppColors.surface,
        title = {
            Text(
                text = AppLocale.homeSpriteDialogTitle,
                color = AppColors.textPrimary,
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
                        containerColor = if (selectedSpriteId == 0) AppColors.blue else AppColors.card
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
                        items(spriteIds, key = { it }) { spriteId ->
                            val spriteUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$spriteId.png"
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.card)
                                    .border(
                                        width = if (selectedSpriteId == spriteId) 2.dp else 1.dp,
                                        color = if (selectedSpriteId == spriteId) AppColors.blue else Color.White.copy(alpha = 0.12f),
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
                Text(AppLocale.cancel, color = AppColors.textMuted)
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
    accentColor: androidx.compose.ui.graphics.Color = AppColors.blue
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
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppColors.textPrimary)
            Text(text = subtitle, fontSize = 12.sp, color = AppColors.textMuted)
        }
        Icon(Icons.Default.ChevronRight, null, tint = AppColors.textMuted, modifier = Modifier.size(20.dp))
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
                .background(AppColors.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Warning,
                null,
                tint = AppColors.red,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = AppLocale.deleteAccountTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = AppLocale.deleteAccountMessage,
                fontSize = 14.sp,
                color = AppColors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isDeleting) {
                CircularProgressIndicator(color = AppColors.red, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(AppLocale.deletingAccount, color = AppColors.textMuted, fontSize = 13.sp)
            } else {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.red)
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
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.textSecondary)
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

@Composable
private fun ReauthenticateDeleteDialog(
    method: DeleteReauthMethod,
    password: String,
    isBusy: Boolean,
    onPasswordChange: (String) -> Unit,
    onConfirmPassword: () -> Unit,
    onConfirmGoogle: () -> Unit,
    onDismiss: () -> Unit
) {
    val isItalian = AppLocale.isItalian
    val title = if (isItalian) "Conferma identita" else "Confirm identity"
    val body = if (isItalian)
        "Per motivi di sicurezza, Google richiede un accesso recente prima di eliminare l'account."
    else
        "For security reasons, Google requires a recent login before deleting your account."

    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !isBusy, dismissOnClickOutside = !isBusy)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Lock, null, tint = AppColors.blue, modifier = Modifier.size(44.dp))

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = body,
                fontSize = 14.sp,
                color = AppColors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            when (method) {
                DeleteReauthMethod.PASSWORD -> {
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy,
                        singleLine = true,
                        label = { Text(if (isItalian) "Password" else "Password") }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onConfirmPassword,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.red)
                    ) {
                        Text(if (isItalian) "Conferma e elimina" else "Confirm and delete")
                    }
                }

                DeleteReauthMethod.GOOGLE -> {
                    Button(
                        onClick = onConfirmGoogle,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (isItalian) "Accedi con Google e elimina" else "Sign in with Google and delete")
                    }
                }

                DeleteReauthMethod.UNKNOWN -> {
                    Button(
                        onClick = onConfirmGoogle,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (isItalian) "Prova con Google" else "Try with Google")
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy,
                        singleLine = true,
                        label = { Text(if (isItalian) "Password (opzionale)" else "Password (optional)") }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onConfirmPassword,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Text(if (isItalian) "Conferma con password" else "Confirm with password")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.textSecondary)
            ) {
                Text(AppLocale.cancel)
            }

            if (isBusy) {
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator(color = AppColors.blue, modifier = Modifier.size(26.dp))
            }
        }
    }
}
