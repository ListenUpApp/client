package com.calypsan.listenup.client

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform