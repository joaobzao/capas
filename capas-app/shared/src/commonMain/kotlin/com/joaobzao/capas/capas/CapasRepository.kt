package com.joaobzao.capas.capas

import com.joaobzao.capas.network.Api
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
}

class CapasRepositoryImpl(
    private val api: Api,
    private val settings: Settings
) : CapasRepository {

    private val KEY = "allowed_capas"
    private var lastCapas: CapasResponse? = null

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
                            capas.economyNewspapers.map { it.id }
                    setAllowedIds(allIds.toSet())
                }

                val currentAllowed = getAllowedIds()
                val filtered = capas.copy(
                    mainNewspapers = capas.mainNewspapers.filter { it.id in currentAllowed },
                    sportNewspapers = capas.sportNewspapers.filter { it.id in currentAllowed },
                    economyNewspapers = capas.economyNewspapers.filter { it.id in currentAllowed }
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

    override fun getRemovedCapas(): List<Capa> {
        val allowed = getAllowedIds()
        val capas = lastCapas ?: return emptyList()
        return (capas.mainNewspapers + capas.sportNewspapers + capas.economyNewspapers)
            .filterNot { it.id in allowed }
    }

    private fun getAllowedIds(): Set<String> {
        val stored = settings.getString(KEY, "")
        return if (stored.isNotEmpty()) stored.split(",").toSet() else emptySet()
    }

    private fun setAllowedIds(ids: Set<String>) {
        settings[KEY] = ids.joinToString(",")
    }
}
