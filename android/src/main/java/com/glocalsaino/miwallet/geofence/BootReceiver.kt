package com.glocalsaino.miwallet.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.glocalsaino.miwallet.model.PassStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BootReceiver : BroadcastReceiver(), KoinComponent {

    private val passStore: PassStore by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            GeofenceManager.registerAll(context.applicationContext, passStore)
        }
    }
}
