package com.glocalsaino.miwallet.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.widget.TextView
import com.google.android.material.navigation.NavigationView
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.model.PassStore

class PassNavigationView(context: Context, attrs: AttributeSet) : NavigationView(context, attrs), KoinComponent {

    val passStore: PassStore by inject()

    private fun getIntent(id: Int) = when (id) {
        R.id.menu_scan_passes -> Intent(context, PassReaderActivity::class.java)
        R.id.menu_beacon -> Intent(context, BeaconActivity::class.java)
        R.id.menu_settings -> Intent(context, PreferenceActivity::class.java)
        R.id.menu_share -> Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, marketUrl)
            type = "text/plain"
        }
        else -> null
    }

    @SuppressLint("RestrictedApi") // FIXME: temporary workaround for false-positive
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        setNavigationItemSelectedListener { item ->
            getIntent(item.itemId)?.let {
                context.startActivity(it)
                true
            } ?: false
        }

        passStoreUpdate()
    }

    private val marketUrl by lazy { context.getString(R.string.market_url, context.packageName) }

    fun passStoreUpdate() {
        val passCount = passStore.passMap.size
        val passCountHeader = getHeaderView(0).findViewById<TextView>(R.id.pass_count_header)
        passCountHeader.text = context.getString(R.string.passes_nav, passCount)
    }
}
