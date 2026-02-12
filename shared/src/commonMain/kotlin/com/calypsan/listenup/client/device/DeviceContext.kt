package com.calypsan.listenup.client.device

data class DeviceContext(
    val type: DeviceType,
) {
    val hasTouch: Boolean get() = type in setOf(DeviceType.Phone, DeviceType.Tablet, DeviceType.Xr, DeviceType.Watch)
    val hasDpad: Boolean get() = type in setOf(DeviceType.Tv, DeviceType.Auto)
    val canEdit: Boolean get() = type in setOf(DeviceType.Phone, DeviceType.Tablet, DeviceType.Desktop)
    val isLeanback: Boolean get() = type == DeviceType.Tv
    val prefersLargeTargets: Boolean get() = type in setOf(DeviceType.Tv, DeviceType.Xr, DeviceType.Auto)
    val isWearable: Boolean get() = type == DeviceType.Watch
    val supportsFullLibrary: Boolean get() = type in setOf(DeviceType.Phone, DeviceType.Tablet, DeviceType.Desktop, DeviceType.Tv)
}
