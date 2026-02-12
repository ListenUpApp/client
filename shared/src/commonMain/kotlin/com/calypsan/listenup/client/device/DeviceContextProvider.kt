package com.calypsan.listenup.client.device

expect class DeviceContextProvider {
    fun detect(): DeviceContext
}
