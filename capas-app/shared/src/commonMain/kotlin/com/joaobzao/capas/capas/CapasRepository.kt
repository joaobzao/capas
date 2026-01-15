package com.joaobzao.capas.capas

import com.joaobzao.capas.network.Api
import com.joaobzao.capas.network.GitHubWorkflowResponse
import com.joaobzao.capas.network.NetworkResult
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface CapasRepository {
    suspend fun getCapas(): Flow<NetworkResult<CapasResponse>>
    fun removeId(id: String)
    fun restoreId(id: String)
    fun getRemovedCapas(): List<Capa>
    fun isOnboardingCompleted(): Boolean
    fun setOnboardingCompleted()
    suspend fun getWorkflowStatus(): Flow<NetworkResult<GitHubWorkflowResponse>>
    suspend fun getFilters(): Flow<NetworkResult<List<String>>>
    fun updateOrder(orderedIds: List<String>)
}

class CapasRepositoryImpl(
    private val api: Api,
    private val settings: Settings
) : CapasRepository {

    private val KEY = "allowed_capas"
    private val ONBOARDING_KEY = "onboarding_completed"
    private val REGIONAIS_INIT_KEY = "regionais_initialized"
    private var lastCapas: CapasResponse? = null

    override fun isOnboardingCompleted(): Boolean {
        return settings.getBoolean(ONBOARDING_KEY, false)
    }

    override fun setOnboardingCompleted() {
        settings.putBoolean(ONBOARDING_KEY, true)
    }

    override suspend fun getFilters(): Flow<NetworkResult<List<String>>> {
        return api.fetchFilters()
    }

    override suspend fun getWorkflowStatus(): Flow<NetworkResult<GitHubWorkflowResponse>> {
        return api.fetchWorkflowStatus()
    }

    override suspend fun getCapas(): Flow<NetworkResult<CapasResponse>> = flow {
        api.fetchCapas().collect { result ->
            if (result is NetworkResult.Success && result.data != null) {
                lastCapas = result.data
                val capas = result.data
                val allowedIds = getAllowedIds()

                // primeira vez: inicializa todos os ids
                if (allowedIds.isEmpty()) {
                    val allIds = capas.mainNewspapers.map { it.id } +
                            capas.sportNewspapers.map { it.id } +
                            capas.economyNewspapers.map { it.id } +
                            capas.regionalNewspapers.map { it.id }
                    setAllowedIds(allIds)
                    settings.putBoolean(REGIONAIS_INIT_KEY, true)
                } else {
                    // Migração: Se regionais ainda não foi inicializado, adicionar novos IDs
                    if (!settings.getBoolean(REGIONAIS_INIT_KEY, false)) {
                        val regionalIds = capas.regionalNewspapers.map { it.id }
                        val currentAllowed = getAllowedIds()
                        // Adicionar apenas os que não estão lá (embora se não foi init, não devem estar)
                        val newAllowed = currentAllowed + regionalIds.filter { it !in currentAllowed }
                        setAllowedIds(newAllowed)
                        settings.putBoolean(REGIONAIS_INIT_KEY, true)
                    }
                }

                val currentAllowed = getAllowedIds() // Now a List
                
                // Helper to sort a list of Capas based on index in currentAllowed
                fun sortCapas(list: List<Capa>): List<Capa> {
                    return list.filter { it.id in currentAllowed }
                        .sortedBy { currentAllowed.indexOf(it.id) }
                }

                val filtered = capas.copy(
                    mainNewspapers = sortCapas(capas.mainNewspapers),
                    sportNewspapers = sortCapas(capas.sportNewspapers),
                    economyNewspapers = sortCapas(capas.economyNewspapers),
                    regionalNewspapers = sortCapas(capas.regionalNewspapers)
                )

                emit(NetworkResult.Success(filtered))
            } else {
                emit(result)
            }
        }
    }

    override fun removeId(id: String) {
        val current = getAllowedIds()
        setAllowedIds(current - id)
    }

    override fun restoreId(id: String) {
        val current = getAllowedIds()
        setAllowedIds(current + id)
    }
    
    override fun updateOrder(orderedIds: List<String>) {
        val current = getAllowedIds()
        // We only want to reorder the IDs that are present in orderedIds.
        // But wait, orderedIds might only be a subset (single category).
        // Strategy: 
        // 1. Keep IDs NOT in orderedIds in their relative original positions? 
        //    OR assume orderedIds contains the specific category active.
        //    Actually, simple merge: Remove all orderedIds from current, then insert them?
        //    Better: Just save everything if the UI passes partial lists?
        //    No, the efficient way for the list:
        //    The UI will likely reorder a subset (e.g. National).
        //    We should take the new list, and effectively replace the sequence of those items in the main list.
        //    BUT easiest for now: just update the persistence with all IDs if possible, or
        //    Standardize: `orderedIds` is just the list of visible items in their new order.
        //    But we have 3 categories.
        
        // Revised Strategy:
        // We have `current` (ALL allowed IDs).
        // `orderedIds` is the new order for a subset of them (the visible ones).
        // We want to construct `newAllowed` such that:
        // - Items NOT in `orderedIds` are kept... where?
        //   Actually, if we only reorder "National", "Sport" items are not participating.
        //   So we can just rebuild the list.
        
        // Let's rely on the fact that IDs are unique.
        // We can just construct a new list where we replace the sub-sequence of items that exist in orderedIds with orderedIds.
        // But they might not be contiguous in the global list.
        
        // Approach:
        // 1. Filter `current` to remove everything in `orderedIds`.
        // 2. But we need to know WHERE to put `orderedIds`.
        //    This is tricky if we mix categories in `allowedIds`.
        //    However, `allowedIds` is just a flat list.
        //    If we sort by `allowedIds`, the relative order of "National" items matters.
        //    The relative order of "Sport" items matters.
        //    It doesn't matter if "National" comes before "Sport" in `allowedIds` if they are displayed in different tabs.
        
        // So:
        // We can just append `orderedIds` at the end? No, that changes global order?
        // Actually, since they are displayed in separate lists (tabs), 
        // we essentially strictly care about the relative order of items *within the same category*.
        // So we can just remove all `orderedIds` from `current`, and then Append `orderedIds` (or Prepend).
        // As long as the integrity of the subset is preserved.
        
        val uniqueOrdered = orderedIds.distinct()
        val remaining = current.filter { it !in uniqueOrdered }
        
        // Use the new order for the active items, keep others as is.
        // We can put the new ones at the end, or keep original relative positions?
        // Putting at end is safe enough for separate tabs.
        setAllowedIds(remaining + uniqueOrdered)
    }

    override fun getRemovedCapas(): List<Capa> {
        val allowed = getAllowedIds()
        val capas = lastCapas ?: return emptyList()
        return (capas.mainNewspapers + capas.sportNewspapers + capas.economyNewspapers + capas.regionalNewspapers)
            .filterNot { it.id in allowed }
    }

    private fun getAllowedIds(): List<String> {
        val stored = settings.getString(KEY, "")
        return if (stored.isNotEmpty()) stored.split(",") else emptyList()
    }

    private fun setAllowedIds(ids: List<String>) {
        settings[KEY] = ids.joinToString(",")
    }
}
