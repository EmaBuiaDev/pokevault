package com.example.pokevault.ui.legal

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.pokevault.ui.theme.*
import com.example.pokevault.util.AppLocale

private const val PREFS_NAME = "pokevault_legal"
private const val KEY_AGE_VERIFIED = "age_verified"
private const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"
private const val KEY_PRIVACY_ACCEPTED = "privacy_accepted"

fun hasCompletedLegalChecks(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_AGE_VERIFIED, false)
            && prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)
            && prefs.getBoolean(KEY_PRIVACY_ACCEPTED, false)
}

fun markLegalChecksCompleted(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_AGE_VERIFIED, true)
        .putBoolean(KEY_DISCLAIMER_ACCEPTED, true)
        .putBoolean(KEY_PRIVACY_ACCEPTED, true)
        .apply()
}

/**
 * Combined first-launch legal flow: Age Gate → Disclaimer → Privacy Policy acceptance.
 * Shows all three steps in a single dialog sequence.
 */
@Composable
fun FirstLaunchLegalFlow(
    onCompleted: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }

    when (step) {
        1 -> AgeGateDialog(
            onConfirmed = { step = 2 },
            onDenied = { /* stays on same screen, user cannot proceed */ }
        )
        2 -> DisclaimerDialog(
            onAccepted = { step = 3 }
        )
        3 -> PrivacyConsentDialog(
            onAccepted = { onCompleted() }
        )
    }
}

@Composable
fun AgeGateDialog(
    onConfirmed: () -> Unit,
    onDenied: () -> Unit
) {
    var showDeniedMessage by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { /* non-dismissable */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = AppLocale.ageGateTitle,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = AppLocale.ageGateMessage,
                fontSize = 14.sp,
                color = TextGray,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            if (showDeniedMessage) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = AppLocale.ageGateDenied,
                    fontSize = 13.sp,
                    color = RedCard,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onConfirmed,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlueCard)
            ) {
                Text(
                    text = AppLocale.ageGateConfirm,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    showDeniedMessage = true
                    onDenied()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextGray)
            ) {
                Text(
                    text = AppLocale.ageGateDeny,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun DisclaimerDialog(
    onAccepted: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* non-dismissable */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurface)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = AppLocale.disclaimerTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = AppLocale.disclaimerBody,
                fontSize = 13.sp,
                color = TextGray,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onAccepted,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlueCard)
            ) {
                Text(
                    text = AppLocale.disclaimerAccept,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun PrivacyConsentDialog(
    onAccepted: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = { /* non-dismissable */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurface)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = AppLocale.privacyConsentTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = AppLocale.privacyConsentSummary,
                fontSize = 13.sp,
                color = TextGray,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppLocale.privacyPolicyUrl))
                    context.startActivity(intent)
                }
            ) {
                Text(
                    text = AppLocale.readFullPrivacyPolicy,
                    color = BlueCard,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onAccepted,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlueCard)
            ) {
                Text(
                    text = AppLocale.privacyConsentAccept,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
