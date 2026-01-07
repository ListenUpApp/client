package com.calypsan.listenup.client.domain.usecase.contributor

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.model.Contributor
import com.calypsan.listenup.client.domain.model.ContributorMetadataResult
import com.calypsan.listenup.client.domain.model.ContributorWithMetadata
import com.calypsan.listenup.client.domain.repository.ContributorRepository
import com.calypsan.listenup.client.domain.repository.ImageRepository
import com.calypsan.listenup.client.domain.repository.MetadataRepository
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ApplyContributorMetadataUseCaseTest {
    // ========== Test Fixture ==========

    private class TestFixture {
        val metadataRepository: MetadataRepository = mock()
        val imageRepository: ImageRepository = mock()
        val contributorRepository: ContributorRepository = mock()

        fun build(): ApplyContributorMetadataUseCase =
            ApplyContributorMetadataUseCase(
                metadataRepository = metadataRepository,
                imageRepository = imageRepository,
                contributorRepository = contributorRepository,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs
        everySuspend { fixture.contributorRepository.upsertContributor(any()) } returns Unit

        return fixture
    }

    // ========== Test Data Factories ==========

    private fun createRequest(
        contributorId: String = "contributor-123",
        asin: String = "B001ABC123",
        imageUrl: String? = "https://audible.com/image.jpg",
        name: Boolean = true,
        biography: Boolean = true,
        image: Boolean = true,
    ): ApplyContributorMetadataRequest =
        ApplyContributorMetadataRequest(
            contributorId = contributorId,
            asin = asin,
            imageUrl = imageUrl,
            selections = MetadataFieldSelections(
                name = name,
                biography = biography,
                image = image,
            ),
        )

    private fun createContributorWithMetadata(
        id: String = "contributor-123",
        name: String = "Updated Author",
        biography: String? = "Updated biography",
        imageUrl: String? = "https://server.com/contributor-image.jpg",
        imageBlurHash: String? = null,
    ): ContributorWithMetadata =
        ContributorWithMetadata(
            id = id,
            name = name,
            biography = biography,
            imageUrl = imageUrl,
            imageBlurHash = imageBlurHash,
        )

    // ========== Validation Tests ==========

    @Test
    fun `returns failure when no fields selected`() = runTest {
        // Given
        val fixture = createFixture()
        val useCase = fixture.build()
        val request = createRequest(name = false, biography = false, image = false)

        // When
        val result = useCase(request)

        // Then
        assertIs<Failure>(result)
        assertEquals("Please select at least one field to apply", result.message)
    }

    // ========== Success Tests ==========

    @Test
    fun `applies metadata successfully`() = runTest {
        // Given
        val fixture = createFixture()
        val contributorData = createContributorWithMetadata(imageUrl = null)
        everySuspend {
            fixture.metadataRepository.applyContributorMetadata(any(), any(), any(), any(), any(), any())
        } returns ContributorMetadataResult.Success(contributor = contributorData)
        val useCase = fixture.build()

        // When
        val result = useCase(createRequest())

        // Then
        assertIs<Success<Contributor>>(result)
        assertEquals("contributor-123", result.data.id)
        assertEquals("Updated Author", result.data.name)
    }

    @Test
    fun `passes correct parameters to repository`() = runTest {
        // Given
        val fixture = createFixture()
        val contributorData = createContributorWithMetadata(imageUrl = null)
        everySuspend {
            fixture.metadataRepository.applyContributorMetadata(any(), any(), any(), any(), any(), any())
        } returns ContributorMetadataResult.Success(contributor = contributorData)
        val useCase = fixture.build()

        val request = createRequest(
            contributorId = "my-contributor",
            asin = "MY-ASIN",
            imageUrl = "https://example.com/img.jpg",
            name = true,
            biography = false,
            image = true,
        )

        // When
        useCase(request)

        // Then
        verifySuspend {
            fixture.metadataRepository.applyContributorMetadata(
                contributorId = "my-contributor",
                asin = "MY-ASIN",
                imageUrl = "https://example.com/img.jpg",
                applyName = true,
                applyBiography = false,
                applyImage = true,
            )
        }
    }

    @Test
    fun `upserts entity to repository`() = runTest {
        // Given
        val fixture = createFixture()
        val contributorData = createContributorWithMetadata(imageUrl = null)
        everySuspend {
            fixture.metadataRepository.applyContributorMetadata(any(), any(), any(), any(), any(), any())
        } returns ContributorMetadataResult.Success(contributor = contributorData)
        val useCase = fixture.build()

        // When
        useCase(createRequest())

        // Then
        verifySuspend {
            fixture.contributorRepository.upsertContributor(any())
        }
    }

    // ========== Image Download Tests ==========

    @Test
    fun `downloads and saves image when imageUrl present`() = runTest {
        // Given
        val fixture = createFixture()
        val contributorData = createContributorWithMetadata(imageUrl = "https://server.com/image.jpg")
        val imageData = byteArrayOf(1, 2, 3, 4)

        everySuspend {
            fixture.metadataRepository.applyContributorMetadata(any(), any(), any(), any(), any(), any())
        } returns ContributorMetadataResult.Success(contributor = contributorData)
        everySuspend {
            fixture.imageRepository.downloadContributorImage("contributor-123")
        } returns Success(imageData)
        everySuspend {
            fixture.imageRepository.saveContributorImage(any(), any())
        } returns Success(Unit)
        everySuspend {
            fixture.imageRepository.getContributorImagePath("contributor-123")
        } returns "/images/contributor-123.jpg"

        val useCase = fixture.build()

        // When
        val result = useCase(createRequest())

        // Then
        assertIs<Success<Contributor>>(result)
        assertEquals("/images/contributor-123.jpg", result.data.imagePath)
    }

    @Test
    fun `skips image download when imageUrl is null`() = runTest {
        // Given
        val fixture = createFixture()
        val contributorData = createContributorWithMetadata(imageUrl = null)
        everySuspend {
            fixture.metadataRepository.applyContributorMetadata(any(), any(), any(), any(), any(), any())
        } returns ContributorMetadataResult.Success(contributor = contributorData)
        val useCase = fixture.build()

        // When
        useCase(createRequest())

        // Then - should not attempt to download
        verifySuspend(VerifyMode.not) {
            fixture.imageRepository.downloadContributorImage(any())
        }
    }

    @Test
    fun `succeeds when image download fails`() = runTest {
        // Given
        val fixture = createFixture()
        val contributorData = createContributorWithMetadata(imageUrl = "https://server.com/image.jpg")

        everySuspend {
            fixture.metadataRepository.applyContributorMetadata(any(), any(), any(), any(), any(), any())
        } returns ContributorMetadataResult.Success(contributor = contributorData)
        everySuspend {
            fixture.imageRepository.downloadContributorImage(any())
        } returns Failure(exception = Exception("Download failed"), message = "Download failed")

        val useCase = fixture.build()

        // When
        val result = useCase(createRequest())

        // Then - should succeed without image
        assertIs<Success<Contributor>>(result)
    }

    @Test
    fun `succeeds when image save fails`() = runTest {
        // Given
        val fixture = createFixture()
        val contributorData = createContributorWithMetadata(imageUrl = "https://server.com/image.jpg")
        val imageData = byteArrayOf(1, 2, 3, 4)

        everySuspend {
            fixture.metadataRepository.applyContributorMetadata(any(), any(), any(), any(), any(), any())
        } returns ContributorMetadataResult.Success(contributor = contributorData)
        everySuspend {
            fixture.imageRepository.downloadContributorImage(any())
        } returns Success(imageData)
        everySuspend {
            fixture.imageRepository.saveContributorImage(any(), any())
        } returns Failure(exception = Exception("Save failed"), message = "Save failed")

        val useCase = fixture.build()

        // When
        val result = useCase(createRequest())

        // Then - should succeed without saved image
        assertIs<Success<Contributor>>(result)
    }

    // ========== API Error Tests ==========

    @Test
    fun `returns failure when repository returns error`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend {
            fixture.metadataRepository.applyContributorMetadata(any(), any(), any(), any(), any(), any())
        } returns ContributorMetadataResult.Error("Server rejected request")
        val useCase = fixture.build()

        // When
        val result = useCase(createRequest())

        // Then
        assertIs<Failure>(result)
        assertEquals("Server rejected request", result.message)
    }

    @Test
    fun `returns failure when repository returns disambiguation`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend {
            fixture.metadataRepository.applyContributorMetadata(any(), any(), any(), any(), any(), any())
        } returns ContributorMetadataResult.NeedsDisambiguation(emptyList())
        val useCase = fixture.build()

        // When
        val result = useCase(createRequest())

        // Then
        assertIs<Failure>(result)
        assertEquals("Unexpected disambiguation request", result.message)
    }

    // ========== Field Selection Tests ==========

    @Test
    fun `accepts request with only name selected`() = runTest {
        // Given
        val fixture = createFixture()
        val contributorData = createContributorWithMetadata(imageUrl = null)
        everySuspend {
            fixture.metadataRepository.applyContributorMetadata(any(), any(), any(), any(), any(), any())
        } returns ContributorMetadataResult.Success(contributor = contributorData)
        val useCase = fixture.build()

        val request = createRequest(name = true, biography = false, image = false)

        // When
        val result = useCase(request)

        // Then
        assertIs<Success<Contributor>>(result)
    }

    @Test
    fun `accepts request with only biography selected`() = runTest {
        // Given
        val fixture = createFixture()
        val contributorData = createContributorWithMetadata(imageUrl = null)
        everySuspend {
            fixture.metadataRepository.applyContributorMetadata(any(), any(), any(), any(), any(), any())
        } returns ContributorMetadataResult.Success(contributor = contributorData)
        val useCase = fixture.build()

        val request = createRequest(name = false, biography = true, image = false)

        // When
        val result = useCase(request)

        // Then
        assertIs<Success<Contributor>>(result)
    }

    @Test
    fun `accepts request with only image selected`() = runTest {
        // Given
        val fixture = createFixture()
        val contributorData = createContributorWithMetadata(imageUrl = null)
        everySuspend {
            fixture.metadataRepository.applyContributorMetadata(any(), any(), any(), any(), any(), any())
        } returns ContributorMetadataResult.Success(contributor = contributorData)
        val useCase = fixture.build()

        val request = createRequest(name = false, biography = false, image = true)

        // When
        val result = useCase(request)

        // Then
        assertIs<Success<Contributor>>(result)
    }
}
