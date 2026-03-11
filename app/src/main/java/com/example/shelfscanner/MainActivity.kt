package com.example.shelfscanner

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.shelfscanner.detection.YoloDetector
import com.example.shelfscanner.tracking.ProductTracker
import com.example.shelfscanner.ui.AROverlayView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var arOverlayView: AROverlayView
    private lateinit var tvCount: TextView
    private lateinit var btnReset: Button

    private lateinit var detector: YoloDetector
    private lateinit var tracker: ProductTracker
    private lateinit var cameraExecutor: ExecutorService

    private val isProcessing = AtomicBoolean(false)
    private var frameSkipCount = 0
    private val FRAME_SKIP = 2

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        arOverlayView = findViewById(R.id.arOverlay)
        tvCount = findViewById(R.id.tvCount)
        btnReset = findViewById(R.id.btnReset)

        detector = YoloDetector(this)
        tracker = ProductTracker()
        cameraExecutor = Executors.newSingleThreadExecutor()

        btnReset.setOnClickListener {
            tracker.reset()
            arOverlayView.clearDetections()
            tvCount.text = "Scanned: 0"
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                frameSkipCount++
                if (frameSkipCount % (FRAME_SKIP + 1) == 0 &&
                    isProcessing.compareAndSet(false, true)) {
                    processFrame(imageProxy)
                } else {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("Camera", "Binding failed: ${e.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.toBitmap()

            val matrix = Matrix()
            matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0,
                bitmap.width, bitmap.height,
                matrix, true
            )

            val detections = detector.detect(rotatedBitmap)
            val tracked = tracker.processDetections(detections)

            runOnUiThread {
                arOverlayView.updateDetections(
                    tracked,
                    rotatedBitmap.width,
                    rotatedBitmap.height
                )
                tvCount.text = "Scanned: ${tracker.getScannedCount()}"
            }
        } catch (e: Exception) {
            Log.e("Frame", "Error: ${e.message}")
        } finally {
            imageProxy.close()
            isProcessing.set(false)
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.close()
        cameraExecutor.shutdown()
    }
}