import SwiftUI
import TheRealDex

struct GroupDetailView: View {
    @State private var screen: Screen<GroupDetailViewModel, GroupDetailState>
    @State private var editing = false
    @State private var adding = false
    @State private var confirmDelete = false
    @Environment(\.dismiss) private var dismiss

    init(groupId: String) {
        _screen = State(initialValue: Screen(Dex.shared.group(groupId: groupId)) { $0.state })
    }

    private var state: GroupDetailState { screen.state }

    var body: some View {
        List {
            Section {
                LabeledContent("Keep in touch", value: state.cadenceLabel)
            } footer: {
                Text("Members without their own cadence follow this one. When someone is in several groups, the most frequent cadence wins.")
            }
            Section(peopleCount(state.members.count)) {
                ForEach(state.members, id: \.id) { row in
                    NavigationLink(value: Route.profile(row.id)) { PersonRowView(row: row) }
                        .swipeActions {
                            Button("Remove", role: .destructive) { screen.vm.removeMember(contactId: row.id) }
                        }
                }
                Button { adding = true } label: { Label("Add people", systemImage: "plus") }
                    .foregroundStyle(Palette.accent)
            }
        }
        .navigationTitle(state.name)
        .overlay { if !state.exists { ContentUnavailableView("Group deleted", systemImage: "trash") } }
        .toolbar {
            Menu {
                Button("Edit group", systemImage: "pencil") { editing = true }
                Button("Delete group", systemImage: "trash", role: .destructive) { confirmDelete = true }
            } label: {
                Image(systemName: "ellipsis.circle")
            }
        }
        .sheet(isPresented: $editing) {
            GroupSettingsSheet(title: "Edit group", name: state.name, cadence: state.cadence) { name, cadence in
                screen.vm.update(name: name, cadence: cadence)
            }
        }
        .sheet(isPresented: $adding) {
            ContactPicker(title: "Add people", excluded: Set(state.members.map(\.id))) { screen.vm.addMembers(contactIds: $0) }
        }
        .confirmationDialog("Delete \(state.name)?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete group", role: .destructive) {
                Task {
                    try? await screen.vm.delete()
                    dismiss()
                }
            }
        } message: {
            Text("People in the group are kept.")
        }
        .task { await screen.run() }
    }
}

struct SmartViewDetailView: View {
    @State private var screen: Screen<SmartViewViewModel, SmartViewState>
    @State private var groups = Screen(Dex.shared.groups()) { $0.state }
    @State private var editing: SmartViewInput?
    @Environment(\.dismiss) private var dismiss
    private let viewId: String

    init(viewId: String) {
        self.viewId = viewId
        _screen = State(initialValue: Screen(Dex.shared.smartView(viewId: viewId)) { $0.state })
    }

    private var state: SmartViewState { screen.state }

    var body: some View {
        List {
            Section {
                Text(state.criteria).font(.subheadline).foregroundStyle(Palette.muted)
            }
            Section(peopleCount(state.people.count)) {
                ForEach(state.people, id: \.id) { row in
                    NavigationLink(value: Route.profile(row.id)) { PersonRowView(row: row) }
                }
            }
        }
        .navigationTitle(state.name)
        .overlay { if !state.exists { ContentUnavailableView("Smart view deleted", systemImage: "trash") } }
        .toolbar {
            Menu {
                Button("Edit", systemImage: "slider.horizontal.3") {
                    Task { editing = try? await screen.vm.input() }
                }
                Button("Delete", systemImage: "trash", role: .destructive) {
                    Task {
                        try? await screen.vm.delete()
                        dismiss()
                    }
                }
            } label: {
                Image(systemName: "ellipsis.circle")
            }
        }
        .sheet(item: Binding(get: { editing.map(IdentifiedInput.init) }, set: { editing = $0?.input })) { item in
            SmartViewEditor(viewId: viewId, initial: item.input, groups: groups.state.groupOptions, tags: groups.state.tags, groupsModel: groups.vm)
        }
        .task { await screen.run() }
        .task { await groups.run() }
    }
}

private struct IdentifiedInput: Identifiable {
    let id = UUID()
    let input: SmartViewInput
}
