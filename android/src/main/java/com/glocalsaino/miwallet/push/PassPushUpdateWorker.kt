package com.glocalsaino.miwallet.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.passkit.PassNotificationPrefs
import com.glocalsaino.miwallet.passkit.PassUpdateManager
import com.glocalsaino.miwallet.ui.PassViewActivity
import com.glocalsaino.miwallet.ui.PassViewActivityBase
import com.glocalsaino.miwallet.ui.UnzipPassController

// Runs the actual pass download+install triggered by an FCM push, via WorkManager
// instead of a plain coroutine launched from FirebaseMessagingService.onMessageReceived().
// That matters: once onMessageReceived() returns, Android considers the message
// "handled" and is free to kill the process at any point - a bare coroutine racing
// a network call can get cut off mid-flight (observed: the process was gone ~20ms
// after receipt, well before any HTTP round trip could finish). WorkManager holds its
// own wake lock and guarantees the work actually runs to completion.
class PassPushUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val passTypeId = inputData.getString(KEY_PASS_TYPE_ID) ?: return Result.failure()
        val serial = inputData.getString(KEY_SERIAL) ?: return Result.failure()

        return try {
            val result = PassUpdateManager.downloadUpdatedPass(applicationContext, passTypeId, serial)
            val muted = PassNotificationPrefs.isMuted(applicationContext, passTypeId, serial)
            if (result.updated && !muted) {
                showPassUpdateNotification(applicationContext, passTypeId, serial, result.changeMessage, result.organizationName)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed updating pass $serial", e)
            Result.retry()
        }
    }

    private fun showPassUpdateNotification(context: Context, passTypeId: String, serial: String, changeMessage: String? = null, organizationName: String? = null) {
        val channelId = "pass_updates"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // IMPORTANCE_HIGH is required for the heads-up banner - IMPORTANCE_DEFAULT
            // only posts silently to the status bar/tray.
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notification_channel_pass_updates),
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, PassViewActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(PassViewActivityBase.EXTRA_KEY_UUID, UnzipPassController.stableId(passTypeId, serial))
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = changeMessage ?: context.getString(R.string.notification_pass_updated_body)
        val titleText = organizationName ?: context.getString(R.string.notification_pass_updated_title)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_refresh)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            // Pre-O devices (e.g. Android 6) don't have channels - PRIORITY_HIGH plus
            // sound/vibration is what triggers the heads-up banner there. On O+ the
            // channel's own IMPORTANCE_HIGH above takes over, but setPriority is kept
            // for compatibility with OEM Notification code that still reads it.
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()

        notificationManager.notify(serial.hashCode(), notification)
    }

    companion object {
        private const val TAG = "PassPushUpdateWorker"
        private const val KEY_PASS_TYPE_ID = "pass_type_id"
        private const val KEY_SERIAL = "serial"

        fun enqueue(context: Context, passTypeId: String, serial: String) {
            val request = OneTimeWorkRequestBuilder<PassPushUpdateWorker>()
                .setInputData(workDataOf(KEY_PASS_TYPE_ID to passTypeId, KEY_SERIAL to serial))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
