package com.glocalsaino.miwallet.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.glocalsaino.miwallet.R
import java.util.UUID

class BeaconActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "beacon_prefs"
        private const val KEY_UUID = "uuid"
        private const val KEY_MAJOR = "major"
        private const val KEY_MINOR = "minor"
        private const val DEFAULT_UUID = "00000000-0000-0000-0000-000000000000"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var etUuid: EditText
    private lateinit var etMajor: EditText
    private lateinit var etMinor: EditText
    private lateinit var btnToggle: Button
    private lateinit var tvStatus: TextView

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) startBeaconService()
        else Toast.makeText(this, getString(R.string.beacon_permission_denied), Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_beacon)

        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        etUuid = findViewById(R.id.et_uuid)
        etMajor = findViewById(R.id.et_major)
        etMinor = findViewById(R.id.et_minor)
        btnToggle = findViewById(R.id.btn_toggle)
        tvStatus = findViewById(R.id.tv_status)

        etUuid.setText(prefs.getString(KEY_UUID, DEFAULT_UUID))
        etMajor.setText(prefs.getInt(KEY_MAJOR, 1).toString())
        etMinor.setText(prefs.getInt(KEY_MINOR, 1).toString())

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
        btnToggle.setOnClickListener {
            if (BeaconService.isRunning) stopBeaconService() else checkAndStart()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun checkAndStart() {
        val uuidStr = etUuid.text.toString().trim()
        try { UUID.fromString(uuidStr) } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.beacon_invalid_uuid), Toast.LENGTH_SHORT).show()
            return
        }
        val major = etMajor.text.toString().toIntOrNull()
        val minor = etMinor.text.toString().toIntOrNull()
        if (major == null || major !in 0..65535 || minor == null || minor !in 0..65535) {
            Toast.makeText(this, getString(R.string.beacon_invalid_major_minor), Toast.LENGTH_SHORT).show()
            return
        }

        @SuppressLint("MissingPermission")
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, getString(R.string.beacon_bt_disabled), Toast.LENGTH_LONG).show()
            return
        }
        if (!adapter.isMultipleAdvertisementSupported) {
            Toast.makeText(this, getString(R.string.beacon_unsupported), Toast.LENGTH_LONG).show()
            return
        }

        prefs.edit()
            .putString(KEY_UUID, uuidStr)
            .putInt(KEY_MAJOR, major)
            .putInt(KEY_MINOR, minor)
            .apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (needed.isNotEmpty()) {
                requestPermissions.launch(needed.toTypedArray())
                return
            }
        }
        startBeaconService()
    }

    private fun startBeaconService() {
        val intent = Intent(this, BeaconService::class.java).apply {
            action = BeaconService.ACTION_START
            putExtra(BeaconService.EXTRA_UUID, etUuid.text.toString().trim())
            putExtra(BeaconService.EXTRA_MAJOR, etMajor.text.toString().toInt())
            putExtra(BeaconService.EXTRA_MINOR, etMinor.text.toString().toInt())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        // Disable button immediately to prevent double-tap while service is starting
        btnToggle.isEnabled = false
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) updateUi() }, 400)
    }

    private fun stopBeaconService() {
        BeaconService.isRunning = false
        startService(Intent(this, BeaconService::class.java).apply { action = BeaconService.ACTION_STOP })
        updateUi()
    }

    private fun updateUi() {
        val running = BeaconService.isRunning
        tvStatus.text = getString(if (running) R.string.beacon_status_active else R.string.beacon_status_inactive)
        tvStatus.setTextColor(if (running) 0xFF00C853.toInt() else 0xFF9E9E9E.toInt())
        btnToggle.text = getString(if (running) R.string.beacon_stop else R.string.beacon_start)
        btnToggle.setBackgroundColor(if (running) 0xFF880E4F.toInt() else 0xFF1565C0.toInt())
        setFieldsEnabled(!running)
    }

    private fun setFieldsEnabled(enabled: Boolean) {
        etUuid.isEnabled = enabled
        etMajor.isEnabled = enabled
        etMinor.isEnabled = enabled
    }
}
