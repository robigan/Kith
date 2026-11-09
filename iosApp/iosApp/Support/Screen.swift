import Observation
import TheRealDex

/// Owns a shared Kotlin view model and mirrors its StateFlow into SwiftUI.
/// SKIE exposes the flow as an AsyncSequence; `run()` is driven by a view's `.task`, so collection stops with the view.
@MainActor
@Observable
final class Screen<VM: TheRealDex.ViewModel, State> {
    let vm: VM
    private(set) var state: State
    @ObservationIgnored private let flow: SkieSwiftStateFlow<State>

    init(_ vm: VM, state: (VM) -> SkieSwiftStateFlow<State>) {
        self.vm = vm
        let flow = state(vm)
        self.flow = flow
        self.state = flow.value
    }

    func run() async {
        for await value in flow {
            state = value
        }
    }

    deinit {
        vm.clear()
    }
}
