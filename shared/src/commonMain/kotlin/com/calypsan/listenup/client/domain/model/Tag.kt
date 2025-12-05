package com.calypsan.listenup.client.domain.model

/**
 * Domain model for a user tag.
 *
 * Tags are personal to each user and used for organizing books.
 * Examples: "beach read", "book club 2024", "gift for mom"
 *
 * Tags have optional colors for visual customization in the UI.
 */
data class Tag(
    val id: String,
    val name: String,
    val slug: String,
    val color: String? = null,
    val bookCount: Int = 0
)
