package com.joaobzao.capas.network

import com.joaobzao.capas.capas.CapasResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface Api {
    suspend fun fetchCapas(): Flow<NetworkResult<CapasResponse>>
}

class ApiImpl(
    private val httpClient: HttpClient,
    private val environment: Environments
): Api {
    override suspend fun fetchCapas(): Flow<NetworkResult<CapasResponse>> {
        val networkResult = apiCall {
            httpClient.get("${environment.host}/capas/capas.json").body<CapasResponse>()
        }
        return flowOf(networkResult)
    }
}