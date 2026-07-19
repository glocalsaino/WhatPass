package com.glocalsaino.miwallet.ui.edit.dialogs

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import androidx.appcompat.app.AlertDialog
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.databinding.EditColorBinding
import com.glocalsaino.miwallet.model.pass.Pass

private val PRESET_COLORS = intArrayOf(
    Color.parseColor("#F44336"), Color.parseColor("#E91E63"), Color.parseColor("#9C27B0"),
    Color.parseColor("#673AB7"), Color.parseColor("#3F51B5"), Color.parseColor("#2196F3"),
    Color.parseColor("#03A9F4"), Color.parseColor("#00BCD4"), Color.parseColor("#009688"),
    Color.parseColor("#4CAF50"), Color.parseColor("#8BC34A"), Color.parseColor("#CDDC39"),
    Color.parseColor("#FFEB3B"), Color.parseColor("#FFC107"), Color.parseColor("#FF9800"),
    Color.parseColor("#FF5722"), Color.parseColor("#795548"), Color.parseColor("#9E9E9E"),
    Color.parseColor("#607D8B"), Color.parseColor("#000000")
)

fun showColorPickDialog(context: Context, pass: Pass, refreshCallback: () -> Unit) {
    val inflate = EditColorBinding.inflate(LayoutInflater.from(context))

    val dialog = AlertDialog.Builder(context)
        .setView(inflate.root)
        .setTitle(R.string.change_color_dialog_title)
        .setNegativeButton(android.R.string.cancel, null)
        .create()

    val swatchSizePx = (context.resources.displayMetrics.density * 48).toInt()
    for (color in PRESET_COLORS) {
        val swatch = View(context).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = swatchSizePx
                height = swatchSizePx
                setMargins(8, 8, 8, 8)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            setOnClickListener {
                pass.accentColor = color
                refreshCallback.invoke()
                dialog.dismiss()
            }
        }
        inflate.colorGrid.addView(swatch)
    }

    dialog.show()
}
