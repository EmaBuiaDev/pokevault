package com.emabuia.pokevault.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.roundToInt

/**
 * Preparazione della striscia con l'ID prima di darla all'OCR.
 *
 * Qui c'era una pipeline completa di preprocessing (deskew, glare removal,
 * binarizzazione adattiva, crop per zone): era tutta irraggiungibile, perche'
 * lo scanner ritaglia la carta da se' e ML Kit lavora bene sul frame cosi'
 * com'e'. L'unico punto dove il preprocessing serve davvero e' il numero da
 * collezione, che e' alto circa 1,6 mm: lo si ingrandisce e si separa
 * l'inchiostro dal fondo, niente di piu'.
 */
object ImagePreprocessor {

    /**
     * Altezza a cui portare la striscia dell'ID prima dell'OCR.
     * A risoluzione nativa il numero misura una ventina di pixel, al limite
     * sotto cui ML Kit smette di leggere.
     */
    private const val TARGET_ID_STRIP_HEIGHT = 480

    /** Oltre questo fattore l'ingrandimento aggiunge solo sfocatura. */
    private const val MAX_ID_STRIP_UPSCALE = 4f

    /**
     * Ingrandisce la striscia e ne alza il contrasto in scala di grigi.
     *
     * Non binarizza di proposito: sul numero da collezione, stampato sottile e
     * spesso su fondo colorato, una soglia locale mangia i tratti e l'OCR legge
     * meno che sul grigio ad alto contrasto.
     */
    fun enhanceIdStrip(bitmap: Bitmap): Bitmap {
        val scale = (TARGET_ID_STRIP_HEIGHT.toFloat() / bitmap.height.coerceAtLeast(1))
            .coerceIn(1f, MAX_ID_STRIP_UPSCALE)

        val scaled = if (scale > 1.05f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }

        val result = toHighContrastGrayscale(scaled)
        if (scaled !== bitmap && scaled !== result) scaled.recycle()
        return result
    }

    /**
     * Scala di grigi con contrasto raddoppiato, pesata per testo scuro su
     * fondo chiaro. Una sola passata su GPU via ColorMatrix.
     */
    private fun toHighContrastGrayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        val contrast = 2.0f
        val translate = (-.5f * contrast + .5f) * 255f

        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        colorMatrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }
}
