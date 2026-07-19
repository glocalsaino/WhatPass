package com.glocalsaino.miwallet.scan.events

import kotlinx.coroutines.channels.ConflatedBroadcastChannel

class PassScanEventChannelProvider {
    val channel = ConflatedBroadcastChannel<PassScanEvent>()
}