import SwiftUI
import UIKit
import SharedLogic

extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: alpha
        )
    }
}

extension Color {
    /// A colour that follows light/dark mode.
    static func dynamic(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light) })
    }
}

/// Design tokens from the Kith design (NFR-021), with dark-mode counterparts.
enum Palette {
    static let ink = Color.dynamic(0x111114, 0xF2F2F7)
    static let secondary = Color.dynamic(0x3A3A40, 0xD1D1D6)
    static let muted = Color.dynamic(0x5E5E66, 0x98989F)
    static let background = Color.dynamic(0xF2F2F7, 0x000000)
    static let card = Color.dynamic(0xFFFFFF, 0x1C1C1E)
    static let accent = Color.dynamic(0xB8461B, 0xEC7C4C)
    static let accentSoft = Color.dynamic(0xFBE9E0, 0x3B2116)
    static let success = Color.dynamic(0x1F7A4D, 0x40C383)
    static let ring = Color.dynamic(0xB9B9C0, 0x5A5A60)
}

extension Tint {
    private var hex: (background: UInt32, foreground: UInt32) {
        switch self {
        case .peach: (0xFCE3D6, 0x8A3A12)
        case .sage: (0xDDEFE3, 0x1F5E3B)
        case .sky: (0xDCE8FA, 0x1E4E8C)
        case .lilac: (0xE9E1F8, 0x5B3A99)
        case .sand: (0xF3EBD6, 0x6B5317)
        case .rose: (0xF8DDE4, 0x8E2D4B)
        }
    }

    /// Light: pastel fill. Dark: the ink colour, dimmed.
    var background: Color {
        let (bg, fg) = hex
        return Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: fg, alpha: 0.45) : UIColor(hex: bg) })
    }

    var foreground: Color { Color.dynamic(hex.foreground, hex.background) }
}

/// Motion spec (NFR-001, NFR-010): critically damped springs, cross-fades under Reduce Motion.
enum Motion {
    static func standard(reduceMotion: Bool) -> Animation {
        reduceMotion ? .easeInOut(duration: 0.2) : .spring(response: 0.35, dampingFraction: 1.0)
    }

    static func transition(reduceMotion: Bool) -> AnyTransition {
        reduceMotion ? .opacity : .opacity.combined(with: .move(edge: .top))
    }
}

/// Press feedback on touch-down: scale 0.97 over 100 ms (NFR-004).
struct PressableStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.97 : 1)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == PressableStyle {
    static var pressable: PressableStyle { PressableStyle() }
}
