import SwiftUI

/// Settings screen.
///
/// Based on mockup showing:
/// - Interface settings (bookshelf view, orientation, theme, haptics, language)
/// - Playback settings (auto rewind, seeking, jump intervals)
/// - Other settings
struct SettingsView: View {
    @State private var useBookshelfView = false
    @State private var lockOrientation = false
    @State private var darkMode = false
    @State private var autoRewind = false

    var body: some View {
        List {
            // Interface section
            Section("Interface") {
                Toggle("Use Bookshelf View", isOn: $useBookshelfView)
                Toggle("Lock Orientation", isOn: $lockOrientation)

                HStack {
                    Text("Theme")
                    Spacer()
                    Picker("Theme", selection: $darkMode) {
                        Image(systemName: "sun.max.fill").tag(false)
                        Image(systemName: "moon.fill").tag(true)
                    }
                    .pickerStyle(.segmented)
                    .frame(width: 100)
                }

                HStack {
                    Text("Haptic Feedback")
                    Spacer()
                    Text("Light")
                        .foregroundStyle(.secondary)
                }

                HStack {
                    Text("Language")
                    Spacer()
                    Text("English")
                        .foregroundStyle(.secondary)
                }
            }

            // Playback section
            Section("Playback") {
                Toggle("Auto Rewind", isOn: $autoRewind)

                HStack {
                    Text("Jump Forward")
                    Spacer()
                    Image(systemName: "goforward.10")
                        .foregroundStyle(.secondary)
                }

                HStack {
                    Text("Jump Backward")
                    Spacer()
                    Image(systemName: "gobackward.10")
                        .foregroundStyle(.secondary)
                }
            }

            // Server section
            Section("Server") {
                HStack {
                    Text("Connected to")
                    Spacer()
                    Text("myserver.local")
                        .foregroundStyle(.secondary)
                }

                Button("Disconnect", role: .destructive) {
                    // TODO: Handle disconnect
                }
            }

            // Account section
            Section("Account") {
                Button("Sign Out", role: .destructive) {
                    // TODO: Handle sign out
                }
            }

            // About section
            Section("About") {
                HStack {
                    Text("Version")
                    Spacer()
                    Text("1.0.0")
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        SettingsView()
    }
}
