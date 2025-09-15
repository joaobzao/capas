package com.joaobzao.capas

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform