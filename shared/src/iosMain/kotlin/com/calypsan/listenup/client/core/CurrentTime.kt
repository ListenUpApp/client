package com.calypsan.listenup.client.core

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun currentEpochMilliseconds(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun currentHourOfDay(): Int {
    val calendar = NSCalendar.currentCalendar
    val components = calendar.components(NSCalendarUnitHour, fromDate = NSDate())
    return components.hour.toInt()
}
