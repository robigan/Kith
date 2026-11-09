package com.robigan.TheRealDex.presentation

import com.robigan.TheRealDex.data.DexRepository
import com.robigan.TheRealDex.data.DoneReceipt
import com.robigan.TheRealDex.data.Snapshot
import com.robigan.TheRealDex.domain.DateProvider
import com.robigan.TheRealDex.domain.DateText
import com.robigan.TheRealDex.domain.Occasions
import com.robigan.TheRealDex.model.Cadence
import com.robigan.TheRealDex.model.ContactSummary
import com.robigan.TheRealDex.model.Interaction
import com.robigan.TheRealDex.model.SignalType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Today tab (FR-010…FR-019). */
class TodayViewModel(private val repository: DexRepository, private val dates: DateProvider) : ViewModel() {
    private class Done(val receipt: DoneReceipt, val row: KeepInTouchRow, val due: LocalDate)

    private val done = MutableStateFlow<Map<String, Done>>(emptyMap())
    private var snapshot = Snapshot.EMPTY
    private var openRows: Map<String, Pair<KeepInTouchRow, LocalDate>> = emptyMap()

    val state: StateFlow<TodayState> = dates.todayFlow().flatMapLatest { today ->
        combine(repository.snapshot(), repository.unnotedMeetings(today.minus(DatePeriod(days = NOTE_WINDOW_DAYS)), today), done) { snap, unnoted, doneMap ->
            snapshot = snap
            build(snap, unnoted, doneMap, today)
        }
    }.toState(TodayState.EMPTY)

    /** Row button: marks done, or undoes a done row. */
    fun toggle(contactId: String) {
        if (contactId in done.value) undo(contactId) else markDone(contactId)
    }

    /** Swipe right (FR-015). */
    fun markDone(contactId: String) {
        val (row, due) = openRows[contactId] ?: return
        val summary = snapshot.byId[contactId] ?: return
        launch {
            val receipt = repository.markDone(contactId)
            val next = summary.effectiveCadence?.nextLabel ?: "later"
            done.update { it + (contactId to Done(receipt, row.copy(isDone = true, doneLabel = "Logged · next $next"), due)) }
        }
    }

    fun undo(contactId: String) {
        val entry = done.value[contactId] ?: return
        launch {
            repository.undoDone(entry.receipt)
            done.update { it - contactId }
        }
    }

    /** Swipe left: snooze one week (FR-015). */
    fun snooze(contactId: String) {
        val due = openRows[contactId]?.second ?: return
        launch { repository.snooze(contactId, due.toEpochDays()) }
    }

    fun dismissReason(key: String) = launch { repository.dismissSignal(key) }

    fun dismissNeedsNote(interactionId: String) = launch { repository.dismissSignal(noteKey(interactionId)) }

    private fun build(snap: Snapshot, unnoted: List<Interaction>, doneMap: Map<String, Done>, today: LocalDate): TodayState {
        val window = today.plus(DatePeriod(days = DUE_WINDOW_DAYS))
        val open = snap.people
            .filter { it.contact.id !in doneMap && it.effectiveCadence != Cadence.NEVER && it.due != null && it.due <= window }
            .map { s -> s.contact.id to (keepInTouchRow(s, s.due!!, today) to s.due) }
        openRows = open.toMap()
        val rows = (open.map { it.second } + doneMap.values.map { it.row to it.due })
            .sortedWith(compareBy({ it.second }, { it.first.name }))
            .map { it.first }
        val openCount = open.size
        return TodayState(
            dateLabel = DateText.long(today),
            rows = rows,
            dueLabel = if (openCount == 0) "All caught up" else "$openCount due",
            reasons = reasons(snap, today),
            needsNote = unnoted.filter { noteKey(it.id) !in snap.dismissed && it.participantIds.isNotEmpty() }.map { needsNote(it) },
            hasPeople = snap.people.isNotEmpty(),
            isLoading = false,
        )
    }

    private fun keepInTouchRow(s: ContactSummary, due: LocalDate, today: LocalDate) = KeepInTouchRow(
        contactId = s.contact.id,
        name = s.contact.name,
        avatar = s.avatar(),
        status = DateText.dueStatus(due, today),
        isUrgent = due <= today,
        cadenceLabel = s.effectiveCadence?.label ?: "",
        isDone = false,
        doneLabel = "",
    )

    private fun reasons(snap: Snapshot, today: LocalDate): List<ReasonCard> {
        val birthdays = snap.people.mapNotNull { s ->
            val b = s.contact.birthday ?: return@mapNotNull null
            val next = Occasions.nextBirthday(b, today)
            if (today.daysUntil(next) > SOON_DAYS) return@mapNotNull null
            val key = "birthday:${s.contact.id}:${next.year}"
            if (key in snap.dismissed) return@mapNotNull null
            val name = s.contact.name
            val main = b.year?.let { "$name turns ${next.year - it}." } ?: "It's $name's birthday."
            val gift = s.giftIdeas.firstOrNull()?.let { " Gift idea saved: ${it.replaceFirstChar(Char::lowercaseChar)}." } ?: ""
            next to ReasonCard(key, SignalType.BIRTHDAY, s.contact.id, s.avatar(), "Birthday · ${DateText.relativeDay(next, today)}", main + gift)
        }.sortedBy { it.first }.map { it.second }

        val roles = snap.signals.filter { it.type == SignalType.NEW_ROLE && it.date.daysUntil(today) <= ROLE_SIGNAL_DAYS }
            .mapNotNull { sig ->
                val s = snap.byId[sig.contactId] ?: return@mapNotNull null
                val key = "signal:${sig.id}"
                if (key in snap.dismissed) return@mapNotNull null
                val label = sig.source?.let { "New role · ${it.label}" } ?: "New role"
                ReasonCard(key, SignalType.NEW_ROLE, s.contact.id, s.avatar(), label, sig.text)
            }

        val anniversaries = snap.people.mapNotNull { s ->
            val met = s.contact.metOn ?: return@mapNotNull null
            val next = Occasions.nextAnniversary(met, today)
            val years = next.year - met.year
            val days = today.daysUntil(next)
            if (years < 1 || days > SOON_DAYS) return@mapNotNull null
            val key = "met:${s.contact.id}:${next.year}"
            if (key in snap.dismissed) return@mapNotNull null
            val label = "Met ${DateText.plural(years, "year")} ago" + if (days == 0) "" else " · ${DateText.relativeDay(next, today)}"
            val how = s.contact.howWeMet?.let { " — ${it.replaceFirstChar(Char::lowercaseChar)}." } ?: "."
            next to ReasonCard(key, SignalType.MET_ANNIVERSARY, s.contact.id, s.avatar(), label, s.contact.name + how)
        }.sortedBy { it.first }.map { it.second }

        return birthdays + roles + anniversaries
    }

    private fun needsNote(i: Interaction) = NeedsNoteCard(
        interactionId = i.id,
        contactIds = i.participantIds,
        weekday = DateText.weekdayShort(i.date.dayOfWeek).uppercase(),
        day = i.date.day.toString(),
        title = i.title ?: i.type.label,
        subtitle = listOfNotNull(i.location, i.durationMinutes?.let { "$it min" }).joinToString(" · "),
    )

    private fun noteKey(interactionId: String) = "note:$interactionId"

    private companion object {
        const val DUE_WINDOW_DAYS = 3
        const val SOON_DAYS = 6
        const val ROLE_SIGNAL_DAYS = 30
        const val NOTE_WINDOW_DAYS = 14
    }
}
