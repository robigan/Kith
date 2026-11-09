package com.robigan.TheRealDex.presentation

import com.robigan.TheRealDex.data.DexRepository
import com.robigan.TheRealDex.data.FollowUp
import com.robigan.TheRealDex.data.ImportantDate
import com.robigan.TheRealDex.data.Snapshot
import com.robigan.TheRealDex.domain.DateProvider
import com.robigan.TheRealDex.domain.DateText
import com.robigan.TheRealDex.model.Cadence
import com.robigan.TheRealDex.model.ContactSummary
import com.robigan.TheRealDex.model.Interaction
import com.robigan.TheRealDex.model.InteractionOrigin
import com.robigan.TheRealDex.model.SourceProvider
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** Contact profile (FR-030…FR-037). */
class ProfileViewModel(
    private val contactId: String,
    private val repository: DexRepository,
    private val dates: DateProvider,
) : ViewModel() {
    private var starred = false

    val state: StateFlow<ProfileState> = combine(
        repository.snapshot(),
        repository.interactionsFor(contactId),
        combine(repository.followUpsFor(contactId), repository.importantDatesFor(contactId)) { f, d -> f to d },
        repository.sourceCountsFor(contactId),
        dates.todayFlow(),
    ) { snap, interactions, (followUps, importantDates), counts, today ->
        val summary = snap.byId[contactId] ?: return@combine ProfileState.empty(contactId).copy(exists = false)
        starred = summary.contact.starred
        build(summary, snap, interactions, followUps, importantDates, counts, today)
    }.toState(ProfileState.empty(contactId))

    fun toggleStar() = launch { repository.setStarred(contactId, !starred) }

    /** null returns to the group default. */
    fun setCadence(cadence: Cadence?) = launch { repository.setCadence(contactId, cadence) }

    fun deleteInteraction(id: String) = launch { repository.deleteInteraction(id) }

    fun setFollowUpDone(id: String, done: Boolean) = launch { repository.setFollowUpDone(id, done) }

    fun deleteFollowUp(id: String) = launch { repository.deleteFollowUp(id) }

    suspend fun delete() = repository.deleteContact(contactId)

    private fun build(
        s: ContactSummary,
        snap: Snapshot,
        interactions: List<Interaction>,
        followUps: List<FollowUp>,
        importantDates: List<ImportantDate>,
        counts: Map<InteractionOrigin, Int>,
        today: LocalDate,
    ): ProfileState {
        val c = s.contact
        return ProfileState(
            exists = true,
            id = c.id,
            name = c.name,
            avatar = s.avatar(),
            subtitle = listOfNotNull(c.roleLine ?: c.relationship, c.location).joinToString(" · "),
            chips = s.groups.map { it.name } + listOfNotNull(c.location),
            starred = c.starred,
            phone = c.phone,
            email = c.email,
            keepInTouch = keepInTouch(s, interactions, today),
            timeline = interactions.map { i ->
                TimelineEntry(
                    id = i.id,
                    type = i.type,
                    title = i.title ?: i.type.label,
                    whenLabel = DateText.dayMonth(i.date, today),
                    note = i.note,
                    needsNote = i.origin == InteractionOrigin.CALENDAR && i.note.isBlank(),
                )
            },
            facts = facts(s, importantDates),
            followUps = followUps.map { FollowUpRow(it.id, it.text, it.done) },
            sources = SourceProvider.entries.filter { it in s.sources }.map { p ->
                when (p) {
                    SourceProvider.GMAIL -> "Gmail · ${DateText.plural(counts[InteractionOrigin.GMAIL] ?: 0, "thread")}"
                    SourceProvider.CALENDAR -> "Calendar · ${DateText.plural(counts[InteractionOrigin.CALENDAR] ?: 0, "event")}"
                    else -> p.shortLabel
                }
            },
        )
    }

    private fun keepInTouch(s: ContactSummary, interactions: List<Interaction>, today: LocalDate): KeepInTouchCard {
        val cadence = s.effectiveCadence
        val last = interactions.firstOrNull { !(it.origin == InteractionOrigin.CALENDAR && it.note.isBlank()) }
        val lastLabel = last?.let { "Last: ${it.type.label.lowercase()}, ${DateText.dayMonth(it.date, today)}" } ?: "No interactions yet"
        val source = if (s.contact.cadence == null && cadence != null) s.groups.firstOrNull { it.cadence == cadence }?.let { "From ${it.name}" } else null
        val due = s.due
        if (cadence == null || cadence == Cadence.NEVER || due == null) {
            val status = if (cadence == Cadence.NEVER) "Reminders off" else "No reminder set"
            return KeepInTouchCard(cadence, cadence?.label ?: "Not set", source, lastLabel, status, false, 0.0, "$lastLabel. $status")
        }
        val start = s.lastInteraction ?: s.contact.createdOn
        val total = start.daysUntil(due).coerceAtLeast(1)
        val elapsed = start.daysUntil(today).coerceAtLeast(0)
        val status = DateText.dueStatus(due, today)
        return KeepInTouchCard(
            cadence = cadence,
            cadenceLabel = cadence.label,
            cadenceSource = source,
            lastLabel = lastLabel,
            status = status,
            isUrgent = due <= today,
            progress = elapsed.toDouble() / total,
            accessibilityLabel = "$elapsed of $total days elapsed, ${status.replaceFirstChar(Char::lowercaseChar)}",
        )
    }

    private fun facts(s: ContactSummary, importantDates: List<ImportantDate>): List<Fact> {
        val c = s.contact
        val metOn = c.metOn?.let { "${DateText.monthShort(it.month)} ${it.year}" }
        return listOfNotNull(
            c.relationship?.let { Fact("Relationship", it) },
            (c.howWeMet ?: metOn?.let { "Met $it" })?.let { Fact("How we met", it) },
            c.birthday?.let { Fact("Birthday", DateText.birthday(it.month, it.day, it.year)) },
            c.family?.let { Fact("Family", it) },
            s.giftIdeas.takeIf { it.isNotEmpty() }?.let { Fact("Gift ideas", it.joinToString(" · ")) },
            listOfNotNull(c.company, c.workHistory).takeIf { it.isNotEmpty() }?.let { Fact("Work history", it.joinToString(" · ")) },
            c.email?.let { Fact("Email", it) },
            c.phone?.let { Fact("Phone", it) },
            s.tags.takeIf { it.isNotEmpty() }?.let { Fact("Tags", it.sorted().joinToString(", ")) },
        ) + importantDates.map { Fact(it.label, DateText.birthday(it.month, it.day, it.year)) }
    }
}
