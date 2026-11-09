package com.robigan.TheRealDex.domain

import com.robigan.TheRealDex.data.Snapshot
import com.robigan.TheRealDex.model.Cadence
import com.robigan.TheRealDex.model.ContactSummary
import com.robigan.TheRealDex.model.PeopleFilter
import com.robigan.TheRealDex.model.SignalType
import com.robigan.TheRealDex.model.SmartViewFilter
import com.robigan.TheRealDex.model.Tags
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number

/** Rules shared by the list screens. */
object People {
    /** Whether a contact counts as "due" for badges and the Due filter. */
    fun isDue(s: ContactSummary, today: LocalDate): Boolean =
        s.effectiveCadence != Cadence.NEVER && s.due != null && s.due <= today

    fun matches(s: ContactSummary, filter: PeopleFilter, today: LocalDate): Boolean = when (filter) {
        PeopleFilter.ALL -> true
        PeopleFilter.DUE -> isDue(s, today)
        PeopleFilter.STARRED -> s.contact.starred
        PeopleFilter.FAMILY -> Tags.FAMILY in s.tags
        PeopleFilter.WORK -> Tags.WORK in s.tags
    }

    fun matchesQuery(s: ContactSummary, query: String, noteMatches: Set<String>): Boolean {
        if (query.isBlank()) return true
        val c = s.contact
        return s.contact.id in noteMatches || listOfNotNull(c.name, c.role, c.company, c.location, c.relationship, c.howWeMet, c.email)
            .any { it.contains(query.trim(), ignoreCase = true) }
    }

    /** "Product Lead at Fernwood", "Birthday Thursday", "College roommate · Melbourne" (FR-024). */
    fun subtitle(s: ContactSummary, today: LocalDate): String {
        val c = s.contact
        c.birthday?.let { b ->
            val next = Occasions.nextBirthday(b, today)
            val days = today.daysUntil(next)
            if (days in 0..6) return "Birthday ${DateText.relativeDay(next, today).let { if (days > 1) it else it.lowercase() }}"
        }
        c.roleLine?.let { return it }
        val context = c.relationship ?: c.howWeMet
        return listOfNotNull(context, c.location).joinToString(" · ")
    }

    fun smartViewMatches(s: ContactSummary, f: SmartViewFilter, snapshot: Snapshot, today: LocalDate): Boolean {
        val c = s.contact
        if (f.tag != null && f.tag !in s.tags) return false
        if (f.groupId != null && s.groups.none { it.id == f.groupId }) return false
        if (f.minDaysSinceContact != null) {
            val since = s.lastInteraction?.daysUntil(today) ?: c.createdOn.daysUntil(today)
            if (since < f.minDaysSinceContact) return false
        }
        if (f.location != null && c.location?.contains(f.location, ignoreCase = true) != true) return false
        if (f.source != null && f.source !in s.sources) return false
        if (f.birthdayThisMonth && c.birthday?.month != today.month.number) return false
        if (f.changedJobsWithinDays != null) {
            val changed = snapshot.signals.any {
                it.type == SignalType.NEW_ROLE && it.contactId == c.id && it.date.daysUntil(today) <= f.changedJobsWithinDays
            }
            if (!changed) return false
        }
        if (f.metContext != null && c.howWeMet?.contains(f.metContext, ignoreCase = true) != true) return false
        return true
    }

    fun describe(f: SmartViewFilter, snapshot: Snapshot): String = buildList {
        f.tag?.let { add("Tagged “$it”") }
        f.groupId?.let { id -> snapshot.groups.firstOrNull { it.id == id }?.let { add("In ${it.name}") } }
        f.minDaysSinceContact?.let { add("No contact for $it+ days") }
        f.location?.let { add("Lives in $it") }
        f.source?.let { add("From ${it.label}") }
        if (f.birthdayThisMonth) add("Birthday this month")
        f.changedJobsWithinDays?.let { add("Changed jobs in the last $it days") }
        f.metContext?.let { add("Met: “$it”") }
    }.ifEmpty { listOf("Everyone") }.joinToString(" · ")
}
