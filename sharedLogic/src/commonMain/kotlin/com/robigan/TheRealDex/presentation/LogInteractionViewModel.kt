package com.robigan.TheRealDex.presentation

import com.robigan.TheRealDex.data.DexRepository
import com.robigan.TheRealDex.data.LogInput
import com.robigan.TheRealDex.domain.DateProvider
import com.robigan.TheRealDex.domain.DateText
import com.robigan.TheRealDex.domain.NoteAnalyzer
import com.robigan.TheRealDex.domain.RoleChange
import com.robigan.TheRealDex.model.Cadence
import com.robigan.TheRealDex.model.InteractionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Log interaction sheet (FR-040…FR-048). Opened for one or more contacts, or to complete an imported meeting
 * ([interactionId]) from a "Needs a note" card.
 */
class LogInteractionViewModel(
    contactIds: List<String>,
    private val interactionId: String?,
    private val repository: DexRepository,
    dates: DateProvider,
) : ViewModel() {
    private data class Draft(
        val type: InteractionType = InteractionType.COFFEE,
        val title: String? = null,
        val participants: List<String>,
        val epochDay: Long,
        val note: String = "",
        val next: Cadence? = null,
        val followUps: List<String> = emptyList(),
        val dismissedSuggestions: Set<String> = emptySet(),
        val roleAccepted: Boolean = false,
    )

    private val draft = MutableStateFlow(Draft(participants = contactIds, epochDay = dates.today().toEpochDays()))
    private var roleChange: RoleChange? = null

    init {
        if (interactionId != null) launch {
            repository.interaction(interactionId)?.let { i ->
                draft.update { it.copy(type = i.type, title = i.title, participants = i.participantIds, epochDay = i.date.toEpochDays()) }
            }
        }
    }

    val state: StateFlow<LogState> = combine(draft, repository.snapshot()) { d, snap ->
        val contacts = snap.people.map { it.contact }
        val byId = snap.byId
        val subject = d.participants.firstOrNull()?.let { byId[it]?.contact }
        val date = LocalDate.fromEpochDays(d.epochDay)
        val defaultNext = subject?.let { byId[it.id]?.effectiveCadence }
        val next = d.next ?: defaultNext?.takeIf { it != Cadence.NEVER } ?: Cadence.MONTHLY
        val options = (BASE_OPTIONS + listOfNotNull(defaultNext)).distinct().sortedBy { it.approxDays }
        val names = d.participants.mapNotNull { byId[it]?.contact?.firstName }
        roleChange = NoteAnalyzer.roleChange(d.note, subject)
        LogState(
            type = d.type,
            types = InteractionType.loggableTypes.let { if (d.type in it) it else it + d.type },
            title = d.title,
            participants = d.participants.mapNotNull { id -> byId[id]?.let { PersonChip(id, it.contact.name, it.avatar()) } },
            epochDay = d.epochDay,
            dateLabel = DateText.short(date),
            note = d.note,
            mentions = NoteAnalyzer.mentions(d.note, contacts, d.participants.toSet()).mapNotNull { m ->
                byId[m.contactId]?.let { PersonChip(m.contactId, m.name, it.avatar()) }
            },
            roleChange = roleChange?.let { "${it.name.substringBefore(' ')}: ${it.summary}" },
            roleChangeAccepted = d.roleAccepted,
            nextOptions = options.map { NextOption(it, it.chipLabel, it == next) },
            nextLine = nextLine(next, date, names),
            suggestions = NoteAnalyzer.followUps(d.note, subject, contacts).filter { it !in d.followUps && it !in d.dismissedSuggestions },
            followUps = d.followUps,
            canSave = d.participants.isNotEmpty(),
        )
    }.toState(
        LogState(
            InteractionType.COFFEE, InteractionType.loggableTypes, null, emptyList(), draft.value.epochDay,
            DateText.short(LocalDate.fromEpochDays(draft.value.epochDay)), "", emptyList(), null, false, emptyList(), "",
            emptyList(), emptyList(), false,
        )
    )

    fun setType(type: InteractionType) = draft.update { it.copy(type = type) }
    fun addParticipant(id: String) = draft.update { if (id in it.participants) it else it.copy(participants = it.participants + id) }
    fun removeParticipant(id: String) = draft.update { it.copy(participants = it.participants - id) }
    fun setDate(epochDay: Long) = draft.update { it.copy(epochDay = epochDay) }
    fun setNote(note: String) = draft.update { it.copy(note = note) }
    fun setNext(cadence: Cadence) = draft.update { it.copy(next = cadence) }
    fun setRoleChangeAccepted(accepted: Boolean) = draft.update { it.copy(roleAccepted = accepted) }
    fun addFollowUp(text: String) = draft.update { if (text.isBlank() || text in it.followUps) it else it.copy(followUps = it.followUps + text.trim()) }
    fun removeFollowUp(text: String) = draft.update { it.copy(followUps = it.followUps - text) }
    fun dismissSuggestion(text: String) = draft.update { it.copy(dismissedSuggestions = it.dismissedSuggestions + text) }

    /** Persists the interaction; returns false when there is nobody to log it for. */
    suspend fun save(): Boolean {
        val d = draft.value
        if (d.participants.isEmpty()) return false
        val next = state.value.nextOptions.firstOrNull { it.isActive }?.cadence ?: Cadence.MONTHLY
        repository.logInteraction(
            LogInput(d.type, d.epochDay, d.participants, d.note, next, d.followUps, existingInteractionId = interactionId)
        )
        val change = roleChange
        if (d.roleAccepted && change != null) repository.applyRoleChange(change.contactId, change.role, change.company)
        return true
    }

    private fun nextLine(next: Cadence, date: LocalDate, names: List<String>): String {
        val reminder = next.period?.let { date.plus(it) }
        if (reminder == null) {
            val who = when (names.size) {
                0 -> "this person"
                1 -> names[0]
                else -> "${names[0]} and ${DateText.plural(names.size - 1, "other")}"
            }
            return "Reminders are off for $who."
        }
        return "You'll be reminded on ${DateText.short(reminder)}."
    }

    private companion object {
        val BASE_OPTIONS = listOf(Cadence.TWO_WEEKS, Cadence.MONTHLY, Cadence.QUARTERLY, Cadence.NEVER)
    }
}
