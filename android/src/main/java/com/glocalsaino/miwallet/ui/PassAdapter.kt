package com.glocalsaino.miwallet.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.glocalsaino.miwallet.startActivityFromClass
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.model.PassStoreProjection
import com.glocalsaino.miwallet.model.Settings
import com.glocalsaino.miwallet.ui.pass_view_holder.CondensedPassViewHolder
import com.glocalsaino.miwallet.ui.pass_view_holder.PassViewHolder
import com.glocalsaino.miwallet.ui.pass_view_holder.VerbosePassViewHolder

class PassAdapter(
        private val passListActivity: AppCompatActivity,
        private val passStoreProjection: PassStoreProjection
) : RecyclerView.Adapter<PassViewHolder>(), KoinComponent {

    private val passStore: PassStore by inject ()
    private val settings: Settings by inject ()

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): PassViewHolder {
        val inflater = LayoutInflater.from(viewGroup.context)

        val res = inflater.inflate(R.layout.pass_list_item, viewGroup, false) as CardView
        return if (settings.isCondensedModeEnabled()) {
            CondensedPassViewHolder(res)
        } else {
            VerbosePassViewHolder(res)
        }
    }

    override fun onBindViewHolder(viewHolder: PassViewHolder, position: Int) {
        val pass = passStoreProjection.passList[position]

        viewHolder.apply(pass, passStore, passListActivity)

        val root = viewHolder.view

        root.setOnClickListener {
            passStore.currentPass = pass
            passListActivity.startActivityFromClass(PassViewActivity::class.java)
        }

        root.setOnLongClickListener {
            Snackbar.make(root, R.string.please_use_the_swipe_feature, Snackbar.LENGTH_LONG).show()
            true
        }
    }

    override fun getItemId(position: Int) = position.toLong()
    override fun getItemCount() = passStoreProjection.passList.size

}
