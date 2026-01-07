package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.domain.model.DiscoveredServer as DomainDiscoveredServer
import com.calypsan.listenup.client.domain.model.Server as DomainServer
import com.calypsan.listenup.client.domain.model.ServerWithStatus as DomainServerWithStatus

/**
 * Type aliases to domain models for backwards compatibility in data layer.
 * New code should import from domain.model directly.
 */
@Deprecated("Use domain.model.ServerWithStatus instead", ReplaceWith("com.calypsan.listenup.client.domain.model.ServerWithStatus"))
typealias ServerWithStatus = DomainServerWithStatus

@Deprecated("Use domain.model.Server instead", ReplaceWith("com.calypsan.listenup.client.domain.model.Server"))
typealias Server = DomainServer

@Deprecated("Use domain.model.DiscoveredServer instead", ReplaceWith("com.calypsan.listenup.client.domain.model.DiscoveredServer"))
typealias DiscoveredServer = DomainDiscoveredServer

/**
 * Type alias for backwards compatibility.
 * Use ServerRepositoryImpl directly for new code.
 */
@Deprecated("Use ServerRepositoryImpl instead", ReplaceWith("ServerRepositoryImpl"))
typealias ServerRepository = ServerRepositoryImpl

/**
 * Type alias for domain interface.
 */
@Deprecated("Use domain.repository.ServerRepository instead", ReplaceWith("com.calypsan.listenup.client.domain.repository.ServerRepository"))
typealias ServerRepositoryContract = com.calypsan.listenup.client.domain.repository.ServerRepository
