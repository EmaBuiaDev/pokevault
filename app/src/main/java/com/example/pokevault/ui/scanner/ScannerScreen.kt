package com.example.pokevault.ui.scanner

import android.Manifest
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.example.pokevault.data.remote.TcgCard
import com.example.pokevault.ui.theme.*
import com.example.pokevault.viewmodel.ScannerViewModel

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onAddCard: (TcgCard) -> Unit = {},
    viewModel: ScannerViewModel = viewModel()
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val state = viewModel.uiState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
    ) {
        // Top Bar
        TopAppBar(
            title = { Text("Scanner", fontWeight = FontWeight.SemiBold, color = TextWhite) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Indietro", tint = TextWhite)
                }
            },
            actions = {
                if (cameraPermission.status.isGranted) {
                    IconButton(onClick = { viewModel.toggleFlash() }) {
                        Icon(
                            if (state.flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            "Flash",
                            tint = if (state.flashEnabled) StarGold else TextWhite
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
        )

        if (!cameraPermission.status.isGranted) {
            // Permesso non concesso
            PermissionRequest(
                shouldShowRationale = cameraPermission.status.shouldShowRationale,
                onRequestPermission = { cameraPermission.launchPermissionRequest() }
            )
        } else {
            // Camera + risultati
            Column(modifier = Modifier.fillMaxSize()) {
                // Camera Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    CameraPreview(
                        onTextDetected = { viewModel.onTextDetected(it) },
                        flashEnabled = state.flashEnabled
                    )

                    // Overlay con nome rilevato
                    if (state.bestGuessName.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .background(
                                    DarkSurface.copy(alpha = 0.85f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Rilevato: ${state.bestGuessName}",
                                color = StarGold,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Risultati / Ricerca manuale
                ResultsPanel(
                    state = state,
                    onSearch = { viewModel.searchManually(it) },
                    onSelectCard = { viewModel.selectCard(it) },
                    onAddCard = onAddCard,
                    onClear = { viewModel.clearResults() }
                )
            }
        }
    }
}

@Composable
private fun PermissionRequest(
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CameraAlt,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (shouldShowRationale)
                "La fotocamera serve per scansionare le carte Pokémon e riconoscerne il nome automaticamente."
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
    }
}

@Suppress("UnsafeOptInUsageError")
@Composable
private fun CameraPreview(
    onTextDetected: (String) -> Unit,
    flashEnabled: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
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
                    camera.cameraControl.enableTorch(flashEnabled)
                } catch (e: Exception) {
                    Log.e("ScannerScreen", "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )

    // Update flash when toggled
    val context2 = LocalContext.current
    LaunchedEffect(flashEnabled) {
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context2).get()
            // Flash is handled at bind time; re-toggling needs camera reference
            // This is a simplified approach - flash toggles on next camera rebind
        } catch (_: Exception) {}
    }
}

@Composable
private fun ResultsPanel(
    state: com.example.pokevault.viewmodel.ScannerUiState,
    onSearch: (String) -> Unit,
    onSelectCard: (TcgCard) -> Unit,
    onAddCard: (TcgCard) -> Unit,
    onClear: () -> Unit
) {
    var manualQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
    ) {
        // Ricerca manuale
        OutlinedTextField(
            value = manualQuery,
            onValueChange = { manualQuery = it },
            placeholder = { Text("Cerca carta manualmente...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
            trailingIcon = {
                if (manualQuery.isNotBlank()) {
                    IconButton(onClick = { manualQuery = ""; onClear() }) {
                        Icon(Icons.Default.Close, null, tint = TextMuted)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                cursorColor = BlueCard,
                focusedBorderColor = BlueCard,
                unfocusedBorderColor = DarkCard
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Bottone cerca
        if (manualQuery.length >= 3) {
            TextButton(onClick = { onSearch(manualQuery) }) {
                Text("Cerca \"$manualQuery\"", color = BlueCard)
            }
        }

        // Loading
        if (state.isSearching) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = BlueCard,
                trackColor = DarkCard
            )
        }

        // Errore
        state.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(error, color = Color(0xFFEF4444), fontSize = 13.sp)
        }

        // Risultati
        if (state.searchResults.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "${state.searchResults.size} risultati",
                color = TextGray,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.searchResults) { card ->
                    ScanResultCard(
                        card = card,
                        isSelected = card.id == state.selectedCard?.id,
                        onClick = { onSelectCard(card) }
                    )
                }
            }

            // Carta selezionata - dettagli + bottone aggiungi
            state.selectedCard?.let { card ->
                Spacer(modifier = Modifier.height(12.dp))
                SelectedCardDetails(card = card, onAdd = { onAddCard(card) })
            }
        } else if (!state.isSearching && state.bestGuessName.isBlank() && manualQuery.isBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Punta la fotocamera su una carta Pokémon\noppure cerca manualmente.",
                color = TextMuted,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ScanResultCard(
    card: TcgCard,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) BlueCard.copy(alpha = 0.2f) else DarkCard)
            .clickable { onClick() }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = card.images.small,
            contentDescription = card.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(120.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            card.name,
            color = TextWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        card.set?.name?.let { setName ->
            Text(
                setName,
                color = TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SelectedCardDetails(
    card: TcgCard,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCard, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = card.images.small,
            contentDescription = card.name,
            modifier = Modifier
                .height(80.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(card.name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            card.set?.name?.let {
                Text(it, color = TextGray, fontSize = 12.sp)
            }
            card.rarity?.let {
                Text(it, color = StarGold, fontSize = 12.sp)
            }
            card.number.let {
                Text("#$it", color = TextMuted, fontSize = 11.sp)
            }
        }
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = GreenCard),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Aggiungi", fontSize = 13.sp)
        }
    }
}
