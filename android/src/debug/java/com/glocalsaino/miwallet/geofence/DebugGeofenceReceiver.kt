package com.glocalsaino.miwallet.geofence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.model.PassBitmapDefinitions
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.ui.PassViewActivity
import com.glocalsaino.miwallet.ui.PassViewActivityBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

// Only compiled in debug builds. Trigger via:
//   adb shell am broadcast -a com.glocalsaino.whatpass.DEBUG_GEO_NOTIFY -p com.glocalsaino.whatpass
class DebugGeofenceReceiver : BroadcastReceiver(), KoinComponent {

    private val passStore: PassStore by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val pass = passStore.allPasses().firstOrNull { it.locations.isNotEmpty() } ?: return
        val loc = pass.locations.firstOrNull() ?: return
        val text = loc.getNameWithFallback(pass) ?: return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    GeofenceBroadcastReceiver.CHANNEL_ID,
                    context.getString(R.string.geofence_notification_channel),
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val openIntent = PendingIntent.getActivity(
            context, pass.id.hashCode(),
            Intent(context, PassViewActivity::class.java)
                .putExtra(PassViewActivityBase.EXTRA_KEY_UUID, pass.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, GeofenceBroadcastReceiver.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_location)
            .setContentTitle(pass.description ?: context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        pass.getBitmap(passStore, PassBitmapDefinitions.BITMAP_ICON)?.let { builder.setLargeIcon(it) }

        nm.notify(pass.id.hashCode(), builder.build())
    }
}
