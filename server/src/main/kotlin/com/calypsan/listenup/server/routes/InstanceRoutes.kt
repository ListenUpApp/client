package com.calypsan.listenup.server.routes

import com.calypsan.listenup.api.dto.ServerInfo
import io.ktor.resources.Resource
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

@Resource("/api/v1/instance")
class InstanceResource

fun Route.instanceRoutes() {
    get<InstanceResource> {
        call.respond(
            ServerInfo(
                name = "ListenUp",
                version = "0.0.1",
                apiVersion = "v1",
            ),
        )
    }
}
