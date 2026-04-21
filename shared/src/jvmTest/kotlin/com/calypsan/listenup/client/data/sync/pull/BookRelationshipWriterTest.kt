package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.ContributorId
import com.calypsan.listenup.client.core.SeriesId
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.data.local.db.AudioFileEntity
import com.calypsan.listenup.client.data.local.db.BookContributorCrossRef
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookGenreCrossRef
import com.calypsan.listenup.client.data.local.db.BookSeriesCrossRef
import com.calypsan.listenup.client.data.local.db.BookTagCrossRef
import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.GenreEntity
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.data.local.db.SeriesEntity
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.TagEntity
import com.calypsan.listenup.client.test.db.createInMemoryTestDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookRelationshipWriterTest {
    private val db: ListenUpDatabase = createInMemoryTestDatabase()
    private val writer: BookRelationshipWriter =
        BookRelationshipWriter(
            bookContributorDao = db.bookContributorDao(),
            bookSeriesDao = db.bookSeriesDao(),
            tagDao = db.tagDao(),
            genreDao = db.genreDao(),
            audioFileDao = db.audioFileDao(),
        )

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `replaceAll with empty bundles is a no-op`() =
        runTest {
            writer.replaceAll(emptyList(), emptyList())
            // No exception thrown; nothing written
            assertEquals(0, db.bookDao().count())
        }

    @Test
    fun `replaceAll persists a single-book bundle across all five junction tables`() =
        runTest {
            val bookId = BookId("book-1")
            val contributorId = ContributorId("contrib-1")
            val seriesId = SeriesId("series-1")
            val genreId = "genre-1"
            val tagId = "tag-1"
            val now = Timestamp(1_000_000L)

            // Seed the book (required by junction FK constraints)
            db.bookDao().upsertAll(listOf(makeBook(bookId, now)))

            // Seed FK parents: contributor, series, genre
            db.contributorDao().upsertAll(listOf(makeContributor(contributorId, now)))
            db.seriesDao().upsertAll(listOf(makeSeries(seriesId, now)))
            db.genreDao().upsertAll(listOf(makeGenre(genreId)))
            // Tag catalog is passed via tagCatalog param, seeded by writer

            val bundle =
                BookRelationshipBundle(
                    bookId = bookId,
                    contributors = listOf(BookContributorCrossRef(bookId, contributorId, "author")),
                    series = listOf(BookSeriesCrossRef(bookId, seriesId, "1")),
                    tags = listOf(BookTagCrossRef(bookId, tagId)),
                    genres = listOf(BookGenreCrossRef(bookId, genreId)),
                    audioFiles =
                        listOf(
                            AudioFileEntity(
                                bookId = bookId,
                                index = 0,
                                id = "af-1",
                                filename = "ch1.m4b",
                                format = "m4b",
                                codec = "aac",
                                duration = 3_600_000L,
                                size = 45_000_000L,
                            ),
                        ),
                )
            val tagCatalog = listOf(TagEntity(id = tagId, slug = "fantasy", createdAt = now))

            writer.replaceAll(listOf(bundle), tagCatalog)

            assertEquals(
                1,
                db.bookContributorDao().getByContributorId(contributorId.value).size,
                "contributor cross-ref",
            )
            assertEquals(1, db.bookSeriesDao().getSeriesForBook(bookId).size, "series cross-ref")
            assertEquals(1, db.tagDao().getTagsForBook(bookId).size, "tag cross-ref")
            assertEquals(1, db.genreDao().getGenresForBook(bookId).size, "genre cross-ref")
            assertEquals(1, db.audioFileDao().getForBook(bookId.value).size, "audio files")
        }

    @Test
    fun `replaceAll handles multi-book bundles with heterogeneous relationships`() =
        runTest {
            val bookAId = BookId("book-a")
            val bookBId = BookId("book-b")
            val contributorId = ContributorId("contrib-a")
            val now = Timestamp(2_000_000L)

            db.bookDao().upsertAll(listOf(makeBook(bookAId, now), makeBook(bookBId, now)))
            db.contributorDao().upsertAll(listOf(makeContributor(contributorId, now)))

            val bundleA =
                BookRelationshipBundle(
                    bookId = bookAId,
                    contributors = listOf(BookContributorCrossRef(bookAId, contributorId, "author")),
                    series = emptyList(),
                    tags = emptyList(),
                    genres = emptyList(),
                    audioFiles = emptyList(),
                )
            val bundleB =
                BookRelationshipBundle(
                    bookId = bookBId,
                    contributors = emptyList(),
                    series = emptyList(),
                    tags = emptyList(),
                    genres = emptyList(),
                    audioFiles =
                        listOf(
                            AudioFileEntity(
                                bookId = bookBId,
                                index = 0,
                                id = "af-b-1",
                                filename = "ch1.mp3",
                                format = "mp3",
                                codec = "mp3",
                                duration = 1_800_000L,
                                size = 20_000_000L,
                            ),
                        ),
                )

            writer.replaceAll(listOf(bundleA, bundleB), emptyList())

            // book-a has 1 contributor, 0 audio files
            assertEquals(
                1,
                db.bookContributorDao().getByContributorId(contributorId.value).size,
                "book-a should have the contributor",
            )
            assertEquals(
                0,
                db.audioFileDao().getForBook(bookAId.value).size,
                "book-a should have no audio files",
            )

            // book-b has 0 contributors, 1 audio file
            assertEquals(
                0,
                db.bookSeriesDao().getSeriesForBook(bookBId).size,
                "book-b should have no series",
            )
            assertEquals(
                1,
                db.audioFileDao().getForBook(bookBId.value).size,
                "book-b should have 1 audio file",
            )
        }

    @Test
    fun `replaceAll replaces existing relationships — old rows gone, new rows present`() =
        runTest {
            val bookId = BookId("book-replace")
            val oldContrib1 = ContributorId("contrib-old-1")
            val oldContrib2 = ContributorId("contrib-old-2")
            val oldContrib3 = ContributorId("contrib-old-3")
            val newContrib = ContributorId("contrib-new")
            val now = Timestamp(3_000_000L)

            db.bookDao().upsertAll(listOf(makeBook(bookId, now)))
            db
                .contributorDao()
                .upsertAll(
                    listOf(
                        makeContributor(oldContrib1, now),
                        makeContributor(oldContrib2, now),
                        makeContributor(oldContrib3, now),
                        makeContributor(newContrib, now),
                    ),
                )

            // Pre-seed 3 old contributor rows
            db
                .bookContributorDao()
                .insertAll(
                    listOf(
                        BookContributorCrossRef(bookId, oldContrib1, "author"),
                        BookContributorCrossRef(bookId, oldContrib2, "narrator"),
                        BookContributorCrossRef(bookId, oldContrib3, "editor"),
                    ),
                )
            assertEquals(
                3,
                db.bookContributorDao().getByContributorId(oldContrib1.value).size +
                    db.bookContributorDao().getByContributorId(oldContrib2.value).size +
                    db.bookContributorDao().getByContributorId(oldContrib3.value).size,
                "pre-condition: 3 old rows",
            )

            val bundle =
                BookRelationshipBundle(
                    bookId = bookId,
                    contributors = listOf(BookContributorCrossRef(bookId, newContrib, "author")),
                    series = emptyList(),
                    tags = emptyList(),
                    genres = emptyList(),
                    audioFiles = emptyList(),
                )

            writer.replaceAll(listOf(bundle), emptyList())

            // Old rows gone
            assertEquals(
                0,
                db.bookContributorDao().getByContributorId(oldContrib1.value).size,
                "old contrib-1 row gone",
            )
            assertEquals(
                0,
                db.bookContributorDao().getByContributorId(oldContrib2.value).size,
                "old contrib-2 row gone",
            )
            assertEquals(
                0,
                db.bookContributorDao().getByContributorId(oldContrib3.value).size,
                "old contrib-3 row gone",
            )
            // New row present
            assertEquals(
                1,
                db.bookContributorDao().getByContributorId(newContrib.value).size,
                "new contributor row present",
            )
        }

    @Test
    fun `replaceAll upserts tag catalog before inserting junctions (foreign-key ordering)`() =
        runTest {
            val bookId = BookId("book-tag-fk")
            val newTagId = "tag-new"
            val now = Timestamp(4_000_000L)

            db.bookDao().upsertAll(listOf(makeBook(bookId, now)))

            val bundle =
                BookRelationshipBundle(
                    bookId = bookId,
                    contributors = emptyList(),
                    series = emptyList(),
                    tags = listOf(BookTagCrossRef(bookId, newTagId)),
                    genres = emptyList(),
                    audioFiles = emptyList(),
                )
            val tagCatalog =
                listOf(
                    TagEntity(id = newTagId, slug = "tag-new", createdAt = now),
                )

            // If catalog upsert ran AFTER junction insert, this would fail with an FK violation
            writer.replaceAll(listOf(bundle), tagCatalog)

            assertEquals(
                1,
                db.tagDao().getTagsForBook(bookId).size,
                "tag junction row written with FK satisfied",
            )
            assertTrue(
                db.tagDao().getById(newTagId) != null,
                "tag catalog entry persisted",
            )
        }

    // ── Fixture helpers ──────────────────────────────────────────────────────

    private fun makeBook(
        bookId: BookId,
        now: Timestamp,
    ) = BookEntity(
        id = bookId,
        title = "Test Book ${bookId.value}",
        coverUrl = null,
        totalDuration = 3_600_000L,
        syncState = SyncState.SYNCED,
        lastModified = now,
        serverVersion = now,
        createdAt = now,
        updatedAt = now,
    )

    private fun makeContributor(
        id: ContributorId,
        now: Timestamp,
    ) = ContributorEntity(
        id = id,
        name = "Contributor ${id.value}",
        description = null,
        imagePath = null,
        syncState = SyncState.SYNCED,
        lastModified = now,
        serverVersion = now,
        createdAt = now,
        updatedAt = now,
    )

    private fun makeSeries(
        id: SeriesId,
        now: Timestamp,
    ) = SeriesEntity(
        id = id,
        name = "Series ${id.value}",
        description = null,
        syncState = SyncState.SYNCED,
        lastModified = now,
        serverVersion = now,
        createdAt = now,
        updatedAt = now,
    )

    private fun makeGenre(id: String) =
        GenreEntity(
            id = id,
            name = "Genre $id",
            slug = id,
            path = "/$id",
        )
}
