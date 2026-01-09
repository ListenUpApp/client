import Foundation
import Shared

/// Swift model for discovered server display.
struct DiscoveredServerItem: Identifiable, Equatable {
    let id: String
    let name: String
    let host: String
    let port: Int
    let version: String
    let isOnline: Bool

    var hostPort: String { "\(host):\(port)" }
}

/// Memory-safe Swift wrapper for Kotlin's ServerSelectViewModel.
///
/// Provides mDNS server discovery functionality with SKIE flow collection.
/// Manages discovery lifecycle and server selection.
@Observable
final class ServerSelectViewModelWrapper {
    private let kotlinVM: ServerSelectViewModel
    private var observationTask: Task<Void, Never>?
    private var navigationTask: Task<Void, Never>?

    // Keep reference to Kotlin servers for selection
    private var kotlinServers: [ServerWithStatus] = []

    // Swift properties mirroring Kotlin UiState
    var servers: [DiscoveredServerItem] = []
    var isDiscovering: Bool = true
    var selectedServerId: String? = nil
    var isConnecting: Bool = false
    var error: String? = nil

    // Navigation callbacks
    var onServerActivated: (() -> Void)?
    var onManualEntryRequested: (() -> Void)?

    init(viewModel: ServerSelectViewModel) {
        self.kotlinVM = viewModel
        observeState()
        observeNavigation()
    }

    private func observeState() {
        observationTask = Task { [weak self] in
            guard let self = self else { return }

            for await state in self.kotlinVM.state {
                guard !Task.isCancelled else { break }

                await MainActor.run { [weak self] in
                    guard let self = self else { return }

                    self.isDiscovering = state.isDiscovering
                    self.selectedServerId = state.selectedServerId
                    self.isConnecting = state.isConnecting
                    self.error = state.error

                    // Store Kotlin servers for later selection
                    self.kotlinServers = Array(state.servers)

                    // Map Kotlin servers to Swift model
                    self.servers = state.servers.map { serverWithStatus in
                        let server = serverWithStatus.server
                        // Extract host:port from localUrl or use placeholder
                        let (host, port) = self.parseHostPort(from: server.localUrl)

                        return DiscoveredServerItem(
                            id: server.id,
                            name: server.name,
                            host: host,
                            port: port,
                            version: server.serverVersion,
                            isOnline: serverWithStatus.isOnline
                        )
                    }
                }
            }
        }
    }

    private func observeNavigation() {
        navigationTask = Task { [weak self] in
            guard let self = self else { return }

            for await event in self.kotlinVM.navigationEvents {
                guard !Task.isCancelled else { break }
                guard let event = event else { continue }

                await MainActor.run { [weak self] in
                    guard let self = self else { return }

                    if event is ServerSelectViewModelNavigationEventServerActivated {
                        self.kotlinVM.onNavigationHandled()
                        self.onServerActivated?()
                    } else if event is ServerSelectViewModelNavigationEventGoToManualEntry {
                        self.kotlinVM.onNavigationHandled()
                        self.onManualEntryRequested?()
                    }
                }
            }
        }
    }

    /// Parse host and port from a URL string like "http://192.168.1.100:8080"
    private func parseHostPort(from urlString: String?) -> (String, Int) {
        guard let urlString = urlString,
              let url = URL(string: urlString) else {
            return ("unknown", 0)
        }
        return (url.host ?? "unknown", url.port ?? 8080)
    }

    // MARK: - Actions

    /// Select a discovered server and activate it
    func selectServer(_ server: DiscoveredServerItem) {
        // Find the matching Kotlin ServerWithStatus from cached list
        guard let serverWithStatus = kotlinServers.first(where: { $0.server.id == server.id }) else { return }

        kotlinVM.onEvent(event: ServerSelectUiEventServerSelected(server: serverWithStatus))
    }

    /// Refresh discovery
    func refresh() {
        kotlinVM.onEvent(event: ServerSelectUiEventRefreshClicked.shared)
    }

    /// Dismiss error
    func dismissError() {
        kotlinVM.onEvent(event: ServerSelectUiEventErrorDismissed.shared)
    }

    /// Request manual entry
    func requestManualEntry() {
        kotlinVM.onEvent(event: ServerSelectUiEventManualEntryClicked.shared)
    }

    // MARK: - Cleanup

    deinit {
        observationTask?.cancel()
        navigationTask?.cancel()
    }
}
