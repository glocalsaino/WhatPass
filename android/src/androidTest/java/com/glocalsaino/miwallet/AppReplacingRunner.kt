package com.glocalsaino.miwallet

import org.ligi.trulesk.AppReplacingRunnerBase

class AppReplacingRunner : AppReplacingRunnerBase() {

    override fun testAppClass() = TestApp::class.java

}
