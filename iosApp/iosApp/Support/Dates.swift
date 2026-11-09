import Foundation

/// Bridges the shared module's epoch days (days since 1970-01-01, no time zone) and local `Date`s.
enum EpochDay {
    private static let utc: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }()

    static func date(_ epochDay: Int64) -> Date {
        let components = utc.dateComponents([.year, .month, .day], from: Date(timeIntervalSince1970: Double(epochDay) * 86_400))
        return Calendar.current.date(from: components) ?? Date()
    }

    static func from(_ date: Date) -> Int64 {
        let components = Calendar.current.dateComponents([.year, .month, .day], from: date)
        let midnight = utc.date(from: components) ?? date
        return Int64((midnight.timeIntervalSince1970 / 86_400).rounded(.down))
    }

    static var today: Int64 { from(Date()) }
}
