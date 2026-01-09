import SwiftUI
import Shared

/// Server selection screen showing discovered servers via mDNS.
///
/// When a server is selected and verified, the Kotlin layer updates AuthState,
/// which automatically transitions the app. No callback needed.
struct ServerSelectView: View {

    // MARK: - State

    @State private var viewModel: ServerSelectViewModelWrapper

    // MARK: - Navigation

    /// Only callback needed: navigate to manual entry within the server flow
    var onManualEntryRequested: (() -> Void)?

    // MARK: - Initialization

    init(onManualEntryRequested: (() -> Void)? = nil) {
        self.onManualEntryRequested = onManualEntryRequested
        _viewModel = State(initialValue: ServerSelectViewModelWrapper(
            viewModel: Dependencies.shared.serverSelectViewModel
        ))
    }

    // MARK: - Body

    var body: some View {
        AuthScreenLayout {
            content
        }
        .onAppear {
            // Wire up the manual entry callback
            viewModel.onManualEntryRequested = onManualEntryRequested
        }
    }

    // MARK: - Content

    @ViewBuilder
    private var content: some View {
        VStack(alignment: .leading, spacing: 24) {
            header
            serverList
        }
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("Select Server")
                    .font(.largeTitle.bold())

                Text("Choose a server from your network")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            refreshButton
        }
    }

    private var refreshButton: some View {
        Button {
            viewModel.refresh()
        } label: {
            if viewModel.isDiscovering {
                ProgressView()
                    .scaleEffect(0.8)
            } else {
                Image(systemName: "arrow.clockwise")
                    .font(.title3)
                    .foregroundStyle(Color.listenUpOrange)
            }
        }
        .disabled(viewModel.isDiscovering)
    }

    private var serverList: some View {
        ScrollView {
            VStack(spacing: 12) {
                if viewModel.servers.isEmpty {
                    EmptyDiscoveryState(isDiscovering: viewModel.isDiscovering)
                } else {
                    ForEach(viewModel.servers) { server in
                        ServerCard(
                            server: server,
                            isSelected: viewModel.selectedServerId == server.id,
                            isConnecting: viewModel.isConnecting && viewModel.selectedServerId == server.id
                        ) {
                            viewModel.selectServer(server)
                        }
                    }
                }

                ManualEntryCard {
                    viewModel.requestManualEntry()
                }
            }
        }
    }
}

// MARK: - Server Card

private struct ServerCard: View {
    let server: DiscoveredServerItem
    let isSelected: Bool
    let isConnecting: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                Circle()
                    .fill(server.isOnline ? Color.green : Color.secondary)
                    .frame(width: 8, height: 8)

                VStack(alignment: .leading, spacing: 2) {
                    Text(server.name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)

                    HStack(spacing: 8) {
                        Text(server.isOnline ? "Online" : "Offline")
                            .font(.caption)
                            .foregroundStyle(.secondary)

                        if server.version != "unknown" {
                            Text("v\(server.version)")
                                .font(.caption)
                                .foregroundStyle(.tertiary)
                        }
                    }
                }

                Spacer()

                statusIndicator
            }
            .padding(16)
            .background {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(isSelected ? Color.listenUpOrange.opacity(0.1) : Color(.secondarySystemBackground))
                    .overlay {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(
                                isSelected ? Color.listenUpOrange.opacity(0.3) : Color.clear,
                                lineWidth: 1
                            )
                    }
            }
        }
        .buttonStyle(.plain)
        .disabled(isConnecting)
    }

    @ViewBuilder
    private var statusIndicator: some View {
        if isConnecting {
            ProgressView()
                .scaleEffect(0.8)
        } else if isSelected {
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(Color.listenUpOrange)
        } else {
            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
    }
}

// MARK: - Empty Discovery State

private struct EmptyDiscoveryState: View {
    let isDiscovering: Bool

    var body: some View {
        VStack(spacing: 8) {
            if isDiscovering {
                HStack(spacing: 8) {
                    ProgressView()
                        .scaleEffect(0.8)
                    Text("Searching for servers...")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            } else {
                Text("No servers found")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Text("Make sure your ListenUp server is running on the same network")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
        .background {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color(.secondarySystemBackground))
        }
    }
}

// MARK: - Manual Entry Card

private struct ManualEntryCard: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                Image(systemName: "plus")
                    .font(.title3)
                    .foregroundStyle(Color.listenUpOrange)

                VStack(alignment: .leading, spacing: 2) {
                    Text("Add Server Manually")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)

                    Text("Enter server URL directly")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
            .padding(16)
            .background {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color(.tertiarySystemBackground))
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Previews

#Preview("Server Select") {
    ServerSelectView()
}
