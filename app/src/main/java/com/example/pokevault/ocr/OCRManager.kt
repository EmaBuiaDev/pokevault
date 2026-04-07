package com.example.pokevault.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.pokevault.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Manager principale della pipeline OCR per carte Pokemon.
 *
 * Orchestrazione completa:
 * 1. Preprocessing immagine (zone crop, contrasto, denoise, glare removal)
 * 2. Text Detection + Recognition (PaddleOCR TFLite o ML Kit)
 * 3. Post-processing Pokemon-specifico (estrazione campi strutturati)
 * 4. Output JSON strutturato
 *
 * ═══════════════════════════════════════════════════
 * ARCHITETTURA DELLA PIPELINE
 * ═══════════════════════════════════════════════════
 *
 *   Bitmap (foto/frame camera)
 *        │
 *        ▼
 *   ┌─────────────────────┐
 *   │  ImagePreprocessor   │  resize, glare removal, contrast, sharpen
 *   │  (full card + zones) │  zone crop (TOP, FOOTER, MIDDLE)
 *   └─────────┬───────────┘
 *             │
 *        ┌────┴────┐
 *        ▼         ▼
 *   Full-frame   Zone-based     (analisi parallela)
 *   OCR pass     OCR pass
 *        │         │
 *        ▼         ▼
 *   ┌─────────────────────┐
 *   │    OCR Engine        │  PaddleOCR TFLite (primario)
 *   │  (detect + recog)    │  ML Kit (fallback)
 *   └─────────┬───────────┘
 *             │
 *             ▼
 *   ┌─────────────────────┐
 *   │   CardFieldParser    │  Nome, numero, HP, rarita, variante,
 *   │  (Pokemon-specific)  │  illustratore, set, supertype
 *   └─────────┬───────────┘
 *             │
 *             ▼
 *   ┌─────────────────────┐
 *   │   CardOCRResult      │  JSON strutturato
 *   │   (output finale)    │  pronto per ricerca API
 *   └─────────────────────┘
 *
 * ═══════════════════════════════════════════════════
 * INTEGRAZIONE IN JETPACK COMPOSE
 * ═══════════════════════════════════════════════════
 *
 *   // Nel ViewModel:
 *   private val ocrManager = OCRManager(application)
 *
 *   init {
 *       viewModelScope.launch { ocrManager.initialize() }
 *   }
 *
 *   fun onFrameCaptured(bitmap: Bitmap) {
 *       viewModelScope.launch {
 *           val result = ocrManager.analyzeCardImage(bitmap)
 *           if (result.isSearchable()) {
 *               // Usa result.cardName e result.cardNumber per la ricerca
 *           }
 *       }
 *   }
 *
 *   override fun onCleared() {
 *       ocrManager.release()
 *   }
 *
 * ═══════════════════════════════════════════════════
 * SCELTA DEL MOTORE OCR
 * ═══════════════════════════════════════════════════
 *
 * PaddleOCR Lite (consigliato per produzione):
 *   + Piu accurato su testo piccolo (numeri carta, footer)
 *   + Open-source, completamente personalizzabile
 *   + Fine-tuning possibile su dataset carte Pokemon
 *   + Supporta testo ruotato/prospettico
 *   - Richiede modelli TFLite in assets (~8MB)
 *   - Setup piu complesso (conversione modelli)
 *
 * ML Kit (consigliato per sviluppo rapido):
 *   + Zero configurazione, funziona subito
 *   + Buona performance generale
 *   + Gestione automatica GPU/NNAPI
 *   - Meno preciso su testo molto piccolo
 *   - Non personalizzabile
 *   - Meno robusto con prospettiva/blur
 *
 * Questa implementazione usa ML Kit come default con fallback automatico,
 * e supporta PaddleOCR quando i modelli TFLite sono presenti in assets/.
 */
class OCRManager(private val context: Context) {

    private var engine: OCREngine? = null
    private var isInitialized = false

    /** Quale engine e attualmente in uso */
    val activeEngineName: String
        get() = engine?.engineName ?: "None"

    /**
     * Inizializza la pipeline OCR.
     * Tenta PaddleOCR TFLite, con fallback a ML Kit.
     *
     * @param preferPaddleOCR Se true, tenta prima PaddleOCR TFLite
     */
    suspend fun initialize(preferPaddleOCR: Boolean = true) {
        withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        if (preferPaddleOCR && hasTFLiteModels()) {
            try {
                val paddleEngine = PaddleOCREngine(context)
                paddleEngine.initialize()
                engine = paddleEngine
                isInitialized = true
                if (BuildConfig.DEBUG) Log.i(TAG, "Inizializzato con PaddleOCR TFLite")
                return@withContext
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "PaddleOCR non disponibile, fallback a ML Kit: ${e.message}")
            }
        }

        // Fallback: ML Kit
        try {
            val mlKitEngine = MLKitOCREngine()
            mlKitEngine.initialize()
            engine = mlKitEngine
            isInitialized = true
            if (BuildConfig.DEBUG) Log.i(TAG, "Inizializzato con ML Kit (fallback)")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Nessun engine OCR disponibile: ${e.message}", e)
            throw e
        }
    } }

    /** Rilascia tutte le risorse */
    fun release() {
        engine?.release()
        engine = null
        isInitialized = false
    }

    fun isReady(): Boolean = isInitialized && engine?.isReady() == true

    // ═══════════════════════════════════════════
    // API PRINCIPALE
    // ═══════════════════════════════════════════

    /**
     * Analizza un'immagine di una carta Pokemon ed estrae tutti i campi.
     *
     * Pipeline completa:
     * 1. Preprocessing (resize, glare removal, contrast, sharpen)
     * 2. OCR full-frame
     * 3. OCR zone-based (TOP + FOOTER) per maggiore precisione
     * 4. Merge risultati + parsing Pokemon-specifico
     *
     * @param bitmap Foto o frame della carta (qualsiasi risoluzione)
     * @return CardOCRResult con tutti i campi estratti + JSON
     */
    suspend fun analyzeCardImage(bitmap: Bitmap): CardOCRResult = withContext(Dispatchers.Default) {
        if (!isReady()) {
            if (BuildConfig.DEBUG) Log.w(TAG, "OCR non inizializzato, tentativo di inizializzazione...")
            initialize()
        }

        val ocr = engine ?: return@withContext CardOCRResult(
            rawText = "", confidence = 0f
        )

        try {
            // Fase 1: Preprocessing full-card
            val preprocessed = ImagePreprocessor.preprocessFullCard(bitmap)

            // Fase 2: OCR full-frame
            val fullBlocks = ocr.recognizeText(preprocessed)
            val fullText = fullBlocks.joinToString("\n") { it.text }
            val fullResult = CardFieldParser.parseFullText(fullText)

            // Fase 3: OCR zone-based per campi critici (nome + numero)
            val zonedResult = analyzeCardZones(bitmap, ocr)

            // Fase 4: Merge dei risultati
            val merged = if (zonedResult != null) {
                CardFieldParser.mergeResults(fullResult, zonedResult)
            } else {
                fullResult
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Analisi completata: ${merged.cardName} #${merged.cardNumber} " +
                        "(confidence: ${merged.confidence})")
            }

            merged
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Errore analisi immagine: ${e.message}", e)
            CardOCRResult(rawText = "", confidence = 0f)
        }
    }

    /**
     * Estrae i campi della carta dal testo OCR gia riconosciuto.
     * Utile quando il testo viene dal flusso camera (onTextDetected).
     *
     * @param rawText Testo grezzo dall'OCR
     * @return CardOCRResult con i campi estratti
     */
    fun extractCardFields(rawText: String): CardOCRResult {
        return CardFieldParser.parseFullText(rawText)
    }

    /**
     * Estrae i campi della carta dai blocchi di testo con posizione.
     * Piu accurato del raw text perche usa le posizioni per le zone.
     *
     * @param textBlocks Blocchi OCR con bounding box
     * @return CardOCRResult con parsing zone-aware
     */
    fun extractCardFields(textBlocks: List<OCRTextBlock>): CardOCRResult {
        return CardFieldParser.parseZonedText(textBlocks)
    }

    /**
     * Rileva il simbolo/codice del set dalla zona footer della carta.
     * Analizza la regione in basso a sinistra dove si trova il logo del set.
     *
     * Nota: il riconoscimento del simbolo set tramite OCR e limitato
     * perche i simboli sono grafici, non testo. Per un rilevamento piu accurato,
     * si consiglia template matching o un classificatore CNN dedicato.
     *
     * @param bitmap Immagine della carta
     * @return Codice set riconosciuto dal testo adiacente, o null
     */
    suspend fun detectSetSymbol(bitmap: Bitmap): String? = withContext(Dispatchers.Default) {
        val ocr = engine ?: return@withContext null

        try {
            // Crop della zona footer (dove si trova il simbolo del set)
            val footerRegion = ImagePreprocessor.ZoneCropRegion(
                zone = CardZone.FOOTER,
                top = 0.90f, bottom = 1.0f,
                left = 0.02f, right = 0.50f // Solo meta sinistra del footer
            )

            val footerBitmap = ImagePreprocessor.preprocessZone(bitmap, footerRegion)
            val blocks = ocr.recognizeText(footerBitmap)
            footerBitmap.recycle()

            val footerText = blocks.joinToString(" ") { it.text }

            // Cerca codice set nel testo del footer
            // I set codes sono tipicamente 2-4 lettere maiuscole (es. "SV", "PAL", "OBF")
            val setCodePattern = Regex("""\b([A-Z]{2,5})\b""")
            val match = setCodePattern.find(footerText)

            match?.groupValues?.get(1)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Errore rilevamento set symbol: ${e.message}")
            null
        }
    }

    /**
     * Stima la qualita dell'immagine per l'OCR.
     * @return Score 0..1 (< 0.3 = scarsa, 0.3-0.6 = media, > 0.6 = buona)
     */
    fun estimateImageQuality(bitmap: Bitmap): Float {
        return ImagePreprocessor.estimateQuality(bitmap)
    }

    // ═══════════════════════════════════════════
    // ANALISI PER ZONE
    // ═══════════════════════════════════════════

    /**
     * Analizza le zone critiche della carta separatamente.
     * Piu lento ma piu preciso per campi specifici.
     */
    private suspend fun analyzeCardZones(
        bitmap: Bitmap,
        ocr: OCREngine
    ): CardOCRResult? {
        try {
            val resized = ImagePreprocessor.resizeForOCR(bitmap)

            // Processa zona TOP (nome + HP) e FOOTER (numero + set) in parallelo
            val topRegion = ImagePreprocessor.CARD_ZONES.first { it.zone == CardZone.TOP }
            val footerRegion = ImagePreprocessor.CARD_ZONES.first { it.zone == CardZone.FOOTER }

            val topBitmap = ImagePreprocessor.preprocessZone(resized, topRegion)
            val footerBitmap = ImagePreprocessor.preprocessZone(resized, footerRegion)

            val topBlocks = ocr.recognizeText(topBitmap)
            val footerBlocks = ocr.recognizeText(footerBitmap)

            topBitmap.recycle()
            footerBitmap.recycle()

            // Combina blocchi con posizioni corrette
            val allBlocks = mutableListOf<OCRTextBlock>()

            // Blocchi top: normalizedY nel range 0.0-0.10
            topBlocks.forEach { block ->
                allBlocks.add(block.copy(
                    normalizedY = block.normalizedY * 0.10f
                ))
            }

            // Blocchi footer: normalizedY nel range 0.90-1.0
            footerBlocks.forEach { block ->
                allBlocks.add(block.copy(
                    normalizedY = 0.90f + block.normalizedY * 0.10f
                ))
            }

            if (allBlocks.isEmpty()) return null

            return CardFieldParser.parseZonedText(allBlocks)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Analisi zone fallita: ${e.message}")
            return null
        }
    }

    // ═══════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════

    /** Verifica se i modelli TFLite PaddleOCR sono presenti in assets */
    private fun hasTFLiteModels(): Boolean {
        return try {
            val assets = context.assets.list("") ?: emptyArray()
            "det_model.tflite" in assets && "rec_model.tflite" in assets
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "OCRManager"
    }
}
