package com.emabuia.pokevault.ui.trade

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.emabuia.pokevault.data.trade.dto.TradeNotifyPrefs
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale

// ── Notifiche (fase 3) ──────────────────────────────────────────────────────

/**
 * Le cinque categorie, ognuna con il suo interruttore e una riga che dice
 * cosa arriva. Se il telefono le ha bloccate lo si dice in cima, con il
 * tasto per le impostazioni di sistema: altrimenti gli interruttori
 * sembrerebbero non fare niente.
 */
@Composable
internal fun NotifySettingsDialog(prefs: TradeNotifyPrefs?, onChange: (kind: String, enabled: Boolean) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val systemEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val rows = listOf(
        Triple("proposals", AppLocale.tradeRadarNotifyProposals, AppLocale.tradeRadarNotifyProposalsHint),
        Triple("meetings", AppLocale.tradeRadarNotifyMeetings, AppLocale.tradeRadarNotifyMeetingsHint),
        Triple("reminders", AppLocale.tradeRadarNotifyReminders, AppLocale.tradeRadarNotifyRemindersHint),
        Triple("after", AppLocale.tradeRadarNotifyAfter, AppLocale.tradeRadarNotifyAfterHint),
        Triple("wants", AppLocale.tradeRadarNotifyWants, AppLocale.tradeRadarNotifyWantsHint)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppLocale.tradeRadarNotifyTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!systemEnabled) {
                    val shape = RoundedCornerShape(12.dp)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(AppColors.orange.copy(alpha = 0.12f))
                            .border(1.dp, AppColors.orange.copy(alpha = 0.5f), shape)
                            .padding(10.dp)
                    ) {
                        Text(AppLocale.tradeRadarNotifyBlocked, fontSize = 13.sp, color = AppColors.textPrimary)
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            }
                        }) { Text(AppLocale.tradeRadarNotifyOpenSettings) }
                    }
                }
                rows.forEach { (kind, title, hint) ->
                    val checked = when (kind) {
                        "proposals" -> prefs?.proposals != false
                        "meetings" -> prefs?.meetings != false
                        "reminders" -> prefs?.reminders != false
                        "after" -> prefs?.after != false
                        else -> prefs?.wants == true
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.textPrimary)
                            Text(hint, fontSize = 12.sp, color = AppColors.textSecondary)
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = checked, onCheckedChange = { onChange(kind, it) })
                    }
                }
                Text(AppLocale.tradeRadarNotifyQuiet, fontSize = 12.sp, color = AppColors.textMuted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(AppLocale.tradeRadarClose) } }
    )
}

/**
 * La domanda sulle carte cercate, una volta sola: e' l'unica categoria che
 * parte spenta, perche' e' l'unica che arriva senza che l'utente abbia fatto
 * niente.
 */
@Composable
internal fun WantsAlertsCard(onAnswer: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.blue.copy(alpha = 0.10f))
            .border(1.dp, AppColors.blue.copy(alpha = 0.4f), shape)
            .padding(12.dp)
    ) {
        Text("🔔 " + AppLocale.tradeRadarWantsAskTitle, fontWeight = FontWeight.Bold, color = AppColors.textPrimary)
        Text(AppLocale.tradeRadarWantsAskBody, fontSize = 13.sp, color = AppColors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onAnswer(false) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                Text(AppLocale.tradeRadarWantsAskNo)
            }
            Button(
                onClick = { onAnswer(true) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
                modifier = Modifier.weight(1f)
            ) { Text(AppLocale.tradeRadarWantsAskYes, fontWeight = FontWeight.Bold) }
        }
    }
}
