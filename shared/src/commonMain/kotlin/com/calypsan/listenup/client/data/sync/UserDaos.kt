package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.db.UserProfileDao
import com.calypsan.listenup.client.data.local.db.UserStatsDao

/**
 * Bundle of user-related DAOs passed to components that need multiple user data sources.
 *
 * Extracted to keep constructor parameter lists under detekt's `LongParameterList` threshold.
 * Components destructure this into individual private fields at the class top for ergonomic use.
 */
data class UserDaos(
    val userDao: UserDao,
    val userProfileDao: UserProfileDao,
    val userStatsDao: UserStatsDao,
)
