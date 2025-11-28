package com.calypsan.listenup.client.domain.model

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Type-safe wrapper for Instance IDs.
 * Value class provides zero runtime overhead while maintaining compile-time type safety.
 *
 * This prevents accidentally passing wrong string types (e.g., user IDs, book IDs) where
 * an instance ID is expected.
 *
 * Note: @JvmInline is required even in commonMain for KMP value classes
 */
@JvmInline
@Serializable
value class InstanceId(val value: String) {
    init {
        require(value.isNotBlank()) { "Instance ID cannot be blank" }
    }

    override fun toString(): String = value
}

/**
 * Represents a ListenUp audiobook server instance.
 *
 * Each server installation is an "instance" with its own configuration,
 * users, and content library.
 *
 * Timestamp fields use kotlin.time.Instant (Kotlin stdlib) which has native
 * kotlinx.serialization support for ISO-8601 format (e.g., "2024-11-20T14:30:45.123Z").
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class Instance(
    @SerialName("id")
    val id: InstanceId,

    @SerialName("name")
    val name: String,

    @SerialName("version")
    val version: String,

    @SerialName("local_url")
    val localUrl: String? = null,

    @SerialName("remote_url")
    val remoteUrl: String? = null,

    @SerialName("setup_required")
    val setupRequired: Boolean,

    @SerialName("created_at")
    val createdAt: Instant,

    @SerialName("updated_at")
    val updatedAt: Instant
) {
    /**
     * True if the instance needs initial setup (no root user configured yet).
     *
     * When a server is first installed, it has no users and requires
     * creating the initial admin/root user before it can be used.
     */
    val needsSetup: Boolean
        get() = setupRequired

    /**
     * True if the instance is ready for use (has root user configured).
     */
    val isReady: Boolean
        get() = !setupRequired

    /**
     * Compatibility property for has_root_user field.
     * Returns true if a root user has been configured.
     */
    val hasRootUser: Boolean
        get() = !setupRequired
}
