package com.glocalsaino.miwallet.model

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.appcompat.app.AppCompatDelegate.*
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.R.string.preference_key_autolight
import com.glocalsaino.miwallet.R.string.preference_key_condensed
import com.glocalsaino.miwallet.model.comparator.PassSortOrder
import java.io.File

class AndroidSettings(val context: Context) : Settings {

    private val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    override fun getSortOrder(): PassSortOrder {
        val key = context.getString(R.string.preference_key_sort)
        val stringValue = sharedPreferences.getString(key, "0")
        val id = Integer.valueOf(stringValue!!)

        return PassSortOrder.values().first { it.int == id }
    }

    override fun doTraceDroidEmailSend() = true

    override fun getPassesDir() = File(context.filesDir.absolutePath, "passes")

    override fun getStateDir() = File(context.filesDir, "state")

    override fun isCondensedModeEnabled() = sharedPreferences.getBoolean(context.getString(preference_key_condensed), false)

    override fun isAutomaticLightEnabled() = sharedPreferences.getBoolean(context.getString(preference_key_autolight), true)

    override fun getNightMode(): Int {
        return when (sharedPreferences.getString(context.getString(R.string.preference_key_nightmode), "auto")) {
            "day" -> MODE_NIGHT_NO
            "night" -> MODE_NIGHT_YES
            "auto" -> MODE_NIGHT_FOLLOW_SYSTEM
            else -> MODE_NIGHT_FOLLOW_SYSTEM
        }
    }

}
