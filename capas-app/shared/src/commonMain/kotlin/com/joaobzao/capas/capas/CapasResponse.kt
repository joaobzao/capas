package com.joaobzao.capas.capas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CapasResponse(
    @SerialName("Jornais Nacionais")
    val mainNewspapers: List<Capa>,
    @SerialName("Desporto")
    val sportNewspapers: List<Capa>,
    @SerialName("Economia e Gestão")
    val economyNewspapers: List<Capa>,
    @SerialName("Regionais")
    val regionalNewspapers: List<Capa> = emptyList()
)

@Serializable
data class Capa(
    val id: String,
    val nome: String,
    val url: String,
    val news: List<NewsItem>? = null
)

@Serializable
data class NewsItem(
    val headline: String,
    val summary: String? = null,
    val category: String? = null
)