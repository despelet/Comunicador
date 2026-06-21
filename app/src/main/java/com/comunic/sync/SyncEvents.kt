package com.comunic.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SyncEvents {

    private val _dataChanged =
        MutableSharedFlow<Unit>(
            replay = 0,
            extraBufferCapacity = 1
        )

    val dataChanged =
        _dataChanged.asSharedFlow()

    fun notifyDataChanged() {
        _dataChanged.tryEmit(Unit)
    }
}