import Foundation
import SwiftUI
import Shared

/// Dependency container that wraps Koin for SwiftUI-native injection.
///
/// This container provides a clean Swift API for accessing dependencies
/// from the shared Kotlin module while enabling:
/// - SwiftUI Environment injection
/// - Easy mocking for previews and tests
/// - Single point of Koin access
/// - Type-safe dependency resolution
///
/// Usage:
/// ```swift
/// @Environment(\.dependencies) private var deps
/// let viewModel = deps.serverConnectViewModel
/// ```
@Observable
final class Dependencies {
    /// Shared instance for production use
    static let shared = Dependencies()

    private init() {
        // Koin is initialized in iOSApp.swift via Koin_iosKt.initializeKoin
    }

    // MARK: - Use Cases

    private var _getInstanceUseCase: GetInstanceUseCase?
    var getInstanceUseCase: GetInstanceUseCase {
        if let cached = _getInstanceUseCase {
            return cached
        }
        let instance = KoinHelper.shared.getInstanceUseCase()
        _getInstanceUseCase = instance
        return instance
    }

    // MARK: - ViewModels

    private var _serverConnectViewModel: ServerConnectViewModel?
    var serverConnectViewModel: ServerConnectViewModel {
        if let cached = _serverConnectViewModel {
            return cached
        }
        let instance = KoinHelper.shared.getServerConnectViewModel()
        _serverConnectViewModel = instance
        return instance
    }

    // MARK: - Repositories

    private var _settingsRepository: SettingsRepository?
    var settingsRepository: SettingsRepository {
        if let cached = _settingsRepository {
            return cached
        }
        let instance = KoinHelper.shared.getSettingsRepository()
        _settingsRepository = instance
        return instance
    }
}

// MARK: - SwiftUI Environment

private struct DependenciesKey: EnvironmentKey {
    static let defaultValue = Dependencies.shared
}

extension EnvironmentValues {
    var dependencies: Dependencies {
        get { self[DependenciesKey.self] }
        set { self[DependenciesKey.self] = newValue }
    }
}

// MARK: - Mock Support

extension Dependencies {
    /// Create a mock Dependencies container for previews and tests
    ///
    /// Example:
    /// ```swift
    /// let mock = Dependencies.mock(
    ///     serverConnectVM: MockServerConnectViewModel()
    /// )
    /// ```
    static func mock(
        getInstanceUC: GetInstanceUseCase? = nil,
        serverConnectVM: ServerConnectViewModel? = nil,
        settingsRepo: SettingsRepository? = nil
    ) -> Dependencies {
        let mock = Dependencies()

        // Override with mocks if provided
        if let uc = getInstanceUC {
            mock._getInstanceUseCase = uc
        }
        if let vm = serverConnectVM {
            mock._serverConnectViewModel = vm
        }
        if let repo = settingsRepo {
            mock._settingsRepository = repo
        }

        return mock
    }
}
