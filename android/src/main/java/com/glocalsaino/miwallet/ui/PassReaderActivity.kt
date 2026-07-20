package com.glocalsaino.miwallet.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Camera
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.glocalsaino.miwallet.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class PassReaderActivity : AppCompatActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "PassReaderActivity"
        // Código de operador persistente durante la vida del proceso
        var sessionEditCode: String? = null
    }

    private var camera: Camera? = null
    private lateinit var surfaceView: SurfaceView
    private lateinit var resultOverlay: FrameLayout
    private lateinit var resultIcon: TextView
    private lateinit var resultText: TextView
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        ))
    }
    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) checkEditCodeAndStart() else {
            Toast.makeText(this, getString(R.string.pass_reader_no_camera), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_pass_reader)

        surfaceView = findViewById(R.id.scanner_surface)
        resultOverlay = findViewById(R.id.result_overlay)
        resultIcon = findViewById(R.id.result_icon)
        resultText = findViewById(R.id.result_text)

        surfaceView.holder.addCallback(this)

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_change_code).setOnClickListener { promptForEditCode(force = true) }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            checkEditCodeAndStart()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkEditCodeAndStart() {
        if (sessionEditCode == null) {
            promptForEditCode(force = false)
        } else {
            startCamera()
        }
    }

    private fun promptForEditCode(force: Boolean) {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.pass_reader_edit_code_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(48, 24, 48, 24)
            if (!force) setText(sessionEditCode ?: "")
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pass_reader_edit_code_title))
            .setMessage(getString(R.string.pass_reader_edit_code_message))
            .setView(input)
            .setCancelable(!force || sessionEditCode == null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val code = input.text.toString().trim()
                if (code.isEmpty() && !force) {
                    sessionEditCode = null
                } else if (code.isNotEmpty()) {
                    sessionEditCode = code
                }
                startCamera()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                startCamera()
            }
            .show()
    }

    private fun startCamera() {
        try {
            camera = Camera.open()
            camera?.setDisplayOrientation(computeDisplayOrientation())
            camera?.parameters = camera?.parameters?.apply {
                val best = chooseBestPreviewSize(this)
                setPreviewSize(best.width, best.height)
                if (supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) == true) {
                    focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                } else if (supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) == true) {
                    focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                }
            }
            camera?.setPreviewDisplay(surfaceView.holder)
            camera?.startPreview()
            scanning = true
            scheduleNextFrame()
        } catch (e: Exception) {
            Log.e(TAG, "Camera error", e)
        }
    }

    private fun chooseBestPreviewSize(params: Camera.Parameters): Camera.Size {
        val sizes = params.supportedPreviewSizes ?: return params.previewSize
        // Smallest size with at least 480×320 — enough for ZXing, faster to decode
        return sizes
            .filter { it.width >= 480 && it.height >= 320 }
            .minByOrNull { it.width * it.height }
            ?: sizes.maxByOrNull { it.width * it.height }
            ?: params.previewSize
    }

    private fun computeDisplayOrientation(): Int {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(0, info)
        @Suppress("DEPRECATION")
        val degrees = when (windowManager.defaultDisplay.rotation) {
            android.view.Surface.ROTATION_0   -> 0
            android.view.Surface.ROTATION_90  -> 90
            android.view.Surface.ROTATION_180 -> 180
            android.view.Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
            (360 - (info.orientation + degrees) % 360) % 360
        else
            (info.orientation - degrees + 360) % 360
    }

    private fun scheduleNextFrame() {
        if (!scanning) return
        camera?.setOneShotPreviewCallback { data, cam ->
            val size = cam.parameters.previewSize
            decodeFrame(data, size.width, size.height)
            if (scanning) handler.postDelayed({ scheduleNextFrame() }, 50)
        }
    }

    private fun decodeFrame(data: ByteArray, width: Int, height: Int) {
        try {
            val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decodeWithState(bitmap)
            val url = result.text
            Log.d(TAG, "QR detected: $url")
            if (isScanUrl(url)) {
                scanning = false
                handler.post { callScanUrl(url) }
            } else {
                Log.d(TAG, "URL does not match scan pattern, ignoring")
            }
        } catch (e: NotFoundException) {
            // sin QR en este frame, normal
        } catch (e: Exception) {
            Log.e(TAG, "Decode error", e)
        } finally {
            reader.reset()
        }
    }

    private fun isScanUrl(url: String): Boolean =
        url.startsWith("http")

    private fun callScanUrl(rawUrl: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Step 1: fetch the QR URL (may return a form asking for psEditCode)
                Log.d(TAG, "Step 1: $rawUrl")
                val r1 = httpClient.newCall(Request.Builder().url(rawUrl).build()).execute()
                val html1 = r1.body?.string() ?: ""
                Log.d(TAG, "Step 1 response (200 chars): ${html1.take(200)}")

                if (!html1.contains("psEditCode", ignoreCase = true)) {
                    // Check if this is a pass download URL, not a validation endpoint
                    val contentType = r1.headers["Content-Type"] ?: ""
                    if (contentType.contains("pkpass", ignoreCase = true) ||
                        html1.contains("<html", ignoreCase = true)
                    ) {
                        withContext(Dispatchers.Main) {
                            showResult(getString(R.string.pass_reader_is_download_url), false)
                        }
                        return@launch
                    }
                    // No form — the server returned a direct result
                    val msg = stripHtml(html1).ifEmpty {
                        if (r1.isSuccessful) getString(R.string.pass_reader_ok)
                        else getString(R.string.pass_reader_error)
                    }
                    withContext(Dispatchers.Main) { showResult(msg, r1.isSuccessful) }
                    return@launch
                }

                // Step 2: build the action URL using hidden form fields + session code
                val baseUri = android.net.Uri.parse(rawUrl)
                val actionBuilder = android.net.Uri.Builder()
                    .scheme(baseUri.scheme)
                    .authority(baseUri.authority)
                    .path(baseUri.path)

                val hashedSerial = extractInputValue(html1, "hashedSerialNumber")
                    ?: baseUri.getQueryParameter("hashedSerialNumber")
                if (!hashedSerial.isNullOrEmpty())
                    actionBuilder.appendQueryParameter("hashedSerialNumber", hashedSerial)

                val templateId = extractInputValue(html1, "templateId")
                if (!templateId.isNullOrEmpty())
                    actionBuilder.appendQueryParameter("templateId", templateId)

                actionBuilder.appendQueryParameter("psEditCode", sessionEditCode.orEmpty())

                val actionUrl = actionBuilder.build().toString()
                Log.d(TAG, "Step 2: $actionUrl")

                // Step 3: submit and show result
                val r2 = httpClient.newCall(Request.Builder().url(actionUrl).build()).execute()
                val html2 = r2.body?.string() ?: ""
                // If the response still contains a psEditCode form, the code was wrong/missing
                val codeRejected = html2.contains("psEditCode", ignoreCase = true)
                val success = r2.isSuccessful && !codeRejected
                val msg = stripHtml(html2).ifEmpty {
                    if (success) getString(R.string.pass_reader_ok) else getString(R.string.pass_reader_error)
                }
                withContext(Dispatchers.Main) { showResult(msg, success) }

            } catch (e: Exception) {
                Log.e(TAG, "Scan request failed", e)
                withContext(Dispatchers.Main) {
                    showResult(getString(R.string.pass_reader_network_error), false)
                }
            }
        }
    }

    private fun extractInputValue(html: String, name: String): String? {
        val n = Regex.escape(name)
        Regex("""<input\b[^>]*\bname=["']$n["'][^>]*\bvalue=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.let { return it.groupValues[1] }
        return Regex("""<input\b[^>]*\bvalue=["']([^"']*)["'][^>]*\bname=["']$n["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
    }

    private fun stripHtml(html: String): String =
        html
            .replace(Regex("<title[^>]*>.*?</title>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("[>\\-<]{3,}"), "")  // decorative separators e.g. >---->--->
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun showResult(message: String, success: Boolean) {
        resultIcon.text = if (success) "✓" else "✗"
        resultText.text = message
        resultOverlay.setBackgroundColor(
            if (success) Color.argb(220, 0, 150, 60) else Color.argb(220, 180, 20, 20)
        )
        resultOverlay.visibility = View.VISIBLE
        resultOverlay.setOnClickListener { dismissResult() }

        handler.postDelayed({ dismissResult() }, 5000)
    }

    private fun dismissResult() {
        handler.removeCallbacksAndMessages(null)
        resultOverlay.visibility = View.GONE
        resultOverlay.setOnClickListener(null)
        scanning = true
        scheduleNextFrame()
    }

    override fun onDestroy() {
        scanning = false
        handler.removeCallbacksAndMessages(null)
        camera?.stopPreview()
        camera?.release()
        camera = null
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        scanning = false
        handler.removeCallbacksAndMessages(null)
        camera?.stopPreview()
        camera?.release()
        camera = null
    }

    override fun onResume() {
        super.onResume()
        if (sessionEditCode != null && resultOverlay.visibility == View.GONE) {
            startCamera()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        camera?.stopPreview()
        try {
            camera?.setPreviewDisplay(holder)
            camera?.startPreview()
            scanning = true
            scheduleNextFrame()
        } catch (e: Exception) {
            Log.e(TAG, "surfaceChanged error", e)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        scanning = false
    }
}
