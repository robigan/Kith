import Foundation
import TheRealDex
import UserNotifications

/// Mirrors the shared module's planned reminders into local notifications at 9:00 on the due date (FR-006).
@MainActor
final class ReminderScheduler {
    private static let prefix = "dex.reminder."
    private static let limit = 60  // iOS keeps at most 64 pending requests per app.
    private let center = UNUserNotificationCenter.current()
    private var lastSignature: [String] = []

    func sync(_ reminders: [PlannedReminder]) async {
        let now = Date()
        let upcoming = reminders.compactMap { reminder -> (PlannedReminder, Date)? in
            guard let fire = Calendar.current.date(bySettingHour: 9, minute: 0, second: 0, of: EpochDay.date(reminder.epochDay)),
                  fire > now else { return nil }
            return (reminder, fire)
        }.prefix(Self.limit)

        let signature = upcoming.map { "\($0.0.contactId)@\($0.0.epochDay)" }
        guard signature != lastSignature else { return }
        lastSignature = signature
        guard !upcoming.isEmpty, await authorized() else { return }

        let pending = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(Self.prefix) }
        center.removePendingNotificationRequests(withIdentifiers: pending)
        for (reminder, fire) in upcoming {
            let content = UNMutableNotificationContent()
            content.title = "Keep in touch with \(reminder.name)"
            content.body = reminder.body
            content.sound = .default
            content.threadIdentifier = "keep-in-touch"
            let components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: fire)
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            try? await center.add(UNNotificationRequest(identifier: Self.prefix + reminder.contactId, content: content, trigger: trigger))
        }
    }

    private func authorized() async -> Bool {
        switch await center.notificationSettings().authorizationStatus {
        case .authorized, .provisional, .ephemeral: return true
        case .notDetermined: return (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        default: return false
        }
    }
}
