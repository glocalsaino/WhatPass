package com.glocalsaino.miwallet.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.glocalsaino.miwallet.passkit.PassUpdateManager

class MiWalletFirebaseService : FirebaseMessagingService() {

    private val TAG = "MiWalletFirebaseService"

    override fun onNewToken(token: String) {
        PassUpdateManager.saveFcmToken(applicationContext, token)
        PassUpdateManager.reRegisterAllPasses(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // TEMPORARY (PassSource integration debugging): log every FCM message this
        // device receives, with its full data payload, before any early return -
        // proof of what actually arrived, independent of whether it's a pass update.
        Log.i(TAG, "onMessageReceived from=${message.from} data=${message.data} sentTime=${message.sentTime}")

        val passTypeId = message.data["passTypeIdentifier"] ?: return
        val serial = message.data["serialNumber"] ?: return

        // Hand off to WorkManager rather than a plain coroutine here: once this method
        // returns, Android is free to kill the process, and a bare coroutine racing
        // the download can get cut off mid-flight. WorkManager holds its own wake lock
        // and guarantees the download actually runs to completion.
        PassPushUpdateWorker.enqueue(applicationContext, passTypeId, serial)
    }
}
