package com.calypsan.listenup.server

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.readUTF8Line

class PluginSmokeTest : FunSpec({
    test("StatusPages returns a structured JSON 404 for unknown paths") {
        testApplication {
            application { module() }

            val response = client.get("/this-path-does-not-exist")

            response.status shouldBe HttpStatusCode.NotFound
            response.headers["Content-Type"]?.let {
                ContentType.parse(it).match(ContentType.Application.Json) shouldBe true
            }
            response.bodyAsText() shouldContain "\"error\""
        }
    }

    test("SSE plugin emits events on CIO and client receives them") {
        testApplication {
            application { module() }

            val response = client.get("/sse/ping")

            response.status shouldBe HttpStatusCode.OK
            response.headers["Content-Type"]?.startsWith("text/event-stream") shouldBe true

            val channel = response.bodyAsChannel()
            var dataLine: String? = null
            while (dataLine == null) {
                val line = channel.readUTF8Line() ?: break
                if (line.startsWith("data:")) dataLine = line
            }
            dataLine shouldContain "pong"
        }
    }
})
