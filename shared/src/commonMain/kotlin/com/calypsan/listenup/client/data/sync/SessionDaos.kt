package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.data.local.db.ActiveSessionDao
import com.calypsan.listenup.client.data.local.db.ListeningEventDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao

/**
 * Bundle of session/playback-state DAOs passed to components that coordinate session events.
 *
 * Extracted to keep constructor parameter lists under detekt's `LongParameterList` threshold.
 */
data class SessionDaos(
    val activeSessionDao: ActiveSessionDao,
    val listeningEventDao: ListeningEventDao,
    val playbackPositionDao: PlaybackPositionDao,
)
