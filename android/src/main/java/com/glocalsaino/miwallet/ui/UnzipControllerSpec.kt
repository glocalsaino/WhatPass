package com.glocalsaino.miwallet.ui

import android.content.Context
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.model.Settings
import com.glocalsaino.miwallet.ui.UnzipPassController.FailCallback
import com.glocalsaino.miwallet.ui.UnzipPassController.SuccessCallback
import java.io.File

open class UnzipControllerSpec(var targetPath: File,
                               val context: Context,
                               val passStore: PassStore,
                               val onSuccessCallback: SuccessCallback?,
                               val failCallback: FailCallback?) {
    // Re-importing a pass that's already installed (same passTypeIdentifier+serialNumber)
    // should refresh it with the latest content, the same way scanning/opening it the first
    // time installs it - not silently do nothing.
    var overwrite = true

    constructor(context: Context, passStore: PassStore, onSuccessCallback: SuccessCallback?, failCallback: FailCallback?, settings: Settings)
            : this(settings.getPassesDir(), context, passStore, onSuccessCallback, failCallback)

}
