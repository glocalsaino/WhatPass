package com.glocalsaino.miwallet.ui.edit.dialogs

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.glocalsaino.miwallet.inflate
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.model.pass.BarCode
import com.glocalsaino.miwallet.model.pass.Pass
import com.glocalsaino.miwallet.ui.edit.BarcodeEditController

fun showBarcodeEditDialog(context: AppCompatActivity, refreshCallback: () -> Unit, pass: Pass, barCode: BarCode) {
    val view = context.inflate(R.layout.barcode_edit)

    val barcodeEditController = BarcodeEditController(view, context, barCode)

    AlertDialog.Builder(context).setView(view)
            .setTitle(R.string.edit_barcode_dialog_title)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                pass.barCode = barcodeEditController.getBarCode()
                refreshCallback.invoke()
            }
            .show()
}
