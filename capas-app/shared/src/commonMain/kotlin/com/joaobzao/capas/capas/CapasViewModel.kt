package com.joaobzao.capas.capas

import co.touchlab.kermit.Logger
import com.joaobzao.capas.models.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CapasViewModel(
    log: Logger,
    private val capasRepository: CapasRepository
) : ViewModel() {
    private val log = log.withTag("CapasViewModel")

    private val mutableCapasViewState: MutableStateFlow<CapasViewState> =
        MutableStateFlow(CapasViewState())

    val capasState: StateFlow<CapasViewState>
        get() {
            return mutableCapasViewState
        }

    fun getCapas() {
        viewModelScope.launch {
            capasRepository.getCapas().collect {
                mutableCapasViewState.value = CapasViewState(it.data)
                    .also { log.v { "🤩 Updating capas: ${it.capas}" } }
            }
        }
    }

    fun removeCapa(capa: Capa) {
        viewModelScope.launch {
            capasRepository.removeId(capa.id)
            getCapas()
        }
    }

    fun restoreCapa(capa: Capa) {
        viewModelScope.launch {
            capasRepository.restoreId(capa.id)
            getCapas()
        }
    }

    override fun onCleared() {
        log.v("Clearing CapasViewModel")
    }
}

data class CapasViewState(
    val capas: CapasResponse? = null
)