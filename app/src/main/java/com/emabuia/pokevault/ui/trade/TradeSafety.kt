package com.emabuia.pokevault.ui.trade

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.trade.dto.TradeBlockedUser
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── Segnala e blocca (fase 2f) ──────────────────────────────────────────────
//
// Discreto dove si guarda una persona (un link in fondo, non un tasto rosso
// accanto a "Proponi"), chiaro quando lo si apre: cosa succede, cosa sa
// l'altro, e che le segnalazioni le guardiamo noi.

/** In fondo alla scheda di una persona: "Segnala o blocca", con le due scelte. */
@Composable
internal fun PersonSafetyLink(onSafety: (report: Boolean) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Outlined.Flag, null, tint = AppColors.textMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(AppLocale.tradeRadarSafetyLink, fontSize = 12.sp, color = AppColors.textMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(AppLocale.tradeRadarBlock) },
                leadingIcon = { Icon(Icons.Default.Block, null) },
                onClick = { open = false; onSafety(false) }
            )
            DropdownMenuItem(
                text = { Text(AppLocale.tradeRadarReport, color = AppColors.red) },
                leadingIcon = { Icon(Icons.Outlined.Flag, null, tint = AppColors.red) },
                onClick = { open = false; onSafety(true) }
            )
        }
    }
}

/** Bloccare: cosa succede, prima di farlo. */
@Composable
internal fun BlockDialog(nickname: String, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Block, null, tint = AppColors.red) },
        title = { Text(AppLocale.tradeRadarBlockTitle(nickname)) },
        text = { Text(AppLocale.tradeRadarBlockBody(nickname), fontSize = 14.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = AppColors.red)) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(AppLocale.tradeRadarBlock, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppLocale.cancel) } }
    )
}

/** I motivi, nell'ordine in cui li si cerca. "Non si e' presentato" ha il suo tasto sulla proposta. */
private val REASONS = listOf("behavior", "scam", "fake_cards", "nickname", "other")

private fun reasonTitle(reason: String) = when (reason) {
    "behavior" -> AppLocale.tradeRadarReasonBehavior
    "scam" -> AppLocale.tradeRadarReasonScam
    "fake_cards" -> AppLocale.tradeRadarReasonFakeCards
    "nickname" -> AppLocale.tradeRadarReasonNickname
    else -> AppLocale.tradeRadarReasonOther
}

private fun reasonHint(reason: String) = when (reason) {
    "behavior" -> AppLocale.tradeRadarReasonBehaviorHint
    "scam" -> AppLocale.tradeRadarReasonScamHint
    "fake_cards" -> AppLocale.tradeRadarReasonFakeCardsHint
    "nickname" -> AppLocale.tradeRadarReasonNicknameHint
    else -> AppLocale.tradeRadarReasonOtherHint
}

/** Per "Altro" serve raccontare: senza, non sapremmo cosa guardare. */
private const val OTHER_MIN_NOTE = 10

/**
 * Segnalare: il motivo, due righe facoltative (obbligatorie per "Altro"),
 * e "Blocca anche" gia' spuntato. In fondo cosa succede alla segnalazione.
 */
@Composable
internal fun ReportDialog(
    nickname: String,
    busy: Boolean,
    onSend: (reason: String, note: String, alsoBlock: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var alsoBlock by rememberSaveable { mutableStateOf(true) }
    val ready = reason != null && (reason != "other" || note.trim().length >= OTHER_MIN_NOTE)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Flag, null, tint = AppColors.red) },
        title = { Text(AppLocale.tradeRadarReportTitle(nickname)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                REASONS.forEach { option ->
                    val selected = reason == option
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .then(if (selected) Modifier.background(AppColors.red.copy(alpha = 0.08f)).border(1.dp, AppColors.red.copy(alpha = 0.4f), shape) else Modifier)
                            .clickable { reason = option }
                            .padding(end = 8.dp)
                    ) {
                        RadioButton(selected = selected, onClick = { reason = option })
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(reasonTitle(option), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.textPrimary)
                            Text(reasonHint(option), fontSize = 12.sp, color = AppColors.textSecondary)
                        }
                    }
                }
                Text(AppLocale.tradeRadarReportNoShowHint, fontSize = 11.sp, color = AppColors.textMuted)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(300) },
                    label = { Text(if (reason == "other") AppLocale.tradeRadarReportNoteRequired else AppLocale.tradeRadarReportNote) },
                    supportingText = { Text("${note.length}/300") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { alsoBlock = !alsoBlock }) {
                    Checkbox(checked = alsoBlock, onCheckedChange = { alsoBlock = it })
                    Text(AppLocale.tradeRadarReportAlsoBlock(nickname), fontSize = 14.sp, color = AppColors.textPrimary)
                }
                Text(AppLocale.tradeRadarReportPrivacy, fontSize = 12.sp, color = AppColors.textMuted)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { reason?.let { onSend(it, note.trim(), alsoBlock) } },
                enabled = ready && !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = AppColors.red)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(AppLocale.tradeRadarReportUserSend, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppLocale.cancel) } }
    )
}

/** Le persone che ho bloccato, con "Sblocca". Chi ha bloccato me non c'e': non si dice. */
@Composable
internal fun BlockedListDialog(items: List<TradeBlockedUser>?, onUnblock: (String) -> Unit, onDismiss: () -> Unit) {
    val locale = if (AppLocale.isItalian) Locale.ITALIAN else Locale.ENGLISH
    val format = remember(locale) { DateTimeFormatter.ofPattern("d MMM yyyy", locale) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppLocale.tradeRadarBlockedTitle) },
        text = {
            when {
                items == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                items.isEmpty() -> Text(AppLocale.tradeRadarBlockedEmpty, color = AppColors.textSecondary)
                else -> Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEach { user ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Avatar(user.nickname.orEmpty(), size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user.nickname.orEmpty(), fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                user.blockedAt?.let {
                                    Text(
                                        AppLocale.tradeRadarBlockedSince(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(format)),
                                        fontSize = 12.sp,
                                        color = AppColors.textMuted
                                    )
                                }
                            }
                            TextButton(onClick = { user.id?.let(onUnblock) }) { Text(AppLocale.tradeRadarUnblock) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(AppLocale.tradeRadarClose) } }
    )
}

/** Lo stesso indirizzo della privacy policy e dei termini: non ce n'e' uno dedicato. */
internal const val SUPPORT_EMAIL = "devteam.vaultcards@hotmail.com"

/** Un ban e' una sospensione che non finisce: oltre il 2100 non si scrive la data. */
private const val FOREVER_FROM = 4_102_444_800_000L

/**
 * In cima al pannello di chi e' sospeso: fino a quando, perche', e cosa puo'
 * ancora fare. Senza, vedrebbe solo un radar vuoto e non capirebbe.
 */
@Composable
internal fun SuspensionBanner(until: Long, reason: String?, nickname: String, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val locale = if (AppLocale.isItalian) Locale.ITALIAN else Locale.ENGLISH
    val date = remember(until, locale) { Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMMM", locale)) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.orange.copy(alpha = 0.12f))
            .border(1.dp, AppColors.orange.copy(alpha = 0.5f), shape)
            .padding(12.dp)
    ) {
        Text(
            if (until >= FOREVER_FROM) AppLocale.tradeRadarSuspendedForever else AppLocale.tradeRadarSuspendedTitle(date),
            fontWeight = FontWeight.Bold,
            color = AppColors.orange
        )
        Text(
            when (reason) {
                "no_show" -> AppLocale.tradeRadarSuspendedNoShow
                "reports" -> AppLocale.tradeRadarSuspendedReports
                else -> AppLocale.tradeRadarSuspendedAdmin
            },
            fontSize = 13.sp,
            color = AppColors.textPrimary
        )
        Text(AppLocale.tradeRadarSuspendedRules, fontSize = 12.sp, color = AppColors.textSecondary)
        // Chi pensa sia un errore deve sapere a chi scrivere: l'oggetto ci dice gia' chi e'.
        Text(
            AppLocale.tradeRadarSuspendedContact(SUPPORT_EMAIL),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.blue,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                val subject = Uri.encode(AppLocale.tradeRadarSuspendedMailSubject(nickname))
                runCatching { uriHandler.openUri("mailto:$SUPPORT_EMAIL?subject=$subject") }
            }
        )
    }
}
