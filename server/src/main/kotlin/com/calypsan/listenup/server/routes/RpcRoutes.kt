package com.calypsan.listenup.server.routes

import com.calypsan.listenup.api.PingService
import io.ktor.server.routing.Route
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.registerService

private class PingServiceImpl : PingService {
    override suspend fun ping(): String = "pong"
}

fun Route.rpcRoutes() {
    rpc("/api/rpc") {
        rpcConfig { serialization { json() } }
        registerService<PingService> { PingServiceImpl() }
    }
}
