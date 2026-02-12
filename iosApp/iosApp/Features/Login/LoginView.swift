import SwiftUI
import Shared

/// Login screen with brand styling.
///
/// Navigation is handled by AuthState changes:
/// - On success: AuthState → .authenticated → App shows MainView
/// - Register: Environment action → AuthFlowCoordinator handles it
/// - Change server: Calls disconnectFromServer → AuthState → .needsServerUrl
///
/// No callbacks. State drives everything.
struct LoginView: View {

    // MARK: - Configuration

    let openRegistration: Bool

    // MARK: - Environment

    @Environment(\.navigateToRegister) private var navigateToRegister
    @Environment(\.dependencies) private var dependencies

    // MARK: - State

    @State private var viewModel: LoginViewModelWrapper
    @State private var email = ""
    @State private var password = ""

    // MARK: - Initialization

    init(openRegistration: Bool = false) {
        self.openRegistration = openRegistration
        // ViewModel initialized immediately, not in onAppear
        // Uses Dependencies.shared since @Environment isn't available in init
        _viewModel = State(initialValue: LoginViewModelWrapper(
            viewModel: Dependencies.shared.loginViewModel
        ))
    }

    // MARK: - Body

    var body: some View {
        AuthScreenLayout {
            formContent
        }
    }

    // MARK: - Form Content

    @ViewBuilder
    private var formContent: some View {
        VStack(alignment: .leading, spacing: 24) {
            header
            fields
            errorMessage
            signInButton
            footerLinks
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(String(localized: "auth.sign_in"))
                .font(.largeTitle.bold())

            Text(String(localized: "auth.sign_in_to_access_your"))
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    private var fields: some View {
        VStack(spacing: 16) {
            GlassTextField(
                label: String(localized: "common.email"),
                placeholder: String(localized: "auth.email_placeholder"),
                text: $email,
                error: viewModel.emailError,
                keyboardType: .emailAddress,
                textContentType: .emailAddress
            )

            GlassSecureField(
                label: String(localized: "auth.password_label"),
                placeholder: String(localized: "auth.password_placeholder"),
                text: $password,
                error: viewModel.passwordError
            )
        }
    }

    @ViewBuilder
    private var errorMessage: some View {
        if let error = viewModel.generalError {
            ErrorBanner(message: error)
        }
    }

    private var signInButton: some View {
        ListenUpButton(
            title: String(localized: "auth.sign_in"),
            isLoading: viewModel.isLoading
        ) {
            viewModel.login(email: email, password: password)
        }
        .disabled(email.isEmpty || password.isEmpty)
    }

    @ViewBuilder
    private var footerLinks: some View {
        VStack(spacing: 16) {
            if openRegistration {
                HStack(spacing: 4) {
                    Text(String(localized: "auth.dont_have_account"))
                        .foregroundStyle(.secondary)

                    Button(String(localized: "auth.sign_up")) {
                        navigateToRegister()
                    }
                    .fontWeight(.semibold)
                    .foregroundStyle(Color.listenUpOrange)
                    .buttonStyle(.plain)
                }
                .font(.subheadline)
            }

            Button(String(localized: "auth.change_server")) {
                Task {
                    try? await dependencies.serverConfig.disconnectFromServer()
                }
            }
            .font(.subheadline)
            .foregroundStyle(Color.listenUpOrange)
            .buttonStyle(.plain)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Error Banner

private struct ErrorBanner: View {
    let message: String

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill")
            Text(message)
        }
        .font(.subheadline)
        .foregroundStyle(.red)
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.red.opacity(0.1), in: RoundedRectangle(cornerRadius: 8))
    }
}

// MARK: - Auth Screen Layout

/// Reusable layout for auth screens: gradient + logo + bottom card.
/// Eliminates GeometryReader by using layout priorities.
struct AuthScreenLayout<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack(alignment: .bottom) {
            // Gradient fills entire screen
            Color.brandGradient
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Logo area - flexible, takes remaining space
                VStack {
                    Spacer()
                    Image("listenup_logo_white")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 190, height: 190)
                    Spacer()
                }
                .frame(maxHeight: .infinity)

                // Content card - sized to content, max 70%
                ScrollView {
                    content()
                        .padding(24)
                        .padding(.bottom, 16)
                }
                .frame(maxHeight: UIScreen.main.bounds.height * 0.7)
                .background(
                    UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28)
                        .fill(Color(.systemBackground))
                        .shadow(color: .black.opacity(0.1), radius: 20, y: -10)
                        .ignoresSafeArea(edges: .bottom)
                )
            }
        }
    }
}

// MARK: - Previews

#Preview("Login") {
    LoginView(openRegistration: true)
}

#Preview("Login - No Registration") {
    LoginView(openRegistration: false)
}
