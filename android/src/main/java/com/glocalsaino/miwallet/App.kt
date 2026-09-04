package com.glocalsaino.miwallet

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.jakewharton.threetenabp.AndroidThreeTen
import com.squareup.moshi.Moshi
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import com.glocalsaino.miwallet.json_adapter.ColorAdapter
import com.glocalsaino.miwallet.json_adapter.ZonedTimeAdapter
import com.glocalsaino.miwallet.model.AndroidFileSystemPassStore
import com.glocalsaino.miwallet.model.AndroidSettings
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.model.Settings
import com.glocalsaino.miwallet.scan.events.PassScanEventChannelProvider
import com.glocalsaino.miwallet.beacon.BeaconScannerManager
import com.glocalsaino.miwallet.geofence.GeofenceManager
import com.glocalsaino.miwallet.passkit.PassUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import timber.log.Timber

open class App : Application() {

    private val moshi = Moshi.Builder()
            .add(ZonedTimeAdapter())
            .add(ColorAdapter())
            .build()

    private val settings by lazy { AndroidSettings(this) }

    open fun createKoin(): Module {

        return module {
            single { AndroidFileSystemPassStore(this@App, get(), moshi) as PassStore }
            single { settings as Settings }
            single { createTracker(this@App) }
            single { PassScanEventChannelProvider() }
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        startKoin {
            if (BuildConfig.DEBUG) androidLogger()
            androidContext(this@App)
            modules(createKoin())
        }

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        AndroidThreeTen.init(this)
        AppCompatDelegate.setDefaultNightMode(settings.getNightMode())
        GeofenceManager.registerAll(this, get())
        val appContext = this
        val passStore = get<com.glocalsaino.miwallet.model.PassStore>()
        GlobalScope.launch(Dispatchers.IO) {
            BeaconScannerManager.startIfNeeded(appContext, passStore)
            // Re-register all passes with their web services on every launch so
            // PassSource always has the current FCM token, even after token rotation
            // or reinstall where onNewToken() may not have completed successfully.
            val token = PassUpdateManager.getFcmToken(appContext)
            if (token != null) PassUpdateManager.reRegisterAllPasses(appContext, token)
        }
        handleInstallReferrer()
    }

    // Reads the Play Install Referrer exactly once (first launch after install).
    // If the link that drove the install was https://glocalsaino.com/pass?url=<pass-url>,
    // the web page encodes that URL as referrer=pass_url%3D<encoded-url> in the Play Store
    // link, and we open PassImportActivity with it here so the pass is imported automatically.
    private fun handleInstallReferrer() {
        val prefs = getSharedPreferences("whatpass_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("referrer_handled", false)) return
        prefs.edit().putBoolean("referrer_handled", true).apply()

        val referrerClient = InstallReferrerClient.newBuilder(this).build()
        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                try {
                    if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                        val referrerString = referrerClient.installReferrer.installReferrer
                        val passUrl = Uri.parse("?$referrerString").getQueryParameter("pass_url")
                        if (!passUrl.isNullOrBlank()) {
                            startActivity(Intent(this@App, com.glocalsaino.miwallet.ui.PassImportActivity::class.java).apply {
                                action = Intent.ACTION_VIEW
                                data = Uri.parse(passUrl)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    }
                } finally {
                    referrerClient.endConnection()
                }
            }
            override fun onInstallReferrerServiceDisconnected() {}
        })
    }

}
