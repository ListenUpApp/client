package com.calypsan.listenup.client.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceContextTest {

    @Test
    fun phoneHasTouch() {
        assertTrue(DeviceContext(DeviceType.Phone).hasTouch)
    }

    @Test
    fun phoneCanEdit() {
        assertTrue(DeviceContext(DeviceType.Phone).canEdit)
    }

    @Test
    fun phoneSupportsFullLibrary() {
        assertTrue(DeviceContext(DeviceType.Phone).supportsFullLibrary)
    }

    @Test
    fun phoneHasNoDpad() {
        assertFalse(DeviceContext(DeviceType.Phone).hasDpad)
    }

    @Test
    fun phoneIsNotLeanback() {
        assertFalse(DeviceContext(DeviceType.Phone).isLeanback)
    }

    @Test
    fun tvIsLeanback() {
        val ctx = DeviceContext(DeviceType.Tv)
        assertTrue(ctx.isLeanback)
        assertTrue(ctx.hasDpad)
        assertTrue(ctx.prefersLargeTargets)
        assertFalse(ctx.hasTouch)
        assertFalse(ctx.canEdit)
        assertTrue(ctx.supportsFullLibrary)
    }

    @Test
    fun desktopCanEditButNoDpad() {
        val ctx = DeviceContext(DeviceType.Desktop)
        assertTrue(ctx.canEdit)
        assertFalse(ctx.hasDpad)
        assertFalse(ctx.hasTouch)
        assertTrue(ctx.supportsFullLibrary)
    }

    @Test
    fun watchIsWearable() {
        val ctx = DeviceContext(DeviceType.Watch)
        assertTrue(ctx.isWearable)
        assertTrue(ctx.hasTouch)
        assertFalse(ctx.canEdit)
        assertFalse(ctx.supportsFullLibrary)
    }

    @Test
    fun autoHasDpadAndLargeTargets() {
        val ctx = DeviceContext(DeviceType.Auto)
        assertTrue(ctx.hasDpad)
        assertTrue(ctx.prefersLargeTargets)
        assertFalse(ctx.hasTouch)
        assertFalse(ctx.canEdit)
    }

    @Test
    fun xrHasTouchAndLargeTargets() {
        val ctx = DeviceContext(DeviceType.Xr)
        assertTrue(ctx.hasTouch)
        assertTrue(ctx.prefersLargeTargets)
        assertFalse(ctx.hasDpad)
    }

    @Test
    fun tabletCapabilities() {
        val ctx = DeviceContext(DeviceType.Tablet)
        assertTrue(ctx.hasTouch)
        assertTrue(ctx.canEdit)
        assertTrue(ctx.supportsFullLibrary)
        assertFalse(ctx.hasDpad)
        assertFalse(ctx.isLeanback)
    }

    @Test
    fun allDeviceTypes() {
        // Ensure every DeviceType creates a valid DeviceContext
        DeviceType.entries.forEach { type ->
            val ctx = DeviceContext(type)
            assertEquals(type, ctx.type)
        }
    }
}
