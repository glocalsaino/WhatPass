package com.glocalsaino.miwallet.ui.pass_view_holder

import android.app.Activity
import androidx.cardview.widget.CardView
import android.view.View
import android.widget.TextView
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.model.pass.Pass

open class VerbosePassViewHolder(view: CardView) : PassViewHolder(view) {

    override fun apply(pass: Pass, passStore: PassStore, activity: Activity) {
        super.apply(pass, passStore, activity)

        val dateOrExtraText = getTimeInfoString(pass)

        val date = view.findViewById<TextView>(R.id.date)
        if (dateOrExtraText != null && dateOrExtraText.isNotEmpty()) {
            date.text = dateOrExtraText
            date.visibility = View.VISIBLE
        } else {
            date.visibility = View.GONE
        }
    }
}
