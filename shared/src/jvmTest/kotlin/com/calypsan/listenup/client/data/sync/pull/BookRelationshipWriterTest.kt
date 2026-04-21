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
    fun `replaceAll with empty bundles does not mutate pre-existing junction rows`() =
        runTest {
            val bookId = BookId("book-noop")
            val contributorId = ContributorId("contrib-noop")
            val seriesId = SeriesId("series-noop")
            val now = Timestamp(500_000L)

            // Seed parent rows
            db.bookDao().upsertAll(listOf(makeBook(bookId, now)))
            db.contributorDao().upsertAll(listOf(makeContributor(contributorId, now)))
            db.seriesDao().upsertAll(listOf(makeSeries(seriesId, now)))

            // Seed one row in contributors and audio_files junction tables
            db.bookContributorDao().insertAll(
                listOf(BookContributorCrossRef(bookId, contributorId, "author")),
            )
            db.audioFileDao().upsertAll(
                listOf(
                    AudioFileEntity(
                        bookId = bookId,
                        index = 0,
                        id = "af-noop-1",
                        filename = "ch1.m4b",
                        format = "m4b",
                        codec = "aac",
                        duration = 1_000L,
                        size = 1_000L,
                    ),
                ),
            )

            // Call with empty bundles — writer must not touch existing rows
            writer.replaceAll(emptyList(), emptyList())

            assertEquals(
                1,
                db.bookContributorDao().getByContributorId(contributorId.value).size,
                "pre-seeded contributor row must still exist after empty-bundle call",
            )
            assertEquals(
                1,
                db.audioFileDao().getForBook(bookId.value).size,
                "pre-seeded audio-file row must still exist after empty-bundle call",
            )
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
    fun `replaceAll replaces existing relationships — old rows gone, new rows present in all 5 tables`() =
        runTest {
            val bookId = BookId("book-replace")
            val now = Timestamp(3_000_000L)

            // Parents for OLD rows
            val oldContrib = ContributorId("contrib-old")
            val oldSeries = SeriesId("series-old")
            val oldTagId = "tag-old"
            val oldGenreId = "genre-old"
            val oldAudioFileId = "af-old"

            // Parents for NEW rows
            val newContrib = ContributorId("contrib-new")
            val newSeries = SeriesId("series-new")
            val newTagId = "tag-new"
            val newGenreId = "genre-new"
            val newAudioFileId = "af-new"

            // Seed book
            db.bookDao().upsertAll(listOf(makeBook(bookId, now)))

            // Seed FK parents for OLD rows
            db.contributorDao().upsertAll(listOf(makeContributor(oldContrib, now), makeContributor(newContrib, now)))
            db.seriesDao().upsertAll(listOf(makeSeries(oldSeries, now), makeSeries(newSeries, now)))
            db.genreDao().upsertAll(listOf(makeGenre(oldGenreId), makeGenre(newGenreId)))
            // Tag catalog for old tag seeded directly; new tag provided via tagCatalog param
            db.tagDao().upsertAll(listOf(TagEntity(id = oldTagId, slug = "old-tag", createdAt = now)))

            // Pre-seed OLD junction rows across all 5 tables
            db.bookContributorDao().insertAll(listOf(BookContributorCrossRef(bookId, oldContrib, "author")))
            db.bookSeriesDao().insertAll(listOf(BookSeriesCrossRef(bookId, oldSeries, "1")))
            db.tagDao().insertAllBookTags(listOf(BookTagCrossRef(bookId, oldTagId)))
            db.genreDao().insertAllBookGenres(listOf(BookGenreCrossRef(bookId, oldGenreId)))
            db.audioFileDao().upsertAll(
                listOf(
                    AudioFileEntity(
                        bookId = bookId,
                        index = 0,
                        id = oldAudioFileId,
                        filename = "old.m4b",
                        format = "m4b",
                        codec = "aac",
                        duration = 1_000L,
                        size = 1_000L,
                    ),
                ),
            )

            // Verify pre-condition: 1 row in each of the 5 tables
            assertEquals(1, db.bookContributorDao().getByContributorId(oldContrib.value).size, "pre: contributor")
            assertEquals(1, db.bookSeriesDao().getSeriesForBook(bookId).size, "pre: series")
            assertEquals(1, db.tagDao().getTagsForBook(bookId).size, "pre: tags")
            assertEquals(1, db.genreDao().getGenresForBook(bookId).size, "pre: genres")
            assertEquals(1, db.audioFileDao().getForBook(bookId.value).size, "pre: audio files")

            // Bundle carries NEW rows for all 5 tables
            val bundle =
                BookRelationshipBundle(
                    bookId = bookId,
                    contributors = listOf(BookContributorCrossRef(bookId, newContrib, "narrator")),
                    series = listOf(BookSeriesCrossRef(bookId, newSeries, "2")),
                    tags = listOf(BookTagCrossRef(bookId, newTagId)),
                    genres = listOf(BookGenreCrossRef(bookId, newGenreId)),
                    audioFiles =
                        listOf(
                            AudioFileEntity(
                                bookId = bookId,
                                index = 0,
                                id = newAudioFileId,
                                filename = "new.m4b",
                                format = "m4b",
                                codec = "aac",
                                duration = 2_000L,
                                size = 2_000L,
                            ),
                        ),
                )
            val tagCatalog = listOf(TagEntity(id = newTagId, slug = "new-tag", createdAt = now))

            writer.replaceAll(listOf(bundle), tagCatalog)

            // Old rows gone
            assertEquals(0, db.bookContributorDao().getByContributorId(oldContrib.value).size, "old contributor gone")
            assertEquals(0, db.bookSeriesDao().getBookSeriesCrossRefs(bookId).count { it.seriesId == oldSeries }, "old series gone")
            assertEquals(0, db.tagDao().getTagsForBook(bookId).count { it.id == oldTagId }, "old tag gone")
            assertEquals(0, db.genreDao().getGenresForBook(bookId).count { it.id == oldGenreId }, "old genre gone")
            assertEquals(0, db.audioFileDao().getForBook(bookId.value).count { it.id == oldAudioFileId }, "old audio file gone")

            // New rows present
            assertEquals(1, db.bookContributorDao().getByContributorId(newContrib.value).size, "new contributor present")
            assertEquals(1, db.bookSeriesDao().getBookSeriesCrossRefs(bookId).count { it.seriesId == newSeries }, "new series present")
            assertEquals(1, db.tagDao().getTagsForBook(bookId).count { it.id == newTagId }, "new tag present")
            assertEquals(1, db.genreDao().getGenresForBook(bookId).count { it.id == newGenreId }, "new genre present")
            assertEquals(1, db.audioFileDao().getForBook(bookId.value).count { it.id == newAudioFileId }, "new audio file present")
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
