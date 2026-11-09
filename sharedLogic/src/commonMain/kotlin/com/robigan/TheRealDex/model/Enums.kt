package com.robigan.TheRealDex.model

import kotlinx.datetime.DatePeriod

/** Keep-in-touch interval. [NEVER] turns reminders off. Months are calendar months (6 Nov + 1 mo = 6 Dec). */
enum class Cadence(
    val code: String,
    val label: String,
    val shortLabel: String,
    val chipLabel: String,
    val nextLabel: String,
    val period: DatePeriod?,
) {
    WEEKLY("1w", "Every week", "weekly", "1 wk", "in 1 week", DatePeriod(days = 7)),
    TWO_WEEKS("2w", "Every 2 weeks", "2 weeks", "2 wks", "in 2 weeks", DatePeriod(days = 14)),
    MONTHLY("1m", "Every month", "monthly", "1 mo", "in 1 month", DatePeriod(months = 1)),
    QUARTERLY("3m", "Every 3 months", "quarterly", "3 mo", "in 3 months", DatePeriod(months = 3)),
    HALF_YEARLY("6m", "Every 6 months", "6 months", "6 mo", "in 6 months", DatePeriod(months = 6)),
    YEARLY("1y", "Every year", "yearly", "1 yr", "in 1 year", DatePeriod(years = 1)),
    NEVER("never", "Never", "no reminders", "Never", "never", null);

    /** Approximate length used to rank cadences; the shortest one wins when several groups apply. */
    val approxDays: Int
        get() = period?.let { it.days + it.months * 30 + it.years * 365 } ?: Int.MAX_VALUE

    companion object {
        fun fromCode(code: String?): Cadence? = entries.firstOrNull { it.code == code }
    }
}

enum class InteractionType(val code: String, val label: String, val loggable: Boolean) {
    COFFEE("CAF", "Coffee", true),
    CALL("CALL", "Call", true),
    MEETING("MTG", "Meeting", true),
    MESSAGE("MSG", "Message", true),
    EVENT("EVT", "Event", true),
    EMAIL("MAIL", "Email", false),
    NOTE("NOTE", "Note", false),
    CHECK_IN("DONE", "Check-in", false);

    val tint: Tint
        get() = when (this) {
            COFFEE -> Tint.PEACH
            CALL -> Tint.SAGE
            EMAIL, MESSAGE -> Tint.SKY
            EVENT, MEETING -> Tint.LILAC
            NOTE, CHECK_IN -> Tint.SAND
        }

    companion object {
        val loggableTypes: List<InteractionType> get() = entries.filter { it.loggable }
        fun fromName(name: String): InteractionType = entries.firstOrNull { it.name == name } ?: NOTE
    }
}

enum class InteractionOrigin { MANUAL, CALENDAR, GMAIL }

/** Avatar/badge palette from the design. Colours live in the Swift theme. */
enum class Tint {
    PEACH, SAGE, SKY, LILAC, SAND, ROSE;

    companion object {
        fun fromName(name: String): Tint = entries.firstOrNull { it.name == name } ?: SKY
        fun forName(name: String): Tint = entries[(name.hashCode() and Int.MAX_VALUE) % entries.size]
    }
}

enum class SourceProvider(val label: String) {
    CONTACTS("iPhone Contacts"),
    LINKEDIN("LinkedIn"),
    CALENDAR("Calendar"),
    GMAIL("Gmail");

    val shortLabel: String
        get() = when (this) {
            CONTACTS -> "Contacts"
            else -> label
        }

    companion object {
        fun fromName(name: String?): SourceProvider? = entries.firstOrNull { it.name == name }
    }
}

enum class SignalType { BIRTHDAY, NEW_ROLE, MET_ANNIVERSARY }

enum class PeopleFilter(val label: String) {
    ALL("All"), DUE("Due"), STARRED("Starred"), FAMILY("Family"), WORK("Work")
}

object Tags {
    const val FAMILY = "family"
    const val WORK = "work"
}
