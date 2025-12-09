package com.joaobzao.capas.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubWorkflowResponse(
    @SerialName("workflow_runs")
    val workflowRuns: List<GitHubWorkflowRun>
)

@Serializable
data class GitHubWorkflowRun(
    val status: String,
    val conclusion: String?,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("html_url")
    val htmlUrl: String
)
