package com.glocalsaino.miwallet.model

import com.glocalsaino.miwallet.model.pass.PassType.*

object PassDefinitions {

    val TYPE_TO_NAME = mapOf(COUPON to "coupon",
            EVENT to "eventTicket",
            PKBOARDING to "boardingPass",
            GENERIC to "generic",
            LOYALTY to "storeCard")

    val NAME_TO_TYPE = TYPE_TO_NAME.entries.associate { it.value to it.key }

    // Per Apple's PassKit image guidelines: the "strip" banner only applies to
    // coupon/storeCard/eventTicket. "thumbnail" (shown beside the primary fields) applies to
    // every style except boardingPass, which uses a fixed transit-specific layout instead.
    val STRIP_VISIBLE_TYPES = setOf(COUPON, LOYALTY, EVENT)
    val THUMBNAIL_VISIBLE_TYPES = setOf(COUPON, LOYALTY, EVENT, GENERIC)

}
