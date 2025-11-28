package com.calypsan.listenup.client.core

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun currentEpochMilliseconds(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
