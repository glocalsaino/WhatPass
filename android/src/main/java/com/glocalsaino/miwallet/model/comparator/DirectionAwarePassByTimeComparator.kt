package com.glocalsaino.miwallet.model.comparator

import com.glocalsaino.miwallet.model.pass.Pass

class DirectionAwarePassByTimeComparator(private val direction: Int) : PassByTimeComparator() {

    override fun compare(lhs: Pass, rhs: Pass): Int {
        return super.compare(lhs, rhs) * direction
    }

    companion object {
        const val DIRECTION_DESC = -1
        const val DIRECTION_ASC = 1
    }
}
