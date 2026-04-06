package com.example.pokevault.ocr

import android.graphics.Bitmap

/**
 * Interfaccia per i motori OCR.
 * Permette di scambiare facilmente tra PaddleOCR TFLite, ML Kit, Tesseract, ecc.
 */
interface OCREngine {

    /** Nome del motore (per logging/debug) */
    val engineName: String

    /** Inizializza il motore (caricamento modelli, warmup) */
    suspend fun initialize()

    /** Rilascia risorse (modelli, buffer) */
    fun release()

    /** true se il motore e pronto per l'inferenza */
    fun isReady(): Boolean

    /**
     * Riconosce testo da una bitmap.
     * @param bitmap Immagine gia preprocessata
     * @return Lista di blocchi di testo riconosciuti con posizione e confidenza
     */
    suspend fun recognizeText(bitmap: Bitmap): List<OCRTextBlock>
}

/**
 * Blocco di testo riconosciuto dall'OCR engine.
 */
data class OCRTextBlock(
    val text: String,
    val confidence: Float,
    val boundingBox: ZoneBoundingBox? = null,
    /** Posizione verticale normalizzata [0..1] nell'immagine originale */
    val normalizedY: Float = 0f
)
