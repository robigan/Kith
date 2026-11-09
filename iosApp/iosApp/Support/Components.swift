import SwiftUI
import TheRealDex

struct AvatarView: View {
    let avatar: Avatar
    var size: CGFloat = 44

    var body: some View {
        Text(avatar.initials)
            .font(.system(size: size * 0.36, weight: .semibold))
            .foregroundStyle(avatar.tint.foreground)
            .frame(width: size, height: size)
            .background(avatar.tint.background, in: Circle())
            .accessibilityHidden(true)
    }
}

/// Overlapping faces for group rows.
struct FacesView: View {
    let faces: [Avatar]

    var body: some View {
        HStack(spacing: -8) {
            ForEach(Array(faces.enumerated()), id: \.offset) { index, face in
                AvatarView(avatar: face, size: 32)
                    .overlay(Circle().stroke(Palette.card, lineWidth: 2))
                    .zIndex(Double(faces.count - index))
            }
        }
        .frame(width: 80, alignment: .leading)
    }
}

/// Short uppercase code on a tinted rounded square ("CAF", "90D").
struct CodeBadge: View {
    let code: String
    let tint: Tint
    var size: CGFloat = 36

    var body: some View {
        Text(code)
            .font(.system(size: code.count > 3 ? 9 : 11, weight: .bold))
            .tracking(0.3)
            .foregroundStyle(tint.foreground)
            .frame(width: size, height: size)
            .background(tint.background, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            .accessibilityHidden(true)
    }
}

struct SectionTitle: View {
    let title: String
    var trailing: String? = nil

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title)
                .font(.title2.bold())
                .foregroundStyle(Palette.ink)
            Spacer()
            if let trailing {
                Text(trailing).font(.subheadline).foregroundStyle(Palette.muted)
            }
        }
        .textCase(nil)
        .accessibilityAddTraits(.isHeader)
    }
}

struct Eyebrow: View {
    let text: String

    var body: some View {
        Text(text.uppercased())
            .font(.footnote.weight(.semibold))
            .tracking(0.3)
    }
}

/// Selectable capsule used for filters, interaction types and reminder options.
struct ChipButton: View {
    let label: String
    var detail: String? = nil
    let isActive: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Text(label).font(.subheadline.weight(.semibold))
                if let detail {
                    Text(detail).font(.subheadline).opacity(0.7)
                }
            }
            .padding(.horizontal, 14)
            .frame(minHeight: 36)
            .foregroundStyle(isActive ? Color.white : Palette.ink)
            .background(isActive ? Palette.ink : Palette.card, in: Capsule())
        }
        .buttonStyle(.pressable)
        .accessibilityAddTraits(isActive ? .isSelected : [])
    }
}

struct Card<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) { content }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

struct PersonRowView: View {
    let row: PersonRow

    var body: some View {
        HStack(spacing: 12) {
            AvatarView(avatar: row.avatar, size: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text(row.name).font(.body.weight(.semibold)).foregroundStyle(Palette.ink)
                if !row.subtitle.isEmpty {
                    Text(row.subtitle).font(.subheadline).foregroundStyle(Palette.muted).lineLimit(1)
                }
            }
            Spacer(minLength: 8)
            if row.isDue {
                Text("Due")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Palette.accent)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Palette.accentSoft, in: Capsule())
            }
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }
}

/// Wraps chips onto multiple lines.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(width: proposal.width ?? .infinity, subviews: subviews)
        return CGSize(width: proposal.width ?? rows.map(\.width).max() ?? 0, height: rows.last.map { $0.y + $0.height } ?? 0)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for row in arrange(width: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: bounds.minY + row.y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
        }
    }

    private struct Row { var indices: [Int] = []; var y: CGFloat = 0; var width: CGFloat = 0; var height: CGFloat = 0 }

    private func arrange(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = [Row()]
        for (index, subview) in subviews.enumerated() {
            let size = subview.sizeThatFits(.unspecified)
            if rows[rows.count - 1].width + size.width > width, !rows[rows.count - 1].indices.isEmpty {
                let previous = rows[rows.count - 1]
                rows.append(Row(y: previous.y + previous.height + spacing))
            }
            rows[rows.count - 1].indices.append(index)
            rows[rows.count - 1].width += size.width + spacing
            rows[rows.count - 1].height = max(rows[rows.count - 1].height, size.height)
        }
        return rows
    }
}

extension InteractionType {
    var symbol: String {
        switch self {
        case .coffee: "cup.and.saucer"
        case .call: "phone"
        case .meeting: "person.2"
        case .message: "message"
        case .event: "calendar"
        case .email: "envelope"
        case .note: "note.text"
        case .checkIn: "checkmark"
        }
    }
}

func peopleCount(_ n: Int) -> String { n == 1 ? "1 person" : "\(n) people" }
