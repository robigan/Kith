import SwiftUI
import TheRealDex

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
