package com.glocalsaino.miwallet.geofence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.model.PassBitmapDefinitions
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.ui.PassViewActivity
import com.glocalsaino.miwallet.ui.PassViewActivityBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GeofenceBroadcastReceiver : BroadcastReceiver(), KoinComponent {

    companion object {
        const val CHANNEL_ID = "geofence_channel"
    }

    private val passStore: PassStore by inject()

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        createNotificationChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        event.triggeringGeofences?.forEach { geofence ->
            val passId = GeofenceManager.passIdFromGeofenceId(geofence.requestId) ?: return@forEach
            val index  = GeofenceManager.locationIndexFromGeofenceId(geofence.requestId) ?: return@forEach
            val pass   = passStore.getPassbookForId(passId) ?: return@forEach
            val loc    = pass.locations.getOrNull(index) ?: return@forEach
            val text   = loc.getNameWithFallback(pass) ?: return@forEach

            val openIntent = PendingIntent.getActivity(
                context, passId.hashCode(),
                Intent(context, PassViewActivity::class.java)
                    .putExtra(PassViewActivityBase.EXTRA_KEY_UUID, passId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val iconBitmap = pass.getBitmap(passStore, PassBitmapDefinitions.BITMAP_ICON)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_maps_place)
                .setContentTitle(pass.description ?: context.getString(R.string.app_name))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
            if (iconBitmap != null) builder.setLargeIcon(iconBitmap)

            nm.notify(passId.hashCode(), builder.build())
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.geofence_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}
