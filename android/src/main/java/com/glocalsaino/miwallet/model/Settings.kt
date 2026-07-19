package com.glocalsaino.miwallet.model

import com.glocalsaino.miwallet.model.comparator.PassSortOrder
import java.io.File

interface Settings {

    fun getSortOrder(): PassSortOrder

    fun doTraceDroidEmailSend(): Boolean

    fun getPassesDir(): File

    fun getStateDir(): File

    fun isCondensedModeEnabled(): Boolean

    fun isAutomaticLightEnabled(): Boolean

    fun getNightMode(): Int
}
