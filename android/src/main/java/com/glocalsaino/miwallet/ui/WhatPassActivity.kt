package com.glocalsaino.miwallet.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.koin.android.ext.android.inject
import com.glocalsaino.miwallet.Tracker
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.model.Settings

open class WhatPassActivity : AppCompatActivity() {

    val passStore: PassStore by inject()
    val settings: Settings by inject()
    val tracker: Tracker by inject()

    private var lastSetNightMode: Int? = null

    override fun onResume() {
        super.onResume()

        if (lastSetNightMode != null && lastSetNightMode != settings.getNightMode()) {
            ActivityCompat.recreate(this)
        }
        lastSetNightMode = settings.getNightMode()
    }

}