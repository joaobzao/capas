package com.joaobzao.capas.models

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

actual abstract class ViewModel {
    actual val viewModelScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    protected actual open fun onCleared() {
        viewModelScope.cancel()
    }

    fun clear() {
        onCleared()
    }
}
