package com.saetasaldo.app.data.ocr

import android.graphics.Bitmap
import android.graphics.Color

object CaptchaPreprocessor {

    fun cleanOcrOutput(raw: String): String {
        return raw.filter { it.isLetterOrDigit() }.uppercase()
    }

    fun isLikelyValid(code: String): Boolean {
        val len = code.length
        return len in 4..6
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
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = if (luminance < threshold) Color.BLACK else Color.WHITE
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}
