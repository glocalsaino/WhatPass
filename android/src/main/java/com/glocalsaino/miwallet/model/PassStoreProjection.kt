package com.glocalsaino.miwallet.model

import com.glocalsaino.miwallet.model.comparator.PassSortOrder
import com.glocalsaino.miwallet.model.pass.Pass
import java.util.*

class PassStoreProjection(private val passStore: PassStore, private val topic: String, private val passSortOrder: PassSortOrder? = null) {

    var passList: List<Pass> = ArrayList()
        private set

    init {
        refresh()
    }

    fun refresh() {
        val newPassList = passStore.classifier.getPassListByTopic(topic)
        if (passSortOrder != null) {
            Collections.sort(newPassList, passSortOrder.toComparator())
        }

        passList = newPassList
    }
}
