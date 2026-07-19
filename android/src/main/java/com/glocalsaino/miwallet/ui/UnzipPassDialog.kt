package com.glocalsaino.miwallet.ui

import android.app.Activity
import android.app.ProgressDialog
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.model.InputStreamWithSource
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.ui.UnzipPassController.FailCallback
import com.glocalsaino.miwallet.ui.UnzipPassController.InputStreamUnzipControllerSpec
import com.glocalsaino.miwallet.ui.UnzipPassController.SuccessCallback

object UnzipPassDialog {

    private fun displayError(activity: Activity, title: String, err: String) {
        AlertDialog.Builder(activity).setTitle(title)
                .setMessage(err)
                .setPositiveButton(android.R.string.ok) { _, _ -> activity.finish() }
                .show()
    }


    fun show(ins: InputStreamWithSource,
             activity: Activity,
             passStore: PassStore,
             callAfterFinishOnUIThread: (path: String) -> Unit) {
        if (activity.isFinishing) {
            return  // no need to act any more ..
        }

        val dialog = ProgressDialog.show(activity,
                activity.getString(R.string.unzip_pass_dialog_title),
                activity.getString(R.string.unzip_pass_dialog_message),
                true)
        dialog.setCancelable(false)

        class AlertDialogUpdater(private val call_after_finish: (path: String) -> Unit) : Runnable {

            override fun run() {
                val spec = InputStreamUnzipControllerSpec(ins, activity, passStore, object : SuccessCallback {

                    override fun call(uuid: String) {
                        activity.runOnUiThread(Runnable {
                            if (!prepareResult(activity, dialog)) {
                                return@Runnable
                            }

                            call_after_finish.invoke(uuid)
                        })
                    }
                }, object : FailCallback {
                    override fun fail(reason: String) {
                        activity.runOnUiThread(Runnable {
                            if (!prepareResult(activity, dialog)) {
                                return@Runnable
                            }

                            displayError(activity, activity.getString(R.string.invalid_passbook_title), reason)
                        })
                    }
                })
                UnzipPassController.processInputStream(spec)
            }
        }

        val alertDialogUpdater = AlertDialogUpdater(callAfterFinishOnUIThread)
        (activity as LifecycleOwner).lifecycleScope.launch(Dispatchers.IO) {
            alertDialogUpdater.run()
        }

    }

    private fun prepareResult(activity: Activity, dialog: ProgressDialog): Boolean {
        if (activity.isFinishing) {
            return false
        }

        if (dialog.isShowing) {
            try {
                dialog.dismiss()
                return true
            } catch (ignored: IllegalArgumentException) {
                // Would love a better option - searched a long time - found nothing - and this is better than a crash
            }

        }
        return false
    }

}
