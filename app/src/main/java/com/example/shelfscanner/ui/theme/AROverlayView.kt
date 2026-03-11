package com.example.shelfscanner.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.example.shelfscanner.tracking.ProductTracker

class AROverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var trackedDetections = listOf<ProductTracker.TrackedDetection>()
    private var imageWidth = 1
    private var imageHeight = 1

    // Blue — first time detected
    private val newBoxPaint = Paint().apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val newCirclePaint = Paint().apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Green — already scanned
    private val scannedBoxPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val scannedCirclePaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // White border for circle
    private val circleBorderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    // White tick
    private val tickPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    fun clearDetections() {
        trackedDetections = emptyList()
        invalidate()
    }
    fun updateDetections(
        detections: List<ProductTracker.TrackedDetection>,
        imgWidth: Int,
        imgHeight: Int
    ) {
        trackedDetections = detections
        imageWidth = imgWidth
        imageHeight = imgHeight
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
        val screenAspect = width.toFloat() / height.toFloat()

        val scaleX: Float
        val scaleY: Float
        val offsetX: Float
        val offsetY: Float

        if (imageAspect > screenAspect) {
            scaleY = height.toFloat() / imageHeight.toFloat()
            scaleX = scaleY
            offsetX = (width - imageWidth * scaleX) / 2f
            offsetY = 0f
        } else {
            scaleX = width.toFloat() / imageWidth.toFloat()
            scaleY = scaleX
            offsetX = 0f
            offsetY = (height - imageHeight * scaleY) / 2f
        }

        for (tracked in trackedDetections) {
            val box = tracked.smoothedBox

            // Scale box to screen
            val scaledBox = RectF(
                box.left * scaleX + offsetX,
                box.top * scaleY + offsetY,
                box.right * scaleX + offsetX,
                box.bottom * scaleY + offsetY
            )

            // Pick color
            val boxPaint = if (tracked.isNew) newBoxPaint else scannedBoxPaint
            val circlePaint = if (tracked.isNew) newCirclePaint else scannedCirclePaint

            // Draw bounding box
            canvas.drawRect(scaledBox, boxPaint)

            // Draw tick at center of box
            val cx = scaledBox.centerX()
            val cy = scaledBox.centerY()
            val radius = 22f

            canvas.drawCircle(cx, cy, radius, circlePaint)
            canvas.drawCircle(cx, cy, radius, circleBorderPaint)
            drawTick(canvas, cx, cy, radius)
        }
    }

    private fun drawTick(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val path = Path().apply {
            moveTo(cx - radius * 0.45f, cy)
            lineTo(cx - radius * 0.1f, cy + radius * 0.4f)
            lineTo(cx + radius * 0.45f, cy - radius * 0.35f)
        }
        canvas.drawPath(path, tickPaint)
    }
}