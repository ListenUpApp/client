package com.calypsan.listenup.client.core

import java.util.Calendar

actual fun currentEpochMilliseconds(): Long = System.currentTimeMillis()

actual fun currentHourOfDay(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
