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

    // MARK: - Cached Resolution

    /// Cache storage for singleton dependencies
    private var cache: [String: Any] = [:]

    /// Resolve a dependency from Koin, caching for subsequent access
    private func resolve<T>(_ factory: () -> T) -> T {
        let key = String(describing: T.self)
        if let cached = cache[key] as? T { return cached }
        let instance = factory()
        cache[key] = instance
        return instance
    }

    // MARK: - Use Cases

    var getInstanceUseCase: GetInstanceUseCase { resolve { KoinHelper.shared.getInstanceUseCase() } }

    // MARK: - ViewModels (singletons)

    var serverConnectViewModel: ServerConnectViewModel { resolve { KoinHelper.shared.getServerConnectViewModel() } }
    var loginViewModel: LoginViewModel { resolve { KoinHelper.shared.getLoginViewModel() } }
    var registerViewModel: RegisterViewModel { resolve { KoinHelper.shared.getRegisterViewModel() } }
    var serverSelectViewModel: ServerSelectViewModel { resolve { KoinHelper.shared.getServerSelectViewModel() } }

    // MARK: - Settings

    var authSession: AuthSession { resolve { KoinHelper.shared.getAuthSession() } }
    var serverConfig: ServerConfig { resolve { KoinHelper.shared.getServerConfig() } }

    // MARK: - Library

    var libraryViewModel: LibraryViewModel { resolve { KoinHelper.shared.getLibraryViewModel() } }

    // MARK: - Playback

    var playbackManager: PlaybackManager { resolve { KoinHelper.shared.getPlaybackManager() } }
    var audioPlayer: AudioPlayer { resolve { KoinHelper.shared.getAudioPlayer() } }
    var bookRepository: BookRepository { resolve { KoinHelper.shared.getBookRepository() } }
    var imageStorage: ImageStorage { resolve { KoinHelper.shared.getImageStorage() } }
    var sleepTimerManager: SleepTimerManager { resolve { KoinHelper.shared.getSleepTimerManager() } }
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
        if let v = getInstanceUC { mock.cache[String(describing: GetInstanceUseCase.self)] = v }
        if let v = serverConnectVM { mock.cache[String(describing: ServerConnectViewModel.self)] = v }
        if let v = loginVM { mock.cache[String(describing: LoginViewModel.self)] = v }
        if let v = registerVM { mock.cache[String(describing: RegisterViewModel.self)] = v }
        if let v = serverSelectVM { mock.cache[String(describing: ServerSelectViewModel.self)] = v }
        if let v = authSession { mock.cache[String(describing: AuthSession.self)] = v }
        if let v = serverConfig { mock.cache[String(describing: ServerConfig.self)] = v }
        if let v = libraryVM { mock.cache[String(describing: LibraryViewModel.self)] = v }
        return mock
    }
}
