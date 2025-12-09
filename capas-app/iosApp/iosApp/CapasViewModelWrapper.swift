import Foundation
import Shared
import SwiftUI

@MainActor
class CapasViewModelWrapper: ObservableObject {
    private let viewModel: CapasViewModel
    @Published var state: CapasViewState = CapasViewState(capas: nil, removed: [], workflowStatus: nil)
    
    init() {
        self.viewModel = CapasViewModelHelper().viewModel
        self.viewModel.getCapas()
        
        // Observe the StateFlow
        let collector = Collector<CapasViewState> { [weak self] state in
            self?.state = state
        }
        
        // Launch a task to collect
        Task {
            do {
                try await viewModel.capasState.collect(collector: collector)
            } catch {
                print("Error collecting state: \(error)")
            }
        }
    }
    
    func removeCapa(_ capa: Capa) {
        viewModel.removeCapa(capa: capa)
    }
    
    func restoreCapa(_ capa: Capa) {
        viewModel.restoreCapa(capa: capa)
    }
    
    func getWorkflowStatus() {
        viewModel.getWorkflowStatus()
    }
}

class Collector<T>: Kotlinx_coroutines_coreFlowCollector {
    let callback: (T) -> Void
    
    init(callback: @escaping (T) -> Void) {
        self.callback = callback
    }
    
    func emit(value: Any?, completionHandler: @escaping (Error?) -> Void) {
        if let value = value as? T {
            DispatchQueue.main.async {
                self.callback(value)
            }
        }
        completionHandler(nil)
    }
}
