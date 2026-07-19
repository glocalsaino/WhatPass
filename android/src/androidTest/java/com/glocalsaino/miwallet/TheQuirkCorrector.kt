package com.glocalsaino.miwallet

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import com.glocalsaino.miwallet.functions.loadPassFromAsset

class TheQuirkCorrector {

    @Test
    fun testWestbahnDescriptionIsFixed() {
        loadPassFromAsset("passes/workarounds/westbahn/special.pkpass") {
            assertThat(it!!.description).isEqualTo("Wien Westbahnhof->Amstetten")
        }
    }

}
