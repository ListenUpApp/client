package com.calypsan.listenup.client.core

import com.calypsan.listenup.client.data.sync.SSEEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * The canonical [Json] instance for the entire app. All [kotlinx.serialization] encoding and
 * decoding against wire payloads (HTTP, SSE, persisted operation payloads, secure storage)
 * goes through this instance — see Finding 04 D5.
 *
 * Settings:
 * - `ignoreUnknownKeys = true` — forward-compatible with server additions.
 * - `isLenient = true` — tolerates minor wire-format variance (mixed quote styles,
 *   numeric booleans) that streaming payloads occasionally produce.
 * - `prettyPrint = false` — minimize over-the-wire bytes.
 * - `serializersModule` — registers [SSEEvent.Unknown] as the default deserializer for
 *   unknown polymorphic discriminators so the SSE decode path never throws on an
 *   unenumerated `type` string (forward-compatible with new server event types).
 *
 * Consumers that need a [Json] instance should inject `get<Json>()` (via Koin) or
 * reference this value directly. File-local `Json { ... }` blocks are forbidden by the
 * rubric rule derived from Finding 04 D5.
 */
val appJson: Json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
        serializersModule =
            SerializersModule {
                polymorphic(SSEEvent::class) {
                    defaultDeserializer { SSEEvent.Unknown.serializer() }
                }
            }
    }
