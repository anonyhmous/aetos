package com.example.engine

import android.graphics.Bitmap
import com.example.model.PickupMetric
import com.example.model.TripMetric
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class OcrProcessor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Processes screen frame or bitmap asynchronously using Google ML Kit Text Recognition.
     * Extracts all text blocks and normalizes digits and metrics.
     */
    suspend fun processFrame(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val lines = visionText.textBlocks.flatMap { block ->
                        block.lines.map { line -> line.text }
                    }
                    val (pickup, trip) = TripMetricParser.parseRideDetails(lines)
                    val fareOffer = TripMetricParser.extractFareAmount(visionText.text)

                    continuation.resume(
                        OcrResult(
                            success = true,
                            pickup = pickup,
                            trip = trip,
                            fareOffer = fareOffer,
                            rawFullText = visionText.text,
                            lines = lines
                        )
                    )
                }
                .addOnFailureListener { exc ->
                    continuation.resume(
                        OcrResult(
                            success = false,
                            pickup = PickupMetric(1.2, 3, "OCR Error"),
                            trip = TripMetric(8.5, 22, "OCR Error"),
                            fareOffer = 0.0,
                            rawFullText = exc.localizedMessage ?: "OCR failure",
                            lines = emptyList()
                        )
                    )
                }
        } catch (e: Exception) {
            continuation.resume(
                OcrResult(
                    success = false,
                    pickup = PickupMetric(1.2, 3, "OCR Exception"),
                    trip = TripMetric(8.5, 22, "OCR Exception"),
                    fareOffer = 0.0,
                    rawFullText = e.localizedMessage ?: "OCR Exception",
                    lines = emptyList()
                )
            )
        }
    }

    fun release() {
        try {
            recognizer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

data class OcrResult(
    val success: Boolean,
    val pickup: PickupMetric,
    val trip: TripMetric,
    val fareOffer: Double,
    val rawFullText: String,
    val lines: List<String> = emptyList()
)
