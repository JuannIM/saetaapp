package com.saetasaldo.app.data.ocr

import android.graphics.Bitmap
import android.graphics.Color

object CaptchaPreprocessor {

    fun cleanOcrOutput(raw: String): String {
        return raw.filter { it.isLetterOrDigit() }
    }

    fun isLikelyValid(code: String): Boolean {
        val len = code.length
        return len in 4..6
    }

    fun scale2x(source: Bitmap): Bitmap {
        return Bitmap.createScaledBitmap(source, source.width * 2, source.height * 2, true)
    }

    /**
     * Binarizes bitmap to high-contrast black/white to enhance ML Kit OCR character edge detection.
     */
    fun binarize(source: Bitmap, threshold: Int = 140): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val color = pixels[i]
            val a = Color.alpha(color)
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            // If pixel is mostly transparent or brighter than threshold, set white background, else black text
            pixels[i] = if (a < 128 || luminance >= threshold) Color.WHITE else Color.BLACK
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}
