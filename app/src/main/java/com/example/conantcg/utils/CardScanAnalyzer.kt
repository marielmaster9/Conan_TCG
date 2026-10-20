package com.example.conantcg.utils

import android.graphics.*
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class CardScanAnalyzer(
    private val onTextRecognized: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        try {
            val bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val rotatedBitmap = rotateBitmap(bitmap, rotationDegrees.toFloat())

            // 1. Crop เฉพาะขอบล่างของการ์ด (Bottom 35%)
            val bottomRoiBitmap = cropBottomROI(rotatedBitmap)

            // 2. ปรับแต่งภาพสำหรับ "การ์ดฟอยล์/ปั๊มทอง"
            val processedBitmap = processFoilImage(bottomRoiBitmap)

            val inputImage = InputImage.fromBitmap(processedBitmap, 0)

            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text
                    if (text.isNotBlank()) {
                        onTextRecognized(text)
                    }
                }
                .addOnCompleteListener {
                    if (bottomRoiBitmap != rotatedBitmap && !bottomRoiBitmap.isRecycled) bottomRoiBitmap.recycle()
                    if (!processedBitmap.isRecycled) processedBitmap.recycle()
                    if (rotatedBitmap != bitmap && !rotatedBitmap.isRecycled) rotatedBitmap.recycle()
                    imageProxy.close()
                }
        } catch (e: Exception) {
            imageProxy.close()
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun cropBottomROI(src: Bitmap): Bitmap {
        val cropWidth = (src.width * 0.90f).toInt()
        val cropHeight = (src.height * 0.35f).toInt()
        val startX = ((src.width - cropWidth) / 2).coerceAtLeast(0)
        val startY = (src.height - cropHeight - (src.height * 0.02f)).toInt().coerceAtLeast(0)

        return Bitmap.createBitmap(src, startX, startY, cropWidth, cropHeight)
    }

    private fun processFoilImage(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()

        val cm = ColorMatrix().apply {
            setSaturation(0f) // แก้ไขจาก setSat เป็น setSaturation
        }
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                1.4f, 0.0f, 0.0f, 0.0f, -20.0f,
                0.0f, 1.4f, 0.0f, 0.0f, -20.0f,
                0.0f, 0.0f, 1.4f, 0.0f, -20.0f,
                0.0f, 0.0f, 0.0f, 1.0f,   0.0f
            )
        )
        cm.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }
}