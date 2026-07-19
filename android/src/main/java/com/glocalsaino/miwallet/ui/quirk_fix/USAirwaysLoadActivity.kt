package com.glocalsaino.miwallet.ui.quirk_fix

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.fragment.app.commit
import com.glocalsaino.miwallet.ui.AlertFragment
import com.glocalsaino.miwallet.ui.WhatPassActivity
import com.glocalsaino.miwallet.ui.PassImportActivity

class USAirwaysLoadActivity : WhatPassActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent.data
        if (data == null || "$data".indexOf("/") == -1){
            val alert = AlertFragment()
            supportFragmentManager.commit { add(alert,"AlertFrag") }
            return
        }
        val url = "$data".removeSuffix("/") ?: ""
        val split = url.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()


        val passId = split[split.size - 2] + "/" + split[split.size - 1]


        val redirectUrl = "http://prod.wap.ncrwebhost.mobi/mobiqa/wap/$passId/passbook"

        tracker.trackEvent("quirk_fix", "redirect", "usairways", null)
        val intent = Intent(this, PassImportActivity::class.java)
        intent.data = redirectUrl.toUri()
        startActivity(intent)
        finish()
    }
}
