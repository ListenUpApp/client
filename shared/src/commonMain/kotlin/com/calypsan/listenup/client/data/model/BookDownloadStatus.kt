package com.calypsan.listenup.client.data.model

import com.calypsan.listenup.client.domain.model.BookDownloadState as DomainBookDownloadState
import com.calypsan.listenup.client.domain.model.BookDownloadStatus as DomainBookDownloadStatus

/**
 * Type aliases to domain models for backwards compatibility in data layer.
 * New code should import from domain.model directly.
 */
typealias BookDownloadStatus = DomainBookDownloadStatus
typealias BookDownloadState = DomainBookDownloadState
