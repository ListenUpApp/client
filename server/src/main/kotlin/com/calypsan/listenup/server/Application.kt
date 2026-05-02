package com.calypsan.listenup.server

import com.calypsan.listenup.server.routes.healthRoutes
import com.calypsan.listenup.server.routes.instanceRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.cio.EngineMain
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.resources.Resources
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import kotlinx.rpc.krpc.ktor.server.Krpc
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    install(ContentNegotiation) { json() }
    install(StatusPages)
    install(CallLogging)
    install(Authentication)
    install(Resources)
    install(SSE)
    install(Krpc)
    install(Koin) { modules(module { }) }

    routing {
        healthRoutes()
        instanceRoutes()
    }
}
