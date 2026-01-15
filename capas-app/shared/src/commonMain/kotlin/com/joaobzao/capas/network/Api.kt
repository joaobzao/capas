package com.joaobzao.capas.network

import com.joaobzao.capas.capas.CapasResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface Api {
    suspend fun fetchCapas(): Flow<NetworkResult<CapasResponse>>
    suspend fun fetchWorkflowStatus(): Flow<NetworkResult<GitHubWorkflowResponse>>
    suspend fun fetchFilters(): Flow<NetworkResult<List<String>>>
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

    override suspend fun fetchFilters(): Flow<NetworkResult<List<String>>> {
        val networkResult = apiCall {
            httpClient.get("${environment.host}/capas/filters.json").body<List<String>>()
        }
        return flowOf(networkResult)
    }

    override suspend fun fetchWorkflowStatus(): Flow<NetworkResult<GitHubWorkflowResponse>> {
        val networkResult = apiCall {
            httpClient.get("https://api.github.com/repos/joaobzao/capas/actions/workflows/update-capas.yml/runs?per_page=1").body<GitHubWorkflowResponse>()
        }
        return flowOf(networkResult)
    }
}