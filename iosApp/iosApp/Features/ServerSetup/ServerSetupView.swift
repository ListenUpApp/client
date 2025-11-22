import SwiftUI
import Shared

/// Server setup screen with liquid glass aesthetic.
///
/// First step in the unauthenticated flow where users enter and verify
/// their ListenUp server URL. Features:
/// - Gradient background (from mockup)
/// - Liquid glass form card
/// - Real-time validation
/// - Loading states
/// - Error handling
///
/// Layout:
/// - Top 1/3: Logo section
/// - Bottom 2/3: Form card with glass aesthetic
struct ServerSetupView: View {
    @Environment(\.dependencies) private var deps
    @Environment(AppNavigator.self) private var navigator
    @State private var viewModel: ServerConnectViewModelWrapper

    init(dependencies: Dependencies = .shared) {
        _viewModel = State(initialValue:
            ServerConnectViewModelWrapper(
                viewModel: dependencies.serverConnectViewModel
            )
        )
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Brand gradient background (Dark Grey -> Orange)
                Color.brandGradient
                    .ignoresSafeArea()

                VStack(spacing: 0) {
                    // Logo section (35% of screen)
                    LogoSection()
                        .frame(height: geometry.size.height * 0.35)

                    // Full-width bottom-anchored sheet
                    VStack(spacing: 0) {
                        FormContent(viewModel: viewModel)
                            .padding(24)
                            .padding(.bottom, geometry.safeAreaInsets.bottom)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: geometry.size.height * 0.65, alignment: .top)
                    .background(
                        UnevenRoundedRectangle(
                            topLeadingRadius: 28,
                            topTrailingRadius: 28
                        )
                        .fill(Color(.systemBackground))
                        .shadow(color: .black.opacity(0.1), radius: 20, x: 0, y: -10)
                        .ignoresSafeArea(.all, edges: .bottom)
                    )
                }
            }
        }
        .onChange(of: viewModel.isVerified) { _, isVerified in
            // Navigate to login when server is verified
            if isVerified {
                navigator.navigate(to: .login)
            }
        }
    }
}

// MARK: - Logo Section

private struct LogoSection: View {
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(spacing: 16) {
            // Logo icon - uses white logo on dark gradient
            Image("listenup_logo_white")
                .resizable()
                .scaledToFit()
                .frame(width: 190, height: 190)
        }
    }
}

// MARK: - Form Content

private struct FormContent: View {
    @Bindable var viewModel: ServerConnectViewModelWrapper

    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            // Title
            Text("Connect to Server")
                .font(.largeTitle.bold())
                .foregroundStyle(.primary)

            Text("Enter your ListenUp server URL to get started")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            // Server URL input
            GlassTextField(
                label: "Server URL",
                placeholder: "example.com or 10.0.2.2:8080",
                text: $viewModel.serverUrl,
                error: viewModel.error,
                keyboardType: .URL,
                textContentType: .URL,
                onSubmit: {
                    if viewModel.isConnectEnabled {
                        viewModel.onConnectClicked()
                    }
                }
            )
            .onChange(of: viewModel.serverUrl) { _, newValue in
                // Notify ViewModel of text changes for validation
                viewModel.onUrlChanged(newValue)
            }

            Spacer()

            // Connect button
            ListenUpButton(
                title: "Connect",
                isLoading: viewModel.isLoading,
                action: viewModel.onConnectClicked
            )
            .disabled(!viewModel.isConnectEnabled)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Previews

#Preview("Server Setup - Default") {
    ServerSetupView()
}

#Preview("Server Setup - Dark Mode") {
    ServerSetupView()
        .preferredColorScheme(.dark)
}
