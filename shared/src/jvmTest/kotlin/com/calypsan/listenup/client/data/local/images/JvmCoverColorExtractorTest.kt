package com.calypsan.listenup.client.data.local.images

import kotlinx.coroutines.test.runTest
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for JvmCoverColorExtractor.
 *
 * Tests color extraction using programmatically generated test images
 * to verify the k-means clustering and color selection logic.
 */
class JvmCoverColorExtractorTest {
    private val extractor = JvmCoverColorExtractor()

    @Test
    fun `extracts dominant color from solid red image`() =
        runTest {
            // Given - a solid red image
            val imageBytes = createSolidColorImage(Color.RED)

            // When
            val colors = extractor.extractColors(imageBytes)

            // Then
            assertNotNull(colors)
            val dominant = Color(colors.dominant)
            // Should be close to red (allowing some variance from algorithm)
            assertTrue(dominant.red > 200, "Red channel should be high: ${dominant.red}")
            assertTrue(dominant.green < 50, "Green channel should be low: ${dominant.green}")
            assertTrue(dominant.blue < 50, "Blue channel should be low: ${dominant.blue}")
        }

    @Test
    fun `extracts dominant color from solid blue image`() =
        runTest {
            // Given - a solid blue image
            val imageBytes = createSolidColorImage(Color.BLUE)

            // When
            val colors = extractor.extractColors(imageBytes)

            // Then
            assertNotNull(colors)
            val dominant = Color(colors.dominant)
            assertTrue(dominant.blue > 200, "Blue channel should be high: ${dominant.blue}")
            assertTrue(dominant.red < 50, "Red channel should be low: ${dominant.red}")
            assertTrue(dominant.green < 50, "Green channel should be low: ${dominant.green}")
        }

    @Test
    fun `extracts colors from two-tone image`() =
        runTest {
            // Given - image split vertically: left half red, right half blue
            val imageBytes = createTwoToneImage(Color.RED, Color.BLUE)

            // When
            val colors = extractor.extractColors(imageBytes)

            // Then
            assertNotNull(colors)
            // Dominant should be either red or blue (whichever cluster is larger)
            val dominant = Color(colors.dominant)
            val isRedish = dominant.red > 150 && dominant.blue < 100
            val isBlueish = dominant.blue > 150 && dominant.red < 100
            assertTrue(isRedish || isBlueish, "Dominant should be red or blue: $dominant")
        }

    @Test
    fun `returns null for empty byte array`() =
        runTest {
            // When
            val colors = extractor.extractColors(ByteArray(0))

            // Then
            assertNull(colors)
        }

    @Test
    fun `returns null for invalid image data`() =
        runTest {
            // Given - random garbage bytes
            val garbageBytes = "not an image at all".toByteArray()

            // When
            val colors = extractor.extractColors(garbageBytes)

            // Then
            assertNull(colors)
        }

    @Test
    fun `handles very small image`() =
        runTest {
            // Given - 1x1 pixel image
            val imageBytes = createSolidColorImage(Color.GREEN, width = 1, height = 1)

            // When
            val colors = extractor.extractColors(imageBytes)

            // Then - might be null (too few pixels) or extract the color
            // Either behavior is acceptable for edge case
            if (colors != null) {
                val dominant = Color(colors.dominant)
                assertTrue(dominant.green > 100, "Should detect green-ish color")
            }
        }

    @Test
    fun `handles large image efficiently`() =
        runTest {
            // Given - large image (1000x1000 = 1M pixels)
            val imageBytes = createSolidColorImage(Color.ORANGE, width = 1000, height = 1000)

            // When - should complete in reasonable time due to sampling
            val startTime = System.currentTimeMillis()
            val colors = extractor.extractColors(imageBytes)
            val elapsed = System.currentTimeMillis() - startTime

            // Then
            assertNotNull(colors)
            assertTrue(elapsed < 5000, "Should complete in under 5 seconds: ${elapsed}ms")
        }

    @Test
    fun `vibrant color has high saturation`() =
        runTest {
            // Given - image with saturated colors
            val imageBytes =
                createTwoToneImage(
                    Color(255, 0, 0), // Pure red (saturated)
                    Color(100, 100, 100), // Gray (unsaturated)
                )

            // When
            val colors = extractor.extractColors(imageBytes)

            // Then
            assertNotNull(colors)
            val vibrant = Color(colors.vibrant)
            val hsb = Color.RGBtoHSB(vibrant.red, vibrant.green, vibrant.blue, null)
            val saturation = hsb[1]
            assertTrue(saturation > 0.1f, "Vibrant color should have decent saturation: $saturation")
        }

    @Test
    fun `dark muted color has low brightness`() =
        runTest {
            // Given - image with dark and light colors
            val imageBytes =
                createTwoToneImage(
                    Color(50, 30, 30), // Dark brownish
                    Color(255, 255, 200), // Light cream
                )

            // When
            val colors = extractor.extractColors(imageBytes)

            // Then
            assertNotNull(colors)
            val darkMuted = Color(colors.darkMuted)
            val hsb = Color.RGBtoHSB(darkMuted.red, darkMuted.green, darkMuted.blue, null)
            val brightness = hsb[2]
            assertTrue(brightness < 0.7f, "Dark muted should have lower brightness: $brightness")
        }

    @Test
    fun `colors are valid ARGB integers`() =
        runTest {
            // Given
            val imageBytes = createSolidColorImage(Color.CYAN)

            // When
            val colors = extractor.extractColors(imageBytes)

            // Then
            assertNotNull(colors)

            // All colors should have full alpha (0xFF in high byte)
            assertEquals(0xFF, (colors.dominant shr 24) and 0xFF, "Dominant should have full alpha")
            assertEquals(0xFF, (colors.darkMuted shr 24) and 0xFF, "DarkMuted should have full alpha")
            assertEquals(0xFF, (colors.vibrant shr 24) and 0xFF, "Vibrant should have full alpha")

            // RGB values should be in valid range (implicit by Int, but verify construction)
            val dominant = Color(colors.dominant)
            assertTrue(dominant.red in 0..255)
            assertTrue(dominant.green in 0..255)
            assertTrue(dominant.blue in 0..255)
        }

    @Test
    fun `handles PNG format`() =
        runTest {
            // Given - PNG image
            val imageBytes = createSolidColorImage(Color.MAGENTA, format = "PNG")

            // When
            val colors = extractor.extractColors(imageBytes)

            // Then
            assertNotNull(colors)
        }

    @Test
    fun `handles JPEG format`() =
        runTest {
            // Given - JPEG image
            val imageBytes = createSolidColorImage(Color.YELLOW, format = "JPEG")

            // When
            val colors = extractor.extractColors(imageBytes)

            // Then
            assertNotNull(colors)
        }

    // --- Helper functions ---

    private fun createSolidColorImage(
        color: Color,
        width: Int = 100,
        height: Int = 100,
        format: String = "PNG",
    ): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = color
        graphics.fillRect(0, 0, width, height)
        graphics.dispose()

        return ByteArrayOutputStream().use { baos ->
            ImageIO.write(image, format, baos)
            baos.toByteArray()
        }
    }

    private fun createTwoToneImage(
        leftColor: Color,
        rightColor: Color,
        width: Int = 100,
        height: Int = 100,
    ): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()

        // Left half
        graphics.color = leftColor
        graphics.fillRect(0, 0, width / 2, height)

        // Right half
        graphics.color = rightColor
        graphics.fillRect(width / 2, 0, width / 2, height)

        graphics.dispose()

        return ByteArrayOutputStream().use { baos ->
            ImageIO.write(image, "PNG", baos)
            baos.toByteArray()
        }
    }
}
