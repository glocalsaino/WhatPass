package com.glocalsaino.miwallet.beacon

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.glocalsaino.miwallet.model.PassStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BeaconScanResultReceiver : BroadcastReceiver(), KoinComponent {

    private val passStore: PassStore by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val results = intent.getParcelableArrayListExtra<ScanResult>(
                BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT
            ) ?: return
            results.forEach { BeaconScannerManager.processResult(context, passStore, it) }
        }
    }
}
