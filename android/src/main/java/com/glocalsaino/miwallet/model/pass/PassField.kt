package com.glocalsaino.miwallet.model.pass

import android.content.res.Resources
import androidx.annotation.StringRes
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class PassField(var key: String?, var label: String?, var value: String?, var hide: Boolean, var hint:String? = null, var changeMessage: String? = null) {

    fun toHtmlSnippet(labelColor: Int? = null, valueColor: Int? = null): String {
        val result = StringBuilder()

        label?.let {
            result.append(wrapWithColor("<b>$it</b>", labelColor))
            result.append("<br/>")
        }
        value?.let {
            result.append(wrapWithColorPreservingLinks(it.replace("\n", "<br/>"), valueColor))
            result.append("<br/>")
        }

        if (label != null || value != null) {
            result.append("<br/>")
        }
        return "$result"
    }

    private fun wrapWithColor(html: String, color: Int?): String {
        if (color == null) return html
        val hex = String.format("#%06X", 0xFFFFFF and color)
        return "<font color='$hex'>$html</font>"
    }

    // Apple pass values may embed raw <a href> links (e.g. social links). Those keep the
    // conventional blue link color instead of the pass's foreground color, which only
    // applies to the surrounding plain text.
    private fun wrapWithColorPreservingLinks(html: String, color: Int?): String {
        val linkTag = Regex("(?is)(<a\\b[^>]*>)(.*?)(</a>)")
        val result = StringBuilder()
        var lastEnd = 0

        for (match in linkTag.findAll(html)) {
            val before = html.substring(lastEnd, match.range.first)
            if (before.isNotEmpty()) result.append(wrapWithColor(before, color))
            val (openTag, inner, closeTag) = match.destructured
            result.append(openTag)
            result.append(wrapWithColor(inner, LINK_COLOR))
            result.append(closeTag)
            lastEnd = match.range.last + 1
        }
        val tail = html.substring(lastEnd)
        if (tail.isNotEmpty()) result.append(wrapWithColor(tail, color))

        return "$result"
    }

    companion object {
        private const val LINK_COLOR = 0xFF3399FF.toInt()

        fun create(@StringRes label: Int, @StringRes value: Int, res: Resources, hide: Boolean = false, hint: String? = null) = PassField(null, res.getString(label), res.getString(value), hide, hint)
    }
}