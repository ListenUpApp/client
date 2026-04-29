package com.calypsan.listenup.client.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Delegation tests for [AndroidPlaybackController].
 *
 * [androidx.media3.session.MediaController] is a final Android framework class that
 * cannot be instantiated in JVM host tests. Tests therefore cover:
 *
 * 1. acquire/release delegate to [ControllerHolder]
 * 2. isReady mirrors holder.isConnected
 * 3. All command methods are silent no-ops when controller is null (no crash)
 * 4. resolveStartPosition index and offset arithmetic
 */
class AndroidPlaybackControllerTest {
    // ---------------------------------------------------------------------------
    // Fake ControllerHolder (no Media3 framework needed)
    // ---------------------------------------------------------------------------

    private class FakeControllerHolder(
        initialConnected: Boolean = true,
    ) : ControllerHolder {
        var acquireCount = 0
        var releaseCount = 0
        private val _isConnected = MutableStateFlow(initialConnected)
        override val isConnected: StateFlow<Boolean> = _isConnected

        /** Always null — MediaController cannot be instantiated in JVM host tests. */
        override val controller: androidx.media3.session.MediaController? = null

        override fun acquire() {
            acquireCount++
        }

        override fun release() {
            releaseCount++
        }

        fun setConnected(value: Boolean) {
            _isConnected.value = value
        }
    }

    // ---------------------------------------------------------------------------
    // acquire / release
    // ---------------------------------------------------------------------------

    @Test
    fun `acquire delegates to holder`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        sut.acquire()
        sut.acquire()

        assertEquals(2, holder.acquireCount)
    }

    @Test
    fun `release delegates to holder`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        sut.release()

        assertEquals(1, holder.releaseCount)
    }

    // ---------------------------------------------------------------------------
    // isReady mirrors isConnected
    // ---------------------------------------------------------------------------

    @Test
    fun `isReady reflects holder isConnected initial value true`() {
        val holder = FakeControllerHolder(initialConnected = true)
        val sut = AndroidPlaybackController(holder)

        assertTrue(sut.isReady.value)
    }

    @Test
    fun `isReady reflects holder isConnected initial value false`() {
        val holder = FakeControllerHolder(initialConnected = false)
        val sut = AndroidPlaybackController(holder)

        assertFalse(sut.isReady.value)
    }

    @Test
    fun `isReady updates when holder isConnected changes`() {
        val holder = FakeControllerHolder(initialConnected = true)
        val sut = AndroidPlaybackController(holder)

        holder.setConnected(false)

        assertFalse(sut.isReady.value)
    }

    // ---------------------------------------------------------------------------
    // Null-controller silent no-ops
    // ---------------------------------------------------------------------------

    @Test
    fun `play does not throw when controller is null`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        sut.play() // holder.controller == null — must not throw
    }

    @Test
    fun `pause does not throw when controller is null`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        sut.pause()
    }

    @Test
    fun `seekTo does not throw when controller is null`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        sut.seekTo(30_000L)
    }

    @Test
    fun `setPlaybackSpeed does not throw when controller is null`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        sut.setPlaybackSpeed(1.5f)
    }

    @Test
    fun `setMediaQueue does not throw when controller is null`() =
        runTest {
            val holder = FakeControllerHolder()
            val sut = AndroidPlaybackController(holder)

            sut.setMediaQueue(emptyList(), 0L)
        }

    // ---------------------------------------------------------------------------
    // resolveStartPosition — pure arithmetic, no Media3 needed
    // ---------------------------------------------------------------------------

    @Test
    fun `resolveStartPosition returns 0,0 for empty item list`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        val (index, offset) = sut.resolveStartPosition(emptyList(), 75_000L)

        assertEquals(0, index)
        assertEquals(0L, offset)
    }

    @Test
    fun `resolveStartPosition maps bookPosition to correct segment index and local offset`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        val items =
            listOf(
                PlaybackMediaItem("id-0", "url0", null, 60_000L, 0L, "Ch 1", null, null, null),
                PlaybackMediaItem("id-1", "url1", null, 60_000L, 60_000L, "Ch 2", null, null, null),
            )

        // bookPositionMs = 75_000 → second segment, 15_000ms in
        val (index, offset) = sut.resolveStartPosition(items, 75_000L)

        assertEquals(1, index)
        assertEquals(15_000L, offset)
    }

    @Test
    fun `resolveStartPosition clamps to start when position precedes first segment`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        val items =
            listOf(
                PlaybackMediaItem("id-0", "url0", null, 60_000L, 1_000L, "Ch 1", null, null, null),
            )

        val (index, offset) = sut.resolveStartPosition(items, 0L)

        assertEquals(0, index)
        assertEquals(0L, offset)
    }

    @Test
    fun `resolveStartPosition clamps to last segment end when position exceeds all segments`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        val items =
            listOf(
                PlaybackMediaItem("id-0", "url0", null, 60_000L, 0L, "Ch 1", null, null, null),
                PlaybackMediaItem("id-1", "url1", null, 60_000L, 60_000L, "Ch 2", null, null, null),
            )

        val (index, offset) = sut.resolveStartPosition(items, 200_000L)

        assertEquals(1, index)
        assertEquals(60_000L, offset)
    }

    // ---------------------------------------------------------------------------
    // stop / setVolume — null-controller silent no-ops
    // ---------------------------------------------------------------------------

    @Test
    fun `stop does not throw when controller is null`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        sut.stop() // holder.controller == null — must not throw
    }

    @Test
    fun `setVolume does not throw when controller is null`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        sut.setVolume(0.5f) // holder.controller == null — must not throw
    }

    // ---------------------------------------------------------------------------
    // seekTo — null-controller silent no-op (already covered by existing test,
    // but verify the enhanced implementation still handles null gracefully)
    // ---------------------------------------------------------------------------

    @Test
    fun `seekTo does not throw when controller is null (enhanced impl)`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        sut.seekTo(75_000L) // controller null — must not throw
    }

    // ---------------------------------------------------------------------------
    // resolveSeekPosition — pure arithmetic, no Media3 needed
    // ---------------------------------------------------------------------------

    @Test
    fun `resolveSeekPosition returns 0 0 for empty item list`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        assertEquals(0 to 0L, sut.resolveSeekPosition(emptyList(), 0L))
        assertEquals(0 to 0L, sut.resolveSeekPosition(emptyList(), 12_345L))
    }

    @Test
    fun `resolveSeekPosition maps bookPosition to correct segment index and local offset`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        val items =
            listOf(
                PlaybackMediaItem("f1", "/1", null, 60_000L, 0L, "T", null, null, null),
                PlaybackMediaItem("f2", "/2", null, 90_000L, 60_000L, "T", null, null, null),
            )

        // Position 75_000 → second item, offset 15_000
        assertEquals(1 to 15_000L, sut.resolveSeekPosition(items, 75_000L))

        // Position 0 → first item, offset 0
        assertEquals(0 to 0L, sut.resolveSeekPosition(items, 0L))

        // Position 30_000 → first item, offset 30_000
        assertEquals(0 to 30_000L, sut.resolveSeekPosition(items, 30_000L))
    }

    @Test
    fun `resolveSeekPosition before first item snaps to 0 0`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        val items =
            listOf(
                PlaybackMediaItem("f1", "/1", null, 60_000L, 100L, "T", null, null, null),
            )

        assertEquals(0 to 0L, sut.resolveSeekPosition(items, 0L))
        assertEquals(0 to 0L, sut.resolveSeekPosition(items, 50L)) // before offsetMs=100
    }

    @Test
    fun `resolveSeekPosition past last item snaps to lastIndex with last item duration (drift 26 fix)`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        val items =
            listOf(
                PlaybackMediaItem("f1", "/1", null, 60_000L, 0L, "T", null, null, null),
                PlaybackMediaItem("f2", "/2", null, 90_000L, 60_000L, "T", null, null, null),
            )

        // Total duration = 150_000. seekTo(200_000) — past end.
        // Drift #26 (a) fix: should return (1, 90_000L) — LAST item's durationMs, not controller.duration
        assertEquals(1 to 90_000L, sut.resolveSeekPosition(items, 200_000L))
    }

    @Test
    fun `seekTo with empty cached queue does not throw and falls back gracefully`() {
        val holder = FakeControllerHolder() // controller is always null
        val sut = AndroidPlaybackController(holder)

        // No setMediaQueue call — cachedQueue is empty. Controller is null so the
        // null-check fires first; either way the call must not throw.
        sut.seekTo(5_000L)
    }
}
