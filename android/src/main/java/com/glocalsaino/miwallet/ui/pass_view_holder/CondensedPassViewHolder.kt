package com.glocalsaino.miwallet.ui.pass_view_holder

import android.app.Activity
import androidx.cardview.widget.CardView
import android.view.View
import android.widget.TextView
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.model.pass.Pass

class CondensedPassViewHolder(view: CardView) : PassViewHolder(view) {

    override fun apply(pass: Pass, passStore: PassStore, activity: Activity) {
        super.apply(pass, passStore, activity)

        val extraString = getExtraString(pass)

        val date = view.findViewById<TextView>(R.id.date)


        if (extraString.isNullOrBlank()) {
            date.visibility = View.GONE
        } else {
            date.text = extraString
            date.visibility = View.VISIBLE
        }

        view.findViewById<TextView>(R.id.timeButton).text = getTimeInfoString(pass)
    }
}
