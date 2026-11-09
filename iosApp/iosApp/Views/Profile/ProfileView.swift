import SwiftUI
import SharedLogic

/// Contact profile (FR-030…FR-037).
struct ProfileView: View {
    @State private var screen: Screen<ProfileViewModel, ProfileState>
    @Environment(Router.self) private var router
    @Environment(\.openURL) private var openURL
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var tab: ProfileTab = .timeline

    enum ProfileTab: String, CaseIterable { case timeline = "Timeline", about = "About" }

    init(contactId: String) {
        _screen = State(initialValue: Screen(Dex.shared.profile(contactId: contactId)) { $0.state })
    }

    private var state: ProfileState { screen.state }
    private var vm: ProfileViewModel { screen.vm }

    var body: some View {
        Group {
            if state.exists {
                content
            } else {
                ContentUnavailableView("Contact deleted", systemImage: "person.crop.circle.badge.xmark")
            }
        }
        .background(Palette.background)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if state.exists {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { vm.toggleStar() } label: {
                        Image(systemName: state.starred ? "star.fill" : "star")
                            .foregroundStyle(state.starred ? Palette.accent : Palette.ink)
                    }
                    .accessibilityLabel(state.starred ? "Unstar" : "Star")
                    Button("Edit") { router.editContact(state.id) }
                }
            }
        }
        .task { await screen.run() }
    }

    private var content: some View {
        ScrollView {
            VStack(spacing: 20) {
                header
                quickActions
                keepInTouch
                if !state.followUps.isEmpty { followUps }
                Picker("Profile sections", selection: $tab.animation(Motion.standard(reduceMotion: reduceMotion))) {
                    ForEach(ProfileTab.allCases, id: \.self) { Text($0.rawValue) }
                }
                .pickerStyle(.segmented)
                if tab == .timeline { timeline } else { about }
                if !state.sources.isEmpty { sources }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 32)
        }
    }

    private var header: some View {
        VStack(spacing: 10) {
            AvatarView(avatar: state.avatar, size: 88)
            Text(state.name).font(.title.bold()).foregroundStyle(Palette.ink).multilineTextAlignment(.center)
            if !state.subtitle.isEmpty {
                Text(state.subtitle).font(.subheadline).foregroundStyle(Palette.muted).multilineTextAlignment(.center)
            }
            if !state.chips.isEmpty {
                FlowLayout(spacing: 6) {
                    ForEach(state.chips, id: \.self) { chip in
                        Text(chip)
                            .font(.footnote.weight(.medium))
                            .foregroundStyle(Palette.secondary)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Palette.card, in: Capsule())
                    }
                }
                .frame(maxWidth: 320)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 8)
    }

    private var quickActions: some View {
        HStack(spacing: 10) {
            QuickAction(title: "Message", symbol: "message.fill", enabled: state.phone != nil) { open("sms:", state.phone) }
            QuickAction(title: "Call", symbol: "phone.fill", enabled: state.phone != nil) { open("tel:", state.phone) }
            QuickAction(title: "Email", symbol: "envelope.fill", enabled: state.email != nil) { open("mailto:", state.email) }
            QuickAction(title: "Log", symbol: "square.and.pencil", enabled: true, prominent: true) {
                router.logInteraction(with: [state.id])
            }
        }
    }

    private func open(_ scheme: String, _ value: String?) {
        guard let value, let url = URL(string: scheme + value.filter { !$0.isWhitespace }) else { return }
        openURL(url)
    }

    private var keepInTouch: some View {
        let card = state.keepInTouch
        return Card {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Keep in touch").font(.footnote.weight(.semibold)).foregroundStyle(Palette.muted)
                    Text(card.cadenceLabel).font(.headline).foregroundStyle(Palette.ink)
                    if let source = card.cadenceSource {
                        Text(source).font(.caption).foregroundStyle(Palette.muted)
                    }
                }
                Spacer()
                Menu {
                    Button("Use group default") { vm.setCadence(cadence: nil) }
                    Divider()
                    ForEach(Cadence.allCases, id: \.self) { cadence in
                        Button(cadence.label) { vm.setCadence(cadence: cadence) }
                    }
                } label: {
                    Text("Change").font(.subheadline.weight(.semibold)).foregroundStyle(Palette.accent)
                }
            }
            if card.cadence != nil && card.cadence != .never {
                ProgressView(value: min(card.progress, 1))
                    .tint(card.isUrgent ? Palette.accent : Palette.success)
                    .accessibilityLabel(card.accessibilityLabel)
            }
            HStack {
                Text(card.lastLabel).foregroundStyle(Palette.muted)
                Spacer()
                Text(card.status).fontWeight(.semibold).foregroundStyle(card.isUrgent ? Palette.accent : Palette.secondary)
            }
            .font(.subheadline)
        }
    }

    private var followUps: some View {
        Card {
            Text("Follow-ups").font(.footnote.weight(.semibold)).foregroundStyle(Palette.muted)
            ForEach(state.followUps, id: \.id) { item in
                HStack(spacing: 10) {
                    Button { vm.setFollowUpDone(id: item.id, done: !item.isDone) } label: {
                        Image(systemName: item.isDone ? "checkmark.circle.fill" : "circle")
                            .font(.title3)
                            .foregroundStyle(item.isDone ? Palette.success : Palette.ring)
                    }
                    .accessibilityLabel(item.isDone ? "Mark not done" : "Mark done")
                    Text(item.text)
                        .strikethrough(item.isDone)
                        .foregroundStyle(item.isDone ? Palette.muted : Palette.ink)
                    Spacer()
                }
                .contextMenu { Button("Delete", systemImage: "trash", role: .destructive) { vm.deleteFollowUp(id: item.id) } }
            }
        }
    }

    private var timeline: some View {
        VStack(spacing: 10) {
            if state.timeline.isEmpty {
                Text("No interactions yet. Tap Log to add the first one.")
                    .font(.subheadline).foregroundStyle(Palette.muted)
                    .frame(maxWidth: .infinity, minHeight: 80)
            }
            ForEach(state.timeline, id: \.id) { entry in
                HStack(alignment: .top, spacing: 12) {
                    CodeBadge(code: entry.type.code, tint: entry.type.tint)
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(alignment: .firstTextBaseline) {
                            Text(entry.title).font(.headline).foregroundStyle(Palette.ink)
                            Spacer()
                            Text(entry.whenLabel).font(.subheadline).foregroundStyle(Palette.muted)
                        }
                        if !entry.note.isEmpty {
                            Text(entry.note).font(.subheadline).foregroundStyle(Palette.secondary)
                        }
                        if entry.needsNote {
                            Button("Add note") { router.logInteraction(with: [state.id], completing: entry.id) }
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Palette.accent)
                        }
                    }
                }
                .padding(14)
                .background(Palette.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .contextMenu {
                    Button("Delete", systemImage: "trash", role: .destructive) { vm.deleteInteraction(id: entry.id) }
                }
            }
        }
    }

    private var about: some View {
        Card {
            if state.facts.isEmpty {
                Text("Nothing saved yet. Tap Edit to add how you met, birthdays and gift ideas.")
                    .font(.subheadline).foregroundStyle(Palette.muted)
            }
            ForEach(Array(state.facts.enumerated()), id: \.offset) { index, fact in
                if index > 0 { Divider() }
                VStack(alignment: .leading, spacing: 2) {
                    Text(fact.label).font(.footnote.weight(.semibold)).foregroundStyle(Palette.muted)
                    Text(fact.value).foregroundStyle(Palette.ink).textSelection(.enabled)
                }
            }
        }
    }

    private var sources: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Synced from").font(.footnote.weight(.semibold)).foregroundStyle(Palette.muted)
            FlowLayout(spacing: 6) {
                ForEach(state.sources, id: \.self) { source in
                    Text(source)
                        .font(.footnote)
                        .foregroundStyle(Palette.secondary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Palette.card, in: Capsule())
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct QuickAction: View {
    let title: String
    let symbol: String
    let enabled: Bool
    var prominent = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: symbol).font(.system(size: 18, weight: .semibold))
                Text(title).font(.caption.weight(.semibold))
            }
            .frame(maxWidth: .infinity, minHeight: 64)
            .foregroundStyle(prominent ? Color.white : Palette.accent)
            .background(prominent ? Palette.accent : Palette.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.pressable)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.45)
    }
}
