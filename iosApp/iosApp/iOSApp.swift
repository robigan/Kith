import SwiftUI
import SharedLogic

@main
struct iOSApp: App {
    init() {
        Dex.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}
