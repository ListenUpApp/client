import SwiftUI

/// Login screen placeholder.
///
/// TODO: Implement once Android LoginScreen is complete.
/// Will feature:
/// - Username/password fields with glass aesthetic
/// - Login button
/// - "Keep me signed in" checkbox
/// - "Forgot password?" link
/// - "Sign Up" link at bottom
struct LoginView: View {
    var body: some View {
        ZStack {
            // Gradient background
            LinearGradient(
                colors: [
                    Color(hex: "8B3A3A"),
                    Color(hex: "E8704A")
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            GlassCard {
                VStack(spacing: 24) {
                    Text("Login")
                        .font(.largeTitle.bold())

                    Text("Enter the required information to login")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)

                    Spacer()

                    // Placeholder
                    VStack(spacing: 16) {
                        Image(systemName: "person.circle")
                            .font(.system(size: 64))
                            .foregroundStyle(.secondary)

                        Text("Login Screen")
                            .font(.title2.bold())

                        Text("To be implemented")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    Spacer()
                }
                .frame(maxWidth: .infinity)
                .frame(height: 500)
            }
            .padding()
        }
    }
}

#Preview {
    LoginView()
}

#Preview("Dark Mode") {
    LoginView()
        .preferredColorScheme(.dark)
}
