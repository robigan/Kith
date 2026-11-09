import SwiftUI
import SharedLogic

enum AppTab: Hashable { case today, people, groups }

struct RootView: View {
    @State private var router = Router()
    @State private var app = Dex.shared.app()
    @State private var reminders = ReminderScheduler()
    @State private var tab: AppTab = .today
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    var body: some View {
        TabView(selection: $tab) {
            Tab("Today", systemImage: "sun.max", value: .today) { TodayView() }
            Tab("People", systemImage: "person.2", value: .people) { PeopleView() }
            Tab("Groups", systemImage: "square.grid.2x2", value: .groups) { GroupsView() }
        }
        .tint(Palette.accent)
        // Reduced Transparency: tab bar goes solid (NFR-011).
        .toolbarBackground(reduceTransparency ? .visible : .automatic, for: .tabBar)
        .toolbarBackground(Palette.card, for: .tabBar)
        .environment(router)
        .sheet(item: $router.log) { request in
            LogInteractionView(request: request)
                .sheetBackground(reduceTransparency: reduceTransparency)
        }
        .sheet(item: $router.editor) { request in
            ContactEditView(request: request)
        }
        .fullScreenCover(isPresented: $router.scanningCard) {
            BusinessCardScanner { lines in
                router.scanningCard = false
                guard let lines else { return }
                router.editContact(prefill: Dex.shared.editor().fromBusinessCard(lines: lines))
            }
            .ignoresSafeArea()
        }
        .task {
            try? await app.start()
            #if DEBUG
            // Launch with `-seedSampleData YES -initialTab people` to demo or screenshot quickly.
            if UserDefaults.standard.bool(forKey: "seedSampleData") { try? await app.loadSampleData() }
            switch UserDefaults.standard.string(forKey: "initialTab") {
            case "people": tab = .people
            case "groups": tab = .groups
            default: break
            }
            #endif
        }
        .task {
            for await planned in app.reminders {
                await reminders.sync(planned)
            }
        }
    }
}

extension View {
    /// Sheets are frosted glass, or solid under Reduce Transparency (NFR-011).
    func sheetBackground(reduceTransparency: Bool) -> some View {
        presentationBackground(reduceTransparency ? AnyShapeStyle(Palette.background) : AnyShapeStyle(.regularMaterial))
            .presentationDragIndicator(.visible)
            .presentationCornerRadius(28)
    }
}
