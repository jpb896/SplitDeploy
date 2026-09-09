package com.jpb.splitdeploy.utils

import com.jpb.splitdeploy.UIInstallerState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object InstallerStateNotifier {
    private val _events = MutableSharedFlow<UIInstallerState>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun postState(state: UIInstallerState) {
        _events.tryEmit(state)
    }
}