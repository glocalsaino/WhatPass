package com.glocalsaino.miwallet.passkit

import android.content.Context

object PassNotificationPrefs {

    private const val PREFS_NAME = "miwallet_notification_prefs"

    private fun key(passTypeId: String, serial: String) = "muted_${passTypeId}_$serial"

    fun isMuted(context: Context, passTypeId: String?, serial: String?): Boolean {
        if (passTypeId == null || serial == null) return false
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(key(passTypeId, serial), false)
    }

    fun setMuted(context: Context, passTypeId: String?, serial: String?, muted: Boolean) {
        if (passTypeId == null || serial == null) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key(passTypeId, serial), muted)
                .apply()
    }
}
