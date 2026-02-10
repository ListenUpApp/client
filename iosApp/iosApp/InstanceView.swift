import SwiftUI
import Shared

/// Screen that displays server instance information
struct InstanceView: View {
    @StateObject private var viewModel = InstanceViewModel()

    var body: some View {
        ZStack {
            Color(.systemGroupedBackground)
                .ignoresSafeArea()

            if viewModel.state.isLoading {
                ProgressView()
            } else if let error = viewModel.state.error {
                ErrorContent(error: error) {
                    viewModel.loadInstance()
                }
            } else if let instance = viewModel.state.instance {
                InstanceContent(instance: instance)
            }
        }
    }
}

/// Displays the instance information in a card-like layout
struct InstanceContent: View {
    let instance: Instance

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(NSLocalizedString("instance.listenup_server", comment: ""))
                    .font(.title)
                    .fontWeight(.semibold)

                Divider()

                InfoRow(label: NSLocalizedString("instance.instance_id", comment: ""), value: instance.id as! String)
                InfoRow(
                    label: NSLocalizedString("instance.status", comment: ""),
                    value: instance.isReady ? NSLocalizedString("instance.ready", comment: "") : NSLocalizedString("instance.needs_setup", comment: "")
                )
                InfoRow(
                    label: NSLocalizedString("instance.has_root_user", comment: ""),
                    value: instance.hasRootUser ? NSLocalizedString("common.yes", comment: "") : NSLocalizedString("common.no", comment: "")
                )

                if instance.needsSetup {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(NSLocalizedString("instance.server_needs_setup", comment: ""))
                            .font(.body)
                            .foregroundColor(.primary)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.blue.opacity(0.1))
                    .cornerRadius(8)
                }
            }
            .padding(24)
            .background(Color(.systemBackground))
            .cornerRadius(12)
            .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
            .padding()
        }
    }
}

/// Displays a label-value pair in a row
struct InfoRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .font(.body)
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .font(.body)
        }
    }
}

/// Displays error state with retry button
struct ErrorContent: View {
    let error: String
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Text(NSLocalizedString("common.error", comment: ""))
                .font(.title2)
                .fontWeight(.semibold)

            Text(error)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            Button(action: onRetry) {
                Text(NSLocalizedString("common.retry", comment: ""))
                    .fontWeight(.medium)
                    .foregroundColor(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Color.blue)
                    .cornerRadius(8)
            }
        }
        .padding(24)
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
        .padding()
    }
}

struct InstanceView_Previews: PreviewProvider {
    static var previews: some View {
        InstanceView()
    }
}
