package com.glocalsaino.miwallet.printing

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.print.PrintManager
import androidx.core.content.getSystemService
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.model.pass.Pass

@TargetApi(Build.VERSION_CODES.KITKAT)
fun doPrint(context: Context, pass: Pass) {
    val printManager = context.getSystemService<PrintManager>()!!
    val jobName = context.getString(R.string.app_name) + " print of " + pass.description
    printManager.print(jobName, PassPrintDocumentAdapter(context, pass, jobName), null)
}
