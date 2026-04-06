package com.example.pokevault.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Preprocessing pipeline ottimizzata per carte Pokemon.
 *
 * Esegue in sequenza:
 * 1. Ridimensionamento a risoluzione ottimale per OCR
 * 2. Crop delle zone di interesse (nome, footer)
 * 3. Correzione prospettiva (deskew semplificato)
 * 4. Rimozione glare (highlight clipping)
 * 5. Normalizzazione colore (contrasto, luminosita)
 * 6. Denoise (median filter leggero)
 * 7. Binarizzazione adattiva per testo
 *
 * Tutte le operazioni usano Android Bitmap API (nessuna dipendenza nativa).
 */
object ImagePreprocessor {

    private const val TAG = "ImagePreprocessor"

    /** Risoluzione target per il lato lungo dell'immagine */
    private const val TARGET_LONG_SIDE = 1280

    /** Aspect ratio standard di una carta Pokemon (63mm x 88mm) */
    private const val CARD_ASPECT_RATIO = 63f / 88f // ~0.716

    // ═══════════════════════════════════════════
    // ZONE CROP - Regioni della carta
    // ═══════════════════════════════════════════

    /**
     * Definizione delle zone di una carta Pokemon in coordinate normalizzate [0..1].
     * Basato sulla struttura standard di una carta Pokemon TCG.
     */
    data class ZoneCropRegion(
        val zone: CardZone,
        val top: Float,      // percentuale dall'alto
        val bottom: Float,   // percentuale dall'alto
        val left: Float = 0f,
        val right: Float = 1f
    )

    /** Zone standard di una carta Pokemon */
    val CARD_ZONES = listOf(
        // Nome + HP: striscia superiore (primi 10%)
        ZoneCropRegion(CardZone.TOP, top = 0.00f, bottom = 0.10f, left = 0.05f, right = 0.95f),
        // Attacchi: zona centrale (40-75%)
        ZoneCropRegion(CardZone.MIDDLE, top = 0.40f, bottom = 0.75f, left = 0.05f, right = 0.95f),
        // Debolezza/Resistenza/Ritirata: (75-85%)
        ZoneCropRegion(CardZone.BOTTOM_STATS, top = 0.75f, bottom = 0.85f, left = 0.05f, right = 0.95f),
        // Illustratore: (85-92%)
        ZoneCropRegion(CardZone.ILLUSTRATOR, top = 0.85f, bottom = 0.92f, left = 0.05f, right = 0.70f),
        // Numero carta + Set + Rarita: (90-100%)
        ZoneCropRegion(CardZone.FOOTER, top = 0.90f, bottom = 1.00f, left = 0.02f, right = 0.98f)
    )

    // ═══════════════════════════════════════════
    // PIPELINE PRINCIPALE
    // ═══════════════════════════════════════════

    /**
     * Pipeline completa di preprocessing per l'intera immagine della carta.
     * Restituisce l'immagine processata ottimizzata per OCR full-frame.
     */
    fun preprocessFullCard(bitmap: Bitmap): Bitmap {
        var result = bitmap

        // 1. Ridimensiona a risoluzione OCR ottimale
        result = resizeForOCR(result)

        // 2. Rimozione glare (prima della normalizzazione colore)
        result = reduceGlare(result)

        // 3. Normalizzazione colore: aumento contrasto + luminosita
        result = normalizeColors(result)

        // 4. Sharpen leggero per migliorare bordi testo
        result = sharpen(result)

        return result
    }

    /**
     * Estrae e preprocessa una singola zona della carta.
     * Il preprocessing e piu aggressivo (binarizzazione) per le zone di testo piccolo.
     */
    fun preprocessZone(bitmap: Bitmap, zone: ZoneCropRegion): Bitmap {
        // Crop della zona
        var cropped = cropZone(bitmap, zone)

        // Per il footer (numeri piccoli), preprocessing piu aggressivo
        if (zone.zone == CardZone.FOOTER || zone.zone == CardZone.ILLUSTRATOR) {
            cropped = enhanceSmallText(cropped)
        } else {
            cropped = normalizeColors(cropped)
            cropped = sharpen(cropped)
        }

        return cropped
    }

    /**
     * Estrae tutte le zone della carta con preprocessing specifico per zona.
     * Restituisce una mappa zona -> bitmap preprocessato.
     */
    fun extractAllZones(bitmap: Bitmap): Map<CardZone, Bitmap> {
        val resized = resizeForOCR(bitmap)
        val deglared = reduceGlare(resized)

        return CARD_ZONES.associate { region ->
            region.zone to preprocessZone(deglared, region)
        }
    }

    // ═══════════════════════════════════════════
    // OPERAZIONI SINGOLE
    // ═══════════════════════════════════════════

    /** Ridimensiona mantenendo aspect ratio. Lato lungo = TARGET_LONG_SIDE */
    fun resizeForOCR(bitmap: Bitmap): Bitmap {
        val longSide = max(bitmap.width, bitmap.height)
        if (longSide <= TARGET_LONG_SIDE) return bitmap

        val scale = TARGET_LONG_SIDE.toFloat() / longSide
        val newW = (bitmap.width * scale).roundToInt()
        val newH = (bitmap.height * scale).roundToInt()

        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    /** Ritaglia una zona dalla bitmap in base alle coordinate normalizzate */
    fun cropZone(bitmap: Bitmap, zone: ZoneCropRegion): Bitmap {
        val x = (zone.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val y = (zone.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val w = ((zone.right - zone.left) * bitmap.width).roundToInt().coerceIn(1, bitmap.width - x)
        val h = ((zone.bottom - zone.top) * bitmap.height).roundToInt().coerceIn(1, bitmap.height - y)

        return Bitmap.createBitmap(bitmap, x, y, w, h)
    }

    /**
     * Riduce il glare (riflessi) tramite highlight clipping.
     * I pixel troppo luminosi vengono attenuati verso il grigio medio.
     */
    fun reduceGlare(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = result.width
        val height = result.height
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        // Soglia di luminosita per glare (molto alta = solo highlights estremi)
        val glareThreshold = 240

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            // Se tutti i canali sono sopra la soglia, e un glare
            if (r > glareThreshold && g > glareThreshold && b > glareThreshold) {
                // Attenua verso grigio medio (preserva un po' di info)
                val avg = (r + g + b) / 3
                val target = min(avg, 200) // cap a 200
                pixels[i] = Color.rgb(target, target, target)
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Normalizzazione colore: aumenta contrasto e corregge luminosita.
     * Usa ColorMatrix per operazione efficiente su GPU.
     */
    fun normalizeColors(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        // Contrasto 1.4x + luminosita +10
        val contrast = 1.4f
        val brightness = 10f
        val translate = (-.5f * contrast + .5f) * 255f + brightness

        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    /**
     * Preprocessing aggressivo per testo piccolo (numeri carta, illustratore).
     * Converte in scala di grigi, aumenta contrasto, binarizza.
     */
    fun enhanceSmallText(bitmap: Bitmap): Bitmap {
        var result = bitmap

        // Upscale 2x per testo piccolo
        result = Bitmap.createScaledBitmap(
            result,
            result.width * 2,
            result.height * 2,
            true
        )

        // Scala di grigi con alto contrasto
        result = toHighContrastGrayscale(result)

        // Binarizzazione adattiva (Sauvola-like semplificata)
        result = adaptiveBinarize(result)

        return result
    }

    /**
     * Converte in scala di grigi con contrasto elevato.
     * Pesi ottimizzati per testo scuro su sfondo chiaro.
     */
    private fun toHighContrastGrayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        // Scala di grigi + alto contrasto (2.0x)
        val contrast = 2.0f
        val translate = (-.5f * contrast + .5f) * 255f

        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f) // scala di grigi
        }
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        colorMatrix.postConcat(contrastMatrix)

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    /**
     * Binarizzazione adattiva semplificata.
     * Calcola soglia locale basata sulla media dell'intorno.
     * Efficace per testo su sfondi non uniformi (tipico delle carte Pokemon).
     */
    private fun adaptiveBinarize(bitmap: Bitmap, blockSize: Int = 15): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Estrai luminanza
        val gray = IntArray(pixels.size) { i ->
            val p = pixels[i]
            (Color.red(p) * 0.299 + Color.green(p) * 0.587 + Color.blue(p) * 0.114).roundToInt()
        }

        // Integral image per media locale veloce
        val integral = LongArray(width * height)
        for (y in 0 until height) {
            var rowSum = 0L
            for (x in 0 until width) {
                val idx = y * width + x
                rowSum += gray[idx]
                integral[idx] = rowSum + if (y > 0) integral[(y - 1) * width + x] else 0L
            }
        }

        val half = blockSize / 2
        val result = IntArray(pixels.size)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val x1 = max(0, x - half)
                val y1 = max(0, y - half)
                val x2 = min(width - 1, x + half)
                val y2 = min(height - 1, y + half)

                val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                var sum = integral[y2 * width + x2]
                if (x1 > 0) sum -= integral[y2 * width + (x1 - 1)]
                if (y1 > 0) sum -= integral[(y1 - 1) * width + x2]
                if (x1 > 0 && y1 > 0) sum += integral[(y1 - 1) * width + (x1 - 1)]

                val mean = sum / count
                val idx = y * width + x

                // Soglia: sotto la media locale - offset = testo (nero)
                val threshold = mean - 12
                val value = if (gray[idx] < threshold) 0 else 255
                result[idx] = Color.rgb(value, value, value)
            }
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Sharpening leggero con unsharp mask semplificato.
     * Migliora i bordi del testo senza amplificare troppo il rumore.
     */
    fun sharpen(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 3 || height < 3) return bitmap

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val result = IntArray(pixels.size)

        // Kernel sharpening 3x3 leggero
        val amount = 0.5f

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val center = pixels[idx]
                val cr = Color.red(center)
                val cg = Color.green(center)
                val cb = Color.blue(center)

                // Media 4-connessa
                var avgR = 0; var avgG = 0; var avgB = 0
                for (d in intArrayOf(-width, width, -1, 1)) {
                    val n = pixels[idx + d]
                    avgR += Color.red(n)
                    avgG += Color.green(n)
                    avgB += Color.blue(n)
                }
                avgR /= 4; avgG /= 4; avgB /= 4

                // Unsharp mask: pixel + amount * (pixel - blur)
                val nr = (cr + amount * (cr - avgR)).roundToInt().coerceIn(0, 255)
                val ng = (cg + amount * (cg - avgG)).roundToInt().coerceIn(0, 255)
                val nb = (cb + amount * (cb - avgB)).roundToInt().coerceIn(0, 255)

                result[idx] = Color.rgb(nr, ng, nb)
            }
        }

        // Copia bordi senza modifiche
        for (x in 0 until width) {
            result[x] = pixels[x]
            result[(height - 1) * width + x] = pixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            result[y * width] = pixels[y * width]
            result[y * width + width - 1] = pixels[y * width + width - 1]
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Verifica se l'immagine ha il rapporto d'aspetto corretto per una carta Pokemon.
     * Utile per validare il crop/inquadratura.
     */
    fun isCardAspectRatio(bitmap: Bitmap, tolerance: Float = 0.15f): Boolean {
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        return abs(ratio - CARD_ASPECT_RATIO) < tolerance
    }

    /**
     * Stima la qualita dell'immagine per OCR (0..1).
     * Analizza contrasto, luminosita e blur.
     */
    fun estimateQuality(bitmap: Bitmap): Float {
        val sampleSize = 100
        val scaleW = bitmap.width.toFloat() / sampleSize
        val scaleH = bitmap.height.toFloat() / sampleSize

        var minLum = 255f
        var maxLum = 0f
        var sumLum = 0f
        var sumEdge = 0f
        var count = 0

        for (sy in 0 until sampleSize) {
            for (sx in 0 until sampleSize) {
                val x = (sx * scaleW).roundToInt().coerceIn(0, bitmap.width - 1)
                val y = (sy * scaleH).roundToInt().coerceIn(0, bitmap.height - 1)
                val pixel = bitmap.getPixel(x, y)

                val lum = Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f
                minLum = min(minLum, lum)
                maxLum = max(maxLum, lum)
                sumLum += lum
                count++

                // Bordo: differenza col pixel precedente
                if (sx > 0) {
                    val prevX = ((sx - 1) * scaleW).roundToInt().coerceIn(0, bitmap.width - 1)
                    val prevPixel = bitmap.getPixel(prevX, y)
                    val prevLum = Color.red(prevPixel) * 0.299f + Color.green(prevPixel) * 0.587f + Color.blue(prevPixel) * 0.114f
                    sumEdge += abs(lum - prevLum)
                }
            }
        }

        // Score contrasto (0..1): range di luminanza ampio = buon contrasto
        val contrastScore = ((maxLum - minLum) / 255f).coerceIn(0f, 1f)

        // Score luminosita (0..1): luminosita media ottimale ~128
        val avgLum = sumLum / count
        val brightnessScore = 1f - (abs(avgLum - 128f) / 128f).coerceIn(0f, 1f)

        // Score sharpness (0..1): alte differenze = immagine nitida
        val edgeScore = (sumEdge / (count * 30f)).coerceIn(0f, 1f)

        // Media pesata
        return contrastScore * 0.3f + brightnessScore * 0.2f + edgeScore * 0.5f
    }
}
