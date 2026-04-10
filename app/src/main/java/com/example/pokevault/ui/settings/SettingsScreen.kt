package com.example.pokevault.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pokevault.data.billing.PremiumManager
import com.example.pokevault.ui.theme.*
import com.example.pokevault.util.AppLocale
import com.example.pokevault.viewmodel.AuthViewModel

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
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

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
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, AppLocale.back, tint = TextWhite)
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

            // Logout
            SettingsItem(
                icon = Icons.Default.Logout,
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
