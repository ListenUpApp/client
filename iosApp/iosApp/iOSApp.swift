import SwiftUI
import Shared

@main
struct ListenUpApp: App {

    init() {
        // Initialize Koin before any UI renders
        Koin_iosKt.initializeKoin(additionalModules: [])
        Log.info("ListenUp iOS app initialized")
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .tint(Color.listenUpOrange)
        }
    }
}

// MARK: - Root View

/// The actual root view that creates observers after Koin is initialized.
/// We use a separate struct because App.init() runs before @State property initializers
/// would access Koin, but View.init() runs after the App is fully constructed.
private struct RootView: View {
    @State private var auth = AuthStateObserver()
    @State private var currentUser = CurrentUserObserver()

    var body: some View {
        content
            .environment(currentUser)
            .animation(.smooth(duration: 0.3), value: auth.state)
    }

    @ViewBuilder
    private var content: some View {
        switch auth.state {
        case .initializing, .checkingServer:
            LaunchScreen()

        case .needsServerUrl:
            ServerFlowCoordinator()

        case .needsSetup:
            ServerFlowCoordinator()

        case .needsLogin:
            AuthFlowCoordinator(openRegistration: auth.openRegistration)

        case .pendingApproval:
            PendingApprovalView()

        case .authenticated:
            MainAppView()
        }
    }
}

// MARK: - Launch Screen

/// Shown during app initialization.
/// Keep it simple. Keep it fast.
private struct LaunchScreen: View {
    var body: some View {
        ZStack {
            Color.brandGradient
                .ignoresSafeArea()

            Image("listenup_logo_white")
                .resizable()
                .scaledToFit()
                .frame(width: 120, height: 120)
        }
    }
}

// MARK: - Pending Approval (Placeholder)

private struct PendingApprovalView: View {
    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "clock.badge.checkmark")
                .font(.system(size: 64))
                .foregroundStyle(Color.listenUpOrange)

            Text(String(localized: "auth.waiting_for_approval"))
                .font(.title.bold())

            Text(String(localized: "auth.pending_approval_message"))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
    }
}

// MARK: - Main App

private struct MainAppView: View {
    var body: some View {
        MainTabView()
    }
}
