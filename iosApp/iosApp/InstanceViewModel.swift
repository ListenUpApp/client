import Foundation
import Shared
import Combine

/// UI state for the instance screen
struct InstanceUiState {
    var isLoading: Bool = true
    var instance: Instance? = nil
    var error: String? = nil
}

/// ViewModel for managing instance data and state
@MainActor
class InstanceViewModel: ObservableObject {
    @Published var state = InstanceUiState()

    private let getInstanceUseCase: GetInstanceUseCase

    init() {
        // Get use case from Koin via helper
        self.getInstanceUseCase = KoinHelper.shared.getInstanceUseCase()
        loadInstance()
    }

    func loadInstance() {
        state = InstanceUiState(isLoading: true)

        Task {
            do {
                let result = try await getInstanceUseCase.invoke(forceRefresh: false)

                if let success = result as? ResultSuccess<Instance> {
                    state = InstanceUiState(
                        isLoading: false,
                        instance: success.data
                    )
                } else if let failure = result as? ResultFailure {
                    state = InstanceUiState(
                        isLoading: false,
                        error: failure.message
                    )
                }
            } catch {
                state = InstanceUiState(
                    isLoading: false,
                    error: error.localizedDescription
                )
            }
        }
    }
}
