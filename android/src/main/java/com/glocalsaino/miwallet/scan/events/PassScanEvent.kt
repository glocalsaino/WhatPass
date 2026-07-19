package com.glocalsaino.miwallet.scan.events

import com.glocalsaino.miwallet.model.pass.Pass

sealed class PassScanEvent

data class DirectoryProcessed(val dir: String) : PassScanEvent()
data class ScanFinished(val foundPasses: List<Pass>) : PassScanEvent()