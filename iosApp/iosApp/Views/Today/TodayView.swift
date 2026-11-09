import SwiftUI
import SharedLogic

/// Today tab (FR-010…FR-019).
struct TodayView: View {
    @State private var screen = Screen(Dex.shared.today()) { $0.state }
    @Environment(Router.self) private var router
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var path = NavigationPath()
    @State private var successHaptic = 0
    @State private var loadingSample = false

    private var state: TodayState { screen.state }
    private var vm: TodayViewModel { screen.vm }

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if !state.isLoading && !state.hasPeople {
                    emptyState
                } else {
                    list
                }
            }
            .background(Palette.background)
            .toolbar(.hidden, for: .navigationBar)
            .routeDestinations()
        }
        .task { await screen.run() }
        .sensoryFeedback(.success, trigger: successHaptic)
    }

    private var list: some View {
        List {
            Section {
                header
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 0, leading: 4, bottom: 0, trailing: 4))
            }

            Section {
                if state.rows.isEmpty {
                    Text("Nobody is due in the next few days.")
                        .foregroundStyle(Palette.muted)
                        .frame(minHeight: 56)
                }
                ForEach(state.rows, id: \.contactId) { row in
                    keepInTouchRow(row)
                }
            } header: {
                SectionTitle(title: "Keep in touch", trailing: state.dueLabel)
            } footer: {
                Text("Swipe right to mark done, left to snooze. Logging an interaction resets the cadence.")
                    .foregroundStyle(Palette.muted)
            }

            if !state.reasons.isEmpty {
                Section {
                    reasonsCarousel
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets())
                } header: {
                    SectionTitle(title: "Reasons to reach out")
                }
            }

            if !state.needsNote.isEmpty {
                Section {
                    ForEach(state.needsNote, id: \.interactionId) { card in
                        NeedsNoteRow(card: card) {
                            router.logInteraction(with: card.contactIds, completing: card.interactionId)
                        }
                        .swipeActions {
                            Button("Dismiss") { vm.dismissNeedsNote(interactionId: card.interactionId) }
                        }
                    }
                } header: {
                    HStack(alignment: .firstTextBaseline) {
                        SectionTitle(title: "Needs a note")
                        Spacer()
                        Text("From Calendar").font(.subheadline).foregroundStyle(Palette.muted).textCase(nil)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .listSectionSpacing(28)
        .scrollContentBackground(.hidden)
        .animation(Motion.standard(reduceMotion: reduceMotion), value: state.rows.map(\.isDone))
    }

    private var header: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 2) {
                Eyebrow(text: state.dateLabel).foregroundStyle(Palette.muted)
                Text("Today").font(.largeTitle.bold()).foregroundStyle(Palette.ink)
            }
            Spacer()
            Button { router.editContact() } label: {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Palette.accent)
                    .frame(width: 44, height: 44)
                    .background(Palette.card, in: Circle())
                    .shadow(color: .black.opacity(0.06), radius: 1, y: 1)
            }
            .buttonStyle(.pressable)
            .accessibilityLabel("Add contact")
        }
        .padding(.top, 8)
    }

    private func keepInTouchRow(_ row: KeepInTouchRow) -> some View {
        HStack(spacing: 12) {
            AvatarView(avatar: row.avatar)
            VStack(alignment: .leading, spacing: 2) {
                Text(row.name).font(.headline).foregroundStyle(Palette.ink).lineLimit(1)
                if row.isDone {
                    Text(row.doneLabel).font(.subheadline.weight(.medium)).foregroundStyle(Palette.success)
                } else {
                    (Text(row.status).fontWeight(.semibold).foregroundColor(row.isUrgent ? Palette.accent : Palette.secondary)
                        + Text(" · \(row.cadenceLabel)").foregroundColor(Palette.muted))
                        .font(.subheadline)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 8)
            Button {
                if !row.isDone { successHaptic += 1 }
                vm.toggle(contactId: row.contactId)
            } label: {
                ZStack {
                    if row.isDone {
                        Circle().fill(Palette.success)
                        Image(systemName: "checkmark").font(.system(size: 13, weight: .heavy)).foregroundStyle(.white)
                    } else {
                        Circle().strokeBorder(Palette.ring, lineWidth: 2)
                    }
                }
                .frame(width: 28, height: 28)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(row.isDone ? "Undo, \(row.name)" : "Mark \(row.name) as done")
        }
        .frame(minHeight: 56)
        .background(NavigationLink(value: Route.profile(row.contactId)) { EmptyView() }.opacity(0))
        .swipeActions(edge: .leading, allowsFullSwipe: true) {
            if row.isDone {
                Button("Undo") { vm.undo(contactId: row.contactId) }.tint(.gray)
            } else {
                Button {
                    successHaptic += 1
                    vm.markDone(contactId: row.contactId)
                } label: { Label("Done", systemImage: "checkmark") }
                    .tint(Palette.success)
            }
        }
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            if !row.isDone {
                Button { vm.snooze(contactId: row.contactId) } label: { Label("Snooze 1 week", systemImage: "moon.zzz") }
                    .tint(.indigo)
            }
        }
        .accessibilityAction(named: row.isDone ? "Undo" : "Mark done") { vm.toggle(contactId: row.contactId) }
        .accessibilityAction(named: "Snooze 1 week") { vm.snooze(contactId: row.contactId) }
    }

    private var reasonsCarousel: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(spacing: 12) {
                ForEach(state.reasons, id: \.key) { reason in
                    NavigationLink(value: Route.profile(reason.contactId)) {
                        ReasonCardView(card: reason)
                    }
                    .buttonStyle(.pressable)
                    .contextMenu {
                        Button("Log interaction", systemImage: "square.and.pencil") { router.logInteraction(with: [reason.contactId]) }
                        Button("Dismiss", systemImage: "xmark") { vm.dismissReason(key: reason.key) }
                    }
                }
            }
            .scrollTargetLayout()
            .padding(.horizontal, 20)
        }
        .scrollTargetBehavior(.viewAligned)
        .frame(height: 156)
    }

    private var emptyState: some View {
        ContentUnavailableView {
            Label("Nobody here yet", systemImage: "person.2")
        } description: {
            Text("Add the people you want to keep in touch with, import them from Groups → Sources, or explore with sample data.")
        } actions: {
            Button("Add contact") { router.editContact() }
                .buttonStyle(.borderedProminent)
            Button("Load sample data") {
                loadingSample = true
                Task {
                    try? await Dex.shared.app().loadSampleData()
                    loadingSample = false
                }
            }
            .disabled(loadingSample)
        }
    }
}

private struct ReasonCardView: View {
    let card: ReasonCard

    private var style: (symbol: String, color: Color) {
        switch card.kind {
        case .birthday: ("birthday.cake", Palette.accent)
        case .newRole: ("briefcase", Tint.sky.foreground)
        case .metAnniversary: ("calendar", Tint.sage.foreground)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: style.symbol).font(.subheadline)
                Eyebrow(text: card.label).lineLimit(1)
            }
            .foregroundStyle(style.color)
            HStack(alignment: .top, spacing: 10) {
                AvatarView(avatar: card.avatar, size: 36)
                Text(card.text)
                    .font(.subheadline)
                    .foregroundStyle(Palette.ink)
                    .multilineTextAlignment(.leading)
                    .lineLimit(4)
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .frame(width: 260, height: 156, alignment: .topLeading)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

private struct NeedsNoteRow: View {
    let card: NeedsNoteCard
    let addNote: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            VStack(spacing: 0) {
                Text(card.weekday).font(.caption2.weight(.bold)).foregroundStyle(Palette.accent)
                Text(card.day).font(.title3.weight(.semibold)).foregroundStyle(Palette.ink)
            }
            .frame(width: 48, height: 48)
            .background(Palette.background, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(card.title).font(.headline).foregroundStyle(Palette.ink)
                if !card.subtitle.isEmpty {
                    Text(card.subtitle).font(.subheadline).foregroundStyle(Palette.muted)
                }
            }
            Spacer(minLength: 8)
            Button("Add note", action: addNote)
                .font(.subheadline.weight(.semibold))
                .buttonStyle(.borderless)
                .foregroundStyle(Palette.accent)
        }
        .padding(.vertical, 4)
    }
}
