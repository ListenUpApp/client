import SwiftUI
import Shared

/// Registration screen with brand styling.
///
/// On success, AuthState transitions automatically (either to .authenticated
/// or .pendingApproval depending on server config). No callback needed.
struct RegisterView: View {

    // MARK: - Environment

    @Environment(\.navigateBack) private var navigateBack

    // MARK: - State

    @State private var viewModel: RegisterViewModelWrapper
    @State private var firstName = ""
    @State private var lastName = ""
    @State private var email = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var passwordMismatch = false

    // MARK: - Initialization

    init() {
        _viewModel = State(initialValue: RegisterViewModelWrapper(
            viewModel: Dependencies.shared.registerViewModel
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
        VStack(alignment: .leading, spacing: 20) {
            header
            fields
            errorMessage
            registerButton
            loginLink
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(NSLocalizedString("auth.create_account", comment: ""))
                .font(.largeTitle.bold())

            Text(NSLocalizedString("auth.join_listenup", comment: ""))
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    private var fields: some View {
        VStack(spacing: 14) {
            HStack(spacing: 12) {
                GlassTextField(
                    label: NSLocalizedString("auth.first_name", comment: ""),
                    placeholder: NSLocalizedString("auth.first_name_placeholder", comment: ""),
                    text: $firstName,
                    textContentType: .givenName,
                    autocapitalization: .words
                )

                GlassTextField(
                    label: NSLocalizedString("auth.last_name", comment: ""),
                    placeholder: NSLocalizedString("auth.last_name_placeholder", comment: ""),
                    text: $lastName,
                    textContentType: .familyName,
                    autocapitalization: .words
                )
            }

            GlassTextField(
                label: NSLocalizedString("common.email", comment: ""),
                placeholder: NSLocalizedString("auth.email_placeholder", comment: ""),
                text: $email,
                keyboardType: .emailAddress,
                textContentType: .emailAddress
            )

            GlassSecureField(
                label: NSLocalizedString("auth.password_label", comment: ""),
                placeholder: NSLocalizedString("auth.create_password_placeholder", comment: ""),
                text: $password
            )

            GlassSecureField(
                label: NSLocalizedString("auth.confirm_password", comment: ""),
                placeholder: NSLocalizedString("auth.confirm_password_placeholder", comment: ""),
                text: $confirmPassword,
                error: passwordMismatch ? NSLocalizedString("auth.passwords_dont_match", comment: "") : nil
            )
            .onChange(of: confirmPassword) { _, newValue in
                passwordMismatch = !newValue.isEmpty && newValue != password
            }
            .onChange(of: password) { _, newValue in
                passwordMismatch = !confirmPassword.isEmpty && confirmPassword != newValue
            }
        }
    }

    @ViewBuilder
    private var errorMessage: some View {
        if let error = viewModel.error {
            ErrorBanner(message: error)
        }
    }

    private var registerButton: some View {
        ListenUpButton(
            title: NSLocalizedString("auth.create_account", comment: ""),
            isLoading: viewModel.isLoading
        ) {
            if validateForm() {
                viewModel.register(
                    email: email,
                    password: password,
                    firstName: firstName,
                    lastName: lastName
                )
            }
        }
        .disabled(!isFormValid)
    }

    private var loginLink: some View {
        HStack(spacing: 4) {
            Text(NSLocalizedString("auth.already_have_account", comment: ""))
                .foregroundStyle(.secondary)

            Button(NSLocalizedString("auth.sign_in", comment: "")) {
                navigateBack()
            }
            .fontWeight(.semibold)
            .foregroundStyle(Color.listenUpOrange)
            .buttonStyle(.plain)
        }
        .font(.subheadline)
        .frame(maxWidth: .infinity)
    }

    // MARK: - Validation

    private var isFormValid: Bool {
        !firstName.isEmpty &&
        !lastName.isEmpty &&
        !email.isEmpty &&
        !password.isEmpty &&
        !confirmPassword.isEmpty &&
        password == confirmPassword
    }

    private func validateForm() -> Bool {
        if password != confirmPassword {
            passwordMismatch = true
            return false
        }
        return isFormValid
    }
}

// MARK: - Error Banner (shared component, could be extracted)

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

// MARK: - Previews

#Preview("Register") {
    RegisterView()
}
