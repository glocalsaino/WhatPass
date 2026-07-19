package com.glocalsaino.miwallet.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.glocalsaino.miwallet.R
import android.hardware.Camera
import android.os.Handler
import android.os.Looper

@Suppress("DEPRECATION")
class QrImportActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private var camera: Camera? = null
    private lateinit var surfaceView: SurfaceView
    private val reader = MultiFormatReader()
    private val handler = Handler(Looper.getMainLooper())
    private var scanning = true

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(this, "Permiso de cámara necesario", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_import)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.qr_scan_hint)

        surfaceView = findViewById(R.id.qr_surface)
        surfaceView.holder.addCallback(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        try {
            camera = Camera.open()
            camera?.setDisplayOrientation(computeDisplayOrientation())
            camera?.parameters = camera?.parameters?.apply {
                if (supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) == true) {
                    focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                } else if (supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) == true) {
                    focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                }
            }
            camera?.setPreviewDisplay(surfaceView.holder)
            camera?.startPreview()
        } catch (e: Exception) {
            Log.e("QrImport", "Camera error", e)
        }
    }

    private fun computeDisplayOrientation(): Int {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(0, info)
        val rotation = windowManager.defaultDisplay.rotation
        val degrees = when (rotation) {
            android.view.Surface.ROTATION_0 -> 0
            android.view.Surface.ROTATION_90 -> 90
            android.view.Surface.ROTATION_180 -> 180
            android.view.Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (360 - (info.orientation + degrees) % 360) % 360
        } else {
            (info.orientation - degrees + 360) % 360
        }
    }

    private fun scheduleNextFrame() {
        if (!scanning) return
        camera?.setOneShotPreviewCallback { data, cam ->
            val size = cam.parameters.previewSize
            Log.d("QrImport", "frame received ${size.width}x${size.height} bytes=${data.size}")
            decodeFrame(data, size.width, size.height)
            if (scanning) handler.postDelayed({ scheduleNextFrame() }, 300)
        }
    }

    private fun decodeFrame(data: ByteArray, width: Int, height: Int) {
        try {
            val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(bitmap)
            val text = result.text
            Log.d("QrImport", "decoded: $text")
            if (text.startsWith("http://") || text.startsWith("https://")) {
                scanning = false
                handler.post { openPassUrl(text) }
            } else {
                handler.post {
                    Toast.makeText(this, getString(R.string.qr_scan_error), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: NotFoundException) {
            Log.d("QrImport", "no QR found in frame")
        } catch (e: Exception) {
            Log.e("QrImport", "decode error", e)
        }
    }

    private fun openPassUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setClass(this@QrImportActivity, PassImportActivity::class.java)
        }
        startActivity(intent)
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        scanning = false
        camera?.stopPreview()
        camera?.release()
        camera = null
        super.onDestroy()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        camera?.stopPreview()
        try {
            camera?.setPreviewDisplay(holder)
            camera?.startPreview()
            scheduleNextFrame()
        } catch (e: Exception) {
            Log.e("QrImport", "surfaceChanged error", e)
        }
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) {}
}
