package com.emabuia.pokevault.ui.scanner

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import timber.log.Timber
import com.emabuia.pokevault.ocr.ImagePreprocessor
import com.emabuia.pokevault.ocr.OCRTextBlock
import com.emabuia.pokevault.ocr.ScannedFrame
import com.emabuia.pokevault.ocr.ZoneBoundingBox
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.Constants
import com.emabuia.pokevault.util.ImageUrlUtils
import com.emabuia.pokevault.util.minimumEurPriceOrZero
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.viewmodel.ScannerViewModel
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * Proporzioni carta Pokemon standard (63mm × 88mm).
 * Usato per calcolare la zona di scansione.
 */
private const val CARD_ASPECT_RATIO = 63f / 88f // ~0.716

/**
 * Larghezza della cornice di scansione, in frazione della larghezza schermo.
 *
 * Tenuta alta di proposito: il numero da collezione e alto circa 1,6 mm, quindi
 * ogni punto di cornice in piu e risoluzione in piu su quel campo, che e il
 * collo di bottiglia del riconoscimento.
 */
private const val SCAN_ZONE_WIDTH_FRACTION = 0.88f

/** Di quanto la cornice e alzata rispetto al centro, per non finire sotto i controlli. */
private const val SCAN_ZONE_VERTICAL_OFFSET = 0.05f

/** Tetto allaltezza della cornice: serve solo in orizzontale, dove la carta non ci starebbe. */
private const val SCAN_ZONE_MAX_HEIGHT_FRACTION = 0.80f

@Composable
private fun ScannerCardImageFallback(
    card: com.emabuia.pokevault.data.remote.TcgCard,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val titleSize = if (compact) 8.sp else 10.sp
    val detailSize = if (compact) 7.sp else 8.sp
    val series = card.set?.series?.takeIf { it.isNotBlank() } ?: "-"
    val setName = card.set?.name?.takeIf { it.isNotBlank() } ?: "-"

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(if (compact) 4.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = card.name,
                color = Color.White,
                fontSize = titleSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = series,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = detailSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = setName,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = detailSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    viewModel: ScannerViewModel = viewModel()
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val state = viewModel.uiState
    val haptics = LocalHapticFeedback.current

    // Si scansiona guardando le carte, non lo schermo: il riscontro deve arrivare
    // alla mano. Un buzz quando c e qualcosa da decidere...
    LaunchedEffect(state.pendingCard?.id, state.candidateCards.firstOrNull()?.id) {
        if (state.pendingCard != null || state.candidateCards.isNotEmpty()) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // ...e uno quando la carta e dentro, che in modalita continua e l unico
    // segnale che la mano riceve.
    LaunchedEffect(state.lastAddedCard?.id) {
        if (state.lastAddedCard != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    if (!cameraPermission.status.isGranted) {
        PermissionRequest(
            shouldShowRationale = cameraPermission.status.shouldShowRationale,
            onRequestPermission = { cameraPermission.launchPermissionRequest() },
            onBack = onBack
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera a tutto schermo
        CameraPreview(
            onFrameScanned = { viewModel.onFrameScanned(it) },
            flashEnabled = state.flashEnabled,
            scanEnabled = state.pendingCard == null && state.candidateCards.isEmpty() && state.lastAddedCard == null
        )

        // Overlay zona di scansione (card-shaped)
        ScanZoneOverlay(
            isDetecting = state.isSearching,
            hasResult = state.pendingCard != null || state.candidateCards.isNotEmpty() || state.lastAddedCard != null,
            detectedName = state.detectedName
        )

        // Barra in alto e controlli di scansione, in una colonna sola
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, "Indietro",
                        tint = Color.White,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(6.dp)
                    )
                }

                if (state.addedCount > 0) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppColors.green.copy(alpha = 0.9f))
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "${state.addedCount}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(onClick = { viewModel.toggleFlash() }) {
                    Icon(
                        if (state.flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        "Flash",
                        tint = if (state.flashEnabled) AppColors.gold else Color.White,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(6.dp)
                    )
                }
            }

            ScannerControls(
                condition = state.condition,
                onConditionSelected = { viewModel.setCondition(it) },
                continuousMode = state.continuousMode,
                onToggleContinuous = { viewModel.toggleContinuousMode() }
            )
        }

        // Istruzione iniziale (sopra la zona di scansione)
        if (state.pendingCard == null && state.candidateCards.isEmpty() && state.lastAddedCard == null && !state.isSearching &&
            state.detectedName.isBlank()
        ) {
            Text(
                "Riempi la cornice con la carta",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(top = 104.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

        // Bottom area
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val idle = state.pendingCard == null && state.candidateCards.isEmpty() && state.lastAddedCard == null

            // Lettura in corso: mostrare ID e nome appena letti dice all utente
            // se deve solo aspettare o se deve avvicinare la carta.
            if (idle && (state.detectedNumber.isNotBlank() || state.detectedName.isNotBlank())) {
                LiveReadout(id = state.detectedNumber, name = state.detectedName)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Suggerimento di inquadratura, quando l ID non si legge
            if (idle) {
                state.hintMessage?.let { hint ->
                    Text(
                        hint,
                        color = Color.White,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(Color(0xE0334155), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Errore
            state.errorMessage?.let { error ->
                Text(
                    error,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .background(Color(0xE0EF4444), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Un solo pannello per volta, in dissolvenza sul posto.
            //
            // Prima erano quattro blocchi fratelli dentro la Column: confermando
            // una carta, l'uscita di "riconosciuta" si sovrapponeva all'ingresso
            // di "ricerca in corso" e poi di "aggiunta", la colonna cambiava
            // altezza tre volte di fila e le scritte sembravano entrare dall'alto
            // e dal basso insieme.
            AnimatedContent(
                targetState = scannerPanelFor(state),
                transitionSpec = {
                    // Niente SizeTransform: se il contenitore si anima, il pannello
                    // nuovo scorre mentre il vecchio sfuma, ed e' esattamente il
                    // guizzo che stiamo togliendo.
                    (fadeIn(tween(PANEL_FADE_MS)) togetherWith fadeOut(tween(PANEL_FADE_MS))) using null
                },
                contentKey = { panel -> panel::class },
                label = "scanner-panel"
            ) { panel ->
                when (panel) {
                    ScannerPanel.None -> Spacer(modifier = Modifier)

                    ScannerPanel.Searching -> SearchingIndicator()

                    is ScannerPanel.Confirm -> PendingCardConfirmation(
                        card = panel.card,
                        condition = state.condition,
                        onConfirm = { viewModel.confirmAdd() },
                        onDismiss = { viewModel.dismissCard() }
                    )

                    is ScannerPanel.Choose -> CandidateCardPicker(
                        cards = panel.cards,
                        onSelect = { viewModel.selectCandidate(it) },
                        onDismiss = { viewModel.dismissCard() }
                    )

                    is ScannerPanel.Added -> AddedCardBanner(card = panel.card)
                }
            }
        }
    }
}

/**
 * Quello che lo scanner sta leggendo, in tempo reale.
 *
 * L'ID sta in un riquadro a se': e' il dato che identifica la carta, e vederlo
 * comparire dice all'utente che l'inquadratura e' quella giusta prima ancora
 * che la ricerca finisca. Il nome lo accompagna, senza rubargli il posto.
 */
@Composable
private fun LiveReadout(id: String, name: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.68f))
            .padding(start = if (id.isBlank()) 12.dp else 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (id.isNotBlank()) {
            Text(
                text = id,
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .background(AppColors.gold, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            if (name.isNotBlank()) Spacer(modifier = Modifier.width(9.dp))
        }
        if (name.isNotBlank()) {
            Text(
                text = name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Dato secondario di una carta: numero, rarita', HP, prezzo. */
@Composable
private fun CardMetaPill(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

// ═══════════════════════════════════════════════
// Controlli di scansione
// ═══════════════════════════════════════════════

/**
 * Condizione e modalita' continua, decise prima di cominciare.
 *
 * Stanno qui e non in un dialogo per carta: scansionare un mazzetto vuol dire
 * decine di carte di seguito, e una domanda ripetuta a ogni carta costerebbe
 * piu' tempo di quanto lo scanner ne faccia risparmiare. Le carte in condizione
 * diversa si separano prima e si fa un secondo giro.
 */
@Composable
private fun ScannerControls(
    condition: String,
    onConditionSelected: (String) -> Unit,
    continuousMode: Boolean,
    onToggleContinuous: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConditionSelector(condition = condition, onSelected = onConditionSelected)

        ScannerChip(
            text = if (continuousMode) "Continuo" else "A conferma",
            highlighted = continuousMode,
            icon = if (continuousMode) Icons.Default.Check else null,
            onClick = onToggleContinuous
        )
    }
}

@Composable
private fun ConditionSelector(condition: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        ScannerChip(
            text = "$condition  ▾",
            highlighted = false,
            onClick = { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Constants.CARD_CONDITIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 14.sp) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                    leadingIcon = if (option == condition) {
                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun ScannerChip(
    text: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (highlighted) AppColors.blue.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.55f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * Cosa mostra in fondo allo schermo lo scanner. Stati alternativi di un unico
 * pannello, non riquadri indipendenti: e' quello che impedisce a due di loro di
 * essere sullo schermo insieme durante un passaggio.
 *
 * Ogni stato porta con se' i dati che gli servono, cosi' il pannello in uscita
 * resta disegnato correttamente anche dopo che lo stato del ViewModel e' gia'
 * cambiato.
 */
private sealed interface ScannerPanel {
    object None : ScannerPanel
    object Searching : ScannerPanel
    data class Confirm(val card: com.emabuia.pokevault.data.remote.TcgCard) : ScannerPanel
    data class Choose(val cards: List<com.emabuia.pokevault.data.remote.TcgCard>) : ScannerPanel
    data class Added(val card: com.emabuia.pokevault.data.remote.TcgCard) : ScannerPanel
}

/**
 * La carta da confermare ha la precedenza sullo spinner: durante la ricerca di
 * una conferma gia' visibile non deve sparire il pannello sotto le dita.
 */
private fun scannerPanelFor(state: com.emabuia.pokevault.viewmodel.ScannerUiState): ScannerPanel {
    return when {
        state.pendingCard != null -> ScannerPanel.Confirm(state.pendingCard)
        state.candidateCards.isNotEmpty() -> ScannerPanel.Choose(state.candidateCards)
        state.lastAddedCard != null -> ScannerPanel.Added(state.lastAddedCard)
        state.isSearching -> ScannerPanel.Searching
        else -> ScannerPanel.None
    }
}

@Composable
private fun SearchingIndicator() {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = AppColors.blue,
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            "Ricerca carta in corso...",
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun AddedCardBanner(card: com.emabuia.pokevault.data.remote.TcgCard) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.green.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubcomposeAsyncImage(
            model = ImageUrlUtils.safeImageUrl(card.images.small),
            contentDescription = card.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(60.dp)
                .clip(RoundedCornerShape(8.dp)),
            error = {
                ScannerCardImageFallback(
                    card = card,
                    compact = true,
                    modifier = Modifier.height(60.dp)
                )
            }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Check, null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Aggiunta!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Text(
                card.name,
                color = Color.White.copy(alpha = 0.95f),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text(
                "${card.set?.name ?: ""} #${card.number}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun CandidateCardPicker(
    cards: List<com.emabuia.pokevault.data.remote.TcgCard>,
    onSelect: (com.emabuia.pokevault.data.remote.TcgCard) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.surface.copy(alpha = 0.97f))
            .padding(14.dp)
    ) {
        Text(
            "Quale di queste?",
            color = AppColors.blue,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            "Hanno lo stesso numero: cambia l'espansione.",
            color = AppColors.textSecondary,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        cards.forEachIndexed { index, card ->
            if (index > 0) Spacer(modifier = Modifier.height(7.dp))
            CandidateRow(card = card, onSelect = { onSelect(card) })
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.textSecondary)
        ) {
            Icon(Icons.Default.Close, null, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(AppLocale.noneOfThese, fontSize = 14.sp)
        }
    }
}

/**
 * Una riga della rosa.
 *
 * L'espansione viene prima del nome: le tre carte hanno lo stesso numero e
 * quasi sempre lo stesso nome, quindi il campo che fa scegliere e' quello, e
 * deve essere il primo che l'occhio incontra.
 */
@Composable
private fun CandidateRow(
    card: com.emabuia.pokevault.data.remote.TcgCard,
    onSelect: () -> Unit
) {
    val price = card.cardmarket?.prices.minimumEurPriceOrZero()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .background(Color.White.copy(alpha = 0.05f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubcomposeAsyncImage(
            model = ImageUrlUtils.safeImageUrl(card.images.small),
            contentDescription = card.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(78.dp)
                .clip(RoundedCornerShape(8.dp)),
            error = {
                ScannerCardImageFallback(
                    card = card,
                    compact = true,
                    modifier = Modifier.height(78.dp)
                )
            }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                card.set?.name?.takeIf { it.isNotBlank() } ?: "Espansione sconosciuta",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                card.name,
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(7.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                CardMetaPill("#${card.number}", AppColors.blue)
                card.rarity?.takeIf { it.isNotBlank() }?.let {
                    CardMetaPill(it, AppColors.lavender)
                }
                if (price > 0.0) {
                    CardMetaPill("${"%.2f".format(price)} €", AppColors.green)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Conferma carta rilevata
// ═══════════════════════════════════════════════

@Composable
private fun PendingCardConfirmation(
    card: com.emabuia.pokevault.data.remote.TcgCard,
    condition: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val price = card.cardmarket?.prices.minimumEurPriceOrZero()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.surface.copy(alpha = 0.97f))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Check,
                null,
                tint = AppColors.green,
                modifier = Modifier
                    .size(18.dp)
                    .background(AppColors.green.copy(alpha = 0.18f), CircleShape)
                    .padding(3.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                AppLocale.recognizedCard,
                color = AppColors.green,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.3.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            // La carta e' l'elemento su cui si decide: merita di essere grande
            // abbastanza da riconoscerla a colpo d'occhio, senza avvicinare lo
            // schermo agli occhi.
            SubcomposeAsyncImage(
                model = ImageUrlUtils.safeImageUrl(card.images.small),
                contentDescription = card.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(132.dp)
                    .clip(RoundedCornerShape(10.dp)),
                error = {
                    ScannerCardImageFallback(
                        card = card,
                        compact = false,
                        modifier = Modifier.height(132.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    card.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                card.set?.name?.takeIf { it.isNotBlank() }?.let { setName ->
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        setName,
                        color = AppColors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(9.dp))

                // I dati di contorno su una riga che va a capo: letti di sfuggita
                // servono a confermare, non a essere studiati uno per uno.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    CardMetaPill("#${card.number}", AppColors.blue)
                    card.rarity?.takeIf { it.isNotBlank() }?.let {
                        CardMetaPill(it, AppColors.lavender)
                    }
                    card.hp?.takeIf { it.isNotBlank() }?.let {
                        CardMetaPill("$it HP", AppColors.orange)
                    }
                    if (price > 0.0) {
                        CardMetaPill("${"%.2f".format(price)} €", AppColors.green)
                    }
                }

                Spacer(modifier = Modifier.height(7.dp))

                // Ricorda con che condizione sta per entrare: e' una scelta fatta
                // prima e facile da dimenticare dopo dieci carte.
                Text(
                    condition,
                    color = AppColors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(46.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 18.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.textSecondary)
            ) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppLocale.discard, fontSize = 14.sp)
            }

            // L'azione che si ripete decine di volte di fila prende piu' spazio
            // dell'altra: e' quella che il pollice deve trovare senza guardare.
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.green)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(19.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppLocale.add, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Overlay zona di scansione
// ═══════════════════════════════════════════════

@Composable
private fun ScanZoneOverlay(
    isDetecting: Boolean,
    hasResult: Boolean,
    detectedName: String
) {
    // Colore degli angoli: verde a carta trovata, blu mentre cerca, oro appena
    // qualcosa si legge, bianco quando non c'e' ancora niente.
    val targetColor = when {
        hasResult -> AppColors.green
        isDetecting -> AppColors.blue
        detectedName.isNotBlank() -> AppColors.gold
        else -> Color.White.copy(alpha = 0.75f)
    }
    // Il cambio di stato si legge come una transizione, non come uno scatto.
    val frameColor by animateColorAsState(targetValue = targetColor, label = "frame-color")

    // Riga che scorre lungo la cornice mentre cerca: e' il solo elemento animato
    // dello schermo, e dice "sto lavorando" senza rubare spazio ai contenuti.
    val sweep = rememberInfiniteTransition(label = "sweep")
    val sweepProgress by sweep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep-progress"
    )

    // Canvas usa size.width/height che sono sempre le dimensioni reali renderizzate,
    // evitando il problema di BoxWithConstraints che riceve constraint non bounded
    // durante le recomposition causate da AnimatedVisibility (crop area "enorme").
    Canvas(modifier = Modifier.fillMaxSize()) {
        val zone = scanZoneRect(size.width, size.height)
        val cornerRadiusPx = 14.dp.toPx()

        // Oscura tutto tranne la zona di scansione
        val cutoutPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = zone,
                    cornerRadius = CornerRadius(cornerRadiusPx)
                )
            )
        }

        clipPath(cutoutPath, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.62f))
        }

        // Solo una traccia sottile del perimetro: a delimitare ci pensano gli
        // angoli, e un bordo pieno sopra la carta distrae piu' di quanto aiuti.
        drawRoundRect(
            color = frameColor.copy(alpha = 0.22f),
            topLeft = zone.topLeft,
            size = zone.size,
            cornerRadius = CornerRadius(cornerRadiusPx),
            style = Stroke(width = 1.dp.toPx())
        )

        drawFrameCorners(
            zone = zone,
            color = frameColor,
            cornerRadiusPx = cornerRadiusPx,
            strokeWidth = 3.5.dp.toPx()
        )

        if (isDetecting) {
            drawSweepLine(zone = zone, color = frameColor, progress = sweepProgress)
        }
    }
}

/**
 * Gli angoli a parentesi, il vocabolario visivo universale del "inquadra qui".
 * Disegnati con estremita' arrotondate per non sembrare tagliati.
 */
private fun DrawScope.drawFrameCorners(
    zone: Rect,
    color: Color,
    cornerRadiusPx: Float,
    strokeWidth: Float
) {
    val armLength = zone.width * 0.11f

    fun corner(x: Float, y: Float, horizontalTo: Float, verticalTo: Float) {
        drawLine(color, Offset(x, y), Offset(horizontalTo, y), strokeWidth, cap = StrokeCap.Round)
        drawLine(color, Offset(x, y), Offset(x, verticalTo), strokeWidth, cap = StrokeCap.Round)
    }

    // L'angolo parte dopo il raggio di curvatura, cosi' le due braccia restano rette.
    corner(
        x = zone.left, y = zone.top + cornerRadiusPx,
        horizontalTo = zone.left + armLength, verticalTo = zone.top + cornerRadiusPx + armLength
    )
    corner(
        x = zone.right, y = zone.top + cornerRadiusPx,
        horizontalTo = zone.right - armLength, verticalTo = zone.top + cornerRadiusPx + armLength
    )
    corner(
        x = zone.left, y = zone.bottom - cornerRadiusPx,
        horizontalTo = zone.left + armLength, verticalTo = zone.bottom - cornerRadiusPx - armLength
    )
    corner(
        x = zone.right, y = zone.bottom - cornerRadiusPx,
        horizontalTo = zone.right - armLength, verticalTo = zone.bottom - cornerRadiusPx - armLength
    )
}

/**
 * Riga luminosa che attraversa la cornice dall'alto in basso, con una scia che
 * sfuma: rende visibile che il lavoro sta avvenendo anche quando la ricerca
 * dura un secondo.
 */
private fun DrawScope.drawSweepLine(zone: Rect, color: Color, progress: Float) {
    val y = zone.top + zone.height * progress
    val trail = zone.height * 0.12f

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, color.copy(alpha = 0.35f)),
            startY = y - trail,
            endY = y
        ),
        topLeft = Offset(zone.left, (y - trail).coerceAtLeast(zone.top)),
        size = androidx.compose.ui.geometry.Size(
            zone.width,
            (y - (y - trail).coerceAtLeast(zone.top)).coerceAtLeast(0f)
        )
    )
    drawLine(
        color = color.copy(alpha = 0.9f),
        start = Offset(zone.left, y),
        end = Offset(zone.right, y),
        strokeWidth = 2.dp.toPx()
    )
}

// ═══════════════════════════════════════════════
// Permission Request
// ═══════════════════════════════════════════════

@Composable
private fun PermissionRequest(
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (shouldShowRationale)
                "La fotocamera serve per scansionare le carte Pokémon e aggiungerle alla collezione."
            else
                "Per usare lo scanner serve il permesso fotocamera.",
            color = AppColors.textSecondary,
            textAlign = TextAlign.Center,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue)
        ) {
            Text(AppLocale.grantPermission)
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text(AppLocale.goBack, color = AppColors.textMuted)
        }
    }
}

// ═══════════════════════════════════════════════
// Camera Preview: ritaglio della carta e doppia lettura OCR
// ═══════════════════════════════════════════════

@Composable
private fun CameraPreview(
    onFrameScanned: (ScannedFrame) -> Unit,
    flashEnabled: Boolean,
    scanEnabled: Boolean
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }

    // L'analyzer nasce una volta sola e gira su un thread di camera: se leggesse
    // stato Compose resterebbe fermo ai valori del primo frame (ed e' il motivo
    // per cui prima continuava ad analizzare anche a carta gia' trovata).
    val scanEnabledFlag = remember { AtomicBoolean(scanEnabled) }
    val frameSink = remember { AtomicReference(onFrameScanned) }
    SideEffect {
        scanEnabledFlag.set(scanEnabled)
        frameSink.set(onFrameScanned)
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizer.close()
            analyzerExecutor.shutdown()
        }
    }

    LaunchedEffect(flashEnabled) {
        cameraRef?.cameraControl?.enableTorch(flashEnabled)
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = runCatching { cameraProviderFuture.get() }
                    .onFailure { Timber.e(it, "Camera provider non disponibile") }
                    .getOrNull() ?: return@addListener

                // Il ViewPort esiste solo a layout fatto, e senza ViewPort l'analisi
                // vede una porzione di scena diversa da quella mostrata nel preview.
                previewView.doOnLayout {
                    bindCamera(
                        cameraProvider = cameraProvider,
                        previewView = previewView,
                        lifecycleOwner = lifecycleOwner,
                        analyzer = CardFrameAnalyzer(
                            recognizer = recognizer,
                            isEnabled = scanEnabledFlag::get,
                            onFrame = { frame -> frameSink.get()(frame) }
                        ),
                        analyzerExecutor = analyzerExecutor,
                        flashEnabled = flashEnabled,
                        onCameraReady = { cameraRef = it }
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun bindCamera(
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    lifecycleOwner: LifecycleOwner,
    analyzer: ImageAnalysis.Analyzer,
    analyzerExecutor: Executor,
    flashEnabled: Boolean,
    onCameraReady: (Camera) -> Unit
) {
    val preview = Preview.Builder().build().also {
        it.setSurfaceProvider(previewView.surfaceProvider)
    }

    // Senza AllowedResolutionMode l'analisi si ferma a 1080p, dove il numero da
    // collezione misura una ventina di pixel: al limite di cio che ML Kit legge.
    // Qui si pagano un paio di frame al secondo in cambio di pixel su quel campo.
    val resolutionSelector = ResolutionSelector.Builder()
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(2560, 1440),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
            )
        )
        .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
        .build()

    val imageAnalysis = ImageAnalysis.Builder()
        .setResolutionSelector(resolutionSelector)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also { it.setAnalyzer(analyzerExecutor, analyzer) }

    val useCases = UseCaseGroup.Builder()
        .addUseCase(preview)
        .addUseCase(imageAnalysis)
        .apply { previewView.viewPort?.let(::setViewPort) }
        .build()

    try {
        cameraProvider.unbindAll()
        val camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            useCases
        )
        onCameraReady(camera)
        camera.cameraControl.enableTorch(flashEnabled)
        focusOnScanZone(camera, previewView)
    } catch (e: Exception) {
        Timber.e(e, "Camera bind failed")
    }
}

/**
 * Punta la messa a fuoco sulla cornice invece che sul centro geometrico.
 * Il numero in basso a sinistra e' alto pochi pixel: fuori fuoco non lo legge
 * nessun OCR. Con l'auto-cancel la camera torna poi al fuoco continuo.
 */
private fun focusOnScanZone(camera: Camera, previewView: PreviewView) {
    if (previewView.width <= 0 || previewView.height <= 0) return

    val point = previewView.meteringPointFactory.createPoint(
        previewView.width / 2f,
        previewView.height * (0.5f - SCAN_ZONE_VERTICAL_OFFSET)
    )
    val action = FocusMeteringAction
        .Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
        .setAutoCancelDuration(FOCUS_AUTO_CANCEL_SECONDS, TimeUnit.SECONDS)
        .build()

    runCatching { camera.cameraControl.startFocusAndMetering(action) }
        .onFailure { Timber.w(it, "Messa a fuoco sulla cornice non riuscita") }
}

/**
 * Estrae da ogni frame le due letture su cui si regge lo scanner:
 *  - il testo dell'intera carta, con la posizione dei blocchi, da cui esce il
 *    nome (sta in alto a sinistra, non "nella prima riga utile");
 *  - il testo della sola striscia in fondo, ritagliata e ingrandita, da cui
 *    esce l'ID: e' l'unico campo uguale in tutte le lingue, ed e' troppo
 *    piccolo per sopravvivere a una lettura fatta insieme al resto.
 */
private class CardFrameAnalyzer(
    private val recognizer: TextRecognizer,
    private val isEnabled: () -> Boolean,
    private val onFrame: (ScannedFrame) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalyzedAt = 0L

    /**
     * Vero quando una lettura e scaduta in timeout: ML Kit puo ancora avere in
     * mano la bitmap, e riciclarla significherebbe fargli toccare memoria
     * liberata. In quel caso la si lascia al garbage collector.
     */
    private var recognitionPending = false

    override fun analyze(imageProxy: ImageProxy) {
        try {
            if (!isEnabled()) return

            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalyzedAt < ANALYSIS_THROTTLE_MS) return
            lastAnalyzedAt = now

            recognitionPending = false
            val card = imageProxy.toCardBitmap() ?: return
            try {
                // Anche un frame vuoto e informazione: dice che davanti
                // all obiettivo non c e piu niente, e il ViewModel lo usa per
                // riarmarsi. Per questo non lo filtriamo piu qui.
                onFrame(readFrame(card))
            } finally {
                if (!recognitionPending) card.recycle()
            }
        } catch (e: Exception) {
            Timber.w(e, "Analisi frame fallita")
        } finally {
            imageProxy.close()
        }
    }

    private fun readFrame(card: Bitmap): ScannedFrame {
        val blocks = recognize(card)
            ?.toTextBlocks(card.width.toFloat(), card.height.toFloat())
            .orEmpty()

        return ScannedFrame(blocks = blocks, idStripText = readIdStrip(card))
    }

    private fun readIdStrip(card: Bitmap): String {
        val strip = card.cropNormalized(top = ID_STRIP_TOP) ?: return ""
        val enhanced = runCatching { ImagePreprocessor.enhanceIdStrip(strip) }.getOrNull()
        return try {
            val enhancedText = recognize(enhanced ?: strip)?.text.orEmpty()
            // Il contrasto spinto a volte mangia i tratti sottili delle cifre: se
            // non ne sono uscite, vale il costo di rileggere la striscia grezza.
            if (enhanced == null || enhancedText.any { it.isDigit() }) {
                enhancedText
            } else {
                enhancedText + "\n" + recognize(strip)?.text.orEmpty()
            }
        } finally {
            if (!recognitionPending) {
                if (enhanced != null && enhanced !== strip) enhanced.recycle()
                strip.recycle()
            }
        }
    }

    /**
     * L'analyzer ha gia' un executor dedicato e la strategia KEEP_ONLY_LATEST:
     * aspettare qui il risultato e' il modo piu' semplice per non avere due
     * letture in volo sullo stesso frame. La bitmap e' gia' dritta, rotazione 0.
     */
    private fun recognize(bitmap: Bitmap): Text? {
        return try {
            Tasks.await(
                recognizer.process(InputImage.fromBitmap(bitmap, 0)),
                OCR_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
            )
        } catch (e: TimeoutException) {
            recognitionPending = true
            Timber.w("OCR oltre il timeout, frame saltato")
            null
        } catch (e: Exception) {
            Timber.w(e, "OCR fallito")
            null
        }
    }
}

// ═══════════════════════════════════════════════
// Geometria e ritagli
// ═══════════════════════════════════════════════

/**
 * La cornice di scansione, nelle coordinate di chi la riceve.
 *
 * La usano sia l'overlay (in coordinate schermo) sia l'analyzer (sul frame
 * raddrizzato): il ViewPort garantisce che le due abbiano le stesse
 * proporzioni, quindi una sola formula tiene allineati cio' che l'utente
 * inquadra e cio' che l'OCR legge davvero.
 */
private fun scanZoneRect(width: Float, height: Float): Rect {
    var zoneWidth = width * SCAN_ZONE_WIDTH_FRACTION
    var zoneHeight = zoneWidth / CARD_ASPECT_RATIO

    // In verticale non scatta mai; in orizzontale evita una cornice piu alta
    // dello schermo, che l analyzer ritaglierebbe storta.
    val maxHeight = height * SCAN_ZONE_MAX_HEIGHT_FRACTION
    if (zoneHeight > maxHeight) {
        zoneHeight = maxHeight
        zoneWidth = zoneHeight * CARD_ASPECT_RATIO
    }

    val left = (width - zoneWidth) / 2f
    val top = (height - zoneHeight) / 2f - height * SCAN_ZONE_VERTICAL_OFFSET
    return Rect(left, top, left + zoneWidth, top + zoneHeight)
}

/**
 * Ritaglia dal frame la sola carta inquadrata, dritta.
 *
 * `cropRect` arriva dal ViewPort e coincide con cio' che si vede nel preview;
 * la rotazione porta l'immagine in verticale. Solo dopo questi due passaggi la
 * cornice dell'overlay e il frame condividono le stesse coordinate: prima no,
 * ed e' li' che lo scanner leggeva la fascia centrale della carta invece del
 * nome in alto e dell'ID in basso.
 */
private fun ImageProxy.toCardBitmap(): Bitmap? {
    val frame = runCatching { toBitmap() }.getOrNull() ?: return null

    val crop = cropRect
    val rotation = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
    val upright = runCatching {
        Bitmap.createBitmap(frame, crop.left, crop.top, crop.width(), crop.height(), rotation, true)
    }.getOrNull()
    if (upright !== frame) frame.recycle()
    if (upright == null) return null

    val zone = scanZoneRect(upright.width.toFloat(), upright.height.toFloat())
    val x = zone.left.roundToInt().coerceIn(0, upright.width - 1)
    val y = zone.top.roundToInt().coerceIn(0, upright.height - 1)
    val width = zone.width.roundToInt().coerceIn(1, upright.width - x)
    val height = zone.height.roundToInt().coerceIn(1, upright.height - y)

    val card = runCatching { Bitmap.createBitmap(upright, x, y, width, height) }.getOrNull()
    if (card !== upright) upright.recycle()
    return card
}

/** Ritaglio in coordinate normalizzate [0..1] della bitmap. */
private fun Bitmap.cropNormalized(
    left: Float = 0f,
    top: Float = 0f,
    right: Float = 1f,
    bottom: Float = 1f
): Bitmap? {
    val x = (left * width).roundToInt().coerceIn(0, width - 1)
    val y = (top * height).roundToInt().coerceIn(0, height - 1)
    val cropWidth = ((right - left) * width).roundToInt().coerceIn(1, width - x)
    val cropHeight = ((bottom - top) * height).roundToInt().coerceIn(1, height - y)
    return runCatching { Bitmap.createBitmap(this, x, y, cropWidth, cropHeight) }.getOrNull()
}

/** Blocchi ML Kit con la posizione normalizzata rispetto alla carta. */
private fun Text.toTextBlocks(width: Float, height: Float): List<OCRTextBlock> {
    if (width <= 0f || height <= 0f) return emptyList()

    return textBlocks.mapNotNull { block ->
        val text = block.text.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val box = block.boundingBox?.let { rect ->
            ZoneBoundingBox(
                left = rect.left / width,
                top = rect.top / height,
                right = rect.right / width,
                bottom = rect.bottom / height
            )
        }
        val confidence = block.lines
            .flatMap { it.elements }
            .mapNotNull { it.confidence }
            .average()
            .toFloat()
            .takeIf { !it.isNaN() } ?: 0.8f

        OCRTextBlock(
            text = text,
            confidence = confidence,
            boundingBox = box,
            normalizedY = box?.let { (it.top + it.bottom) / 2f } ?: 0f
        )
    }.sortedBy { it.normalizedY }
}

/**
 * Quota da cui parte la striscia con l'ID, in frazione dell'altezza carta.
 * Generosa: nessuno allinea la carta alla cornice al pixel, e una striscia
 * troppo stretta taglia via proprio il numero.
 */
private const val ID_STRIP_TOP = 0.78f

/**
 * Freno minimo. Il ritmo vero lo impone l attesa sincrona delle due passate
 * OCR: alzarlo rallenterebbe solo il riconoscimento sui telefoni veloci.
 */
private const val ANALYSIS_THROTTLE_MS = 100L

private const val OCR_TIMEOUT_MS = 2000L

private const val FOCUS_AUTO_CANCEL_SECONDS = 3L

/** Dissolvenza fra i pannelli: corta, deve leggersi come un cambio, non come un volo. */
private const val PANEL_FADE_MS = 160
