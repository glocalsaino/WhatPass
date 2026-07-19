package com.glocalsaino.miwallet.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.scale
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.model.InputStreamWithSource
import com.glocalsaino.miwallet.model.PassBitmapDefinitions.BITMAP_ICON
import com.glocalsaino.miwallet.model.State
import com.glocalsaino.miwallet.model.pass.Pass
import com.glocalsaino.miwallet.passkit.PassNotificationPrefs
import com.glocalsaino.miwallet.geofence.GeofenceManager
import com.glocalsaino.miwallet.passkit.PassUpdateManager
import com.glocalsaino.miwallet.ui.UnzipPassController.InputStreamUnzipControllerSpec
import java.io.IOException

@SuppressLint("Registered")
open class PassViewActivityBase : WhatPassActivity() {

    lateinit var currentPass: Pass
    private var fullBrightnessSet = false
    private var updateHttpCall: Call? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // a little hack because I strongly disagree with the style guide here
        // ;-)
        // not having the Actionbar overflow menu also with devices with hardware
        // key really helps discoverability
        // http://stackoverflow.com/questions/9286822/how-to-force-use-of-overflow-menu-on-devices-with-menu-button
        try {
            val config = ViewConfiguration.get(this)
            val menuKeyField = ViewConfiguration::class.java.getDeclaredField("sHasPermanentMenuKey")
            menuKeyField.isAccessible = true
            menuKeyField.setBoolean(config, false)
        } catch (ex: Exception) {
            // Ignore - but at least we tried ;-)
        }

        updateCurrentPass()
    }

    override fun onDestroy() {
        super.onDestroy()
        updateHttpCall?.cancel()
    }

    override fun onPause() {
        super.onPause()
        State.lastSelectedPassUUID = currentPass.id
    }

    override fun onResume() {
        super.onResume()

        configureActionBar()

        if (settings.isAutomaticLightEnabled()) {
            setToFullBrightness()
        }
    }

    private fun updateCurrentPass() {
        val uuid = intent.getStringExtra(EXTRA_KEY_UUID)

        if (uuid != null) {
            passStore.currentPass = passStore.getPassbookForId(uuid)
        }

        if (passStore.currentPass == null) {
            passStore.currentPass = passStore.getPassbookForId(State.lastSelectedPassUUID)
        }

        if (passStore.currentPass == null) {
            tracker.trackException("pass not present in $this", false)
            finish()
            return
        }

        currentPass = passStore.currentPass!!
    }

    protected fun configureActionBar() {
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    protected open fun refresh() {
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_pass_view, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val res = super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.menu_light).isVisible = !fullBrightnessSet

        menu.findItem(R.id.menu_share)?.isVisible = !currentPass.sharingProhibited

        val isMuted = PassNotificationPrefs.isMuted(this, currentPass.passIdent, currentPass.serial)
        menu.findItem(R.id.menu_mute_notifications).setTitle(
                if (isMuted) R.string.menu_unmute_notifications else R.string.menu_mute_notifications
        )

        val deleteItem = menu.findItem(R.id.menu_delete)
        val redTitle = SpannableString(deleteItem.title)
        redTitle.setSpan(ForegroundColorSpan(Color.RED), 0, redTitle.length, 0)
        deleteItem.title = redTitle

        return res
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (PassMenuOptions(this, currentPass).process(item)) {
            return true
        }

        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            R.id.menu_light -> {
                setToFullBrightness()
                true
            }

            R.id.install_shortcut -> {
                createShortcut()
                true
            }

            R.id.menu_update -> {
                startPassUpdate()
                true
            }

            R.id.menu_mute_notifications -> {
                val newMutedState = !PassNotificationPrefs.isMuted(this, currentPass.passIdent, currentPass.serial)
                PassNotificationPrefs.setMuted(this, currentPass.passIdent, currentPass.serial, newMutedState)
                invalidateOptionsMenu()
                Snackbar.make(window.decorView,
                        if (newMutedState) R.string.notifications_muted else R.string.notifications_unmuted,
                        Snackbar.LENGTH_LONG).show()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }

    }

    private fun createShortcut() {
        val passBitmap = currentPass.getBitmap(passStore, BITMAP_ICON)
        val shortcutIcon = passBitmap?.scale(128, 128, filter = true) ?: BitmapFactory.decodeResource(resources, R.drawable.ic_launcher)
        val name: CharSequence = currentPass.description.let {
            if (it.isNullOrEmpty()) "pass" else it
        }
        val targetIntent = Intent(this, PassViewActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .putExtra(EXTRA_KEY_UUID, currentPass.id)
        val shortcutInfo = ShortcutInfoCompat.Builder(this, "shortcut$name")
            .setIntent(targetIntent)
            .setShortLabel(name)
            .setIcon(IconCompat.createWithBitmap(shortcutIcon))
            .build()
        ShortcutManagerCompat.requestPinShortcut(this, shortcutInfo, null)
    }

    private fun startPassUpdate() {
        val pass = currentPass
        val dlg = ProgressDialog(this).also {
            it.setMessage(getString(R.string.downloading_new_pass_version))
            it.show()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            val url = pass.webServiceURL + "/v1/passes/" + pass.passIdent + "/" + pass.serial
            val request = Request.Builder().url(url)
                .addHeader("Authorization", "ApplePass " + pass.authToken)
                .build()
            val call = client.newCall(request)
            updateHttpCall = call
            try {
                val response = call.execute()
                response.body?.let { body ->
                    val inputStreamWithSource = InputStreamWithSource(url, body.byteStream())
                    val spec = InputStreamUnzipControllerSpec(
                        inputStreamWithSource, this@PassViewActivityBase, passStore,
                        object : UnzipPassController.SuccessCallback {
                            override fun call(uuid: String) {
                                runOnUiThread {
                                    if (isFinishing || isDestroyed) return@runOnUiThread
                                    dlg.dismiss()
                                    if (currentPass.id != uuid) passStore.deletePassWithId(currentPass.id)
                                    val newPass = passStore.getPassbookForId(uuid)
                                    passStore.currentPass = newPass
                                    currentPass = passStore.currentPass!!
                                    PassUpdateManager.registerForUpdatesIfPossible(applicationContext, currentPass)
                                    GeofenceManager.register(applicationContext, currentPass)
                                    refresh()
                                    Snackbar.make(window.decorView, R.string.pass_updated, Snackbar.LENGTH_LONG).show()
                                }
                            }
                        },
                        object : UnzipPassController.FailCallback {
                            override fun fail(reason: String) {
                                runOnUiThread {
                                    if (isFinishing || isDestroyed) return@runOnUiThread
                                    dlg.dismiss()
                                    AlertDialog.Builder(this@PassViewActivityBase)
                                        .setMessage("Could not update pass :( $reason)")
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show()
                                }
                            }
                        }
                    )
                    spec.overwrite = true
                    UnzipPassController.processInputStream(spec)
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    if (!isDestroyed) dlg.dismiss()
                }
            }
        }
    }

    private fun setToFullBrightness() {
        val win = window
        val params = win.attributes
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        win.attributes = params
        fullBrightnessSet = true
        invalidateOptionsMenu()
    }

    companion object {

        const val EXTRA_KEY_UUID = "uuid"

        fun mightPassBeAbleToUpdate(pass: Pass?): Boolean {
            return pass?.webServiceURL != null && pass.passIdent != null && pass.serial != null
        }
    }
}