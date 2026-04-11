package com.emabuia.pokevault.ocr

import android.graphics.Bitmap
import android.util.Log
import com.emabuia.pokevault.BuildConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Motore OCR basato su Google ML Kit (gratuito, on-device).
 *
 * Pro:
 * - Zero setup: nessun modello da scaricare/convertire
 * - Ottimizzato per mobile (Google Neural API)
 * - Buon supporto per testo latino
 * - Restituisce bounding box per blocco/riga/parola
 *
 * Contro:
 * - Closed-source (ma gratuito e on-device)
 * - Meno accurato su testo molto piccolo o ruotato
 * - Non personalizzabile
 *
 * Usato come fallback quando PaddleOCR TFLite non e disponibile,
 * oppure come engine primario durante lo sviluppo.
 */
class MLKitOCREngine : OCREngine {

    override val engineName = "MLKit-TextRecognition"

    private var recognizer: TextRecognizer? = null
    private var ready = false

    override suspend fun initialize() {
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        ready = true
        if (BuildConfig.DEBUG) Log.i(TAG, "ML Kit Text Recognition inizializzato")
    }

    override fun release() {
        recognizer?.close()
        recognizer = null
        ready = false
    }

    override fun isReady(): Boolean = ready

    override suspend fun recognizeText(bitmap: Bitmap): List<OCRTextBlock> {
        val rec = recognizer ?: return emptyList()
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        return suspendCancellableCoroutine { continuation ->
            rec.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val blocks = mutableListOf<OCRTextBlock>()
                    val imageH = bitmap.height.toFloat()
                    val imageW = bitmap.width.toFloat()

                    for (block in visionText.textBlocks) {
                        val rect = block.boundingBox

                        // Crea bounding box normalizzato
                        val bbox = if (rect != null) {
                            ZoneBoundingBox(
                                left = rect.left / imageW,
                                top = rect.top / imageH,
                                right = rect.right / imageW,
                                bottom = rect.bottom / imageH
                            )
                        } else null

                        // Confidenza media delle righe
                        val confidence = block.lines
                            .flatMap { it.elements }
                            .mapNotNull { it.confidence }
                            .average()
                            .toFloat()
                            .takeIf { !it.isNaN() } ?: 0.8f

                        blocks.add(OCRTextBlock(
                            text = block.text,
                            confidence = confidence,
                            boundingBox = bbox,
                            normalizedY = bbox?.let { (it.top + it.bottom) / 2f } ?: 0f
                        ))
                    }

                    // Ordina per posizione verticale
                    blocks.sortBy { it.normalizedY }

                    if (continuation.isActive) {
                        continuation.resume(blocks)
                    }
                }
                .addOnFailureListener { e ->
                    if (BuildConfig.DEBUG) Log.w(TAG, "ML Kit recognition fallito: ${e.message}")
                    if (continuation.isActive) {
                        continuation.resume(emptyList())
                    }
                }
        }
    }

    companion object {
        private const val TAG = "MLKitOCREngine"
    }
}
