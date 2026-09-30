package com.saetasaldo.app.data.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

open class MlKitCaptchaSolver(
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
) {
    open suspend fun solve(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val preprocessed = CaptchaPreprocessor.binarize(bitmap)
        val image = InputImage.fromBitmap(preprocessed, 0)

        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = CaptchaPreprocessor.cleanOcrOutput(visionText.text)
                    continuation.resume(text)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }
    }
}
