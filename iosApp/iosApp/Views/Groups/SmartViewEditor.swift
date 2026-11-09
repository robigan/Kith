import SwiftUI
import TheRealDex

/// Create or edit a smart view's criteria (FR-054). Every enabled criterion must match.
struct SmartViewEditor: View {
    let viewId: String?
    let groups: [GroupOption]
    let tags: [String]
    let groupsModel: GroupsViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var code: String
    @State private var tint: Tint
    @State private var tag: String?
    @State private var groupId: String?
    @State private var useMinDays: Bool
    @State private var minDays: Int
    @State private var location: String
    @State private var source: SourceProvider?
    @State private var birthdayThisMonth: Bool
    @State private var useChangedJobs: Bool
    @State private var changedJobsDays: Int
    @State private var metContext: String

    init(viewId: String?, initial: SmartViewInput?, groups: [GroupOption], tags: [String], groupsModel: GroupsViewModel) {
        self.viewId = viewId
        self.groups = groups
        self.tags = tags
        self.groupsModel = groupsModel
        _name = State(initialValue: initial?.name ?? "")
        _code = State(initialValue: initial?.code ?? "")
        _tint = State(initialValue: initial?.tint ?? .sky)
        _tag = State(initialValue: initial?.tag)
        _groupId = State(initialValue: initial?.groupId)
        _useMinDays = State(initialValue: initial?.minDaysSinceContact != nil)
        _minDays = State(initialValue: Int(initial?.minDaysSinceContact?.int32Value ?? 90))
        _location = State(initialValue: initial?.location ?? "")
        _source = State(initialValue: initial?.source)
        _birthdayThisMonth = State(initialValue: initial?.birthdayThisMonth ?? false)
        _useChangedJobs = State(initialValue: initial?.changedJobsWithinDays != nil)
        _changedJobsDays = State(initialValue: Int(initial?.changedJobsWithinDays?.int32Value ?? 60))
        _metContext = State(initialValue: initial?.metContext ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Name", text: $name)
                    TextField("Short code (up to 4 letters)", text: $code)
                        .textInputAutocapitalization(.characters)
                        .onChange(of: code) { _, value in code = String(value.uppercased().prefix(4)) }
                    Picker("Colour", selection: $tint) {
                        ForEach(Tint.allCases, id: \.self) { Text($0.name.capitalized).tag($0) }
                    }
                }
                Section("Criteria") {
                    Picker("Tag", selection: $tag) {
                        Text("Any").tag(String?.none)
                        ForEach(tags, id: \.self) { Text($0).tag(String?.some($0)) }
                    }
                    Picker("Group", selection: $groupId) {
                        Text("Any").tag(String?.none)
                        ForEach(groups, id: \.id) { Text($0.name).tag(String?.some($0.id)) }
                    }
                    Toggle("Not talked to recently", isOn: $useMinDays)
                    if useMinDays {
                        Stepper("For \(minDays)+ days", value: $minDays, in: 7...730, step: 7)
                    }
                    TextField("Lives in (city)", text: $location)
                    Picker("Source", selection: $source) {
                        Text("Any").tag(SourceProvider?.none)
                        ForEach(SourceProvider.allCases, id: \.self) { Text($0.label).tag(SourceProvider?.some($0)) }
                    }
                    Toggle("Birthday this month", isOn: $birthdayThisMonth)
                    Toggle("Changed jobs recently", isOn: $useChangedJobs)
                    if useChangedJobs {
                        Stepper("Within \(changedJobsDays) days", value: $changedJobsDays, in: 7...365, step: 7)
                    }
                    TextField("How we met contains…", text: $metContext)
                }
            }
            .navigationTitle(viewId == nil ? "New smart view" : "Edit smart view")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: save).disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }

    private func save() {
        let trimmed = { (s: String) -> String? in
            let t = s.trimmingCharacters(in: .whitespaces)
            return t.isEmpty ? nil : t
        }
        let input = SmartViewInput(
            name: name,
            code: trimmed(code) ?? String(name.filter(\.isLetter).prefix(3)).uppercased(),
            tint: tint,
            tag: tag,
            groupId: groupId,
            minDaysSinceContact: useMinDays ? KotlinInt(value: Int32(minDays)) : nil,
            location: trimmed(location),
            source: source,
            birthdayThisMonth: birthdayThisMonth,
            changedJobsWithinDays: useChangedJobs ? KotlinInt(value: Int32(changedJobsDays)) : nil,
            metContext: trimmed(metContext)
        )
        Task {
            _ = try? await groupsModel.saveSmartView(id: viewId, input: input)
            dismiss()
        }
    }
}
