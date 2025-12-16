package com.joaobzao.capas.capas

import co.touchlab.kermit.Logger
import com.joaobzao.capas.models.ViewModel
import com.joaobzao.capas.network.GitHubWorkflowRun
import com.joaobzao.capas.network.NetworkResult
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
        get() = mutableCapasViewState

    fun getCapas() {
        viewModelScope.launch {
            capasRepository.getCapas().collect { result ->
                mutableCapasViewState.value = CapasViewState(
                    capas = result.data,
                    removed = capasRepository.getRemovedCapas()
                ).also { log.v { "🤩 Updating capas: ${it.capas}" } }
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

    fun updateCapaOrder(capas: List<Capa>) {
        val orderedIds = capas.map { it.id }
        capasRepository.updateOrder(orderedIds)
        // No need to refresh immediately as UI has the state, but robust to do so?
        // UI uses local state for drag, but eventually needs to be consistent.
        // Repository update doesn't emit new flow unless we trigger getCapas or separate store.
        // Let's trigger getCapas() to ensure consistency.
        // Actually, triggering getCapas might cause a jitter if the local state is ahead.
        // But for persistence correctness it's safer.
        // Maybe silent update is better for UX?
        // User already has the list on screen.
        // Let's NOT trigger getCapas, just update persistence.
    }

    fun isOnboardingCompleted(): Boolean {
        return capasRepository.isOnboardingCompleted()
    }

    fun completeOnboarding() {
        capasRepository.setOnboardingCompleted()
    }

    fun getWorkflowStatus() {
        viewModelScope.launch {
            capasRepository.getWorkflowStatus().collect { result ->
                if (result is NetworkResult.Success) {
                    val run = result.data?.workflowRuns?.firstOrNull()
                    mutableCapasViewState.value = mutableCapasViewState.value.copy(
                        workflowStatus = run
                    )
                }
            }
        }
    }

    override fun onCleared() {
        log.v("Clearing CapasViewModel")
    }
}

data class CapasViewState(
    val capas: CapasResponse? = null,
    val removed: List<Capa> = emptyList(),
    val workflowStatus: GitHubWorkflowRun? = null
)
