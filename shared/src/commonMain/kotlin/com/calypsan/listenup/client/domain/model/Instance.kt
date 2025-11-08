package com.calypsan.listenup.client.domain.model

import kotlinx.datetime.Instant
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
 */
@Serializable
data class Instance(
    @SerialName("id")
    val id: InstanceId,

    @SerialName("has_root_user")
    val hasRootUser: Boolean,

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
        get() = !hasRootUser

    /**
     * True if the instance is ready for use (has root user configured).
     */
    val isReady: Boolean
        get() = hasRootUser
}
