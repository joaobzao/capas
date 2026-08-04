package com.joaobzao.capas.capas

import com.joaobzao.capas.logBreadcrumb
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
    fun isInternationalAnnouncementSeen(): Boolean
    fun setInternationalAnnouncementSeen()
    suspend fun getWorkflowStatus(): Flow<NetworkResult<GitHubWorkflowResponse>>
    fun updateOrder(orderedIds: List<String>)
    fun toggleFavorite(id: String)
    fun isFavorite(id: String): Boolean
    fun getFavoriteIds(): List<String>
    fun getFavoriteCapas(): List<Capa>
    fun updateFavoriteOrder(orderedIds: List<String>)
}

class CapasRepositoryImpl(
    private val api: Api,
    private val settings: Settings
) : CapasRepository {

    private val KEY = "allowed_capas"
    private val FAVORITES_KEY = "favorite_capas"
    private val ONBOARDING_KEY = "onboarding_completed"
    private val REGIONAIS_INIT_KEY = "regionais_initialized"
    private val INTERNACIONAL_INIT_KEY = "internacional_initialized"
    private val ALL_SECTIONS_RESYNC_KEY = "all_sections_resynced_sapo"
    private val INTERNATIONAL_ANNOUNCEMENT_SEEN_KEY = "international_announcement_seen"
    private var lastCapas: CapasResponse? = null

    override fun isOnboardingCompleted(): Boolean {
        return settings.getBoolean(ONBOARDING_KEY, false)
    }

    override fun setOnboardingCompleted() {
        settings.putBoolean(ONBOARDING_KEY, true)
        // New users discover international covers as part of the app, so they
        // should never see the "what's new" announcement aimed at existing users.
        settings.putBoolean(INTERNATIONAL_ANNOUNCEMENT_SEEN_KEY, true)
    }

    override fun isInternationalAnnouncementSeen(): Boolean {
        return settings.getBoolean(INTERNATIONAL_ANNOUNCEMENT_SEEN_KEY, false)
    }

    override fun setInternationalAnnouncementSeen() {
        settings.putBoolean(INTERNATIONAL_ANNOUNCEMENT_SEEN_KEY, true)
    }

    override suspend fun getWorkflowStatus(): Flow<NetworkResult<GitHubWorkflowResponse>> {
        return api.fetchWorkflowStatus()
    }

    override suspend fun getCapas(): Flow<NetworkResult<CapasResponse>> = flow {
        logBreadcrumb("api: capas fetch start")
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
                            capas.regionalNewspapers.map { it.id } +
                            capas.internationalNewspapers.map { it.id }
                    setAllowedIds(allIds)
                    settings.putBoolean(REGIONAIS_INIT_KEY, true)
                    settings.putBoolean(INTERNACIONAL_INIT_KEY, true)
                    // Instalação nova: já inicializa com todas as capas do SAPO,
                    // por isso a migração SAPO não tem nada a acrescentar.
                    settings.putBoolean(ALL_SECTIONS_RESYNC_KEY, true)
                } else {
                    // Migração: Se regionais ainda não foi inicializado, adicionar novos IDs
                    if (!settings.getBoolean(REGIONAIS_INIT_KEY, false)) {
                        val regionalIds = capas.regionalNewspapers.map { it.id }
                        val currentAllowed = getAllowedIds()
                        val newAllowed = currentAllowed + regionalIds.filter { it !in currentAllowed }
                        setAllowedIds(newAllowed)
                        settings.putBoolean(REGIONAIS_INIT_KEY, true)
                    }
                    // Migração: adicionar IDs internacionais que ainda não estejam em allowedIds
                    if (capas.internationalNewspapers.isNotEmpty()) {
                        val internationalIds = capas.internationalNewspapers.map { it.id }
                        val currentAllowed = getAllowedIds()
                        val missingIds = internationalIds.filter { it !in currentAllowed }
                        if (missingIds.isNotEmpty()) {
                            setAllowedIds(currentAllowed + missingIds)
                        }
                        settings.putBoolean(INTERNACIONAL_INIT_KEY, true)
                    }

                    // Migração SAPO: a fonte das capas passou de vercapas para
                    // SAPO, que traz muitos mais jornais. Uma única vez, acrescentar
                    // ao fim os ids novos (preservando a ordem já guardada pelo
                    // utilizador). Sem isto, sortCapas() filtra os ids desconhecidos
                    // e as novas capas nunca apareceriam para quem já usa a app.
                    if (!settings.getBoolean(ALL_SECTIONS_RESYNC_KEY, false)) {
                        val fetchedIds = capas.mainNewspapers.map { it.id } +
                                capas.sportNewspapers.map { it.id } +
                                capas.economyNewspapers.map { it.id } +
                                capas.regionalNewspapers.map { it.id } +
                                capas.internationalNewspapers.map { it.id }
                        setAllowedIds(mergeNewAllowedIds(getAllowedIds(), fetchedIds))
                        settings.putBoolean(ALL_SECTIONS_RESYNC_KEY, true)
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
                    regionalNewspapers = sortCapas(capas.regionalNewspapers),
                    internationalNewspapers = sortCapas(capas.internationalNewspapers)
                )

                val total = filtered.mainNewspapers.size +
                        filtered.sportNewspapers.size +
                        filtered.economyNewspapers.size +
                        filtered.regionalNewspapers.size
                logBreadcrumb("api: capas fetch ok, count=$total")
                emit(NetworkResult.Success(filtered))
            } else {
                logBreadcrumb("api: capas fetch ${result::class.simpleName}")
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
        return (capas.mainNewspapers + capas.sportNewspapers + capas.economyNewspapers + capas.regionalNewspapers + capas.internationalNewspapers)
            .filterNot { it.id in allowed }
    }

    private fun getAllowedIds(): List<String> {
        val stored = settings.getString(KEY, "")
        return if (stored.isNotEmpty()) stored.split(",") else emptyList()
    }

    private fun setAllowedIds(ids: List<String>) {
        settings[KEY] = ids.joinToString(",")
    }

    override fun toggleFavorite(id: String) {
        val current = getFavoriteIds()
        if (id in current) {
            setFavoriteIds(current - id)
        } else {
            setFavoriteIds(current + id)
        }
    }

    override fun isFavorite(id: String): Boolean {
        return id in getFavoriteIds()
    }

    override fun getFavoriteIds(): List<String> {
        val stored = settings.getString(FAVORITES_KEY, "")
        return if (stored.isNotEmpty()) stored.split(",") else emptyList()
    }

    override fun updateFavoriteOrder(orderedIds: List<String>) {
        val current = getFavoriteIds()
        val uniqueOrdered = orderedIds.distinct()
        val remaining = current.filter { it !in uniqueOrdered }
        setFavoriteIds(uniqueOrdered + remaining)
    }

    override fun getFavoriteCapas(): List<Capa> {
        val favoriteIds = getFavoriteIds()
        val capas = lastCapas ?: return emptyList()
        return (capas.mainNewspapers + capas.sportNewspapers + capas.economyNewspapers + capas.regionalNewspapers + capas.internationalNewspapers)
            .filter { it.id in favoriteIds }
            .sortedBy { favoriteIds.indexOf(it.id) }
    }

    private fun setFavoriteIds(ids: List<String>) {
        settings[FAVORITES_KEY] = ids.joinToString(",")
    }
}

/**
 * Junta ao fim da lista de ids permitidos os ids ainda não conhecidos,
 * preservando a ordem já guardada pelo utilizador e sem duplicar. É a base da
 * migração SAPO; isolada como função pura para ser testável sem corrotinas.
 */
internal fun mergeNewAllowedIds(current: List<String>, fetched: List<String>): List<String> {
    val newIds = fetched.filter { it !in current }.distinct()
    return if (newIds.isEmpty()) current else current + newIds
}
