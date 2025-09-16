package com.joaobzao.capas.capas

import com.joaobzao.capas.network.Api
import com.joaobzao.capas.network.NetworkResult
import kotlinx.coroutines.flow.Flow

interface CapasRepository {
    suspend fun getCapas(): Flow<NetworkResult<CapasResponse>>
}

class CapasRepositoryImpl(
    private val api: Api
): CapasRepository {
    override suspend fun getCapas(): Flow<NetworkResult<CapasResponse>> = api.fetchCapas()

}