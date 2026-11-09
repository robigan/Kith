package com.robigan.TheRealDex.presentation

import com.robigan.TheRealDex.data.DexRepository
import com.robigan.TheRealDex.data.SmartViewInput
import com.robigan.TheRealDex.data.SourceConnection
import com.robigan.TheRealDex.domain.DateProvider
import com.robigan.TheRealDex.domain.People
import com.robigan.TheRealDex.model.Cadence
import com.robigan.TheRealDex.model.SourceProvider
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/** Groups tab: groups, smart views and sources (FR-050…FR-055). */
class GroupsViewModel(private val repository: DexRepository, private val dates: DateProvider) : ViewModel() {

    val state: StateFlow<GroupsState> = combine(repository.snapshot(), dates.todayFlow()) { snap, today ->
        GroupsState(
            groups = snap.groups.map { g ->
                val members = g.memberIds.mapNotNull { snap.byId[it] }.sortedBy { it.contact.name }
                GroupRow(
                    id = g.id,
                    name = g.name,
                    count = members.size,
                    cadenceLabel = g.cadence?.shortLabel ?: "no cadence",
                    dueCount = members.count { People.isDue(it, today) },
                    faces = members.take(3).map { it.avatar() },
                )
            },
            views = snap.smartViews.map { v ->
                SmartViewRow(v.id, v.name, v.code, v.tint, snap.people.count { People.smartViewMatches(it, v.filter, snap, today) })
            },
            sources = snap.connections.map { sourceRow(it) },
            tags = snap.people.flatMap { it.tags }.distinct().sorted(),
        )
    }.toState(GroupsState.EMPTY)

    suspend fun createGroup(name: String, cadence: Cadence?): String = repository.createGroup(name, cadence, emptyList())

    suspend fun saveSmartView(id: String?, input: SmartViewInput): String = repository.saveSmartView(id, input)

    fun deleteSmartView(id: String) = launch { repository.deleteSmartView(id) }

    fun deleteGroup(id: String) = launch { repository.deleteGroup(id) }

    private fun sourceRow(c: SourceConnection): SourceRow {
        val available = c.provider != SourceProvider.GMAIL
        val status = when {
            !available -> "Not available yet"
            !c.connected -> ""
            else -> c.lastSyncAtMillis?.let { "Synced · ${ago(it)}" } ?: "Synced"
        }
        return SourceRow(c.provider, c.provider.label, status, c.connected, available)
    }

    private fun ago(millis: Long): String {
        val minutes = ((dates.nowMillis() - millis) / 60_000).coerceAtLeast(0)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 60 * 24 -> "${minutes / 60} h ago"
            else -> "${minutes / (60 * 24)} d ago"
        }
    }
}

/** A group's members and settings. */
class GroupDetailViewModel(
    private val groupId: String,
    private val repository: DexRepository,
    dates: DateProvider,
) : ViewModel() {
    val state: StateFlow<GroupDetailState> = combine(repository.snapshot(), dates.todayFlow()) { snap, today ->
        val group = snap.groups.firstOrNull { it.id == groupId } ?: return@combine GroupDetailState.EMPTY.copy(exists = false)
        GroupDetailState(
            exists = true,
            name = group.name,
            cadence = group.cadence,
            cadenceLabel = group.cadence?.label ?: "No cadence",
            members = group.memberIds.mapNotNull { snap.byId[it] }.sortedBy { it.contact.name }.map { s ->
                PersonRow(s.contact.id, s.contact.name, s.avatar(), People.subtitle(s, today), People.isDue(s, today))
            },
        )
    }.toState(GroupDetailState.EMPTY)

    fun update(name: String, cadence: Cadence?) = launch { repository.updateGroup(groupId, name, cadence) }
    fun addMembers(contactIds: List<String>) = launch { repository.addMembers(groupId, contactIds) }
    fun removeMember(contactId: String) = launch { repository.removeMember(groupId, contactId) }
    suspend fun delete() = repository.deleteGroup(groupId)
}

/** The people a smart view currently matches. */
class SmartViewViewModel(
    private val viewId: String,
    private val repository: DexRepository,
    dates: DateProvider,
) : ViewModel() {
    val state: StateFlow<SmartViewState> = combine(repository.snapshot(), dates.todayFlow()) { snap, today ->
        val view = snap.smartViews.firstOrNull { it.id == viewId } ?: return@combine SmartViewState.EMPTY.copy(exists = false)
        SmartViewState(
            exists = true,
            name = view.name,
            code = view.code,
            tint = view.tint,
            criteria = People.describe(view.filter, snap),
            people = snap.people.filter { People.smartViewMatches(it, view.filter, snap, today) }.map { s ->
                PersonRow(s.contact.id, s.contact.name, s.avatar(), People.subtitle(s, today), People.isDue(s, today))
            },
        )
    }.toState(SmartViewState.EMPTY)

    /** The view's current criteria, to prefill the editor. */
    suspend fun input(): SmartViewInput? = repository.smartView(viewId)?.let { v ->
        val f = v.filter
        SmartViewInput(v.name, v.code, v.tint, f.tag, f.groupId, f.minDaysSinceContact, f.location, f.source, f.birthdayThisMonth, f.changedJobsWithinDays, f.metContext)
    }

    suspend fun delete() = repository.deleteSmartView(viewId)
}
