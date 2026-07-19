package com.glocalsaino.miwallet

import org.koin.core.module.Module
import org.koin.dsl.module
import com.glocalsaino.miwallet.injections.FixedPassListPassStore
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.model.Settings
import com.glocalsaino.miwallet.model.comparator.PassSortOrder
import com.glocalsaino.miwallet.model.pass.BarCode
import com.glocalsaino.miwallet.model.pass.Pass
import com.glocalsaino.miwallet.model.pass.PassBarCodeFormat
import com.glocalsaino.miwallet.model.pass.PassImpl
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File
import java.util.*

class TestApp : App() {

    override fun createKoin(): Module {

        return module {
            single { passStore as PassStore }
            single { settings }
            single { tracker }
        }
    }

    companion object {

        val tracker = mock(Tracker::class.java)
        val passStore = FixedPassListPassStore(emptyList())
        val settings = mock(Settings::class.java).apply {
            `when`(getSortOrder()).thenReturn(PassSortOrder.DATE_ASC)
            `when`(getPassesDir()).thenReturn(File(""))
            `when`(doTraceDroidEmailSend()).thenReturn(false)
        }

        fun populatePassStoreWithSinglePass() {

            val passList = ArrayList<Pass>()
            val pass = PassImpl(UUID.randomUUID().toString())
            pass.description = "description"
            pass.barCode = BarCode(PassBarCodeFormat.AZTEC, "messageprobe")
            passList.add(pass)

            fixedPassListPassStore().setList(passList)

            passStore.classifier.moveToTopic(pass, "test")
        }

        fun emptyPassStore() {
            fixedPassListPassStore().setList(emptyList())
        }

        private fun fixedPassListPassStore() = passStore as FixedPassListPassStore
    }
}
