package com.emabuia.pokevault.ui.premium

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emabuia.pokevault.data.billing.GiftCodeRepository
import com.emabuia.pokevault.data.billing.PremiumManager
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Codici regalo: il proprio codice AMICO da condividere e il campo di riscatto.
 *
 * Tutto quello che conta lo decide il Worker (vedi GiftCodeRepository): qui non
 * si valida nessun codice e non si concede nessun mese. La schermata mostra ciò
 * che il server risponde, compreso il motivo di un rifiuto, tradotto.
 */
@Composable
fun GiftCodeScreen(
    onBack: () -> Unit,
    onNavigateToPremium: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val premiumManager = remember { PremiumManager.getInstance() }
    val giftUntilMs by premiumManager.giftUntilMs.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var status by remember { mutableStateOf<GiftCodeRepository.GiftStatus?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var codeInput by remember { mutableStateOf("") }
    var redeeming by remember { mutableStateOf(false) }
    var redeemError by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        val fetched = GiftCodeRepository.fetchStatus()
        status = fetched
        loadFailed = fetched == null
        // Il server è l'unico a sapere se il regalo è ancora valido: allineare
        // qui evita che la schermata dica una cosa e il resto dell'app un'altra.
        fetched?.let { premiumManager.applyGiftGrant(it.giftUntilMs ?: 0L) }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

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
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        AppLocale.back,
                        tint = AppColors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = AppLocale.giftTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Testata ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(AppColors.gold, AppColors.gold.copy(alpha = 0.55f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CardGiftcard,
                        contentDescription = null,
                        tint = AppColors.background,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = AppLocale.giftHeadline,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = AppColors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = AppLocale.giftHeadlineBody,
                    fontSize = 13.sp,
                    color = AppColors.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Regalo attivo ──
            if (giftUntilMs > System.currentTimeMillis()) {
                Surface(
                    color = AppColors.green.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AppColors.green.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = AppLocale.giftActiveUntil(formatDate(giftUntilMs)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.textPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AppColors.gold)
                    }
                }

                loadFailed -> {
                    // Un codice AMICO inventato dal telefono non esisterebbe per
                    // nessun altro: meglio dire che non si riesce a leggerlo.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = AppLocale.giftErrorUnavailable,
                            fontSize = 13.sp,
                            color = AppColors.textMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { scope.launch { reload() } }) {
                            Text(AppLocale.retry, color = AppColors.blue)
                        }
                    }
                }

                else -> {
                    val current = status
                    if (current != null && current.code.isNotBlank()) {
                        FriendCodeCard(
                            status = current,
                            onCopy = {
                                copyToClipboard(context, current.code)
                                scope.launch {
                                    snackbarHostState.showSnackbar(AppLocale.giftCopiedToast)
                                }
                            },
                            onShare = { shareCode(context, current.code) }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    RedeemCard(
                        alreadyRedeemed = current?.alreadyRedeemed == true,
                        codeInput = codeInput,
                        onCodeChange = {
                            codeInput = it
                            redeemError = null
                        },
                        busy = redeeming,
                        error = redeemError,
                        onRedeem = {
                            if (codeInput.isBlank()) {
                                redeemError = AppLocale.giftErrorEmpty
                                return@RedeemCard
                            }
                            redeeming = true
                            redeemError = null
                            scope.launch {
                                when (val result = GiftCodeRepository.redeem(context, codeInput)) {
                                    is GiftCodeRepository.RedeemResult.Success -> {
                                        premiumManager.applyGiftGrant(result.giftUntilMs)
                                        codeInput = ""
                                        reload()
                                        snackbarHostState.showSnackbar(
                                            AppLocale.giftRedeemSuccess(result.grantDays)
                                        )
                                    }

                                    is GiftCodeRepository.RedeemResult.Rejected ->
                                        redeemError = rejectionMessage(result.reason)

                                    GiftCodeRepository.RedeemResult.Unavailable ->
                                        redeemError = AppLocale.giftErrorUnavailable
                                }
                                redeeming = false
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = onNavigateToPremium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = AppLocale.premiumFeaturesTitle,
                    color = AppColors.blue,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun FriendCodeCard(
    status: GiftCodeRepository.GiftStatus,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val exhausted = status.invitesMax > 0 && status.invitesUsed >= status.invitesMax

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.card)
            .border(
                BorderStroke(1.dp, AppColors.gold.copy(alpha = 0.25f)),
                RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Text(
            text = AppLocale.giftMyCodeTitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.textSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = status.code,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = AppColors.gold,
            letterSpacing = 2.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (exhausted) AppLocale.giftInvitesExhausted
            else AppLocale.giftInvitesUsed(status.invitesUsed, status.invitesMax),
            fontSize = 12.sp,
            color = AppColors.textMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onCopy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppLocale.giftCopyCta, fontSize = 13.sp)
            }
            Button(
                onClick = onShare,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.gold),
                // Condividere un codice esaurito manderebbe l'amico dritto su
                // un rifiuto: il bottone si spegne prima.
                enabled = !exhausted
            ) {
                Icon(
                    Icons.Default.Share,
                    null,
                    tint = AppColors.background,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppLocale.giftShareCta, fontSize = 13.sp, color = AppColors.background)
            }
        }
    }
}

@Composable
private fun RedeemCard(
    alreadyRedeemed: Boolean,
    codeInput: String,
    onCodeChange: (String) -> Unit,
    busy: Boolean,
    error: String?,
    onRedeem: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.card)
            .padding(20.dp)
    ) {
        Text(
            text = AppLocale.giftRedeemTitle,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.textPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (alreadyRedeemed) AppLocale.giftErrorAlreadyRedeemed
            else AppLocale.giftRedeemRule,
            fontSize = 12.sp,
            color = AppColors.textMuted,
            lineHeight = 17.sp
        )

        if (!alreadyRedeemed) {
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = codeInput,
                onValueChange = onCodeChange,
                singleLine = true,
                enabled = !busy,
                isError = error != null,
                placeholder = { Text(AppLocale.giftRedeemHint, fontSize = 13.sp) },
                // I codici sono in maiuscolo: lasciare la tastiera in minuscolo
                // costringerebbe a premere shift per ogni carattere.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    fontSize = 12.sp,
                    color = AppColors.red,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onRedeem,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.gold)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = AppColors.background,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = AppLocale.giftRedeemCta,
                        color = AppColors.background,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun rejectionMessage(reason: GiftCodeRepository.RedeemRejection): String = when (reason) {
    GiftCodeRepository.RedeemRejection.CODE_MISSING -> AppLocale.giftErrorEmpty
    GiftCodeRepository.RedeemRejection.CODE_NOT_FOUND -> AppLocale.giftErrorNotFound
    GiftCodeRepository.RedeemRejection.CODE_DISABLED -> AppLocale.giftErrorDisabled
    GiftCodeRepository.RedeemRejection.CODE_EXPIRED -> AppLocale.giftErrorExpired
    GiftCodeRepository.RedeemRejection.CODE_EXHAUSTED -> AppLocale.giftErrorExhausted
    GiftCodeRepository.RedeemRejection.OWN_CODE -> AppLocale.giftErrorOwnCode
    GiftCodeRepository.RedeemRejection.ALREADY_REDEEMED -> AppLocale.giftErrorAlreadyRedeemed
    GiftCodeRepository.RedeemRejection.DEVICE_ALREADY_REDEEMED -> AppLocale.giftErrorDeviceUsed
    GiftCodeRepository.RedeemRejection.RATE_LIMITED -> AppLocale.giftErrorRateLimited
    GiftCodeRepository.RedeemRejection.UNKNOWN -> AppLocale.giftErrorUnavailable
}

private fun formatDate(epochMs: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))

private fun copyToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("PokeVault", code))
}

private fun shareCode(context: Context, code: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, AppLocale.giftShareMessage(code))
    }
    context.startActivity(Intent.createChooser(intent, AppLocale.giftShareChooserTitle))
}
