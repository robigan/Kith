import SwiftUI
import SharedLogic
import UniformTypeIdentifiers

/// Groups tab: groups, smart views and sources (FR-050…FR-055).
struct GroupsView: View {
    @State private var screen = Screen(Dex.shared.groups()) { $0.state }
    @State private var newGroup = false
    @State private var newSmartView = false
    @State private var importingLinkedIn = false
    @State private var syncing: SourceProvider?
    @State private var message: String?

    private var state: GroupsState { screen.state }
    private var vm: GroupsViewModel { screen.vm }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if state.groups.isEmpty {
                        Text("Group people you want to keep up with at the same rhythm.").foregroundStyle(Palette.muted)
                    }
                    ForEach(state.groups, id: \.id) { group in
                        NavigationLink(value: Route.group(group.id)) { GroupRowView(group: group) }
                            .swipeActions {
                                Button("Delete", role: .destructive) { vm.deleteGroup(id: group.id) }
                            }
                    }
                }

                Section {
                    ForEach(state.views, id: \.id) { view in
                        NavigationLink(value: Route.smartView(view.id)) {
                            HStack(spacing: 12) {
                                CodeBadge(code: view.code, tint: view.tint)
                                Text(view.name).foregroundStyle(Palette.ink)
                                Spacer()
                                Text("\(view.count)").foregroundStyle(Palette.muted)
                            }
                        }
                        .swipeActions {
                            Button("Delete", role: .destructive) { vm.deleteSmartView(id: view.id) }
                        }
                    }
                    Button { newSmartView = true } label: { Label("New smart view", systemImage: "plus") }
                        .foregroundStyle(Palette.accent)
                } header: {
                    SectionTitle(title: "Smart views")
                } footer: {
                    Text("A smart view is a saved filter on tags, groups, last contact, location or source.")
                }

                Section {
                    ForEach(state.sources, id: \.provider) { source in
                        sourceRow(source)
                    }
                } header: {
                    SectionTitle(title: "Sources")
                } footer: {
                    Text("LinkedIn imports the Connections.csv from LinkedIn's “Get a copy of your data”. Google Calendar syncs through the calendar accounts on this iPhone.")
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(Palette.background)
            .navigationTitle("Groups")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { newGroup = true } label: { Image(systemName: "plus") }
                        .accessibilityLabel("New group")
                }
            }
            .routeDestinations()
            .sheet(isPresented: $newGroup) {
                GroupSettingsSheet(title: "New group", name: "", cadence: .monthly) { name, cadence in
                    Task { _ = try? await vm.createGroup(name: name, cadence: cadence) }
                }
            }
            .sheet(isPresented: $newSmartView) {
                SmartViewEditor(viewId: nil, initial: nil, groups: state.groupOptions, tags: state.tags, groupsModel: vm)
            }
            .fileImporter(isPresented: $importingLinkedIn, allowedContentTypes: [.commaSeparatedText, .plainText]) { result in
                importLinkedIn(result)
            }
            .alert("Sources", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
                Button("OK") { message = nil }
            } message: {
                Text(message ?? "")
            }
        }
        .task { await screen.run() }
    }

    @ViewBuilder
    private func sourceRow(_ source: SourceRow) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(source.name).foregroundStyle(Palette.ink)
                if !source.status.isEmpty {
                    Text(source.status).font(.subheadline).foregroundStyle(source.isConnected ? Palette.success : Palette.muted)
                }
            }
            Spacer()
            if syncing == source.provider {
                ProgressView()
            } else if !source.isAvailable {
                EmptyView()
            } else if source.isConnected {
                Menu {
                    Button(source.provider == .linkedin ? "Import new export" : "Sync now", systemImage: "arrow.triangle.2.circlepath") { sync(source.provider) }
                    Button("Disconnect", systemImage: "xmark.circle", role: .destructive) {
                        Task { try? await Dex.shared.sources.setConnected(provider: source.provider, connected: false) }
                    }
                } label: {
                    Image(systemName: "ellipsis.circle").font(.title3).foregroundStyle(Palette.accent)
                }
                .accessibilityLabel("\(source.name) options")
            } else {
                Button("Connect") { sync(source.provider) }
                    .font(.subheadline.weight(.semibold))
                    .buttonStyle(.bordered)
                    .tint(Palette.accent)
            }
        }
    }

    private func sync(_ provider: SourceProvider) {
        switch provider {
        case .linkedin:
            importingLinkedIn = true
        case .contacts:
            run(provider) { try await ContactsImporter.importAll() }
        case .calendar:
            run(provider) { try await CalendarImporter.importRecent() }
        case .gmail:
            break
        }
    }

    private func run(_ provider: SourceProvider, _ work: @escaping () async throws -> ImportResult) {
        syncing = provider
        Task {
            do {
                message = "\(provider.label): \(try await work().summary)"
            } catch {
                message = error.localizedDescription
            }
            syncing = nil
        }
    }

    private func importLinkedIn(_ result: Result<URL, Error>) {
        guard case .success(let url) = result else { return }
        run(.linkedin) {
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            let csv = try String(contentsOf: url, encoding: .utf8)
            return try await Dex.shared.sources.importLinkedIn(csv: csv)
        }
    }
}

private struct GroupRowView: View {
    let group: GroupRow

    var body: some View {
        HStack(spacing: 12) {
            FacesView(faces: group.faces)
            VStack(alignment: .leading, spacing: 2) {
                Text(group.name).font(.headline).foregroundStyle(Palette.ink)
                Text("\(peopleCount(Int(group.count))) · \(group.cadenceLabel)").font(.subheadline).foregroundStyle(Palette.muted)
            }
            Spacer()
            if group.dueCount > 0 {
                Text("\(group.dueCount) due")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Palette.accent)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Palette.accentSoft, in: Capsule())
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}

/// Name and cadence for a new or existing group.
struct GroupSettingsSheet: View {
    let title: String
    @State var name: String
    @State var cadence: Cadence?
    let onSave: (String, Cadence?) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                TextField("Name", text: $name)
                Picker("Keep in touch", selection: $cadence) {
                    Text("No cadence").tag(Cadence?.none)
                    ForEach(Cadence.allCases.filter { $0 != .never }, id: \.self) { Text($0.label).tag(Cadence?.some($0)) }
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        onSave(name, cadence)
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
        .presentationDetents([.medium])
    }
}
