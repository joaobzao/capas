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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeAnnouncementApi : Api {
    override suspend fun fetchCapas(): Flow<NetworkResult<CapasResponse>> =
        flowOf(NetworkResult.Success(CapasResponse(emptyList(), emptyList(), emptyList())))

    override suspend fun fetchWorkflowStatus(): Flow<NetworkResult<GitHubWorkflowResponse>> =
        flowOf(NetworkResult.Success(GitHubWorkflowResponse(emptyList())))
}

class CapasRepositoryAnnouncementTest {

    private fun repo(settings: Settings) = CapasRepositoryImpl(FakeAnnouncementApi(), settings)

    @Test
    fun internationalAnnouncement_defaultsToUnseen() {
        assertFalse(repo(MapSettings()).isInternationalAnnouncementSeen())
    }

    @Test
    fun setInternationalAnnouncementSeen_marksAndPersists() {
        val settings = MapSettings()
        repo(settings).setInternationalAnnouncementSeen()

        assertTrue(repo(settings).isInternationalAnnouncementSeen())
    }

    @Test
    fun completingOnboarding_alsoMarksAnnouncementSeen() {
        // New users meet international covers as part of the app, so finishing
        // onboarding must suppress the "what's new" announcement for them.
        val repo = repo(MapSettings())
        assertFalse(repo.isInternationalAnnouncementSeen())

        repo.setOnboardingCompleted()

        assertTrue(repo.isInternationalAnnouncementSeen())
    }
}
