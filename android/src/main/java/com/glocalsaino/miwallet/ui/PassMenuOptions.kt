package com.glocalsaino.miwallet.ui

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NavUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.glocalsaino.miwallet.startActivityFromClass
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.Tracker
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.model.Settings
import com.glocalsaino.miwallet.model.pass.Pass
import com.glocalsaino.miwallet.geofence.GeofenceManager
import com.glocalsaino.miwallet.passkit.PassUpdateManager
import java.io.File

class PassMenuOptions(val activity: Activity, val pass: Pass) : KoinComponent {

    val passStore: PassStore by inject()
    val tracker: Tracker by inject()
    val settings: Settings by inject()

    fun process(item: MenuItem): Boolean {
        when (item.itemId) {

            R.id.menu_delete -> {
                tracker.trackEvent("ui_action", "delete", "delete", null)

                val builder = AlertDialog.Builder(activity)
                builder.setMessage(activity.getString(R.string.dialog_delete_confirm_text))
                builder.setTitle(activity.getString(R.string.dialog_delete_title))
                builder.setIcon(R.drawable.ic_alert_warning)

                val sourceDeleteCheckBoxView = LayoutInflater.from(activity).inflate(R.layout.delete_dialog_layout, null)

                val source = pass.getSource(passStore)

                val checkBox = sourceDeleteCheckBoxView.findViewById<CheckBox>(R.id.sourceDeleteCheckbox)
                if (source != null && source.startsWith("file://")) {


                    checkBox.text = activity.getString(R.string.dialog_delete_confirm_delete_source_checkbox)
                    builder.setView(sourceDeleteCheckBoxView)
                }

                builder.setPositiveButton(activity.getString(R.string.delete)) { _, _ ->
                    if (checkBox.isChecked) {

                        File(source!!.replace("file://", "")).delete()
                    }
                    PassUpdateManager.unregisterIfPossible(activity.applicationContext, pass)
                    GeofenceManager.unregister(activity.applicationContext, pass)
                    passStore.deletePassWithId(pass.id)
                    if (activity is PassViewActivityBase) {
                        val passListIntent = Intent(activity, PassListActivity::class.java)
                        NavUtils.navigateUpTo(activity, passListIntent)
                    }
                }
                builder.setNegativeButton(android.R.string.no, null)

                builder.show()

                return true
            }

            R.id.menu_map -> {
                activity.showNavigateToLocationsDialog(pass, false)
                return true
            }

            R.id.menu_share -> {
                tracker.trackEvent("ui_action", "share", "shared", null)
                PassExportTaskAndShare(activity, passStore.getPathForID(pass.id)).execute()
                return true
            }

        }
        return false
    }

}
