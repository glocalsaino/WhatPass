package com.glocalsaino.miwallet.model.pass

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class PassBeacon {
    var proximityUUID: String = ""
    var major: Int? = null
    var minor: Int? = null
    var relevantText: String? = null

    fun getTextWithFallback(pass: Pass) =
        if (relevantText.isNullOrBlank()) pass.description else relevantText
}
