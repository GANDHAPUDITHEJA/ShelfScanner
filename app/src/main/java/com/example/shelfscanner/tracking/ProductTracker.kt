package com.example.shelfscanner.tracking

import android.graphics.RectF
import com.example.shelfscanner.detection.YoloDetector
import kotlin.math.sqrt

class ProductTracker {

    private val scannedProducts = mutableListOf<ScannedProduct>()
    private val activeDetections = mutableMapOf<String, StableDetection>()
    private val STABILITY_FRAMES = 10
    private val MAX_DISTANCE = 120f
    private val IOU_THRESHOLD = 0.45f

    data class ScannedProduct(
        val centerX: Float,
        val centerY: Float,
        val boundingBox: RectF,
        val label: String
    )

    data class StableDetection(
        var detection: YoloDetector.Detection,
        var framesRemaining: Int,
        var isNew: Boolean,
        var smoothedBox: RectF
    )

    data class TrackedDetection(
        val detection: YoloDetector.Detection,
        val isNew: Boolean,
        val smoothedBox: RectF
    )

    fun processDetections(
        detections: List<YoloDetector.Detection>
    ): List<TrackedDetection> {

        val updatedKeys = mutableSetOf<String>()
        val sortedDetections = detections.sortedByDescending { it.confidence }

        for (detection in sortedDetections) {
            val matchingKey = findBestMatch(detection)

            if (matchingKey != null) {
                val existing = activeDetections[matchingKey]!!
                existing.smoothedBox = smoothBox(existing.smoothedBox, detection.boundingBox)
                existing.detection = detection
                existing.framesRemaining = STABILITY_FRAMES
                updatedKeys.add(matchingKey)
            } else {
                val key = "${detection.label}_${System.currentTimeMillis()}_${detection.boundingBox.centerX().toInt()}"
                val alreadyScanned = isAlreadyScanned(detection)

                if (!alreadyScanned) {
                    scannedProducts.add(
                        ScannedProduct(
                            centerX = detection.boundingBox.centerX(),
                            centerY = detection.boundingBox.centerY(),
                            boundingBox = RectF(detection.boundingBox),
                            label = detection.label
                        )
                    )
                }

                activeDetections[key] = StableDetection(
                    detection = detection,
                    framesRemaining = STABILITY_FRAMES,
                    isNew = !alreadyScanned,
                    smoothedBox = RectF(detection.boundingBox)
                )
                updatedKeys.add(key)
            }
        }

        // Reduce frames for undetected items
        val keysToRemove = mutableListOf<String>()
        for ((key, stable) in activeDetections) {
            if (key !in updatedKeys) {
                stable.framesRemaining--
                if (stable.framesRemaining <= 0) {
                    keysToRemove.add(key)
                }
            }
        }
        keysToRemove.forEach { activeDetections.remove(it) }

        return activeDetections.values.map {
            TrackedDetection(it.detection, it.isNew, it.smoothedBox)
        }
    }

    private fun findBestMatch(detection: YoloDetector.Detection): String? {
        var bestKey: String? = null
        var bestScore = Float.MAX_VALUE

        val newCx = detection.boundingBox.centerX()
        val newCy = detection.boundingBox.centerY()

        for ((key, stable) in activeDetections) {
            if (stable.detection.label != detection.label) continue

            val oldCx = stable.smoothedBox.centerX()
            val oldCy = stable.smoothedBox.centerY()

            val distance = sqrt(
                (newCx - oldCx) * (newCx - oldCx) +
                        (newCy - oldCy) * (newCy - oldCy)
            )

            val iouScore = iou(stable.smoothedBox, detection.boundingBox)
            val score = distance * (1f - iouScore)

            if (distance < MAX_DISTANCE && score < bestScore) {
                bestScore = score
                bestKey = key
            }
        }
        return bestKey
    }

    private fun isAlreadyScanned(detection: YoloDetector.Detection): Boolean {
        val cx = detection.boundingBox.centerX()
        val cy = detection.boundingBox.centerY()

        return scannedProducts.any { scanned ->
            if (scanned.label != detection.label) return@any false

            val distance = sqrt(
                (cx - scanned.centerX) * (cx - scanned.centerX) +
                        (cy - scanned.centerY) * (cy - scanned.centerY)
            )
            val iouScore = iou(scanned.boundingBox, detection.boundingBox)

            distance < MAX_DISTANCE || iouScore > IOU_THRESHOLD
        }
    }

    private fun smoothBox(old: RectF, new: RectF, factor: Float = 0.4f): RectF {
        return RectF(
            old.left   * (1 - factor) + new.left   * factor,
            old.top    * (1 - factor) + new.top    * factor,
            old.right  * (1 - factor) + new.right  * factor,
            old.bottom * (1 - factor) + new.bottom * factor
        )
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

    fun reset() {
        scannedProducts.clear()
        activeDetections.clear()
    }

    fun getScannedCount(): Int {
        return scannedProducts.size
    }
}