import SwiftUI
import SharedLogic

/// Add / edit contact (FR-070…FR-072).
struct ContactEditView: View {
    let request: EditorRequest
    @State private var screen = Screen(Dex.shared.editor()) { $0.groups }
    @State private var form = ContactForm()
    @State private var loaded = false
    @State private var confirmDelete = false
    @Environment(\.dismiss) private var dismiss

    private var isNew: Bool { request.contactId == nil }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Name", text: $form.name).textContentType(.name)
                    TextField("Role", text: $form.role).textContentType(.jobTitle)
                    TextField("Company", text: $form.company).textContentType(.organizationName)
                    TextField("Location", text: $form.location).textContentType(.addressCity)
                    TextField("Relationship (e.g. College roommate)", text: $form.relationship)
                    Toggle("Starred", isOn: $form.starred)
                }

                Section("Keep in touch") {
                    Picker("Cadence", selection: $form.cadence) {
                        Text("Group default").tag(Cadence?.none)
                        ForEach(Cadence.allCases, id: \.self) { Text($0.label).tag(Cadence?.some($0)) }
                    }
                    if !screen.state.isEmpty {
                        ForEach(screen.state, id: \.id) { group in
                            Toggle(group.name, isOn: Binding(
                                get: { form.groupIds.contains(group.id) },
                                set: { on in
                                    if on { form.groupIds.append(group.id) } else { form.groupIds.removeAll { $0 == group.id } }
                                }
                            ))
                        }
                    }
                    TextField("Tags, comma separated (e.g. work, family)", text: $form.tags)
                        .textInputAutocapitalization(.never)
                }

                Section("Contact") {
                    TextField("Email", text: $form.email)
                        .textContentType(.emailAddress)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                    TextField("Phone", text: $form.phone).textContentType(.telephoneNumber).keyboardType(.phonePad)
                }

                Section("How we met") {
                    TextField("Where and how", text: $form.howWeMet, axis: .vertical)
                    Toggle("Date we met", isOn: $form.hasMetOn)
                    if form.hasMetOn {
                        DatePicker("Met on", selection: $form.metOn, in: ...Date(), displayedComponents: .date)
                    }
                }

                Section("Dates") {
                    Toggle("Birthday", isOn: $form.hasBirthday)
                    if form.hasBirthday {
                        DatePicker("Birthday", selection: $form.birthday, displayedComponents: .date)
                        Toggle("Year is known", isOn: $form.birthdayYearKnown)
                    }
                }

                Section("Family") {
                    TextField("Partner, kids, siblings…", text: $form.family, axis: .vertical)
                }

                Section("Gift ideas") {
                    ForEach($form.gifts) { $gift in
                        TextField("Idea", text: $gift.text)
                    }
                    .onDelete { form.gifts.remove(atOffsets: $0) }
                    Button { form.gifts.append(ContactForm.Gift(text: "")) } label: { Label("Add gift idea", systemImage: "plus") }
                }

                Section("Work history") {
                    TextField("Previous companies and roles", text: $form.workHistory, axis: .vertical)
                }

                if !isNew {
                    Section {
                        Button("Delete contact", role: .destructive) { confirmDelete = true }
                    }
                }
            }
            .navigationTitle(isNew ? "New contact" : "Edit contact")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: save)
                        .fontWeight(.semibold)
                        .disabled(form.name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .confirmationDialog("Delete \(form.name)?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Delete contact", role: .destructive) {
                    guard let id = request.contactId else { return }
                    Task {
                        try? await screen.vm.delete(contactId: id)
                        dismiss()
                    }
                }
            } message: {
                Text("Their notes and interaction history will be removed.")
            }
        }
        .task { await screen.run() }
        .task {
            guard !loaded else { return }
            loaded = true
            if let id = request.contactId, let input = try? await screen.vm.load(contactId: id) {
                form = ContactForm(input)
            } else if let prefill = request.prefill {
                form = ContactForm(prefill)
            }
        }
    }

    private func save() {
        let input = form.input
        Task {
            _ = try? await screen.vm.save(contactId: request.contactId, input: input)
            dismiss()
        }
    }
}

/// Swift-side form state, converted to the shared `ContactInput` on save.
struct ContactForm {
    struct Gift: Identifiable { let id = UUID(); var text: String }

    var name = ""
    var role = ""
    var company = ""
    var location = ""
    var relationship = ""
    var howWeMet = ""
    var hasMetOn = false
    var metOn = Date()
    var family = ""
    var hasBirthday = false
    var birthday = Date()
    var birthdayYearKnown = false
    var workHistory = ""
    var email = ""
    var phone = ""
    var cadence: Cadence?
    var tags = ""
    var gifts: [Gift] = []
    var groupIds: [String] = []
    var starred = false

    init() {}

    init(_ input: ContactInput) {
        name = input.name
        role = input.role
        company = input.company
        location = input.location
        relationship = input.relationship
        howWeMet = input.howWeMet
        if let met = input.metOnEpochDay {
            hasMetOn = true
            metOn = EpochDay.date(met.int64Value)
        }
        family = input.family
        if input.birthdayMonth > 0 && input.birthdayDay > 0 {
            hasBirthday = true
            birthdayYearKnown = input.birthdayYear > 0
            let year = input.birthdayYear > 0 ? Int(input.birthdayYear) : 2000
            birthday = Calendar.current.date(from: DateComponents(year: year, month: Int(input.birthdayMonth), day: Int(input.birthdayDay))) ?? Date()
        }
        workHistory = input.workHistory
        email = input.email
        phone = input.phone
        cadence = input.cadence
        tags = input.tags.joined(separator: ", ")
        gifts = input.giftIdeas.map { Gift(text: $0) }
        groupIds = input.groupIds
        starred = input.starred
    }

    var input: ContactInput {
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: birthday)
        return ContactInput(
            name: name,
            role: role,
            company: company,
            location: location,
            relationship: relationship,
            howWeMet: howWeMet,
            metOnEpochDay: hasMetOn ? KotlinLong(value: EpochDay.from(metOn)) : nil,
            family: family,
            birthdayMonth: hasBirthday ? Int32(parts.month ?? 0) : 0,
            birthdayDay: hasBirthday ? Int32(parts.day ?? 0) : 0,
            birthdayYear: hasBirthday && birthdayYearKnown ? Int32(parts.year ?? 0) : 0,
            workHistory: workHistory,
            email: email,
            phone: phone,
            cadence: cadence,
            tags: tags.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty },
            giftIdeas: gifts.map(\.text),
            groupIds: groupIds,
            starred: starred
        )
    }
}
