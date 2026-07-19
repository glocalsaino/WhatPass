package com.glocalsaino.miwallet.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.glocalsaino.miwallet.ui.quirk_fix.OpenIphoneWebView

class AlertFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        val tracker = (requireActivity() as WhatPassActivity).tracker
        val builder = AlertDialog.Builder(activity)
        builder.setTitle("Workaround failed")
        builder.setMessage("The URL WhatPass tried to work around failed :-( some companies just send PassBooks to Apple Devices - this was an attempt to workaround this." + "Unfortunately it failed - perhaps there where changes on the serverside - you can open the site with your browser now - to see it in WhatPass in future again it would help if you can send me the pass")
        builder.setPositiveButton("Browser") { _, _ ->
            tracker.trackException(requireActivity().localClassName + "with invalid activity", false)
            val intent = Intent(requireActivity(), OpenIphoneWebView::class.java)
            intent.data = requireActivity().intent.data
            startActivity(intent)
        }
        builder.setNeutralButton("send") { _, _ ->
            val intent = Intent(Intent.ACTION_SEND)
            intent.putExtra(Intent.EXTRA_SUBJECT, "WhatPass: URLRewrite Problem")
            intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("info@whatpass.es"))
            if (requireActivity().intent.data != null) {
                intent.putExtra(Intent.EXTRA_TEXT, requireActivity().intent.data!!.toString())
            } else {
                intent.putExtra(Intent.EXTRA_TEXT, "null")
            }
            intent.type = "text/plain"
        }
        builder.setNegativeButton("cancle") { _, _ -> requireActivity().finish() }
        val dialog = builder.create()
        dialog.show()
    }
}
