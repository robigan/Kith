import SwiftUI
import SharedLogic

/// People tab (FR-020…FR-026).
struct PeopleView: View {
    @State private var screen = Screen(Dex.shared.people()) { $0.state }
    @Environment(Router.self) private var router
    @State private var query = ""

    private var state: PeopleState { screen.state }

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                List {
                    Section {
                        filterChips
                            .listRowBackground(Color.clear)
                            .listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0))
                    }
                    ForEach(state.sections, id: \.letter) { section in
                        Section {
                            ForEach(section.rows, id: \.id) { row in
                                NavigationLink(value: Route.profile(row.id)) { PersonRowView(row: row) }
                                    .swipeActions(edge: .leading) {
                                        Button { router.logInteraction(with: [row.id]) } label: { Label("Log", systemImage: "square.and.pencil") }
                                            .tint(Palette.accent)
                                    }
                            }
                        } header: {
                            Text(section.letter).id(section.letter)
                        }
                    }
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
                .background(Palette.background)
                .overlay {
                    if state.totalCount > 0 && state.sections.isEmpty {
                        ContentUnavailableView.search(text: query)
                    } else if state.totalCount == 0 {
                        ContentUnavailableView("No people yet", systemImage: "person.crop.circle.badge.plus",
                                               description: Text("Add someone, scan a business card, or import from Groups → Sources."))
                    }
                }
                .overlay(alignment: .trailing) {
                    if query.isEmpty && state.letters.count > 1 {
                        SectionIndex(letters: state.letters) { letter in proxy.scrollTo(letter, anchor: .top) }
                    }
                }
            }
            .navigationTitle("People")
            .searchable(text: $query, prompt: "Names, companies, notes")
            .onChange(of: query) { _, text in screen.vm.setQuery(text: text) }
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { router.scanningCard = true } label: { Image(systemName: "person.text.rectangle") }
                        .accessibilityLabel("Scan business card")
                    Button { router.editContact() } label: { Image(systemName: "plus") }
                        .accessibilityLabel("Add contact")
                }
            }
            .routeDestinations()
        }
        .task { await screen.run() }
    }

    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(state.filters, id: \.filter) { chip in
                    ChipButton(label: chip.label, detail: "\(chip.count)", isActive: chip.isActive) {
                        screen.vm.setFilter(value: chip.filter)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 4)
        }
        .accessibilityLabel("Filter")
    }
}

/// A–Z index along the trailing edge; tap or drag to jump (FR-023).
private struct SectionIndex: View {
    let letters: [String]
    let jump: (String) -> Void
    @State private var current: String?

    var body: some View {
        GeometryReader { geo in
            VStack(spacing: 1) {
                ForEach(letters, id: \.self) { letter in
                    Text(letter)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Palette.accent)
                        .frame(width: 20)
                }
            }
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0).onChanged { value in
                    let rowHeight = geo.size.height / CGFloat(max(letters.count, 1))
                    let index = min(max(Int((value.location.y - (geo.size.height - rowHeight * CGFloat(letters.count)) / 2) / rowHeight), 0), letters.count - 1)
                    let letter = letters[index]
                    if letter != current {
                        current = letter
                        jump(letter)
                    }
                }
                .onEnded { _ in current = nil }
            )
        }
        .frame(width: 20)
        .frame(maxHeight: CGFloat(letters.count) * 15)
        .padding(.trailing, 2)
        .sensoryFeedback(.selection, trigger: current)
        .accessibilityHidden(true)
    }
}

/// Multi-select person picker used by Log interaction ("Add person") and group membership.
struct ContactPicker: View {
    let title: String
    let excluded: Set<String>
    let onDone: ([String]) -> Void
    @State private var screen = Screen(Dex.shared.people()) { $0.state }
    @State private var query = ""
    @State private var selected: [String] = []
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                ForEach(screen.state.sections, id: \.letter) { section in
                    Section(section.letter) {
                        ForEach(section.rows.filter { !excluded.contains($0.id) }, id: \.id) { row in
                            Button {
                                if let index = selected.firstIndex(of: row.id) { selected.remove(at: index) } else { selected.append(row.id) }
                            } label: {
                                HStack {
                                    PersonRowView(row: row)
                                    Image(systemName: selected.contains(row.id) ? "checkmark.circle.fill" : "circle")
                                        .font(.title3)
                                        .foregroundStyle(selected.contains(row.id) ? Palette.accent : Palette.ring)
                                }
                            }
                            .accessibilityAddTraits(selected.contains(row.id) ? .isSelected : [])
                        }
                    }
                }
            }
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search people")
            .onChange(of: query) { _, text in screen.vm.setQuery(text: text) }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        onDone(selected)
                        dismiss()
                    }
                    .disabled(selected.isEmpty)
                }
            }
        }
        .task { await screen.run() }
    }
}
