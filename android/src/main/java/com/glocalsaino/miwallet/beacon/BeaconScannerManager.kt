package com.glocalsaino.miwallet.beacon

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.model.PassBitmapDefinitions
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.model.pass.Pass
import com.glocalsaino.miwallet.model.pass.PassBeacon
import com.glocalsaino.miwallet.ui.PassViewActivity
import com.glocalsaino.miwallet.ui.PassViewActivityBase
import java.nio.ByteBuffer
import java.util.UUID

object BeaconScannerManager {

    private const val TAG = "BeaconScannerManager"
    private const val APPLE_COMPANY_ID = 0x004C
    private const val COOLDOWN_MS = 5 * 60 * 1000L
    private const val PREFS_COOLDOWN = "beacon_scanner_cooldown"
    private const val SCAN_REQUEST_CODE = 5001
    const val CHANNEL_ID = "beacon_proximity_channel"

    // Held alive for callback-based scanning on Android < 8
    private var callbackPassStore: PassStore? = null
    private var callbackContext: Context? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val ctx = callbackContext ?: return
            val ps = callbackPassStore ?: return
            processResult(ctx, ps, result)
        }
        override fun onBatchScanResults(results: List<ScanResult>) {
            val ctx = callbackContext ?: return
            val ps = callbackPassStore ?: return
            results.forEach { processResult(ctx, ps, it) }
        }
        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed: $errorCode")
        }
    }

    fun hasScanPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun hasPassesWithBeacons(passStore: PassStore) =
        passStore.passMap.values.any { it.beacons.isNotEmpty() }

    @SuppressLint("MissingPermission")
    fun startIfNeeded(context: Context, passStore: PassStore) {
        if (!hasScanPermission(context)) {
            Log.d(TAG, "startIfNeeded: no scan permission")
            return
        }
        if (!hasPassesWithBeacons(passStore)) {
            Log.d(TAG, "startIfNeeded: no passes with beacons")
            return
        }
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) return
        val scanner = adapter.bluetoothLeScanner ?: return

        ensureNotificationChannel(context)

        val filter = ScanFilter.Builder()
            .setManufacturerData(APPLE_COMPANY_ID, byteArrayOf())
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pi = PendingIntent.getBroadcast(
                context.applicationContext, SCAN_REQUEST_CODE,
                Intent(context.applicationContext, BeaconScanResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            val result = scanner.startScan(listOf(filter), settings, pi)
            Log.d(TAG, "startScan (PendingIntent): $result")
        } else {
            callbackContext = context.applicationContext
            callbackPassStore = passStore
            scanner.startScan(listOf(filter), settings, scanCallback)
            Log.d(TAG, "startScan (callback) started")
        }
    }

    fun processResult(context: Context, passStore: PassStore, result: ScanResult) {
        val mfr = result.scanRecord?.getManufacturerSpecificData(APPLE_COMPANY_ID) ?: return
        if (mfr.size < 23 || mfr[0] != 0x02.toByte() || mfr[1] != 0x15.toByte()) return

        val bb = ByteBuffer.wrap(mfr, 2, 16)
        val uuid = UUID(bb.long, bb.long).toString()
        val major = ((mfr[18].toInt() and 0xFF) shl 8) or (mfr[19].toInt() and 0xFF)
        val minor = ((mfr[20].toInt() and 0xFF) shl 8) or (mfr[21].toInt() and 0xFF)
        Log.d(TAG, "iBeacon detected: uuid=$uuid major=$major minor=$minor")

        val prefs = context.getSharedPreferences(PREFS_COOLDOWN, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        passStore.passMap.values.forEach { pass ->
            pass.beacons.forEach beacon@{ beacon ->
                if (!beacon.proximityUUID.equals(uuid, ignoreCase = true)) return@beacon
                if (beacon.major != null && beacon.major != major) return@beacon
                if (beacon.minor != null && beacon.minor != minor) return@beacon
                val lastTime = prefs.getLong(pass.id, 0L)
                if (now - lastTime < COOLDOWN_MS) return@beacon
                prefs.edit().putLong(pass.id, now).apply()
                showNotification(context, passStore, pass, beacon)
            }
        }
    }

    private fun showNotification(context: Context, passStore: PassStore, pass: Pass, beacon: PassBeacon) {
        val text = beacon.getTextWithFallback(pass) ?: return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = PendingIntent.getActivity(
            context, pass.id.hashCode(),
            Intent(context, PassViewActivity::class.java)
                .putExtra(PassViewActivityBase.EXTRA_KEY_UUID, pass.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val iconBitmap = pass.getBitmap(passStore, PassBitmapDefinitions.BITMAP_ICON)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bluetooth_beacon)
            .setContentTitle(pass.description ?: context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        if (iconBitmap != null) builder.setLargeIcon(iconBitmap)

        nm.notify(pass.id.hashCode() + 1, builder.build())
        Log.d(TAG, "Notification shown for pass: ${pass.description}, text: $text")
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.beacon_proximity_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}
