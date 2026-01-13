import SwiftUI
import Shared

/// Manual server URL entry screen.
///
/// When server is verified, AuthState updates automatically.
/// No onServerVerified callback needed.
struct ServerManualEntryView: View {

    // MARK: - State

    @State private var viewModel: ServerConnectViewModelWrapper

    // MARK: - Navigation

    var onBack: (() -> Void)?

    // MARK: - Initialization

    init(onBack: (() -> Void)? = nil) {
        self.onBack = onBack
        _viewModel = State(initialValue: ServerConnectViewModelWrapper(
            viewModel: Dependencies.shared.serverConnectViewModel
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
            serverUrlField
            connectButton
            backLink
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Add Server")
                .font(.largeTitle.bold())

            Text("Enter your ListenUp server URL")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    private var serverUrlField: some View {
        GlassTextField(
            label: "Server URL",
            placeholder: "example.com or 192.168.1.100:8080",
            text: Binding(
                get: { viewModel.serverUrl },
                set: { viewModel.onUrlChanged($0) }
            ),
            error: viewModel.error,
            keyboardType: .URL,
            textContentType: .URL,
            onSubmit: {
                if viewModel.isConnectEnabled {
                    viewModel.onConnectClicked()
                }
            }
        )
    }

    private var connectButton: some View {
        ListenUpButton(
            title: "Connect",
            isLoading: viewModel.isLoading
        ) {
            viewModel.onConnectClicked()
        }
        .disabled(!viewModel.isConnectEnabled)
    }

    private var backLink: some View {
        Button {
            onBack?()
        } label: {
            HStack(spacing: 4) {
                Image(systemName: "chevron.left")
                    .font(.subheadline)
                Text("Back to Server List")
                    .font(.subheadline)
            }
            .foregroundStyle(Color.listenUpOrange)
        }
        .buttonStyle(.plain)
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Previews

#Preview("Manual Entry") {
    ServerManualEntryView()
}
