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

    private var _loginViewModel: LoginViewModel?
    var loginViewModel: LoginViewModel {
        if let cached = _loginViewModel {
            return cached
        }
        let instance = KoinHelper.shared.getLoginViewModel()
        _loginViewModel = instance
        return instance
    }

    private var _registerViewModel: RegisterViewModel?
    var registerViewModel: RegisterViewModel {
        if let cached = _registerViewModel {
            return cached
        }
        let instance = KoinHelper.shared.getRegisterViewModel()
        _registerViewModel = instance
        return instance
    }

    private var _serverSelectViewModel: ServerSelectViewModel?
    var serverSelectViewModel: ServerSelectViewModel {
        if let cached = _serverSelectViewModel {
            return cached
        }
        let instance = KoinHelper.shared.getServerSelectViewModel()
        _serverSelectViewModel = instance
        return instance
    }

    // MARK: - Settings (Segregated Interfaces)

    private var _authSession: AuthSession?
    var authSession: AuthSession {
        if let cached = _authSession {
            return cached
        }
        let instance = KoinHelper.shared.getAuthSession()
        _authSession = instance
        return instance
    }

    private var _serverConfig: ServerConfig?
    var serverConfig: ServerConfig {
        if let cached = _serverConfig {
            return cached
        }
        let instance = KoinHelper.shared.getServerConfig()
        _serverConfig = instance
        return instance
    }

    // MARK: - Library

    private var _libraryViewModel: LibraryViewModel?
    var libraryViewModel: LibraryViewModel {
        if let cached = _libraryViewModel {
            return cached
        }
        let instance = KoinHelper.shared.getLibraryViewModel()
        _libraryViewModel = instance
        return instance
    }

    // MARK: - Detail ViewModels (factory - new instance each time)

    /// Creates a new BookDetailViewModel instance (not cached - each screen gets its own)
    func createBookDetailViewModel() -> BookDetailViewModel {
        KoinHelper.shared.getBookDetailViewModel()
    }

    /// Creates a new SeriesDetailViewModel instance (not cached - each screen gets its own)
    func createSeriesDetailViewModel() -> SeriesDetailViewModel {
        KoinHelper.shared.getSeriesDetailViewModel()
    }

    /// Creates a new ContributorDetailViewModel instance (not cached - each screen gets its own)
    func createContributorDetailViewModel() -> ContributorDetailViewModel {
        KoinHelper.shared.getContributorDetailViewModel()
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
        loginVM: LoginViewModel? = nil,
        registerVM: RegisterViewModel? = nil,
        serverSelectVM: ServerSelectViewModel? = nil,
        authSession: AuthSession? = nil,
        serverConfig: ServerConfig? = nil,
        libraryVM: LibraryViewModel? = nil
    ) -> Dependencies {
        let mock = Dependencies()

        // Override with mocks if provided
        if let uc = getInstanceUC {
            mock._getInstanceUseCase = uc
        }
        if let vm = serverConnectVM {
            mock._serverConnectViewModel = vm
        }
        if let vm = loginVM {
            mock._loginViewModel = vm
        }
        if let vm = registerVM {
            mock._registerViewModel = vm
        }
        if let vm = serverSelectVM {
            mock._serverSelectViewModel = vm
        }
        if let auth = authSession {
            mock._authSession = auth
        }
        if let config = serverConfig {
            mock._serverConfig = config
        }
        if let vm = libraryVM {
            mock._libraryViewModel = vm
        }

        return mock
    }
}
