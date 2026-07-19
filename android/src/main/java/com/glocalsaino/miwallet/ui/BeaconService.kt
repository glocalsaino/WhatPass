package com.glocalsaino.miwallet.ui

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.glocalsaino.miwallet.R
import java.nio.ByteBuffer
import java.util.UUID

class BeaconService : Service() {

    companion object {
        const val ACTION_START = "com.glocalsaino.miwallet.BEACON_START"
        const val ACTION_STOP  = "com.glocalsaino.miwallet.BEACON_STOP"
        const val EXTRA_UUID   = "uuid"
        const val EXTRA_MAJOR  = "major"
        const val EXTRA_MINOR  = "minor"
        private const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "beacon_channel"

        @Volatile var isRunning = false
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) { /* isRunning already set in onStartCommand */ }
        override fun onStartFailure(errorCode: Int) { isRunning = false; stopSelf() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        val uuidStr = intent?.getStringExtra(EXTRA_UUID) ?: run { stopSelf(); return START_NOT_STICKY }
        val major   = intent.getIntExtra(EXTRA_MAJOR, 1)
        val minor   = intent.getIntExtra(EXTRA_MINOR, 1)

        isRunning = true
        startForeground(NOTIFICATION_ID, buildNotification())
        startBeacon(uuidStr, major, minor)
        return START_STICKY
    }

    override fun onDestroy() {
        stopBeacon()
        isRunning = false
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun startBeacon(uuidStr: String, major: Int, minor: Int) {
        val advertiser = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter?.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(0x004C, buildIBeaconPayload(uuidStr, major, minor))
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopBeacon() {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
    }

    // iBeacon payload: type(1) + length(1) + uuid(16) + major(2) + minor(2) + txPower(1) = 23 bytes
    private fun buildIBeaconPayload(uuidStr: String, major: Int, minor: Int): ByteArray {
        val uuid = UUID.fromString(uuidStr)
        val buf = ByteBuffer.allocate(23)
        buf.put(0x02.toByte())
        buf.put(0x15.toByte())
        buf.putLong(uuid.mostSignificantBits)
        buf.putLong(uuid.leastSignificantBits)
        buf.put((major shr 8).toByte())
        buf.put((major and 0xFF).toByte())
        buf.put((minor shr 8).toByte())
        buf.put((minor and 0xFF).toByte())
        buf.put((-65).toByte())
        return buf.array()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.beacon_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { setShowBadge(false) }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_bluetooth_beacon)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.beacon_notification_text))
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(PendingIntent.getActivity(
            this, 0,
            Intent(this, BeaconActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        ))
        .addAction(0, getString(R.string.beacon_stop),
            PendingIntent.getService(
                this, 1,
                Intent(this, BeaconService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()
}
