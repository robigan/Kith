package com.robigan.TheRealDex.presentation

import com.robigan.TheRealDex.data.DexRepository
import com.robigan.TheRealDex.data.SampleData
import com.robigan.TheRealDex.domain.DateProvider
import com.robigan.TheRealDex.model.Cadence
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

data class PlannedReminder(val contactId: String, val name: String, val epochDay: Long, val body: String)

/** App-wide concerns: first-launch setup, sample data, and the reminders Swift schedules as notifications (FR-006). */
class AppViewModel(
    private val repository: DexRepository,
    private val sampleData: SampleData,
    dates: DateProvider,
) : ViewModel() {
    val reminders: StateFlow<List<PlannedReminder>> = combine(repository.snapshot(), dates.todayFlow()) { snap, today ->
        snap.people.mapNotNull { s ->
            val due = s.due ?: return@mapNotNull null
            val cadence = s.effectiveCadence ?: return@mapNotNull null
            if (cadence == Cadence.NEVER || due < today) return@mapNotNull null
            PlannedReminder(
                s.contact.id,
                s.contact.name,
                due.toEpochDays(),
                "Time to catch up with ${s.contact.firstName} — ${cadence.label.lowercase()}."
            )
        }.sortedBy { it.epochDay }
    }.toState(emptyList())

    suspend fun start() = repository.ensureDefaults()

    suspend fun loadSampleData() {
        if (repository.isEmpty()) sampleData.load()
    }
}
