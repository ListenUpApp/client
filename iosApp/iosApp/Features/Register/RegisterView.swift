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
            Text("Create Account")
                .font(.largeTitle.bold())

            Text("Join ListenUp to start listening")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    private var fields: some View {
        VStack(spacing: 14) {
            HStack(spacing: 12) {
                GlassTextField(
                    label: "First Name",
                    placeholder: "First",
                    text: $firstName,
                    textContentType: .givenName,
                    autocapitalization: .words
                )

                GlassTextField(
                    label: "Last Name",
                    placeholder: "Last",
                    text: $lastName,
                    textContentType: .familyName,
                    autocapitalization: .words
                )
            }

            GlassTextField(
                label: "Email",
                placeholder: "Enter your email",
                text: $email,
                keyboardType: .emailAddress,
                textContentType: .emailAddress
            )

            GlassSecureField(
                label: "Password",
                placeholder: "Create a password",
                text: $password
            )

            GlassSecureField(
                label: "Confirm Password",
                placeholder: "Confirm your password",
                text: $confirmPassword,
                error: passwordMismatch ? "Passwords don't match" : nil
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
            title: "Create Account",
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
            Text("Already have an account?")
                .foregroundStyle(.secondary)

            Button("Sign In") {
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
