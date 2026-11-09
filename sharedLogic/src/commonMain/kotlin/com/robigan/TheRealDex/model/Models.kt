package com.robigan.TheRealDex.model

import kotlinx.datetime.LocalDate

data class Birthday(val month: Int, val day: Int, val year: Int?)

data class Contact(
    val id: String,
    val name: String,
    val role: String?,
    val company: String?,
    val location: String?,
    val relationship: String?,
    val howWeMet: String?,
    val metOn: LocalDate?,
    val family: String?,
    val birthday: Birthday?,
    val workHistory: String?,
    val email: String?,
    val phone: String?,
    val tint: Tint,
    val starred: Boolean,
    val cadence: Cadence?,
    val dueOverride: LocalDate?,
    val createdOn: LocalDate,
) {
    val initials: String get() = initialsOf(name)
    val firstName: String get() = name.trim().substringBefore(' ')

    /** "Product Lead at Fernwood", "Fernwood", or null. */
    val roleLine: String?
        get() = when {
            !role.isNullOrBlank() && !company.isNullOrBlank() -> "$role at $company"
            !role.isNullOrBlank() -> role
            !company.isNullOrBlank() -> company
            else -> null
        }
}

fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

data class PeopleGroup(val id: String, val name: String, val cadence: Cadence?, val memberIds: List<String>)

data class Interaction(
    val id: String,
    val type: InteractionType,
    val date: LocalDate,
    val title: String?,
    val note: String,
    val origin: InteractionOrigin,
    val location: String?,
    val durationMinutes: Int?,
    val participantIds: List<String>,
)

data class StoredSignal(
    val id: String,
    val type: SignalType,
    val contactId: String,
    val date: LocalDate,
    val text: String,
    val source: SourceProvider?,
)

/** Saved filter; every non-null criterion must match. */
data class SmartViewFilter(
    val tag: String? = null,
    val groupId: String? = null,
    val minDaysSinceContact: Int? = null,
    val location: String? = null,
    val source: SourceProvider? = null,
    val birthdayThisMonth: Boolean = false,
    val changedJobsWithinDays: Int? = null,
    val metContext: String? = null,
)

data class SmartView(val id: String, val name: String, val code: String, val tint: Tint, val filter: SmartViewFilter)

/** Everything the list screens need to know about one contact, assembled from several tables. */
data class ContactSummary(
    val contact: Contact,
    val tags: Set<String>,
    val groups: List<PeopleGroup>,
    val giftIdeas: List<String>,
    val sources: Set<SourceProvider>,
    val lastInteraction: LocalDate?,
    val effectiveCadence: Cadence?,
    val due: LocalDate?,
)
