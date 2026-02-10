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
            Section(NSLocalizedString("settings.interface", comment: "")) {
                Toggle(NSLocalizedString("settings.use_bookshelf_view", comment: ""), isOn: $useBookshelfView)
                Toggle(NSLocalizedString("settings.lock_orientation", comment: ""), isOn: $lockOrientation)

                HStack {
                    Text(NSLocalizedString("settings.theme_label", comment: ""))
                    Spacer()
                    Picker(NSLocalizedString("settings.theme_label", comment: ""), selection: $darkMode) {
                        Image(systemName: "sun.max.fill").tag(false)
                        Image(systemName: "moon.fill").tag(true)
                    }
                    .pickerStyle(.segmented)
                    .frame(width: 100)
                }

                HStack {
                    Text(NSLocalizedString("settings.haptic_feedback", comment: ""))
                    Spacer()
                    Text(NSLocalizedString("settings.haptic_light", comment: ""))
                        .foregroundStyle(.secondary)
                }

                HStack {
                    Text(NSLocalizedString("settings.language", comment: ""))
                    Spacer()
                    Text(NSLocalizedString("settings.language_english", comment: ""))
                        .foregroundStyle(.secondary)
                }
            }

            // Playback section
            Section(NSLocalizedString("settings.playback", comment: "")) {
                Toggle(NSLocalizedString("settings.auto_rewind_label", comment: ""), isOn: $autoRewind)

                HStack {
                    Text(NSLocalizedString("settings.jump_forward", comment: ""))
                    Spacer()
                    Image(systemName: "goforward.10")
                        .foregroundStyle(.secondary)
                }

                HStack {
                    Text(NSLocalizedString("settings.jump_backward", comment: ""))
                    Spacer()
                    Image(systemName: "gobackward.10")
                        .foregroundStyle(.secondary)
                }
            }

            // Server section
            Section(NSLocalizedString("settings.server_section", comment: "")) {
                HStack {
                    Text(NSLocalizedString("settings.connected_to", comment: ""))
                    Spacer()
                    Text("myserver.local")
                        .foregroundStyle(.secondary)
                }

                Button(NSLocalizedString("common.disconnect", comment: ""), role: .destructive) {
                    // TODO: Handle disconnect
                }
            }

            // Account section
            Section(NSLocalizedString("settings.account_section", comment: "")) {
                Button(NSLocalizedString("common.sign_out", comment: ""), role: .destructive) {
                    // TODO: Handle sign out
                }
            }

            // About section
            Section(NSLocalizedString("settings.about_section", comment: "")) {
                HStack {
                    Text(NSLocalizedString("common.version", comment: ""))
                    Spacer()
                    Text("1.0.0")
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle(NSLocalizedString("settings.title", comment: ""))
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        SettingsView()
    }
}
