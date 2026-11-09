package com.robigan.TheRealDex.presentation

import com.robigan.TheRealDex.data.DexRepository
import com.robigan.TheRealDex.domain.DateProvider
import com.robigan.TheRealDex.domain.People
import com.robigan.TheRealDex.model.PeopleFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

/** People tab (FR-020…FR-025); also backs the person pickers. */
class PeopleViewModel(private val repository: DexRepository, private val dates: DateProvider) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(PeopleFilter.ALL)

    private val noteMatches = query.debounce(150).mapLatest { q ->
        if (q.isBlank()) emptySet() else repository.contactIdsWithNotesMatching(q.trim())
    }.onStart { emit(emptySet()) }

    val state: StateFlow<PeopleState> = combine(repository.snapshot(), dates.todayFlow(), query, filter, noteMatches) { snap, today, q, f, notes ->
        val searched = snap.people.filter { People.matchesQuery(it, q, notes) }
        val filters = PeopleFilter.entries.map { pf ->
            FilterChip(pf, pf.label, searched.count { People.matches(it, pf, today) }, pf == f)
        }
        val sections = searched.filter { People.matches(it, f, today) }
            .groupBy { s -> s.contact.name.firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar()?.toString() ?: "#" }
            .toList()
            .sortedWith(compareBy({ it.first == "#" }, { it.first }))
            .map { (letter, people) ->
                PeopleSection(letter, people.map { s ->
                    PersonRow(s.contact.id, s.contact.name, s.avatar(), People.subtitle(s, today), People.isDue(s, today))
                })
            }
        PeopleState(q, filters, sections, sections.map { it.letter }, snap.people.size)
    }.toState(PeopleState.EMPTY)

    fun setQuery(text: String) {
        query.value = text
    }

    fun setFilter(value: PeopleFilter) {
        filter.value = value
    }
}
