import EventKit
import Foundation
import SharedLogic

/// Reads recent meetings from the calendars on this iPhone, including Google accounts (FR-063).
enum CalendarImporter {
    static let lookbackDays = 90

    static func importRecent() async throws -> ImportResult {
        let store = EKEventStore()
        guard try await store.requestFullAccessToEvents() else { throw ImportError.denied("Calendars") }
        let end = Date()
        let start = Calendar.current.date(byAdding: .day, value: -lookbackDays, to: end) ?? end
        let predicate = store.predicateForEvents(withStart: start, end: end, calendars: nil)
        let events = store.events(matching: predicate)
            .filter { !$0.isAllDay && ($0.attendees?.isEmpty == false) }
            .map { event in
                ImportedEvent(
                    externalId: "\(event.calendarItemExternalIdentifier ?? event.eventIdentifier ?? UUID().uuidString)@\(Int64(event.startDate.timeIntervalSince1970))",
                    title: event.title ?? "Meeting",
                    location: event.location.flatMap { $0.isEmpty ? nil : $0.components(separatedBy: "\n").first },
                    startEpochMillis: Int64(event.startDate.timeIntervalSince1970 * 1000),
                    durationMinutes: Int32(event.endDate.timeIntervalSince(event.startDate) / 60),
                    attendees: (event.attendees ?? [])
                        .filter { !$0.isCurrentUser }
                        .map { attendee in
                            let email = attendee.url.absoluteString.hasPrefix("mailto:")
                                ? String(attendee.url.absoluteString.dropFirst("mailto:".count))
                                : nil
                            return ImportedAttendee(name: attendee.name, email: email)
                        }
                )
            }
        return try await Dex.shared.sources.importCalendarEvents(events: events)
    }
}
