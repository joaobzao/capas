package com.joaobzao.capas.network

enum class Environments(
    val host: String,
    val certificatePinningHashes: List<String> = emptyList()
) {
    PROD(
        host = "https://joaobzao.github.io",
    )
}
