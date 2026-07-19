package com.glocalsaino.miwallet.model

import kotlinx.coroutines.channels.BroadcastChannel
import com.glocalsaino.miwallet.model.pass.Pass
import java.io.File

interface PassStore {

    val updateChannel: BroadcastChannel<PassStoreUpdateEvent>

    fun save(pass: Pass)

    fun getPassbookForId(id: String): Pass?

    fun deletePassWithId(id: String): Boolean

    // Drops any cached in-memory Pass for this id, so the next getPassbookForId()
    // re-reads it from disk instead of returning stale data. Needed after a pass's
    // files on disk were overwritten in place (e.g. re-importing an updated pass).
    fun invalidate(id: String)

    fun getPathForID(id: String): File

    val passMap: Map<String, Pass>

    var currentPass: Pass?

    val classifier: PassClassifier

    fun notifyChange()

    fun syncPassStoreWithClassifier(defaultTopic: String)

    fun allPasses(): List<Pass>
}
