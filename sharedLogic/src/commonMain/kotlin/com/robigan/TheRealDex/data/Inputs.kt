package com.robigan.TheRealDex.data

import com.robigan.TheRealDex.model.Cadence
import com.robigan.TheRealDex.model.InteractionType

/**
 * Editable contact fields, used by the Swift edit form. Dates are epoch days; 0 means "not set" for birthday parts
 * so Swift can use plain Ints.
 */
data class ContactInput(
    val name: String,
    val role: String = "",
    val company: String = "",
    val location: String = "",
    val relationship: String = "",
    val howWeMet: String = "",
    val metOnEpochDay: Long? = null,
    val family: String = "",
    val birthdayMonth: Int = 0,
    val birthdayDay: Int = 0,
    val birthdayYear: Int = 0,
    val workHistory: String = "",
    val email: String = "",
    val phone: String = "",
    val cadence: Cadence? = null,
    val tags: List<String> = emptyList(),
    val giftIdeas: List<String> = emptyList(),
    val groupIds: List<String> = emptyList(),
    val starred: Boolean = false,
)

data class LogInput(
    val type: InteractionType,
    val epochDay: Long,
    val participantIds: List<String>,
    val note: String,
    /** When to remind next, counted from the interaction date. [Cadence.NEVER] turns reminders off. */
    val nextReminder: Cadence,
    val followUps: List<String>,
    /** When set, the note completes this existing (e.g. calendar) interaction instead of creating a new one. */
    val existingInteractionId: String? = null,
)

data class SmartViewInput(
    val name: String,
    val code: String,
    val tint: com.robigan.TheRealDex.model.Tint,
    val tag: String? = null,
    val groupId: String? = null,
    val minDaysSinceContact: Int? = null,
    val location: String? = null,
    val source: com.robigan.TheRealDex.model.SourceProvider? = null,
    val birthdayThisMonth: Boolean = false,
    val changedJobsWithinDays: Int? = null,
    val metContext: String? = null,
)

/** A person as read from the iPhone address book. Birthday parts use 0 for "unknown". */
data class ImportedContact(
    val externalId: String,
    val name: String,
    val email: String?,
    val phone: String?,
    val company: String?,
    val role: String?,
    val birthdayMonth: Int,
    val birthdayDay: Int,
    val birthdayYear: Int,
)

data class ImportedAttendee(val name: String?, val email: String?)

/** A calendar event read via EventKit (including Google accounts added to iOS). */
data class ImportedEvent(
    val externalId: String,
    val title: String,
    val location: String?,
    val startEpochMillis: Long,
    val durationMinutes: Int,
    val attendees: List<ImportedAttendee>,
)

data class ImportResult(val created: Int, val updated: Int, val signals: Int) {
    val summary: String
        get() = buildList {
            add("$created added")
            add("$updated updated")
            if (signals > 0) add("$signals job change${if (signals == 1) "" else "s"}")
        }.joinToString(" · ")
}
