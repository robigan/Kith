import SwiftUI
import TheRealDex

enum Route: Hashable {
    case profile(String)
    case group(String)
    case smartView(String)
}

struct LogRequest: Identifiable {
    let id = UUID()
    let contactIds: [String]
    var interactionId: String? = nil
}

struct EditorRequest: Identifiable {
    let id = UUID()
    var contactId: String? = nil
    var prefill: ContactInput? = nil
}

/// App-wide modal presentation: the Log interaction sheet and the contact editor can be opened from any tab.
@MainActor
@Observable
final class Router {
    var log: LogRequest?
    var editor: EditorRequest?
    var scanningCard = false

    func logInteraction(with contactIds: [String], completing interactionId: String? = nil) {
        log = LogRequest(contactIds: contactIds, interactionId: interactionId)
    }

    func editContact(_ contactId: String? = nil, prefill: ContactInput? = nil) {
        editor = EditorRequest(contactId: contactId, prefill: prefill)
    }
}

extension View {
    func routeDestinations() -> some View {
        navigationDestination(for: Route.self) { route in
            switch route {
            case .profile(let id): ProfileView(contactId: id)
            case .group(let id): GroupDetailView(groupId: id)
            case .smartView(let id): SmartViewDetailView(viewId: id)
            }
        }
    }
}
