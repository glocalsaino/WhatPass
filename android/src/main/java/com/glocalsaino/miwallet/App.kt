package com.glocalsaino.miwallet

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
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
        BeaconScannerManager.startIfNeeded(this, get())
    }

}
