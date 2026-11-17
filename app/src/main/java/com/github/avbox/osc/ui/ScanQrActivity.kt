package com.github.avbox.osc.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

class ScanQrActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RESULT = "scan_result"
        private const val REQUEST_CAMERA = 1001
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val reader = MultiFormatReader()
    private lateinit var previewView: PreviewView
    private lateinit var hintText: android.widget.TextView

    private val hints: MutableMap<DecodeHintType, Any> = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
        ),
        DecodeHintType.TRY_HARDER to true,
    ).toMutableMap()

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startCamera() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = android.widget.FrameLayout(this)
        previewView = PreviewView(this)
        root.addView(
            previewView,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        hintText = android.widget.TextView(this).apply {
            text = getString(com.github.avbox.osc.R.string.scan_qr_hint)
            textSize = 15f
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setShadowLayer(6f, 0f, 0f, android.graphics.Color.BLACK)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL,
            ).also { it.setMargins(0, 0, 0, 96) }
        }
        root.addView(hintText)
        setContentView(root)
        title = ""
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val text = decode(proxy)
                    proxy.close()
                    if (!text.isNullOrEmpty()) {
                        runOnUiThread { deliver(text) }
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun deliver(text: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, text))
        finish()
    }

    private fun decode(proxy: ImageProxy): String? {
        val plane = proxy.planes[0]
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val width = proxy.width
        val height = proxy.height
        val rotation = proxy.imageInfo.rotationDegrees
        val source = if (rotation == 90 || rotation == 270) {
            PlanarYUVLuminanceSource(
                rotate90(bytes, width, height), height, width, 0, 0, height, width, false,
            )
        } else {
            PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false)
        }
        return try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (e: Exception) {
            null
        }
    }

    private fun rotate90(data: ByteArray, width: Int, height: Int): ByteArray {
        val out = ByteArray(data.size)
        var index = 0
        for (x in 0 until width) {
            for (y in height - 1 downTo 0) {
                out[index++] = data[y * width + x]
            }
        }
        return out
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
