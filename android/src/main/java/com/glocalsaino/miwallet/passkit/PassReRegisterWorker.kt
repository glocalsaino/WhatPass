package com.glocalsaino.miwallet.passkit

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class PassReRegisterWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val newToken = inputData.getString(KEY_NEW_TOKEN) ?: return Result.failure()
        val prefs = applicationContext.getSharedPreferences("miwallet_passkit", Context.MODE_PRIVATE)

        prefs.all.keys
            .filter { it.startsWith("webservice_") }
            .forEach { key ->
                val parts = key.removePrefix("webservice_").split("_", limit = 2)
                if (parts.size == 2) {
                    val (passTypeId, serial) = parts
                    val webServiceUrl = prefs.getString(key, null) ?: return@forEach
                    val authToken = prefs.getString("authtoken_${passTypeId}_${serial}", null) ?: return@forEach
                    try {
                        PassUpdateManager.registerPass(applicationContext, webServiceUrl, authToken, passTypeId, serial)
                    } catch (e: Exception) {
                        Log.e("PassReRegisterWorker", "Failed re-registering $serial", e)
                    }
                }
            }
        return Result.success()
    }

    companion object {
        private const val KEY_NEW_TOKEN = "new_fcm_token"

        fun enqueue(context: Context, newToken: String) {
            val request = OneTimeWorkRequestBuilder<PassReRegisterWorker>()
                .setInputData(workDataOf(KEY_NEW_TOKEN to newToken))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
