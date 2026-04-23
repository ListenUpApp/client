package com.calypsan.listenup.client.data.sync.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Payload for the `CREATE_SHELF` pending operation. */
@Serializable
data class CreateShelfPayload(
    @SerialName("local_id")
    val localId: String,
    val name: String,
    val description: String?,
)

/** Payload for the `UPDATE_SHELF` pending operation. */
@Serializable
data class UpdateShelfPayload(
    @SerialName("shelf_id")
    val shelfId: String,
    val name: String,
    val description: String?,
)

/** Payload for the `DELETE_SHELF` pending operation. */
@Serializable
data class DeleteShelfPayload(
    @SerialName("shelf_id")
    val shelfId: String,
)

/** Payload for the `ADD_BOOKS_TO_SHELF` pending operation. */
@Serializable
data class AddBooksToShelfPayload(
    @SerialName("shelf_id")
    val shelfId: String,
    @SerialName("book_ids")
    val bookIds: List<String>,
)

/** Payload for the `REMOVE_BOOK_FROM_SHELF` pending operation. */
@Serializable
data class RemoveBookFromShelfPayload(
    @SerialName("shelf_id")
    val shelfId: String,
    @SerialName("book_id")
    val bookId: String,
)
