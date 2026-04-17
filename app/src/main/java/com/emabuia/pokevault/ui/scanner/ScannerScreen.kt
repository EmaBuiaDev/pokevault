package com.emabuia.pokevault.ui.scanner

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.emabuia.pokevault.BuildConfig
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.viewmodel.ScannerViewModel

/**
 * Proporzioni carta Pokemon standard (63mm × 88mm).
 * Usato per calcolare la zona di scansione.
 */
private const val CARD_ASPECT_RATIO = 63f / 88f // ~0.716

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    viewModel: ScannerViewModel = viewModel()
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val state = viewModel.uiState

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
            onTextDetected = { viewModel.onTextDetected(it) },
            flashEnabled = state.flashEnabled,
            scanEnabled = state.pendingCard == null && state.candidateCards.isEmpty() && state.lastAddedCard == null
        )

        // Overlay zona di scansione (card-shaped)
        ScanZoneOverlay(
            isDetecting = state.isSearching,
            hasResult = state.pendingCard != null || state.candidateCards.isNotEmpty() || state.lastAddedCard != null,
            detectedName = state.detectedName
        )

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
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
                Text(
                    text = "${state.addedCount} aggiunte",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .background(GreenCard.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            IconButton(onClick = { viewModel.toggleFlash() }) {
                Icon(
                    if (state.flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    "Flash",
                    tint = if (state.flashEnabled) StarGold else Color.White,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(6.dp)
                )
            }
        }

        // Istruzione iniziale (sopra la zona di scansione)
        if (state.pendingCard == null && state.candidateCards.isEmpty() && state.lastAddedCard == null && !state.isSearching &&
            state.detectedName.isBlank()
        ) {
            Text(
                "Posiziona la carta nella cornice",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(top = 56.dp)
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
            // Errore
            state.errorMessage?.let { error ->
                Text(
                    error,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .background(Color(0xCCEF4444), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Searching indicator
            if (state.isSearching) {
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = BlueCard,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        buildString {
                            append("Riconoscimento")
                            if (state.detectedName.isNotBlank()) append(" \"${state.detectedName}\"")
                            if (state.detectedNumber.isNotBlank()) append(" #${state.detectedNumber}")
                            append("...")
                        },
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Carta trovata: anteprima con conferma
            AnimatedVisibility(
                visible = state.pendingCard != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                state.pendingCard?.let { card ->
                    PendingCardConfirmation(
                        card = card,
                        onConfirm = { viewModel.confirmAdd() },
                        onDismiss = { viewModel.dismissCard() }
                    )
                }
            }

            AnimatedVisibility(
                visible = state.pendingCard == null && state.candidateCards.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                CandidateCardPicker(
                    cards = state.candidateCards,
                    onSelect = { viewModel.selectCandidate(it) },
                    onDismiss = { viewModel.dismissCard() }
                )
            }

            // Carta appena aggiunta - conferma visiva
            AnimatedVisibility(
                visible = state.lastAddedCard != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                state.lastAddedCard?.let { card ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GreenCard.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = card.images.small,
                            contentDescription = card.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(60.dp)
                                .clip(RoundedCornerShape(8.dp))
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
            }
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
            .background(DarkSurface.copy(alpha = 0.96f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(
            "Più risultati trovati",
            color = BlueCard,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Tocca la carta corretta per confermare.",
            color = TextGray,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        cards.forEach { card ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(card) }
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = card.images.small,
                    contentDescription = card.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        card.name,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        buildString {
                            append(card.set?.name ?: "Set sconosciuto")
                            append(" · #")
                            append(card.number)
                        },
                        color = TextGray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val metadata = listOfNotNull(card.supertype.takeIf { it.isNotBlank() }, card.rarity)
                    if (metadata.isNotEmpty()) {
                        Text(
                            metadata.joinToString(" · "),
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextGray)
        ) {
            Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Nessuna di queste", fontSize = 14.sp)
        }
    }
}

// ═══════════════════════════════════════════════
// Conferma carta rilevata
// ═══════════════════════════════════════════════

@Composable
private fun PendingCardConfirmation(
    card: com.emabuia.pokevault.data.remote.TcgCard,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            "Carta riconosciuta",
            color = BlueCard,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Card info
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = card.images.small,
                contentDescription = card.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    card.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (card.set != null) {
                    Text(
                        card.set.name,
                        color = TextGray,
                        fontSize = 13.sp
                    )
                }
                Text(
                    "#${card.number}" + (card.rarity?.let { " · $it" } ?: ""),
                    color = TextMuted,
                    fontSize = 12.sp
                )
                if (card.hp != null) {
                    Text(
                        "${card.hp} HP",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottoni conferma / scarta
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Scarta
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextGray
                )
            ) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scarta", fontSize = 14.sp)
            }

            // Aggiungi
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenCard
                )
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Aggiungi", fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
    // Colore bordo: blu in scansione, verde se trovata, bianco default
    val borderColor = when {
        hasResult -> GreenCard
        isDetecting -> BlueCard
        detectedName.isNotBlank() -> StarGold
        else -> Color.White.copy(alpha = 0.6f)
    }

    val borderWidth = if (isDetecting || hasResult) 3.dp else 2.dp

    // Canvas usa size.width/height che sono sempre le dimensioni reali renderizzate,
    // evitando il problema di BoxWithConstraints che riceve constraint non bounded
    // durante le recomposition causate da AnimatedVisibility (crop area "enorme").
    Canvas(modifier = Modifier.fillMaxSize()) {
        val screenW = size.width
        val screenH = size.height

        // Zona di scansione: carta centrata, larga 75% dello schermo
        val zoneW = screenW * 0.75f
        val zoneH = zoneW / CARD_ASPECT_RATIO
        val zoneLeft = (screenW - zoneW) / 2f
        val zoneTop = (screenH - zoneH) / 2f - screenH * 0.05f

        val cornerRadiusPx = 12.dp.toPx()
        val borderWidthPx = borderWidth.toPx()

        // Oscura tutto tranne la zona di scansione
        val cutoutPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(zoneLeft, zoneTop, zoneLeft + zoneW, zoneTop + zoneH),
                    cornerRadius = CornerRadius(cornerRadiusPx)
                )
            )
        }

        clipPath(cutoutPath, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.55f))
        }

        // Bordo della zona di scansione
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(zoneLeft, zoneTop),
            size = androidx.compose.ui.geometry.Size(zoneW, zoneH),
            cornerRadius = CornerRadius(cornerRadiusPx),
            style = Stroke(width = borderWidthPx)
        )

        // Angoli piu spessi per effetto "mirino"
        val cornerLen = zoneW * 0.08f
        val thickStroke = borderWidthPx * 2.5f

        // Top-left
        drawLine(borderColor, Offset(zoneLeft, zoneTop + cornerRadiusPx), Offset(zoneLeft, zoneTop + cornerLen), thickStroke)
        drawLine(borderColor, Offset(zoneLeft + cornerRadiusPx, zoneTop), Offset(zoneLeft + cornerLen, zoneTop), thickStroke)
        // Top-right
        drawLine(borderColor, Offset(zoneLeft + zoneW, zoneTop + cornerRadiusPx), Offset(zoneLeft + zoneW, zoneTop + cornerLen), thickStroke)
        drawLine(borderColor, Offset(zoneLeft + zoneW - cornerRadiusPx, zoneTop), Offset(zoneLeft + zoneW - cornerLen, zoneTop), thickStroke)
        // Bottom-left
        drawLine(borderColor, Offset(zoneLeft, zoneTop + zoneH - cornerRadiusPx), Offset(zoneLeft, zoneTop + zoneH - cornerLen), thickStroke)
        drawLine(borderColor, Offset(zoneLeft + cornerRadiusPx, zoneTop + zoneH), Offset(zoneLeft + cornerLen, zoneTop + zoneH), thickStroke)
        // Bottom-right
        drawLine(borderColor, Offset(zoneLeft + zoneW, zoneTop + zoneH - cornerRadiusPx), Offset(zoneLeft + zoneW, zoneTop + zoneH - cornerLen), thickStroke)
        drawLine(borderColor, Offset(zoneLeft + zoneW - cornerRadiusPx, zoneTop + zoneH), Offset(zoneLeft + zoneW - cornerLen, zoneTop + zoneH), thickStroke)
    }
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
            .background(DarkBackground)
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
            color = TextGray,
            textAlign = TextAlign.Center,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = BlueCard)
        ) {
            Text("Concedi permesso")
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text("Torna indietro", color = TextMuted)
        }
    }
}

// ═══════════════════════════════════════════════
// Camera Preview con crop alla scan zone
// ═══════════════════════════════════════════════

@Suppress("UnsafeOptInUsageError")
@Composable
private fun CameraPreview(
    onTextDetected: (String) -> Unit,
    flashEnabled: Boolean,
    scanEnabled: Boolean
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var cameraRef by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    // Flag per evitare di processare piu frame contemporaneamente
    var isProcessing by remember { mutableStateOf(false) }

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
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1920, 1080),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                            // Non processare se OCR e in pausa (carta gia trovata)
                            if (!scanEnabled || isProcessing) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                isProcessing = true

                                // Crop al centro dell'immagine (zona della carta)
                                val image = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )

                                recognizer.process(image)
                                    .addOnSuccessListener { visionText ->
                                        if (visionText.text.isNotBlank()) {
                                            onTextDetected(visionText.text)
                                        }
                                    }
                                    .addOnCompleteListener {
                                        isProcessing = false
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    cameraRef = camera
                    camera.cameraControl.enableTorch(flashEnabled)
                    camera.cameraControl.cancelFocusAndMetering()
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.e("ScannerScreen", "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
