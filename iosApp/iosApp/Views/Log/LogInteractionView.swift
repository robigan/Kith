import SwiftUI
import TheRealDex

/// Log interaction sheet (FR-040…FR-048).
struct LogInteractionView: View {
    @State private var screen: Screen<LogInteractionViewModel, LogState>
    @State private var note = ""
    @State private var followUp = ""
    @State private var pickingPeople = false
    @State private var saving = false
    @State private var dictation = Dictation()
    @FocusState private var noteFocused: Bool
    @Environment(\.dismiss) private var dismiss

    init(request: LogRequest) {
        _screen = State(initialValue: Screen(Dex.shared.log(contactIds: request.contactIds, interactionId: request.interactionId)) { $0.state })
    }

    private var state: LogState { screen.state }
    private var vm: LogInteractionViewModel { screen.vm }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    if let title = state.title {
                        Text(title).font(.headline).foregroundStyle(Palette.muted)
                    }
                    typePicker
                    participants
                    dateRow
                    noteEditor
                    nextReminder
                    followUps
                }
                .padding(16)
            }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle("Log interaction")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .fontWeight(.semibold)
                        .disabled(!state.canSave || saving)
                }
            }
            .sheet(isPresented: $pickingPeople) {
                ContactPicker(title: "Add people", excluded: Set(state.participants.map(\.id))) { ids in
                    ids.forEach { vm.addParticipant(id: $0) }
                }
            }
        }
        .task { await screen.run() }
        .onChange(of: note) { _, text in vm.setNote(note: text) }
        .onChange(of: dictation.transcript) { _, text in
            if let text { note = text }
        }
        .onDisappear { dictation.stop() }
    }

    private func save() {
        saving = true
        dictation.stop()
        Task {
            vm.setNote(note: note)
            let saved = (try? await vm.save())?.boolValue ?? false
            saving = false
            if saved { dismiss() }
        }
    }

    private var typePicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(state.types, id: \.self) { type in
                    Button { vm.setType(type: type) } label: {
                        HStack(spacing: 6) {
                            Image(systemName: type.symbol)
                            Text(type.label).fontWeight(.semibold)
                        }
                        .font(.subheadline)
                        .padding(.horizontal, 14)
                        .frame(minHeight: 40)
                        .foregroundStyle(type == state.type ? Color.white : Palette.ink)
                        .background(type == state.type ? Palette.accent : Palette.card, in: Capsule())
                    }
                    .buttonStyle(.pressable)
                    .accessibilityAddTraits(type == state.type ? .isSelected : [])
                }
            }
        }
        .accessibilityLabel("Type")
    }

    private var participants: some View {
        VStack(alignment: .leading, spacing: 8) {
            FieldLabel("With")
            FlowLayout(spacing: 8) {
                ForEach(state.participants, id: \.id) { person in
                    HStack(spacing: 6) {
                        AvatarView(avatar: person.avatar, size: 26)
                        Text(person.name).font(.subheadline.weight(.medium))
                        Button { vm.removeParticipant(id: person.id) } label: {
                            Image(systemName: "xmark").font(.caption2.weight(.bold)).foregroundStyle(Palette.muted)
                        }
                        .accessibilityLabel("Remove \(person.name)")
                    }
                    .padding(.leading, 4)
                    .padding(.trailing, 10)
                    .frame(minHeight: 34)
                    .background(Palette.card, in: Capsule())
                }
                Button { pickingPeople = true } label: {
                    Label("Add person", systemImage: "plus")
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 12)
                        .frame(minHeight: 34)
                        .foregroundStyle(Palette.accent)
                        .background(Palette.accentSoft, in: Capsule())
                }
                .buttonStyle(.pressable)
            }
        }
    }

    private var dateRow: some View {
        HStack {
            FieldLabel("Date")
            Spacer()
            DatePicker(
                "Date",
                selection: Binding(get: { EpochDay.date(state.epochDay) }, set: { vm.setDate(epochDay: EpochDay.from($0)) }),
                in: ...Date(),
                displayedComponents: .date
            )
            .labelsHidden()
        }
    }

    private var noteEditor: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                FieldLabel("Note")
                Spacer()
                Button {
                    dictation.toggle(appendingTo: note)
                } label: {
                    Image(systemName: dictation.isRecording ? "stop.circle.fill" : "mic.fill")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(dictation.isRecording ? Color.red : Palette.accent)
                        .frame(width: 44, height: 44)
                        .background(Palette.card, in: Circle())
                        .symbolEffect(.pulse, isActive: dictation.isRecording)
                }
                .accessibilityLabel(dictation.isRecording ? "Stop dictation" : "Dictate note")
            }
            TextEditor(text: $note)
                .focused($noteFocused)
                .frame(minHeight: 140)
                .scrollContentBackground(.hidden)
                .padding(10)
                .background(Palette.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay(alignment: .topLeading) {
                    if note.isEmpty {
                        Text("What did you talk about? Mention people, new jobs, intros…")
                            .foregroundStyle(Palette.muted)
                            .padding(16)
                            .allowsHitTesting(false)
                    }
                }
            if let error = dictation.error {
                Text(error).font(.footnote).foregroundStyle(.red)
            }
            if !state.mentions.isEmpty {
                HStack(spacing: 6) {
                    Text("Mentions:").font(.footnote.weight(.semibold)).foregroundStyle(Palette.muted)
                    ForEach(state.mentions, id: \.id) { person in
                        Button(person.name) { vm.addParticipant(id: person.id) }
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(Palette.accent)
                            .accessibilityHint("Adds \(person.name) to this interaction")
                    }
                }
            }
            if let change = state.roleChange {
                Button { vm.setRoleChangeAccepted(accepted: !state.roleChangeAccepted) } label: {
                    HStack(alignment: .top, spacing: 8) {
                        Image(systemName: state.roleChangeAccepted ? "checkmark.circle.fill" : "briefcase")
                            .foregroundStyle(state.roleChangeAccepted ? Palette.success : Tint.sky.foreground)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("New role detected").font(.footnote.weight(.semibold))
                            Text(change).font(.footnote)
                            Text(state.roleChangeAccepted ? "Profile will be updated on save" : "Tap to update their profile")
                                .font(.caption).foregroundStyle(Palette.muted)
                        }
                        Spacer()
                    }
                    .foregroundStyle(Palette.ink)
                    .padding(12)
                    .background(Tint.sky.background.opacity(0.6), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.pressable)
            }
        }
    }

    private var nextReminder: some View {
        VStack(alignment: .leading, spacing: 8) {
            FieldLabel("Next reminder")
            HStack(spacing: 8) {
                ForEach(state.nextOptions, id: \.cadence) { option in
                    ChipButton(label: option.label, isActive: option.isActive) { vm.setNext(cadence: option.cadence) }
                }
            }
            .accessibilityLabel("Next reminder")
            Text(state.nextLine).font(.footnote).foregroundStyle(Palette.muted)
        }
    }

    private var followUps: some View {
        VStack(alignment: .leading, spacing: 8) {
            FieldLabel("Follow-ups")
            ForEach(state.suggestions, id: \.self) { suggestion in
                HStack {
                    Image(systemName: "sparkles").foregroundStyle(Palette.accent)
                    Text(suggestion).font(.subheadline)
                    Spacer()
                    Button("Add") { vm.addFollowUp(text: suggestion) }
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Palette.accent)
                    Button { vm.dismissSuggestion(text: suggestion) } label: { Image(systemName: "xmark") }
                        .foregroundStyle(Palette.muted)
                        .accessibilityLabel("Dismiss suggestion")
                }
                .padding(12)
                .background(Palette.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            ForEach(state.followUps, id: \.self) { item in
                HStack {
                    Image(systemName: "circle").foregroundStyle(Palette.ring)
                    Text(item).font(.subheadline)
                    Spacer()
                    Button { vm.removeFollowUp(text: item) } label: { Image(systemName: "minus.circle") }
                        .foregroundStyle(Palette.muted)
                        .accessibilityLabel("Remove follow-up")
                }
                .padding(.horizontal, 12)
            }
            HStack {
                TextField("Add a follow-up", text: $followUp)
                    .submitLabel(.done)
                    .onSubmit(addFollowUp)
                Button("Add", action: addFollowUp)
                    .font(.subheadline.weight(.semibold))
                    .disabled(followUp.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding(12)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
    }

    private func addFollowUp() {
        vm.addFollowUp(text: followUp)
        followUp = ""
    }
}

private struct FieldLabel: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text).font(.footnote.weight(.semibold)).foregroundStyle(Palette.muted).textCase(.uppercase)
    }
}
