package com.glocalsaino.miwallet.functions

import android.app.Activity
import com.google.android.material.snackbar.Snackbar
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.model.PassClassifier
import com.glocalsaino.miwallet.model.pass.Pass

fun moveWithUndoSnackbar(passClassifier: PassClassifier, pass: Pass, topic: String, activity: Activity) {
    val oldTopic = passClassifier.getTopic(pass, "")

    Snackbar.make(activity.window.decorView.findViewById(R.id.fam), "Pass moved to $topic", Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { passClassifier.moveToTopic(pass, oldTopic) }
            .show()
    passClassifier.moveToTopic(pass, topic)
}
