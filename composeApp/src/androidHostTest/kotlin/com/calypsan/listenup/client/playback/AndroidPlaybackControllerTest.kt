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
 * 4. resolveQueuePosition index and offset arithmetic
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
    // resolveQueuePosition — pure arithmetic, no Media3 needed
    // Used by both setMediaQueue and seekTo call sites.
    // ---------------------------------------------------------------------------

    @Test
    fun `resolveQueuePosition returns 0 0 for empty item list`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        assertEquals(0 to 0L, sut.resolveQueuePosition(emptyList(), 0L))
        assertEquals(0 to 0L, sut.resolveQueuePosition(emptyList(), 12_345L))
    }

    @Test
    fun `resolveQueuePosition maps bookPosition to correct segment index and local offset`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        val items =
            listOf(
                PlaybackMediaItem("f1", "/1", null, 60_000L, 0L, "T", null, null, null),
                PlaybackMediaItem("f2", "/2", null, 90_000L, 60_000L, "T", null, null, null),
            )

        // Position 75_000 → second item, offset 15_000
        assertEquals(1 to 15_000L, sut.resolveQueuePosition(items, 75_000L))

        // Position 0 → first item, offset 0
        assertEquals(0 to 0L, sut.resolveQueuePosition(items, 0L))

        // Position 30_000 → first item, offset 30_000
        assertEquals(0 to 30_000L, sut.resolveQueuePosition(items, 30_000L))
    }

    @Test
    fun `resolveQueuePosition before first item snaps to 0 0`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        val items =
            listOf(
                PlaybackMediaItem("f1", "/1", null, 60_000L, 100L, "T", null, null, null),
            )

        assertEquals(0 to 0L, sut.resolveQueuePosition(items, 0L))
        assertEquals(0 to 0L, sut.resolveQueuePosition(items, 50L)) // before offsetMs=100
    }

    @Test
    fun `resolveQueuePosition past last item snaps to lastIndex with last item duration (drift 26 fix)`() {
        val holder = FakeControllerHolder()
        val sut = AndroidPlaybackController(holder)

        val items =
            listOf(
                PlaybackMediaItem("f1", "/1", null, 60_000L, 0L, "T", null, null, null),
                PlaybackMediaItem("f2", "/2", null, 90_000L, 60_000L, "T", null, null, null),
            )

        // Total duration = 150_000. seekTo(200_000) — past end.
        // Drift #26 (a) fix: should return (1, 90_000L) — LAST item's durationMs, not controller.duration
        assertEquals(1 to 90_000L, sut.resolveQueuePosition(items, 200_000L))
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
