package com.glocalsaino.miwallet

import android.content.Context
import timber.log.Timber

class NotTracker : Tracker {

    override fun trackException(s: String, e: Throwable, fatal: Boolean) {
        Timber.w(e, "Exception %s (fatal=%s)", s, fatal)
    }

    override fun trackException(s: String, fatal: Boolean) {
        Timber.w("Exception %s (fatal=%s)", s, fatal)
    }

    override fun trackEvent(category: String?, action: String?, label: String?, val_: Long?) {
        // no analytics backend wired up yet
    }
}

fun createTracker(context: Context): Tracker = NotTracker()
