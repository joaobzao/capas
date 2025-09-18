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
    val economyNewspapers: List<Capa>
)

@Serializable
data class Capa(
    val id: String,
    val nome: String,
    val url: String
)