package com.glocalsaino.miwallet.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.model.pass.Pass

object GeofenceManager {

    private const val TAG = "GeofenceManager"
    private const val RADIUS_METERS = 150f

    fun hasLocationPermission(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bg = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            return bg == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    @SuppressLint("MissingPermission")
    fun registerAll(context: Context, passStore: PassStore) {
        if (!hasLocationPermission(context)) {
            Log.d(TAG, "registerAll: no location permission, skipping")
            return
        }
        val geofences = passStore.allPasses().flatMap { pass -> geofencesForPass(pass) }
        Log.d(TAG, "registerAll: ${geofences.size} geofences to register")
        if (geofences.isEmpty()) return
        addGeofences(context, geofences)
    }

    @SuppressLint("MissingPermission")
    fun register(context: Context, pass: Pass) {
        if (!hasLocationPermission(context)) return
        val geofences = geofencesForPass(pass)
        if (geofences.isEmpty()) return
        addGeofences(context, geofences)
    }

    fun unregister(context: Context, pass: Pass) {
        if (pass.locations.isEmpty()) return
        val ids = pass.locations.indices.map { geofenceId(pass.id, it) }
        LocationServices.getGeofencingClient(context).removeGeofences(ids)
            .addOnFailureListener { Log.w(TAG, "Failed to remove geofences for ${pass.id}", it) }
    }

    @SuppressLint("MissingPermission")
    private fun addGeofences(context: Context, geofences: List<Geofence>) {
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()
        LocationServices.getGeofencingClient(context)
            .addGeofences(request, pendingIntent(context))
            .addOnSuccessListener { Log.d(TAG, "addGeofences: ${geofences.size} registered OK") }
            .addOnFailureListener { Log.w(TAG, "addGeofences: FAILED", it) }
    }

    private fun geofencesForPass(pass: Pass): List<Geofence> =
        pass.locations.mapIndexedNotNull { index, loc ->
            if (loc.lat == 0.0 && loc.lon == 0.0) null
            else Geofence.Builder()
                .setRequestId(geofenceId(pass.id, index))
                .setCircularRegion(loc.lat, loc.lon, RADIUS_METERS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context.applicationContext, 0,
        Intent(context.applicationContext, GeofenceBroadcastReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    fun geofenceId(passId: String, index: Int) = "${passId}_loc_$index"
    fun passIdFromGeofenceId(id: String) = id.substringBefore("_loc_").ifEmpty { null }
    fun locationIndexFromGeofenceId(id: String) = id.substringAfterLast("_loc_").toIntOrNull()
}
