package com.joaobzao.capas.models

actual abstract class ViewModel actual constructor() {
    actual val viewModelScope: kotlinx.coroutines.CoroutineScope
        get() = TODO("Not yet implemented")

    protected actual open fun onCleared() {
    }
}