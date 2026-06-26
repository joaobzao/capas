package com.joaobzao.capas

import com.joaobzao.capas.capas.CapasRepositoryImpl
import com.joaobzao.capas.capas.CapasResponse
import com.joaobzao.capas.network.Api
import com.joaobzao.capas.network.GitHubWorkflowResponse
import com.joaobzao.capas.network.NetworkResult
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeApi : Api {
    override suspend fun fetchCapas(): Flow<NetworkResult<CapasResponse>> =
        flowOf(NetworkResult.Success(CapasResponse(emptyList(), emptyList(), emptyList())))

    override suspend fun fetchWorkflowStatus(): Flow<NetworkResult<GitHubWorkflowResponse>> =
        flowOf(NetworkResult.Success(GitHubWorkflowResponse(emptyList())))
}

class CapasRepositoryFavoritesTest {

    private fun repo(settings: Settings) = CapasRepositoryImpl(FakeApi(), settings)

    @Test
    fun toggleFavorite_addsAndRemoves() {
        val repo = repo(MapSettings())

        assertFalse(repo.isFavorite("publico"))

        repo.toggleFavorite("publico")
        assertTrue(repo.isFavorite("publico"))
        assertEquals(listOf("publico"), repo.getFavoriteIds())

        repo.toggleFavorite("publico")
        assertFalse(repo.isFavorite("publico"))
        assertEquals(emptyList(), repo.getFavoriteIds())
    }

    @Test
    fun favoriteIds_preserveInsertionOrder() {
        val repo = repo(MapSettings())

        repo.toggleFavorite("record")
        repo.toggleFavorite("a-bola")
        repo.toggleFavorite("publico")

        assertEquals(listOf("record", "a-bola", "publico"), repo.getFavoriteIds())
    }

    @Test
    fun updateFavoriteOrder_reordersStoredIds() {
        val repo = repo(MapSettings())
        repo.toggleFavorite("a-bola")
        repo.toggleFavorite("record")
        repo.toggleFavorite("publico")

        repo.updateFavoriteOrder(listOf("publico", "a-bola", "record"))

        assertEquals(listOf("publico", "a-bola", "record"), repo.getFavoriteIds())
    }

    @Test
    fun favorites_persistAcrossRepositoryInstances() {
        val settings = MapSettings()
        repo(settings).toggleFavorite("publico")

        // A fresh repository reading the same settings still sees the favorite.
        assertTrue(repo(settings).isFavorite("publico"))
    }

    @Test
    fun getFavoriteCapas_isEmptyBeforeAnyFetch() {
        val repo = repo(MapSettings())
        repo.toggleFavorite("publico")

        // No covers loaded yet, so there is nothing to resolve the id against.
        assertEquals(emptyList(), repo.getFavoriteCapas())
    }
}
