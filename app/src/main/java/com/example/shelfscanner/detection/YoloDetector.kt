package com.example.shelfscanner.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class YoloDetector(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val inputSize = 320
    private val confidenceThreshold = 0.50f
    private val iouThreshold = 0.45f

    data class Detection(
        val boundingBox: RectF,
        val confidence: Float,
        val label: String
    )

    init {
        setupInterpreter()
    }

    private fun setupInterpreter() {
        try {
            val options = Interpreter.Options().apply {
                numThreads = 4
            }
            interpreter = Interpreter(loadModelFromAssets(), options)
            Log.d("YOLO", "Model loaded successfully")
        } catch (e: Exception) {
            Log.e("YOLO", "Failed to load model: ${e.message}")
        }
    }

    private fun loadModelFromAssets(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd("yolov8n_float32.tflite")
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val interpreter = interpreter ?: return emptyList()

        // Keep original dimensions for correct scaling
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = bitmapToByteBuffer(resized)
        val output = Array(1) { Array(84) { FloatArray(2100) } }
        interpreter.run(inputBuffer, output)
        return parseDetections(output[0], bitmap.width, bitmap.height)
    }

    private fun parseDetections(
        output: Array<FloatArray>,
        originalWidth: Int,
        originalHeight: Int
    ): List<Detection> {
        val detections = mutableListOf<Detection>()

        val allowedLabels = listOf(
            // Drinks & containers
            "bottle", "wine glass", "cup", "bowl",
            // Food
            "banana", "apple", "sandwich", "orange",
            "broccoli", "carrot", "hot dog", "pizza",
            "donut", "cake",
            // Electronics
            "cell phone", "laptop", "keyboard", "mouse",
            "remote", "tv",
            // Bags
            "backpack", "handbag", "suitcase",
            // Other
            "book", "clock", "scissors", "toothbrush",
            "vase", "teddy bear"
        )

        for (i in 0 until 2100) {
            val cx = output[0][i]
            val cy = output[1][i]
            val w  = output[2][i]
            val h  = output[3][i]

            var maxScore = 0f
            var classId = 0
            for (c in 4 until 84) {
                if (output[c][i] > maxScore) {
                    maxScore = output[c][i]
                    classId = c - 4
                }
            }

            val label = getLabel(classId)

            if (maxScore >= confidenceThreshold && label in allowedLabels)  {
                // Correct coordinate conversion
                val left   = (cx - w / 2f) * originalWidth
                val top    = (cy - h / 2f) * originalHeight
                val right  = (cx + w / 2f) * originalWidth
                val bottom = (cy + h / 2f) * originalHeight

                val clampedBox = RectF(
                    left.coerceIn(0f, originalWidth.toFloat()),
                    top.coerceIn(0f, originalHeight.toFloat()),
                    right.coerceIn(0f, originalWidth.toFloat()),
                    bottom.coerceIn(0f, originalHeight.toFloat())
                )

                if (clampedBox.width() > 10 && clampedBox.height() > 10) {
                    detections.add(Detection(clampedBox, maxScore, label))
                }
            }
        }
        return applyNMS(detections)
    }

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val result = mutableListOf<Detection>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)
            sorted.removeAll { iou(best.boundingBox, it.boundingBox) > iouThreshold }
        }
        return result
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interLeft   = maxOf(a.left, b.left)
        val interTop    = maxOf(a.top, b.top)
        val interRight  = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)
        val inter = maxOf(0f, interRight - interLeft) * maxOf(0f, interBottom - interTop)
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union > 0) inter / union else 0f
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            buffer.putFloat(((pixel shr 8)  and 0xFF) / 255.0f)
            buffer.putFloat((pixel          and 0xFF) / 255.0f)
        }
        return buffer
    }

    private fun getLabel(classId: Int): String {
        val labels = listOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train",
            "truck", "boat", "traffic light", "fire hydrant", "stop sign",
            "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep",
            "cow", "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella",
            "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard",
            "sports ball", "kite", "baseball bat", "baseball glove", "skateboard",
            "surfboard", "tennis racket", "bottle", "wine glass", "cup", "fork",
            "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
            "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
            "couch", "potted plant", "bed", "dining table", "toilet", "tv",
            "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave",
            "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase",
            "scissors", "teddy bear", "hair drier", "toothbrush"
        )
        return labels.getOrElse(classId) { "product" }
    }

    fun close() {
        interpreter?.close()
    }
}