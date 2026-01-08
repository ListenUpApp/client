package com.calypsan.listenup.client.domain.usecase.lens

import com.calypsan.listenup.client.checkIs
import com.calypsan.listenup.client.core.ErrorCode
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.model.Lens
import com.calypsan.listenup.client.domain.repository.LensRepository
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for Lens use cases.
 *
 * Tests cover:
 * - CreateLensUseCase: validation, repository call, error handling
 * - UpdateLensUseCase: validation, repository call, error handling
 * - DeleteLensUseCase: repository call, error handling
 */
class LensUseCasesTest {
    // ========== Test Fixtures ==========

    private fun createLens(
        id: String = "lens-123",
        name: String = "Test Lens",
        description: String? = null,
    ) = Lens(
        id = id,
        name = name,
        description = description,
        ownerId = "owner-123",
        ownerDisplayName = "Test User",
        ownerAvatarColor = "#FF0000",
        bookCount = 0,
        totalDurationSeconds = 0,
        createdAtMs = 1736208000000L,
        updatedAtMs = 1736208000000L,
    )

    // ========== CreateLensUseCase Tests ==========

    @Test
    fun `create lens returns success with valid name`() =
        runTest {
            // Given
            val lensRepository: LensRepository = mock()
            val expectedLens = createLens(name = "My Reading List")
            everySuspend { lensRepository.createLens(any(), any()) } returns expectedLens
            val useCase = CreateLensUseCase(lensRepository)

            // When
            val result = useCase(name = "My Reading List", description = null)

            // Then
            val success = assertIs<Success<Lens>>(result)
            assertEquals("My Reading List", success.data.name)
        }

    @Test
    fun `create lens calls repository with trimmed name`() =
        runTest {
            // Given
            val lensRepository: LensRepository = mock()
            everySuspend { lensRepository.createLens(any(), any()) } returns createLens()
            val useCase = CreateLensUseCase(lensRepository)

            // When
            useCase(name = "  Trimmed Name  ", description = null)

            // Then
            verifySuspend { lensRepository.createLens("Trimmed Name", null) }
        }

    @Test
    fun `create lens returns validation error for blank name`() =
        runTest {
            // Given
            val lensRepository: LensRepository = mock()
            val useCase = CreateLensUseCase(lensRepository)

            // When
            val result = useCase(name = "   ", description = null)

            // Then
            val failure = assertIs<Failure>(result)
            assertEquals(ErrorCode.VALIDATION_ERROR, failure.errorCode)
            assertEquals("Lens name is required", failure.message)
        }

    @Test
    fun `create lens returns validation error for empty name`() =
        runTest {
            // Given
            val lensRepository: LensRepository = mock()
            val useCase = CreateLensUseCase(lensRepository)

            // When
            val result = useCase(name = "", description = null)

            // Then
            val failure = assertIs<Failure>(result)
            assertEquals(ErrorCode.VALIDATION_ERROR, failure.errorCode)
        }

    @Test
    fun `create lens passes description to repository`() =
        runTest {
            // Given
            val lensRepository: LensRepository = mock()
            everySuspend { lensRepository.createLens(any(), any()) } returns createLens()
            val useCase = CreateLensUseCase(lensRepository)

            // When
            useCase(name = "Test", description = "A curated list")

            // Then
            verifySuspend { lensRepository.createLens("Test", "A curated list") }
        }

    @Test
    fun `create lens converts empty description to null`() =
        runTest {
            // Given
            val lensRepository: LensRepository = mock()
            everySuspend { lensRepository.createLens(any(), any()) } returns createLens()
            val useCase = CreateLensUseCase(lensRepository)

            // When
            useCase(name = "Test", description = "   ")

            // Then
            verifySuspend { lensRepository.createLens("Test", null) }
        }

    @Test
    fun `create lens returns failure on repository error`() =
        runTest {
            // Given
            val lensRepository: LensRepository = mock()
            everySuspend { lensRepository.createLens(any(), any()) } throws Exception("Network error")
            val useCase = CreateLensUseCase(lensRepository)

            // When
            val result = useCase(name = "Test", description = null)

            // Then
            val failure = assertIs<Failure>(result)
            assertEquals("Network error", failure.message)
        }

    // ========== UpdateLensUseCase Tests ==========

    @Test
    fun `update lens returns success with valid name`() =
        runTest {
            // Given
            val lensRepository: LensRepository = mock()
            val expectedLens = createLens(name = "Updated Name")
            everySuspend { lensRepository.updateLens(any(), any(), any()) } returns expectedLens
            val useCase = UpdateLensUseCase(lensRepository)

            // When
            val result = useCase(lensId = "lens-123", name = "Updated Name", description = null)

            // Then
            val success = assertIs<Success<Lens>>(result)
            assertEquals("Updated Name", success.data.name)
        }

    @Test
    fun `update lens calls repository with correct parameters`() =
        runTest {
            // Given
            val lensRepository: LensRepository = mock()
            everySuspend { lensRepository.updateLens(any(), any(), any()) } returns createLens()
            val useCase = UpdateLensUseCase(lensRepository)

            // When
            useCase(lensId = "lens-456", name = "New Name", description = "New description")

            // Then
            verifySuspend { lensRepository.updateLens("lens-456", "New Name", "New description") }
        }

    @Test
    fun `update lens returns validation error for blank name`() =
        runTest {
            // Given
            val lensRepository: LensRepository = mock()
            val useCase = UpdateLensUseCase(lensRepository)

            // When
            val result = useCase(lensId = "lens-123", name = "   ", description = null)

            // Then
            val failure = assertIs<Failure>(result)
            assertEquals(ErrorCode.VALIDATION_ERROR, failure.errorCode)
            assertEquals("Lens name is required", failure.message)
        }

    @Test
    fun `update lens trims name before repository call`() =
        runTest {
            // Given
            val lensRepository: LensRepository = mock()
            everySuspend { lensRepository.updateLens(any(), any(), any()) } returns createLens()
            val useCase = UpdateLensUseCase(lensRepository)

            // When
            useCase(lensId = "lens-123", name = "  Trimmed  ", description = null)

            // Then
            verifySuspend { lensRepository.updateLens("lens-123", "Trimmed", null) }
        }

    @Test
    fun `update lens converts empty description to null`() =
        runTest {
            // Given
            val lensRepository: LensRepository = mock()
            everySuspend { lensRepository.updateLens(any(), any(), any()) } returns createLens()
            val useCase = UpdateLensUseCase(lensRepository)

            // When
            useCase(lensId = "lens-123", name = "Test", description = "")

            // Then
            verifySuspend { lensRepository.updateLens("lens-123", "Test", null) }
        }

    @Test
    fun `update lens returns failure on repository error`() =
        runTest {
            // Given
            val lensRepository: LensRepository = mock()
            everySuspend { lensRepository.updateLens(any(), any(), any()) } throws Exception("Server error")
            val useCase = UpdateLensUseCase(lensRepository)

            // When
            val result = useCase(lensId = "lens-123", name = "Test", description = null)

            // Then
            val failure = assertIs<Failure>(result)
            assertEquals("Server error", failure.message)
        }

    // ========== DeleteLensUseCase Tests ==========

    @Test
    fun `delete lens returns success`() =
        runTest {
            // Given
            val lensRepository: LensRepository = mock()
            everySuspend { lensRepository.deleteLens(any()) } returns Unit
            val useCase = DeleteLensUseCase(lensRepository)

            // When
            val result = useCase(lensId = "lens-123")

            // Then
            checkIs<Success<Unit>>(result)
        }

    @Test
    fun `delete lens calls repository with correct ID`() =
        runTest {
            // Given
            val lensRepository: LensRepository = mock()
            everySuspend { lensRepository.deleteLens(any()) } returns Unit
            val useCase = DeleteLensUseCase(lensRepository)

            // When
            useCase(lensId = "lens-456")

            // Then
            verifySuspend { lensRepository.deleteLens("lens-456") }
        }

    @Test
    fun `delete lens returns failure on repository error`() =
        runTest {
            // Given
            val lensRepository: LensRepository = mock()
            everySuspend { lensRepository.deleteLens(any()) } throws Exception("Not found")
            val useCase = DeleteLensUseCase(lensRepository)

            // When
            val result = useCase(lensId = "lens-123")

            // Then
            val failure = assertIs<Failure>(result)
            assertEquals("Not found", failure.message)
        }
}
